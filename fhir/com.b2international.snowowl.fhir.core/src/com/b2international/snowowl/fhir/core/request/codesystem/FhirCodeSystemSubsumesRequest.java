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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
		
		validateCodes(context, codeSystem, codeA, codeB);
		
		if (Objects.equals(codeA, codeB)) {
			return CodeSystemSubsumptionResultParameters.equivalent();
		} else if (isSubsumedBy(context, codeSystem, codeA, codeB)) {
			return CodeSystemSubsumptionResultParameters.subsumedBy(); 
		} else if (isSubsumedBy(context, codeSystem, codeB, codeA)) {
			return CodeSystemSubsumptionResultParameters.subsumes();	
		} else {
			return CodeSystemSubsumptionResultParameters.notSubsumed();
		}
	}

	private boolean isSubsumedBy(ServiceProvider context, CodeSystem codeSystem, final String subType, final String superType) {
		final ResourceFragment resource = FhirModelHelpers.getResourceFragment(codeSystem);

		final Repository codeSystemToolingRepository = context.service(RepositoryManager.class).get(resource.getToolingId());
		
		// for performance reasons, running the raw evaluator here as we already identified the CodeSystem to evaluate it on
		Options conceptSearchOptions = Options.builder()
				.put(ConceptSearchRequestEvaluator.OptionKey.ID, subType)
				.put(ConceptSearchRequestEvaluator.OptionKey.ANCESTOR, superType)
				// we only need hit count to answer the subsumes question
				.put(ConceptSearchRequestEvaluator.OptionKey.LIMIT, 0) 
				.build();
		
		// seed already fetched resource information to prevent refetching the metadata
		final ServiceProvider searchContext = context.inject().bind(ResourceFragment.class, resource).build();
		
		return codeSystemToolingRepository.service(ConceptSearchRequestEvaluator.class)
				.evaluate(resource.getResourceURI(), searchContext, conceptSearchOptions)
				.getTotal() > 0;
	}

	private void validateCodes(ServiceProvider context, CodeSystem codeSystem, String codeA, String codeB) {
		if (codeA == null || codeB == null) {
			throw new BadRequestException("codeA and codeB parameters are required");
		}
		
		final List<String> codesToValidate = List.of(codeA, codeB);
		final ResourceFragment resource = FhirModelHelpers.getResourceFragment(codeSystem);
		final Repository codeSystemToolingRepository = context.service(RepositoryManager.class).get(resource.getToolingId());
	
		final Options conceptSearchOptions = Options.builder()
				.put(ConceptSearchRequestEvaluator.OptionKey.ID, codesToValidate)
				.put(ConceptSearchRequestEvaluator.OptionKey.LIMIT, codesToValidate.size())
				.build();
	
		final ServiceProvider searchContext = context.inject()
				.bind(ResourceFragment.class, resource)
				.build();
	
		final Set<String> existingConceptIds = codeSystemToolingRepository.service(ConceptSearchRequestEvaluator.class)
				.evaluate(resource.getResourceURI(), searchContext, conceptSearchOptions)
				.stream()
				.map(Concept::getId)
				.collect(Collectors.toSet());
	
		final Set<String> missingConceptIds = codesToValidate.stream()
				.filter(code -> !existingConceptIds.contains(code))
				.collect(Collectors.toCollection(HashSet::new));
	
		if (!missingConceptIds.isEmpty()) {
			throw new BadRequestException("An invalid code was supplied");
		}
	}
}
