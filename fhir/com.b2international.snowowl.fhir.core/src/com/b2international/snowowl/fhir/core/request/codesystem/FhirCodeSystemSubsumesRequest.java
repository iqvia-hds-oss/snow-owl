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

import com.b2international.commons.options.Options;
import com.b2international.fhir.r5.operations.CodeSystemSubsumptionParameters;
import com.b2international.fhir.r5.operations.CodeSystemSubsumptionResultParameters;
import com.b2international.snowowl.core.Repository;
import com.b2international.snowowl.core.RepositoryManager;
import com.b2international.snowowl.core.ResourceFragment;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.domain.Concept;
import com.b2international.snowowl.core.request.ConceptSearchRequestEvaluator;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.exceptions.BadRequestException;
import com.fasterxml.jackson.annotation.JsonProperty;

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

	public FhirCodeSystemSubsumesRequest(CodeSystemSubsumptionParameters parameters) {
		super(parameters.extractSystem(), parameters.extractSystemVersion());
		this.parameters = parameters;
	}

	@Override
	public CodeSystemSubsumptionResultParameters doExecute(ServiceProvider context, CodeSystem codeSystem) {
		
		final String codeA = parameters.getCodeA() != null ? parameters.getCodeA().getValue() : parameters.getCodingA().getCode();
		final String codeB = parameters.getCodeB() != null ? parameters.getCodeB().getValue() : parameters.getCodingB().getCode();
		
		final Map<String, Concept> conceptsById = fetchConcepts(context, codeSystem, codeA, codeB);
		
		final Concept conceptA = conceptsById.get(codeA);
		final Concept conceptB = conceptsById.get(codeB);
		
		if (conceptA == null) {
			throw new BadRequestException(String.format("An invalid code was supplied, codeA: \"%s\".", codeA));
		}
		
		if (conceptB == null) {
			throw new BadRequestException(String.format("An invalid code was supplied, codeB: \"%s\".", codeB));
		}
		
		if (Objects.equals(codeA, codeB)) {
			return CodeSystemSubsumptionResultParameters.equivalent();
		} else if (isSubsumedBy(conceptA, codeB)) {
			return CodeSystemSubsumptionResultParameters.subsumedBy();
		} else if (isSubsumedBy(conceptB, codeA)) {
			return CodeSystemSubsumptionResultParameters.subsumes();
		} else {
			return CodeSystemSubsumptionResultParameters.notSubsumed();
		}
	}

	private Map<String, Concept> fetchConcepts(ServiceProvider context, CodeSystem codeSystem, String codeA, String codeB) {
		
		if (codeA == null || codeB == null) {
			throw new BadRequestException("codeA and codeB parameters are required");
		}

		final List<String> conceptIds = List.of(codeA, codeB);

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
		return subTypeConcept.getAncestorIds() != null && 
				(subTypeConcept.getParentIds().contains(superTypeCode) || subTypeConcept.getAncestorIds().contains(superTypeCode));
	}
}
