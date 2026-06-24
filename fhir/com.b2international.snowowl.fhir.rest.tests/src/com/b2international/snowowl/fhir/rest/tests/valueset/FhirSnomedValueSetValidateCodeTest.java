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

import static com.b2international.snowowl.test.commons.fhir.FhirApiHelpers.FHIR_ROOT_CONTEXT;
import static com.b2international.snowowl.test.commons.fhir.FhirApiHelpers.VALUESET_VALIDATE_CODE;
import static com.b2international.snowowl.test.commons.rest.RestExtensions.givenAuthenticatedRequest;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

import org.junit.Test;

import com.b2international.snowowl.fhir.rest.tests.FhirRestTest;
import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants;

/**
 * @since 10.1.0
 */
public class FhirSnomedValueSetValidateCodeTest extends FhirRestTest {

	@Test
	public void snomedImplicitValueSet_ValidateCode_NoCodeParameter() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", SNOMEDCT_URL + "?fhir_vs")
			.when().get(VALUESET_VALIDATE_CODE)
			.then()
			.statusCode(400);
	}
	
	@Test
	public void snomedImplicitValueSet_ValidateCode() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", SNOMEDCT_URL + "?fhir_vs")
			.queryParam("code", "103335007")
			.when().get(VALUESET_VALIDATE_CODE)
			.then()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter.find { it.name == 'result' }.valueBoolean", equalTo(true))
			.body("parameter.find { it.name == 'system' }.valueUri", equalTo(SnomedTerminologyComponentConstants.SNOMED_URI_SCT))
			.body("parameter.find { it.name == 'version' }.valueString", equalTo(SNOMEDCT_URL))
			.body("parameter.find { it.name == 'message' }.valueString", equalTo("OK"));
	}
	
	@Test
	public void snomedImplicitValueSet_ValidateCode_CodeNotPresentInValueSet() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", SNOMEDCT_URL + "?fhir_vs=isa/105590001")
			.queryParam("code", Concepts.ROOT_CONCEPT) // root is no child of others, so this should not be available
			.when().get(VALUESET_VALIDATE_CODE)
			.then()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter.find { it.name == 'result' }.valueBoolean", equalTo(false))
			// System and version were inferred from the implicit url
			.body("parameter.find { it.name == 'system' }.valueUri", equalTo(SnomedTerminologyComponentConstants.SNOMED_URI_SCT))
			.body("parameter.find { it.name == 'version' }.valueString", equalTo(SNOMEDCT_URL))
			.body("parameter.find { it.name == 'message' }.valueString", containsString("Could not find code '138875005' in ValueSet 'http://snomed.info/sct/900000000000207008?fhir_vs=isa/105590001'"));
	}
	
}
