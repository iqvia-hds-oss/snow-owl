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
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;

import com.b2international.fhir.r5.operations.ValueSetValidateCodeParameters;
import com.b2international.fhir.r5.operations.ValueSetValidateCodeResultParameters;
import com.b2international.snowowl.core.RepositoryManager;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.exceptions.BadRequestException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;

/**
 * @since 8.0
 */
final class FhirValueSetValidateCodeRequest extends FhirValueSetOperationRequest<ValueSetValidateCodeResultParameters> {

	private static final long serialVersionUID = 2L;

	@JsonProperty
	private final ValueSetValidateCodeParameters parameters;
	
	public FhirValueSetValidateCodeRequest(ValueSetValidateCodeParameters parameters) {
		super(parameters.getUrl() == null ? null : parameters.getUrl().asStringValue());
		this.parameters = parameters;
	}
	
	@Override
	public ValueSetValidateCodeResultParameters doExecute(ServiceProvider context, ValueSet valueSet) {
		
		// TODO support coding and codeable concept parameters as well to get the referenced code
		final String code = parameters.getCode() == null ? null : parameters.getCode().getCode();
		
		if (Strings.isNullOrEmpty(code)) {
			throw new BadRequestException("'code' parameter is required to perform the operation.", "code");
		}
		
		// TODO have an 'inferSystem' flag to infer the system from the url (the parameter is planned to be added in R6)
		// Currently the server always tries to infer the system param from the url
		String system = parameters.getSystem() != null ? parameters.getSystem().getValue() : null;
		String version = parameters.getSystemVersion() != null ? parameters.getSystemVersion().getValue() : null;
		
		final ValueSet.ValueSetExpansionContainsComponent valueSetExpansionContainsComponent = context.service(RepositoryManager.class)
				.get(FhirModelHelpers.getResourceFragment(valueSet).getToolingId())
				.optionalService(FhirValueSetCodeValidator.class)
				.orElseThrow(() -> new BadRequestException("No validate-code implementation is available to handle valueSet: " + getUrl()))
				.validateCode(context, valueSet, code, parameters);
		
		if (valueSetExpansionContainsComponent == null) {
			
			ConceptSetComponent compose = valueSet.getCompose().getIncludeFirstRep();
			
			system = compose.getSystem();
			version = compose.getVersion();
			
			final String message = String.format("Could not find code '%s' in ValueSet '%s'.", code, valueSet.getUrl());
			
			return new ValueSetValidateCodeResultParameters()
					.setResult(false)
					.setCode(code)
					.setSystem(system)
					.setVersion(version)
					.setMessage(message);
		} else {
			
			// TODO add system and version validation here?
			// TODO add display validation here?
			
			// rely on the returned contains component information to check whether the included code is correct, do NOT fetch extra data here
			return new ValueSetValidateCodeResultParameters()
					.setResult(true)
					.setMessage("OK")
					.setCode(valueSetExpansionContainsComponent.getCode())
					.setSystem(valueSetExpansionContainsComponent.getSystem())
					.setVersion(valueSetExpansionContainsComponent.getVersion())
					.setDisplay(valueSetExpansionContainsComponent.getDisplay());
		}
	}
}
