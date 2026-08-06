/*
 * Copyright 2021-2024 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.fhir.core.request.conceptmap;

import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ConceptMap;

import com.b2international.fhir.r5.operations.ConceptMapTranslateParameters;
import com.b2international.fhir.r5.operations.ConceptMapTranslateResultParameters;
import com.b2international.snowowl.core.ServiceProvider;

/**
 * @since 8.0
 */
@FunctionalInterface
public interface FhirConceptMapTranslator {

	FhirConceptMapTranslator NOOP = (context, conceptMap, request) -> new ConceptMapTranslateResultParameters().setResult(false).setMessage("N/A"); 
	
	/**
	 * @param context - the service context where to execute the operation
	 * @param conceptMap - restricts the scope of the translate operation into a single map
	 * @param parameters - translate in parameters
	 * @return
	 */
	ConceptMapTranslateResultParameters translate(ServiceProvider context, ConceptMap conceptMap, ConceptMapTranslateParameters parameters);
	
	default Coding getSourceCoding(ConceptMapTranslateParameters parameters) {
		if (parameters.getSourceCode() != null) {
			return new Coding()
				.setSystemElement(parameters.getSystem())
				.setVersionElement(parameters.getVersion())
				.setCodeElement(parameters.getSourceCode());
		} else if (parameters.getSourceCoding() != null) {
			return parameters.getSourceCoding();
		} else if (parameters.getSourceCodeableConcept() != null && parameters.getSourceCodeableConcept().getCodingFirstRep() != null) {
			return parameters.getSourceCodeableConcept().getCodingFirstRep();
		} else {
			return new Coding();
		}
	}
	
	default Coding getTargetCoding(ConceptMapTranslateParameters parameters) {
		if (parameters.getTargetCode() != null) {
			// XXX: there is no targetVersion
			return new Coding()
				.setSystemElement(parameters.getTargetSystem())
				.setCodeElement(parameters.getTargetCode());
		} else if (parameters.getTargetCoding() != null) {
			return parameters.getTargetCoding();
		} else if (parameters.getTargetCodeableConcept() != null && parameters.getTargetCodeableConcept().getCodingFirstRep() != null) {
			return parameters.getTargetCodeableConcept().getCodingFirstRep();
		} else {
			return new Coding();
		}
	}
}
