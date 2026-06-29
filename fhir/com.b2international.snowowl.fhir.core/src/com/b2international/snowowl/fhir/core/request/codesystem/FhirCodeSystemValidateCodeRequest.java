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

import com.b2international.commons.http.AcceptLanguageHeader;
import com.b2international.commons.options.Options;
import com.b2international.fhir.r5.operations.CodeSystemValidateCodeParameters;
import com.b2international.fhir.r5.operations.CodeSystemValidateCodeResultParameters;
import com.b2international.snowowl.core.*;
import com.b2international.snowowl.core.domain.Concept;
import com.b2international.snowowl.core.request.ConceptSearchRequestEvaluator;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.FhirModelHelpers.ValidateCodeInputType;
import com.b2international.snowowl.fhir.core.exceptions.BadRequestException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

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
		
		final ValidateCodeInputType inputType = getValidateCodeInputType(parameters);
		final String system = codeSystem.getUrl();
		final String version = codeSystem.getVersion();
		final List<Coding> codings = collectCodingsToValidate(parameters, inputType);
		
		final String displayLanguage = compactLocale(parameters.getDisplayLanguage());
		
		final Set<String> codes = codings.stream()
			.map(Coding::getCode)
			.filter(code -> !Strings.isNullOrEmpty(code))
			.collect(Collectors.toCollection(HashSet::new));
	
		final ResourceFragment resource = FhirModelHelpers.getResourceFragment(codeSystem);
		ResourceURI codeSystemUri = resource.getResourceURI();
		
		if (parameters.getDate() != null) {
			codeSystemUri = codeSystemUri.withTimestampPart("@" + Long.toString(parameters.getDate().getValue().getTime()));
		}
		
		// for performance reasons, running the raw evaluator here as we already identified the CodeSystem to evaluate it on
		final Repository codeSystemToolingRepository = context.service(RepositoryManager.class).get(resource.getToolingId());
		Options conceptSearchOptions = Options.builder()
			.put(ConceptSearchRequestEvaluator.OptionKey.ID, codes)
			.put(ConceptSearchRequestEvaluator.OptionKey.LIMIT, codes.size())
			.put(ConceptSearchRequestEvaluator.OptionKey.LOCALES, AcceptLanguageHeader.parseHeader(displayLanguage))
			.build();
		
		// seed already fetched resource information to prevent refetching the metadata
		final ServiceProvider searchContext = context.inject().bind(ResourceFragment.class, resource).build();
		
		final Map<String, Concept> conceptsById = codeSystemToolingRepository.service(ConceptSearchRequestEvaluator.class)
			.evaluate(codeSystemUri, searchContext, conceptSearchOptions)
			.stream()
			.collect(Collectors.toMap(
				Concept::getId,
				c -> c));
		
		// Multiple codings can be passed in inside a CodeableConcept
		if (inputType == ValidateCodeInputType.CODEABLE_CONCEPT) {
			return validateCodeableConcept(codings, conceptsById, system, version);
		}
		
		// If a Code or Coding is supplied, then only a single-code validation is necessary
		return validateSingleCode(inputType, Iterables.getOnlyElement(codings), conceptsById, system, version);
	}

	private ValidateCodeInputType getValidateCodeInputType(CodeSystemValidateCodeParameters parameters) {
		final boolean hasCode = parameters.getCode() != null;
		final boolean hasCoding = parameters.getCoding() != null;
		final boolean hasCodeableConcept = parameters.getCodeableConcept() != null;
		
		final int inputCount = (hasCode ? 1 : 0) + (hasCoding ? 1 : 0) + (hasCodeableConcept ? 1 : 0);
	
		if (inputCount != 1) {
			throw new BadRequestException("Exactly one of 'code', 'coding', or 'codeableConcept' must be provided for CodeSystem/$validate-code.");
		}
	
		if (hasCoding) {
			return ValidateCodeInputType.CODING;
		}
	
		if (hasCodeableConcept) {
			return ValidateCodeInputType.CODEABLE_CONCEPT;
		}
	
		return ValidateCodeInputType.CODE;
	}
	
	private List<Coding> collectCodingsToValidate(CodeSystemValidateCodeParameters parameters, ValidateCodeInputType validateCodeInputType) {
		// Use list to make sure the order of the parameters remain the same
		List<Coding> codings = new ArrayList<>();
				
		if (validateCodeInputType == ValidateCodeInputType.CODE) {
				
			Coding coding = new Coding()
					.setCode(parameters.getCode().getValue())
					.setDisplay(parameters.getDisplay() != null ? parameters.getDisplay().getValue() : null);
				
			codings.add(coding);
			
		} else if (validateCodeInputType == ValidateCodeInputType.CODING) {
			
			codings.add(parameters.getCoding());
			
		} else if (validateCodeInputType == ValidateCodeInputType.CODEABLE_CONCEPT)	{
			if (parameters.getCodeableConcept().getCoding() != null) {
				parameters.getCodeableConcept().getCoding().forEach(coding -> {
					if (!codings.contains(coding)) {
						codings.add(coding);
					}
				});
			}
		}
		return codings;
	}
	
	private CodeSystemValidateCodeResultParameters validateCodeableConcept(
			List<Coding> codings, 
			Map<String, Concept> conceptsById,
			String system,
			String version) {
		
		boolean foundValidCoding = false;
		Concept firstValidConcept = null;
		
		final OperationOutcome issues = new OperationOutcome();
		final List<String> messages = new ArrayList<>();
		
		for (int i = 0; i < codings.size(); i++) {
			
			final Coding coding = codings.get(i);
			final String code = coding.getCode();
		
			if (Strings.isNullOrEmpty(code)) {
				continue;
			}
			
			final Concept concept = conceptsById.get(code);
			
			if (concept == null) {
				final String location = String.format("CodeableConcept.coding[%d].code", i);
				
				addInvalidCodeIssue(issues, code, system, version, location);
				
				messages.add(String.format("Unknown code '%s' in the CodeSystem '%s'%s",
						code,
						system,
						formatVersionMessage(version))
				);
				continue;
			}
			
			// Display information about the first valid concept that the system has found
			if (!foundValidCoding) {
				firstValidConcept = concept;
			}
			
			foundValidCoding = true;
		}
		
		if (foundValidCoding) {
			// According to the FHIR standards, if one coding is valid, the response should be true
			return new CodeSystemValidateCodeResultParameters()
					.setResult(true)
					.setCode(firstValidConcept.getId())
					.setSystem(system)
					.setVersion(version)
					.setDisplay(firstValidConcept.getTerm())
					.setIssues(issues).setMessage(String.join("; ", messages));
		}
	
		return new CodeSystemValidateCodeResultParameters()
				.setResult(false)
				.setSystem(system)
				.setVersion(version)
				.setCodeableConcept(parameters.getCodeableConcept())
				.setIssues(issues)
				.setMessage(String.join("; ", messages));
	}

	private CodeSystemValidateCodeResultParameters validateSingleCode(
		ValidateCodeInputType inputType,
		Coding coding,
		Map<String, Concept> conceptsById,
		String system,
		String version) {
		
		final String code = coding.getCode();
		final Concept concept = conceptsById.get(code);
		final OperationOutcome issues = new OperationOutcome();
		final String location = inputType == ValidateCodeInputType.CODING ? "coding.code" : "code";
		
		if (concept == null) {
			addInvalidCodeIssue(issues, code, system, version, location);
			
			return new CodeSystemValidateCodeResultParameters()
				.setResult(false)
				.setCode(code)
				.setSystem(system)
				.setVersion(version)
				.setIssues(issues)
				.setMessage(String.format("Unknown code '%s' in the CodeSystem '%s'%s.",
						code,
						system,
						formatVersionMessage(version))
				);
		}
		
		final String expectedDisplay = coding.getDisplay();
		final String actualDisplay = concept.getTerm();
		
		if (expectedDisplay != null && !expectedDisplay.equals(actualDisplay)) {
			addInvalidDisplayIssue(issues, code, expectedDisplay, actualDisplay);
			
			return new CodeSystemValidateCodeResultParameters()
				.setResult(false)
				.setCode(code)
				.setSystem(system)
				.setVersion(version)
				.setDisplay(actualDisplay)
				.setIssues(issues)
				.setMessage(String.format("Incorrect display '%s' for code '%s'.", expectedDisplay, code));
		}
		
		return new CodeSystemValidateCodeResultParameters()
			.setResult(true)
			.setCode(concept.getId())
			.setSystem(system)
			.setVersion(version)
			.setDisplay(actualDisplay);
	}
	
	private void addInvalidCodeIssue(OperationOutcome outcome, String code, String system, String version, String location) {
		final String message = String.format("Unknown code '%s' in the CodeSystem '%s'%s",
			code,
			system,
			formatVersionMessage(version)
		);
	
		outcome.addIssue()
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
	

	private void addInvalidDisplayIssue(
		OperationOutcome outcome,
		String code,
		String expectedDisplay,
		String actualDisplay) {
		
		final String message = String.format("Incorrect display '%s' for code '%s'. Recommended display is '%s'",
			expectedDisplay,
			code,
			actualDisplay);
		
		outcome.addIssue()
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
