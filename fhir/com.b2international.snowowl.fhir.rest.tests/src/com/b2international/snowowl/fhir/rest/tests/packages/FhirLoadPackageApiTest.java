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

import static com.b2international.snowowl.test.commons.rest.RestExtensions.givenAuthenticatedRequest;
import static org.hamcrest.CoreMatchers.equalTo;

import java.time.LocalDate;

import org.hl7.fhir.r5.model.CodeSystem;
import org.junit.Test;

import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.util.PlatformUtil;
import com.b2international.snowowl.fhir.core.request.FhirResourceUpdateResult;
import com.b2international.snowowl.fhir.core.request.codesystem.FhirCodeSystemWriteSupport;
import com.b2international.snowowl.fhir.core.request.packages.FhirLoadPackageParameters;
import com.b2international.snowowl.fhir.rest.tests.FhirRestTest;

/**
 * @since 10.1.0
 */
public class FhirLoadPackageApiTest extends FhirRestTest {

	@Override
	public void before() {
		// register writer to only simulate the actual import of the various resources
		ApplicationContext.getInstance().registerService(FhirCodeSystemWriteSupport.class, new FhirCodeSystemWriteSupport() {
			@Override
			public FhirResourceUpdateResult update(ServiceProvider context, CodeSystem fhirCodeSystem, String author, String owner, String ownerProfileName,
					LocalDate defaultEffectiveDate, String bundleId) {
				return FhirResourceUpdateResult.created(bundleId);
			}
		});
	}
	
	@Test
	public void downloadFromRegistry() throws Exception {
		var parameters = new FhirLoadPackageParameters()
				.setName("hl7.fhir.r4.examples")
				.setVersion("4.0.1");
		
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.contentType(APPLICATION_FHIR_JSON)
			.body(toJson(parameters.getParameters()))
			.when().post(LOAD_PACKAGE)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("success"))
			.body("parameter[0].valueBoolean", equalTo(true));
	}
	
	@Test
	public void manualUpload() throws Exception {
		givenAuthenticatedRequest(FHIR_ROOT_CONTEXT)
			.multiPart(PlatformUtil.toAbsolutePath(FhirLoadPackageApiTest.class, "hl7.fhir.r4.core-4.0.1.tgz").toFile())
			.post(LOAD_PACKAGE)
			.then().assertThat()
			.statusCode(200)
			.body("resourceType", equalTo("Parameters"))
			.body("parameter[0].name", equalTo("success"))
			.body("parameter[0].valueBoolean", equalTo(true));
	}
	
}
