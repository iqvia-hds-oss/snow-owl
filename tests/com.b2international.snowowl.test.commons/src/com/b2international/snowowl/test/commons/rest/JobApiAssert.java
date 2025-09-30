/*
 * Copyright 2025 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.test.commons.rest;

import static com.b2international.snowowl.test.commons.rest.RestExtensions.*;
import com.b2international.snowowl.core.jobs.RemoteJobEntry;
import com.b2international.snowowl.test.commons.ApiTestConstants;

import io.restassured.response.ValidatableResponse;

/**
 * @since 9.8.0
 */
public class JobApiAssert {

	public static ValidatableResponse assertGetJob(String jobId) {
		return givenAuthenticatedRequest(ApiTestConstants.JOBS_API)
			.contentType(JSON_UTF8)
			.accept(JSON_UTF8)
			.get("/{jobId}", jobId)
			.then()
			.assertThat();
	}
	
	public static RemoteJobEntry getJob(String jobId) {
		return assertGetJob(jobId).extract().as(RemoteJobEntry.class);
	}
	
}
