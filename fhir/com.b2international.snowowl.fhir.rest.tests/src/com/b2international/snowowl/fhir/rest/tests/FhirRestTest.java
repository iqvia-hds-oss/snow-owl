/*
 * Copyright 2011-2024 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.fhir.rest.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants;
import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts;
import com.b2international.snowowl.test.commons.TestMethodNameRule;
import com.b2international.snowowl.test.commons.codesystem.CodeSystemRestRequests;

import io.restassured.path.json.JsonPath;

/**
 * Superclass for common REST-related test functionality. All tests receive a single Code System to test/verify/use as test fixture. The CodeSystemId
 * can be accessed with the {@link #getTestCodeSystemId()} method and it is available at the start of the test and will be removed once the test
 * completes. Extra CodeSystems can be requested with the {@link #createCodeSystem(String)} and those will be removed at the end of the test as well.
 * 
 * @since 6.9
 */
public abstract class FhirRestTest extends FhirTest {
	
	protected static final String APPLICATION_FHIR_JSON = "application/fhir+json";
	
	public static final String FHIR_ROOT_CONTEXT = "/fhir"; //$NON-NLS-N$
	
	public static final String SNOMEDCT_URL = SnomedTerminologyComponentConstants.SNOMED_URI_SCT + "/900000000000207008";
	
	@Rule
	public TestMethodNameRule methodNameRule = new TestMethodNameRule();
	
	public static final class Endpoints {
		public static final String CODESYSTEM = "/CodeSystem";
		public static final String CODESYSTEM_ID = "/CodeSystem/{id}";
		public static final String CODESYSTEM_LOOKUP = "/CodeSystem/$lookup";
		public static final String CODESYSTEM_SUBSUMES = "/CodeSystem/$subsumes";
		public static final String CODESYSTEM_VALIDATE_CODE = "/CodeSystem/$validate-code";

		public static final String VALUESET = "/ValueSet";
		public static final String VALUESET_ID = "/ValueSet/{id}";

	}
	
	protected final String getTestCodeSystemId() {
		return methodNameRule.get().replaceAll("\\$", "");
	}
	
	private final Set<String> createdCodeSystems = new HashSet<>(3); 
	
	@Before
	public void before() {
		createCodeSystem(getTestCodeSystemId());
	}
	
	protected final String createCodeSystem(String codeSystemId) {
		CodeSystemRestRequests.createCodeSystem(codeSystemId);
		createdCodeSystems.add(codeSystemId);
		return codeSystemId;
	}

	@After
	public void after() {
		for (String codeSystemId : createdCodeSystems) {
			CodeSystemRestRequests.deleteCodeSystem(codeSystemId).statusCode(204);
		}
	}
	
	protected final String getTestCodeSystemUrl() {
		return CodeSystemRestRequests.getSnomedIntUrl(getTestCodeSystemId());
	}

	protected final static void checkDesignationUseContext(JsonPath jsonPath, String rootPath, String refsetId, String typeId) {
		jsonPath.setRootPath(rootPath);
		assertThat(jsonPath.getString("url")).isEqualTo("http://snomed.info/fhir/StructureDefinition/designation-use-context");
		
		assertThat(jsonPath.getString("extension[0].url")).isEqualTo("context");
		assertThat(jsonPath.getString("extension[0].valueCoding.code")).isEqualTo(refsetId);
		assertThat(jsonPath.getString("extension[1].url")).isEqualTo("role");
		assertThat(jsonPath.getString("extension[1].valueCoding.code")).isEqualTo(Concepts.REFSET_DESCRIPTION_ACCEPTABILITY_PREFERRED);
		assertThat(jsonPath.getString("extension[2].url")).isEqualTo("type");
		assertThat(jsonPath.getString("extension[2].valueCoding.code")).isEqualTo(typeId);
	}
}
