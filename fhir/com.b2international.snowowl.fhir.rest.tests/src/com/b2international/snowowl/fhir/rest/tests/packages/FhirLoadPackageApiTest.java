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
package com.b2international.snowowl.fhir.rest.tests.packages;

import static com.b2international.snowowl.test.commons.fhir.FhirApiHelpers.APPLICATION_FHIR_JSON;
import static com.b2international.snowowl.test.commons.fhir.FhirApiHelpers.FHIR_ROOT_CONTEXT;
import static com.b2international.snowowl.test.commons.fhir.FhirApiHelpers.LOAD_PACKAGE;
import static com.b2international.snowowl.test.commons.fhir.FhirApiHelpers.toJson;
import static com.b2international.snowowl.test.commons.rest.RestExtensions.givenAuthenticatedRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.SortedSet;
import java.util.TreeSet;

import org.elasticsearch.core.List;
import org.hl7.fhir.r5.model.CodeSystem;
import org.junit.Test;

import com.b2international.fhir.r5.operations.LoadPackageParameters;
import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.util.PlatformUtil;
import com.b2international.snowowl.fhir.core.request.FhirResourceUpdateResult;
import com.b2international.snowowl.fhir.core.request.codesystem.FhirCodeSystemWriteSupport;
import com.b2international.snowowl.fhir.rest.tests.FhirRestTest;

/**
 * @since 10.1.0
 */
public class FhirLoadPackageApiTest extends FhirRestTest implements FhirCodeSystemWriteSupport {

	@Override
	public void before() {
		// register writer to simulate the actual import of the various resources
		ApplicationContext.getInstance().registerService(FhirCodeSystemWriteSupport.class, this);
	}
	
	@Override
	public void after() {
		ApplicationContext.getInstance().unregisterService(FhirCodeSystemWriteSupport.class);
	}
	
	private SortedSet<String> visitedResources = new TreeSet<String>();
	
	@Test
	public void downloadFromRegistry() throws Exception {
		var parameters = new LoadPackageParameters()
				.setName("hl7.fhir.r4.core")
				.setVersion("4.0.1");
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.contentType(APPLICATION_FHIR_JSON)
			.body(toJson(parameters.getParameters()))
			.when().post(LOAD_PACKAGE)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("success"))
			.body("parameter[0].valueBoolean", equalTo(true))
			.body("parameter[1].name", equalTo("numberOfLoadedCodeSystems"))
			.body("parameter[1].valueInteger", equalTo(1061))
			.body("parameter[2].name", equalTo("numberOfLoadedValueSets"))
			.body("parameter[2].valueInteger", equalTo(0))
			.body("parameter[3].name", equalTo("numberOfLoadedConceptMaps"))
			.body("parameter[3].valueInteger", equalTo(0));
		
		assertThat(visitedResources).contains("abstract-types");
	}
	
	@Test
	public void manualUpload() throws Exception {
		final Path packageFile = PlatformUtil.toAbsolutePath(FhirLoadPackageApiTest.class, "hl7.fhir.r4.core-4.0.1.tgz");
		try (final InputStream inputStream = Files.newInputStream(packageFile)) {
			givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
				.multiPart("file", packageFile.getFileName().toString(), inputStream)
				.post(LOAD_PACKAGE)
				.then().assertThat()
				.statusCode(200)
				.body("resourceType", equalTo("Parameters"))
				.body("parameter[0].name", equalTo("success"))
				.body("parameter[0].valueBoolean", equalTo(true))
				.body("parameter[1].name", equalTo("numberOfLoadedCodeSystems"))
				.body("parameter[1].valueInteger", equalTo(1061))
				.body("parameter[2].name", equalTo("numberOfLoadedValueSets"))
				.body("parameter[2].valueInteger", equalTo(0))
				.body("parameter[3].name", equalTo("numberOfLoadedConceptMaps"))
				.body("parameter[3].valueInteger", equalTo(0));
			
			assertThat(visitedResources).contains("abstract-types");
		}
	}
	
	@Test
	public void filterByUrl() throws Exception {
		var parameters = new LoadPackageParameters()
				.setName("hl7.fhir.r4.core")
				.setVersion("4.0.1")
				// import only a single resource from this package
				.setResourceUrl(List.of("http://hl7.org/fhir/abstract-types"));
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.contentType(APPLICATION_FHIR_JSON)
			.body(toJson(parameters.getParameters()))
			.when().post(LOAD_PACKAGE)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("success"))
			.body("parameter[0].valueBoolean", equalTo(true))
			.body("parameter[1].name", equalTo("numberOfLoadedCodeSystems"))
			.body("parameter[1].valueInteger", equalTo(1))
			.body("parameter[2].name", equalTo("numberOfLoadedValueSets"))
			.body("parameter[2].valueInteger", equalTo(0))
			.body("parameter[3].name", equalTo("numberOfLoadedConceptMaps"))
			.body("parameter[3].valueInteger", equalTo(0));
		
		assertThat(visitedResources).containsOnly("abstract-types");
	}
	
	@Override
	public FhirResourceUpdateResult update(ServiceProvider context, CodeSystem fhirCodeSystem, String author, String owner, String ownerProfileName,
			LocalDate defaultEffectiveDate, String bundleId) {
		visitedResources.add(fhirCodeSystem.getId());
		return FhirResourceUpdateResult.created(bundleId, "", "");
	}
	
}
