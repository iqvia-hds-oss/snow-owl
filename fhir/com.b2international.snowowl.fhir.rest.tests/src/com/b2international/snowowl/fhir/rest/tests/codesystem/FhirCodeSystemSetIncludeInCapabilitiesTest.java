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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.time.LocalDate;

import org.hl7.fhir.r5.model.BooleanType;
import org.hl7.fhir.r5.model.Parameters;
import org.junit.Test;

import com.b2international.snowowl.core.date.DateFormats;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.fhir.rest.tests.FhirRestTest;
import com.b2international.snowowl.test.commons.codesystem.CodeSystemVersionRestRequests;

/**
 * REST test cases for CodeSystem <code>$set-include-in-capabilities</code> operation, verified
 * via the <code>GET /{fhir_base}/metadata?mode=terminology</code> (TerminologyCapabilities) endpoint.
 * 
 * @since 10.2.0
 */
public class FhirCodeSystemSetIncludeInCapabilitiesTest extends FhirRestTest {

	private static final String TEST_FHIR_URL = "http://example.com/fhir/CodeSystem/capabilities-test";

	@Test
	public void GET_CodeSystem_$set_include_in_capabilities_true() throws Exception {
		// Assign a FHIR URL
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		// Set include in capabilities to true
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("includeInCapabilities", true)
			.when().get(CODESYSTEM_ID_SET_INCLUDE_IN_CAPABILITIES)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true));

		// Verify the code system appears in the TerminologyCapabilities response
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("mode", "terminology")
			.when().get(METADATA)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("TerminologyCapabilities"))
			.body("codeSystem.uri", hasItem(TEST_FHIR_URL));
	}

	@Test
	public void GET_CodeSystem_$set_include_in_capabilities_false() throws Exception {
		// Assign a FHIR URL
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		// Set include in capabilities to true first
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("includeInCapabilities", true)
			.when().get(CODESYSTEM_ID_SET_INCLUDE_IN_CAPABILITIES)
			.then().assertThat()
			.statusCode(200);

		// Now set include in capabilities to false
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("includeInCapabilities", false)
			.when().get(CODESYSTEM_ID_SET_INCLUDE_IN_CAPABILITIES)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true));

		// Verify the code system no longer appears in the TerminologyCapabilities response
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("mode", "terminology")
			.when().get(METADATA)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("TerminologyCapabilities"))
			.body("codeSystem.uri", not(hasItem(TEST_FHIR_URL)));
	}

	@Test
	public void GET_CodeSystem_$set_include_in_capabilities_NonExistingCodeSystem() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", "non-existing-code-system")
			.queryParam("includeInCapabilities", true)
			.when().get(CODESYSTEM_ID_SET_INCLUDE_IN_CAPABILITIES)
			.then().assertThat()
			.statusCode(404);
	}

	@Test
	public void POST_CodeSystem_$set_include_in_capabilities_true() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		var parameters = new Parameters()
			.addParameter("includeInCapabilities", new BooleanType(true));

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.contentType(APPLICATION_FHIR_JSON)
			.body(toJson(parameters))
			.when().post(CODESYSTEM_ID_SET_INCLUDE_IN_CAPABILITIES)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true));

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("mode", "terminology")
			.when().get(METADATA)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("TerminologyCapabilities"))
			.body("codeSystem.uri", hasItem(TEST_FHIR_URL));
	}

	@Test
	public void POST_CodeSystem_$set_include_in_capabilities_false() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("includeInCapabilities", true)
			.when().get(CODESYSTEM_ID_SET_INCLUDE_IN_CAPABILITIES)
			.then().assertThat()
			.statusCode(200);

		var parameters = new Parameters()
			.addParameter("includeInCapabilities", new BooleanType(false));

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.contentType(APPLICATION_FHIR_JSON)
			.body(toJson(parameters))
			.when().post(CODESYSTEM_ID_SET_INCLUDE_IN_CAPABILITIES)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true));

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("mode", "terminology")
			.when().get(METADATA)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("TerminologyCapabilities"))
			.body("codeSystem.uri", not(hasItem(TEST_FHIR_URL)));
	}

	@Test
	public void POST_CodeSystem_$set_include_in_capabilities_NonExistingCodeSystem() throws Exception {
		var parameters = new Parameters()
			.addParameter("includeInCapabilities", new BooleanType(true));

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", "non-existing-code-system")
			.contentType(APPLICATION_FHIR_JSON)
			.body(toJson(parameters))
			.when().post(CODESYSTEM_ID_SET_INCLUDE_IN_CAPABILITIES)
			.then().assertThat()
			.statusCode(404);
	}

	@Test
	public void GET_CodeSystem_$set_include_in_capabilities_NoChanges() throws Exception {
		// Assign a FHIR URL first
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		// Set include in capabilities to true
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("includeInCapabilities", true)
			.when().get(CODESYSTEM_ID_SET_INCLUDE_IN_CAPABILITIES)
			.then().assertThat()
			.statusCode(200)
			.body("parameter[0].valueBoolean", equalTo(true));

		// Set the same value again - should return false (nothing changed)
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("includeInCapabilities", true)
			.when().get(CODESYSTEM_ID_SET_INCLUDE_IN_CAPABILITIES)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(false));
	}

	@Test
	public void GET_CodeSystem_$set_include_in_capabilities_SetExistingVersion() throws Exception {
		// Create a version BEFORE the operation
		final LocalDate effectiveTime = EffectiveTimes.parse("20260104", DateFormats.SHORT);
		CodeSystemVersionRestRequests.createVersion(getTestCodeSystemId(), "1.0.0", effectiveTime)
			.statusCode(201);

		// Assign a FHIR URL
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		// Set include in capabilities to true - should propagate to the version document
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("includeInCapabilities", true)
			.when().get(CODESYSTEM_ID_SET_INCLUDE_IN_CAPABILITIES)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true));

		// Verify the code system appears in the TerminologyCapabilities response
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("mode", "terminology")
			.when().get(METADATA)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("TerminologyCapabilities"))
			.body("codeSystem[0].uri", equalTo(TEST_FHIR_URL))
			.body("codeSystem[0].version.code", containsInAnyOrder(
				getTestCodeSystemUrl(),
				getTestCodeSystemUrl() + "/version/" + EffectiveTimes.format(effectiveTime, DateFormats.SHORT)
			));
	}

	@Test
	public void GET_CodeSystem_$set_include_in_capabilities_UnsetExistingVersion() throws Exception {
		// Create a version BEFORE the operation
		final LocalDate effectiveTime = EffectiveTimes.parse("20260105", DateFormats.SHORT);
		CodeSystemVersionRestRequests.createVersion(getTestCodeSystemId(), "1.0.0", effectiveTime)
			.statusCode(201);

		// Assign a FHIR URL
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		// Set include in capabilities to true first
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("includeInCapabilities", true)
			.when().get(CODESYSTEM_ID_SET_INCLUDE_IN_CAPABILITIES)
			.then().assertThat()
			.statusCode(200);

		// Now set to false - should propagate to the version document
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("includeInCapabilities", false)
			.when().get(CODESYSTEM_ID_SET_INCLUDE_IN_CAPABILITIES)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true));

		// Verify the code system no longer appears in the TerminologyCapabilities response
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("mode", "terminology")
			.when().get(METADATA)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("TerminologyCapabilities"))
			.body("codeSystem.uri", not(hasItem(TEST_FHIR_URL)));
	}
}
