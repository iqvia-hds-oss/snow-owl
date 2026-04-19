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
package com.b2international.snowowl.fhir.core.request.packages;

import org.hl7.fhir.r5.model.BooleanType;
import org.hl7.fhir.r5.model.Parameters;

import com.b2international.fhir.r5.operations.BaseParameters;

/**
 * @since 10.1.0
 */
public final class FhirLoadPackageResultParameters extends BaseParameters {

	private static final String PARAM_SUCCESS = "success";

	public FhirLoadPackageResultParameters() {
		this(new Parameters());
	}
	
	public FhirLoadPackageResultParameters(Parameters parameters) {
		super(parameters);
	}
	
	public BooleanType getSuccess() {
		return getParameterValue(PARAM_SUCCESS, Parameters.ParametersParameterComponent::getValueBooleanType);
	}
	
	public FhirLoadPackageResultParameters setSuccess(Boolean success) {
		return setSuccess(new BooleanType(success));
	}
	
	public FhirLoadPackageResultParameters setSuccess(BooleanType success) {
		addParameter(PARAM_SUCCESS, success);
		return this;
	}
	
}
