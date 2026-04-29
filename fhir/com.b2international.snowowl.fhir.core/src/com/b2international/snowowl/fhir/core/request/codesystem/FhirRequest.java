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

import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeType;

import com.b2international.commons.StringUtils;
import com.b2international.commons.exceptions.NotFoundException;
import com.b2international.commons.http.AcceptLanguageHeader;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.events.Request;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.Summary;
import com.b2international.snowowl.fhir.core.exceptions.BadRequestException;
import com.b2international.snowowl.fhir.core.request.FhirRequests;
import com.google.common.base.Splitter;

/**
 * Abstract base class for FHIR CodeSystem operations that require fetching a CodeSystem resource first.
 * 
 * @see FhirCodeSystemLookupRequest
 * @see FhirCodeSystemSubsumesRequest
 * @see FhirCodeSystemValidateCodeRequest
 * 
 * @since 8.0
 * @param <R> the response type of the request
 */
public abstract class FhirRequest<R> implements Request<ServiceProvider, R> {

	private static final long serialVersionUID = 1L;
	
	private final String system;
	
	private final String version;

	public FhirRequest(final String system, final String version) {
		this.system = system;
		this.version = version;
	}
	
	private Optional<CodeSystem> fetchCodeSystem(
		final ServiceProvider context, 
		final Consumer<FhirCodeSystemSearchRequestBuilder> searchConfigurer
	) {
		final FhirCodeSystemSearchRequestBuilder requestBuilder = FhirRequests.codeSystems()
			.prepareSearch()
			.one()
			.setSummary(configureSummary());
		
		searchConfigurer.accept(requestBuilder);
		
		return requestBuilder.buildAsync()
			.getRequest()
			.execute(context)
			.getEntry()
			.stream()
			.findFirst()
			.map(Bundle.BundleEntryComponent::getResource)
			.map(CodeSystem.class::cast);
	}
	
	private Optional<CodeSystem> fetchDefaultSctCodeSystem(final ServiceProvider context) {
		if (!FhirModelHelpers.isBaseSnomedUri(system) || !StringUtils.isEmpty(version)) {
			return Optional.empty();
		}
		
		/*
		 * TODO: See SO-6575: we should not hardcode the core module ID here, but
		 * instead determine it dynamically. Also it should point to the latest released
		 * version of the code system.
		 */
		return fetchCodeSystem(context, rb -> rb
			.filterByUrl(system)
			.filterByVersion(system + "/900000000000207008"));
	}
	
	private Optional<CodeSystem> fetchByUrlAndVersion(final ServiceProvider context) {
		// Clean mapping from "system" to "url" and "version" to "version"
		return fetchCodeSystem(context, rb -> rb
			.filterByUrl(system)
			.filterByVersion(version));
	}

	private Optional<CodeSystem> fetchByIdAndVersion(final ServiceProvider context) {
		// Using "name" as the "id" filter matches native resource IDs and URLs, see FhirResourceSearchRequest#addFhirIdFilter
		return fetchCodeSystem(context, rb -> rb
			.filterByName(system)
			.filterByVersion(version));
	}

	@Override
	public final R execute(final ServiceProvider context) {
		/*
		 * Attempt multiple ways of resolving the CodeSystem resource, in the following order:
		 * 
		 * 1. If the system URI is a base SNOMED CT URI and no version is specified, attempt to fetch the default SNOMED CT edition
		 * 2. Search for a CodeSystem with the specified URL and version
		 * 3. Search for a CodeSystem with the specified ID and version
		 */
		final CodeSystem codeSystem = Optional.<CodeSystem>empty()
			.or(() -> fetchDefaultSctCodeSystem(context))
			.or(() -> fetchByUrlAndVersion(context))
			.or(() -> fetchByIdAndVersion(context))
			.orElseThrow(() -> new NotFoundException("CodeSystem", system));
		
		return doExecute(context, codeSystem);
	}

	protected String configureSummary() {
		return Summary.TRUE;
	}

	/**
	 * Converts a BCP-47 language tag (where the private use extension portions
	 * are split into at most 8 characters, separated by dashes) into the "compact"
	 * non-standard representation used in Snow Owl's internal API.
	 * 
	 * @param localeAsCode
	 * @return
	 */
	public static String compactLocale(final CodeType localeAsCode) {
		final String locale = (localeAsCode != null) ? localeAsCode.getCode() : null;
		if (StringUtils.isEmpty(locale)) {
			return AcceptLanguageHeader.DEFAULT_ACCEPT_LANGUAGE_HEADER;
		}
		
		return compactLocale(locale);
	}

	public static String compactLocale(final String locale) {
		// Parse the input in accordance with BCP-47 grammar (it should be valid)
		final Locale.Builder builder = new Locale.Builder();
		try {
			builder.setLanguageTag(locale);
		} catch (final IllformedLocaleException ex) {
			throw new BadRequestException(ex.getMessage());
		}
		
		// Remove hyphen separators from the private use extension
		final Locale parsedLocale = builder.build();
		final String privateUseExtension = parsedLocale.getExtension(Locale.PRIVATE_USE_EXTENSION);
		if (StringUtils.isEmpty(privateUseExtension)) {
			return locale;
		}
		
		/*
		 * Remove dashes and replace the old private use extension with the compact one
		 * (using "-x-" as the anchoring prefix -- it should not appear elsewhere in the
		 * language tag)
		 */
		final String separatorsRemovedExtension = privateUseExtension.replace("-", "");
		return locale.replace("-x-" + privateUseExtension, "-x-" + separatorsRemovedExtension);
	}
	
	/**
	 * Converts a "compact" locale representation into a BCP-47 language tag by
	 * splitting the private use extension portions into at most 8 characters,
	 * separating each section with a dash.
	 * 
	 * @param locale
	 * @return
	 */
	public static String expandLocale(final String locale) {
		if (StringUtils.isEmpty(locale)) {
			return null;
		}
		
		/*
		 * XXX: Assuming locales returned by Snow Owl are in the form of eg. "en-US" or
		 * "en-x-1234567890123456789" (We can not use Java's built-in parser as at this
		 * point the extension breaks length limits and the language tag is invalid)
		 * 
		 * See FhirLocaleTest#expandSplitPrivateUseExtension for an example.
		 */
		final int privateUseIdx = locale.lastIndexOf("-x-");
		if (privateUseIdx < 0 || privateUseIdx + 3 >= locale.length()) {
			return locale;
		}
		
		final String separatorsRemovedExtension = locale.substring(privateUseIdx + 3);
		final String privateUseExtension = Splitter.fixedLength(8) // split private use portion into 8 character segments
			.splitToStream(separatorsRemovedExtension)
			.collect(Collectors.joining("-")); // combine again with hyphens
		
		// Replace the old private use extension
		return locale.replace("-x-" + separatorsRemovedExtension, "-x-" + privateUseExtension);
	}

	protected abstract R doExecute(ServiceProvider context, CodeSystem codeSystem);

}
