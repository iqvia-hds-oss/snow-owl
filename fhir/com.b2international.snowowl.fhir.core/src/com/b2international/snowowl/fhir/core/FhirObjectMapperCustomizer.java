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
package com.b2international.snowowl.fhir.core;

import com.b2international.fhir.r5.formats.FhirR5JsonModule;
import com.b2international.snowowl.core.plugin.Component;
import com.b2international.snowowl.core.repository.ObjectMapperCustomizer;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 10.0.1
 */
@Component
public class FhirObjectMapperCustomizer implements ObjectMapperCustomizer {

	@Override
	public void customize(final ObjectMapper objectMapper) {
		objectMapper.registerModule(new FhirR5JsonModule());
	}
}
