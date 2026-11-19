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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.OperationOutcome;

import com.b2international.commons.options.Options;
import com.b2international.fhir.r5.operations.CodeSystemSubsumptionParameters;
import com.b2international.fhir.r5.operations.CodeSystemSubsumptionResultParameters;
import com.b2international.snowowl.core.Repository;
import com.b2international.snowowl.core.RepositoryManager;
import com.b2international.snowowl.core.ResourceFragment;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.domain.Concept;
import com.b2international.snowowl.core.request.ConceptSearchRequestEvaluator;
import com.b2international.snowowl.fhir.core.FhirException;
import com.b2international.snowowl.fhir.core.FhirException.TerminologyIssueType;
import com.b2international.snowowl.fhir.core.FhirExceptions;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;

/**
 * Test the subsumption relationship between code/Coding A and code/Coding B given the semantics of subsumption in the underlying code system (see hierarchyMeaning).
 * 
 * @see <a href="http://hl7.org/fhir/codesystem-operation-subsumes.html">offical FHIR $subsumes operation docs</a> for more details.
 * @since 8.0
 */
final class FhirCodeSystemSubsumesRequest extends FhirCodeSystemOperationRequest<CodeSystemSubsumptionResultParameters> {

	private static final long serialVersionUID = 1L;

	@JsonProperty
	private final CodeSystemSubsumptionParameters parameters;

	private String codeAId;
	private String codeBId;
	private String codeAExpression;
	private String codeBExpression;

	public FhirCodeSystemSubsumesRequest(CodeSystemSubsumptionParameters parameters) {
		super(parameters.extractSystem(), parameters.extractSystemVersion());
		this.parameters = parameters;
		
		// Note this system value can come from either the specific system parameter or either codings if provided
		if (Strings.isNullOrEmpty(parameters.extractSystem())) {
			throw FhirExceptions.paramInvalid("Parameter 'system' is not specified for subsumption testing.");
		}
		
		final String codeA = parameters.getCodeA() == null ? null : parameters.getCodeA().getValue();
		final String codingA = parameters.getCodingA() == null ? null : parameters.getCodingA().getCode();
		final String codeB = parameters.getCodeB() == null ? null : parameters.getCodeB().getValue();
		final String codingB = parameters.getCodingB() == null ? null : parameters.getCodingB().getCode();

		final boolean hasCodeA = !Strings.isNullOrEmpty(codeA);
		final boolean hasCodingA = !Strings.isNullOrEmpty(codingA);
		final boolean hasCodeB = !Strings.isNullOrEmpty(codeB);
		final boolean hasCodingB = !Strings.isNullOrEmpty(codingB);
		
		// We do allow using codes and codings together as long as there is only one for A and B
		if (!hasCodeA && !hasCodingA) {
			throw FhirExceptions.paramInvalid("Provide either codeA or codingA.");
		}
		
		if (hasCodeA && hasCodingA) {
			throw FhirExceptions.paramInvalid("Provide either codeA or codingA, not both.");
		}
		
		if (!hasCodeB && !hasCodingB) {
			throw FhirExceptions.paramInvalid("Provide either codeB or codingB.");
		}
		
		if (hasCodeB && hasCodingB) {
			throw FhirExceptions.paramInvalid("Provide either codeB or codingB, not both.");
		}
		
		// extract actual concept IDs based on supplied parameters
		this.codeAId = hasCodeA ? codeA : codingA;
		this.codeBId = hasCodeB ? codeB : codingB;
		
		// select which property to present in the expression if invalid codes are supplied
		this.codeAExpression = hasCodeA ? "codeA" : "codingA";
		this.codeBExpression = hasCodeB ? "codeB" : "codingB";
	}
	
	@Override
	public CodeSystemSubsumptionResultParameters doExecute(ServiceProvider context, CodeSystem codeSystem) {
		// fetch both concepts first
		final Map<String, Concept> conceptsById = fetchConcepts(context, codeSystem, codeAId, codeBId);
		
		// extract them
		final Concept conceptA = conceptsById.get(codeAId);
		final Concept conceptB = conceptsById.get(codeBId);
		
		// check if they exists, if not they are invalid
		if (conceptA == null && conceptB == null) {
			throw FhirException.builder()
				.addIssue(
					OperationOutcome.IssueSeverity.ERROR,
					OperationOutcome.IssueType.CODEINVALID,
					TerminologyIssueType.INVALID_CODE,
					String.format("codeA: '%s' and codeB: '%s' are invalid or non-existent codes in '%s' system.", codeAId, codeBId, codeSystem.getUrl()),
					null  // XXX: FHIR validator has no test for this case, so it is unclear what would be expected
				).build();
		} else if (conceptA == null) {
			throw FhirException.builder()
				.addIssue(
					OperationOutcome.IssueSeverity.ERROR,
					OperationOutcome.IssueType.CODEINVALID,
					TerminologyIssueType.INVALID_CODE,
					String.format("codeA: '%s' is an invalid or non-existent code in '%s' system.", codeAId, codeSystem.getUrl()),
					codeAExpression
				).build();
		} else if (conceptB == null) {
			throw FhirException.builder()
				.addIssue(
					OperationOutcome.IssueSeverity.ERROR,
					OperationOutcome.IssueType.CODEINVALID,
					TerminologyIssueType.INVALID_CODE,
					String.format("codeB: '%s' is an invalid or non-existent code in '%s' system.", codeBId, codeSystem.getUrl()),
					codeBExpression
				).build();
		}
		
		// then check subsumption
		if (Objects.equals(codeAId, codeBId)) {
			return CodeSystemSubsumptionResultParameters.equivalent();
		} else if (isSubsumedBy(conceptA, codeBId)) {
			return CodeSystemSubsumptionResultParameters.subsumedBy();
		} else if (isSubsumedBy(conceptB, codeAId)) {
			return CodeSystemSubsumptionResultParameters.subsumes();
		} else {
			return CodeSystemSubsumptionResultParameters.notSubsumed();
		}
	}

	private Map<String, Concept> fetchConcepts(ServiceProvider context, CodeSystem codeSystem, String codeAId, String codeBId) {
		final List<String> conceptIds = List.of(codeAId, codeBId);
		
		final ResourceFragment resource = FhirModelHelpers.getResourceFragment(codeSystem);
		final Repository codeSystemToolingRepository = context.service(RepositoryManager.class).get(resource.getToolingId());
		
		final Options conceptSearchOptions = Options.builder()
			.put(ConceptSearchRequestEvaluator.OptionKey.ID, conceptIds)
			.put(ConceptSearchRequestEvaluator.OptionKey.LIMIT, conceptIds.size())
			.build();
		
		final ServiceProvider searchContext = context.inject().bind(ResourceFragment.class, resource).build();

		return codeSystemToolingRepository.service(ConceptSearchRequestEvaluator.class)
			.evaluate(resource.getResourceURI(), searchContext, conceptSearchOptions)
			.stream()
			.collect(Collectors.toMap(Concept::getId, concept -> concept));
	}
	
	private boolean isSubsumedBy(Concept subTypeConcept, String superTypeCode) {
		return subTypeConcept.getParentIds().contains(superTypeCode) || subTypeConcept.getAncestorIds().contains(superTypeCode);
	} 
}
