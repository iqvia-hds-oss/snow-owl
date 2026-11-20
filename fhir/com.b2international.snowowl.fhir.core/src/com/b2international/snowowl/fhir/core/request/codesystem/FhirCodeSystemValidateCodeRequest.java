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
import java.util.stream.Stream;

import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent;

import com.b2international.commons.CompareUtils;
import com.b2international.commons.exceptions.NotFoundException;
import com.b2international.commons.http.AcceptLanguageHeader;
import com.b2international.commons.options.Options;
import com.b2international.fhir.r5.operations.CodeSystemValidateCodeParameters;
import com.b2international.fhir.r5.operations.CodeSystemValidateCodeResultParameters;
import com.b2international.snowowl.core.*;
import com.b2international.snowowl.core.domain.Concept;
import com.b2international.snowowl.core.request.ConceptSearchRequestEvaluator;
import com.b2international.snowowl.fhir.core.FhirException.TerminologyIssueType;
import com.b2international.snowowl.fhir.core.FhirExceptions;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.FhirModelHelpers.SystemAndVersion;
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
	
	private final boolean hasCode;
	private final boolean hasCoding;
	private final boolean hasCodeableConcept;
	
	private final List<Coding> codings;
	private final String expressionTemplate;
	
	FhirCodeSystemValidateCodeRequest(CodeSystemValidateCodeParameters parameters) {
		final boolean hasCode = parameters.getCode() != null;
		final boolean hasCoding = parameters.getCoding() != null;
		final boolean hasCodeableConcept = parameters.getCodeableConcept() != null && parameters.getCodeableConcept().getCoding() != null;
		
		if (!hasCode && !hasCoding && !hasCodeableConcept) {
			throw FhirExceptions.paramInvalid("At least one of 'code', 'coding', or 'codeableConcept' must be provided for $validate-code.");
		}
		
		if (hasCode ? (hasCoding || hasCodeableConcept) : (hasCoding && hasCodeableConcept)) {
			throw FhirExceptions.paramInvalid("Exactly one of 'code', 'coding', or 'codeableConcept' must be provided for $validate-code.");
		}
		
		final List<Coding> codings;
		
		if (hasCode) {
			Coding coding = new Coding()
				.setCodeElement(parameters.getCode())
				.setSystemElement(parameters.getUrl())
				.setVersionElement(parameters.getVersion())
				.setDisplayElement(parameters.getDisplay());
			codings = List.of(coding);
			expressionTemplate = "";
		} else if (hasCoding) {
			codings = List.of(parameters.getCoding());
			expressionTemplate = "Coding";
		} else if (hasCodeableConcept) {
			codings = List.copyOf(parameters.getCodeableConcept().getCoding());
			// TODO For now we have disabled this feature (until we clear how the batch validate should work)
			if (codings.size() > 1) {
				throw FhirExceptions.paramInvalid("Multiple codeableConcept codings are not supported.");
			}
			expressionTemplate = "CodeableConcept.coding[%d]";
		} else {
			throw new IllegalStateException("Should not happen");
		}
		
		// Make sure we compare systems with URL as well
		SystemAndVersion urlAndVersion = new SystemAndVersion(
			parameters.getUrl() != null ? parameters.getUrl().getValue() : null,
			parameters.getVersion() != null ? parameters.getVersion().getValue() : null
		);
		
		Set<SystemAndVersion> systemAndVersions = Stream.concat(
			Stream.of(urlAndVersion),
			codings.stream()
				.map(c -> new SystemAndVersion(c.getSystem(), c.getVersion()))
			)
			.filter(s -> !Strings.isNullOrEmpty(s.system()))  // Ignore codings with empty system for now
			.collect(Collectors.toSet());
		
		final String system;
		final String version;
		if (systemAndVersions.isEmpty()) {
			if (hasCode) {
				throw FhirExceptions.paramInvalid("$validate-code must supply a 'url'");
			} else {
				// This special case will be handled in handleMissingSystem callback
				system = null;
				version = null;
			}
		} else if (systemAndVersions.size() > 1) {
			throw FhirExceptions.paramInvalid("different 'url' and 'system(s)' are not supported");
		} else {
			final SystemAndVersion systemAndVersion = Iterables.getOnlyElement(systemAndVersions);	
			system = systemAndVersion.system();
			version = systemAndVersion.version();
		}
		
		super(system, version);
		
		this.hasCode = hasCode;
		this.hasCoding = hasCoding;
		this.hasCodeableConcept = hasCodeableConcept;
		this.codings = codings;
		this.parameters = parameters;
	}

	@Override
	public CodeSystemValidateCodeResultParameters doExecute(ServiceProvider context, CodeSystem codeSystem) {
		
		final boolean[] invalidDate = { false };
		final Map<String, Concept> conceptsById = fetchCodes(context, codeSystem, codings, invalidDate);

		final String system = codeSystem.getUrl();
		final String version = codeSystem.getVersion();
		
		Concept conceptToDisplayInResult = null;
		final List<OperationOutcome.OperationOutcomeIssueComponent> issues = new ArrayList<>(1);
		
		if (invalidDate[0]) {
			final OperationOutcome.OperationOutcomeIssueComponent issue = buildInvalidDateIssue(system);
			issues.add(issue);
		}
		
		final String displayLanguage = parameters.getDisplayLanguage() != null ? parameters.getDisplayLanguage().getValue() : null;
		
		boolean atLeastOneValid = false;
		
		for (int i = 0; i < codings.size(); i++) {
			
			final Coding coding = codings.get(i);
			final String code = coding.getCode();
			final String currentSystem = coding.getSystem();
			final String indexedExpression = String.format(expressionTemplate, i);
	
			if (Strings.isNullOrEmpty(code) && Strings.isNullOrEmpty(currentSystem)) {
				issues.add(buildMissingCodeAndSystemIssue(indexedExpression));
				continue;
			} else if (Strings.isNullOrEmpty(code)) {
				issues.add(buildMissingCodeIssue(indexedExpression));
				continue;
			} else if (Strings.isNullOrEmpty(currentSystem)){
				issues.add(buildMissingSystemIssue(indexedExpression));
				continue;
			}

			final Concept concept = conceptsById.get(code);
			
			if (concept == null) {
				final OperationOutcome.OperationOutcomeIssueComponent issue = buildInvalidCodeIssue(code, system, version, indexedExpression);
				issues.add(issue);
			} else {
				// Display information about the first valid concept that the system has found
				if (conceptToDisplayInResult == null) {
					conceptToDisplayInResult = concept;
				}				
					
				final String expectedDisplay = coding.getDisplay();
				final String actualDisplay = concept.getTerm();
				
				if (expectedDisplay != null && !expectedDisplay.equals(actualDisplay)) {
					
					final OperationOutcome.OperationOutcomeIssueComponent issue = buildInvalidDisplayIssue(code, displayLanguage, expectedDisplay, actualDisplay, indexedExpression);
					issues.add(issue);
					
				} else {
					// display looks good, no issue found
					atLeastOneValid = true;
				}
			}
		}
		
		final CodeSystemValidateCodeResultParameters result = new CodeSystemValidateCodeResultParameters();
		
		if (hasCode || hasCoding) { 
			result.setSystem(system);
			
			if (conceptToDisplayInResult != null) {
				// Use validated concept if present
				result
					.setVersion(version)
					.setCode(conceptToDisplayInResult.getId())
					.setDisplay(conceptToDisplayInResult.getTerm());
			} else {
				// Use provided values if present
				codings.stream().findFirst()
					.ifPresent(coding -> {
						result
							// We do not use the coding.system as it could be an ID, instead we have already it using the CodeSystem.url 
							.setVersion(coding.getVersion())
							.setCode(coding.getCode())
							.setDisplay(coding.getDisplay());
					});
			}
		} else if (hasCodeableConcept) {
			// Codeable concept was used returned input parameters
			result.setCodeableConcept(parameters.getCodeableConcept());
			if (conceptToDisplayInResult != null) {
				// Return first successfully validated code's properties
				result
					.setSystem(system)
					.setVersion(version)
					.setCode(conceptToDisplayInResult.getId())
					.setDisplay(conceptToDisplayInResult.getTerm());
			}
		} else {
			throw new IllegalStateException("Should not happen");
		}
		
		if (!CompareUtils.isEmpty(issues)) {
			final String message = issues.stream()
				.map(issue -> issue.getDetails().getText())
				.filter(s -> !Strings.isNullOrEmpty(s))
				.collect(Collectors.joining("; "));
			
			result
				.setResult(atLeastOneValid)
				.setIssues(new OperationOutcome().setIssue(issues))
				.setMessage(message);
		
		} else {
			// if there are no issues the result is always true
			result
				.setResult(true);
		}
	
		return result;
	}
	
	@Override
	protected CodeSystemValidateCodeResultParameters handleMissingSystem(ServiceProvider context, String system, String version) {
		if (Strings.isNullOrEmpty(system)) {
			/*
			 * No system were provided and either Coding or CodeableConcept has been used.
			 * We must return a dummy Parameters results containing blames for the provided Coding(s).
			 */
			final CodeSystemValidateCodeResultParameters result = new CodeSystemValidateCodeResultParameters();
			if (hasCode) {
				// This case should have already raised an error in the constructor
				throw new IllegalStateException("Should not happen");
			} else if (hasCoding) {
				// We do not set these if Coding was used
				final Coding coding = Iterables.getOnlyElement(codings);
				// system is null
				result.setCode(coding.getCode());
				result.setDisplay(coding.getDisplay());
				result.setVersion(coding.getVersion());
			} else if (hasCodeableConcept) {
				result.setCodeableConcept(parameters.getCodeableConcept());
			} else {
				throw new IllegalStateException("Should not happen");
			}
			
			final List<OperationOutcome.OperationOutcomeIssueComponent> issues = new ArrayList<>(1);
			
			for (int i = 0; i < codings.size(); i++) {
				
				final Coding coding = codings.get(i);
				final String code = coding.getCode();
				final String currentSystem = coding.getSystem();
				final String indexedExpression = String.format(expressionTemplate, i);
				
				if (Strings.isNullOrEmpty(code) && Strings.isNullOrEmpty(currentSystem)) {
					issues.add(buildMissingCodeAndSystemIssue(indexedExpression));
					continue;
				} else if (Strings.isNullOrEmpty(code)) {
					issues.add(buildMissingCodeIssue(indexedExpression));
					continue;
				} else if (Strings.isNullOrEmpty(currentSystem)){
					issues.add(buildMissingSystemIssue(indexedExpression));
					continue;
				}
				
			}
			
			final String message = issues.stream()
				.map(issue -> issue.getDetails().getText())
				.filter(s -> !Strings.isNullOrEmpty(s))
				.collect(Collectors.joining("; "));
			
			result
				.setResult(false)
				.setIssues(new OperationOutcome().setIssue(issues))
				.setMessage(message);
			
			return result;
		} else {
			return super.handleMissingSystem(context, system, version);
		}
	}
	
	private Map<String, Concept> fetchCodes(ServiceProvider context, CodeSystem codeSystem, final List<Coding> codings, final boolean[] invalidDate) {
		final Set<String> codes = codings.stream()
			.map(Coding::getCode)
			.filter(code -> !Strings.isNullOrEmpty(code))
			.collect(Collectors.toSet());
	
		final ResourceFragment resource = FhirModelHelpers.getResourceFragment(codeSystem);
		ResourceURI codeSystemUri = resource.getResourceURI();

		final OptionalLong parameterTimestamp = (parameters.getDate() == null) 
			? OptionalLong.empty()
			: OptionalLong.of(parameters.getDate().getValue().getTime());

		if (parameterTimestamp.isPresent()) {
			codeSystemUri = codeSystemUri.withTimestampPart("@" + Long.toString(parameterTimestamp.getAsLong()));
		}
		
		final String displayLanguage = compactLocale(parameters.getDisplayLanguage());
		// for performance reasons, running the raw evaluator here as we already identified the CodeSystem to evaluate it on
		final Repository codeSystemToolingRepository = context.service(RepositoryManager.class).get(resource.getToolingId());
		Options conceptSearchOptions = Options.builder()
			.put(ConceptSearchRequestEvaluator.OptionKey.ID, codes)
			.put(ConceptSearchRequestEvaluator.OptionKey.LIMIT, codes.size())
			.put(ConceptSearchRequestEvaluator.OptionKey.LOCALES, AcceptLanguageHeader.parseHeader(displayLanguage))
			.build();
		
		/* 
		 * seed already fetched resource information, but only if the point-in-time parameter 
		 * is set before the already fetched (most recent) resource's creation time
		 */
		final ServiceProvider searchContext;
		final long createdTimestamp = resource.getCreatedAt();

		if (parameterTimestamp.isEmpty() || parameterTimestamp.getAsLong() >= createdTimestamp) {
			searchContext = context.inject().bind(ResourceFragment.class, resource).build();
		} else {
			searchContext = context;
		}

		try {
			
			return codeSystemToolingRepository.service(ConceptSearchRequestEvaluator.class)
				.evaluate(codeSystemUri, searchContext, conceptSearchOptions)
				.stream()
				.collect(Collectors.toMap(Concept::getId, c -> c));
		
		} catch (final NotFoundException e) {
			// We know that the code system exists, in general. If it goes missing at this stage, the date parameter is invalid.
			invalidDate[0] = true;
			return Map.of();
		}
	}

	private OperationOutcomeIssueComponent buildMissingCodeAndSystemIssue(final String expression) {
		return new OperationOutcome.OperationOutcomeIssueComponent()
			.setSeverity(OperationOutcome.IssueSeverity.WARNING)
			.setCode(OperationOutcome.IssueType.INVALID)
			.setDetails(new CodeableConcept()
				.addCoding(TerminologyIssueType.INVALID_DATA.getCoding())
				.setText("Coding has neither a system nor a code, so it cannot be validated. A system and a code should be provided"))
			.addLocation(expression)
			.addExpression(expression);
	}
	
	private OperationOutcomeIssueComponent buildMissingCodeIssue(final String expression) {
		
		return new OperationOutcome.OperationOutcomeIssueComponent()
			.setSeverity(OperationOutcome.IssueSeverity.WARNING)
			.setCode(OperationOutcome.IssueType.INVALID)
			.setDetails(new CodeableConcept()
				.addCoding(TerminologyIssueType.INVALID_DATA.getCoding())
				.setText("Coding has no code, so it cannot be validated. A code should be provided"))
			.addLocation(expression)
			.addExpression(expression);
	}
	
	private OperationOutcomeIssueComponent buildMissingSystemIssue(final String expression) {
		return new OperationOutcome.OperationOutcomeIssueComponent()
			.setSeverity(OperationOutcome.IssueSeverity.WARNING)
			.setCode(OperationOutcome.IssueType.INVALID)
			.setDetails(new CodeableConcept()
				.addCoding(TerminologyIssueType.INVALID_DATA.getCoding())
				.setText("Coding has no system. A code with no system has no defined meaning, and it cannot be validated. A system should be provided"))
			.addLocation(expression)
			.addExpression(expression);
	}
	
	private OperationOutcomeIssueComponent buildInvalidDateIssue(final String system) {
		final String message = String.format("CodeSystem '%s' does not exist at the specified date '%s'", 
			system, 
			parameters.getDate().toHumanDisplay()
		);
		
		return new OperationOutcome.OperationOutcomeIssueComponent()
			.setSeverity(OperationOutcome.IssueSeverity.ERROR)
			.setCode(OperationOutcome.IssueType.NOTFOUND)
			.setDetails(new CodeableConcept()
				.addCoding(TerminologyIssueType.NOT_FOUND.getCoding())
				.setText(message));
	}

	private OperationOutcome.OperationOutcomeIssueComponent buildInvalidCodeIssue(String code, String system, String version, String baseExpression) {
		final String expression = baseExpression.isEmpty() ? "code" : String.join(".", baseExpression, "code");
		final String message = String.format("Unknown code '%s' in the CodeSystem '%s'%s",
			code,
			system,
			formatVersionMessage(version)
		);
	
		return new OperationOutcome.OperationOutcomeIssueComponent()
			.setSeverity(OperationOutcome.IssueSeverity.ERROR)
			.setCode(OperationOutcome.IssueType.CODEINVALID)
			.setDetails(new CodeableConcept()
					.addCoding(TerminologyIssueType.INVALID_CODE.getCoding())
					.setText(message))
			.addLocation(expression)
			.addExpression(expression);
	}
	

	private OperationOutcome.OperationOutcomeIssueComponent buildInvalidDisplayIssue(String code, String displayLanguage, String expectedDisplay, String actualDisplay, String baseExpression) {
		final String message;
		if (Strings.isNullOrEmpty(displayLanguage)) {
			message = String.format("Incorrect display '%s' for code '%s'. Recommended display is '%s'",
				expectedDisplay,
				code,
				actualDisplay
			);
		} else {
			message = String.format("Incorrect display '%s' for language(s) '%s' for code '%s'. Recommended display is '%s'",
				expectedDisplay,
				displayLanguage,
				code,
				actualDisplay
			);
		}
		
		final String expression = baseExpression.isEmpty() ? "display" : String.join(".", baseExpression, "display");
		
		return new OperationOutcome.OperationOutcomeIssueComponent()
			.setSeverity(OperationOutcome.IssueSeverity.ERROR)
			.setCode(OperationOutcome.IssueType.INVALID)
			.setDetails(new CodeableConcept()
					.addCoding(TerminologyIssueType.INVALID_DISPLAY.getCoding())
					.setText(message))
			.addLocation(expression)
			.addExpression(expression);
	}

	
	private String formatVersionMessage(String version) {
		return Strings.isNullOrEmpty(version) ? "" : String.format(" version '%s'", version);
	}
}
