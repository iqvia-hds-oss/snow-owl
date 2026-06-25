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
package com.b2international.snowowl.fhir.core.request.codesystem;

import static com.b2international.snowowl.fhir.core.FhirModelHelpers.computeEffectiveVersion;
import static com.b2international.snowowl.fhir.core.FhirModelHelpers.getEffectiveFhirUrl;

import java.util.*;
import java.util.stream.Collectors;

import org.hl7.fhir.r5.model.TerminologyCapabilities;
import org.hl7.fhir.r5.model.TerminologyCapabilities.TerminologyCapabilitiesCodeSystemComponent;

import com.b2international.snowowl.core.Resources;
import com.b2international.snowowl.core.TerminologyResource;
import com.b2international.snowowl.core.domain.RepositoryContext;
import com.b2international.snowowl.core.events.Request;
import com.b2international.snowowl.core.internal.ResourceDocument;
import com.b2international.snowowl.core.request.ResourceRequests;
import com.b2international.snowowl.core.request.SearchResourceRequest;
import com.b2international.snowowl.core.version.Version;
import com.b2international.snowowl.core.version.Versions;
import com.google.common.base.Strings;
import com.google.common.collect.*;

/**
 * Retrieves all code systems that have the <code>fhirIncludeInCapabilities</code>
 * setting set to <code>"true"</code> and converts them (along with their version
 * documents) to a list of {@link TerminologyCapabilitiesCodeSystemComponent}
 * instances ready to be embedded in a {@link TerminologyCapabilities} response.
 * <p>
 * Code systems sharing the same effective FHIR URL are merged into a single
 * code system component, with each version contributing one nested <code>version</code> entry.
 *
 * @since 10.2.0
 */
final class FhirCodeSystemGetComponentsRequest
	implements Request<RepositoryContext, List<TerminologyCapabilitiesCodeSystemComponent>> {

	private static final long serialVersionUID = 1L;
	
	private static final Comparator<? super Version> EFFECTIVE_TIME_ORDERING = Ordering.natural()
		.nullsFirst()
		.onResultOf(Version::getEffectiveTime);

	private static String getFhirVersionProperty(final Map<String, Object> settings) {
		if (settings == null) {
			return null;
		}
		
		return (String) settings.get(TerminologyResource.Settings.FHIR_VERSION_PROPERTY);
	}

	private static boolean hasEffectiveFhirUrlOverride(final Map<String, Object> settings) {
		if (settings == null) {
			return false;
		}
		
		final String fhirUrlOverride = (String) settings.get(TerminologyResource.Settings.FHIR_URL);
		return !Strings.isNullOrEmpty(fhirUrlOverride);
	}

	private static boolean isUseAsDefault(final Map<String, Object> settings) {
		if (settings == null) {
			return false;
		}
		
		return "true".equals(settings.get(TerminologyResource.Settings.FHIR_USE_AS_DEFAULT));
	}

	@Override
	public List<TerminologyCapabilitiesCodeSystemComponent> execute(final RepositoryContext context) {
		// Retrieve all code systems flagged for inclusion in terminology metadata
		final List<TerminologyResource> codeSystems = ResourceRequests.prepareSearch()
			.filterBySettings(TerminologyResource.Settings.FHIR_INCLUDE_IN_CAPABILITIES, "true")
			.setLimit(context.getPageSize())
			.sortBy(
				SearchResourceRequest.Sort.fieldAsc(ResourceDocument.Fields.SETTINGS + "." + TerminologyResource.Settings.FHIR_URL),
				SearchResourceRequest.Sort.fieldAsc(ResourceDocument.Fields.URL)
			)
			.stream(context)
			.flatMap(Resources::stream)
			.filter(TerminologyResource.class::isInstance)
			.map(TerminologyResource.class::cast)
			.collect(Collectors.toList());
	
		if (codeSystems.isEmpty()) {
			return List.of();
		}
	
		final List<String> codeSystemIds = codeSystems.stream()
			.map(TerminologyResource::getId)
			.collect(Collectors.toList());
	
		// Retrieve all versions for the flagged code systems (we will not check inclusion on the version level)
		final Multimap<String, Version> versionsByCodeSystemId = ResourceRequests.prepareSearchVersion()
			.filterByResources(codeSystemIds)
			.setLimit(context.getPageSize())
			.stream(context)
			.flatMap(Versions::stream)
			.collect(Multimaps.toMultimap(
				v -> v.getResource().getResourceId(),
				v -> v,
				ArrayListMultimap::create));
	
		// Group by effective FHIR URL; multiple code systems under the same URL share one terminology capabilities component
		final Map<String, TerminologyCapabilitiesCodeSystemComponent> componentByUrl = new LinkedHashMap<>();
	
		for (final TerminologyResource codeSystem : codeSystems) {
			final String effectiveFhirUrl = getEffectiveFhirUrl(codeSystem);
			final String fhirVersionProperty = getFhirVersionProperty(codeSystem.getSettings());
	
			/*
			 * A code system contributes a "latest is default" marker only if either:
			 * 
			 * - it does NOT have an overridden FHIR URL (native URL resources are always the default for their own URL) OR
			 * - it has fhirUseAsDefault = "true" explicitly set
			 * 
			 * If a URL-override is present but fhirUseAsDefault is absent/false, _all_ version entries for this code system 
			 * will be marked as non-default -- the flag should be set in another code system with the same effective URL!
			 */
			final boolean markDefault = !hasEffectiveFhirUrlOverride(codeSystem.getSettings()) || isUseAsDefault(codeSystem.getSettings());
	
			final TerminologyCapabilitiesCodeSystemComponent component = componentByUrl.computeIfAbsent(effectiveFhirUrl,
				uri -> new TerminologyCapabilitiesCodeSystemComponent()
					.setUri(uri)
					.setSubsumption(true));
	
			final List<Version> versions = ImmutableList.sortedCopyOf(EFFECTIVE_TIME_ORDERING, versionsByCodeSystemId.get(codeSystem.getId()));
	
			// Determine which version should be the default (latest by effective time)
			final String latestVersionId;
			if (!versions.isEmpty() && markDefault) {
				latestVersionId = versions.getLast().getId();
			} else {
				latestVersionId = null;
			}
	
			if (ResourceDocument.Fields.URL.equals(fhirVersionProperty)) {
				// If the version code is determined by the version's URL, we also need to add the
				// code system's _native_ URL as a version entry as well (representing the HEAD/draft content).
				// This entry is never the default because it does not correspond to a released version.
				component.addVersion()
					.setCode(codeSystem.getUrl())
					.setIsDefault(false)
					.setCompositional(true);
			}
	
			for (final Version version : versions) {
				final String effectiveVersionCode = computeEffectiveVersion(version, fhirVersionProperty);
				final boolean isDefault = markDefault && version.getId().equals(latestVersionId);
				
				component.addVersion()
					.setCode(effectiveVersionCode)
					.setIsDefault(isDefault)
					.setCompositional(true);
			}
		}
	
		return new ArrayList<>(componentByUrl.values());
	}
}
