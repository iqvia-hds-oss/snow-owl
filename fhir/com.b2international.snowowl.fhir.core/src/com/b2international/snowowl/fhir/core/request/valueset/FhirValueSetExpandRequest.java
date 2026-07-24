/*
 * Copyright 2021-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.fhir.core.request.valueset;

import org.hl7.fhir.r5.model.ValueSet;

import com.b2international.fhir.r5.operations.ValueSetExpandParameters;
import com.b2international.snowowl.core.RepositoryManager;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @since 8.0
 */
final class FhirValueSetExpandRequest extends FhirValueSetOperationRequest<ValueSet> {

	private static final long serialVersionUID = 1L;
	
	@JsonProperty
	private final ValueSetExpandParameters parameters;
	
	private final String preferredDisplay;

	public FhirValueSetExpandRequest(ValueSetExpandParameters parameters, String preferredDisplay) {
		super(parameters.getUrl() == null ? null : parameters.getUrl().asStringValue());
		this.parameters = parameters;
		this.preferredDisplay = preferredDisplay;
	}
	
	@Override
	public ValueSet doExecute(ServiceProvider context, ValueSet valueSet) {
		return context.service(RepositoryManager.class)
			.get(FhirModelHelpers.getResourceFragment(valueSet).getToolingId())
			.optionalService(FhirValueSetExpander.class)
			.orElse(FhirValueSetExpander.NOOP)
			.expand(context, valueSet, parameters, preferredDisplay);

	}

}
