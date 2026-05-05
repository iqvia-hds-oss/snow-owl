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
import org.hl7.fhir.r5.model.Enumerations.FilterOperator;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetFilterComponent;
import org.hl7.fhir.r5.model.ValueSet.ValueSetComposeComponent;

import com.b2international.commons.options.OptionsBuilder;
import com.b2international.snowowl.core.request.ConceptSearchRequestEvaluator;

/**
 * @since 10.1.0
 */
public abstract class SnomedFhirImplicitValueSetSupport {

	protected void configureValueSetQuery(ValueSet valueSet, OptionsBuilder conceptSearchOptions) {
		if (!valueSet.hasCompose()) {
			// do nothing, search all concepts
		} else {
			final ValueSetComposeComponent compose = valueSet.getCompose();
			final ConceptSetComponent firstInclude = compose.getIncludeFirstRep();
			
			if (!firstInclude.hasFilter() && !firstInclude.hasConcept() && !firstInclude.hasValueSet()) {
				/*
				 * do nothing, search all concepts (theoretically we should be retrieving system
				 * and version information from compose.include.system and compose.include.version, 
				 * but we have already received the CodeSystem resource URI to resolve concepts)
				 */
			} else {
				final ConceptSetFilterComponent firstFilter = firstInclude.getFilterFirstRep();
				
				if ("constraint".equals(firstFilter.getProperty()) && FilterOperator.EQUAL.equals(firstFilter.getOp())) {
					// Filter concepts by ECL
					conceptSearchOptions.put(ConceptSearchRequestEvaluator.OptionKey.QUERY, firstFilter.getValue());
				} else if ("constraint".equals(firstFilter.getProperty()) && FilterOperator.ISA.equals(firstFilter.getOp())) {
					// Filter concepts by ancestor
					conceptSearchOptions.put(ConceptSearchRequestEvaluator.OptionKey.ANCESTOR, firstFilter.getValue());
				} else if ("concept".equals(firstFilter.getProperty()) && FilterOperator.IN.equals(firstFilter.getOp())) {
					// filter concepts by memberOf ECL
					conceptSearchOptions.put(ConceptSearchRequestEvaluator.OptionKey.QUERY, "^" + firstFilter.getValue());
				} else {
					throw new IllegalStateException("Unsupported implicit value set compose definition: " + compose);
				}
			}
		}		
	}
	
}
