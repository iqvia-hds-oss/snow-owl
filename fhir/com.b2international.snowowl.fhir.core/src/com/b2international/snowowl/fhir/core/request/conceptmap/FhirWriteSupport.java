/*
 * Copyright 2022-2026 B2i Healthcare, https://b2ihealthcare.com
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

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.hl7.fhir.r5.model.Meta;
import org.hl7.fhir.r5.model.MetadataResource;

import com.b2international.commons.StringUtils;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.request.ResourceRequests;
import com.b2international.snowowl.core.version.Version;
import com.google.common.base.CharMatcher;

/**
 * @since 8.7.1
 */
public interface FhirWriteSupport {

	/**
	 * Pattern to match any character that is permitted in a resource ID without having to be encoded.
	 * <p>
	 * We have multiple conflicting requirements for resource IDs:
	 * <ul>
	 * <li><b>Branch names</b>: allowed characters are <code>[a-zA-Z0-9._~-]</code>
	 * <li><b>Native resource IDs</b>: allowed characters are <code>[A-Za-z0-9-_]</code>
	 * <li><b>Versioned resource IDs</b>: allowed characters are <code>[A-Za-z0-9-_/]</code> (the '/' is a separator)
	 * <li><b>FHIR identifiers</b>: allowed characters are <code>[A-Za-z0-9-.]</code>
	 * </ul>
	 */
	CharMatcher ALLOWED_ID_MATCHER = CharMatcher.inRange('A', 'Z')
		.or(CharMatcher.inRange('a', 'z'))
		.or(CharMatcher.inRange('0', '9'))
		.or(CharMatcher.anyOf("-_"));
	
	/**
	 * Replacement character for any input that is not allowed in a resource ID.
	 * This character is safe to use in all contexts (branch names, native resource
	 * IDs, versioned resource IDs and FHIR identifiers).
	 */
	char SAFE_ID_CHARACTER = '_';

	default LocalDate getEffectiveDate(final MetadataResource resource, final LocalDate defaultEffectiveDate) {
		return Optional.ofNullable(resource.getDate())
			.or(() -> Optional.ofNullable(resource.getMeta()).map(Meta::getLastUpdated))
			// Time zone information is no longer available as we mapped it to a java.util.Date instance; treat value as UTC
			.map(date -> date.toInstant()
				.atOffset(ZoneOffset.UTC)
				.toLocalDate())
			.or(() -> Optional.ofNullable(defaultEffectiveDate))
			.orElse(LocalDate.now(ZoneOffset.UTC));
	}
	
	default boolean isExistingVersion(
		final ServiceProvider context,
		final ResourceURI resourceUri,
		final String versionId
	) {
		return ResourceRequests.prepareSearchVersion()
			.setLimit(0)
			.filterByResource(resourceUri)
			.filterByVersionId(versionId)
			.buildAsync()
			.execute(context)
			.getTotal() > 0;
	}
	
	default Optional<LocalDate> getLatestEffectiveTime(
		final ServiceProvider context,
		final ResourceURI resourceUri
	) {
		return ResourceRequests.prepareSearchVersion()
			.one()
			.filterByResource(resourceUri)
			.sortBy("effectiveTime:desc")
			.buildAsync()
			.execute(context)
			.first()
			.map(Version::getEffectiveTime);
	}

	static String safeId(final String id) {
		return safeId(id, false);
	}
	
	static String safeId(final String id, final boolean allowForwardSlash) {
		if (StringUtils.isEmpty(id)) {
			return id;
		}
		
		final StringBuilder safeIdBuilder = new StringBuilder();
		
		id.chars().forEach(c -> {
			if (ALLOWED_ID_MATCHER.matches((char) c) || (allowForwardSlash && c == '/')) {
				// Allowed characters are safe to use as-is (we are making an exception for the forward slash as well if enabled)
				safeIdBuilder.append((char) c);
			} else if (c == '.') {
				// The dot character appears in FHIR identifiers frequently so we will replace it with a single underscore
				safeIdBuilder.append(SAFE_ID_CHARACTER);
			} else {
				/*
				 * Any other disallowed character will be replaced with a safe character
				 * followed by the Unicode code point of the character, represented as decimal
				 * digits
				 */
				safeIdBuilder.append(SAFE_ID_CHARACTER).append('u').append(c);
			}
		});
		
		return safeIdBuilder.toString();
	}

}
