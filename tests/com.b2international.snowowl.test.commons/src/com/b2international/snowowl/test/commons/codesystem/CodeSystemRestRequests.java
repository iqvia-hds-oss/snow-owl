/*
 * Copyright 2011-2025 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.test.commons.codesystem;

import static com.b2international.snowowl.test.commons.rest.RestExtensions.assertCreated;
import static com.b2international.snowowl.test.commons.rest.RestExtensions.givenAuthenticatedRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.b2international.commons.json.Json;
import com.b2international.snowowl.core.Dependency;
import com.b2international.snowowl.core.Resource;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.TerminologyResource;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.core.branch.Branch;
import com.b2international.snowowl.core.codesystem.CodeSystem;
import com.b2international.snowowl.core.codesystem.CodeSystems;
import com.b2international.snowowl.core.repository.RepositoryRequests;
import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants;
import com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants.Settings;
import com.b2international.snowowl.test.commons.ApiTestConstants;
import com.b2international.snowowl.test.commons.Services;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

/**
 * Common requests for working with Code Systems (only SNOMED CT is supported).
 * 
 * @since 4.7
 */
public abstract class CodeSystemRestRequests {
	
	public static String createCodeSystem(String codeSystemId) {
		return assertCreated(assertCreateCodeSystem(codeSystemId));
	}
	
	public static ValidatableResponse assertCreateCodeSystem(String codeSystemId) {
		String branchPath = RepositoryRequests.branching().prepareCreate()
			.setParent(Branch.MAIN_PATH)
			.setName(codeSystemId)
			.build(SnomedTerminologyComponentConstants.TOOLING_ID)
			.execute(Services.bus())
			.getSync(1, TimeUnit.MINUTES);
		return assertCreateCodeSystem(null, branchPath, codeSystemId);
	}
	
	public static String createCodeSystem(IBranchPath branchPath, String codeSystemId) {
		return assertCreated(assertCreateCodeSystem(branchPath.getPath(), codeSystemId));
	}
	
	public static ValidatableResponse assertCreateCodeSystem(IBranchPath branchPath, String codeSystemId) {
		return assertCreateCodeSystem(branchPath.getPath(), codeSystemId);
	}
	
	public static ValidatableResponse assertCreateCodeSystem(String branchPath, String codeSystemId) {
		return assertCreateCodeSystem(null, branchPath, codeSystemId);
	}

	public static String createCodeSystem(ResourceURI extensionOf, String codeSystemId) {
		return assertCreated(assertCreateCodeSystem(extensionOf, null, codeSystemId));
	}
	
	public static ValidatableResponse assertCreateCodeSystem(ResourceURI extensionOf, String codeSystemId) {
		return assertCreateCodeSystem(extensionOf, null, codeSystemId);
	}
	
	public static ValidatableResponse assertCreateCodeSystem(ResourceURI extensionOf, String branchPath, String codeSystemId) {
		return assertCreateCodeSystem(extensionOf, branchPath, codeSystemId, Map.of());
	}
	
	public static ValidatableResponse createCodeSystem(ResourceURI extensionOf, String codeSystemId,  Map<String, Object> settings) {
		return assertCreateCodeSystem(extensionOf, null, codeSystemId, settings);
	}
	
	public static String createCodeSystem(ResourceURI extensionOf, String branchPath, String codeSystemId,  Map<String, Object> settings) {				
		return assertCreated(assertCreateCodeSystem(prepareCodeSystemCreateBody(extensionOf, branchPath, codeSystemId, settings)));
	}
	
	public static ValidatableResponse assertCreateCodeSystem(ResourceURI extensionOf, String branchPath, String codeSystemId,  Map<String, Object> settings) {				
		return assertCreateCodeSystem(prepareCodeSystemCreateBody(extensionOf, branchPath, codeSystemId, settings));
	}
	
	public static ValidatableResponse assertCreateCodeSystem(Json requestBody) {
		return givenAuthenticatedRequest(ApiTestConstants.CODESYSTEMS_API)
				.contentType(ContentType.JSON)
				.body(requestBody)
				.post()
				.then();
	}

	public static Json prepareCodeSystemCreateBody(ResourceURI extensionOf, String branchPath, String codeSystemId,  Map<String, Object> settings) {
		Json requestBody = Json.object(
			"id", codeSystemId,
			"title", "Title of " + codeSystemId,
			"url", getSnomedIntUrl(codeSystemId),
			"description", "<div>Markdown supported</div>",
			"toolingId", SnomedTerminologyComponentConstants.TOOLING_ID,
			"oid", "oid_" + codeSystemId,
			"language", "ENG",
			"branchPath", branchPath,
			"owner", "owner",
			"contact", "https://b2ihealthcare.com",
			"settings", configureLanguageAndPublisher(settings)
		);

		if (extensionOf != null) {
			requestBody = requestBody.with("dependencies", List.of(Dependency.of(extensionOf, TerminologyResource.DependencyScope.EXTENSION_OF)));
		} else if (branchPath != null) {
			requestBody = requestBody.with("branchPath", branchPath);
		}
		
		return requestBody;
	}

	private static Map<String, Object> configureLanguageAndPublisher(Map<String, Object> settings) {
		settings = settings == null ? Maps.newHashMap() : Maps.newHashMap(settings);
		settings.putIfAbsent(Resource.Settings.PUBLISHER, "SNOMED International");
		settings.putIfAbsent(Settings.LANGUAGES, List.of(
			Map.of(
				"languageTag", "en",
				"languageRefSetIds", Lists.newArrayList(Concepts.REFSET_LANGUAGE_TYPE_UK, Concepts.REFSET_LANGUAGE_TYPE_US)
			),
			Map.of(
				"languageTag", "en-us",
				"languageRefSetIds", Lists.newArrayList(Concepts.REFSET_LANGUAGE_TYPE_US)
			),
			Map.of(
				"languageTag", "en-gb",
				"languageRefSetIds", Lists.newArrayList(Concepts.REFSET_LANGUAGE_TYPE_UK)
			)
		));
		return settings;
	}

	public static String getSnomedIntUrl(String additionalParts) {
		if (Strings.isNullOrEmpty(additionalParts)) {
			return String.join("/", SnomedTerminologyComponentConstants.SNOMED_URI_SCT, Concepts.MODULE_SCT_CORE);
		} else {
			return String.join("/", SnomedTerminologyComponentConstants.SNOMED_URI_SCT, Concepts.MODULE_SCT_CORE, "test-case", additionalParts);
		}
	}

	public static ValidatableResponse assertGetCodeSystem(String codeSystemId, String...expand) {
		return givenAuthenticatedRequest(ApiTestConstants.CODESYSTEMS_API)
				.queryParam("expand", expand == null ? null : List.of(expand))
				.get("/{id}", codeSystemId)
				.then().assertThat();
	}
	
	public static CodeSystem getCodeSystem(String codeSystemId, String...expand) {
		return assertGetCodeSystem(codeSystemId, expand).statusCode(200).extract().as(CodeSystem.class);
	}
	
	public static ValidatableResponse updateCodeSystem(String id, Map<?, ?> requestBody) {
		return givenAuthenticatedRequest(ApiTestConstants.CODESYSTEMS_API)
				.contentType(ContentType.JSON)
				.body(requestBody)
				.put("/{id}", id)
				.then().assertThat().statusCode(204);
	}
	
	public static ValidatableResponse deleteCodeSystem(String codeSystemId) {
		return givenAuthenticatedRequest(ApiTestConstants.CODESYSTEMS_API)
				.delete("/{id}", codeSystemId)
				.then().assertThat().statusCode(204);

	}
	
	public static CodeSystems search(String id, String...expand) {
		return givenAuthenticatedRequest(ApiTestConstants.CODESYSTEMS_API)
				.contentType(ContentType.JSON)
				.body(Map.of(
					"id", Set.of(id),
					"expand", List.of(expand)
				))
				.post("/search")
				.then().assertThat().statusCode(200)
				.extract().as(CodeSystems.class);
	}
	
	public static void createCodeSystemAndVersion(final IBranchPath branchPath, String codeSystemId, String versionId, LocalDate effectiveTime) {
		assertCreateCodeSystem(branchPath, codeSystemId).statusCode(201);
		CodeSystemVersionRestRequests.createVersion(codeSystemId, versionId, effectiveTime).statusCode(201);
	}

	private CodeSystemRestRequests() {
		throw new UnsupportedOperationException("This class is not supposed to be instantiated.");
	}

}
