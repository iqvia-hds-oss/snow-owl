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
import java.util.regex.Pattern;

import org.hl7.fhir.r5.model.Meta;
import org.hl7.fhir.r5.model.MetadataResource;

import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.id.IDs;
import com.b2international.snowowl.core.request.ResourceRequests;
import com.b2international.snowowl.core.version.Version;

/**
 * @since 8.7.1
 */
public interface FhirWriteSupport {

	static final Pattern DISALLOWED_ID_PATTERN = Pattern.compile(String.format("[^%s]", IDs.BASE64_CHARSET));
	static final String SAFE_ID_CHARACTER = "_";

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
	
	static String base64SafeId(final String id) {
		String replacedId = DISALLOWED_ID_PATTERN.matcher(id).replaceAll(result -> {
			if (result.group().equals(".")) {
				return SAFE_ID_CHARACTER;
			} else {
				return SAFE_ID_CHARACTER + "u" + (int) result.group().charAt(0);
			}
		});
	
		if (replacedId.startsWith(SAFE_ID_CHARACTER)) {
			replacedId = "u95" + replacedId.substring(1);
		}
	
		return replacedId.replace("__", "_u95");
	}

}
