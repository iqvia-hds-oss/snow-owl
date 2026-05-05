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
import com.b2international.snowowl.core.ResourceFragment;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.request.ResourceRequests;
import com.b2international.snowowl.core.terminology.TerminologyRegistry;
import com.b2international.snowowl.core.version.Version;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

/**
 * @since 9.4.0
 */
public class FhirModelHelpers {

	public static final String OID_PREFIX = "urn:oid:";
	
	public static final String SNOMED_BASE_URI_STRING = "http://snomed.info/sct";

	public static final String VERSION_SEGMENT = "/version/";

	private static final String GENERIC_IMPLICIT_VALUESET_SUFFIX = "/vs";
	
	/**
	 * @param resource
	 * @return the {@link ResourceFragment} associated with the given FHIR R5 resource in its user data under the key R5ObjectFields.MetadataResource.UserData.INTERNAL_RESOURCE.
	 */
	public static ResourceFragment getResourceFragment(Resource resource) {
		return (ResourceFragment) resource.getUserData(R5ObjectFields.MetadataResource.UserData.INTERNAL_RESOURCE);
	}
	
	public static ResourceURI resourceUriFrom(final Resource resource) {
		final ResourceFragment res = getResourceFragment(resource);
		if (res != null) {
			return res.getResourceURI();
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
		return isBaseSnomedUri(uri) || isEditionSnomedUri(uri);
	}
	
	public static boolean isBaseSnomedUri(String uri) {
		return SNOMED_BASE_URI_STRING.equals(uri);
	}
	
	public static boolean isEditionSnomedUri(String uri) {
		return uri != null && uri.startsWith(SNOMED_BASE_URI_STRING + "/");
	}
	
	public static boolean isRegularVersionedUri(String uri) {
		// Check that the version segment is present and not at the start or end of the URI
		return uri != null 
			&& !uri.startsWith(VERSION_SEGMENT) 
			&& !uri.endsWith(VERSION_SEGMENT)
			&& uri.contains(VERSION_SEGMENT);
	}
	
	public static String addRegularVersionSuffix(String uri) {
		// If the URI already contains a version segment, don't add it again
		if (isRegularVersionedUri(uri)) {
			return uri;
		}

		// Remove trailing slash if present
		if (uri.endsWith("/")) { 
			uri = uri.substring(0, uri.length() - 1); 
		}
		
		// Append version segment to the URI
		return uri + VERSION_SEGMENT;
	}
	
	public static String getRegularUrlBase(String uri) {
		// If the URI does not contain a version segment, return it as is
		if (!isRegularVersionedUri(uri)) {
			return uri;
		}

		// Otherwise return the portion of the URI before the "/version/" segment
		return uri.substring(0, uri.indexOf(VERSION_SEGMENT));
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
		
		if (isRegularVersionedUri(fhirUrl)) {
			// For regular versioned URIs we can use the portion before the "/version/" segment as the system
			systemConsumer.accept(getRegularUrlBase(fhirUrl));
		} else {
			// For other URIs we can use the resulting URL as the system
			systemConsumer.accept(fhirUrl);
		}
			
		// Instead of checking the resulting URL for version information, use the original ResourceURI's path as the version
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
	 * Returns <code>true</code> if the current URL is an implicit Value Set URL. The URL must start with http:// and contain either a '?fhir_vs' query part  for SNOMED or a /vs path segment for any other terminology
	 * @param url
	 * @return
	 */
	public static boolean isImplicitValueSetURL(String url) {
		return isSnomedImplicitValueSetUrl(url) || isGenericImplicitValueSetUrl(url);
	}
	
	// for snomed we need a proper SNOMED URI + fhir_vs after the ? query start 
	public static boolean isSnomedImplicitValueSetUrl(String url) {
		return url != null && url.startsWith(SNOMED_BASE_URI_STRING) && url.substring(url.indexOf("?") + 1, url.indexOf("?") + "fhir_vs".length() + 1).equals("fhir_vs");
	}
	
	// generic means the original CodeSystem URL + an ending /vs suffix to get the implicit ValueSet working
	public static boolean isGenericImplicitValueSetUrl(String url) {
		return url != null && url.startsWith("http://") && url.endsWith(GENERIC_IMPLICIT_VALUESET_SUFFIX);
	}
	
	public static String toGenericCodeSystemUrl(String genericImplicitValueSetUrl) {
		Preconditions.checkArgument(isGenericImplicitValueSetUrl(genericImplicitValueSetUrl), "'url' is not a generic implicit Value Set URL");
		return genericImplicitValueSetUrl.replace(GENERIC_IMPLICIT_VALUESET_SUFFIX, ""); 
	}

}
