/*
 * Copyright 2018-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.fhir.rest.tests.codesystem;

import static com.b2international.snowowl.fhir.rest.tests.FhirTestConcepts.BACTERIA;
import static com.b2international.snowowl.test.commons.rest.RestExtensions.givenAuthenticatedRequest;
import static org.hamcrest.CoreMatchers.equalTo;
import static com.b2international.snowowl.test.commons.fhir.FhirApiHelpers.*;

import org.junit.Test;

import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.rest.tests.FhirRestTest;

/**
 * CodeSystem $subsumes operation REST end-point test cases
 * 
 * @since 6.7
 */
public class FhirSnomedCodeSystemSubsumesTest extends FhirRestTest {
	
	private static final String PROCEDURE = "71388002";
	private static final String ORGANISM_TOP_LEVEL = "410607006";
	private static final Object MICROORGANISM = "264395009";

	@Test
	public void GET_CodeSystem_$subsumes_Subsumes() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("codeA", ORGANISM_TOP_LEVEL)
			.queryParam("codeB", BACTERIA)
			.queryParam("system", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.queryParam("version", SNOMEDCT_URL)
			.when().get(CODESYSTEM_SUBSUMES)
			.then().assertThat()
			.statusCode(200)
			.body("parameter[0].name", equalTo("outcome"))
			.body("parameter[0].valueCode", equalTo("subsumes"));
	}
	
	@Test
	public void GET_CodeSystem_$subsumes_SubsumedBy() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("codeA", BACTERIA)
			.queryParam("codeB", ORGANISM_TOP_LEVEL)
			.queryParam("system", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.queryParam("version", SNOMEDCT_URL)
			.when().get(CODESYSTEM_SUBSUMES)
			.then().assertThat()
			.statusCode(200)
			.body("parameter[0].name", equalTo("outcome"))
			.body("parameter[0].valueCode", equalTo("subsumed-by"));
	}
	
	@Test
	public void GET_CodeSystem_$subsumes_NotSubsumed() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("codeA", BACTERIA)
			.queryParam("codeB", PROCEDURE)
			.queryParam("system", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.queryParam("version", SNOMEDCT_URL)
			.when().get(CODESYSTEM_SUBSUMES)
			.then().assertThat()
			.statusCode(200)
			.body("parameter[0].name", equalTo("outcome"))
			.body("parameter[0].valueCode", equalTo("not-subsumed"));
	}
	
	@Test
	public void GET_CodeSystem_$subsumes_Equivalent() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("codeA", BACTERIA)
			.queryParam("codeB", BACTERIA)
			.queryParam("system", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.queryParam("version", SNOMEDCT_URL)
			.when().get(CODESYSTEM_SUBSUMES)
			.then().assertThat()
			.statusCode(200)
			.body("parameter[0].name", equalTo("outcome"))
			.body("parameter[0].valueCode", equalTo("equivalent"));
	}
	
	@Test
	public void GET_CodeSystem_$subsumes_SubsumedBy_WithVersion() throws Exception {
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("codeA", BACTERIA) //Bacteria
			.queryParam("codeB", MICROORGANISM) //Microorganism (parent)
			.queryParam("system", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.queryParam("version", "http://snomed.info/sct/900000000000207008/version/20180131")
			.when().get(CODESYSTEM_SUBSUMES)
			.then().assertThat()
			.statusCode(200)
			.body("parameter[0].name", equalTo("outcome"))
			.body("parameter[0].valueCode", equalTo("subsumed-by"));
	}
	
	@Test
	public void GET_CodeSystem_$subsumes_Subsumes_BaseURI() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("codeA", ORGANISM_TOP_LEVEL)
			.queryParam("codeB", BACTERIA)
			.queryParam("system", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			// .queryParam("version", ...) is omitted to test whether the base URI alone can be used
			.when().get(CODESYSTEM_SUBSUMES)
			.then().assertThat()
			.statusCode(200)
			.body("parameter[0].name", equalTo("outcome"))
			.body("parameter[0].valueCode", equalTo("subsumes"));
	}
	
}
