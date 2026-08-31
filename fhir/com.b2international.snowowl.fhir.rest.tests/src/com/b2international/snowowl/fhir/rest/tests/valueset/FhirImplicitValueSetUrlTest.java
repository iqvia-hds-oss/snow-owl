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
package com.b2international.snowowl.fhir.rest.tests.valueset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.b2international.snowowl.fhir.core.FhirModelHelpers;

/**
 * Tests for implicit Value Set URL detection and parsing methods in {@link FhirModelHelpers}.
 * 
 * @since 10.2.0
 */
public class FhirImplicitValueSetUrlTest {

	@Test
	public void isImplicitValueSetURL_null_returnsFalse() {
		assertFalse(FhirModelHelpers.isImplicitValueSetUrl(null));
	}

	@Test
	public void isImplicitValueSetURL_empty_returnsFalse() {
		assertFalse(FhirModelHelpers.isImplicitValueSetUrl(""));
	}

	@Test
	public void isImplicitValueSetURL_snomed_returnsTrue() {
		assertTrue(FhirModelHelpers.isImplicitValueSetUrl("http://snomed.info/sct?fhir_vs"));
	}

	@Test
	public void isImplicitValueSetURL_snomedWithIsaQuery_returnsTrue() {
		assertTrue(FhirModelHelpers.isImplicitValueSetUrl("http://snomed.info/sct?fhir_vs=isa/138875005"));
	}

	@Test
	public void isImplicitValueSetURL_loinc_returnsTrue() {
		assertTrue(FhirModelHelpers.isImplicitValueSetUrl("http://loinc.org/vs"));
	}

	@Test
	public void isImplicitValueSetURL_loincWithCode_returnsTrue() {
		assertTrue(FhirModelHelpers.isImplicitValueSetUrl("http://loinc.org/vs/LP12345-6"));
	}

	@Test
	public void isImplicitValueSetURL_genericVsSuffix_returnsTrue() {
		assertTrue(FhirModelHelpers.isImplicitValueSetUrl("http://example.com/codesystem/vs"));
	}

	@Test
	public void isImplicitValueSetURL_unrelatedUrl_returnsFalse() {
		assertFalse(FhirModelHelpers.isImplicitValueSetUrl("http://example.com/codesystem"));
	}

	// -------------------------------------------------------------------------

	@Test
	public void isSnomedImplicitValueSetUrl_null_returnsFalse() {
		assertFalse(FhirModelHelpers.isSnomedImplicitValueSetUrl(null));
	}

	@Test
	public void isSnomedImplicitValueSetUrl_baseUriOnly_returnsFalse() {
		assertFalse(FhirModelHelpers.isSnomedImplicitValueSetUrl("http://snomed.info/sct"));
	}

	@Test
	public void isSnomedImplicitValueSetUrl_fhirVsQueryParam_returnsTrue() {
		assertTrue(FhirModelHelpers.isSnomedImplicitValueSetUrl("http://snomed.info/sct?fhir_vs"));
	}

	@Test
	public void isSnomedImplicitValueSetUrl_fhirVsIsaQuery_returnsTrue() {
		assertTrue(FhirModelHelpers.isSnomedImplicitValueSetUrl("http://snomed.info/sct?fhir_vs=isa/138875005"));
	}

	@Test
	public void isSnomedImplicitValueSetUrl_fhirVsRefsetQuery_returnsTrue() {
		assertTrue(FhirModelHelpers.isSnomedImplicitValueSetUrl("http://snomed.info/sct?fhir_vs=refset"));
	}

	@Test
	public void isSnomedImplicitValueSetUrl_fhirVsRefsetWithIdQuery_returnsTrue() {
		assertTrue(FhirModelHelpers.isSnomedImplicitValueSetUrl("http://snomed.info/sct?fhir_vs=refset/900000000000497000"));
	}

	@Test
	public void isSnomedImplicitValueSetUrl_withModuleAndVersion_returnsTrue() {
		assertTrue(FhirModelHelpers.isSnomedImplicitValueSetUrl("http://snomed.info/sct/900000000000207008/version/20230131?fhir_vs"));
	}

	@Test
	public void isSnomedImplicitValueSetUrl_differentQueryParam_returnsFalse() {
		assertFalse(FhirModelHelpers.isSnomedImplicitValueSetUrl("http://snomed.info/sct?other_param=value"));
	}

	@Test
	public void isSnomedImplicitValueSetUrl_loincUrl_returnsFalse() {
		assertFalse(FhirModelHelpers.isSnomedImplicitValueSetUrl("http://loinc.org/vs"));
	}

	// -------------------------------------------------------------------------

	@Test
	public void isLoincImplicitValueSetUrl_null_returnsFalse() {
		assertFalse(FhirModelHelpers.isLoincImplicitValueSetUrl(null));
	}

	@Test
	public void isLoincImplicitValueSetUrl_loincVs_returnsTrue() {
		assertTrue(FhirModelHelpers.isLoincImplicitValueSetUrl("http://loinc.org/vs"));
	}

	@Test
	public void isLoincImplicitValueSetUrl_withAlphanumericCode_returnsTrue() {
		assertTrue(FhirModelHelpers.isLoincImplicitValueSetUrl("http://loinc.org/vs/LP12345-6"));
	}

	@Test
	public void isLoincImplicitValueSetUrl_withNumericCode_returnsTrue() {
		assertTrue(FhirModelHelpers.isLoincImplicitValueSetUrl("http://loinc.org/vs/1234-5"));
	}

	@Test
	public void isLoincImplicitValueSetUrl_withAlphaCode_returnsTrue() {
		assertTrue(FhirModelHelpers.isLoincImplicitValueSetUrl("http://loinc.org/vs/CHOL"));
	}

	@Test
	public void isLoincImplicitValueSetUrl_extraTrailingSlash_returnsFalse() {
		assertFalse(FhirModelHelpers.isLoincImplicitValueSetUrl("http://loinc.org/vs/LP12345-6/extra"));
	}

	@Test
	public void isLoincImplicitValueSetUrl_loincBaseUrlWithoutVs_returnsFalse() {
		assertFalse(FhirModelHelpers.isLoincImplicitValueSetUrl("http://loinc.org"));
	}

	@Test
	public void isLoincImplicitValueSetUrl_snomedUrl_returnsFalse() {
		assertFalse(FhirModelHelpers.isLoincImplicitValueSetUrl("http://snomed.info/sct?fhir_vs"));
	}

	// -------------------------------------------------------------------------

	@Test
	public void getLoincImplicitValueSetCode_bareLoincVs_returnsNull() {
		assertNull(FhirModelHelpers.getLoincImplicitValueSetCode("http://loinc.org/vs"));
	}

	@Test
	public void getLoincImplicitValueSetCode_withCode_returnsCode() {
		assertEquals("LP12345-6", FhirModelHelpers.getLoincImplicitValueSetCode("http://loinc.org/vs/LP12345-6"));
	}

	@Test
	public void getLoincImplicitValueSetCode_withNumericCode_returnsCode() {
		assertEquals("1234-5", FhirModelHelpers.getLoincImplicitValueSetCode("http://loinc.org/vs/1234-5"));
	}

	@Test
	public void getLoincImplicitValueSetCode_withAlphaCode_returnsCode() {
		assertEquals("CHOL", FhirModelHelpers.getLoincImplicitValueSetCode("http://loinc.org/vs/CHOL"));
	}

	@Test
	public void getLoincImplicitValueSetCode_nonLoincUrl_returnsNull() {
		assertNull(FhirModelHelpers.getLoincImplicitValueSetCode("http://example.com/vs"));
	}

	@Test
	public void getLoincImplicitValueSetCode_null_returnsNull() {
		assertNull(FhirModelHelpers.getLoincImplicitValueSetCode(null));
	}

	// -------------------------------------------------------------------------

	@Test
	public void isGenericImplicitValueSetUrl_null_returnsFalse() {
		assertFalse(FhirModelHelpers.isGenericImplicitValueSetUrl(null));
	}

	@Test
	public void isGenericImplicitValueSetUrl_httpWithVsSuffix_returnsTrue() {
		assertTrue(FhirModelHelpers.isGenericImplicitValueSetUrl("http://example.com/codesystem/vs"));
	}

	@Test
	public void isGenericImplicitValueSetUrl_noVsSuffix_returnsFalse() {
		assertFalse(FhirModelHelpers.isGenericImplicitValueSetUrl("http://example.com/codesystem"));
	}

	@Test
	public void isGenericImplicitValueSetUrl_vsSuffixWithoutSlash_returnsFalse() {
		assertFalse(FhirModelHelpers.isGenericImplicitValueSetUrl("http://example.com/codesystemvs"));
	}

	@Test
	public void isGenericImplicitValueSetUrl_httpsScheme_returnsFalse() {
		// Generic implicit VS URL requires http:// prefix
		assertFalse(FhirModelHelpers.isGenericImplicitValueSetUrl("https://example.com/codesystem/vs"));
	}

	@Test
	public void isGenericImplicitValueSetUrl_loincUrl_returnsTrue() {
		// LOINC base URL also ends with /vs, so generic check applies too (making the order of checks important)
		assertTrue(FhirModelHelpers.isGenericImplicitValueSetUrl("http://loinc.org/vs"));
	}

	// -------------------------------------------------------------------------

	@Test
	public void toGenericCodeSystemUrl_stripsVsSuffix() {
		assertEquals("http://example.com/codesystem", 
			FhirModelHelpers.toGenericCodeSystemUrl("http://example.com/codesystem/vs"));
	}

	@Test
	public void toGenericCodeSystemUrl_loincVsUrl_returnsLoincBase() {
		assertEquals("http://loinc.org", 
			FhirModelHelpers.toGenericCodeSystemUrl("http://loinc.org/vs"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void toGenericCodeSystemUrl_notAGenericVsUrl_throwsException() {
		FhirModelHelpers.toGenericCodeSystemUrl("http://example.com/codesystem");
	}

	@Test(expected = IllegalArgumentException.class)
	public void toGenericCodeSystemUrl_null_throwsException() {
		FhirModelHelpers.toGenericCodeSystemUrl(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void toGenericCodeSystemUrl_httpsScheme_throwsException() {
		FhirModelHelpers.toGenericCodeSystemUrl("https://example.com/codesystem/vs");
	}
}
