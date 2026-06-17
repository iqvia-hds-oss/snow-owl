/*
 * Copyright 2026 B2i Healthcare, https://b2ihealthcare.com
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

import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.events.Request;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.R5ObjectFields;
import com.b2international.snowowl.fhir.core.exceptions.BadRequestException;
import com.b2international.snowowl.fhir.core.request.FhirRequests;
import com.b2international.snowowl.fhir.core.request.codesystem.FhirCodeSystemOperationRequest;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;

/**
 * Common base class to handle implicit ValueSet generation when performing various ValueSet operations.
 * 
 * @see FhirValueSetExpandRequest
 * @see FhirValueSetValidateCodeRequest
 * 
 * @since 10.1.0
 * @param <R> - the result parameter type
 */
public abstract class FhirValueSetOperationRequest<R> implements Request<ServiceProvider, R> {

	private static final long serialVersionUID = 1L;

	private final String url;
	
	public FhirValueSetOperationRequest(String url) {
		if (Strings.isNullOrEmpty(url)) {
			throw new BadRequestException("URL must be defined to identify a value set. 'valueSet' parameter is not available yet.", "url");
		}
		this.url = url;
	}
	
	protected final String getUrl() {
		return url;
	}
	
	@Override
	public final R execute(ServiceProvider context) {
		
		// for now we need an URL to be defined
		
		ValueSet valueSet = null;
		
		// check if url is an implicit URL
		if (FhirModelHelpers.isImplicitValueSetURL(url)) {
			valueSet = expandImplicitValueSet(context, url);
		} else {
			valueSet = FhirRequests.valueSets().prepareGet(url)
					.setElements(ImmutableList.<String>builder()
							.addAll(R5ObjectFields.ValueSet.SUMMARY)
							.add(R5ObjectFields.ValueSet.STATUS)
							.add(R5ObjectFields.ValueSet.COMPOSE)
							.build())
					.buildAsync()
					.execute(context);
		}
		
		return doExecute(context, valueSet);
	}

	protected abstract R doExecute(ServiceProvider context, ValueSet valueSet);
	
	private ValueSet expandImplicitValueSet(ServiceProvider context, String urlValue) {
		// only URLs with query parts are supported, every other case is rejected for now
		if (urlValue.contains("#")) {
			throw new BadRequestException("Unsupported implicit Value Set URL with fragment '#' character: " + urlValue, urlValue);
		}
		
		String codeSystemUrl = null;
		String version = null;
		String query = "";
		if (FhirModelHelpers.isSnomedImplicitValueSetUrl(urlValue)) {
			codeSystemUrl = FhirModelHelpers.SNOMED_BASE_URI_STRING;
			// extract the non-query part from the URL value
			version = urlValue.split("\\?")[0];
			if (urlValue.contains("?")) {
				query = urlValue.split("\\?")[1];
			}
			
			// if this is the SNOMED CT base URI string then append the core module to represent the International Edition
			if (FhirModelHelpers.SNOMED_BASE_URI_STRING.equals(version)) {
				version = version.concat("/900000000000207008");
			}
		} else if (FhirModelHelpers.isGenericImplicitValueSetUrl(urlValue)) {
			codeSystemUrl = FhirModelHelpers.toGenericCodeSystemUrl(urlValue);
			// version cannot be extracted from the URL (TODO use another parameter, force-system-version or something else)
		} else {
			throw new BadRequestException("Unsupported implicit Value Set URL " + urlValue, urlValue);
		}
		
		
		// try to lookup the CodeSystem using the baseUrl and version (to get the proper edition)
		CodeSystem codeSystem = FhirRequests.codeSystems().prepareSearch()
			.one()
			.filterByUrl(codeSystemUrl)
			.filterByVersion(version)
			.setElements(FhirCodeSystemOperationRequest.MINIMAL_CODESYSTEM_FIELD_SELECTION, false)
			.buildAsync()
			.execute(context)
			.getEntry().stream().findFirst()
			.map(Bundle.BundleEntryComponent.class::cast)
			.map(Bundle.BundleEntryComponent::getResource)
			.map(CodeSystem.class::cast)
			.orElse(null);
		
		// if no CodeSystem stored to use as Value Set source, return bad request response
		if (codeSystem == null) {
			throw new BadRequestException("Supported implicit Value Set URL but no underlying CodeSystem is available at " + codeSystemUrl, urlValue);
		}
		
		// return the content of the CodeSystem as Value Set
		String id = Hashing.goodFastHash(8).hashString(urlValue, StandardCharsets.UTF_8).toString();
		ValueSet valueSet = (ValueSet) new ValueSet()
			.setUrl(urlValue)
			// according to https://terminology.hl7.org/SNOMEDCT.html#snomed-ct-implicit-value-sets publication status is always ACTIVE
			.setStatus(PublicationStatus.ACTIVE)
			.setId(id);
		
		// since an implicit ValueSet does not have an internal resource representation, use the CodeSystem's fragment instead
		valueSet.setUserData(R5ObjectFields.MetadataResource.UserData.INTERNAL_RESOURCE, FhirModelHelpers.getResourceFragment(codeSystem));
		// also store the explicit version requested
		valueSet.setUserData(R5ObjectFields.ValueSet.UserData.CODE_SYSTEM_VERSION, version);
		
		// configure query based on fhir_vs query parameter and also build the compose declaration for this implicit Value Set
		return configureImplicitValueSet(valueSet, codeSystem, urlValue, codeSystemUrl, version, query);
	}
	
	private ValueSet configureImplicitValueSet(
		ValueSet valueSet, 
		CodeSystem codeSystem, 
		String urlValue,
		String codeSystemUrl,
		String version, 
		String query
	) {
		if (FhirModelHelpers.isSnomedImplicitValueSetUrl(urlValue)) {
			return handleSctImplicitValueSetUrl(valueSet, codeSystem, urlValue, version, query);
		} else if (FhirModelHelpers.isLoincImplicitValueSetUrl(urlValue)) {
			return handleLoincImplicitValueSetUrl(valueSet, codeSystem, urlValue, codeSystemUrl, version, query);
		} else {
			return handleGenericImplicitValueSetUrl(valueSet, codeSystem, urlValue, codeSystemUrl, version, query);
		}
	}

	private ValueSet handleSctImplicitValueSetUrl(
		ValueSet valueSet, 
		CodeSystem codeSystem, 
		String urlValue,
		// String codeSystemUrl is not needed for SCT implicit Value Sets
		String version,
		String query
	) {
		// This is a SNOMED CT implicit Value Set URL which supports multiple query types based on the "fhir_vs" query parameter
		if (Strings.isNullOrEmpty(query) || "fhir_vs".equals(query)) {
			return sctAllConcepts(valueSet, codeSystem, version);
		} 
		
		if (!query.startsWith("fhir_vs=")) {
			// No support for other query parameters, return bad request response
			throw new BadRequestException("Unsupported implicit Value Set URL query type: " + urlValue, urlValue);
		}
			
		// Get the value of the "fhir_vs" query parameter and check which type of filter is requested
		final String fhirVsValue = query.replace("fhir_vs=", "");
		
		if (fhirVsValue.startsWith("ecl/")) {
			// This contains an ECL expression, use it to configure the Value Set and its compose statement
			String ecl = fhirVsValue.replace("ecl/", "");
					
			// Make sure we decode the ECL before using it
			try {
				ecl = URLDecoder.decode(ecl, StandardCharsets.UTF_8.toString());
			} catch (UnsupportedEncodingException e) {
				throw new BadRequestException("Failed to decode ECL expression: " + e.getMessage());
			}
					
			return sctEclExpression(valueSet, codeSystem, version, ecl);
		} 
		
		if (fhirVsValue.startsWith("isa/")) {
			// Configure the Value Set for IS A filter, extract the parent concept from the query parameter value
			final String parentId = fhirVsValue.replace("isa/", "");
			return sctDescendantsOf(valueSet, codeSystem, version, parentId);
		} 
		
		if (fhirVsValue.startsWith("refset/")) {
			// Configure the Value Set for reference set membership filter, extract the refSetId from the query parameter value
			final String refsetId = fhirVsValue.replace("refset/", "");
			
			if (Strings.isNullOrEmpty(refsetId)) {
				// TODO: support refset identifier concept search
				throw new BadRequestException("Reference set identifier is missing in the query parameter value: " + urlValue, urlValue);
			}
			
			return sctMembersOfRefSet(valueSet, codeSystem, version, refsetId);
		}
					
		// We have ran out of supported query types, return bad request response for unsupported query parameter value
		// TODO: check against declared filter values in CodeSystem
		throw new BadRequestException("Unsupported implicit SNOMED CT Value Set URL type: " + urlValue, urlValue);
	}

	private ValueSet sctAllConcepts(ValueSet valueSet, CodeSystem codeSystem, String version) {
		valueSet
			.setName(String.format("%s concepts", codeSystem.getName()))
			.setDescription("All SNOMED CT concepts");
		
		valueSet.getCompose()
			.addInclude()
				.setSystem(FhirModelHelpers.SNOMED_BASE_URI_STRING)
				.setVersion(version);
			
		return valueSet;
	}

	private ValueSet sctEclExpression(ValueSet valueSet, CodeSystem codeSystem, String version, String ecl) {
		valueSet
			.setName(String.format("%s concepts matching %s", codeSystem.getName(), ecl))
			.setDescription(String.format("All SNOMED CT concepts that match the expression constraint %s", ecl));
				
		valueSet.getCompose()
			.addInclude()
				.setSystem(FhirModelHelpers.SNOMED_BASE_URI_STRING)
				.setVersion(version)
				.addFilter()
				.setProperty("constraint")
				.setOp(FilterOperator.EQUAL)
				.setValue(ecl);

		return valueSet;
	}

	private ValueSet sctDescendantsOf(ValueSet valueSet, CodeSystem codeSystem, String version, String parentId) {
		valueSet
			.setName(String.format("%s descendants of concept %s", codeSystem.getName(), parentId))
			.setDescription(String.format("Descendants of SNOMED CT concept %s", parentId));
		
		valueSet.getCompose()
			.addInclude()
				.setSystem(FhirModelHelpers.SNOMED_BASE_URI_STRING)
				.setVersion(version)
				.addFilter()
				.setProperty("constraint")
				.setOp(FilterOperator.ISA)
				.setValue(parentId);
		
		return valueSet;
	}
	
	private ValueSet sctMembersOfRefSet(ValueSet valueSet, CodeSystem codeSystem, String version, String refsetId) {
		valueSet
			.setName(String.format("%s members of reference set %s", codeSystem.getName(), refsetId))
			.setDescription(String.format("All SNOMED CT concepts in the reference set %s", refsetId));
		
		valueSet.getCompose()
			.addInclude()
				.setSystem(FhirModelHelpers.SNOMED_BASE_URI_STRING)
				.setVersion(version)
				.addFilter()
				.setProperty("concept")
				.setOp(FilterOperator.IN)
				.setValue(refsetId);
		
		return valueSet;
	}

	private ValueSet handleLoincImplicitValueSetUrl(
		ValueSet valueSet, 
		CodeSystem codeSystem, 
		String urlValue,
		String codeSystemUrl, 
		String version, 
		String query
	) {
		// This is a LOINC implicit Value Set URL which supports the "/vs" and "/vs/[part]" URL patterns
		if (!Strings.isNullOrEmpty(query)) {
			throw new BadRequestException("Unsupported implicit Value Set URL with query parameters: " + urlValue, urlValue);
		}
		
		final String code = FhirModelHelpers.getLoincImplicitValueSetCode(urlValue);
		if (code == null) {
			// The "all concepts" implicit Value Set definition will work for LOINC as well
			return genericAllConcepts(valueSet, codeSystem, codeSystemUrl, version);
		} else if (code.startsWith("LP-")) {
			// This is a LOINC implicit Value Set URL for a specific part, extract the part number and configure the Value Set accordingly
			return loincPartDescendants(valueSet, codeSystem, codeSystemUrl, version, code);
		}
		
		// Return bad request response for unsupported query parameter value
		throw new BadRequestException("Unsupported implicit LOINC Value Set URL type: " + urlValue, urlValue);
	}

	private ValueSet loincPartDescendants(
		ValueSet valueSet, 
		CodeSystem codeSystem, 
		String codeSystemUrl, 
		String version,
		String code
	) {
		valueSet
			.setName(String.format("%s concepts with part %s", codeSystem.getName(), code))
			.setDescription(String.format("Descendants of LOINC part %s", code));
		
		valueSet.getCompose()
			.addInclude()
				.setSystem(codeSystemUrl)
				.setVersion(version)
				.addFilter()
				.setProperty("constraint")
				.setOp(FilterOperator.ISA)
				.setValue(code);
		
		return valueSet;
	}

	private ValueSet handleGenericImplicitValueSetUrl(
		ValueSet valueSet, 
		CodeSystem codeSystem, 
		String urlValue, 
		String codeSystemUrl,
		String version, 
		String query
	) {
		// This is a generic implicit Value Set URL which only supports the "fhir_vs" query type for now
		if (!"fhir_vs".equals(query)) {
			throw new BadRequestException("Unsupported implicit Value Set URL query type: " + urlValue, urlValue);
		}
		
		return genericAllConcepts(valueSet, codeSystem, codeSystemUrl, version);
	}

	private ValueSet genericAllConcepts(
		ValueSet valueSet, 
		CodeSystem codeSystem, 
		String codeSystemUrl, 
		String version
	) {
		valueSet
			.setName(String.format("%s concepts", codeSystem.getName()))
			.setDescription(String.format("All concepts from %s code system", codeSystem.getName()));
			
		valueSet.getCompose()
			.addInclude()
				.setSystem(codeSystemUrl)
				.setVersion(version);
		
		return valueSet;
	}	
	
}
