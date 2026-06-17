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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.hl7.fhir.r5.model.CodeSystem;

import com.b2international.commons.exceptions.NotFoundException;
import com.b2international.commons.http.AcceptLanguageHeader;
import com.b2international.commons.options.Options;
import com.b2international.fhir.r5.operations.CodeSystemLookupParameters;
import com.b2international.fhir.r5.operations.CodeSystemLookupResultParameters;
import com.b2international.snowowl.core.*;
import com.b2international.snowowl.core.domain.Concept;
import com.b2international.snowowl.core.request.ConceptSearchRequestEvaluator;
import com.b2international.snowowl.core.request.ExpandParser;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.R5ObjectFields;
import com.b2international.snowowl.fhir.core.exceptions.BadRequestException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * Performs the lookup operation based on the parameter-based lookup request.
 * 
 * <p>
 * From the spec:
 * If no properties are specified, the server chooses what to return. The following properties are defined for all code systems: url, name, version (code system info) 
 * and code information: display, definition, designation, parent and child, and for designations, lang.X where X is a designation language code. 
 * Some of the properties are returned explicit in named parameters (when the names match), and the rest (except for lang.X) in the property parameter group
 * </p>
 * @see LookupRequest
 * @see LookupResult
 * @since 8.0
 */
final class FhirCodeSystemLookupRequest extends FhirCodeSystemOperationRequest<CodeSystemLookupResultParameters> {

	private static final long serialVersionUID = 1L;
	
	@JsonProperty
	private final CodeSystemLookupParameters parameters;

	FhirCodeSystemLookupRequest(CodeSystemLookupParameters parameters) {
		super(parameters.extractSystem(), parameters.extractSystemVersion());
		this.parameters = parameters;
	}

	@Override
	protected CodeSystemLookupResultParameters doExecute(ServiceProvider context, CodeSystem codeSystem) {
		validateRequestedProperties(codeSystem);
		
		final String displayLanguage = compactLocale(parameters.getDisplayLanguage());
		
		final ResourceFragment resource = FhirModelHelpers.getResourceFragment(codeSystem);

		final Repository codeSystemToolingRepository = context.service(RepositoryManager.class).get(resource.getToolingId());
		final FhirCodeSystemLookupConverter converter = codeSystemToolingRepository
				.optionalService(FhirCodeSystemLookupConverter.class)
				.orElse(FhirCodeSystemLookupConverter.DEFAULT);
		
		final String conceptExpand = converter.configureConceptExpand(parameters);
		
		final ResourceURI codeSystemUri = resource.getResourceURI();
		
		// for performance reasons, running the raw evaluator here as we already identified the CodeSystem to evaluate it on
		Options conceptSearchOptions = Options.builder()
				.put(ConceptSearchRequestEvaluator.OptionKey.ID, parameters.extractCode())
				.put(ConceptSearchRequestEvaluator.OptionKey.LIMIT, 1)
				.put(ConceptSearchRequestEvaluator.OptionKey.LOCALES, AcceptLanguageHeader.parseHeader(displayLanguage))
				.put(ConceptSearchRequestEvaluator.OptionKey.EXPAND, ExpandParser.parse(conceptExpand))
				.build();
		
		// seed already fetched resource information to prevent refetching the metadata
		final ServiceProvider searchContext = context.inject().bind(ResourceFragment.class, resource).build();
		
		Concept concept = codeSystemToolingRepository.service(ConceptSearchRequestEvaluator.class)
				.evaluate(codeSystemUri, searchContext, conceptSearchOptions)
				.first()
				.orElseThrow(() -> new NotFoundException("Concept", parameters.getCode().getCode()));
		
		CodeSystemLookupResultParameters result = new CodeSystemLookupResultParameters();
		
		result.setName(codeSystem.getName());
		result.setDisplay(concept.getTerm());
		result.setVersion(codeSystem.getVersion());
		result.setDesignation(converter.expandDesignations(context, codeSystem, concept, parameters));
		result.setProperty(converter.expandProperties(context, codeSystem, concept, parameters));
		
		return result;
	}
	
	@Override
	protected Set<String> configureFieldsToLoad() {
		if (!parameters.getPropertyValues().isEmpty()) {
			// if an non-official property is requested, then we need to load the property part 
			final Set<String> requestedProperties = Set.copyOf(parameters.getPropertyValues());
			// first check if any of the properties are lookup request properties
			final Set<String> nonLookupRequestProperties = Sets.difference(requestedProperties, CodeSystemLookupParameters.OFFICIAL_R5_PROPERTY_VALUES);
			
			if (!nonLookupRequestProperties.isEmpty()) {
				return ImmutableSet.<String>builder()
						// ensure we always extend the superclass default set of required fields
						.addAll(super.configureFieldsToLoad())
						// expand the properties of the CodeSystem, so that we can properly expand it on the concept
						.add(R5ObjectFields.CodeSystem.PROPERTY)
						.build();
			}
		}
		// super defines the ultra minimal set
		return super.configureFieldsToLoad();
	}
	
	private void validateRequestedProperties(CodeSystem codeSystem) {
		// No property requested, nothing to validate
		if (parameters.getPropertyValues().isEmpty()) {
			return;
		}

		final Set<String> requestedProperties = Set.copyOf(parameters.getPropertyValues());

		final List<CodeSystem.PropertyComponent> codeSystemProperties = codeSystem.getProperty() == null
				? Collections.emptyList()
				: codeSystem.getProperty();
		
		// First, check if any of the properties are lookup request properties
		final Set<String> nonLookupProperties = Sets.difference(requestedProperties, CodeSystemLookupParameters.OFFICIAL_R5_PROPERTY_VALUES);
		
		// Second, check if the remaining unsupported properties are supported by the CodeSystem either via full URL
		final Set<String> supportedPropertyUris = codeSystemProperties.stream()
				.map(CodeSystem.PropertyComponent::getUri)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		final Set<String> unsupportedByUri = Sets.difference(nonLookupProperties, supportedPropertyUris);
		
		// or via their code only
		final Set<String> supportedPropertyCodes = codeSystemProperties.stream()
				.map(CodeSystem.PropertyComponent::getCode)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		final Set<String> unsupportedProperties = Sets.difference(unsupportedByUri, supportedPropertyCodes);

		if (!unsupportedProperties.isEmpty()) {
			final Set<String> supportedPropertiesDisplay = codeSystemProperties.stream()
					.map(property -> property.getUri() != null ? property.getUri() : property.getCode())
					.filter(Objects::nonNull)
					.collect(Collectors.toSet());

			if (unsupportedProperties.size() == 1) {
				throw new BadRequestException(String.format("Unrecognized property %s. Supported properties are: %s.", unsupportedProperties, supportedPropertiesDisplay), "LookupRequest.property");
			} else {
				throw new BadRequestException(String.format("Unrecognized properties %s. Supported properties are: %s.", unsupportedProperties, supportedPropertiesDisplay), "LookupRequest.property");
			}
		}
	} 
}
