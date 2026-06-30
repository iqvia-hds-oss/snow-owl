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
import static com.b2international.snowowl.test.commons.rest.RestExtensions.generateToken;
import static com.b2international.snowowl.test.commons.rest.RestExtensions.givenAuthenticatedRequest;
import static com.b2international.snowowl.test.commons.rest.RestExtensions.givenRequestWithToken;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.time.LocalDate;
import java.util.Map;

import org.hl7.fhir.r5.model.BooleanType;
import org.hl7.fhir.r5.model.Parameters;
import org.junit.Test;

import com.b2international.snowowl.core.date.DateFormats;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.core.domain.IComponent;
import com.b2international.snowowl.core.identity.Permission;
import com.b2international.snowowl.fhir.rest.tests.FhirRestTest;
import com.b2international.snowowl.test.commons.codesystem.CodeSystemRestRequests;
import com.b2international.snowowl.test.commons.codesystem.CodeSystemVersionRestRequests;
import com.b2international.snowowl.test.commons.rest.BundleApiAssert;

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

	@Test
	public void GET_CodeSystem_$set_include_in_capabilities_EditPermissionOnResource() throws Exception {
		// Assign a FHIR URL first (as admin)
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		// User has edit permission on the code system - operation should succeed
		final String token = generateToken(
			Permission.requireAny(Permission.OPERATION_EDIT, getTestCodeSystemId()),
			Permission.requireAny(Permission.OPERATION_READ, getTestCodeSystemId())
		);

		givenRequestWithToken(FHIR_ROOT_CONTEXT, token)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("includeInCapabilities", true)
			.when().get(CODESYSTEM_ID_SET_INCLUDE_IN_CAPABILITIES)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("result"))
			.body("parameter[0].valueBoolean", equalTo(true));
	}

	@Test
	public void GET_CodeSystem_$set_include_in_capabilities_EditPermissionMissing() throws Exception {
		// Assign a FHIR URL first (as admin)
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		// User has only read permission on the code system, no edit - operation should fail
		final String token = generateToken(
			Permission.requireAny(Permission.OPERATION_READ, getTestCodeSystemId())
		);

		givenRequestWithToken(FHIR_ROOT_CONTEXT, token)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("includeInCapabilities", true)
			.when().get(CODESYSTEM_ID_SET_INCLUDE_IN_CAPABILITIES)
			.then().assertThat()
			.statusCode(403);
	}

	@Test
	public void GET_CodeSystem_$set_include_in_capabilities_EditPermissionOnBundle() throws Exception {
		// Create a bundle and move the code system into it
		final String bundleId = getTestCodeSystemId() + "-bundle";
		
		try {
			BundleApiAssert.createBundle(bundleId);
			CodeSystemRestRequests.updateCodeSystem(getTestCodeSystemId(), Map.of("bundleId", bundleId));
	
			// Assign a FHIR URL first (as admin)
			givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
				.pathParam("id", getTestCodeSystemId())
				.queryParam("fhirUrl", TEST_FHIR_URL)
				.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
				.then().assertThat()
				.statusCode(200);
	
			// User has edit permission on the parent bundle (not the resource directly) - should still work
			final String token = generateToken(
				Permission.requireAny(Permission.OPERATION_EDIT, bundleId)
			);
	
			givenRequestWithToken(FHIR_ROOT_CONTEXT, token)
				.pathParam("id", getTestCodeSystemId())
				.queryParam("includeInCapabilities", true)
				.when().get(CODESYSTEM_ID_SET_INCLUDE_IN_CAPABILITIES)
				.then().assertThat()
				.statusCode(200)
				.body("resourceType", equalTo("Parameters"))
				.body("parameter[0].name", equalTo("result"))
				.body("parameter[0].valueBoolean", equalTo(true));
		} finally {
			CodeSystemRestRequests.updateCodeSystem(getTestCodeSystemId(), Map.of("bundleId", IComponent.ROOT_ID));
			BundleApiAssert.deleteBundle(bundleId);
		}
	}

	@Test
	public void GET_CodeSystem_$set_include_in_capabilities_GroupedByFhirUrl() throws Exception {
		final String secondCodeSystemId = getTestCodeSystemId() + "-2";
		createCodeSystem(secondCodeSystemId);

		final String thirdCodeSystemId = getTestCodeSystemId() + "-3";
		createCodeSystem(thirdCodeSystemId);

		/*
		 * Native URLs become version codes when fhirVersionProperty is "url".
		 * Lexicographic order: firstNativeUrl < secondNativeUrl < thirdNativeUrl (base < base-2 < base-3).
		 * 
		 * These also look like SNOMED CT International Edition versioned URLs, but this
		 * is only due to the way the test code systems are generated.
		 */
		final String firstUrl = CodeSystemRestRequests.getSnomedIntUrl(getTestCodeSystemId());
		final String secondUrl = CodeSystemRestRequests.getSnomedIntUrl(secondCodeSystemId);
		final String thirdUrl = CodeSystemRestRequests.getSnomedIntUrl(thirdCodeSystemId);

		// Assign the same FHIR URL to all three code systems, using their native URL as the version property
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.queryParam("fhirVersionProperty", "url")
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", secondCodeSystemId)
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.queryParam("fhirVersionProperty", "url")
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", thirdCodeSystemId)
			.queryParam("fhirUrl", TEST_FHIR_URL)
			.queryParam("fhirVersionProperty", "url")
			.when().get(CODESYSTEM_ID_ASSIGN_FHIR_URL)
			.then().assertThat()
			.statusCode(200);

		// Enable inclusion in capabilities for all three code systems
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", getTestCodeSystemId())
			.queryParam("includeInCapabilities", true)
			.when().get(CODESYSTEM_ID_SET_INCLUDE_IN_CAPABILITIES)
			.then().assertThat()
			.statusCode(200);

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", secondCodeSystemId)
			.queryParam("includeInCapabilities", true)
			.when().get(CODESYSTEM_ID_SET_INCLUDE_IN_CAPABILITIES)
			.then().assertThat()
			.statusCode(200);

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.pathParam("id", thirdCodeSystemId)
			.queryParam("includeInCapabilities", true)
			.when().get(CODESYSTEM_ID_SET_INCLUDE_IN_CAPABILITIES)
			.then().assertThat()
			.statusCode(200);

		// Verify grouping and auto-default selection in the TerminologyCapabilities response
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("mode", "terminology")
			.when().get(METADATA)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("TerminologyCapabilities"))
			// All three code systems are grouped under the single shared FHIR URL entry
			.body("codeSystem.uri", hasItem(TEST_FHIR_URL))
			.body("codeSystem.find { it.uri == '" + TEST_FHIR_URL + "' }.version.code",
				containsInAnyOrder(firstUrl, secondUrl, thirdUrl))
			// The version whose code is the lexicographically largest native URL is automatically set as default
			.body("codeSystem.find { it.uri == '" + TEST_FHIR_URL + "' }.version.find { it.isDefault }.code",
				equalTo(thirdUrl));
	}
}
