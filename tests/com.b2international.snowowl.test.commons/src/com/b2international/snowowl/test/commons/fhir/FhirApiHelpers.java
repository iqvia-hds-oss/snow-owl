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
	public static final String CODESYSTEM_ID_HISTORY = "/CodeSystem/{id}/_history";
	public static final String CODESYSTEM_ID_HISTORY_VERSION = "/CodeSystem/{id}/_history/{version}";
	
	public static final String CODESYSTEM_LOOKUP = "/CodeSystem/$lookup";
	public static final String CODESYSTEM_SUBSUMES = "/CodeSystem/$subsumes";
	public static final String CODESYSTEM_VALIDATE_CODE = "/CodeSystem/$validate-code";
	
	public static final String CODESYSTEM_ID_ASSIGN_FHIR_URL = "/CodeSystem/{id}/$assign-fhir-url";
	public static final String CODESYSTEM_ID_REMOVE_FHIR_URL = "/CodeSystem/{id}/$remove-fhir-url";
	public static final String CODESYSTEM_ID_SET_AS_DEFAULT = "/CodeSystem/{id}/$set-as-default";
	public static final String CODESYSTEM_ID_SET_INCLUDE_IN_CAPABILITIES = "/CodeSystem/{id}/$set-include-in-capabilities";
	public static final String CODESYSTEM_INITIALIZE_FHIR_URLS = "/CodeSystem/$initialize-fhir-urls";
	
	public static final String VALUESET = "/ValueSet";
	public static final String VALUESET_ID = "/ValueSet/{id}";
	public static final String VALUESET_ID_HISTORY = "/ValueSet/{id}/_history";
	public static final String VALUESET_ID_HISTORY_VERSION = "/ValueSet/{id}/_history/{version}";
	
	public static final String VALUESET_EXPAND = "/ValueSet/$expand";
	public static final String VALUESET_ID_EXPAND = "/ValueSet/{id}/$expand";
	public static final String VALUESET_VALIDATE_CODE = "/ValueSet/$validate-code";
	public static final String VALUESET_ID_VALIDATE_CODE = "/ValueSet/{id}/$validate-code";
	
	public static final String CONCEPTMAP = "/ConceptMap";
	public static final String CONCEPTMAP_ID = "/ConceptMap/{id}";
	public static final String CONCEPTMAP_ID_HISTORY = "/ConceptMap/{id}/_history";
	public static final String CONCEPTMAP_ID_HISTORY_VERSION = "/ConceptMap/{id}/_history/{version}";
	
	public static final String CONCEPTMAP_TRANSLATE = "/ConceptMap/$translate";
	public static final String CONCEPTMAP_ID_TRANSLATE = "/ConceptMap/{id}/$translate";

	public static final String LOAD_PACKAGE = "/$load-package";

	public static final String METADATA = "/metadata";
	
	public static final String toJson(Resource resource) throws Exception {
		return new JsonParser().composeString(resource);
	}
	
	public static final <T> T fromJson(String resource) throws Exception {
		return (T) new JsonParser().parse(resource);
	}

}
