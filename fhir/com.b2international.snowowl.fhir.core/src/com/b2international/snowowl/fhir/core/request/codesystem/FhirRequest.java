/*
 * Copyright 2021-2024 B2i Healthcare, https://b2ihealthcare.com
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
import java.util.stream.Collectors;

import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeType;

import com.b2international.commons.StringUtils;
import com.b2international.commons.exceptions.NotFoundException;
import com.b2international.commons.http.AcceptLanguageHeader;
import com.b2international.snowowl.core.RepositoryManager;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.events.Request;
import com.b2international.snowowl.core.uri.ResourceURLSchemaSupport;
import com.b2international.snowowl.fhir.core.Summary;
import com.b2international.snowowl.fhir.core.exceptions.BadRequestException;
import com.b2international.snowowl.fhir.core.request.FhirRequests;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;

/**
 * @since 8.0
 * @param <R>
 */
public abstract class FhirRequest<R> implements Request<ServiceProvider, R> {

	private static final long serialVersionUID = 1L;
	
	private final String system;
	
	private final String version;

	public FhirRequest(String system, String version) {
		this.system = system;
		this.version = version;
	}
	
	@Override
	public final R execute(ServiceProvider context) {
		// try as is via the URL + version (optional) config
		CodeSystem codeSystem = fetchCodeSystemByUrlAndVersion(context)
				.or(() -> fetchCodeSystemByIdAndVersion(context))
				.or(() -> {
					// perform the third step only if there is a version specified
					if (Strings.isNullOrEmpty(version)) {
						return Optional.empty();
					} else {
						return fetchCodeSystemByUrl(context, system)
								// if there is a codesystem with the specified URL then construct a versioned form using its official URL schema from its tooling
								.flatMap((cs) -> fetchCodeSystemByUrl(context, context.service(RepositoryManager.class).get((String) cs.getUserData("toolingId")).service(ResourceURLSchemaSupport.class).withVersion(system, version, null)));
					}
				})
				.orElseThrow(() -> new NotFoundException("CodeSystem", system));
		
		return doExecute(context, codeSystem);
	}

	private Optional<? extends CodeSystem> fetchCodeSystemByIdAndVersion(ServiceProvider context) {
		return FhirRequests
			.codeSystems().prepareSearch()
			.one()
			.filterById(system)
			.filterByVersion(version)
			.setSummary(configureSummary())
			.buildAsync()
			.getRequest()
			.execute(context)
			.getEntry().stream().findFirst()
			.map(Bundle.BundleEntryComponent.class::cast)
			.map(Bundle.BundleEntryComponent::getResource)
			.map(CodeSystem.class::cast);
	}

	private Optional<CodeSystem> fetchCodeSystemByUrlAndVersion(ServiceProvider context) {
		return FhirRequests
				.codeSystems().prepareSearch()
				.one()
				.filterByUrl(system)
				.filterByVersion(version)
				.setSummary(configureSummary())
				.buildAsync()
				.getRequest()
				.execute(context)
				.getEntry().stream().findFirst()
				.map(Bundle.BundleEntryComponent.class::cast)
				.map(Bundle.BundleEntryComponent::getResource)
				.map(CodeSystem.class::cast);
	}
	
	private Optional<CodeSystem> fetchCodeSystemByUrl(ServiceProvider context, String url) {
		return FhirRequests
				.codeSystems().prepareSearch()
				.one()
				.filterByUrl(url)
				.setSummary(configureSummary())
				.buildAsync()
				.getRequest()
				.execute(context)
				.getEntry().stream().findFirst()
				.map(Bundle.BundleEntryComponent.class::cast)
				.map(Bundle.BundleEntryComponent::getResource)
				.map(CodeSystem.class::cast);
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
	public static final String compactLocale(final CodeType localeAsCode) {
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
		} catch (IllformedLocaleException ex) {
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
	public static final String expandLocale(String locale) {
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
