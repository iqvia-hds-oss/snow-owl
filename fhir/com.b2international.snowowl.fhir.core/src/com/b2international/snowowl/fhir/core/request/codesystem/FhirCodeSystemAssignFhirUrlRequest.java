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

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.b2international.commons.exceptions.BadRequestException;
import com.b2international.commons.exceptions.NotFoundException;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.authorization.AccessControl;
import com.b2international.snowowl.core.codesystem.CodeSystem;
import com.b2international.snowowl.core.codesystem.CodeSystemRequests;
import com.b2international.snowowl.core.codesystem.CodeSystems;
import com.b2international.snowowl.core.domain.IComponent;
import com.b2international.snowowl.core.domain.TransactionContext;
import com.b2international.snowowl.core.events.Request;
import com.b2international.snowowl.core.identity.Permission;
import com.b2international.snowowl.core.internal.ResourceDocument;
import com.b2international.snowowl.core.request.ResourceRequests;
import com.b2international.snowowl.core.version.VersionDocument;
import com.b2international.snowowl.core.version.Versions;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import jakarta.validation.constraints.NotEmpty;

/**
 * Assigns a <code>fhirUrl</code> and a <code>fhirVersionProperty</code> setting
 * to one or more code systems identified by their native IDs.
 * <p>
 * After the assignment, no two code systems sharing the same effective FHIR URL
 * may have the same effective FHIR version. The effective FHIR version for a
 * code system is determined by the <code>fhirVersionProperty</code> setting:
 * <ul>
 *     <li><code>"url"</code> &rarr; the resource fragment's (native) URL field is used as the FHIR version</li>
 *     <li>anything else (or absent) &rarr; the resource fragment's <code>version</code> field is used as the FHIR version</li>
 * </ul>
 * For code systems that are <em>not</em> being assigned in this request but
 * already share the URL, their existing <code>fhirVersionProperty</code>
 * setting (if any) is used for this check, they will not be modified.
 * <p>
 * <b>Note:</b> The resource settings snapshot stored on version documents of all 
 * modified code systems will be updated in accordance with the new values set in 
 * this request. In most cases this could be considered a retroactive modification
 * of data we typically treat as immutable, but it is necessary for consistency.
 *
 * @since 10.2.0
 */
final class FhirCodeSystemAssignFhirUrlRequest implements Request<TransactionContext, Boolean>, AccessControl {

	private static final long serialVersionUID = 1L;

	@NotEmpty
	@JsonProperty
	private final List<String> codeSystemIds;

	@NotEmpty
	@JsonProperty
	private final String fhirUrl;

	// Nullable; null input means do not change the existing value (if present)
	@JsonProperty
	private final String fhirVersionProperty;

	// Cached values which would need to be requested multiples times (e.g. for access control checks and request execution)
	private transient List<CodeSystem> targetCodeSystems;
	private transient List<CodeSystem> modifiableTargetResources;
	private transient Multimap<String, String> targetVersionIdsByResourceId;

	FhirCodeSystemAssignFhirUrlRequest(final List<String> codeSystemIds, final String fhirUrl, final String fhirVersionProperty) {
		this.codeSystemIds = codeSystemIds;
		this.fhirUrl = fhirUrl;
		this.fhirVersionProperty = fhirVersionProperty;
	}

	private List<CodeSystem> getTargetCodeSystems(final ServiceProvider context) {
		if (targetCodeSystems == null) {
			final Set<String> uniqueIds = new HashSet<>(codeSystemIds);
			
			targetCodeSystems = CodeSystemRequests.prepareSearchCodeSystem()
				.filterByIds(uniqueIds)
				.setFields(
					ResourceDocument.Fields.ID,
					ResourceDocument.Fields.RESOURCE_TYPE,
					ResourceDocument.Fields.BUNDLE_ID,
					ResourceDocument.Fields.BUNDLE_ANCESTOR_IDS,
					ResourceDocument.Fields.SETTINGS
				)
				.setLimit(context.getPageSize())
				.stream(context, rb -> rb.buildAsync())
				.flatMap(CodeSystems::stream)
				.collect(Collectors.toList());
			
			final Set<String> foundIds = targetCodeSystems.stream()
				.map(CodeSystem::getId)
				.collect(Collectors.toSet());
			
			if (uniqueIds.size() != foundIds.size()) {
				// Change unique IDs to missing IDs for the error message
				uniqueIds.removeAll(foundIds);
				throw new NotFoundException("Code system(s)",  uniqueIds.toString())
					.withDeveloperMessage("");
			}
		}
		
		return targetCodeSystems;
	}

	private static String getEffectiveFhirUrl(final CodeSystem existingResource) {
		final Map<String, Object> settings = existingResource.getSettings();
		if (settings == null) {
			return existingResource.getUrl();
		}
		
		final String fhirUrl = (String) settings.get(CodeSystem.Settings.FHIR_URL);
		if (Strings.isNullOrEmpty(fhirUrl)) {
			return existingResource.getUrl();
		}
		
		return fhirUrl;
	}

	private Collection<CodeSystem> findExistingResourcesWithSameUrl(final TransactionContext context, final Set<String> modifiableIdSet) {
		final Map<String, CodeSystem> existingResourcesById = new HashMap<>();
	
		// Resources explicitly overriding their FHIR URL to the value in the request
		CodeSystemRequests.prepareSearchCodeSystem()
			.filterBySettings(CodeSystem.Settings.FHIR_URL, fhirUrl)
			.setFields(
				ResourceDocument.Fields.ID, 
				ResourceDocument.Fields.URL, 
				ResourceDocument.Fields.SETTINGS
			)
			.setLimit(context.getPageSize())
			.stream(context)
			.flatMap(CodeSystems::stream)
			.filter(r -> !modifiableIdSet.contains(r.getId()))
			.forEach(r -> existingResourcesById.put(r.getId(), r));
	
		/*
		 * Resource with no override in settings (but only if it hasn't been included in
		 * the map yet) - at most one hit is expected since native URLs are unique, but 
		 * we need to check the "no override in settings" condition after it has been 
		 * returned by the search request
		 */
		CodeSystemRequests.prepareSearchCodeSystem()
			.one()
			.filterByUrl(fhirUrl)
			.setFields(
				ResourceDocument.Fields.ID, 
				ResourceDocument.Fields.URL, 
				ResourceDocument.Fields.SETTINGS
			)
			.build()
			.execute(context)
			.stream()
			.filter(r -> !modifiableIdSet.contains(r.getId()))
			.filter(r -> fhirUrl.equals(getEffectiveFhirUrl(r)))
			.forEach(r -> existingResourcesById.putIfAbsent(r.getId(), r));
	
		return existingResourcesById.values();
	}

	private Multimap<String, String> getVersionIdsByResourceId(
		final TransactionContext context,
		final Set<String> targetResourceIds
	) {
		return ResourceRequests.prepareSearchVersion()
			.filterByResources(targetResourceIds) // TODO: check if this filter actually accepts resource identifiers
			.setFields(
				VersionDocument.Fields.ID, 
				VersionDocument.Fields.RESOURCE
			)
			.setLimit(context.getPageSize())
			.stream(context)
			.flatMap(Versions::stream)
			.collect(Multimaps.toMultimap(
				v -> v.getResource().getResourceId(),
				v -> v.getId(),
				HashMultimap::create));
	}

	private String getExistingFhirVersionProperty(final Map<String, Object> settings) {
		if (settings == null) {
			return null;
		} else {
			return (String) settings.get(CodeSystem.Settings.FHIR_VERSION_PROPERTY);
		}
	}

	private Multimap<String, String> getTargetVersionIdsByResourceId(final TransactionContext context, final Set<String> targetResourceIds) {
		if (targetVersionIdsByResourceId == null) {
			targetVersionIdsByResourceId = getVersionIdsByResourceId(context, targetResourceIds);
			context.lookup(targetVersionIdsByResourceId.values(), VersionDocument.class);
		}
		
		return targetVersionIdsByResourceId;
	}

	private String getNewFhirVersionProperty(final Map<String, Object> settings) {
		if (fhirVersionProperty != null) {
			return fhirVersionProperty;
		} else {
			return getExistingFhirVersionProperty(settings);
		}
	}

	private static String computeEffectiveVersion(final CodeSystem codeSystem, final String fhirVersionProperty) {
		if (CodeSystem.Fields.URL.equals(fhirVersionProperty)) {
			return codeSystem.getUrl();
		} else {
			return "";
		}
	}

	private static String computeEffectiveVersion(final VersionDocument versionDocument, final String fhirVersionProperty) {
		if (CodeSystem.Fields.URL.equals(fhirVersionProperty)) {
			return versionDocument.getUrl();
		} else {
			return versionDocument.getVersion();
		}
	}

	private void collectVersions(
		final TransactionContext context, 
		final Multimap<String, String> versionIdsByResourceId,
		final Multimap<String, String> resourceIdsByVersion,
		final Function<Map<String, Object>, String> getFhirVersionPropertyFn,
		final String resourceId
	) {
		final Collection<String> existingVersionIds = versionIdsByResourceId.get(resourceId);
		final Collection<VersionDocument> existingVersions = context.lookup(existingVersionIds, VersionDocument.class).values();
		
		for (final VersionDocument existingVersion : existingVersions) {
			final Map<String, Object> versionSettings = existingVersion.getSettings();
			final String versionProperty = getFhirVersionPropertyFn.apply(versionSettings);
			final String versionValue = computeEffectiveVersion(existingVersion, versionProperty);
			resourceIdsByVersion.put(versionValue, resourceId);
		}
	}

	private void checkVersionUniqueness(
		final Collection<CodeSystem> targetCodeSystems,
		final Collection<CodeSystem> existingResources, 
		final TransactionContext context
	) {
		// Map from effective version string to the list of code system IDs having it
		final Multimap<String, String> resourceIdsByVersion = HashMultimap.create();

		final Set<String> existingResourceIds = existingResources.stream()
			.map(CodeSystem::getId)
			.collect(Collectors.toSet());
		
		final Multimap<String, String> existingVersionIdsByResourceId = getVersionIdsByResourceId(context, existingResourceIds);
		context.lookup(existingVersionIdsByResourceId.values(), VersionDocument.class);
	
		// Existing code systems (not being assigned) use their own fhirVersionProperty
		for (final CodeSystem existingResource : existingResources) {
			final Map<String, Object> resourceSettings = existingResource.getSettings();
			final String resourceProperty = getExistingFhirVersionProperty(resourceSettings);
			final String resourceValue = computeEffectiveVersion(existingResource, resourceProperty);
			resourceIdsByVersion.put(resourceValue, existingResource.getId());
			
			collectVersions(
				context, 
				existingVersionIdsByResourceId, 
				resourceIdsByVersion, 
				this::getExistingFhirVersionProperty,
				existingResource.getId());
		}
	
		final Set<String> targetResourceIds = targetCodeSystems.stream()
			.map(CodeSystem::getId)
			.collect(Collectors.toSet());
		
		final Multimap<String, String> targetVersionIdsByResourceId = getTargetVersionIdsByResourceId(context, targetResourceIds);
		// context.lookup(targetVersionIdsByResourceId.values(), VersionDocument.class) is called in getTargetVersionIdsByResourceId so not needed here
		
		// For target code systems, use the fhirVersionProperty from the request if present, otherwise fall back to their existing value
		for (final CodeSystem targetCodeSystem : targetCodeSystems) {
			final Map<String, Object> resourceSettings = targetCodeSystem.getSettings();
			final String resourceProperty = getNewFhirVersionProperty(resourceSettings);
			final String resourceValue = computeEffectiveVersion(targetCodeSystem, resourceProperty);
			resourceIdsByVersion.put(resourceValue, targetCodeSystem.getId());
			
			// The parameter will apply to future and existing versions created for the resource so we need to check that as well
			collectVersions(
				context, 
				targetVersionIdsByResourceId, 
				resourceIdsByVersion, 
				this::getNewFhirVersionProperty,
				targetCodeSystem.getId());
		}
	
		// Report any duplicated effective versions
		final List<String> conflicts = resourceIdsByVersion.asMap()
			.entrySet()
			.stream()
			.filter(e -> e.getValue().size() > 1)
			.map(e -> String.format("version '%s' is shared by: %s", e.getKey(), e.getValue()))
			.collect(Collectors.toList());
	
		if (!conflicts.isEmpty()) {
			throw new BadRequestException(
				"Code systems sharing FHIR URL '%s' must have unique effective versions. Conflicts detected: %s",
				fhirUrl,
				conflicts);
		}
	}

	@Override
	public String getOperation() {
		return Permission.OPERATION_EDIT;
	}

	@Override
	public List<Permission> getPermissions(final ServiceProvider context, final Request<ServiceProvider, ?> req) {
		final List<CodeSystem> codeSystems = getTargetCodeSystems(context);
		final List<Permission> permissions = new ArrayList<>();
		
		for (final CodeSystem codeSystem : codeSystems) {
			final Set<String> uniqueUris = new HashSet<>();

			uniqueUris.add(codeSystem.getResourceURI().getUri());
			uniqueUris.add(codeSystem.getResourceURI().withoutResourceType());
			uniqueUris.add(codeSystem.getBundleId());
			uniqueUris.addAll(codeSystem.getBundleAncestorIds());
			uniqueUris.remove(IComponent.ROOT_ID);
			
			// OR-combine all relevant URIs for a single permission
			permissions.add(Permission.requireAny(getOperation(), uniqueUris));
		}
		
		// AND-combine permissions for all resources for the request
		return permissions;
	}
	
	@Override
	public void collectAccessedResources(final ServiceProvider context, final Request<ServiceProvider, ?> req, final List<String> accessedResources) {
		throw new UnsupportedOperationException("Access control is handled by getPermissions() in this request");
	}

	private boolean needsUpdate(final CodeSystem codeSystem) {
		final Map<String, Object> settings = codeSystem.getSettings();
		if (settings == null) {
			return true;
		}
		
		final String currentFhirUrl = (String) settings.get(CodeSystem.Settings.FHIR_URL);
		if (!fhirUrl.equals(currentFhirUrl)) {
			return true;
		}
		
		final String currentFhirVersionProperty = (String) settings.get(CodeSystem.Settings.FHIR_VERSION_PROPERTY);
		if (fhirVersionProperty != null && !fhirVersionProperty.equals(currentFhirVersionProperty)) {
			return true;
		}
	
		return false;
	}

	private List<CodeSystem> getModifiableCodeSystems(final TransactionContext context) {
		if (modifiableTargetResources == null) {
			modifiableTargetResources = getTargetCodeSystems(context)
				.stream()
				.filter(this::needsUpdate)
				.collect(Collectors.toList());
		}
		
		return modifiableTargetResources;
	}

	private void updateVersionDocument(final TransactionContext context, final String versionId) {
		final VersionDocument existingDoc = context.lookup(versionId, VersionDocument.class);
		
		final Map<String, Object> currentSettings = existingDoc.getSettings();
		final String currentFhirUrl;
		final String currentFhirVersionProperty;
		
		if (currentSettings == null) {
			currentFhirUrl = null;
			currentFhirVersionProperty = null;
		} else {
			currentFhirUrl = (String) currentSettings.get(CodeSystem.Settings.FHIR_URL);
			currentFhirVersionProperty = (String) currentSettings.get(CodeSystem.Settings.FHIR_VERSION_PROPERTY);
		}
	
		final boolean urlChanged = !fhirUrl.equals(currentFhirUrl);
		final boolean propertyChanged = fhirVersionProperty != null && !fhirVersionProperty.equals(currentFhirVersionProperty);
		if (!urlChanged && !propertyChanged) {
			return;
		}
	
		final Map<String, Object> newSettings = new HashMap<>(currentSettings != null ? currentSettings : Map.of());
		newSettings.put(CodeSystem.Settings.FHIR_URL, fhirUrl);
		if (fhirVersionProperty != null) {
			newSettings.put(CodeSystem.Settings.FHIR_VERSION_PROPERTY, fhirVersionProperty);
		}
	
		context.add(VersionDocument.builder(existingDoc)
			.settings(newSettings)
			.build());
	}

	@Override
	public Boolean execute(final TransactionContext context) {
		// Narrow down resources that actually need to be changed; exit early if all are already up to date
		final List<CodeSystem> modifiableCodeSystems = getModifiableCodeSystems(context);
		if (modifiableCodeSystems.isEmpty()) {
			return Boolean.FALSE;
		}

		/* 
		 * Find existing code systems (not in the set about to be changed) that already share the
		 * same effective FHIR URL. We need to include them in the version uniqueness check.
		 */
		final Set<String> modifiableIdSet = modifiableCodeSystems.stream()
			.map(CodeSystem::getId)
			.collect(Collectors.toSet());
		
		final Collection<CodeSystem> existingResources = findExistingResourcesWithSameUrl(context, modifiableIdSet);
		checkVersionUniqueness(modifiableCodeSystems, existingResources, context);
	
		// Pre-fetch version IDs for all modified resources (only the assigned ones)
		final Multimap<String, String> versionIdsByResourceId = getTargetVersionIdsByResourceId(context, modifiableIdSet);
	
		context.ensurePresent(ResourceDocument.class, modifiableIdSet);
		
		for (final CodeSystem modifiableCodeSystem : modifiableCodeSystems) {
			final ResourceDocument targetResource = context.lookup(modifiableCodeSystem.getId(), ResourceDocument.class);
			final Map<String, Object> existingSettings = targetResource.getSettings();
			final Map<String, Object> newSettings = new HashMap<>(existingSettings != null ? existingSettings : Map.of());
			newSettings.put(CodeSystem.Settings.FHIR_URL, fhirUrl);
			
			if (fhirVersionProperty != null) {
				newSettings.put(CodeSystem.Settings.FHIR_VERSION_PROPERTY, fhirVersionProperty);
			}
			
			context.update(targetResource, ResourceDocument.builder(targetResource)
				.settings(newSettings)
				.build());
	
			final Collection<String> versionIds = versionIdsByResourceId.get(targetResource.getId());
			for (final String versionId : versionIds) {
				updateVersionDocument(context, versionId);
			}
		}
	
		return Boolean.TRUE;
	}
}
