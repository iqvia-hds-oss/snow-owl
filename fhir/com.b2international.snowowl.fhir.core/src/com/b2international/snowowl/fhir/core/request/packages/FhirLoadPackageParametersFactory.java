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

import com.b2international.fhir.operations.OperationParametersFactory;

/**
 * @since 10.1.0
 */
public enum FhirLoadPackageParametersFactory implements OperationParametersFactory {
	INSTANCE;

	@Override
	public com.b2international.fhir.r4.operations.BaseParameters create(org.hl7.fhir.r4.model.Parameters parameters) {
		return new com.b2international.snowowl.fhir.core.request.packages.r4.FhirLoadPackageParameters(parameters);
	}

	@Override
	public com.b2international.fhir.r4b.operations.BaseParameters create(org.hl7.fhir.r4b.model.Parameters parameters) {
		return new com.b2international.snowowl.fhir.core.request.packages.r4b.FhirLoadPackageParameters(parameters);
	}

	@Override
	public com.b2international.fhir.r5.operations.BaseParameters create(org.hl7.fhir.r5.model.Parameters parameters) {
		return new com.b2international.snowowl.fhir.core.request.packages.r5.FhirLoadPackageParameters(parameters);
	}

}
