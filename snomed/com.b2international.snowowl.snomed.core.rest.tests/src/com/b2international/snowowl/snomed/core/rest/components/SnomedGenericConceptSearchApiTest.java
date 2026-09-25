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
package com.b2international.snowowl.snomed.core.rest.components;

import static com.b2international.snowowl.test.commons.rest.RestExtensions.JSON_UTF8;
import static com.b2international.snowowl.test.commons.rest.RestExtensions.givenAuthenticatedRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

import com.b2international.commons.json.Json;
import com.b2international.snowowl.core.config.RepositoryConfiguration;
import com.b2international.snowowl.core.config.SnowOwlConfiguration;
import com.b2international.snowowl.core.domain.Concept;
import com.b2international.snowowl.core.domain.Concepts;
import com.b2international.snowowl.snomed.core.rest.AbstractSnomedApiTest;
import com.b2international.snowowl.test.commons.Services;
import com.b2international.snowowl.test.commons.SnomedContentRule;
import com.b2international.snowowl.test.commons.rest.RestExtensions;

import io.restassured.response.ValidatableResponse;

/**
 * As multiple versions or dependent code systems are not supported we do not test those here.
 * 
 * @since 10.4.0
 */
public class SnomedGenericConceptSearchApiTest extends AbstractSnomedApiTest {
	
	private static final String CODESYSTEM_2018_01_31 = SnomedContentRule.SNOMEDCT.withPath("2018-01-31").toString();
	private static final String CODESYSTEM_2021_01_31 = SnomedContentRule.SNOMEDCT.withPath("2021-01-31").toString();
	private static final String CODESYSTEM_EXT = SnomedContentRule.SNOMEDCT_COMPLEX_MAP_BLOCK_EXT.toString();
	
	private static final String ID = "105590001";
	
	@Test
	public void GET_Concepts_filterById() {
		final List<String> conceptIds = assertGenericSearchConcepts(Json.object(
				"id", Json.array(ID),
				"codeSystem", Json.array(CODESYSTEM_2018_01_31)
			)).statusCode(200)
			.extract()
			.as(Concepts.class)
			.stream()
			.map(Concept::getId)
			.toList();
		
		assertThat(conceptIds)
			.contains(ID);
	}
	
	@Test
	public void GET_Concepts_filterByQuery() {
		final Concepts concepts = assertGenericSearchConcepts(Json.object(
				"limit", 0,
				"query", "*",
				"codeSystem", Json.array(CODESYSTEM_2018_01_31)
			)).statusCode(200)
			.extract()
			.as(Concepts.class);
		
		assertThat(concepts.getTotal())
			.isEqualTo(1888);
	}
	
	@Test
	public void GET_Concepts_withoutCodeSystem() {
		assertGenericSearchConcepts(Json.object("id", Json.array(ID)))
			.statusCode(400)
			.body("message", equalTo("One or more code systems must be provided"));
	}
	
	@Test
	public void GET_Concepts_multiCodeSystemTooMany() {
		final int maxThreads = Services.context().service(SnowOwlConfiguration.class).getModuleConfig(RepositoryConfiguration.class).getMaxThreadsGenericConceptSearch();
		
		// Create n+1 dummy path
		final List<String> codeSystemUris = IntStream.range(0, maxThreads + 1)
			.boxed()
			.map(i -> SnomedContentRule.SNOMEDCT.withPath(String.valueOf(i)).toString())
			.collect(Collectors.toList());
		
		assertGenericSearchConcepts(Json.object("codeSystem", codeSystemUris))
			.statusCode(400)
			.body("message", equalTo(String.format("Too many code systems supplied, maximum allowed number is %d", maxThreads)));
	}
	
	@Test
	public void GET_Concepts_multiCodeSystemWithDependency() {
		assertGenericSearchConcepts(Json.object(
				"codeSystem", Json.array(CODESYSTEM_2018_01_31, CODESYSTEM_EXT)))
			.statusCode(400)
			.body("message", equalTo("Searching dependent code systems is not supported"));
	}
	
	@Test
	public void GET_Concepts_multiCodeSystemWithDifferentVersions() {
		assertGenericSearchConcepts(Json.object(
				"codeSystem", Json.array(CODESYSTEM_2018_01_31, CODESYSTEM_2021_01_31)))
			.statusCode(400)
			.body("message", equalTo("Searching multiple versions of the same code system is not supported"));
	}
	
	@Test
	public void POST_Concepts_filterById() {
		final List<String> conceptIds = assertGenericSearchConceptsWithPost(Json.object(
				"id", Json.array(ID),
				"codeSystem", Json.array(CODESYSTEM_2018_01_31)
			)).statusCode(200)
			.extract()
			.as(Concepts.class)
			.stream()
			.map(Concept::getId)
			.toList();
		
		assertThat(conceptIds)
			.contains(ID);
	}
	
	@Test
	public void POST_Concepts_filterByQuery() {
		final Concepts concepts = assertGenericSearchConceptsWithPost(Json.object(
				"limit", 0,
				"query", "*",
				"codeSystem", Json.array(CODESYSTEM_2018_01_31)
			)).statusCode(200)
			.extract()
			.as(Concepts.class);
		
		assertThat(concepts.getTotal())
			.isEqualTo(1888);
	}
	
	@Test
	public void POST_Concepts_withoutCodeSystem() {
		assertGenericSearchConceptsWithPost(Json.object("id", Json.array(ID)))
			.statusCode(400)
			.body("message", equalTo("One or more code systems must be provided"));
	}
	
	@Test
	public void POST_Concepts_multiCodeSystemTooMany() {
		final int maxThreads = Services.context().service(SnowOwlConfiguration.class).getModuleConfig(RepositoryConfiguration.class).getMaxThreadsGenericConceptSearch();
		
		// Create n+1 dummy path
		final List<String> codeSystemUris = IntStream.range(0, maxThreads + 1)
			.boxed()
			.map(i -> SnomedContentRule.SNOMEDCT.withPath(String.valueOf(i)).toString())
			.collect(Collectors.toList());
		
		assertGenericSearchConceptsWithPost(Json.object("codeSystem", codeSystemUris))
			.statusCode(400)
			.body("message", equalTo(String.format("Too many code systems supplied, maximum allowed number is %d", maxThreads)));
	}
	
	@Test
	public void POST_Concepts_multiCodeSystemWithDependency() {
		assertGenericSearchConceptsWithPost(Json.object("codeSystem", Json.array(CODESYSTEM_2018_01_31, CODESYSTEM_EXT)))
			.statusCode(400)
			.body("message", equalTo("Searching dependent code systems is not supported"));
	}
	
	@Test
	public void POST_Concepts_multiCodeSystemWithDifferentVersions() {
		assertGenericSearchConceptsWithPost(Json.object("codeSystem", Json.array(CODESYSTEM_2018_01_31, CODESYSTEM_2021_01_31)))
			.statusCode(400)
			.body("message", equalTo("Searching multiple versions of the same code system is not supported"));
	}
	
	private ValidatableResponse assertGenericSearchConcepts(final Map<String, Object> queryParams) {
		return givenAuthenticatedRequest("/")
			.accept(JSON_UTF8)
			.queryParams(RestExtensions.encodeQueryParameters(queryParams))
			.get("/concepts")
			.then();
	}
	
	private ValidatableResponse assertGenericSearchConceptsWithPost(final Map<String, Object> queryParams) {
		return givenAuthenticatedRequest("/")
			.contentType(JSON_UTF8)
			.accept(JSON_UTF8)
			.body(queryParams)
			.post("/concepts/search")
			.then();
	}
}
