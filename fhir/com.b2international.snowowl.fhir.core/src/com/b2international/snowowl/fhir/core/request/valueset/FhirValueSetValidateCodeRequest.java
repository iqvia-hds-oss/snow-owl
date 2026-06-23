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

import com.b2international.fhir.r5.operations.ValueSetValidateCodeParameters;
import com.b2international.fhir.r5.operations.ValueSetValidateCodeResultParameters;
import com.b2international.snowowl.core.RepositoryManager;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.R5ObjectFields;
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
		final String system = getValidationSystem(valueSet);
		final String version = getValidationVersion(valueSet);
		
		if (Strings.isNullOrEmpty(code)) {
			throw new BadRequestException("'code' parameter is required to perform the operation.", "code");
		}
		
		final ValueSet.ValueSetExpansionContainsComponent valueSetExpansionContainsComponent = context.service(RepositoryManager.class)
				.get(FhirModelHelpers.getResourceFragment(valueSet).getToolingId())
				.optionalService(FhirValueSetCodeValidator.class)
				.orElseThrow(() -> new BadRequestException("No validate-code implementation is available to handle valueSet: " + getUrl()))
				.validateCode(context, valueSet, code, parameters);
		
		if (valueSetExpansionContainsComponent == null) {
			
			final String message = String.format("Could not find code '%s' in ValueSet '%s'.", code, valueSet.getUrl());
			
			// Found a member that uses the requested code: verify the code system, version, display label via a code system lookup request
			return new ValueSetValidateCodeResultParameters()
					.setResult(false)
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
					.setDisplay(valueSetExpansionContainsComponent.getDisplay())
					.setCode(valueSetExpansionContainsComponent.getCode())
					.setSystem(Strings.isNullOrEmpty(valueSetExpansionContainsComponent.getSystem())
							? system
							: valueSetExpansionContainsComponent.getSystem())
					.setVersion(Strings.isNullOrEmpty(valueSetExpansionContainsComponent.getVersion())
							? version
							: valueSetExpansionContainsComponent.getVersion()
);
		}
	}
	

	private String getValidationSystem(ValueSet valueSet) {
		if (parameters.getSystem() != null) {
			return parameters.getSystem().getValue();
		}
	
	if (valueSet.hasCompose() && valueSet.getCompose().hasInclude()) {
		final String system = valueSet.getCompose().getIncludeFirstRep().getSystem();
			if (!Strings.isNullOrEmpty(system)) {
				return system;
			}
		}
		return valueSet.getUrl();
	}
	

	private String getValidationVersion(ValueSet valueSet) {
		if (parameters.getSystemVersion() != null) {
			return parameters.getSystemVersion().getValue();
		}
	
		if (valueSet.hasCompose() && valueSet.getCompose().hasInclude()) {
			final String version = valueSet.getCompose().getIncludeFirstRep().getVersion();
			
			if (!Strings.isNullOrEmpty(version)) {
				return version;
			}
		}
	
		final Object codeSystemVersion = valueSet.getUserData(R5ObjectFields.ValueSet.UserData.CODE_SYSTEM_VERSION);
		
		if (codeSystemVersion instanceof String version && !Strings.isNullOrEmpty(version)) {
			return version;
		}

		return valueSet.getVersion();
	}
}
