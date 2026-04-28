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
package com.b2international.snowowl.fhir.core;

import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.DateTimeType;
import org.hl7.fhir.r5.model.InstantType;
import org.hl7.fhir.r5.model.Resource;

import com.b2international.commons.CompareUtils;
import com.b2international.commons.StringUtils;
import com.b2international.commons.exceptions.NotFoundException;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.TerminologyResource;
import com.b2international.snowowl.core.request.ResourceRequests;
import com.b2international.snowowl.core.terminology.TerminologyRegistry;
import com.b2international.snowowl.core.version.Version;
import com.google.common.base.CharMatcher;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.Hashing;

/**
 * @since 9.4.0
 */
public class FhirModelHelpers {

	public static final String OID_PREFIX = "urn:oid:";
	
	public static final String SNOMED_BASE_URI_STRING = "http://snomed.info/sct";

	private static final CharMatcher HEX_MATCHER = CharMatcher.inRange('0', '9')
			.or(CharMatcher.inRange('a', 'f'))
			.or(CharMatcher.inRange('A', 'F'))
			.precomputed();

	public static ResourceURI resourceUriFrom(final Resource resource) {
		final ResourceURI resourceUri = (ResourceURI) resource.getUserData(TerminologyResource.Fields.RESOURCE_URI);
		if (resourceUri != null) {
			return resourceUri;
		} else {
			return ResourceURI.of(resource.getResourceType().name().toLowerCase() + "s", resource.getId());
		}
	}
	
	public static DateTimeType toDateTimeElement(Long date) {
		return toDateTimeElement(date == null ? null : new Date(date));
	}
	
	public static DateTimeType toDateTimeElement(Date date) {
		if (date == null) {
			return null;
		} else {
			var dateElement = new DateTimeType(date);
			dateElement.setTimeZoneZulu(true);
			return dateElement;
		}
	}
	
	public static InstantType toInstantElement(Long date) {
		return toInstantElement(date == null ? null : new Date(date));
	}
	
	public static InstantType toInstantElement(Date date) {
		if (date == null) {
			return null;
		} else {
			var instantElement = new InstantType(date);
			instantElement.setTimeZoneZulu(true);
			return instantElement;
		}
	}
	
	public static boolean isOid(CanonicalType system) {
		return system != null & isOid(system.getValue());
	}
	
	public static boolean isOid(String system) {
		return system != null && system.startsWith(OID_PREFIX);
	}
	
	public static boolean isSnomedUri(String uri) {
		return uri != null && (uri.equals(SNOMED_BASE_URI_STRING) || uri.startsWith(SNOMED_BASE_URI_STRING + "/"));
	}

	public static String getSystemWithoutOidPrefix(CanonicalType system) {
		return getSystemWithoutOidPrefix(system == null ? null : system.getValue());
	}
	
	public static String getSystemWithoutOidPrefix(String system) {
		if (CompareUtils.isEmpty(system)) {
			return "";
		}
		
		if (isOid(system)) {
			return system.substring(OID_PREFIX.length());
		}
		
		return system;
	}
	
	/**
	 * Provides a fallback URN for resource URIs that cannot be resolved to an
	 * actual resource. This suggests an internal inconsistency in the data, but
	 * allows the system to continue building a FHIR response without throwing an
	 * exception.
	 * <p>
	 * Consumers will most likely not be able to do anything meaningful with the
	 * returned value, but it is still useful for debugging and error reporting
	 * purposes.
	 * 
	 * @param resourceUri
	 * @return
	 */
	private static String getUrnForUnresolvedUri(final ResourceURI resourceUri) {
		final String resourceType = resourceUri.getResourceType()
			.toLowerCase(Locale.ENGLISH);
		final String resourceUriWithoutType = resourceUri.getResourceId()
			.toLowerCase(Locale.ENGLISH);
		
		return String.format("urn:snowowl:%s:%s", resourceType, resourceUriWithoutType);
	}

	private static String getUrlForResourceUri(final ServiceProvider context, final ResourceURI resourceUri) {
		if (TerminologyRegistry.UNSPECIFIED.equals(resourceUri.getResourceId())) {
			return getUrnForUnresolvedUri(resourceUri);
		}
		
		final Optional<Version> versionWithURI;
		
		if (!StringUtils.isEmpty(resourceUri.getPath())) {
			versionWithURI = ResourceRequests.prepareSearchVersion()
				.one()
				.filterByResource(resourceUri.withoutPath())
				.filterByVersionId(resourceUri.getPath())
				.buildAsync()
				.execute(context)
				.first();
		} else {
			versionWithURI = Optional.empty();
		}
			
		if (versionWithURI.isPresent()) {
			return versionWithURI.get().getUrl();
		}
			
		try {
			
			return ResourceRequests.prepareGet(resourceUri)
				.buildAsync()
				.execute(context)
				.getUrl();
			
		} catch (final NotFoundException e) {
			return getUrnForUnresolvedUri(resourceUri);
		}
	}
	
	public static Function<ResourceURI, String> createResourceUriToUrlFunction(final ServiceProvider context) {
		final CacheLoader<ResourceURI, String> loader = CacheLoader.from(resourceUri -> getUrlForResourceUri(context, resourceUri));
		final LoadingCache<ResourceURI, String> urlByResourceId = CacheBuilder.newBuilder().build(loader);
		return urlByResourceId;
	}
	
	public static void setSystemAndVersion(
		final ResourceURI resourceUri, 
		final Function<ResourceURI, String> mapperFunction, 
		final Consumer<String> systemConsumer, 
		final Consumer<String> versionConsumer
	) {
		final String fhirUrl = mapperFunction.apply(resourceUri);
		
		if (isSnomedUri(fhirUrl)) {
			// For SNOMED CT we need to use the base URL as the system and the original URL as the version
			systemConsumer.accept(FhirModelHelpers.SNOMED_BASE_URI_STRING);
			versionConsumer.accept(fhirUrl);
			return;
		}
			
		// In other cases we can use the resulting URL as the system...
		systemConsumer.accept(fhirUrl);
		
		// ...and extract the path portion as the version
		if (!resourceUri.isHead() && !resourceUri.isNext()) {
			versionConsumer.accept(resourceUri.getPath());
		}
	}

	public static CanonicalType toCanonicalType(
		final ResourceURI resourceUri, 
		final Function<ResourceURI, String> mapperFunction
	) {
		final CanonicalType canonicalType = new CanonicalType();
		setSystemAndVersion(resourceUri, mapperFunction, canonicalType::setValue, canonicalType::addVersion);
		return canonicalType;
	}

	/**
	 * Converts a native resource ID to a FHIR-compatible resource ID.
	 * <p>
	 * Rules are evaluated in order in a left-to-right pass and only the first
	 * matching rule is applied, no stacking of multiple rules occurs:
	 * <ol>
	 * <li>Each occurrence of {@code "/"} is replaced with {@code "--"}</li>
	 * <li>Each occurrence of {@code "--"} is replaced with {@code ".h"}</li>
	 * <li>Each occurrence of {@code "_"} is replaced with {@code ".u"}</li>
	 * <li>If the resulting ID would be longer than 64 characters, return the 
	 * first 46 characters of the result, then {@code ".c"}, then 16 hex digits 
	 * of a SipHash-2-4 tag of the original input to ensure uniqueness while 
	 * retaining a recoverable prefix of the original ID
	 * </ol>
	 * If none of the rules match, the input is returned unchanged.
	 *
	 * @param nativeId the native resource ID to convert
	 * @return the FHIR-compatible resource ID
	 */
	public static String toFhirResourceId(final String nativeId) {
		if (nativeId == null) {
			return null;
		}
		
		final StringBuilder sb = new StringBuilder(nativeId.length());
		int i = 0;
		
		while (i < nativeId.length()) {
			if (nativeId.charAt(i) == '/') {
				sb.append("--");
				i++;
			} else if (nativeId.startsWith("--", i)) {
				sb.append(".h");
				i += 2;
			} else if (nativeId.charAt(i) == '_') {
				sb.append(".u");
				i++;
			} else {
				sb.append(nativeId.charAt(i));
				i++;
			}
		}
		
		if (sb.length() > 64) {
			final String hash = Hashing.sipHash24()
				.hashUnencodedChars(nativeId)
				.toString();
			
			// Carve out space for the hash trailer and append it
			sb.setLength((64 - 2 - 16));
			sb.append(".c");
			sb.append(hash);
		}
		
		return sb.toString();
	}

	/**
	 * Reverses a FHIR resource ID previously produced by {@link #toFhirResourceId(String)}, 
	 * recovering the original native resource ID or its prefix.
	 * <p>
	 * The following substitutions are applied in a single left-to-right pass:
	 * <ol>
	 * <li>Each occurrence of {@code "--"} is replaced with {@code "/"}</li>
	 * <li>Each occurrence of {@code ".h"} is replaced with {@code "--"}</li>
	 * <li>Each occurrence of {@code ".u"} is replaced with {@code "_"}</li>
	 * </ol>
	 * When rule 4 (length truncation with hash) was applied during encoding, the
	 * original string cannot be fully recovered. In that case only the prefix that
	 * was retained during encoding is returned (after reverse-substitution),
	 * without the hash trailer.
	 * <p>The caller can detect this situation by checking whether {@code fhirId} 
	 * matches the pattern {@code <14 chars>.c<12 hex digits>} before calling this method.
	 *
	 * @param fhirId the FHIR resource ID to reverse
	 * @return the original native resource ID, or its recoverable prefix
	 */
	public static String fromFhirResourceId(final String fhirId) {
		if (fhirId == null) {
			return null;
		}

		if (isTruncatedFhirResourceId(fhirId)) {
			// Only the first 46 characters are recoverable
			return reverseSubstitutes(fhirId.substring(0, 46));
		} else {
			return reverseSubstitutes(fhirId);
		}
	}

	/**
	 * Checks whether the given FHIR resource ID matches the pattern of a truncated
	 * ID produced by {@link #toFhirResourceId(String)} when the input was too long.
	 * 
	 * @param fhirId the FHIR resource ID to check
	 * @return {@code true} if the ID matches the pattern of a truncated ID, 
	 * {@code false} otherwise
	 */
	public static boolean isTruncatedFhirResourceId(final String fhirId) {
		return fhirId.length() == 64
			&& fhirId.charAt(46) == '.'
			&& fhirId.charAt(47) == 'c'
			&& HEX_MATCHER.matchesAllOf(fhirId.substring(48));
	}

	private static String reverseSubstitutes(final String fhirId) {
		final StringBuilder sb = new StringBuilder(fhirId.length());
		int i = 0;
		
		while (i < fhirId.length()) {
			if (fhirId.startsWith("--", i)) {
				sb.append('/');
				i += 2;
			} else if (fhirId.startsWith(".h", i)) {
				sb.append("--");
				i += 2;
			} else if (fhirId.startsWith(".u", i)) {
				sb.append('_');
				i += 2;
			} else {
				sb.append(fhirId.charAt(i));
				i++;
			}
		}
		
		return sb.toString();
	}
}
