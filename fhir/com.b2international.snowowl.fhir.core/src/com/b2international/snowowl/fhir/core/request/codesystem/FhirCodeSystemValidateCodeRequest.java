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
package com.b2international.snowowl.fhir.core.request.codesystem;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;

import com.b2international.fhir.r5.operations.CodeSystemValidateCodeParameters;
import com.b2international.fhir.r5.operations.CodeSystemValidateCodeResultParameters;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.codesystem.CodeSystemRequests;
import com.b2international.snowowl.core.domain.Concept;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;

import jakarta.validation.constraints.NotNull;

/**
 * @since 8.0
 */
final class FhirCodeSystemValidateCodeRequest extends FhirRequest<CodeSystemValidateCodeResultParameters> {

	private static final long serialVersionUID = 1L;

	@NotNull
	@JsonProperty
	private final CodeSystemValidateCodeParameters parameters;
	
	FhirCodeSystemValidateCodeRequest(CodeSystemValidateCodeParameters parameters) {
		super(parameters.extractUrl(), parameters.extractVersion());
		this.parameters = parameters;
	}

	@Override
	public CodeSystemValidateCodeResultParameters doExecute(ServiceProvider context, CodeSystem codeSystem) {
		final Set<Coding> codings = collectCodingsToValidate(parameters);
		
		final String displayLanguage = compactLocale(parameters.getDisplayLanguage());
		ResourceURI codeSystemUri = FhirModelHelpers.resourceUriFrom(codeSystem);
		
		if (parameters.getDate() != null) {
			codeSystemUri = codeSystemUri.withTimestampPart("@" + Long.toString(parameters.getDate().getValue().getTime()));
		}
		
		final Map<String, Coding> codingsById = codings.stream()
			.collect(Collectors.toMap(
				c -> c.getCode(), 
				c -> c));

		final Map<String, Concept> conceptsById = CodeSystemRequests.prepareSearchConcepts()
			.setLimit(codingsById.keySet().size())
			.filterByCodeSystemUri(codeSystemUri)
			.filterByIds(codingsById.keySet())
			.setLocales(displayLanguage)
			.buildAsync()
			.execute(context)
			.stream()
			.collect(Collectors.toMap(
				c -> c.getId(), 
				c -> c));
		
		// Check if both Maps have the same keys and report if not
		Set<String> missingConceptIds = Sets.difference(codingsById.keySet(), conceptsById.keySet());
		
		if (!missingConceptIds.isEmpty()) {
			return new CodeSystemValidateCodeResultParameters()
				.setResult(false)
				.setMessage(String.format("Could not find code%s '%s'.", missingConceptIds.size() == 1 ? "" : "s", ImmutableSortedSet.copyOf(missingConceptIds)));
		}
		
		// TODO: add more validation functionality that is checked for each coding (eg. alternative terms)
		for (final String id : codingsById.keySet()) {
			final Coding coding = codingsById.get(id);
			final String expectedDisplay = coding.getDisplay();
			
			if (expectedDisplay != null) {
				final Concept actualConcept = conceptsById.get(id);
				final String actualDisplay = actualConcept.getTerm();
				
				if (!expectedDisplay.equals(actualDisplay)) {
					return new CodeSystemValidateCodeResultParameters()
						.setResult(false)
						.setMessage(String.format("Incorrect display '%s' for code '%s'.", expectedDisplay, coding.getCode())) 
						.setDisplay(actualDisplay);
				}
			}
		}
		
		return new CodeSystemValidateCodeResultParameters().setResult(true);
	}
	
	private Set<Coding> collectCodingsToValidate(CodeSystemValidateCodeParameters parameters) {
		Set<Coding> codings = new HashSet<>(3);
				
		if (parameters.getCode() != null) {
			Coding coding = new Coding()
				.setCode(parameters.getCode().getValue())
				.setDisplay(parameters.getDisplay() != null ? parameters.getDisplay().getValue() : null);
			
			codings.add(coding);
		}
				
		if (parameters.getCoding() != null) {
			codings.add(parameters.getCoding());
		}
			
		CodeableConcept codeableConcept = parameters.getCodeableConcept();
		if (codeableConcept != null && codeableConcept.getCoding() != null) {
			codeableConcept.getCoding().forEach(codings::add);
		}
		
		return codings;
	}
}
