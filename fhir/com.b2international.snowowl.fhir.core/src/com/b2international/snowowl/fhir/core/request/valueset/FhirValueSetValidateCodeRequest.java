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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r5.model.ValueSet;

import com.b2international.commons.CompareUtils;
import com.b2international.commons.exceptions.NotFoundException;
import com.b2international.fhir.r5.operations.ValueSetValidateCodeParameters;
import com.b2international.fhir.r5.operations.ValueSetValidateCodeResultParameters;
import com.b2international.snowowl.core.*;
import com.b2international.snowowl.core.codesystem.CodeSystem;
import com.b2international.snowowl.core.codesystem.CodeSystemRequests;
import com.b2international.snowowl.core.domain.Concept;
import com.b2international.snowowl.core.internal.DependencyDocument;
import com.b2international.snowowl.fhir.core.FhirExceptions;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.request.codesystem.FhirCodeSystemOperationRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;

/**
 * @since 8.0
 */
final class FhirValueSetValidateCodeRequest extends FhirValueSetOperationRequest<ValueSetValidateCodeResultParameters> {

	private static final long serialVersionUID = 2L;

	@JsonProperty
	private final ValueSetValidateCodeParameters parameters;
	
	public FhirValueSetValidateCodeRequest(ValueSetValidateCodeParameters parameters) {
		var url = parameters.getUrl() == null ? null : parameters.getUrl().asStringValue();
		var version = Strings.emptyToNull(parameters.getValueSetVersion() == null ? null : parameters.getValueSetVersion().getValue());
		super(url, version);
		this.parameters = parameters;
	}
	
	@Override
	public ValueSetValidateCodeResultParameters doExecute(ServiceProvider context, ValueSet valueSet) {
		
		final boolean hasCode = parameters.getCode() != null;
		final boolean hasCoding = parameters.getCoding() != null;
		final boolean hasCodeableConcept = parameters.getCodeableConcept() != null && parameters.getCodeableConcept().hasCoding();
		
		if (!hasCode && !hasCoding && !hasCodeableConcept) {
			// TODO: [FHIR] add `invalid-data` coding as details
			throw FhirExceptions.paramInvalid("At least one of 'code', 'coding', or 'codeableConcept' must be provided for $validate-code.");
		}
		
		if (hasCode ? (hasCoding || hasCodeableConcept) : (hasCoding && hasCodeableConcept)) {
			throw FhirExceptions.paramInvalid("Exactly one of 'code', 'coding', or 'codeableConcept' must be provided for $validate-code.");
		}
		
		final Coding coding;
		final String expression;
		
		if (hasCode) {
			coding = new Coding()
				.setCodeElement(parameters.getCode())
				.setDisplayElement(parameters.getDisplay())
				.setSystemElement(parameters.getSystem())
				.setVersionElement(parameters.getSystemVersion());
			expression = "";
		} else if (hasCoding) {
			coding = parameters.getCoding();
			expression = "Coding";
		} else if (hasCodeableConcept)	{
			// TODO: support multiple values in codeableConcept? currently the underlying interface and its implementations do not support it
			if (parameters.getCodeableConcept().getCoding().size() > 1) {
				throw FhirExceptions.paramInvalid("Multiple codeableConcept codings are not supported.");
			}
			coding = parameters.getCodeableConcept().getCoding().getFirst();
			expression = "CodeableConcept.coding[0]";
		} else {
			throw new IllegalStateException("Should not happen");
		}
		
		final String code = coding.getCode();
		
		// TODO: [FHIR] r6 will introduce parameter `inferSystem`
		// TODO: [FHIR] validate that system does exist: "A definition for CodeSystem '{}' could not be found, so the code cannot be validated"
		// TODO: [FHIR] validate that system is in fact a Code System: The Coding references a value set, not a code system ('{}')
		final String system = coding.getSystem();  
		 
		String version = coding.getVersion();
		
		final String expectedDisplay = coding.getDisplay();
		String display = expectedDisplay;  // Initialize display with provided value
		// XXX: [FHIR] r6 will introduce parameter `lenient-display-validation` which should turn the error into a warning
		final boolean validateDisplay = !Strings.isNullOrEmpty(expectedDisplay);  
		
		boolean invalidDate;
		ValueSet.ValueSetExpansionContainsComponent valueSetExpansionContainsComponent = null;
		
		final List<OperationOutcome.OperationOutcomeIssueComponent> issues = new ArrayList<>(1);
		
		if (hasCode) {
			if (Strings.isNullOrEmpty(system)) {
				throw FhirExceptions.paramInvalid("$validate-code must supply a 'system'");
			}
		} else {
			// These problems are expected as issues in the output parameters
			if (Strings.isNullOrEmpty(code) && Strings.isNullOrEmpty(system)) {
				issues.add(buildMissingCodeAndSystemIssue(expression));
			} else if (Strings.isNullOrEmpty(code)) {
				// TODO: [FHIR] validator expects not-in-vs issue if CodeableConcept was used
				issues.add(buildMissingCodeIssue(expression));
			} else if (Strings.isNullOrEmpty(system)){
				// TODO: [FHIR] validator expects not-in-vs issue if CodeableConcept was used
				issues.add(buildMissingSystemIssue(expression));
			}
		}
		
		invalidDate = false;
		boolean codeValidated = false;
		// We only do the validation is there are no issues already
		if (issues.isEmpty()) {
			try {
				valueSetExpansionContainsComponent = context.service(RepositoryManager.class)
					.get(FhirModelHelpers.getResourceFragment(valueSet).getToolingId())
					.optionalService(FhirValueSetCodeValidator.class)
					.orElseThrow(() -> FhirExceptions.paramInvalid("No validate-code implementation is available to handle valueSet: " + getUrl()))
					.validateCode(context, valueSet, code, parameters);
			} catch (final NotFoundException e) {
				invalidDate = true;
			}
			
			if (invalidDate) {
				issues.add(buildInvalidDateIssue(system));
			}
			
			if (valueSetExpansionContainsComponent == null) {
				
				// extract version from compose definition if not defined externally
				if (Strings.isNullOrEmpty(version)) {
					ValueSet.ConceptSetComponent compose = valueSet.getCompose().getIncludeFirstRep();
					version = compose.getVersion();
				}
				
				issues.add(buildNotInVsIssue(code, system, valueSet.getUrl(), expression));
				
				// TODO: [FHIR] validate that provided system matches the code system here
				ResourceURI codeSystemUri = getCodeSystemUri(valueSet);
				
				if (codeSystemUri != null) {
					final String locale = FhirCodeSystemOperationRequest.compactLocale(parameters.getDisplayLanguage());
					
					Optional<Concept> concept = CodeSystemRequests.prepareSearchConcepts()
						.filterByCodeSystemUri(codeSystemUri)
						.filterById(code)
						.setLocales(locale)
						.setLimit(1)
						.build()
						.execute(context)
						.first();
					
					if (!concept.isPresent()) {
						// Concept was not even found in code system
						issues.add(buildInvalidCodeIssue(code, system, version, expression));
					} else {
						// Display was not provided, so we will use the term
						if (display == null) {
							display = concept.get().getTerm();
						}
					}
				}
	
			} else {
				// extract version from returned contains component
				if (Strings.isNullOrEmpty(version)) {
					version = valueSetExpansionContainsComponent.getVersion();
				}
				
				final String actualSystem = valueSetExpansionContainsComponent.getSystem();
				if (Objects.equals(system, actualSystem)) {
					// Make sure the provided system is the same
					codeValidated = true;
				} else {
					issues.add(buildNotInVsIssue(code, system, valueSet.getUrl(), expression));
				}
				
				// Use returned display, if there was a provided display then validate it now
				display = valueSetExpansionContainsComponent.getDisplay();
				if (validateDisplay) {
					if (!Objects.equals(expectedDisplay, display)) {
						// TODO: [FHIR] validate that provided display is a valid alternate
						issues.add(buildInvalidDisplayIssue(code, expectedDisplay, display, expression));
					}
				}
			}
		}
		
		final ValueSetValidateCodeResultParameters result = new ValueSetValidateCodeResultParameters();
		
		if (hasCode || hasCoding) {
			result.setCode(code);
			result.setSystem(system);
			result.setDisplay(display);
			result.setVersion(version);
		} else if (hasCodeableConcept) {
			result.setCodeableConcept(parameters.getCodeableConcept());
			if (codeValidated) {
				// If a CodeableConcept was provided then FHIR validator only expects the code/system/display parameters if the code was deemed valid,
				// invalid display is not considered here.
				result
					.setCode(code)
					.setSystem(system)
					.setDisplay(display)
					.setVersion(version);
			}
		}
		
		if (!CompareUtils.isEmpty(issues)) {
			final String message = issues.stream()
				.map(issue -> issue.getDetails().getText())
				.filter(s -> !Strings.isNullOrEmpty(s))
				.collect(Collectors.joining("; "));
			
			result
				.setResult(false)  // TODO: [FHIR] sometimes warning and information issues are expected but with a result=True
				.setIssues(new OperationOutcome().setIssue(issues))
				.setMessage(message);
			
		} else {
			// if there are no issues the result is always true
			result
				.setResult(true);
		}
	
		return result;
	}
	
	private ResourceURI getCodeSystemUri(ValueSet valueSet) {
		ResourceFragment resourceFragment = FhirModelHelpers.getResourceFragment(valueSet);
		
		if ("valuesets".equals(resourceFragment.getResourceType())) {
			return resourceFragment.getDependencies().stream()
				.filter(document -> "domain".equals(document.getScope()))
				.map(DependencyDocument::getUri)
				.map(ResourceURIWithQuery::getResourceUri)
				.filter(resourceUri -> CodeSystem.RESOURCE_TYPE.equals(resourceUri.getResourceType()))
				.findFirst()
				.orElse(null);
		} else if (CodeSystem.RESOURCE_TYPE.equals(resourceFragment.getResourceType())) {
			return resourceFragment.getResourceURI();
		} else {
			// Failed to identify the source code system
			return null;
		}
	}
	
	private OperationOutcomeIssueComponent buildMissingCodeAndSystemIssue(final String expression) {
		return new OperationOutcome.OperationOutcomeIssueComponent()
			.setSeverity(OperationOutcome.IssueSeverity.WARNING)
			.setCode(OperationOutcome.IssueType.INVALID)
			.setDetails(new CodeableConcept()
				.addCoding(new Coding()
					.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type")
					.setCode("invalid-data"))
				.setText("Coding has neither a system nor a code, so it cannot be validated. A system and a code should be provided"))
			.addLocation(expression)
			.addExpression(expression);
	}
	
	private OperationOutcomeIssueComponent buildMissingCodeIssue(final String expression) {
		
		return new OperationOutcome.OperationOutcomeIssueComponent()
			.setSeverity(OperationOutcome.IssueSeverity.WARNING)
			.setCode(OperationOutcome.IssueType.INVALID)
			.setDetails(new CodeableConcept()
				.addCoding(new Coding()
					.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type")
					.setCode("invalid-data"))
				.setText("Coding has no code, so it cannot be validated. A code should be provided"))
			.addLocation(expression)
			.addExpression(expression);
	}
	
	private OperationOutcomeIssueComponent buildMissingSystemIssue(final String expression) {
		return new OperationOutcome.OperationOutcomeIssueComponent()
			.setSeverity(OperationOutcome.IssueSeverity.WARNING)
			.setCode(OperationOutcome.IssueType.INVALID)
			.setDetails(new CodeableConcept()
				.addCoding(new Coding()
					.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type")
					.setCode("invalid-data"))
				.setText("Coding has no system. A code with no system has no defined meaning, and it cannot be validated. A system should be provided"))
			.addLocation(expression)
			.addExpression(expression);
	}
	
	private OperationOutcomeIssueComponent buildInvalidDateIssue(final String system) {
		final String message = String.format("ValueSet or referenced CodeSystem '%s' does not exist at the specified date '%s'", 
			system, 
			parameters.getDate().toHumanDisplay()
		);
		
		return new OperationOutcome.OperationOutcomeIssueComponent()
			.setSeverity(OperationOutcome.IssueSeverity.ERROR)
			.setCode(OperationOutcome.IssueType.NOTFOUND)
			.setDetails(new CodeableConcept()
				.addCoding(new Coding()
					.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type")
					.setCode("not-found"))
				.setText(message));
	}	
	
	private OperationOutcome.OperationOutcomeIssueComponent buildNotInVsIssue(final String code, final String system, final String valueSetUrl, final String expressionBase) {
		final String message = String.format("The provided code '%s#%s' was not found in the value set '%s'",
			system,
			code,
			valueSetUrl
		);
		final String expression = Strings.isNullOrEmpty(expressionBase) ? "code" : String.join(".", expressionBase, "code");
	
		return new OperationOutcome.OperationOutcomeIssueComponent()
			.setSeverity(OperationOutcome.IssueSeverity.ERROR)
			.setCode(OperationOutcome.IssueType.CODEINVALID)
			.setDetails(new CodeableConcept()
				.addCoding(new Coding()
					.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type")
					.setCode("not-in-vs"))
				.setText(message))
			.addLocation(expression)
			.addExpression(expression);
	}
	
	private OperationOutcome.OperationOutcomeIssueComponent buildInvalidCodeIssue(final String code, final String system, final String version, final String expressionBase) {
		final String message = String.format("Unknown code '%s' in the CodeSystem '%s' version '%s'",
			code,
			system,
			version
		);
		final String expression = Strings.isNullOrEmpty(expressionBase) ? "code" : String.join(".", expressionBase, "code");
	
		return new OperationOutcome.OperationOutcomeIssueComponent()
			.setSeverity(OperationOutcome.IssueSeverity.ERROR)
			.setCode(OperationOutcome.IssueType.CODEINVALID)
			.setDetails(new CodeableConcept()
				.addCoding(new Coding()
					.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type")
					.setCode("invalid-code"))
				.setText(message))
			.addLocation(expression)
			.addExpression(expression);
	}
	
	private OperationOutcome.OperationOutcomeIssueComponent buildInvalidDisplayIssue(final String code, final String expectedDisplay, final String actualDisplay, final String expressionBase) {
		final String message = String.format("Incorrect display '%s' for code '%s'. Recommended display is '%s'",
			expectedDisplay,
			code,
			actualDisplay);
		final String expression = Strings.isNullOrEmpty(expressionBase) ? "display" : String.join(".", expressionBase, "display");
		
		return new OperationOutcome.OperationOutcomeIssueComponent()
			.setSeverity(OperationOutcome.IssueSeverity.ERROR)
			.setCode(OperationOutcome.IssueType.INVALID)
			.setDetails(new CodeableConcept()
				.addCoding(new Coding()
					.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type")
					.setCode("invalid-display"))
				.setText(message))
			.addLocation(expression)
			.addExpression(expression);
	}
	
}
