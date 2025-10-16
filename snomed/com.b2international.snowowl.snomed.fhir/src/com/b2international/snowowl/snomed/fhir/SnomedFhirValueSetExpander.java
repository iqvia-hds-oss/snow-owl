/*
 * Copyright 2024-2025 B2i Healthcare, https://b2ihealthcare.com
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

import static com.google.common.collect.Lists.newArrayListWithCapacity;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.Enumerations.FilterOperator;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.StringType;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.*;

import com.b2international.commons.CompareUtils;
import com.b2international.fhir.r5.operations.ValueSetExpandParameters;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.codesystem.CodeSystemRequests;
import com.b2international.snowowl.core.domain.Concept;
import com.b2international.snowowl.core.domain.Concepts;
import com.b2international.snowowl.core.request.ConceptSearchRequestBuilder;
import com.b2international.snowowl.core.request.SearchIndexResourceRequest;
import com.b2international.snowowl.core.request.SearchResourceRequest;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.request.codesystem.FhirRequest;
import com.b2international.snowowl.fhir.core.request.valueset.FhirValueSetExpander;
import com.b2international.snowowl.snomed.core.SnomedDisplayTermType;
import com.b2international.snowowl.snomed.core.domain.Acceptability;
import com.b2international.snowowl.snomed.core.domain.SnomedDescription;

/**
 * @since 9.5.0
 */
public class SnomedFhirValueSetExpander implements FhirValueSetExpander {

	@Override
	public ValueSet expand(ServiceProvider context, ValueSet valueSet, ValueSetExpandParameters parameters) {
		
		final String termFilter = parameters.getFilter() == null ? null : parameters.getFilter().getValue();
		
		ConceptSearchRequestBuilder req = CodeSystemRequests.prepareSearchConcepts()
			.filterByCodeSystemUri((ResourceURI) valueSet.getUserData("codeSystemUri"))
			.filterByActive(parameters.getActiveOnly() == null ? null : parameters.getActiveOnly().getValue())
			.filterByTerm(termFilter)
			.setLimit(parameters.getCount() == null ? 10 : parameters.getCount().getValue())
			.setSearchAfter(parameters.getAfter() == null ? null : parameters.getAfter().getValue())
			// SNOMED only preferred display support (VS should always use FSN)
			.setPreferredDisplay("FSN")
			// Expand descriptions so that type information can be extracted later
			.setExpand("descriptions(expand(type(expand(pt()))))")
			.setLocales(FhirRequest.compactLocale(parameters.getDisplayLanguage()))
			// always return sorted results for consistency, in case of term filtering return by score otherwise by ID
			.sortBy(!CompareUtils.isEmpty(termFilter) ? SearchIndexResourceRequest.SCORE : SearchResourceRequest.Sort.fieldAsc("id"));

		
		if (!valueSet.hasCompose()) {
			// do nothing, search all concepts
		} else {
			final ValueSetComposeComponent compose = valueSet.getCompose();
			final ConceptSetComponent firstInclude = compose.getIncludeFirstRep();
			final ConceptSetFilterComponent firstFilter = firstInclude.getFilterFirstRep();
			
			if ("constraint".equals(firstFilter.getProperty()) && FilterOperator.EQUAL.equals(firstFilter.getOp())) {
				// Filter concepts by ECL
				req.filterByQuery(firstFilter.getValue());
			} else if ("constraint".equals(firstFilter.getProperty()) && FilterOperator.ISA.equals(firstFilter.getOp())) {
				// Filter concepts by ancestor
				req.filterByAncestor(firstFilter.getValue());
			} else if ("concept".equals(firstFilter.getProperty()) && FilterOperator.IN.equals(firstFilter.getOp())) {
				req.filterByQuery("^" + firstFilter.getValue());
			} else {
				throw new IllegalStateException("Unsupported implicit value set compose definition: " + compose);
			}
		}
		
		final Concepts concepts = req.buildAsync().execute(context);
		
		final ValueSet.ValueSetExpansionComponent expansion = new ValueSet.ValueSetExpansionComponent()
				.setIdentifier(valueSet.getId())
				.setTimestampElement(FhirModelHelpers.toDateTimeElement(new Date()))
				.setTotal(concepts.getTotal());

		expansion.addExtension(FhirValueSetExpander.EXTENSION_AFTER_PROPERTY_URL, new StringType(concepts.getSearchAfter()));
		
		final String baseUrl = valueSet.getUserString("baseUrl");
		
		for (Concept concept : concepts) {
			var contains = new ValueSet.ValueSetExpansionContainsComponent()
				.setCode(concept.getId())
				.setSystem(baseUrl)
				.setDisplay(concept.getTerm());
			
			if (parameters.getIncludeDesignations() != null && parameters.getIncludeDesignations().getValue()) {
				includeDesignations(baseUrl, concept, contains);
			}
			
			expansion.addContains(contains);
		}
		
		return valueSet.setExpansion(expansion);
	}

	private void includeDesignations(final String baseUrl, Concept concept, ValueSetExpansionContainsComponent contains) {
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
			final List<Extension> designationExtensions = newArrayListWithCapacity(acceptabilityMap.size());
			final List<String> languageRefsetIds = acceptabilityMap.keySet()
				.stream()
				.sorted()
				.toList();

			// Extract information about the description type here because it is used both in "designation-use-context" and the converted designation
			final Coding typeCoding = new Coding()
				.setSystem(baseUrl)
				.setCode(snomedDescription.getTypeId())
				.setDisplay(SnomedDisplayTermType.PT.getLabel(snomedDescription.getType()));

			for (String languageRefsetId : languageRefsetIds) {

				// Extension "context" encodes the language reference set ID
				final Coding contextCoding = new Coding()
					// FIXME: "system" may need to be set to the code system's URL we are calling from
					.setSystem(baseUrl)
					.setCode(languageRefsetId);
				
				// Extension "role" encodes the acceptability ID for the language reference set
				final Acceptability acceptability = acceptabilityMap.get(languageRefsetId);
				
				final Coding roleCoding = new Coding()
					// FIXME: "system" may need to be set to the code system's URL we are calling from
					.setSystem(baseUrl)
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
