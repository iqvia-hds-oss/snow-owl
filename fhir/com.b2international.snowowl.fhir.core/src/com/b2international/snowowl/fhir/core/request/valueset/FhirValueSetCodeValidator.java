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
import org.hl7.fhir.r5.model.ValueSet.ValueSetExpansionContainsComponent;

import com.b2international.fhir.r5.operations.ValueSetValidateCodeParameters;
import com.b2international.snowowl.core.ServiceProvider;

/**
 * @since 8.0
 */
@FunctionalInterface
public interface FhirValueSetCodeValidator {

	/**
	 * Validates whether the given code conforms to the {@link ValueSet} definition or not.
	 * 
	 * @param context - context to run the validation on
	 * @param valueSet - the value set to validate the code aginst
	 * @param code - the code to validate against the value set composition
	 * @param parameters - extra validate-code operation parameters if needed for the evaluation
	 * @return a {@link ValueSetExpansionContainsComponent} if the value set contains the code, or <code>null</code> if not
	 */
	ValueSet.ValueSetExpansionContainsComponent validateCode(ServiceProvider context, ValueSet valueSet, String code, ValueSetValidateCodeParameters parameters);
	
}
