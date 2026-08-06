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
import static com.b2international.snowowl.test.commons.rest.RestExtensions.givenAuthenticatedRequest;
import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.Matchers.*;

import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.b2international.commons.json.Json;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.rest.tests.FhirRestTest;
import com.b2international.snowowl.fhir.rest.tests.FhirTestConcepts;
import com.b2international.snowowl.snomed.common.SnomedConstants;
import com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants;

/**
 * Concept Map $translate REST tests for implicit SNOMED Map type reference sets
 * 
 * @since 10.3
 */
public class FhirSnomedConceptMapTranslateTest extends FhirRestTest {
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
	
	@Test
	public void translateInvalidReferenceSet() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", SNOMEDCT_URL + "?fhir_cm=INVALID")
			.queryParam("sourceCode", FhirTestConcepts.MICROORGANISM)
			.queryParam("system",  SnomedTerminologyComponentConstants.SNOMED_URI_SCT)
			.when().get(CONCEPTMAP_TRANSLATE)
			.then()
			.statusCode(400)
			.body("resourceType", equalTo("OperationOutcome"))
			.body("issue.severity", hasItem("error"))
			.body("issue.code", hasItem("invalid"))
			.body("issue.diagnostics", hasItem("Reference set could not be found: INVALID"));
	}
	
	@Test
	public void translateSimpleMap() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.SIMPLE_MAP_TEST_REF_SET);
		final String url = FhirModelHelpers.SNOMED_BASE_URI_STRING + "?fhir_cm=" + refSetId;
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.param("sourceCode", FhirTestConcepts.MICROORGANISM) 
			.param("system", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.param("url", url)
			.when()
			.get(CONCEPTMAP_TRANSLATE)
			.then()
			.statusCode(200)
			.body("parameter.find { it.name == 'result' }.valueBoolean", equalTo(true))
			.body("parameter.find { it.name == 'message' }.valueString", equalTo("1 member(s) from concept map: " + url))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'relationship' }.valueCode", equalTo("related-to"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.code", equalTo("MO"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.display", nullValue())
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.system", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.version", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING + "/900000000000207008"));
	}
	
	@Test
	public void translateSimpleMapReversed() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.SIMPLE_MAP_TEST_REF_SET);
		final String url = FhirModelHelpers.SNOMED_BASE_URI_STRING + "?fhir_cm=" + refSetId;
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.param("targetCode", "MO") 
			.param("targetSystem", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.param("url", url)
			.when()
			.get(CONCEPTMAP_TRANSLATE)
			.then()
			.statusCode(200)
			.body("parameter.find { it.name == 'result' }.valueBoolean", equalTo(true))
			.body("parameter.find { it.name == 'message' }.valueString", equalTo("1 member(s) from concept map: " + url))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'relationship' }.valueCode", equalTo("related-to"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.code", equalTo(FhirTestConcepts.MICROORGANISM))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.display", equalTo("Microorganism"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.system", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.version", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING + "/900000000000207008"));
	}
	
	@Test
	public void translateSimpleMapTo() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.SIMPLE_MAP_TO_TEST_REF_SET);
		final String url = FhirModelHelpers.SNOMED_BASE_URI_STRING + "?fhir_cm=" + refSetId;
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.param("sourceCode", "MO") 
			.param("system", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.param("url", url)
			.when()
			.get(CONCEPTMAP_TRANSLATE)
			.then()
			.statusCode(200)
			.body("parameter.find { it.name == 'result' }.valueBoolean", equalTo(true))
			.body("parameter.find { it.name == 'message' }.valueString", equalTo("1 member(s) from concept map: " + url))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'relationship' }.valueCode", equalTo("related-to"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.code", equalTo(FhirTestConcepts.MICROORGANISM))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.display", equalTo("Microorganism"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.system", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.version", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING + "/900000000000207008"));
	}
	
	@Test
	public void translateSimpleMapToReversed() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.SIMPLE_MAP_TO_TEST_REF_SET);
		final String url = FhirModelHelpers.SNOMED_BASE_URI_STRING + "?fhir_cm=" + refSetId;
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.param("targetCode", FhirTestConcepts.MICROORGANISM) 
			.param("targetSystem", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.param("url", url)
			.when()
			.get(CONCEPTMAP_TRANSLATE)
			.then()
			.statusCode(200)
			.body("parameter.find { it.name == 'result' }.valueBoolean", equalTo(true))
			.body("parameter.find { it.name == 'message' }.valueString", equalTo("1 member(s) from concept map: " + url))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'relationship' }.valueCode", equalTo("related-to"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.code", equalTo("MO"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.display", nullValue())
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.system", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.version", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING + "/900000000000207008"));
	}
	
	@Test
	public void translateComplexMap() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.COMPLEX_MAP_TEST_REF_SET);
		final String url = FhirModelHelpers.SNOMED_BASE_URI_STRING + "?fhir_cm=" + refSetId;
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.param("sourceCode", FhirTestConcepts.MICROORGANISM) 
			.param("system", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.param("url", url)
			.when()
			.get(CONCEPTMAP_TRANSLATE)
			.then()
			.statusCode(200)
			.body("parameter.find { it.name == 'result' }.valueBoolean", equalTo(true))
			.body("parameter.find { it.name == 'message' }.valueString", equalTo("1 member(s) from concept map: " + url))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'relationship' }.valueCode", equalTo("equivalent"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.code", equalTo("MO"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.display", nullValue())
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.system", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.version", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING + "/900000000000207008"));
	}
	
	@Test
	public void translateComplexMapReversed() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.COMPLEX_MAP_TEST_REF_SET);
		final String url = FhirModelHelpers.SNOMED_BASE_URI_STRING + "?fhir_cm=" + refSetId;
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.param("targetCode", "MO") 
			.param("targetSystem", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.param("url", url)
			.when()
			.get(CONCEPTMAP_TRANSLATE)
			.then()
			.statusCode(200)
			.body("parameter.find { it.name == 'result' }.valueBoolean", equalTo(true))
			.body("parameter.find { it.name == 'message' }.valueString", equalTo("1 member(s) from concept map: " + url))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'relationship' }.valueCode", equalTo("equivalent"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.code", equalTo(FhirTestConcepts.MICROORGANISM))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.display", equalTo("Microorganism"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.system", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.version", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING + "/900000000000207008"));
	}
	
	@Test
	public void translateSimpleMapVersioned() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.VERSIONED_SIMPLE_MAP_TEST_REF_SET);
		final String version = FhirModelHelpers.SNOMED_BASE_URI_STRING + "/900000000000207008/version/" + FhirSnomedConceptMapGenerator.VERSION_2026_01_01;
		final String url = version + "?fhir_cm=" + refSetId;
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.param("sourceCode", FhirTestConcepts.MICROORGANISM) 
			.param("system", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.param("url", url)
			.when()
			.get(CONCEPTMAP_TRANSLATE)
			.then()
			.statusCode(200)
			.body("parameter.find { it.name == 'result' }.valueBoolean", equalTo(true))
			.body("parameter.find { it.name == 'message' }.valueString", equalTo("1 member(s) from concept map: " + url))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'relationship' }.valueCode", equalTo("related-to"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.code", equalTo("MO"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.display", nullValue())
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.system", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.version", equalTo(version));
	}
	
	@Test
	public void translateSimpleMapVersionedLater() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.VERSIONED_SIMPLE_MAP_TEST_REF_SET);
		final String version = FhirModelHelpers.SNOMED_BASE_URI_STRING + "/900000000000207008/version/" + FhirSnomedConceptMapGenerator.VERSION_2027_01_01;
		final String url = version + "?fhir_cm=" + refSetId;
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.param("sourceCode", FhirTestConcepts.MICROORGANISM) 
			.param("system", FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.param("url", url)
			.when()
			.get(CONCEPTMAP_TRANSLATE)
			.then()
			.statusCode(200)
			.body("parameter.find { it.name == 'result' }.valueBoolean", equalTo(true))
			.body("parameter.find { it.name == 'message' }.valueString", equalTo("2 member(s) from concept map: " + url))
			.body("parameter.findAll { it.name == 'match' }.part.flatten().findAll { it.name == 'relationship' }.valueCode", contains("related-to", "related-to"))
			.body("parameter.findAll { it.name == 'match' }.part.flatten().findAll { it.name == 'concept' }.valueCoding.code", containsInAnyOrder("MO", "MO2"))  // XXX: flaky test, but other values are the same so just ignore order
			.body("parameter.findAll { it.name == 'match' }.part.flatten().findAll { it.name == 'concept' }.valueCoding.display", contains(null, null))
			.body("parameter.findAll { it.name == 'match' }.part.flatten().findAll { it.name == 'concept' }.valueCoding.system", contains(FhirModelHelpers.SNOMED_BASE_URI_STRING, FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("parameter.findAll { it.name == 'match' }.part.flatten().findAll { it.name == 'concept' }.valueCoding.version", contains(version, version));
	}
	
	@Test
	public void translateAssociationMapSameAs() throws Exception {
		/*
		 * Use real association map loaded from RF2
		 */
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", SNOMEDCT_URL + "?fhir_cm=" + SnomedConstants.Concepts.REFSET_SAME_AS_ASSOCIATION)
			.queryParam("sourceCode", "272388002")
			.queryParam("system",  SnomedTerminologyComponentConstants.SNOMED_URI_SCT)
			.when().get(CONCEPTMAP_TRANSLATE)
			.then()
			.statusCode(200)
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.code", equalTo("272394005"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.display", equalTo("Technique"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'relationship' }.valueCode", equalTo("equivalent"));
	}
	
	@Test
	public void translateAssociationMapPossiblyEquivalent() throws Exception {
		/*
		 * Use real association map loaded from RF2
		 */
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", SNOMEDCT_URL + "?fhir_cm=" + SnomedConstants.Concepts.REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION)
			.queryParam("sourceCode", "114244009")
			.queryParam("system",  SnomedTerminologyComponentConstants.SNOMED_URI_SCT)
			.when().get(CONCEPTMAP_TRANSLATE)
			.then()
			.statusCode(200)
			.body("parameter.findAll { it.name == 'match' }.part.flatten().findAll { it.name == 'concept' }.valueCoding.code", contains("413864003", "409853001", "413858005"))
			.body("parameter.findAll { it.name == 'match' }.part.flatten().findAll { it.name == 'concept' }.valueCoding.display", contains("Gammaproteobacteria", "Betaproteobacteria", "Alphaproteobacteria"))
			.body("parameter.findAll { it.name == 'match' }.part.flatten().findAll { it.name == 'relationship' }.valueCode", contains("related-to", "related-to", "related-to"));
	}
	
	@Test
	public void translateAssociationMapPossiblyEquivalentReversed() throws Exception {
		/*
		 * Use real association map loaded from RF2
		 */
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", SNOMEDCT_URL + "?fhir_cm=" + SnomedConstants.Concepts.REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION)
			.queryParam("targetCode", "413864003")
			.queryParam("targetSystem",  SnomedTerminologyComponentConstants.SNOMED_URI_SCT)
			.when().get(CONCEPTMAP_TRANSLATE)
			.then()
			.statusCode(200)
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.code", equalTo("114244009"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.display", equalTo("Class Scotobacteria"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'relationship' }.valueCode", equalTo("related-to"));
	}
	
	@Test
	public void translateSimpleMapUsingCoding() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.SIMPLE_MAP_TEST_REF_SET);
		final String url = FhirModelHelpers.SNOMED_BASE_URI_STRING + "?fhir_cm=" + refSetId;
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.contentType(APPLICATION_FHIR_JSON)
			.body(translateCodingBody(url, FhirTestConcepts.MICROORGANISM, null))
			.post(CONCEPTMAP_TRANSLATE)
			.then()
			.statusCode(200)
			.body("parameter.find { it.name == 'result' }.valueBoolean", equalTo(true))
			.body("parameter.find { it.name == 'message' }.valueString", equalTo("1 member(s) from concept map: " + url))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'relationship' }.valueCode", equalTo("related-to"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.code", equalTo("MO"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.display", nullValue())
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.system", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.version", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING + "/900000000000207008"));
	}
	
	@Test
	public void translateSimpleMapUsingCodingReversed() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.SIMPLE_MAP_TEST_REF_SET);
		final String url = FhirModelHelpers.SNOMED_BASE_URI_STRING + "?fhir_cm=" + refSetId;
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.contentType(APPLICATION_FHIR_JSON)
			.body(translateCodingBody(url, null, "MO"))
			.post(CONCEPTMAP_TRANSLATE)
			.then()
			.statusCode(200)
			.body("parameter.find { it.name == 'result' }.valueBoolean", equalTo(true))
			.body("parameter.find { it.name == 'message' }.valueString", equalTo("1 member(s) from concept map: " + url))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'relationship' }.valueCode", equalTo("related-to"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.code", equalTo(FhirTestConcepts.MICROORGANISM))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.display", equalTo("Microorganism"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.system", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.version", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING + "/900000000000207008"));
	}
	
	@Test
	public void translateSimpleMapUsingCodeableConcept() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.SIMPLE_MAP_TEST_REF_SET);
		final String url = FhirModelHelpers.SNOMED_BASE_URI_STRING + "?fhir_cm=" + refSetId;
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.contentType(APPLICATION_FHIR_JSON)
			.body(translateCodeableConceptBody(url, FhirTestConcepts.MICROORGANISM, null))
			.post(CONCEPTMAP_TRANSLATE)
			.then()
			.statusCode(200)
			.body("parameter.find { it.name == 'result' }.valueBoolean", equalTo(true))
			.body("parameter.find { it.name == 'message' }.valueString", equalTo("1 member(s) from concept map: " + url))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'relationship' }.valueCode", equalTo("related-to"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.code", equalTo("MO"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.display", nullValue())
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.system", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.version", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING + "/900000000000207008"));
	}
	
	@Test
	public void translateSimpleMapUsingCodeableConceptReversed() throws Exception {
		final String refSetId = refSetIds.get(FhirSnomedConceptMapGenerator.SIMPLE_MAP_TEST_REF_SET);
		final String url = FhirModelHelpers.SNOMED_BASE_URI_STRING + "?fhir_cm=" + refSetId;
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.contentType(APPLICATION_FHIR_JSON)
			.body(translateCodeableConceptBody(url, null, "MO"))
			.post(CONCEPTMAP_TRANSLATE)
			.then()
			.statusCode(200)
			.body("parameter.find { it.name == 'result' }.valueBoolean", equalTo(true))
			.body("parameter.find { it.name == 'message' }.valueString", equalTo("1 member(s) from concept map: " + url))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'relationship' }.valueCode", equalTo("related-to"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.code", equalTo(FhirTestConcepts.MICROORGANISM))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.display", equalTo("Microorganism"))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.system", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("parameter.find { it.name == 'match' }.part.find { it.name == 'concept' }.valueCoding.version", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING + "/900000000000207008"));
	}
	
}