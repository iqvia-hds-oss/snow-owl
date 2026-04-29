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
package com.b2international.snowowl.fhir.rest.tests.capabilitystatement;

import static com.b2international.snowowl.test.commons.rest.RestExtensions.givenAuthenticatedRequest;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.*;
import static com.b2international.snowowl.test.commons.fhir.FhirApiHelpers.*;

import org.hl7.fhir.r5.model.Enumerations.CapabilityStatementKind;
import org.junit.Test;

import com.b2international.snowowl.fhir.rest.tests.FhirRestTest;

/**
 * REST test cases for {@link CapabilityStatement} and the referenced {@link OperationDefinition}s.
 * 
 * @since 8.0.0
 */
public class CapabilityStatementApiTest extends FhirRestTest {
	
	@Test(timeout = 15000)
	public void capabilityStatementTest_4_0() {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.when()
			.accept(APPLICATION_FHIR_JSON_R4)
			.get("metadata")
			.then()
			.assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("CapabilityStatement"))
			.body("url", notNullValue())
			.body("version", notNullValue())
			.body("name", notNullValue())
			.body("title", notNullValue())
			.body("status", notNullValue())
			.body("date", notNullValue())
			.body("description", notNullValue())
			.body("kind", equalTo(CapabilityStatementKind.INSTANCE.toCode()))
			.body("fhirVersion", equalTo("4.0.1"))
			.body("rest[0]", notNullValue())
			.body("rest[0].resource[0]", notNullValue());
	}
	
	@Test(timeout = 15000)
	public void capabilityStatementTest_5_0_0() {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.when().get("metadata")
			.then()
			.assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("CapabilityStatement"))
			.body("url", notNullValue())
			.body("version", notNullValue())
			.body("name", notNullValue())
			.body("title", notNullValue())
			.body("status", notNullValue())
			.body("date", notNullValue())
			.body("description", notNullValue())
			.body("kind", equalTo(CapabilityStatementKind.INSTANCE.toCode()))
			.body("fhirVersion", equalTo("5.0.0"))
			.body("rest[0]", notNullValue())
			.body("rest[0].resource[0]", notNullValue());
	}
	
	@Test(timeout = 15000)
	public void operationDefinitionTest() {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.when()
			.get("OperationDefinition/CodeSystem-it-lookup")
			.then()
			.assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("OperationDefinition"))
			.body("base", equalTo("http://hl7.org/fhir/OperationDefinition/CodeSystem-lookup"));
	}

	@Test(timeout = 15000)
	public void nonExistentOperationDefinitionTest() {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.when()
			.get("OperationDefinition/CodeSystem-it-invalid")
			.then()
			.assertThat()
			.statusCode(404)
			.body("resourceType", equalTo("OperationOutcome"))
			.body("issue[0].code", equalTo("not-found"));
	}
	
	@Test(timeout = 15000)
	public void versions() {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.when()
			.get("$versions")
			.then()
			.assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter.valueCode", hasItems("4.0", "4.3", "5.0", "4.0.1", "4.3.0", "5.0.0"))
			.body("parameter[6].name", equalTo("default"))
			.body("parameter[6].valueCode", equalTo("5.0.0"));
	}

}
