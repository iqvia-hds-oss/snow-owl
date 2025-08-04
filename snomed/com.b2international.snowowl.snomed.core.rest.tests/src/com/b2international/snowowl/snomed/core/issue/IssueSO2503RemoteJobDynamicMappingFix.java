/*
 * Copyright 2017-2025 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.snomed.core.issue;

import static com.b2international.snowowl.test.commons.codesystem.CodeSystemRestRequests.createCodeSystem;
import static com.b2international.snowowl.test.commons.codesystem.CodeSystemVersionRestRequests.getNextAvailableEffectiveDate;

import java.time.LocalDate;

import org.junit.Test;

import com.b2international.snowowl.core.codesystem.CodeSystem;
import com.b2international.snowowl.core.jobs.JobRequests;
import com.b2international.snowowl.core.request.ResourceRequests;
import com.b2international.snowowl.snomed.core.rest.AbstractSnomedApiTest;

/**
 * @since 5.11.3
 */
public class IssueSO2503RemoteJobDynamicMappingFix extends AbstractSnomedApiTest {

	@Test
	public void verify() throws Exception {
		// create a codesystem to test on
		String codeSystemId = createCodeSystem(branchPath, "SNOMEDCT-ISSUESO2503");
		
		// 1. create a version with a datelike versionId
		LocalDate nextAvailableEffectiveDate1 = getNextAvailableEffectiveDate(codeSystemId);
		ResourceRequests.prepareNewVersion()
			.setResource(CodeSystem.uri(codeSystemId))
			// XXX use default format, ES will likely try to convert this to a date field, unless we disable it in the mapping
			.setVersion(nextAvailableEffectiveDate1.toString())
			.setEffectiveTime(nextAvailableEffectiveDate1)
			.buildAsync()
			.runAsJob("Creating version with datelike versionId")
			.execute(getBus())
			.then(this::waitDone)
			.thenWith(unused -> {
				// 2. create another version with a non-datelike versionId
				LocalDate nextAvailableEffectiveDate2 = getNextAvailableEffectiveDate(codeSystemId);
				return ResourceRequests.prepareNewVersion()
					.setResource(CodeSystem.uri(codeSystemId))
					.setVersion("xx-" + nextAvailableEffectiveDate2.toString())
					.setEffectiveTime(nextAvailableEffectiveDate2)
					.buildAsync()
					.runAsJob("Creating version with non-datelike versionId")
					.execute(getBus());
			})
			.then(this::waitDone)
			.getSync();
		// 3. second step either has failed with index exception or the job is not available via the remote job API thus it throws a NotFoundException on first get call
	}

	private Object waitDone(String jobId) {
		return JobRequests.waitForJob(getBus(), jobId, 50);
	}
	
}
