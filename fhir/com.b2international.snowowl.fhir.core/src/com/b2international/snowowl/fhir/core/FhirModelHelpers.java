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
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import com.b2international.snowowl.core.TerminologyResource;
import com.b2international.snowowl.core.codesystem.CodeSystem;
import com.b2international.snowowl.core.internal.ResourceDocument;
import com.b2international.snowowl.core.request.ResourceRequests;
import com.b2international.snowowl.core.terminology.TerminologyRegistry;
import com.b2international.snowowl.core.version.Version;
import com.b2international.snowowl.core.version.VersionDocument;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

/**
 * @since 9.4.0
 */
public class FhirModelHelpers {
	
	public static final String OID_PREFIX = "urn:oid:";
	
	public static final String SNOMED_BASE_URI_STRING = "http://snomed.info/sct";
	public static final String LOINC_BASE_URI_STRING = "http://loinc.org";
	
	public static final String VERSION_SEGMENT = "/version/";

	private static final String GENERIC_IMPLICIT_VALUESET_SUFFIX = "/vs";
	
	// Slash is a non-capturing group, the optional "code" is a named capturing group
	private static final Pattern LOINC_IMPLICIT_VALUESET_PATTERN = Pattern.compile("http://loinc.org/vs(?:/(?<code>[a-zA-Z0-9\\-]+))?");
	
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
	
	public static record SystemAndVersion(String system, String version) {
		// Empty record body
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
	private static SystemAndVersion getSystemAndVersionForUnresolvedUri(final ResourceURI resourceUri) {
		final String resourceType = resourceUri.getResourceType()
			.toLowerCase(Locale.ENGLISH);
		final String resourceUriWithoutType = resourceUri.getResourceId()
			.toLowerCase(Locale.ENGLISH);
		
		return new SystemAndVersion(
			String.format("urn:snowowl:%s:%s", resourceType, resourceUriWithoutType),
			null);
	}

	private static SystemAndVersion getSystemAndVersionForResourceUri(final ServiceProvider context, final ResourceURI resourceUri) {
		if (TerminologyRegistry.UNSPECIFIED.equals(resourceUri.getResourceId())) {
			return getSystemAndVersionForUnresolvedUri(resourceUri);
		}
		
		final Optional<Version> versionForURI;
		
		if (!StringUtils.isEmpty(resourceUri.getPath())) {
			versionForURI = ResourceRequests.prepareSearchVersion()
				.one()
				.filterById(resourceUri.withoutResourceType())
				.setFields(
					VersionDocument.Fields.ID,
					VersionDocument.Fields.URL,
					VersionDocument.Fields.VERSION,
					VersionDocument.Fields.SETTINGS
				)
				.buildAsync()
				.execute(context)
				.first();
		} else {
			versionForURI = Optional.empty();
		}
			
		if (versionForURI.isPresent()) {
			final Version version = versionForURI.get();
			
			final String fhirUrlOverride = version.getFhirUrl();
			if (StringUtils.isEmpty(fhirUrlOverride)) {
				return new SystemAndVersion(version.getUrl(), version.getVersion());
			}
			
			final String fhirVersionProperty = version.getFhirVersionProperty();
			if (ResourceDocument.Fields.URL.equals(fhirVersionProperty)) {
				return new SystemAndVersion(fhirUrlOverride, version.getUrl());
			} else {
				return new SystemAndVersion(fhirUrlOverride, version.getVersion());
			}
		}
			
		try {
			
			final com.b2international.snowowl.core.Resource resource = ResourceRequests.prepareGet(resourceUri)
				.setFields(
					ResourceDocument.Fields.ID,
					ResourceDocument.Fields.URL,
					ResourceDocument.Fields.SETTINGS
				)
				.buildAsync()
				.execute(context);
			
			final Map<String, Object> resourceSettings = resource.getSettings();
			if (resourceSettings == null) {
				return new SystemAndVersion(resource.getUrl(), null);
			}
			
			final String fhirUrlOverride = (String) resourceSettings.get(TerminologyResource.Settings.FHIR_URL);
			if (StringUtils.isEmpty(fhirUrlOverride)) {
				return new SystemAndVersion(resource.getUrl(), null);
			}
			
			final String fhirVersionProperty = (String) resourceSettings.get(TerminologyResource.Settings.FHIR_VERSION_PROPERTY);
			if (ResourceDocument.Fields.URL.equals(fhirVersionProperty)) {
				return new SystemAndVersion(fhirUrlOverride, resource.getUrl());
			} else {
				return new SystemAndVersion(fhirUrlOverride, null);
			}
			
		} catch (final NotFoundException e) {
			return getSystemAndVersionForUnresolvedUri(resourceUri);
		}
	}
	
	public static Function<ResourceURI, SystemAndVersion> createResourceUriToUrlFunction(final ServiceProvider context) {
		final CacheLoader<ResourceURI, SystemAndVersion> loader = CacheLoader.from(resourceUri -> getSystemAndVersionForResourceUri(context, resourceUri));
		final LoadingCache<ResourceURI, SystemAndVersion> urlByResourceId = CacheBuilder.newBuilder().build(loader);
		return urlByResourceId;
	}
	
	public static void setSystemAndVersion(
		final ResourceURI resourceUri, 
		final Function<ResourceURI, SystemAndVersion> mapperFunction, 
		final Consumer<String> systemConsumer, 
		final Consumer<String> versionConsumer
	) {
		final SystemAndVersion systemAndVersion = mapperFunction.apply(resourceUri);
		systemConsumer.accept(systemAndVersion.system());
		versionConsumer.accept(systemAndVersion.version());
	}

	public static CanonicalType toCanonicalType(
		final ResourceURI resourceUri, 
		final Function<ResourceURI, SystemAndVersion> mapperFunction
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
		return isSnomedImplicitValueSetUrl(url)
			|| isLoincImplicitValueSetUrl(url)
			|| isGenericImplicitValueSetUrl(url);
	}
	
	// For SNOMED CT we need a proper SNOMED URI + fhir_vs after the ? query start 
	public static boolean isSnomedImplicitValueSetUrl(String url) {
		return url != null && url.startsWith(SNOMED_BASE_URI_STRING) && url.substring(url.indexOf("?") + 1, url.indexOf("?") + "fhir_vs".length() + 1).equals("fhir_vs");
	}
	
	// For LOINC we need a LOINC URI + "/vs" suffix that is allowed to continue with a single path segment
	public static boolean isLoincImplicitValueSetUrl(String url) {
		return url != null && LOINC_IMPLICIT_VALUESET_PATTERN.matcher(url).matches();
	}
	
	public static String getLoincImplicitValueSetCode(String url) {
		if (url == null) {
			return null;
		}
		
		final Matcher matcher = LOINC_IMPLICIT_VALUESET_PATTERN.matcher(url);
		if (!matcher.matches()) {
			return null;
		}
		
		return Strings.emptyToNull(matcher.group("code"));
	}
	
	// "Generic" means the original CodeSystem URL + an ending /vs suffix to get the implicit ValueSet working
	public static boolean isGenericImplicitValueSetUrl(String url) {
		return url != null && url.startsWith("http://") && url.endsWith(GENERIC_IMPLICIT_VALUESET_SUFFIX);
	}
	
	public static String toGenericCodeSystemUrl(String genericImplicitValueSetUrl) {
		Preconditions.checkArgument(isGenericImplicitValueSetUrl(genericImplicitValueSetUrl), "'url' is not a generic implicit Value Set URL");
		return genericImplicitValueSetUrl.replace(GENERIC_IMPLICIT_VALUESET_SUFFIX, ""); 
	}

	// Methods related to FHIR-specific settings
	
	public static String getEffectiveFhirUrl(final TerminologyResource resource) {
		final Map<String, Object> settings = resource.getSettings();
		if (settings == null) {
			return resource.getUrl();
		}
		
		final String fhirUrlOverride = (String) settings.get(TerminologyResource.Settings.FHIR_URL);
		if (Strings.isNullOrEmpty(fhirUrlOverride)) {
			return resource.getUrl();
		} else {
			return fhirUrlOverride;
		}
	}

	private static String computeEffectiveVersion(
		final String fhirVersionProperty, 
		final Supplier<String> urlSupplier, 
		final Supplier<String> versionSupplier
	) {
		if (fhirVersionProperty == null) {
			return versionSupplier.get();
		}
		
		switch (fhirVersionProperty) {
			case ResourceDocument.Fields.URL:
				return urlSupplier.get();
			case VersionDocument.Fields.VERSION:
				return versionSupplier.get();
			default:
				throw new IllegalStateException("Unsupported FHIR version property '" + fhirVersionProperty + "'");
		}
	}

	public static String computeEffectiveVersion(final CodeSystem codeSystem, final String fhirVersionProperty) {
		return computeEffectiveVersion(fhirVersionProperty, codeSystem::getUrl, () -> "");
	}

	public static String computeEffectiveVersion(final VersionDocument versionDocument, final String fhirVersionProperty) {
		return computeEffectiveVersion(fhirVersionProperty, versionDocument::getUrl, versionDocument::getVersion);
	}
	
	public static String computeEffectiveVersion(final Version version, final String fhirVersionProperty) {
		return computeEffectiveVersion(fhirVersionProperty, version::getUrl, version::getVersion);
	}
}
