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
package com.b2international.snowowl.fhir.rest.tests.codesystem;

import static com.b2international.snowowl.test.commons.fhir.FhirApiHelpers.CODESYSTEM_ID_HISTORY;
import static com.b2international.snowowl.test.commons.fhir.FhirApiHelpers.CODESYSTEM_ID_HISTORY_VERSION;
import static com.b2international.snowowl.test.commons.fhir.FhirApiHelpers.FHIR_ROOT_CONTEXT;
import static com.b2international.snowowl.test.commons.rest.RestExtensions.givenAuthenticatedRequest;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.Map;

import org.junit.Test;

import com.b2international.snowowl.fhir.core.FhirHistorySort;
import com.b2international.snowowl.fhir.rest.tests.FhirRestTest;

import io.restassured.response.ValidatableResponse;

/**
 * FHIR Resource History Tests:
 * <ul>
 * 	<li>FHIR /CodeSystem/{id}/_history</li>
 * 	<li>FHIR /CodeSystem/{id}/_history/{version}</li>
 * </ul>
 * 
 * @since 10.3
 */
public class FhirCodeSystemHistoryTest extends FhirRestTest {
	
	private ValidatableResponse assertGetHistory(String id) {
		return assertGetHistory(id, Map.of());
	}
	
	private ValidatableResponse assertGetHistory(String id, Map<String, String> headers) {
		return givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.headers(headers)
			.when()
			.get(CODESYSTEM_ID_HISTORY, id)
			.then().assertThat();
	}
	
	private ValidatableResponse assertGetVersion(String id, String version) {
		return givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.when()
			.get(CODESYSTEM_ID_HISTORY_VERSION, id, version)
			.then().assertThat();
	}
	
	@Test
	public void GET_History() throws Exception {
		assertGetHistory("SNOMEDCT")
			.statusCode(200)
			.body("resourceType", equalTo("Bundle"))
			.body("entry.resource.version", contains(
				equalTo(SNOMEDCT_URL + "/version/20200131"),
				equalTo(SNOMEDCT_URL + "/version/20190731"),
				equalTo(SNOMEDCT_URL + "/version/20190131"),
				equalTo(SNOMEDCT_URL + "/version/20180731"),
				equalTo(SNOMEDCT_URL + "/version/20180131"),
				equalTo(SNOMEDCT_URL + "/version/20170731"),
				equalTo(SNOMEDCT_URL + "/version/20170131"),
				equalTo(SNOMEDCT_URL + "/version/20160731"),
				equalTo(SNOMEDCT_URL + "/version/20160131"),
				equalTo(SNOMEDCT_URL + "/version/20150731")
			))
			.body("entry.fullUrl", contains(
				endsWith("/CodeSystem/SNOMEDCT/_history/2020-01-31"),
				endsWith("/CodeSystem/SNOMEDCT/_history/2019-07-31"),
				endsWith("/CodeSystem/SNOMEDCT/_history/2019-01-31"),
				endsWith("/CodeSystem/SNOMEDCT/_history/2018-07-31"),
				endsWith("/CodeSystem/SNOMEDCT/_history/2018-01-31"),
				endsWith("/CodeSystem/SNOMEDCT/_history/2017-07-31"),
				endsWith("/CodeSystem/SNOMEDCT/_history/2017-01-31"),
				endsWith("/CodeSystem/SNOMEDCT/_history/2016-07-31"),
				endsWith("/CodeSystem/SNOMEDCT/_history/2016-01-31"),
				endsWith("/CodeSystem/SNOMEDCT/_history/2015-07-31")
			));
	}
	
	@Test
	public void GET_History_Count() throws Exception {
		assertGetHistory("SNOMEDCT", Map.of("_count", "3"))
			.statusCode(200)
			.body("entry.resource.version", contains(
				equalTo(SNOMEDCT_URL + "/version/20200131"),
				equalTo(SNOMEDCT_URL + "/version/20190731"),
				equalTo(SNOMEDCT_URL + "/version/20190131")
			));
	}
	
	@Test
	public void GET_History_Sort_Descending() throws Exception {
		assertGetHistory("SNOMEDCT", Map.of("_sort", FhirHistorySort.LAST_UPDATED_DESCENDING))
			.statusCode(200)
			.body("entry.resource.version", contains(
				equalTo(SNOMEDCT_URL + "/version/20200131"),
				equalTo(SNOMEDCT_URL + "/version/20190731"),
				equalTo(SNOMEDCT_URL + "/version/20190131"),
				equalTo(SNOMEDCT_URL + "/version/20180731"),
				equalTo(SNOMEDCT_URL + "/version/20180131"),
				equalTo(SNOMEDCT_URL + "/version/20170731"),
				equalTo(SNOMEDCT_URL + "/version/20170131"),
				equalTo(SNOMEDCT_URL + "/version/20160731"),
				equalTo(SNOMEDCT_URL + "/version/20160131"),
				equalTo(SNOMEDCT_URL + "/version/20150731")
			));
	}
	
	@Test
	public void GET_History_Sort_Ascending() throws Exception {
		assertGetHistory("SNOMEDCT", Map.of("_sort", FhirHistorySort.LAST_UPDATED_ASCENDING))
			.statusCode(200)
			.body("entry.resource.version", contains(
				equalTo(SNOMEDCT_URL + "/version/20020131"),
				equalTo(SNOMEDCT_URL + "/version/20020731"),
				equalTo(SNOMEDCT_URL + "/version/20030131"),
				equalTo(SNOMEDCT_URL + "/version/20030731"),
				equalTo(SNOMEDCT_URL + "/version/20040131"),
				equalTo(SNOMEDCT_URL + "/version/20040731"),
				equalTo(SNOMEDCT_URL + "/version/20050131"),
				equalTo(SNOMEDCT_URL + "/version/20050731"),
				equalTo(SNOMEDCT_URL + "/version/20060131"),
				equalTo(SNOMEDCT_URL + "/version/20060731")
			));
	}
	
	@Test
	public void GET_History_Since() throws Exception {
		// Extract second latest version
		String lastUpdated = assertGetVersion("SNOMEDCT", "2019-07-31")
			.statusCode(200)
			.extract()
			.path("meta.lastUpdated");
		
		assertGetHistory("SNOMEDCT", Map.of("_since", lastUpdated))
			.statusCode(200)
			.body("entry.resource.version", contains(
				equalTo(SNOMEDCT_URL + "/version/20200131"),
				equalTo(SNOMEDCT_URL + "/version/20190731")
			));
	}
	
	@Test()
	public void GET_History_At() throws Exception {
		// Extract second version
		String lastUpdated = assertGetVersion("SNOMEDCT", "2002-07-31")
				.statusCode(200)
				.extract()
				.path("meta.lastUpdated");
		// XXX: at should probably return only a single revision that was valid as of that time.
		assertGetHistory("SNOMEDCT", Map.of("_at", lastUpdated))
			.statusCode(200)
			.body("entry.resource.version", contains(
				equalTo(SNOMEDCT_URL + "/version/20020731"),
				equalTo(SNOMEDCT_URL + "/version/20020131")
			));
	}
	
	@Test
	public void GET_Version() throws Exception {
		assertGetVersion("SNOMEDCT", "2019-07-31")
			.statusCode(200)
			.body("resourceType", equalTo("CodeSystem"))
			.body("id", equalTo("SNOMEDCT"))
			.body("version", equalTo(SNOMEDCT_URL + "/version/20190731"))
			.body("status", equalTo("active"))
			.body("count", equalTo(1928))
			.body("effectivePeriod.start", equalTo("2019-07-31T00:00:00Z"));
	}
	
	@Test
	public void GET_Version_LATEST() throws Exception {
		assertGetVersion("SNOMEDCT", "LATEST")
			.statusCode(200)
			.body("resourceType", equalTo("CodeSystem"))
			.body("id", equalTo("SNOMEDCT"))
			.body("version", equalTo(SNOMEDCT_URL + "/version/20200131"))
			.body("status", equalTo("active"))
			.body("count", equalTo(1943))
			.body("effectivePeriod.start", equalTo("2020-01-31T00:00:00Z"));
	}
	
	@Test
	public void GET_Version_HEAD() throws Exception {
		assertGetVersion("SNOMEDCT", "HEAD")
			.statusCode(200)
			.body("resourceType", equalTo("CodeSystem"))
			.body("id", equalTo("SNOMEDCT"))
			.body("version", equalTo(SNOMEDCT_URL))
			.body("status", equalTo("active"))
			.body("count", equalTo(1943))
			.body("effectivePeriod", nullValue());
	}
}
