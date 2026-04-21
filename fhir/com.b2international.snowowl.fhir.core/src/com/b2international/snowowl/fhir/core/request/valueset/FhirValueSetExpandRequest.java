/*
 * Copyright 2021-2025 B2i Healthcare, https://b2ihealthcare.com
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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.Enumerations.FilterOperator;
import org.hl7.fhir.r5.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r5.model.ValueSet;

import com.b2international.commons.exceptions.NotFoundException;
import com.b2international.fhir.r5.operations.ValueSetExpandParameters;
import com.b2international.snowowl.core.RepositoryManager;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.TerminologyResource;
import com.b2international.snowowl.core.events.Request;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.R5ObjectFields;
import com.b2international.snowowl.fhir.core.exceptions.BadRequestException;
import com.b2international.snowowl.fhir.core.request.FhirRequests;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;

/**
 * @since 8.0
 */
final class FhirValueSetExpandRequest implements Request<ServiceProvider, ValueSet> {

	private static final long serialVersionUID = 1L;
	
	@JsonProperty
	private ValueSetExpandParameters parameters;

	public FhirValueSetExpandRequest(ValueSetExpandParameters parameters) {
		this.parameters = parameters;
	}
	
	@Override
	public ValueSet execute(ServiceProvider context) {
		
		final String uri = parameters.getUrl().asStringValue();
		ValueSet valueSet = null;
		
		try {
			
			valueSet = FhirRequests.valueSets().prepareGet(uri)
					.setElements(ImmutableList.<String>builder()
							.addAll(R5ObjectFields.ValueSet.SUMMARY)
							.add(R5ObjectFields.ValueSet.STATUS)
							.add(R5ObjectFields.ValueSet.COMPOSE)
							.build())
					.buildAsync()
					.execute(context);
			
		} catch (NotFoundException e) {
			
			// If there is no Value Set present for the given URL, then try to parse the URL to a meaningful value if possible and evaluate it
			if (uri.startsWith("http://")) {
				valueSet = expandImplicitValueSet(context, uri);
			}

			// If we couldn't come up with an implicit value set definition, fail using the original exception...
			if (valueSet == null) {
				throw e;
			}
		}

		// ...otherwise we should have a VS composition that can be evaluated 
		return context.service(RepositoryManager.class)
			.get(valueSet.getUserString(TerminologyResource.Fields.TOOLING_ID))
			.optionalService(FhirValueSetExpander.class)
			.orElse(FhirValueSetExpander.NOOP)
			.expand(context, valueSet, parameters);

	}

	private ValueSet expandImplicitValueSet(ServiceProvider context, String urlValue) {
		// only URLs with query parts are supported, every other case is rejected for now
		if (urlValue.contains("#")) {
			return null;
		}
		
		// restrict to only SNOMED CT for now, re-enable if clients relied on implicit value set expansion for other code systems
		if (!FhirModelHelpers.isSnomedUri(urlValue)) {
			return null;
		}
		
		// extract the non-query part from the URL value
		String version = urlValue.split("\\?")[0];
		String query = "";
		if (urlValue.contains("?")) {
			query = urlValue.split("\\?")[1];
		}
		
		// if this is the SNOMED CT base URI string then append the core module to represent the International Edition
		if (FhirModelHelpers.SNOMED_BASE_URI_STRING.equals(version)) {
			version = version.concat("/900000000000207008");
		}
		
		// try to lookup the CodeSystem using the baseUrl
		CodeSystem codeSystem = FhirRequests.codeSystems().prepareSearch()
			.one()
			.filterByUrl(FhirModelHelpers.SNOMED_BASE_URI_STRING)
			.filterByVersion(version)
			.buildAsync()
			.execute(context)
			.getEntry().stream().findFirst()
			.map(Bundle.BundleEntryComponent.class::cast)
			.map(Bundle.BundleEntryComponent::getResource)
			.map(CodeSystem.class::cast)
			.orElse(null);
		
		// if no CodeSystem stored to use as Value Set source, return NotFound response
		if (codeSystem == null) {
			return null;
		}
		
		// return the content of the CodeSystem as Value Set
		String id = Hashing.goodFastHash(8).hashString(urlValue, StandardCharsets.UTF_8).toString();
		ValueSet valueSet = (ValueSet) new ValueSet()
			.setUrl(urlValue)
			// according to https://terminology.hl7.org/SNOMEDCT.html#snomed-ct-implicit-value-sets publication status is always ACTIVE
			.setStatus(PublicationStatus.ACTIVE)
			.setId(id);
		
		// XXX: Use the code system's tooling ID as the expand service selector
		valueSet.setUserData(TerminologyResource.Fields.TOOLING_ID, codeSystem.getUserString(TerminologyResource.Fields.TOOLING_ID));
		valueSet.setUserData("codeSystemUri", FhirModelHelpers.resourceUriFrom(codeSystem));
		valueSet.setUserData("version", version);
		
		ValueSet.ValueSetComposeComponent compose = null;
		
		// configure query based on fhir_vs query parameter and also build the compose declaration for this implicit Value Set
		if (Strings.isNullOrEmpty(query) || "fhir_vs".equals(query)) {
			// do nothing, search all concepts
		} else if (query.startsWith("fhir_vs=")) {
			String fhirVsValue = query.replace("fhir_vs=", "");
			if (fhirVsValue.startsWith("ecl/")) {
				String ecl = fhirVsValue.replace("ecl/", "");
				
				// make sure we decode the ECL before using it
				try {
					ecl = URLDecoder.decode(ecl, StandardCharsets.UTF_8.toString());
				} catch (UnsupportedEncodingException e) {
					throw new BadRequestException("Failed to decode ECL expression: " + e.getMessage());
				}
				
				// configure Value Set for ECL
				valueSet
					.setName(String.format("%s Concepts matching %s", codeSystem.getName(), ecl))
					.setDescription(String.format("All SNOMED CT concepts that match the expression constraint %s", ecl));
				
				// configure compose for ECL
				compose = new ValueSet.ValueSetComposeComponent();
				compose.addInclude()
					.setSystem(FhirModelHelpers.SNOMED_BASE_URI_STRING)
					.addFilter()
						.setProperty("constraint")
						.setOp(FilterOperator.EQUAL)
						.setValue(ecl);

			} else if (fhirVsValue.startsWith("isa/")) {
				String parent = fhirVsValue.replace("isa/", "");
				
				// configure Value Set for IS A
				valueSet
					.setName(String.format("%s Concept %s and descendants", codeSystem.getName(), parent))
					.setDescription(String.format("All SNOMED CT concepts for %s", parent));
			
				// configure compose for IS A
				compose = new ValueSet.ValueSetComposeComponent();
				compose.addInclude()
					.setSystem(FhirModelHelpers.SNOMED_BASE_URI_STRING)
					.addFilter()
						.setProperty("constraint")
						.setOp(FilterOperator.ISA)
						.setValue(parent);

			} else if (fhirVsValue.startsWith("refset/")) {
				String refsetId = fhirVsValue.replace("refset/", "");
				if (Strings.isNullOrEmpty(refsetId)) {
					// TODO support refset identifier concept search
					return null;
				} else {
					// configure Value Set for REFSET
					valueSet
						.setName(String.format("%s Reference Set %s", codeSystem.getName(), refsetId))
						.setDescription(String.format("All SNOMED CT concepts in the reference set %s", refsetId));
					
					// configure compose for REFSET
					compose = new ValueSet.ValueSetComposeComponent();
					compose.addInclude()
						.setSystem(version)
						.addFilter()
							.setProperty("concept")
							.setOp(FilterOperator.IN)
							.setValue(refsetId);
				}
			} else {
				// no support for this unknown filter, return 404
				// TODO return unsupported maybe?
				// TODO check against declared filter values in CodeSystem
				return null;
			}
		}

		return valueSet.setCompose(compose);
	}
}
