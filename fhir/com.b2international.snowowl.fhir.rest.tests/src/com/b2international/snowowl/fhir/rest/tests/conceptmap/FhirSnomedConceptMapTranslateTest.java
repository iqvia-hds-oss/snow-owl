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
package com.b2international.snowowl.fhir.rest.tests.conceptmap;

import static com.b2international.snowowl.test.commons.fhir.FhirApiHelpers.APPLICATION_FHIR_JSON;
import static com.b2international.snowowl.test.commons.fhir.FhirApiHelpers.CONCEPTMAP_TRANSLATE;
import static com.b2international.snowowl.test.commons.fhir.FhirApiHelpers.FHIR_ROOT_CONTEXT;
import static com.b2international.snowowl.test.commons.fhir.FhirApiHelpers.fromJson;
import static com.b2international.snowowl.test.commons.rest.RestExtensions.givenAuthenticatedRequest;
import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import java.util.List;
import java.util.Map;

import org.assertj.core.groups.Tuple;
import org.junit.BeforeClass;
import org.junit.Test;

import com.b2international.commons.json.Json;
import com.b2international.fhir.r5.operations.ConceptMapTranslateResultParameters;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.rest.tests.FhirRestTest;
import com.b2international.snowowl.fhir.rest.tests.FhirTestConcepts;
import com.b2international.snowowl.snomed.common.SnomedConstants;

import io.restassured.response.ValidatableResponse;

/**
 * Concept Map $translate REST tests for implicit SNOMED Map type reference sets
 * 
 * @since 10.3
 */
public class FhirSnomedConceptMapTranslateTest extends FhirRestTest {
	private static final String SNOMED_BASE = FhirModelHelpers.SNOMED_BASE_URI_STRING;
	private static final String INTERNATIONAL = FhirModelHelpers.SNOMED_BASE_URI_STRING + "/900000000000207008";
	
	
	protected static Map<String, String> refSetIds;

	@BeforeClass
	public static void setupMaps() {
		refSetIds = FhirSnomedConceptMapGenerator.createReferenceSets();
	}
	
	private static Json translateCodingBody(final String url, final String sourceCode, final String targetCode) {
		final List<Json> params = newArrayList();
		
		params.add(Json.object("name", "url", "valueUri", url));
		
		if (sourceCode != null) { params.add(Json.object("name", "sourceCoding", "valueCoding", Json.object("code", sourceCode))); }		
		if (targetCode != null) { params.add(Json.object("name", "targetCoding", "valueCoding", Json.object("code", targetCode))); }
		
		return Json.object(
			"resourceType", "Parameters",
			"parameter", params
		);
	}
	
	private static Json translateCodeableConceptBody(final String url, final String sourceCode, final String targetCode) {
		final List<Json> params = newArrayList();
		
		params.add(Json.object("name", "url", "valueUri", url));
		
		if (sourceCode != null) { params.add(Json.object("name", "sourceCodeableConcept", "valueCodeableConcept", Json.object("coding", Json.array(Json.object("code", sourceCode))))); }		
		if (targetCode != null) { params.add(Json.object("name", "targetCodeableConcept", "valueCodeableConcept", Json.object("coding", Json.array(Json.object("code", targetCode))))); }
		
		return Json.object(
			"resourceType", "Parameters",
			"parameter", params
		);
	}
	
	private ValidatableResponse assertGetTranslate(String url, Map<String, String> queryParams) {
		return givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", url)
			.queryParams(queryParams)
			.when()
			.get(CONCEPTMAP_TRANSLATE)
			.then();
	}
	
	private ValidatableResponse assertPostTranslate(Json body) {
		return givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.contentType(APPLICATION_FHIR_JSON)
			.body(body)
			.post(CONCEPTMAP_TRANSLATE)			
			.then();
	}
	
	private void assertParameters(ValidatableResponse response, boolean result, String message, Tuple... matches) throws Exception  {
		var parameters = new ConceptMapTranslateResultParameters(fromJson(response.extract().asString()));
		
		assertThat(parameters.getResult())
			.isNotNull()
			.hasFieldOrPropertyWithValue("value", result);
		
		assertThat(parameters.getMessage())
			.isNotNull()
			.hasFieldOrPropertyWithValue("value", message);
		
		assertThat(parameters.getMatch())
			.extracting(
				m -> m.getRelationship().getCode(),
				m -> m.getConcept().getCode(),
				m -> m.getConcept().getDisplay(),
				m -> m.getConcept().getSystem(),
				m -> m.getConcept().getVersion()
			).containsExactlyInAnyOrder(matches);
	}
	
	@Test
	public void translateGetMissingUrl() throws Exception {
		// XXX: fhir specification would allow this
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("sourceCode", FhirTestConcepts.MICROORGANISM)
			.queryParam("system",  SNOMED_BASE)
			.get(CONCEPTMAP_TRANSLATE)
			.then()
			.statusCode(400)
			.body("resourceType", equalTo("OperationOutcome"))
			.body("issue.severity", hasItem("error"))
			.body("issue.code", hasItem("exception"))
			.body("issue.diagnostics", hasItem("Required request parameter 'url' for method parameter type String is not present"));
	}
	
	@Test
	public void translatePostMissingUrl() throws Exception {
		// XXX: fhir specification would allow this
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.contentType(APPLICATION_FHIR_JSON)
			.body(translateCodingBody(null, FhirTestConcepts.MICROORGANISM, null))
			.post(CONCEPTMAP_TRANSLATE)
			.then()
			.statusCode(400)
			.body("resourceType", equalTo("OperationOutcome"))
			.body("issue.severity", hasItem("error"))
			.body("issue.code", hasItem("invalid"))
			.body("issue.diagnostics", hasItem("'url' is required to reduce the scope of the translate operation to a single ConceptMap"));
	}
	
	@Test
	public void translateGetMissingSourceOrTargetCode() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.SIMPLE_MAP_TEST_REF_SET);
		final String url = SNOMED_BASE + "?fhir_cm=" + refSetId;
		
		assertGetTranslate(url, Map.of())
			.statusCode(400)
			.body("resourceType", equalTo("OperationOutcome"))
			.body("issue.severity", hasItem("error"))
			.body("issue.code", hasItem("invalid"))
			.body("issue.diagnostics", hasItem("One (and only one) of the 'in' parameters (sourceCode, sourceCoding, sourceCodeableConcept, targetCode, targetCoding, targetCodeableConcept) must be provided to identify the code that is to be translated."));
	}
	
	@Test
	public void translatePostMissingSourceOrTargetCode() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.SIMPLE_MAP_TEST_REF_SET);
		final String url = SNOMED_BASE + "?fhir_cm=" + refSetId;
		 
		assertPostTranslate(translateCodingBody(url, null, null))
			.statusCode(400)
			.body("resourceType", equalTo("OperationOutcome"))
			.body("issue.severity", hasItem("error"))
			.body("issue.code", hasItem("invalid"))
			.body("issue.diagnostics", hasItem("One (and only one) of the 'in' parameters (sourceCode, sourceCoding, sourceCodeableConcept, targetCode, targetCoding, targetCodeableConcept) must be provided to identify the code that is to be translated."));
	}
	
	@Test
	public void translateInvalidReferenceSet() throws Exception {
		final String url = SNOMEDCT_URL + "?fhir_cm=INVALID";
		
		assertGetTranslate(url, Map.of("sourceCode", FhirTestConcepts.MICROORGANISM, "system", SNOMED_BASE))
			.statusCode(400)
			.body("resourceType", equalTo("OperationOutcome"))
			.body("issue.severity", hasItem("error"))
			.body("issue.code", hasItem("invalid"))
			.body("issue.diagnostics", hasItem("'url' contains an invalid reference set id: INVALID"));
	}
	
	@Test
	public void translateMissingReferenceSet() throws Exception {
		final String url = SNOMEDCT_URL + "?fhir_cm=1204364002";
		
		assertGetTranslate(url, Map.of("sourceCode", FhirTestConcepts.MICROORGANISM, "system", SNOMED_BASE))
			.statusCode(400)
			.body("resourceType", equalTo("OperationOutcome"))
			.body("issue.severity", hasItem("error"))
			.body("issue.code", hasItem("invalid"))
			.body("issue.diagnostics", hasItem("Reference set could not be found: 1204364002"));
	}
	
	@Test
	public void translateSimpleMap() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.SIMPLE_MAP_TEST_REF_SET);
		final String url = SNOMED_BASE + "?fhir_cm=" + refSetId;
		
		var response = assertGetTranslate(url, Map.of("sourceCode", FhirTestConcepts.MICROORGANISM, "system", SNOMED_BASE))
			.statusCode(200);
		
		assertParameters(response,
			true,
			"1 member(s) from concept map: " + url,
			tuple("related-to", "MO", null, null, null)
		);
	}
	
	@Test
	public void translateSimpleMapReversed() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.SIMPLE_MAP_TEST_REF_SET);
		final String url = SNOMED_BASE + "?fhir_cm=" + refSetId;
		
		var response = assertGetTranslate(url, Map.of("targetCode", "MO", "targetSystem", SNOMED_BASE))
			.statusCode(200);
		
		assertParameters(response,
			true,
			"1 member(s) from concept map: " + url,
			tuple("related-to", FhirTestConcepts.MICROORGANISM, "Microorganism", SNOMED_BASE, INTERNATIONAL)
		);
	}
	
	@Test
	public void translateSimpleMapMissingCode() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.SIMPLE_MAP_TEST_REF_SET);
		final String url = SNOMED_BASE + "?fhir_cm=" + refSetId;
		
		var response = assertGetTranslate(url, Map.of("sourceCode", "MissingCode", "system", SNOMED_BASE))
			.statusCode(200);
		
		assertParameters(response,
			false,
			"No matches"
		);
	}
	
	@Test
	public void translateSimpleMapTo() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.SIMPLE_MAP_TO_TEST_REF_SET);
		final String url = SNOMED_BASE + "?fhir_cm=" + refSetId;
		
		var response = assertGetTranslate(url, Map.of("sourceCode", "MO", "system", SNOMED_BASE))
			.statusCode(200);
		
		assertParameters(response,
			true,
			"1 member(s) from concept map: " + url,
			tuple("related-to", FhirTestConcepts.MICROORGANISM, "Microorganism", SNOMED_BASE, INTERNATIONAL)
		);
	}
	
	@Test
	public void translateSimpleMapToReversed() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.SIMPLE_MAP_TO_TEST_REF_SET);
		final String url = SNOMED_BASE + "?fhir_cm=" + refSetId;
		
		var response = assertGetTranslate(url, Map.of("targetCode", FhirTestConcepts.MICROORGANISM, "targetSystem", SNOMED_BASE))
			.statusCode(200);
		
		assertParameters(response,
			true,
			"1 member(s) from concept map: " + url,
			tuple("related-to", "MO", null, null, null)
		);
	}
	
	@Test
	public void translateComplexMap() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.COMPLEX_MAP_TEST_REF_SET);
		final String url = SNOMED_BASE + "?fhir_cm=" + refSetId;
		
		var response = assertGetTranslate(url, Map.of("sourceCode", FhirTestConcepts.MICROORGANISM, "system", SNOMED_BASE))
			.statusCode(200);
		
		assertParameters(response,
			true,
			"1 member(s) from concept map: " + url,
			tuple("equivalent", "MO", null, null, null)
		);
	}
	
	@Test
	public void translateComplexMapReversed() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.COMPLEX_MAP_TEST_REF_SET);
		final String url = SNOMED_BASE + "?fhir_cm=" + refSetId;
		
		var response = assertGetTranslate(url, Map.of("targetCode", "MO", "targetSystem", SNOMED_BASE))
			.statusCode(200);
		
		assertParameters(response,
			true,
			"1 member(s) from concept map: " + url,
			tuple("equivalent", FhirTestConcepts.MICROORGANISM, "Microorganism", SNOMED_BASE, INTERNATIONAL)
		);
	}
	
	@Test
	public void translateSimpleMapVersioned() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.VERSIONED_SIMPLE_MAP_TEST_REF_SET);
		final String version = SNOMED_BASE + "/900000000000207008/version/" + FhirSnomedConceptMapGenerator.VERSION_2026_01_01;
		final String url = version + "?fhir_cm=" + refSetId;
		
		var response = assertGetTranslate(url, Map.of("sourceCode", FhirTestConcepts.MICROORGANISM, "system", SNOMED_BASE))
			.statusCode(200);
		
		assertParameters(response,
			true,
			"1 member(s) from concept map: " + url,
			tuple("related-to", "MO", null, null, null)
		);
	}
	
	@Test
	public void translateSimpleMapVersionedReversed() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.VERSIONED_SIMPLE_MAP_TEST_REF_SET);
		final String version = SNOMED_BASE + "/900000000000207008/version/" + FhirSnomedConceptMapGenerator.VERSION_2026_01_01;
		final String url = version + "?fhir_cm=" + refSetId;
		
		var response = assertGetTranslate(url, Map.of("targetCode", "MO", "targetSystem", SNOMED_BASE))
			.statusCode(200);
			
		assertParameters(response,
			true,
			"1 member(s) from concept map: " + url,
			tuple("related-to", FhirTestConcepts.MICROORGANISM, "Microorganism", SNOMED_BASE, version)
		);
	}
	
	@Test
	public void translateSimpleMapVersionedLater() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.VERSIONED_SIMPLE_MAP_TEST_REF_SET);
		final String version = SNOMED_BASE + "/900000000000207008/version/" + FhirSnomedConceptMapGenerator.VERSION_2027_01_01;
		final String url = version + "?fhir_cm=" + refSetId;
		
		var response = assertGetTranslate(url, Map.of("sourceCode", FhirTestConcepts.MICROORGANISM, "system", SNOMED_BASE))
			.statusCode(200);
		
		assertParameters(response,
			true,
			"2 member(s) from concept map: " + url,
			tuple("related-to", "MO", null, null, null),
			tuple("related-to", "MO2", null, null, null)
		);
	}
	
	@Test
	public void translateAssociationMapSameAs() throws Exception {
		/*
		 * Use real association map loaded from RF2
		 */
		final String refSetId = SnomedConstants.Concepts.REFSET_SAME_AS_ASSOCIATION;
		final String url = SNOMED_BASE + "?fhir_cm=" + refSetId;
		
		var response = assertGetTranslate(url, Map.of("sourceCode", "272388002", "system", SNOMED_BASE))
			.statusCode(200);
		
		assertParameters(response,
			true,
			"1 member(s) from concept map: " + url,
			tuple("equivalent", "272394005", "Technique", SNOMED_BASE, INTERNATIONAL)
		);
	}
	
	@Test
	public void translateAssociationMapPossiblyEquivalent() throws Exception {
		/*
		 * Use real association map loaded from RF2
		 */
		final String refSetId = SnomedConstants.Concepts.REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION;
		final String url = SNOMED_BASE + "?fhir_cm=" + refSetId;
		
		var response = assertGetTranslate(url, Map.of("sourceCode", "114244009", "system", SNOMED_BASE))
			.statusCode(200);
		
		assertParameters(response,
			true,
			"3 member(s) from concept map: " + url,
			tuple("related-to", "413864003", "Gammaproteobacteria", SNOMED_BASE, INTERNATIONAL),
			tuple("related-to", "409853001", "Betaproteobacteria", SNOMED_BASE, INTERNATIONAL),
			tuple("related-to", "413858005", "Alphaproteobacteria", SNOMED_BASE, INTERNATIONAL)
		);
	}
	
	@Test
	public void translateAssociationMapPossiblyEquivalentReversed() throws Exception {
		/*
		 * Use real association map loaded from RF2
		 */
		final String refSetId = SnomedConstants.Concepts.REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION;
		final String url = SNOMED_BASE + "?fhir_cm=" + refSetId;
		
		var response = assertGetTranslate(url, Map.of("targetCode", "413864003", "system", SNOMED_BASE))
			.statusCode(200);
		
		assertParameters(response,
			true,
			"1 member(s) from concept map: " + url,
			tuple("related-to", "114244009", "Class Scotobacteria", SNOMED_BASE, INTERNATIONAL)
		);
	}
	
	@Test
	public void translateSimpleMapUsingCoding() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.SIMPLE_MAP_TEST_REF_SET);
		final String url = SNOMED_BASE + "?fhir_cm=" + refSetId;
		
		
		var response = assertPostTranslate(translateCodingBody(url, FhirTestConcepts.MICROORGANISM, null));
		
		assertParameters(response,
			true,
			"1 member(s) from concept map: " + url,
			tuple("related-to", "MO", null, null, null)
		);
	}
	
	@Test
	public void translateSimpleMapUsingCodingReversed() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.SIMPLE_MAP_TEST_REF_SET);
		final String url = SNOMED_BASE + "?fhir_cm=" + refSetId;
		
		var response = assertPostTranslate(translateCodingBody(url, null, "MO"));
		
		assertParameters(response,
			true,
			"1 member(s) from concept map: " + url,
			tuple("related-to", FhirTestConcepts.MICROORGANISM, "Microorganism", SNOMED_BASE, INTERNATIONAL)
		);
	}
	
	@Test
	public void translateSimpleMapUsingCodeableConcept() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.SIMPLE_MAP_TEST_REF_SET);
		final String url = SNOMED_BASE + "?fhir_cm=" + refSetId;
		
		var response = assertPostTranslate(translateCodeableConceptBody(url, FhirTestConcepts.MICROORGANISM, null));
		
		assertParameters(response,
			true,
			"1 member(s) from concept map: " + url,
			tuple("related-to", "MO", null, null, null)
		);
	}
	
	@Test
	public void translateSimpleMapUsingCodeableConceptReversed() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.SIMPLE_MAP_TEST_REF_SET);
		final String url = SNOMED_BASE + "?fhir_cm=" + refSetId;
		
		var response = assertPostTranslate(translateCodeableConceptBody(url, null, "MO"));
		
		assertParameters(response,
			true,
			"1 member(s) from concept map: " + url,
			tuple("related-to", FhirTestConcepts.MICROORGANISM, "Microorganism", SNOMED_BASE, INTERNATIONAL)
		);
	}
	
}