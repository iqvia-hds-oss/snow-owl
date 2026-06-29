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
package com.b2international.snowowl.fhir.core.request.codesystem;

import java.util.*;
import java.util.stream.Collectors;

import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.OperationOutcome;

import com.b2international.commons.CompareUtils;
import com.b2international.commons.http.AcceptLanguageHeader;
import com.b2international.commons.options.Options;
import com.b2international.fhir.r5.operations.CodeSystemValidateCodeParameters;
import com.b2international.fhir.r5.operations.CodeSystemValidateCodeResultParameters;
import com.b2international.snowowl.core.*;
import com.b2international.snowowl.core.domain.Concept;
import com.b2international.snowowl.core.request.ConceptSearchRequestEvaluator;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.exceptions.BadRequestException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;

import jakarta.validation.constraints.NotNull;

/**
 * @since 8.0
 */
final class FhirCodeSystemValidateCodeRequest extends FhirCodeSystemOperationRequest<CodeSystemValidateCodeResultParameters> {

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
		
		final boolean hasCode = parameters.getCode() != null;
		final boolean hasCoding = parameters.getCoding() != null;
		final boolean hasCodeableConcept = parameters.getCodeableConcept() != null && parameters.getCodeableConcept().getCoding() != null;
		
		if (!hasCode && !hasCoding && !hasCodeableConcept) {
			throw new BadRequestException("At least one of 'code', 'coding', or 'codeableConcept' must be provided for $validate-code.");
		}
		
		if (hasCode ? (hasCoding || hasCodeableConcept) : (hasCoding && hasCodeableConcept)) {
			throw new BadRequestException("Exactly one of 'code', 'coding', or 'codeableConcept' must be provided for $validate-code.");
		}
		
		final List<Coding> codings;
		final String propertyTemplate;
		final boolean validateDisplay;
		
		if (hasCode) {
				
			Coding coding = new Coding()
					.setCode(parameters.getCode().getValue())
					.setDisplay(parameters.getDisplay() != null ? parameters.getDisplay().getValue() : null);
				
			codings = List.of(coding);
			propertyTemplate = "code";
			validateDisplay = true;
			
		} else if (hasCoding) {
			
			codings = List.of(parameters.getCoding());
			propertyTemplate = "coding.code";
			validateDisplay = true;
			
		} else if (hasCodeableConcept)	{
			
			codings = List.copyOf(parameters.getCodeableConcept().getCoding());
			propertyTemplate = "CodeableConcept.coding[%d].code";
			validateDisplay = false;
		
		} else {
			throw new IllegalStateException("Should not happen");
		}
		
		final Map<String, Concept> conceptsById = fetchCodes(context, codeSystem, codings);
		
		final String system = codeSystem.getUrl();
		final String version = codeSystem.getVersion();
		
		Concept conceptToDisplayInResult = null;
		final StringJoiner messages = new StringJoiner("; ");
		final List<OperationOutcome.OperationOutcomeIssueComponent> issues = new ArrayList<>(1);
		
		for (int i = 0; i < codings.size(); i++) {
			
			final Coding coding = codings.get(i);
			final String code = coding.getCode();
		
			if (Strings.isNullOrEmpty(code)) {
				continue;
			}
			
			final Concept concept = conceptsById.get(code);
			
			if (concept == null) {
				final String location = String.format(propertyTemplate, i);
				final OperationOutcome.OperationOutcomeIssueComponent issue = buildInvalidCodeIssue(code, system, version, location);
				messages.add(issue.getDetails().getText());
				issues.add(issue);
			} else {
				// Display information about the first valid concept that the system has found
				if (conceptToDisplayInResult == null) {
					conceptToDisplayInResult = concept;
				}
				
				// if display needs to be validated, validate it here
				if (validateDisplay) {
					
					final String expectedDisplay = coding.getDisplay();
					final String actualDisplay = concept.getTerm();
					
					if (expectedDisplay != null && !expectedDisplay.equals(actualDisplay)) {
						
						final OperationOutcome.OperationOutcomeIssueComponent issue = buildInvalidDisplayIssue(code, expectedDisplay, actualDisplay);
						messages.add(issue.getDetails().getText());
						issues.add(issue);
						
					} else {
						// display looks good, no issue found
					}
					
				} else {
					// otherwise no issue is found
				}
				
			}
		}
		
		final CodeSystemValidateCodeResultParameters result = new CodeSystemValidateCodeResultParameters();
		
		result
			.setSystem(system)
			.setVersion(version);
		
		if (conceptToDisplayInResult != null) {
			result
				.setCode(conceptToDisplayInResult.getId())
				.setDisplay(conceptToDisplayInResult.getTerm());
		}
		
		if (!CompareUtils.isEmpty(issues)) {
			result
				// if at least one code is found in case of a codeableConcept request, then regardless of the issues this should be marked true
				.setResult(conceptToDisplayInResult != null && hasCodeableConcept)
				.setIssues(new OperationOutcome().setIssue(issues));
		} else {
			// if there are not issues the result if always true
			result
				.setResult(true);
		}
		
		final String message = messages.toString();
		if (!CompareUtils.isEmpty(message)) {
			result.setMessage(message);
		}
	
		return result;

	}

	private Map<String, Concept> fetchCodes(ServiceProvider context, CodeSystem codeSystem, final List<Coding> codings) {
		final Set<String> codes = codings.stream()
			.map(Coding::getCode)
			.filter(code -> !Strings.isNullOrEmpty(code))
			.collect(Collectors.toSet());
	
		final ResourceFragment resource = FhirModelHelpers.getResourceFragment(codeSystem);
		ResourceURI codeSystemUri = resource.getResourceURI();
		
		if (parameters.getDate() != null) {
			codeSystemUri = codeSystemUri.withTimestampPart("@" + Long.toString(parameters.getDate().getValue().getTime()));
		}
		
		final String displayLanguage = compactLocale(parameters.getDisplayLanguage());
		// for performance reasons, running the raw evaluator here as we already identified the CodeSystem to evaluate it on
		final Repository codeSystemToolingRepository = context.service(RepositoryManager.class).get(resource.getToolingId());
		Options conceptSearchOptions = Options.builder()
			.put(ConceptSearchRequestEvaluator.OptionKey.ID, codes)
			.put(ConceptSearchRequestEvaluator.OptionKey.LIMIT, codes.size())
			.put(ConceptSearchRequestEvaluator.OptionKey.LOCALES, AcceptLanguageHeader.parseHeader(displayLanguage))
			.build();
		
		// seed already fetched resource information to prevent refetching the metadata
		final ServiceProvider searchContext = context.inject().bind(ResourceFragment.class, resource).build();
		
		return codeSystemToolingRepository.service(ConceptSearchRequestEvaluator.class)
			.evaluate(codeSystemUri, searchContext, conceptSearchOptions)
			.stream()
			.collect(Collectors.toMap(Concept::getId, c -> c));
	}

	private OperationOutcome.OperationOutcomeIssueComponent buildInvalidCodeIssue(String code, String system, String version, String location) {
		final String message = String.format("Unknown code '%s' in the CodeSystem '%s'%s",
			code,
			system,
			formatVersionMessage(version)
		);
	
		return new OperationOutcome.OperationOutcomeIssueComponent()
			.setSeverity(OperationOutcome.IssueSeverity.ERROR)
			.setCode(OperationOutcome.IssueType.CODEINVALID)
			.setDetails(new CodeableConcept()
					.addCoding(new Coding()
							.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type")
							.setCode("invalid-code"))
					.setText(message))
			.addLocation(location)
			.addExpression(location);
	}
	

	private OperationOutcome.OperationOutcomeIssueComponent buildInvalidDisplayIssue(String code, String expectedDisplay, String actualDisplay) {
		
		final String message = String.format("Incorrect display '%s' for code '%s'. Recommended display is '%s'",
			expectedDisplay,
			code,
			actualDisplay);
		
		return new OperationOutcome.OperationOutcomeIssueComponent()
			.setSeverity(OperationOutcome.IssueSeverity.ERROR)
			.setCode(OperationOutcome.IssueType.INVALID)
			.setDetails(new CodeableConcept()
					.addCoding(new Coding()
							.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type")
							.setCode("invalid-display"))
					.setText(message))
			.addLocation("display")
			.addExpression("display");
	}

	
	private String formatVersionMessage(String version) {
		return Strings.isNullOrEmpty(version) ? "" : String.format(" version '%s'", version);
	}
}
