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
import com.b2international.snowowl.core.internal.DependencyDocument;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.exceptions.BadRequestException;
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
		super(parameters.getUrl() == null ? null : parameters.getUrl().asStringValue());
		this.parameters = parameters;
	}
	
	@Override
	public ValueSetValidateCodeResultParameters doExecute(ServiceProvider context, ValueSet valueSet) {
		
		final boolean hasCode = parameters.getCode() != null;
		final boolean hasCoding = parameters.getCoding() != null;
		final boolean hasCodeableConcept = parameters.getCodeableConcept() != null && parameters.getCodeableConcept().getCoding() != null;
		
		if (!hasCode && !hasCoding && !hasCodeableConcept) {
			throw new BadRequestException("At least one of 'code', 'coding', or 'codeableConcept' must be provided for $validate-code.");
		}
		
		if (hasCode ? (hasCoding || hasCodeableConcept) : (hasCoding && hasCodeableConcept)) {
			throw new BadRequestException("Exactly one of 'code', 'coding', or 'codeableConcept' must be provided for $validate-code.");
		}
		
		final Coding coding;
		final String propertyTemplate;
		final boolean validateDisplay;
		
		if (hasCode) {
				
			coding = new Coding()
					.setCode(parameters.getCode().getValue())
					.setDisplay(parameters.getDisplay() != null ? parameters.getDisplay().getValue() : null);
				
			propertyTemplate = "code";
			validateDisplay = true;
			
		} else if (hasCoding) {
			
			coding = parameters.getCoding();
			propertyTemplate = "coding.code";
			validateDisplay = true;
			
		} else if (hasCodeableConcept)	{
			
			// TODO support multiple values in codeableConcept? currently the underlying interface and its implementations do not support it
			if (parameters.getCodeableConcept().getCoding().size() > 1) {
				throw new BadRequestException("Multiple codeableConcept codings are not supported.");
			}
			
			coding = parameters.getCodeableConcept().getCoding().getFirst();
			propertyTemplate = "CodeableConcept.coding[%d].code";
			validateDisplay = false;
			
		} else {
			throw new IllegalStateException("Should not happen");
		}
		
		final String code = coding.getCode();

		// TODO have an 'inferSystem' flag to infer the system from the url (the parameter is planned to be added in R6)
		String system = parameters.getSystem() != null ? parameters.getSystem().getValue() : null;
		String version = parameters.getSystemVersion() != null ? parameters.getSystemVersion().getValue() : null;
		
		if (Strings.isNullOrEmpty(system)) {
			throw new BadRequestException("$validate-code must supply a 'system'");
		}
		
		boolean invalidDate;
		ValueSet.ValueSetExpansionContainsComponent valueSetExpansionContainsComponent;
		
		try {
			
			valueSetExpansionContainsComponent = context.service(RepositoryManager.class)
				.get(FhirModelHelpers.getResourceFragment(valueSet).getToolingId())
				.optionalService(FhirValueSetCodeValidator.class)
				.orElseThrow(() -> new BadRequestException("No validate-code implementation is available to handle valueSet: " + getUrl()))
				.validateCode(context, valueSet, code, parameters);
			
			invalidDate = false;
			
		} catch (final NotFoundException e) {
			valueSetExpansionContainsComponent = null;
			invalidDate = true;
		}
		
		String conceptCodeToDisplayInResult = null;
		String conceptDisplay = null;
		final List<OperationOutcome.OperationOutcomeIssueComponent> issues = new ArrayList<>(1);
		
		if (invalidDate) {
			final OperationOutcome.OperationOutcomeIssueComponent issue = buildInvalidDateIssue(system);
			issues.add(issue);
		}
		
		if (valueSetExpansionContainsComponent == null) {
			
			// extract version from compose definition if not defined externally
			if (Strings.isNullOrEmpty(version)) {
				ValueSet.ConceptSetComponent compose = valueSet.getCompose().getIncludeFirstRep();
				version = compose.getVersion();
			}
			
			// TODO add iteration from FhirCodeSystemValidateCodeRequest once we support multi-valued codeableConcepts
			final String location = String.format(propertyTemplate, 0);
			final OperationOutcome.OperationOutcomeIssueComponent issue = buildNotInVsIssue(code, system, location, valueSet.getUrl());
			issues.add(issue);
			
			ResourceURI codeSystemUri = getCodeSystemUri(valueSet);
			
			if (codeSystemUri != null) {
				int total = CodeSystemRequests.prepareSearchConcepts()
					.filterByCodeSystemUri(codeSystemUri)
					.filterById(code)
					.setLimit(0)
					.build()
					.execute(context)
					.getTotal();
				
				if (total == 0); {
					final OperationOutcome.OperationOutcomeIssueComponent invalidCodeIssue = buildInvalidCodeIssue(code, system, version, location);
					issues.add(invalidCodeIssue);
				}
			}

		} else {

			// Display information about the first valid concept that the system has found
			if (conceptCodeToDisplayInResult == null) {
				conceptCodeToDisplayInResult = valueSetExpansionContainsComponent.getCode();
				conceptDisplay = valueSetExpansionContainsComponent.getDisplay();
				
				// extract version from returned contains component
				if (Strings.isNullOrEmpty(version)) {
					version = valueSetExpansionContainsComponent.getVersion();
				}
			}
			
			
			// if display needs to be validated, validate it here
			if (validateDisplay) {
				
				final String expectedDisplay = coding.getDisplay();
				
				if (expectedDisplay != null && !expectedDisplay.equals(conceptDisplay)) {
					
					final OperationOutcome.OperationOutcomeIssueComponent issue = buildInvalidDisplayIssue(code, expectedDisplay, conceptDisplay);
					issues.add(issue);
					
				} else {
					// display looks good, no issue found
				}
				
			} else {
				// otherwise no issue is found
			}
			
		}
		
		final ValueSetValidateCodeResultParameters result = new ValueSetValidateCodeResultParameters();
		
		result
			.setSystem(system)
			.setVersion(version);
		
		if (conceptCodeToDisplayInResult != null) {
			result
				.setCode(conceptCodeToDisplayInResult)
				.setDisplay(conceptDisplay);
		}
		
		if (!CompareUtils.isEmpty(issues)) {
			final String message = issues.stream()
				.map(issue -> issue.getDetails().getText())
				.filter(s -> !Strings.isNullOrEmpty(s))
				.collect(Collectors.joining("; "));
			
			result
				// if at least one code is found in case of a codeableConcept request, then regardless of the issues this should be marked true
				.setResult(conceptCodeToDisplayInResult != null && hasCodeableConcept)
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
	
	private OperationOutcome.OperationOutcomeIssueComponent buildNotInVsIssue(String code, String system, String location, String valueSetUrl) {
		final String message = String.format("The provided code '%s#%s' was not found in the value set '%s'",
			system,
			code,
			valueSetUrl
		);
	
		return new OperationOutcome.OperationOutcomeIssueComponent()
			.setSeverity(OperationOutcome.IssueSeverity.ERROR)
			.setCode(OperationOutcome.IssueType.CODEINVALID)
			.setDetails(new CodeableConcept()
					.addCoding(new Coding()
						.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type")
						.setCode("not-in-vs"))
					.setText(message))
			.addLocation(location)
			.addExpression(location);
	}
	
	private OperationOutcome.OperationOutcomeIssueComponent buildInvalidCodeIssue(String code, String system, String version, String location) {
		final String message = String.format("Unknown code '%s' in the CodeSystem '%s' version '%s'",
			code,
			system,
			version
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
	
}
