/*
 * Copyright 2011-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.fhir.rest.tests.codesystem;

import static com.b2international.snowowl.test.commons.fhir.FhirApiHelpers.CODESYSTEM;
import static com.b2international.snowowl.test.commons.fhir.FhirApiHelpers.CODESYSTEM_ID;
import static com.b2international.snowowl.test.commons.fhir.FhirApiHelpers.FHIR_ROOT_CONTEXT;
import static com.b2international.snowowl.test.commons.rest.RestExtensions.givenAuthenticatedRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.UUID;

import org.junit.Test;

import com.b2international.commons.json.Json;
import com.b2international.fhir.FhirCodeSystems;
import com.b2international.snowowl.core.commit.CommitInfo;
import com.b2international.snowowl.core.commit.CommitInfos;
import com.b2international.snowowl.core.context.ResourceRepositoryRequestBuilder;
import com.b2international.snowowl.core.domain.RepositoryContext;
import com.b2international.snowowl.core.events.Request;
import com.b2international.snowowl.core.repository.RepositoryRequests;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.R5ObjectFields;
import com.b2international.snowowl.fhir.rest.tests.FhirRestTest;
import com.b2international.snowowl.snomed.fhir.SnomedUri;
import com.b2international.snowowl.test.commons.Services;
import com.b2international.snowowl.test.commons.codesystem.CodeSystemRestRequests;
import com.b2international.snowowl.test.commons.rest.RestExtensions;

/**
 * FHIR /CodeSystem Resource API Tests
 * 
 * @since 6.6
 */
public class FhirCodeSystemApiTest extends FhirRestTest {
	
	private static final int NUM_CONCEPTS = 1943;

	@Test
	public void GET_CodeSystem() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("type", equalTo("searchset"))
			.body("meta.tag.code", not(hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode())))
			.body("total", greaterThanOrEqualTo(1)) // actual number depends on test data, just verify existence
			.body("entry[0].resource.id", equalTo(getTestCodeSystemId()))
			.body("entry[0].resource.url", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("entry[0].resource.version", equalTo(getTestCodeSystemUrl()))
			.body("entry[0].resource.valueSet", equalTo(String.join("?", getTestCodeSystemUrl(), SnomedUri.QueryPart.PREFIX_VS)))
			.body("entry[0].resource.count", equalTo(NUM_CONCEPTS)); // concept count should be updated if the imported RF2 file is changed
	}
	
	@Test
	public void GET_CodeSystem_Strict() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("unsupportedParam", "value")
			.header("Prefer", "handling=strict")
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(400)
			.body("resourceType", equalTo("OperationOutcome"))
			.body("issue.severity", hasItem("error"))
			.body("issue.code", hasItem("exception"));
	}
	
	@Test
	public void GET_CodeSystem_IdFilter_NoMatch() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("_id", "non-existent")
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("type", equalTo("searchset"))
			.body("meta.tag.code", not(hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode())))
			.body("total", equalTo(0));
	}
	
	@Test
	public void GET_CodeSystem_IdFilter_Match() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("_id", getTestCodeSystemId())
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("type", equalTo("searchset"))
			.body("meta.tag.code", not(hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode())))
			.body("total", equalTo(1))
			.body("entry[0].resource.url", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("entry[0].resource.version", equalTo(getTestCodeSystemUrl()));
	}
	
	@Test
	public void GET_CodeSystem_IdFilter_Match_Multi() throws Exception {
		String anotherCodeSystemId = createCodeSystem(UUID.randomUUID().toString());
		String thirdCodeSystemId = createCodeSystem(UUID.randomUUID().toString());
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("_id", getTestCodeSystemId(), anotherCodeSystemId)
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("type", equalTo("searchset"))
			.body("meta.tag.code", not(hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode())))
			.body("total", equalTo(2))
			.body("entry.resource.id", allOf(
				hasItems(getTestCodeSystemId(), anotherCodeSystemId), 
				not(hasItem(thirdCodeSystemId))))
			.body("entry.resource.url", hasItem(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("entry.resource.version", hasItem(getTestCodeSystemUrl()));
	}
	
	@Test
	public void GET_CodeSystem_NameFilter_NoMatch() {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("name", RestExtensions.encodeQueryParameter("unknown name"))
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("type", equalTo("searchset"))
			.body("meta.tag.code", not(hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode())))
			.body("total", equalTo(0));
	}
	
	@Test
	public void GET_CodeSystem_NameFilter_Match_Single() {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("name", getTestCodeSystemId())
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("type", equalTo("searchset"))
			.body("meta.tag.code", not(hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode())))
			.body("total", equalTo(1))
			.body("entry[0].resource.id", equalTo(getTestCodeSystemId()))
			.body("entry[0].resource.url", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("entry[0].resource.version", equalTo(getTestCodeSystemUrl()));
	}
	
	@Test
	public void GET_CodeSystem_NameFilter_Match_Multiple() {
		String anotherCodeSystemId = createCodeSystem(UUID.randomUUID().toString());
		String thirdCodeSystemId = createCodeSystem(UUID.randomUUID().toString());
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("name", getTestCodeSystemId(), anotherCodeSystemId)
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("meta.tag.code", not(hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode())))
			.body("type", equalTo("searchset"))
			.body("total", equalTo(2))
			.body("entry.resource.id", allOf(
				hasItems(getTestCodeSystemId(), anotherCodeSystemId),
				not(hasItem(thirdCodeSystemId))))
			.body("entry.resource.version", hasItem(getTestCodeSystemUrl()));
	}
	
	@Test
	public void GET_CodeSystem_Summary_True() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("_id", getTestCodeSystemId())
			.queryParam("_summary", true)
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("meta.tag.code", hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode()))
			.body("type", equalTo("searchset"))
			.body("total", equalTo(1))
			.rootPath("entry[0].resource")
			.body("id", equalTo(getTestCodeSystemId()))
			.body("title", equalTo("Title of " + getTestCodeSystemId()))
			.body("property", notNullValue())
			.body("filter", notNullValue())
			.body("caseSensitive", equalTo(true))
			.body("effectivePeriod", nullValue())
			
			//no concept definitions are part of the summary
			.body("entry.resource", not(hasItem("concept")));
	}
	
	@Test
	public void GET_Versioned_CodeSystem_Summary_True() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("_id", "SNOMEDCT/2002-01-31")
			.queryParam("_summary", true)
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("meta.tag.code", hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode()))
			.body("type", equalTo("searchset"))
			.body("total", equalTo(1))
			.rootPath("entry[0].resource")
			.body("id", equalTo("SNOMEDCT")) // XXX: the versioned code system id is not returned in the resource
			.body("title", equalTo("SNOMEDCT"))
			.body("property", notNullValue())
			.body("filter", notNullValue())
			.body("effectivePeriod.start", equalTo("2002-01-31T00:00:00Z"))
			//end is currently not supported
			.body("effectivePeriod.end", nullValue())
			.body("caseSensitive", equalTo(true))
			
			//no concept definitions are part of the summary
			.body("entry.resource", not(hasItem("concept")));
	}
	
	@Test
	public void GET_CodeSystem_Summary_Text() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("_id", getTestCodeSystemId())
			.queryParam("_summary", "text")
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("meta.tag.code", hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode()))
			.body("type", equalTo("searchset"))
			.body("total", equalTo(1))
			
			// only text, id, meta and mandatory
			.rootPath("entry[0].resource")
			.body("id", equalTo(getTestCodeSystemId()))
			.body("status", equalTo("draft"))
			.body("content", equalTo("complete"))
			.body("meta", notNullValue())
			.body("text", notNullValue())
			.body("count", notNullValue())
			.body("name", nullValue())
			.body("concept", nullValue()) 
			.body("copyright", nullValue())
			.body("effectivePeriod", nullValue())
			.body("url", nullValue());
	}
	
	@Test
	public void GET_CodeSystem_Summary_Data() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("_id", getTestCodeSystemId())
			.queryParam("_summary", "data")
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("meta.tag.code", hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode()))
			.body("type", equalTo("searchset"))
			.body("total", equalTo(1))
			
			.rootPath("entry[0].resource")
			// only id, meta and mandatory
			.body("id", notNullValue())
			.body("status", equalTo("draft"))
			.body("content", equalTo("complete"))
			// other fields should be null
			.body("text", nullValue())
			.body("url", nullValue())
			.body("name", nullValue())
			.body("copyright", nullValue())
			.body("count", notNullValue())
			.body("caseSignificance", nullValue())
			.body("effectivePeriod", nullValue())
			.body("text", nullValue());
	}
	
	@Test
	public void GET_CodeSystem_Summary_Count() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("_id", getTestCodeSystemId())
			.queryParam("_summary", "count")
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("meta.tag.code", not(hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode())))
			.body("total", equalTo(1))
			.body("type", equalTo("searchset"))
			.body("entry", equalTo(null));
	}
	
	@Test
	public void GET_CodeSystem_Summary_False() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("_id", getTestCodeSystemId())
			.queryParam("_summary", false)
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("meta.tag.code", not(hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode())))
			.body("total", equalTo(1))
			.body("type", equalTo("searchset"))
			
			.rootPath("entry[0].resource")
			.body("id", equalTo(getTestCodeSystemId()))
			.body("url", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("version", equalTo(getTestCodeSystemUrl()));
	}
	
	@Test
	public void GET_CodeSystem_Elements() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("_id", getTestCodeSystemId())
			.queryParam("_elements", "name", "url")
			.when().get(CODESYSTEM)
			.then().assertThat() 
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("meta.tag.code", hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode()))
			.body("total", equalTo(1))
			.body("type", equalTo("searchset"))
			// mandatory fields
			.body("entry[0].resource.status", equalTo("draft"))
			.body("entry[0].resource.content", equalTo("complete"))
			.body("entry[0].resource.id", equalTo(getTestCodeSystemId()))
			// returned because we need to calculate the concept count for the content property
			.body("entry[0].resource.count", equalTo(NUM_CONCEPTS))
			// summary and optional fields
			.body("entry[0].resource.text", nullValue())
			.body("entry[0].resource.concept", nullValue()) 
			.body("entry[0].resource.copyright", nullValue()) 
			// requested fields
			.body("entry[0].resource.name", equalTo(getTestCodeSystemId()))
			.body("entry[0].resource.url", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING));
	}
	
	@Test
	public void GET_CodeSystem_multiple_Elements_stored_in_settings() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("_id", getTestCodeSystemId())
			// Both publisher and caseSensitive are stored settings, this case tests if issues arise when 
			// the settings field is called multiple times
			.queryParam("_elements", "publisher", "caseSensitive")
			.when().get(CODESYSTEM)
			.then().assertThat() 
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("meta.tag.code", hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode()))
			.body("total", equalTo(1))
			.body("type", equalTo("searchset"))
			// mandatory fields
			.body("entry[0].resource.status", equalTo("draft"))
			.body("entry[0].resource.content", equalTo("complete"))
			.body("entry[0].resource.id", equalTo(getTestCodeSystemId()))
			// returned because we need to calculate the concept count for the content property
			.body("entry[0].resource.count", equalTo(NUM_CONCEPTS))
			// summary and optional fields
			.body("entry[0].resource.text", nullValue())
			.body("entry[0].resource.concept", nullValue()) 
			.body("entry[0].resource.copyright", nullValue()) 
			// requested fields
			.body("entry[0].resource.publisher", equalTo("SNOMED International"))
			.body("entry[0].resource.caseSensitive", equalTo(true));
	}
	
	@Test
	public void GET_CodeSystem_ElementsMixedWithSummaryFields() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("_id", getTestCodeSystemId())
			.queryParam("_elements", 
				R5ObjectFields.CodeSystem.ID, 
				R5ObjectFields.CodeSystem.META, 
				R5ObjectFields.CodeSystem.URL, 
				R5ObjectFields.CodeSystem.VERSION, 
				R5ObjectFields.CodeSystem.NAME, 
				R5ObjectFields.CodeSystem.TITLE, 
				R5ObjectFields.CodeSystem.DATE, 
				R5ObjectFields.CodeSystem.PUBLISHER)
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("meta.tag.code", hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode()))
			.body("total", equalTo(1))
			.body("type", equalTo("searchset"))
			// mandatory fields
			.body("entry[0].resource.id", equalTo(getTestCodeSystemId()))
			.body("entry[0].resource.status", equalTo("draft"))
			.body("entry[0].resource.content", equalTo("complete"))
			// requested fields
			.body("entry[0].resource.url", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("entry[0].resource.version", equalTo(getTestCodeSystemUrl()))
			.body("entry[0].resource.name", equalTo(getTestCodeSystemId()))
			.body("entry[0].resource.title", equalTo("Title of " + getTestCodeSystemId()))
			.body("entry[0].resource.date", nullValue())
			.body("entry[0].resource.publisher", equalTo("SNOMED International"));
	}
	
	@Test
	public void GET_CodeSystem_Elements_Unrecognized() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("_elements", "xyz", "abcs")
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(400)
			.body("resourceType", equalTo("OperationOutcome"))
			.body("issue.severity", hasItem("error"))
			.body("issue.code", hasItem("invalid"));
	}
	
	@Test
	public void GET_CodeSystem_Url_NoMatch() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("url", "http://unknown.com")
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("meta.tag.code", not(hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode())))
			.body("total", equalTo(0))
			.body("type", equalTo("searchset"));
	}

	@Test
	public void GET_CodeSystem_System_NoMatch() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("system", "http://unknown.com")
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("meta.tag.code", not(hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode())))
			.body("total", equalTo(0))
			.body("type", equalTo("searchset"));
	}
	
	@Test
	public void GET_CodeSystem_Version_NoMatch() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("version", "unknown-version")
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("meta.tag.code", not(hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode())))
			.body("total", equalTo(0))
			.body("type", equalTo("searchset"));
	}
	
	@Test
	public void GET_CodeSystem_Version_Match_Single() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("version", SNOMEDCT_URL + "/version/20020131")
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("meta.tag.code", not(hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode())))
			.body("total", equalTo(1))
			.body("type", equalTo("searchset"))
			.body("entry[0].resource.id", equalTo("SNOMEDCT")) // XXX: the versioned code system id is not returned in the resource
			.body("entry[0].resource.url", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("entry[0].resource.valueSet", equalTo(SNOMEDCT_URL + "/version/20020131?fhir_vs"))
			.body("entry[0].resource.version", equalTo(SNOMEDCT_URL + "/version/20020131"))
			.body("entry[0].resource.date", equalTo("2002-01-31T00:00:00Z"));
	}
	
	@Test
	public void GET_CodeSystem_Version_Match_Multiple() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("version", SNOMEDCT_URL + "/version/20020131", SNOMEDCT_URL + "/version/20200131")
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("meta.tag.code", not(hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode())))
			.body("total", equalTo(2))
			.body("type", equalTo("searchset"))
			.body("entry[0].resource.id", equalTo("SNOMEDCT")) // XXX: the versioned code system id is not returned in the resource
			.body("entry[0].resource.name", equalTo("SNOMEDCT/2002-01-31"))
			.body("entry[0].resource.url", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("entry[0].resource.valueSet", equalTo(SNOMEDCT_URL + "/version/20020131?fhir_vs"))
			.body("entry[0].resource.version", equalTo(SNOMEDCT_URL + "/version/20020131"))
			.body("entry[0].resource.date", equalTo("2002-01-31T00:00:00Z"))
			.body("entry[1].resource.id", equalTo("SNOMEDCT")) // XXX: same with this one
			.body("entry[1].resource.name", equalTo("SNOMEDCT/2020-01-31"))
			.body("entry[1].resource.url", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("entry[1].resource.valueSet", equalTo(SNOMEDCT_URL + "/version/20200131?fhir_vs"))
			.body("entry[1].resource.version", equalTo(SNOMEDCT_URL + "/version/20200131"))
			.body("entry[1].resource.date", equalTo("2020-01-31T00:00:00Z"));
	}
	
	@Test
	public void GET_CodeSystem_Status_NoMatch() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("status", "unknown")
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("meta.tag.code", not(hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode())))
			.body("total", equalTo(0))
			.body("type", equalTo("searchset"));
	}
	
	@Test
	public void GET_CodeSystem_Status_Match_Single() throws Exception {
		CodeSystemRestRequests.updateCodeSystem(getTestCodeSystemId(), Json.object("status", "mysterious"))
			.statusCode(204);
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("status", "mysterious")
			.queryParam("_sort", "id")
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("meta.tag.code", not(hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode())))
			.body("total", equalTo(1))
			.body("type", equalTo("searchset"))
			.body("entry[0].resource.id", equalTo("GET_CodeSystem_Status_Match_Single"))
			.body("entry[0].resource.url", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("entry[0].resource.version", equalTo(getTestCodeSystemUrl()))
			// This is the PublicationStatus code "mysterious" maps to
			.body("entry[0].resource.status", equalTo("unknown"));
	}
	
	@Test
	public void GET_CodeSystem_Status_Match_Multiple() throws Exception {
		CodeSystemRestRequests.updateCodeSystem(getTestCodeSystemId(), Json.object("status", "mysterious"))
			.statusCode(204);

		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("status", "active", "mysterious")
			.queryParam("_sort", "id")
			.when().get(CODESYSTEM)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("meta.tag.code", not(hasItem(FhirCodeSystems.CODING_SUBSETTED.getCode())))
			.body("total", equalTo(39))
			.body("type", equalTo("searchset"))
			.body("entry[0].resource.id", equalTo("GET_CodeSystem_Status_Match_Multiple"))
			.body("entry[0].resource.url", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("entry[0].resource.version", equalTo(getTestCodeSystemUrl()))
			// This is the PublicationStatus code "mysterious" maps to
			.body("entry[0].resource.status", equalTo("unknown"))
			.body("entry[1].resource.id", equalTo("SNOMEDCT"))
			.body("entry[1].resource.url", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("entry[1].resource.version", equalTo(SNOMEDCT_URL))
			.body("entry[1].resource.status", equalTo("active"));
	}
	
	@Test
	public void GET_CodeSystemId() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.when().get(CODESYSTEM_ID, getTestCodeSystemId())
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("CodeSystem"))
			.body("id", equalTo(getTestCodeSystemId()))
			.body("url", equalTo(FhirModelHelpers.SNOMED_BASE_URI_STRING))
			.body("version", equalTo(getTestCodeSystemUrl()))
			.body("status", equalTo("draft"));
	}
	
	//Summary-count should not be allowed for non-search type operations?
	//https://www.hl7.org/fhir/search.html#summary
	@Test
	public void GET_CodeSystemId_Summary_Count_BadRequest() {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.queryParam("_summary", "count")
			.when().get(CODESYSTEM_ID, getTestCodeSystemId())
			.then().assertThat()
			.statusCode(400)
			.body("resourceType", equalTo("OperationOutcome"))
			.body("issue.severity", hasItem("error"))
			.body("issue.code", hasItem("invalid"));
	}
	
	@Test
	public void DELETE_CodeSystem_with_x_author() {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.header("X-Author", "user")
			.when().delete(CODESYSTEM_ID, getTestCodeSystemId())
			.then().assertThat()
			.statusCode(204);
		
		Request<RepositoryContext, CommitInfos> req = RepositoryRequests
			.commitInfos()
			.prepareSearchCommitInfo()
			.filterByComment("Deleting code system " + getTestCodeSystemId())
			.build();
		
		CommitInfos commitInfos = new ResourceRepositoryRequestBuilder<CommitInfos>() {
			@Override
			public Request<RepositoryContext, CommitInfos> build() {
				return req;
			}
		 }.buildAsync().execute(Services.bus()).getSync();
	 
		assertThat(commitInfos)
		 	.hasSize(1)
		 	.first()
		 	.extracting(CommitInfo::getAuthor)
		 	.isEqualTo("user");
	}
	
	@Test
	public void DELETE_CodeSystem_without_x_author() {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.when().delete(CODESYSTEM_ID, getTestCodeSystemId())
			.then().assertThat()
			.statusCode(204);
		
		Request<RepositoryContext, CommitInfos> req = RepositoryRequests
				.commitInfos()
				.prepareSearchCommitInfo()
				.filterByComment("Deleting code system " + getTestCodeSystemId())
				.build();
			
			CommitInfos commitInfos = new ResourceRepositoryRequestBuilder<CommitInfos>() {
				@Override
				public Request<RepositoryContext, CommitInfos> build() {
					return req;
				}
			 }.buildAsync().execute(Services.bus()).getSync();
		
		assertThat(commitInfos)
		 	.hasSize(1)
		 	.first()
		 	.extracting(CommitInfo::getAuthor)
		 	.isEqualTo("snowowl");
	}
}
