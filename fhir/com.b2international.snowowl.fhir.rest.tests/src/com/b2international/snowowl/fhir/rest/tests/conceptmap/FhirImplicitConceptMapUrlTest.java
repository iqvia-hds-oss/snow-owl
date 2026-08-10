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
package com.b2international.snowowl.fhir.rest.tests.conceptmap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.b2international.snowowl.fhir.core.FhirModelHelpers;

/**
 * Tests for implicit Concept Map URL detection and parsing methods in {@link FhirModelHelpers}.
 * 
 * @since 10.3.0
 */
public class FhirImplicitConceptMapUrlTest {

	@Test
	public void isImplicitConceptMapURL_null_returnsFalse() {
		assertFalse(FhirModelHelpers.isImplicitConceptMapUrl(null));
	}

	@Test
	public void isImplicitConceptMapURL_empty_returnsFalse() {
		assertFalse(FhirModelHelpers.isImplicitConceptMapUrl(""));
	}

	@Test
	public void isImplicitConceptMapURL_snomedWithoutId_returnsTrue() {
		// XXX: Actually this is a malformed query as a reference set ID would be required
		assertTrue(FhirModelHelpers.isImplicitConceptMapUrl("http://snomed.info/sct?fhir_cm"));
	}

	@Test
	public void isImplicitConceptMapURL_snomed_returnsTrue() {
		assertTrue(FhirModelHelpers.isImplicitConceptMapUrl("http://snomed.info/sct?fhir_cm=900000000000523009"));
	}

	// -------------------------------------------------------------------------

	@Test
	public void isSnomedImplicitConceptMapUrl_null_returnsFalse() {
		assertFalse(FhirModelHelpers.isSnomedImplicitConceptMapUrl(null));
	}

	@Test
	public void isSnomedImplicitConceptMapUrl_baseUriOnly_returnsFalse() {
		assertFalse(FhirModelHelpers.isSnomedImplicitConceptMapUrl("http://snomed.info/sct"));
	}

	@Test
	public void isSnomedImplicitConceptMapUrl_fhirCmWithoutId_returnsTrue() {
		// XXX: Actually this is a malformed query as a reference set ID would be required
		assertTrue(FhirModelHelpers.isSnomedImplicitConceptMapUrl("http://snomed.info/sct?fhir_cm"));
	}

	@Test
	public void isSnomedImplicitConceptMapUrl_fhirCm_returnsTrue() {
		assertTrue(FhirModelHelpers.isSnomedImplicitConceptMapUrl("http://snomed.info/sct?fhir_cm=900000000000523009"));
	}

	@Test
	public void isSnomedImplicitConceptMapUrl_differentQueryParam_returnsFalse() {
		assertFalse(FhirModelHelpers.isSnomedImplicitConceptMapUrl("http://snomed.info/sct?other_param=value"));
	}
}
