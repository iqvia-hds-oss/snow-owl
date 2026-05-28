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
package com.b2international.snowowl.fhir.rest.tests.codesystem;

import static com.b2international.snowowl.test.commons.fhir.FhirApiHelpers.*;
import static com.b2international.snowowl.test.commons.rest.RestExtensions.givenAuthenticatedRequest;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;

import java.time.LocalDate;

import org.hl7.fhir.r5.model.Parameters;
import org.hl7.fhir.r5.model.StringType;
import org.junit.Test;

import com.b2international.snowowl.core.date.DateFormats;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.fhir.rest.tests.FhirRestTest;
import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts;
import com.b2international.snowowl.test.commons.codesystem.CodeSystemVersionRestRequests;

/**
 * REST test cases for CodeSystem administrative operations ($assign-fhir-url, $remove-fhir-url and $set-as-default).
 * 
 * @since 10.2.0
 */
public class FhirCodeSystemUrlOperationTests extends FhirRestTest {

	private static final String TEST_FHIR_URL = "http://example.com/fhir/CodeSystem/test";

	@Test
	public void GET_CodeSystem_$assign_fhir_url() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true));

		// After assigning a FHIR URL, the code system should no longer be visible via its native URL
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", getTestCodeSystemUrl())
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("total", equalTo(0));
		
		// ...however the assigned FHIR URL should now resolve to the code system
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", TEST_FHIR_URL)
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("total", equalTo(1))
			.body("entry[0].resource.url", equalTo(TEST_FHIR_URL));
	}

	@Test
	public void GET_CodeSystem_$assign_fhir_url_WithVersionProperty() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.queryParam("fhirVersionProperty", "url")
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true));
		
		// Create a new version for the code system as well to verify URL sharing across versions
		final LocalDate effectiveTime = EffectiveTimes.parse("20260527", DateFormats.SHORT);
		CodeSystemVersionRestRequests.createVersion(getTestCodeSystemId(), "2.0.0", effectiveTime)
			.statusCode(201);

		// After assigning a FHIR URL, the code system should no longer be visible via its native URL
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", getTestCodeSystemUrl())
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("total", equalTo(0));
		
		// ...however the assigned FHIR URL should now resolve to the code system and its versioned variant
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", TEST_FHIR_URL)
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("total", equalTo(2))
			.body("entry.resource.url", everyItem(equalTo(TEST_FHIR_URL)))
			.body("entry.resource.version", containsInAnyOrder(
				getTestCodeSystemUrl(), // native URL from the resource document representing HEAD
				getTestCodeSystemUrl() + "/version/" + EffectiveTimes.format(effectiveTime, DateFormats.SHORT))); // versioned URL from the version document
	}

	@Test
	public void GET_CodeSystem_$assign_fhir_url_NonExistingCodeSystem() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", "non-existing-code-system")
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(404);
	}

	@Test
	public void POST_CodeSystem_$assign_fhir_url() throws Exception {
		var parameters = new Parameters()
			.addParameter("fhirUrl", new StringType(TEST_FHIR_URL));

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.contentType(APPLICATION_FHIR_JSON)
			.body(toJson(parameters))
			.when().post(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true));

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", getTestCodeSystemUrl())
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("total", equalTo(0));
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", TEST_FHIR_URL)
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("total", equalTo(1))
			.body("entry[0].resource.url", equalTo(TEST_FHIR_URL));
	}

	@Test
	public void POST_CodeSystem_$assign_fhir_url_WithVersionProperty() throws Exception {

		var parameters = new Parameters()
			.addParameter("fhirUrl", new StringType(TEST_FHIR_URL))
			.addParameter("fhirVersionProperty", new StringType("url"));
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.contentType(APPLICATION_FHIR_JSON)
			.body(toJson(parameters))
			.when().post(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true));

		final LocalDate effectiveTime = EffectiveTimes.parse("20260528", DateFormats.SHORT);
		CodeSystemVersionRestRequests.createVersion(getTestCodeSystemId(), "3.0.0", effectiveTime)
			.statusCode(201);
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", getTestCodeSystemUrl())
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("total", equalTo(0));
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", TEST_FHIR_URL)
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("total", equalTo(2))
			.body("entry.resource.url", everyItem(equalTo(TEST_FHIR_URL)))
			.body("entry.resource.version", containsInAnyOrder(
				getTestCodeSystemUrl(), 
				getTestCodeSystemUrl() + "/version/" + EffectiveTimes.format(effectiveTime, DateFormats.SHORT)));
	}

	@Test
	public void POST_CodeSystem_$assign_fhir_url_NonExistingCodeSystem() throws Exception {
		var parameters = new Parameters()
			.addParameter("fhirUrl", new StringType(TEST_FHIR_URL));

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", "non-existing-code-system")
			.contentType(APPLICATION_FHIR_JSON)
			.body(toJson(parameters))
			.when().post(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(404);
	}

	@Test
	public void GET_CodeSystem_$remove_fhir_url() throws Exception {
		// First assign a FHIR URL
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		// Verify the code system is no longer visible via native URL
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", getTestCodeSystemUrl())
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("total", equalTo(0));

		// Now remove the FHIR URL
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.when().get(CODESYSTEM_ID_REMOVE_FHIR_URL)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true));

		// After removing the FHIR URL, the code system should be visible again via its native URL
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", getTestCodeSystemUrl())
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("total", equalTo(1))
			.body("entry[0].resource.url", equalTo(getTestCodeSystemUrl()));
	}

	@Test
	public void GET_CodeSystem_$remove_fhir_url_NonExistingCodeSystem() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", "non-existing-code-system")
			.when().get(CODESYSTEM_ID_REMOVE_FHIR_URL)
			.then().assertThat()
			.statusCode(404);
	}

	@Test
	public void POST_CodeSystem_$remove_fhir_url() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", getTestCodeSystemUrl())
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("total", equalTo(0));

		var parameters = new Parameters();

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.contentType(APPLICATION_FHIR_JSON)
			.body(toJson(parameters))
			.when().post(CODESYSTEM_ID_REMOVE_FHIR_URL)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true));

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", getTestCodeSystemUrl())
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("total", equalTo(1))
			.body("entry[0].resource.url", equalTo(getTestCodeSystemUrl()));
	}

	@Test
	public void POST_CodeSystem_$remove_fhir_url_NonExistingCodeSystem() throws Exception {
		var parameters = new Parameters();

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", "non-existing-code-system")
			.contentType(APPLICATION_FHIR_JSON)
			.body(toJson(parameters))
			.when().post(CODESYSTEM_ID_REMOVE_FHIR_URL)
			.then().assertThat()
			.statusCode(404);
	}

	@Test
	public void GET_CodeSystem_$set_as_default() throws Exception {
		// Add a second code system with the same FHIR URL
		final String secondaryCodeSystemId = getTestCodeSystemId() + "-2";
		createCodeSystem(secondaryCodeSystemId);
		
		// Assign the same FHIR URL to both code systems
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", secondaryCodeSystemId)
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		// Now set the first code system as default for its URL
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.when().get(CODESYSTEM_ID_SET_AS_DEFAULT)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true));
		
		/*
		 * After setting the first code system as default, it should be used in
		 * CodeSystem operations like $lookup if no other selection criteria is provided
		 */
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("system", TEST_FHIR_URL)
			.queryParam("code", Concepts.ROOT_CONCEPT)
			.queryParam("_format", "json")
			.when().get(CODESYSTEM_LOOKUP)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("name"))
			.body("parameter[0].valueString", equalTo(getTestCodeSystemId()));
	}

	@Test
	public void GET_CodeSystem_$set_as_default_NonExistingCodeSystem() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", "non-existing-code-system")
			.when().get(CODESYSTEM_ID_SET_AS_DEFAULT)
			.then().assertThat()
			.statusCode(404);
	}

	@Test
	public void POST_CodeSystem_$set_as_default() throws Exception {
		final String secondaryCodeSystemId = getTestCodeSystemId() + "-2";
		createCodeSystem(secondaryCodeSystemId);
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", secondaryCodeSystemId)
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);
		
		var parameters = new Parameters();

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.contentType(APPLICATION_FHIR_JSON)
			.body(toJson(parameters))
			.when().post(CODESYSTEM_ID_SET_AS_DEFAULT)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true));

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("system", TEST_FHIR_URL)
			.queryParam("code", Concepts.ROOT_CONCEPT)
			.queryParam("_format", "json")
			.when().get(CODESYSTEM_LOOKUP)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("name"))
			.body("parameter[0].valueString", equalTo(getTestCodeSystemId()));
	}

	@Test
	public void POST_CodeSystem_$set_as_default_NonExistingCodeSystem() throws Exception {
		var parameters = new Parameters();

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", "non-existing-code-system")
			.contentType(APPLICATION_FHIR_JSON)
			.body(toJson(parameters))
			.when().post(CODESYSTEM_ID_SET_AS_DEFAULT)
			.then().assertThat()
			.statusCode(404);
	}

	@Test
	public void GET_CodeSystem_$assign_fhir_url_ExistingVersion() throws Exception {
		// Create a version BEFORE assigning a FHIR URL to cover the updateVersionDocument path
		final LocalDate effectiveTime = EffectiveTimes.parse("20260101", DateFormats.SHORT);
		CodeSystemVersionRestRequests.createVersion(getTestCodeSystemId(), "1.0.0", effectiveTime)
			.statusCode(201);

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true));

		// Verify the FHIR URL resolves to the code system and its version
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", TEST_FHIR_URL)
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("total", equalTo(2))
			.body("entry.resource.url", everyItem(equalTo(TEST_FHIR_URL)))
			.body("entry.resource.version", containsInAnyOrder(
				getTestCodeSystemUrl(), 
				getTestCodeSystemUrl() + "/version/" + EffectiveTimes.format(effectiveTime, DateFormats.SHORT)
			));
	}

	@Test
	public void GET_CodeSystem_$assign_fhir_url_AlreadyAssigned() throws Exception {
		// Assign the FHIR URL once
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200)
			.body("parameter[0].valueBoolean", equalTo(true));

		// Assign the same FHIR URL again - should return false (no changes made)
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(false));
	}

	@Test
	public void GET_CodeSystem_$assign_fhir_url_ConflictingVersions() throws Exception {
		// Create two code systems and assign the same FHIR URL
		final String secondaryCodeSystemId = getTestCodeSystemId() + "-2";
		createCodeSystem(secondaryCodeSystemId);

		// Also create a version for each code system with the same version ID
		LocalDate effectiveTime = EffectiveTimes.parse("20260201", DateFormats.SHORT);
		CodeSystemVersionRestRequests.createVersion(getTestCodeSystemId(), "1.0.0", effectiveTime)
			.statusCode(201);

		effectiveTime = EffectiveTimes.parse("20260202", DateFormats.SHORT);
		CodeSystemVersionRestRequests.createVersion(secondaryCodeSystemId, "1.0.0", effectiveTime)
			.statusCode(201);
		
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.queryParam("fhirVersionProperty", "version")
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		// Assigning the same URL without a distinguishing version property should trigger a 400 conflict
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", secondaryCodeSystemId)
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.queryParam("fhirVersionProperty", "version")
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(400)
			.body("resourceType", equalTo("OperationOutcome"))
			.body("issue[0].diagnostics", containsString("unique effective versions"));
	}

	@Test
	public void GET_CodeSystem_$remove_fhir_url_NoUrlAssigned() throws Exception {
		// The test code system is already configured with a FHIR URL so it needs to be removed first
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.when().get(CODESYSTEM_ID_REMOVE_FHIR_URL)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))			
			.body("parameter[0].valueBoolean", equalTo(true));
		
		// Removing a FHIR URL a second time (when none is assigned) should return false however!
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.when().get(CODESYSTEM_ID_REMOVE_FHIR_URL)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(false));
	}

	@Test
	public void GET_CodeSystem_$remove_fhir_url_ExistingVersion() throws Exception {
		// Create a version BEFORE assigning and then removing a FHIR URL to cover version update paths
		final LocalDate effectiveTime = EffectiveTimes.parse("20260102", DateFormats.SHORT);
		CodeSystemVersionRestRequests.createVersion(getTestCodeSystemId(), "1.0.0", effectiveTime)
			.statusCode(201);

		// Assign a FHIR URL (which propagates to the version)
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		// Now remove the FHIR URL (which also propagates to the version)
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.when().get(CODESYSTEM_ID_REMOVE_FHIR_URL)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true));

		// After removing, the code system should be visible via its native URL again
		// Since the version document now has a distinct URL (with version), it should not appear in the search results
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", getTestCodeSystemUrl())
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("total", equalTo(1));

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", getTestCodeSystemUrl() + "/version/" + EffectiveTimes.format(effectiveTime, DateFormats.SHORT))
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("total", equalTo(1));
	}

	@Test
	public void GET_CodeSystem_$set_as_default_AlreadyDefault() throws Exception {
		final String secondaryCodeSystemId = getTestCodeSystemId() + "-2";
		createCodeSystem(secondaryCodeSystemId);

		// Assign same FHIR URL with version property to avoid conflict
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.queryParam("fhirVersionProperty", "url")
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", secondaryCodeSystemId)
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.queryParam("fhirVersionProperty", "url")
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		// Set as default the first time
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.when().get(CODESYSTEM_ID_SET_AS_DEFAULT)
			.then().assertThat()
			.statusCode(200)
			.body("parameter[0].valueBoolean", equalTo(true));

		// Set as default again - should return false (already default)
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.when().get(CODESYSTEM_ID_SET_AS_DEFAULT)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(false));
	}

	@Test
	public void GET_CodeSystem_$set_as_default_WithExistingVersion() throws Exception {
		final String secondaryCodeSystemId = getTestCodeSystemId() + "-2";
		createCodeSystem(secondaryCodeSystemId);

		// Create a version BEFORE set-as-default to cover updateVersionDocument path
		final LocalDate effectiveTime = EffectiveTimes.parse("20260103", DateFormats.SHORT);
		CodeSystemVersionRestRequests.createVersion(getTestCodeSystemId(), "1.0.0", effectiveTime)
			.statusCode(201);

		// Assign same FHIR URL with version property
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.queryParam("fhirVersionProperty", "url")
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", secondaryCodeSystemId)
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.queryParam("fhirVersionProperty", "url")
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		// Set as default - this should propagate to the version document
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.when().get(CODESYSTEM_ID_SET_AS_DEFAULT)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true));

		// Verify lookup still works with version-aware default
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("system", TEST_FHIR_URL)
			.queryParam("code", Concepts.ROOT_CONCEPT)
			.queryParam("_format", "json")
			.when().get(CODESYSTEM_LOOKUP)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("name"))
			.body("parameter[0].valueString", equalTo(getTestCodeSystemId() + "/1.0.0"));
	}
}
