/*
 * Copyright 2026 B2i Healthcare, https://b2ihealthcare.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b2international.snowowl.fhir.rest.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.b2international.snowowl.fhir.core.FhirModelHelpers;

/**
 * Verifies ID conversion methods {@link FhirModelHelpers#toFhirResourceId(String)} and
 * {@link FhirModelHelpers#fromFhirResourceId(String)}.
 *
 * @since 9.4.0
 */
public class FhirResourceIdMappingTest {

	private record ForwardCase(String description, String input, String expected) {
		// Empty record body
	}

	private record RoundTripCase(String description, String input) {
		// Empty record body
	}

	private static final List<ForwardCase> FORWARD_CASES = List.of(

		// Null / identity
		new ForwardCase("null input",                   null,                  null),
		new ForwardCase("empty string unchanged",       "",                    ""),
		new ForwardCase("plain id unchanged",           "SNOMEDCT",            "SNOMEDCT"),
		new ForwardCase("exactly 64 chars unchanged",   "a".repeat(64),        "a".repeat(64)),

		// Rule 1: "/" -> "--"
		new ForwardCase("single slash",                 "SNOMEDCT/20240101",   "SNOMEDCT--20240101"),
		new ForwardCase("multiple slashes",             "a/b/c",               "a--b--c"),

		// Rule 2: "--" -> ".h"
		new ForwardCase("single double-dash",           "a--b",                "a.hb"),
		new ForwardCase("multiple double-dashes",       "a--b--c",             "a.hb.hc"),

		// Rule 3: "_" -> ".u"
		new ForwardCase("single underscore",            "some_id",             "some.uid"),
		new ForwardCase("multiple underscores",         "a_b_c",               "a.ub.uc"),

		// Mixed: all three rules applied in a single pass
		new ForwardCase("slash and double-dash",        "a/b--c",              "a--b.hc"),
		new ForwardCase("slash and underscore",         "a/b_c",               "a--b.uc"),
		new ForwardCase("double-dash and underscore",   "a--b_c",              "a.hb.uc"),
		new ForwardCase("all three patterns",           "a/b--c_d",            "a--b.hc.ud"),

		// No chaining: "/" must not produce ".h" via a second pass
		new ForwardCase("slash does not chain to .h",   "a/b",                 "a--b")
	);

	private static final List<RoundTripCase> ROUND_TRIP_CASES = List.of(

		new RoundTripCase(null,                 null),
		new RoundTripCase("",                   ""),
		new RoundTripCase("plain id",           "SNOMEDCT"),
		new RoundTripCase("slash",              "SNOMEDCT/20240101"),
		new RoundTripCase("multiple slashes",   "a/b/c"),
		new RoundTripCase("double-dash",        "a--b"),
		new RoundTripCase("underscore",         "some_id"),
		new RoundTripCase("mixed patterns",     "a/b--c_d")
	);

	@Test
	public void testForwardMapping() {
		for (ForwardCase c : FORWARD_CASES) {
			assertEquals(c.description(), c.expected(), 
				FhirModelHelpers.toFhirResourceId(c.input()));
		}
	}

	@Test
	public void testRoundTrip() {
		for (RoundTripCase c : ROUND_TRIP_CASES) {
			assertEquals(c.description(), c.input(),
				FhirModelHelpers.fromFhirResourceId(FhirModelHelpers.toFhirResourceId(c.input())));
		}
	}

	@Test
	public void testLongIdTruncatedWithHash() {
		String longId = "a".repeat(65);
		String fhirId = FhirModelHelpers.toFhirResourceId(longId);
		
		assertEquals(64, fhirId.length());
		assertEquals("a".repeat(46), fhirId.substring(0, 46));
		assertEquals(".c", fhirId.substring(46, 48));
		
		fhirId.substring(48).chars().forEach(ch ->
			assertTrue("Expected hex digit, got: " + (char) ch,
				(ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f')));
	}

	@Test
	public void testLongIdHashIsDeterministic() {
		assertEquals(
			FhirModelHelpers.toFhirResourceId("x".repeat(65)),
			FhirModelHelpers.toFhirResourceId("x".repeat(65)));
	}

	@Test
	public void testLongIdSamePrefixProduceDifferentHashes() {
		assertNotEquals(
			FhirModelHelpers.toFhirResourceId("a".repeat(46) + "x".repeat(19)),
			FhirModelHelpers.toFhirResourceId("a".repeat(46) + "y".repeat(19)));
	}

	@Test
	public void testLongIdWithSubstitutionCausingTruncation() {
		// 31 'a' + '/' + 32 'b' = 64 input chars; after substitution: 65 chars
		String borderlineId = "a".repeat(31) + "/" + "b".repeat(32);
		String fhirId = FhirModelHelpers.toFhirResourceId(borderlineId);
		assertEquals(64, fhirId.length());
		assertEquals(".c", fhirId.substring(46, 48));
	}

	@Test
	public void testHashTruncatedReverseReturnsSuffix() {
		// Only the 46-char prefix is recoverable
		String longId = "a".repeat(65);
		String fhirId = FhirModelHelpers.toFhirResourceId(longId);
		assertEquals("a".repeat(46), FhirModelHelpers.fromFhirResourceId(fhirId));
	}
}