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

import static com.b2international.snowowl.fhir.rest.tests.FhirRestTest.Endpoints.CODESYSTEM_LOOKUP;
import static com.b2international.snowowl.test.commons.rest.RestExtensions.givenAuthenticatedRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;

import java.util.List;

import org.junit.Test;

import com.b2international.fhir.r5.operations.CodeSystemLookupParameters;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.rest.tests.FhirRestTest;
import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts;
import com.b2international.snowowl.test.commons.rest.RestExtensions;

import io.restassured.path.json.JsonPath;

/**
 * CodeSystem $lookup operation for FHIR code systems REST end-point test cases
 * 
 * @since 6.6
 */
public class FhirSnomedCodeSystemLookupTest extends FhirRestTest {

	private static final String CLINICAL_FINDING = "404684003";

	@Test
	public void GET_CodeSystem_$lookup_NonExistentSystem() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("system", "unknown")
			.queryParam("code", "12345")
			.queryParam("_format", "json")
			.when().get(CODESYSTEM_LOOKUP)
			.then().assertThat()
			.statusCode(404)
			.body("resourceType", equalTo("OperationOutcome"))
			.body("issue.severity", hasItem("error"))
			.body("issue.code", hasItem("not-found"))
			.body("issue.diagnostics", hasItem("CodeSystem with identifier 'unknown' could not be found."));
	}
	
	@Test
	public void GET_CodeSystem_$lookup_NonExistentCode() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("system", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.queryParam("version", SNOMEDCT_URL)
			.queryParam("code", "12345")
			.queryParam("_format", "json")
			.when().get(CODESYSTEM_LOOKUP)
			.then().assertThat()
			.statusCode(404)
			.body("resourceType", equalTo("OperationOutcome"))
			.body("issue.severity", hasItem("error"))
			.body("issue.code", hasItem("not-found"))
			.body("issue.diagnostics", hasItem("Concept with identifier '12345' could not be found."));
	}

	@Test
	public void GET_CodeSystem_$lookup_Existing_R4() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("system", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.queryParam("version", SNOMEDCT_URL)
			.queryParam("code", Concepts.ROOT_CONCEPT)
			.queryParam("_format", RestExtensions.encodeQueryParameter("application/fhir+json;fhirVersion=4.0.1"))
			.when().get(CODESYSTEM_LOOKUP)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			// Parameter order depends on OperationConvertor_40_50 since we are starting out with an R5 result and converting to R4
			.body("parameter[0].name", equalTo("name"))
			.body("parameter[0].valueString", equalTo("SNOMEDCT"))
			.body("parameter[1].name", equalTo("version"))
			.body("parameter[1].valueString", equalTo(SNOMEDCT_URL))
			.body("parameter[2].name", equalTo("display"))
			.body("parameter[2].valueString", equalTo("SNOMED CT Concept"));
	}
	
	@Test
	public void GET_CodeSystem_$lookup_Existing_R4B() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("system", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.queryParam("version", SNOMEDCT_URL)
			.queryParam("code", Concepts.ROOT_CONCEPT)
			.queryParam("_format", RestExtensions.encodeQueryParameter("application/fhir+json;fhirVersion=4.3.0"))
			.when().get(CODESYSTEM_LOOKUP)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			// Parameter order depends on OperationConvertor_40_50 since we are starting out with an R5 result and converting to R4B
			.body("parameter[0].name", equalTo("name"))
			.body("parameter[0].valueString", equalTo("SNOMEDCT"))
			.body("parameter[1].name", equalTo("version"))
			.body("parameter[1].valueString", equalTo(SNOMEDCT_URL))
			.body("parameter[2].name", equalTo("display"))
			.body("parameter[2].valueString", equalTo("SNOMED CT Concept"));
	}
	
	@Test
	public void GET_CodeSystem_$lookup_Existing_R5() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("system", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.queryParam("version", SNOMEDCT_URL)
			.queryParam("code", Concepts.ROOT_CONCEPT)
			.queryParam("_format", RestExtensions.encodeQueryParameter("application/fhir+json;fhirVersion=5.0.0"))
			.when().get(CODESYSTEM_LOOKUP)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("name"))
			.body("parameter[0].valueString", equalTo("SNOMEDCT"))
			.body("parameter[1].name", equalTo("display"))
			.body("parameter[1].valueString", equalTo("SNOMED CT Concept"))
			.body("parameter[2].name", equalTo("version"))
			.body("parameter[2].valueString", equalTo(SNOMEDCT_URL));
	}
	
	@Test
	public void GET_CodeSystem_$lookup_Existing_Versioned() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("system", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.queryParam("version", SNOMEDCT_URL + "/version/20020131")
			.queryParam("code", Concepts.ROOT_CONCEPT)
			.queryParam("_format", "json")
			.when().get(CODESYSTEM_LOOKUP)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("name"))
			.body("parameter[0].valueString", equalTo("SNOMEDCT/2002-01-31"))
			.body("parameter[1].name", equalTo("display"))
			.body("parameter[1].valueString", equalTo("SNOMED CT Concept"))
			.body("parameter[2].name", equalTo("version"))
			.body("parameter[2].valueString", equalTo(SNOMEDCT_URL + "/version/20020131"));
	}
	
	@Test
	public void GET_CodeSystem_$lookup_Existing_Versioned_ViaVersionField() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("system", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.queryParam("version", SNOMEDCT_URL + "/version/20020131")
			.queryParam("code", Concepts.ROOT_CONCEPT)
			.queryParam("_format", "json")
			.when().get(CODESYSTEM_LOOKUP)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("name"))
			.body("parameter[0].valueString", equalTo("SNOMEDCT/2002-01-31"))
			.body("parameter[1].name", equalTo("display"))
			.body("parameter[1].valueString", equalTo("SNOMED CT Concept"))
			.body("parameter[2].name", equalTo("version"))
			.body("parameter[2].valueString", equalTo(SNOMEDCT_URL + "/version/20020131"));
	}
	
	@Test
	public void GET_CodeSystem_$lookup_Existing_WithProperty() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("system", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.queryParam("version", SNOMEDCT_URL)
			.queryParam("code", CLINICAL_FINDING)
			.queryParam("property", "parent")
			.queryParam("_format", "json")
			.when().get(CODESYSTEM_LOOKUP)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("name"))
			.body("parameter[0].valueString", equalTo("SNOMEDCT"))
			.body("parameter[1].name", equalTo("display"))
			.body("parameter[1].valueString", equalTo("Clinical finding"))
			.body("parameter[2].name", equalTo("version"))
			.body("parameter[2].valueString", equalTo(SNOMEDCT_URL))
			.body("parameter[3].name", equalTo("property"))
			.body("parameter[3].part[0].valueCode", equalTo("parent"))
			.body("parameter[3].part[1].valueCode", equalTo(Concepts.ROOT_CONCEPT))
			.body("parameter[3].part[2].valueString", equalTo("SNOMED CT Concept"));
	}
	
	@Test
	public void GET_CodeSystem_$lookup_Existing_WithInvalidProperty() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("system", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.queryParam("version", SNOMEDCT_URL)
			.queryParam("code", Concepts.ROOT_CONCEPT)
			.queryParam("property", "name")
			.queryParam("property", "http://snomed.info/id/12345")
			.queryParam("_format", "json")
			.when().get(CODESYSTEM_LOOKUP)
			.then().assertThat()
			.statusCode(400)
			.body("resourceType", equalTo("OperationOutcome"))
			.body("issue.severity", hasItem("error"))
			.body("issue.code", hasItem("invalid"))
			.body("issue.diagnostics[0]", containsString("Unrecognized property [http://snomed.info/id/12345]."));
	}
	
	@Test
	public void GET_CodeSystem_$lookup_Designations() throws Exception {
		JsonPath jsonPath = givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("system", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.queryParam("version", SNOMEDCT_URL)
			.queryParam("code", CLINICAL_FINDING)
			.queryParam("property", "designation")
			.queryParam("_format", "json")
			.when().get(CODESYSTEM_LOOKUP)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("name"))
			.body("parameter[0].valueString", equalTo("SNOMEDCT"))
			.body("parameter[1].name", equalTo("display"))
			.body("parameter[1].valueString", equalTo("Clinical finding"))
			.body("parameter[2].name", equalTo("version"))
			.body("parameter[2].valueString", equalTo(SNOMEDCT_URL))
			.extract()
			.jsonPath();
		
		jsonPath.setRootPath("parameter[3]");
		assertThat(jsonPath.getString("name")).isEqualTo("designation");

		assertThat(jsonPath.getString("part[0].name")).isEqualTo("language");
		assertThat(jsonPath.getString("part[0].valueCode")).isEqualTo("en");
		assertThat(jsonPath.getString("part[1].name")).isEqualTo("use");
		assertThat(jsonPath.getString("part[1].valueCoding.code")).isEqualTo(Concepts.FULLY_SPECIFIED_NAME);
		assertThat(jsonPath.getString("part[2].name")).isEqualTo("value");
		assertThat(jsonPath.getString("part[2].valueString")).isEqualTo("Clinical finding (finding)");

		jsonPath.setRootPath("parameter[4]");
		assertThat(jsonPath.getString("name")).isEqualTo("designation");

		assertThat(jsonPath.getString("part[0].name")).isEqualTo("language");
		assertThat(jsonPath.getString("part[0].valueCode")).isEqualTo("en");
		assertThat(jsonPath.getString("part[1].name")).isEqualTo("use");
		assertThat(jsonPath.getString("part[1].valueCoding.code")).isEqualTo(Concepts.SYNONYM);
		assertThat(jsonPath.getString("part[2].name")).isEqualTo("value");
		assertThat(jsonPath.getString("part[2].valueString")).isEqualTo("Clinical finding");
		
		checkDesignationUseContext(jsonPath, "parameter[3].extension[0]", Concepts.REFSET_LANGUAGE_TYPE_UK, Concepts.FULLY_SPECIFIED_NAME);
		checkDesignationUseContext(jsonPath, "parameter[3].extension[1]", Concepts.REFSET_LANGUAGE_TYPE_US, Concepts.FULLY_SPECIFIED_NAME);
		checkDesignationUseContext(jsonPath, "parameter[4].extension[0]", Concepts.REFSET_LANGUAGE_TYPE_UK, Concepts.SYNONYM);
		checkDesignationUseContext(jsonPath, "parameter[4].extension[1]", Concepts.REFSET_LANGUAGE_TYPE_US, Concepts.SYNONYM);
	}
	
	@Test
	public void POST_CodeSystem_$lookup_Existing() throws Exception {
		var parameters = new CodeSystemLookupParameters()
			.setSystem(FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.setVersion(SNOMEDCT_URL)
			.setCode(Concepts.ROOT_CONCEPT);
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.contentType(APPLICATION_FHIR_JSON)
			.body(toJson(parameters.getParameters()))
			.when().post(CODESYSTEM_LOOKUP)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("name"))
			.body("parameter[0].valueString", equalTo("SNOMEDCT"))
			.body("parameter[1].name", equalTo("display"))
			.body("parameter[1].valueString", equalTo("SNOMED CT Concept"))
			.body("parameter[2].name", equalTo("version"))
			.body("parameter[2].valueString", equalTo(SNOMEDCT_URL));
	}
	
	@Test
	public void POST_CodeSystem_$lookup_Existing_Property() throws Exception {
		var parameters = new CodeSystemLookupParameters()
			.setSystem(FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.setVersion(SNOMEDCT_URL)
			.setCode(CLINICAL_FINDING)
			.setProperty(List.of("parent"));
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.contentType(APPLICATION_FHIR_JSON)
			.body(toJson(parameters.getParameters()))
			.when().post(CODESYSTEM_LOOKUP)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("name"))
			.body("parameter[0].valueString", equalTo("SNOMEDCT"))
			.body("parameter[1].name", equalTo("display"))
			.body("parameter[1].valueString", equalTo("Clinical finding"))
			.body("parameter[2].name", equalTo("version"))
			.body("parameter[2].valueString", equalTo(SNOMEDCT_URL))
			.body("parameter[3].name", equalTo("property"))
			.body("parameter[3].part[0].valueCode", equalTo("parent"))
			.body("parameter[3].part[1].valueCode", equalTo(Concepts.ROOT_CONCEPT))
			.body("parameter[3].part[2].valueString", equalTo("SNOMED CT Concept"));
	}
	
	@Test
	public void POST_CodeSystem_$lookup_Existing_WithInvalidProperty() throws Exception {
		var parameters = new CodeSystemLookupParameters()
			.setSystem(FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.setVersion(SNOMEDCT_URL)
			.setCode(Concepts.ROOT_CONCEPT)
			.setProperty(List.of("http://snomed.info/id/12345"));
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.contentType(APPLICATION_FHIR_JSON)
			.body(toJson(parameters.getParameters()))
			.when().post(CODESYSTEM_LOOKUP)
			.then().assertThat()
			.statusCode(400)
			.body("resourceType", equalTo("OperationOutcome"))
			.body("issue.severity", hasItem("error"))
			.body("issue.code", hasItem("invalid"))
			.body("issue.diagnostics[0]", containsString("Unrecognized property [http://snomed.info/id/12345]."));
	}

}