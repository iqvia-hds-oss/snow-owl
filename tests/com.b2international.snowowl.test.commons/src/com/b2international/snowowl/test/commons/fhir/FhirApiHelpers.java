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

import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.model.Resource;

/**
 * @since 10.1.0
 */
public final class FhirApiHelpers {

	public static final String FHIR_ROOT_CONTEXT = "/fhir"; //$NON-NLS-N$
	public static final String APPLICATION_FHIR_JSON = "application/fhir+json"; //$NON-NLS-N$
	public static final String APPLICATION_FHIR_JSON_R4 = "application/fhir+json; fhirVersion=4.0"; //$NON-NLS-N$
	public static final String APPLICATION_FHIR_JSON_R4_0_1 = "application/fhir+json;fhirVersion=4.0.1"; //$NON-NLS-N$
	public static final String APPLICATION_FHIR_JSON_R4_3_0 = "application/fhir+json;fhirVersion=4.3.0"; //$NON-NLS-N$
	public static final String APPLICATION_FHIR_JSON_R5_0_0 = "application/fhir+json;fhirVersion=5.0.0"; //$NON-NLS-N$
	
	public static final String CODESYSTEM = "/CodeSystem";
	public static final String CODESYSTEM_ID = "/CodeSystem/{id}";
	public static final String CODESYSTEM_LOOKUP = "/CodeSystem/$lookup";
	public static final String CODESYSTEM_SUBSUMES = "/CodeSystem/$subsumes";
	public static final String CODESYSTEM_VALIDATE_CODE = "/CodeSystem/$validate-code";

	public static final String VALUESET = "/ValueSet";
	public static final String VALUESET_ID = "/ValueSet/{id}";

	public static final String LOAD_PACKAGE = "/$load-package";
	
	public static final String toJson(Resource resource) throws Exception {
		return new JsonParser().composeString(resource);
	}
	
	public static final <T> T fromJson(String resource) throws Exception {
		return (T) new JsonParser().parse(resource);
	}

}
