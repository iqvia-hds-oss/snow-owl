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
package com.b2international.snowowl.snomed.core.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.codesystem.CodeSystemRequests;
import com.b2international.snowowl.core.domain.Concepts;
import com.b2international.snowowl.snomed.common.SnomedConstants;
import com.b2international.snowowl.test.commons.Services;
import com.b2international.snowowl.test.commons.SnomedContentRule;

/**
 * @since 10.4.0
 */
public class SnomedGenericConceptSearchRequestTest {

	private static final ResourceURI CODESYSTEM = SnomedContentRule.SNOMEDCT.withPath("2018-01-31");
	
	@Test
	public void filterByQuery() {
		// XXX: exclusion is not exposed on REST API so test it here
		final Concepts matches = CodeSystemRequests.prepareSearchConcepts()
			.setLimit(0)
			.filterByCodeSystemUri(CODESYSTEM)
			.filterByQuery("*")
			.filterByExclusion(SnomedConstants.Concepts.ROOT_CONCEPT)
			.buildAsync()
			.execute(Services.bus())
			.getSync();
		assertThat(matches.getTotal()).isEqualTo(1887);
	}	
}
