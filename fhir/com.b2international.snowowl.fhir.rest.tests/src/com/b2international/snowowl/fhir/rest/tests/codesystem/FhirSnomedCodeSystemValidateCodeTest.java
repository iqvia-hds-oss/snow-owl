/*
 * Copyright 2021-2026 B2i Healthcare, https://b2ihealthcare.com
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

import static com.b2international.snowowl.test.commons.fhir.FhirApiHelpers.*;
import static com.b2international.snowowl.test.commons.rest.RestExtensions.givenAuthenticatedRequest;
import static org.hamcrest.CoreMatchers.equalTo;

import org.hl7.fhir.r5.model.Coding;
import org.junit.Test;

import com.b2international.fhir.r5.operations.CodeSystemValidateCodeParameters;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.rest.tests.FhirRestTest;
import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants;
import com.b2international.snowowl.test.commons.rest.RestExtensions;

/**
 * CodeSystem $validate-code operation for SNOMED CT REST end-point test cases
 * 
 * @since 7.17.0
 */
public class FhirSnomedCodeSystemValidateCodeTest extends FhirRestTest {
	
	@Test
	public void GET_CodeSystem_$validate_code_NonExisting() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.queryParam("version", SNOMEDCT_URL)
			.queryParam("code", "12345")
			.when().get(CODESYSTEM_VALIDATE_CODE)
			.then().assertThat()
			.statusCode(200)
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(false))
			.body("parameter[1].name", equalTo("system"))
			.body("parameter[1].valueUri", equalTo(SnomedTerminologyComponentConstants.SNOMED_URI_SCT))
			.body("parameter[2].name", equalTo("version"))
			.body("parameter[2].valueString", equalTo(SNOMEDCT_URL))
			.body("parameter[3].name", equalTo("message"))
			.body("parameter[3].valueString", equalTo("Could not find code '[12345]' in CodeSystem 'http://snomed.info/sct' version 'http://snomed.info/sct/900000000000207008'."));
	}
	
	@Test
	public void GET_CodeSystem_$validate_code_InvalidDisplay() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.queryParam("version", SNOMEDCT_URL)
			.queryParam("code", Concepts.ROOT_CONCEPT)
			.queryParam("display", RestExtensions.encodeQueryParameter("Unknown display"))
			.when().get(CODESYSTEM_VALIDATE_CODE)
			.then().assertThat()
			.statusCode(200)
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(false))
			.body("parameter[1].name", equalTo("system"))
			.body("parameter[1].valueUri", equalTo("http://snomed.info/sct"))
			.body("parameter[2].name", equalTo("version"))
			.body("parameter[2].valueString", equalTo(SNOMEDCT_URL))
			.body("parameter[3].name", equalTo("message"))
			.body("parameter[3].valueString", equalTo("Incorrect display 'Unknown display' for code '138875005'."))
			.body("parameter[4].name", equalTo("display"))
			.body("parameter[4].valueString", equalTo("SNOMED CT Concept"));
	}
	
	@Test
	public void GET_CodeSystem_$validate_code_Existing() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.queryParam("version", SNOMEDCT_URL)
			.queryParam("code", Concepts.ROOT_CONCEPT)
			.when().get(CODESYSTEM_VALIDATE_CODE)
			.then().assertThat()
			.statusCode(200)
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true))
			.body("parameter[1].name", equalTo("system"))
			.body("parameter[1].valueUri", equalTo(SnomedTerminologyComponentConstants.SNOMED_URI_SCT))
			.body("parameter[2].name", equalTo("version"))
			.body("parameter[2].valueString", equalTo(SNOMEDCT_URL));
	}
	
	
	@Test
	public void GET_CodeSystem_$validate_code_BaseURI() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
		.queryParam("url", FhirModelHelpers.SNOMED_BASE_URI_STRING)
		// .queryParam("version", ...) is omitted to test whether the base URI alone can be used
		.queryParam("code", Concepts.ROOT_CONCEPT)
		.when().get(CODESYSTEM_VALIDATE_CODE)
		.then().assertThat()
		.statusCode(200)
		.body("parameter[0].name", equalTo("result"))
		.body("parameter[0].valueBoolean", equalTo(true))
		.body("parameter[1].name", equalTo("system"))
		.body("parameter[1].valueUri", equalTo(SnomedTerminologyComponentConstants.SNOMED_URI_SCT))
		.body("parameter[2].name", equalTo("version"))
		.body("parameter[2].valueString", equalTo("http://snomed.info/sct/900000000000207008/version/20200131"));
	}
	
	@Test
	public void POST_CodeSystem_$validate_code_Existing_R4() throws Exception {
		var parameters = new CodeSystemValidateCodeParameters()
			.setUrl(FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.setCoding(new Coding()
				.setSystem(FhirModelHelpers.SNOMED_BASE_URI_STRING)
				.setVersion(SNOMEDCT_URL)
				.setCode(Concepts.ROOT_CONCEPT));

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.contentType(APPLICATION_FHIR_JSON_R4_0_1)
			.accept(APPLICATION_FHIR_JSON_R4_0_1)
			.body(toJson(parameters.getParameters()))
			.when().post(CODESYSTEM_VALIDATE_CODE)
			.then().assertThat()
			.statusCode(200)
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true))
			.body("parameter[1].name", equalTo("system"))
			.body("parameter[1].valueUri", equalTo(SnomedTerminologyComponentConstants.SNOMED_URI_SCT))
			.body("parameter[2].name", equalTo("version"))
			.body("parameter[2].valueString", equalTo(SNOMEDCT_URL));
	}
	
	@Test
	public void POST_CodeSystem_$validate_code_Existing_R4B() throws Exception {
		var parameters = new CodeSystemValidateCodeParameters()
			.setUrl(FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.setCoding(new Coding()
				.setSystem(FhirModelHelpers.SNOMED_BASE_URI_STRING)
				.setVersion(SNOMEDCT_URL)
				.setCode(Concepts.ROOT_CONCEPT));

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.contentType(APPLICATION_FHIR_JSON_R4_3_0)
			.accept(APPLICATION_FHIR_JSON_R4_3_0)
			.body(toJson(parameters.getParameters()))
			.when().post(CODESYSTEM_VALIDATE_CODE)
			.then().assertThat()
			.statusCode(200)
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true))
			.body("parameter[1].name", equalTo("system"))
			.body("parameter[1].valueUri", equalTo(SnomedTerminologyComponentConstants.SNOMED_URI_SCT))
			.body("parameter[2].name", equalTo("version"))
			.body("parameter[2].valueString", equalTo(SNOMEDCT_URL));
	}
	
	@Test
	public void POST_CodeSystem_$validate_code_Existing_R5() throws Exception {
		var parameters = new CodeSystemValidateCodeParameters()
			.setUrl(FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.setCoding(new Coding()
				.setSystem(FhirModelHelpers.SNOMED_BASE_URI_STRING)
				.setVersion(SNOMEDCT_URL)
				.setCode(Concepts.ROOT_CONCEPT));

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.contentType(APPLICATION_FHIR_JSON_R5_0_0)
			.accept(APPLICATION_FHIR_JSON_R5_0_0)
			.body(toJson(parameters.getParameters()))
			.when().post(CODESYSTEM_VALIDATE_CODE)
			.then().assertThat()
			.statusCode(200)
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true))
			.body("parameter[1].name", equalTo("system"))
			.body("parameter[1].valueUri", equalTo(SnomedTerminologyComponentConstants.SNOMED_URI_SCT))
			.body("parameter[2].name", equalTo("version"))
			.body("parameter[2].valueString", equalTo(SNOMEDCT_URL));
	}
	
}
