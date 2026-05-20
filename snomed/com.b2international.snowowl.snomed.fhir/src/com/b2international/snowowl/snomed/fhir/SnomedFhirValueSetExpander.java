/*
 * Copyright 2024-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.snomed.fhir;

import java.util.*;

import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.StringType;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ConceptReferenceDesignationComponent;
import org.hl7.fhir.r5.model.ValueSet.ValueSetExpansionContainsComponent;

import com.b2international.commons.CompareUtils;
import com.b2international.commons.http.AcceptLanguageHeader;
import com.b2international.commons.options.Options;
import com.b2international.commons.options.OptionsBuilder;
import com.b2international.fhir.r5.operations.ValueSetExpandParameters;
import com.b2international.snowowl.core.*;
import com.b2international.snowowl.core.domain.Concept;
import com.b2international.snowowl.core.domain.Concepts;
import com.b2international.snowowl.core.request.ConceptSearchRequestEvaluator;
import com.b2international.snowowl.core.request.ExpandParser;
import com.b2international.snowowl.core.request.SearchIndexResourceRequest;
import com.b2international.snowowl.core.request.SearchResourceRequest;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.R5ObjectFields;
import com.b2international.snowowl.fhir.core.request.codesystem.FhirRequest;
import com.b2international.snowowl.fhir.core.request.valueset.FhirValueSetExpander;
import com.b2international.snowowl.snomed.core.SnomedDisplayTermType;
import com.b2international.snowowl.snomed.core.domain.Acceptability;
import com.b2international.snowowl.snomed.core.domain.SnomedDescription;

/**
 * @since 9.5.0
 */
public class SnomedFhirValueSetExpander extends SnomedFhirImplicitValueSetSupport implements FhirValueSetExpander {

	@Override
	public ValueSet expand(ServiceProvider context, ValueSet valueSet, ValueSetExpandParameters parameters) {
		// XXX since this is an implicit VS, and resource stored in the VS here is a CodeSystem referring to the proper SNOMED CT Edition
		final ResourceFragment resource = FhirModelHelpers.getResourceFragment(valueSet);
		ResourceURI codeSystemUri = resource.getResourceURI();
		
		if (parameters.getDate() != null) {
			codeSystemUri = codeSystemUri.withTimestampPart("@" + Long.toString(parameters.getDate().getValue().getTime()));
		}
		
		final String termFilter = parameters.getFilter() == null ? null : parameters.getFilter().getValue();

		// for performance reasons, running the raw evaluator here as we already identified the CodeSystem to evaluate it on
		OptionsBuilder conceptSearchOptions = Options.builder()
				.put(ConceptSearchRequestEvaluator.OptionKey.ACTIVE, parameters.getActiveOnly() == null ? null : parameters.getActiveOnly().getValue())
				.put(ConceptSearchRequestEvaluator.OptionKey.TERM, termFilter)
				.put(ConceptSearchRequestEvaluator.OptionKey.LIMIT, parameters.getCount() == null ? 10 : parameters.getCount().getValue())
				.put(ConceptSearchRequestEvaluator.OptionKey.AFTER, parameters.getAfter() == null ? null : parameters.getAfter().getValue())
				// SNOMED only preferred display support (VS should always use FSN)
				.put(ConceptSearchRequestEvaluator.OptionKey.DISPLAY, "FSN")
				.put(ConceptSearchRequestEvaluator.OptionKey.LOCALES, AcceptLanguageHeader.parseHeader(FhirRequest.compactLocale(parameters.getDisplayLanguage())))
				// always return sorted results for consistency, in case of term filtering return by score otherwise by ID
				.put(SearchResourceRequest.OptionKey.SORT_BY, !CompareUtils.isEmpty(termFilter) ? SearchIndexResourceRequest.SCORE : SearchResourceRequest.Sort.fieldAsc("id"));
		
		configureValueSetQuery(valueSet, conceptSearchOptions);
		
		final boolean includeDesignations = parameters.getIncludeDesignations() != null && parameters.getIncludeDesignations().getValue();
		if (includeDesignations) {
			// Expand descriptions when requested via includeDesignations
			conceptSearchOptions.put(ConceptSearchRequestEvaluator.OptionKey.EXPAND, ExpandParser.parse("descriptions(expand(type(expand(pt()))))"));
		}
		
		// seed already fetched resource information to prevent refetching the metadata
		final ServiceProvider searchContext = context.inject().bind(ResourceFragment.class, resource).build();
		
		final Repository codeSystemToolingRepository = context.service(RepositoryManager.class).get(resource.getToolingId());
		final Concepts concepts = codeSystemToolingRepository.service(ConceptSearchRequestEvaluator.class)
				.evaluate(codeSystemUri, searchContext, conceptSearchOptions.build());
		
		final ValueSet.ValueSetExpansionComponent expansion = new ValueSet.ValueSetExpansionComponent()
				.setIdentifier(valueSet.getId())
				.setTimestampElement(FhirModelHelpers.toDateTimeElement(new Date()))
				.setTotal(concepts.getTotal());

		expansion.addExtension(FhirValueSetExpander.EXTENSION_AFTER_PROPERTY_URL, new StringType(concepts.getSearchAfter()));
		
		final String version = valueSet.getUserString(R5ObjectFields.ValueSet.UserData.CODE_SYSTEM_VERSION);
		
		
		for (Concept concept : concepts) {
			var contains = new ValueSet.ValueSetExpansionContainsComponent()
				.setCode(concept.getId())
				.setSystem(FhirModelHelpers.SNOMED_BASE_URI_STRING)
				.setVersion(version)
				.setDisplay(concept.getTerm());
			
			if (includeDesignations) {
				includeDesignations(version, concept, contains);
			}
			
			expansion.addContains(contains);
		}
		
		return valueSet.setExpansion(expansion);
	}

	private void includeDesignations(final String version, Concept concept, ValueSetExpansionContainsComponent contains) {
		/*
		 * XXX: We create multiple tooling-independent representations in the general
		 * concept representation, these need to be collapsed back into a single
		 * internal instance.
		 */
		final List<SnomedDescription> snomedDescriptions = concept.getDescriptions()
			.stream()
			.map(d -> (SnomedDescription) d.getInternalDescription())
			.distinct()
			.toList();
			
		for (final SnomedDescription snomedDescription : snomedDescriptions) {

			/* 
			 * Convert language reference set ID, acceptability and description type triples into "designation-use-context" extensions
			 * https://confluence.ihtsdotools.org/display/FHIR/Designation+extension
			 */
			final Map<String, Acceptability> acceptabilityMap = snomedDescription.getAcceptabilityMap();
			final List<Extension> designationExtensions = new ArrayList<>(acceptabilityMap.size());
			final List<String> languageRefsetIds = acceptabilityMap.keySet()
				.stream()
				.sorted()
				.toList();

			// Extract information about the description type here because it is used both in "designation-use-context" and the converted designation
			final Coding typeCoding = new Coding()
				.setSystem(FhirModelHelpers.SNOMED_BASE_URI_STRING)
				.setVersion(version)
				.setCode(snomedDescription.getTypeId())
				.setDisplay(SnomedDisplayTermType.PT.getLabel(snomedDescription.getType()));

			for (String languageRefsetId : languageRefsetIds) {

				// Extension "context" encodes the language reference set ID
				final Coding contextCoding = new Coding()
					.setSystem(FhirModelHelpers.SNOMED_BASE_URI_STRING)
					.setVersion(version)
					.setCode(languageRefsetId);
				
				// Extension "role" encodes the acceptability ID for the language reference set
				final Acceptability acceptability = acceptabilityMap.get(languageRefsetId);
				
				final Coding roleCoding = new Coding()
					.setSystem(FhirModelHelpers.SNOMED_BASE_URI_STRING)
					.setVersion(version)
					.setCode(acceptability.getConceptId())
					.setDisplay(acceptability.getLabel());
				
				final Extension useContextExtension = new Extension("http://snomed.info/fhir/StructureDefinition/designation-use-context");
				
				useContextExtension.addExtension()
					.setUrl("context")
					.setValue(contextCoding);

				useContextExtension.addExtension()
					.setUrl("role")
					.setValue(roleCoding);

				useContextExtension.addExtension()
					.setUrl("type")
					.setValue(typeCoding);
				
				designationExtensions.add(useContextExtension);
			}
			
			/*
			 * FIXME: Using "en" when the description's languageCode is not available. See also:
			 * SnomedConceptSearchRequestEvaluator#generateGenericDescriptions(SnomedDescriptions)
			 * - we are partially repeating/undoing the steps taken there
			 */
			final String languageCode = Optional.ofNullable(snomedDescription.getLanguageCode()).orElse("en");
			
			// Now convert the native SNOMED CT description into a FHIR designation
			var designation = new ConceptReferenceDesignationComponent();
			
			designation
				.setValue(snomedDescription.getTerm())
				.setLanguage(languageCode)
				.setUse(typeCoding)
				.setExtension(designationExtensions);
			
			contains.addDesignation(designation);
		}
	}
}
