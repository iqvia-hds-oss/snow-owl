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
package com.b2international.snowowl.fhir.core.request.conceptmap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.Enumerations.PublicationStatus;

import com.b2international.fhir.r5.operations.ConceptMapTranslateParameters;
import com.b2international.fhir.r5.operations.ConceptMapTranslateResultParameters;
import com.b2international.snowowl.core.RepositoryManager;
import com.b2international.snowowl.core.ResourceFragment;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.request.ResourceRequest;
import com.b2international.snowowl.core.request.SearchResourceRequest.Sort;
import com.b2international.snowowl.core.version.VersionDocument;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.R5ObjectFields;
import com.b2international.snowowl.fhir.core.exceptions.BadRequestException;
import com.b2international.snowowl.fhir.core.request.FhirRequests;
import com.b2international.snowowl.fhir.core.request.codesystem.FhirCodeSystemOperationRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.hash.Hashing;

/**
 * @since 8.0
 */
final class FhirConceptMapTranslateRequest extends ResourceRequest<ServiceProvider, ConceptMapTranslateResultParameters> {

	private static final long serialVersionUID = 1L;
	
	@JsonProperty
	private final ConceptMapTranslateParameters parameters;

	public FhirConceptMapTranslateRequest(ConceptMapTranslateParameters parameters) {
		this.parameters = parameters;

		if (parameters.getUrl() == null || parameters.getUrl().getValue() == null) {
			throw new BadRequestException("'url' is required to reduce the scope of the translate operation to a single ConceptMap");
		}
		
		// One (and only one) of the in parameters (sourceCode, sourceCoding, sourceCodeableConcept, targetCode, targetCoding, or targetCodeableConcept) SHALL be provided, to identify the code that is to be translated.
		final long nonNullInputs = Stream.of(
			parameters.getSourceCode(), 
			parameters.getSourceCoding(), 
			parameters.getSourceCodeableConcept(),
			parameters.getTargetCode(),
			parameters.getTargetCoding(),
			parameters.getTargetCodeableConcept()
		)
		.filter(p -> p != null)
		.count();
		
		if (nonNullInputs != 1L) {
			throw new BadRequestException("One (and only one) of the 'in' parameters (sourceCode, sourceCoding, sourceCodeableConcept, targetCode, targetCoding, targetCodeableConcept) must be provided to identify the code that is to be translated.");
		}
		
		// Check that an actual sourceCode and targetCode was provided as the value itself can still be null
		if (FhirConceptMapTranslator.getSourceCoding(parameters).getCode() == null && FhirConceptMapTranslator.getTargetCoding(parameters).getCode() == null) {
			throw new BadRequestException("Either sourceCode or targetCode must be provided to identify the code that is to be translated");
		};
		
// XXX: according to the fhir specification system is required if sourceCode was provided		
//		if (parameters.getSourceCode() != null && parameters.getSystem() == null) {
//			throw new BadRequestException("'system' is required when using 'sourceCode'");
//		}
	}
	
	@Override
	public ConceptMapTranslateResultParameters execute(ServiceProvider context) {
		ConceptMap conceptMap = lookupConceptMaps(context);
		if (conceptMap == null) {
			return new ConceptMapTranslateResultParameters()
					.setResult(false)
					.setMessage(String.format("ConceptMap '%s' does not exist and/or not yet created.", parameters.getUrl().getValue()));
		}
		return context.service(RepositoryManager.class)
				.get(FhirModelHelpers.getResourceFragment(conceptMap).getToolingId())
				.optionalService(FhirConceptMapTranslator.class)
				.orElse(FhirConceptMapTranslator.NOOP)
				.translate(context, conceptMap, parameters);
	}

	// TODO make this consider source/target scopes to find appropriate ConceptMaps when URL is not defined, for now we basically need the URL parameter to be able to translate using a dedicated map
	private ConceptMap lookupConceptMaps(ServiceProvider context) {
		if (FhirModelHelpers.isImplicitConceptMapUrl(parameters.getUrl().getValue())) {
			return buildImplicitConceptMap(context, parameters.getUrl().getValue());
		} else {
			return FhirRequests.conceptMaps().prepareSearch()
					.filterById(parameters.getUrl().getValue())
					.setElements(List.copyOf(R5ObjectFields.ConceptMap.MANDATORY))
					.setCount(1)
					.buildAsync()
					.execute(context)
					.getEntry()
					.stream()
					.map(BundleEntryComponent::getResource)
					.filter(ConceptMap.class::isInstance)
					.map(ConceptMap.class::cast)
					.findFirst()
					.orElse(null);
		}
	}

	private ConceptMap buildImplicitConceptMap(ServiceProvider context, String urlValue) {
		// only URLs with query parts are supported, every other case is rejected for now
		if (urlValue.contains("#")) {
			throw new BadRequestException("Unsupported implicit Concept Map URL with fragment '#' character: " + urlValue, urlValue);
		}
		
		String codeSystemUrl = null;
		String version = null;
		
		if (FhirModelHelpers.isSnomedImplicitConceptMapUrl(urlValue)) {
			codeSystemUrl = FhirModelHelpers.SNOMED_BASE_URI_STRING;
			// extract the non-query part from the URL value
			String[] parts = urlValue.split("\\?");
			version = parts[0];
			final String query = parts[1];
			
			// if this is the SNOMED CT base URI string then append the core module to represent the International Edition
			if (FhirModelHelpers.SNOMED_BASE_URI_STRING.equals(version)) {
				version = version.concat("/900000000000207008");
			}
			if (!query.startsWith("fhir_cm=")) {
				throw new BadRequestException("Unsupported implicit Concept Map URL query type: " + urlValue, urlValue);
			}
		} else {
			throw new BadRequestException("Unsupported implicit Concept Map URL " + urlValue, urlValue);
		}
		
		// try to lookup the CodeSystem using the baseUrl and version (to get the proper edition)
		CodeSystem codeSystem = FhirRequests.codeSystems().prepareSearch()
			.one()
			.filterByUrl(codeSystemUrl)
			.filterByVersion(version)
			.setElements(FhirCodeSystemOperationRequest.MINIMAL_CODESYSTEM_FIELD_SELECTION, false)
			.sortBy(Sort.fieldDesc(VersionDocument.Fields.EFFECTIVE_TIME)) // Use latest version if "filterByVersion" is left unused
			.buildAsync()
			.execute(context)
			.getEntry()
			.stream()
			.findFirst()
			.map(Bundle.BundleEntryComponent::getResource)
			.map(CodeSystem.class::cast)
			.orElse(null);
		
		// if no CodeSystem stored to use as Concept Map source, return bad request response
		if (codeSystem == null) {
			throw new BadRequestException("Supported implicit Concept Map URL but no underlying CodeSystem is available at " + codeSystemUrl, urlValue);
		}
		
		// return the content of the CodeSystem as Concept Map
		String id = Hashing.goodFastHash(8).hashString(urlValue, StandardCharsets.UTF_8).toString();
		
		ConceptMap conceptMap = (ConceptMap) new ConceptMap()
			.setUrl(urlValue)
			// according to https://terminology.hl7.org/en/SNOMEDCT.html#snomed-ct-implicit-concept-maps publication status is always ACTIVE
			.setStatus(PublicationStatus.ACTIVE)
			.setId(id);
		
		// since an implicit Concept Map does not have an internal resource representation, use the CodeSystem's fragment instead
		ResourceFragment resource = FhirModelHelpers.getResourceFragment(codeSystem);
		conceptMap.setUserData(R5ObjectFields.MetadataResource.UserData.INTERNAL_RESOURCE, resource);
		// TODO: this could be added the translator in parameter as `displayLangauge` similarly to the value set expand operation
		conceptMap.setUserData(R5ObjectFields.ConceptMap.UserData.LOCALE, locales());
		
		// XXX: We do not create a default group as we do not know yet what is the reference set's source and target
		
		return conceptMap;
	}
}
