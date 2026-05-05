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
package com.b2international.snowowl.snomed.core.rest.perf;

import static com.b2international.snowowl.snomed.core.rest.SnomedRestFixtures.createNewConcept;

import org.junit.Test;

import com.b2international.snowowl.snomed.core.rest.AbstractSnomedApiTest;

/**
 * @since 4.7
 */
public class SnomedConceptCreatePerformanceTest extends AbstractSnomedApiTest {
	
	@Test
	public void createConcept() throws Exception {
		for (int i = 0; i < 10; i++) {
			createNewConcept(branchPath);
		}
	}
	
}
