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
package com.b2international.snowowl.snomed.fhir;

import org.hl7.fhir.r5.model.ValueSet;

import com.b2international.commons.http.AcceptLanguageHeader;
import com.b2international.commons.options.Options;
import com.b2international.commons.options.OptionsBuilder;
import com.b2international.fhir.r5.operations.ValueSetValidateCodeParameters;
import com.b2international.snowowl.core.*;
import com.b2international.snowowl.core.request.ConceptSearchRequestEvaluator;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.R5ObjectFields;
import com.b2international.snowowl.fhir.core.request.codesystem.FhirCodeSystemOperationRequest;
import com.b2international.snowowl.fhir.core.request.valueset.FhirValueSetCodeValidator;

/**
 * @since 10.1.0
 */
public final class SnomedFhirValueSetCodeValidator extends SnomedFhirImplicitValueSetSupport implements FhirValueSetCodeValidator {

	@Override
	public ValueSet.ValueSetExpansionContainsComponent validateCode(ServiceProvider context, ValueSet valueSet, String code, ValueSetValidateCodeParameters parameters) {
		// XXX since this is an implicit VS, and resource stored in the VS here is a CodeSystem referring to the proper SNOMED CT Edition
		final ResourceFragment resource = FhirModelHelpers.getResourceFragment(valueSet);
		ResourceURI codeSystemUri = resource.getResourceURI();
		
		if (parameters.getDate() != null) {
			codeSystemUri = codeSystemUri.withTimestampPart("@" + Long.toString(parameters.getDate().getValue().getTime()));
		}
		
		// for performance reasons, running the raw evaluator here as we already identified the CodeSystem to evaluate it on
		OptionsBuilder conceptSearchOptions = Options.builder()
				.put(ConceptSearchRequestEvaluator.OptionKey.ID, code)
				.put(ConceptSearchRequestEvaluator.OptionKey.LIMIT, 1)
				// SNOMED only preferred display support (VS should always use FSN)
				.put(ConceptSearchRequestEvaluator.OptionKey.DISPLAY, "FSN")
				.put(ConceptSearchRequestEvaluator.OptionKey.LOCALES, AcceptLanguageHeader.parseHeader(FhirCodeSystemOperationRequest.compactLocale(parameters.getDisplayLanguage())));
		
		configureValueSetQuery(valueSet, conceptSearchOptions);
		
		// seed already fetched resource information to prevent refetching the metadata
		final ServiceProvider searchContext = context.inject().bind(ResourceFragment.class, resource).build();
		final Repository codeSystemToolingRepository = context.service(RepositoryManager.class).get(resource.getToolingId());
		return codeSystemToolingRepository.service(ConceptSearchRequestEvaluator.class)
				.evaluate(codeSystemUri, searchContext, conceptSearchOptions.build())
				.first()
				.map(concept -> {
					final String version = valueSet.getUserString(R5ObjectFields.ValueSet.UserData.CODE_SYSTEM_VERSION);
					return new ValueSet.ValueSetExpansionContainsComponent()
						.setCode(code)
						.setDisplay(concept.getTerm())
						.setSystem(FhirModelHelpers.SNOMED_BASE_URI_STRING)
						.setVersion(version)
						;					
				})
				.orElse(null);
	}

}
