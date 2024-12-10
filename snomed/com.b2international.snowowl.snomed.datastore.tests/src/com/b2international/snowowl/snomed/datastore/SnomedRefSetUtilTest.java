/*
 * Copyright 2024 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.snomed.datastore;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * @since 9.5.0
 */
@RunWith(Parameterized.class)
public class SnomedRefSetUtilTest {

	// Close-to-but-not-quite-production reference set labels
	private static final Object[][] INPUT = {
		{ "FORMERLY association reference set", "Formerly" },
		{ "IDENTICAL TO association reference set", "Identical to" },
		{ "RELOCATED TO association reference set", "Relocated to" },
		{ "Archival association reference set", "Archival" },
		{ "RELOCATED FROM association reference set", "Relocated from" },
		{ "ANALOGOUS TO association reference set", "Analogous to" },
		{ "SUBSTITUTED BY association reference set", "Substituted by" },
		{ "Was bundle of association reference set", "Was bundle of" },
		{ "OPTIONAL association reference set", "Optional" },
		{ "POINTS TO concept association reference set", "Points to" },
		{ "POTENTIALLY SUBSTITUTED BY association reference set", "Potentially substituted by" },
		{ "POTENTIALLY IDENTICAL TO association reference set", "Potentially identical to" },
		{ "XHS Health Record Component association reference set", "XHS Health Record Component" },
		{ "PARTIALLY IDENTICAL TO association reference set", "Partially identical to" },
		{ "Frame and segment association reference set", "Frame and segment" },
		{ "Frame and whole association reference set", "Frame and whole" },
		{ "Had real medicinal product association reference set", "Had real medicinal product" },
		{ "Had hypothetical medicinal product association reference set", "Had hypothetical medicinal product" },
		{ "XHS catalog of medicines and devices association type reference set", "XHS catalog of medicines and devices" },
		{ "European Blood Filtering Association reference set", "European Blood Filtering Association" },
		{ "XHS catalog of medicines and devices Virtual Therapeutic Moiety revision association reference set", "XHS catalog of medicines and devices Virtual Therapeutic Moiety revision" },
	};
	
	@Parameters(name = "{0}")
	public static Object[][] data() {
		return INPUT;
	}

	@Parameter(0)
	public String refSetName;
	
	@Parameter(1)
	public String shortenedName;

	@Test
	public void testRefSetNameShortening() {
	    assertEquals(shortenedName, SnomedRefSetUtil.shortenAssociationRefSetName(refSetName));
	}
}
