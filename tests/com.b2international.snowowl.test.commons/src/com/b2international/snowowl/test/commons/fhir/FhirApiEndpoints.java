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
package com.b2international.snowowl.test.commons.fhir;

/**
 * @since 10.1.0
 */
public interface FhirApiEndpoints {

	String FHIR_ROOT_CONTEXT = "/fhir"; //$NON-NLS-N$
	String APPLICATION_FHIR_JSON = "application/fhir+json"; //$NON-NLS-N$
	String APPLICATION_FHIR_JSON_R4 = "application/fhir+json; fhirVersion=4.0"; //$NON-NLS-N$
	String APPLICATION_FHIR_JSON_R4_0_1 = "application/fhir+json;fhirVersion=4.0.1"; //$NON-NLS-N$
	String APPLICATION_FHIR_JSON_R4_3_0 = "application/fhir+json;fhirVersion=4.3.0"; //$NON-NLS-N$
	String APPLICATION_FHIR_JSON_R5_0_0 = "application/fhir+json;fhirVersion=5.0.0"; //$NON-NLS-N$
	
	String CODESYSTEM = "/CodeSystem";
	String CODESYSTEM_ID = "/CodeSystem/{id}";
	String CODESYSTEM_LOOKUP = "/CodeSystem/$lookup";
	String CODESYSTEM_SUBSUMES = "/CodeSystem/$subsumes";
	String CODESYSTEM_VALIDATE_CODE = "/CodeSystem/$validate-code";

	String VALUESET = "/ValueSet";
	String VALUESET_ID = "/ValueSet/{id}";

	String LOAD_PACKAGE = "/$load-package";

}
