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
import java.util.stream.Collectors;

import com.b2international.commons.StringUtils;
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
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

/**
 * Initializes <code>fhirUrl</code> and <code>fhirVersionProperty</code> settings on code system
 * resources and their corresponding version documents based on rules keyed by
 * the code system's {@code toolingId}:
 * <ul>
 *     <li>SNOMED CT &rarr; FHIR URL = <code>"http://snomed.info/sct"</code>, FHIR version property = <code>"url"</code></li>
 *     <li>LOINC &rarr; FHIR URL = <code>"http://loinc.org"</code>, FHIR version property = <code>"version"</code></li>
 *     <li>Other tooling IDs &rarr; FHIR URL = <code>&lt;resource URL&gt;</code>, FHIR version property = <code>null</code>
 *     (implies <code>"version"</code>)</li>
 * </ul>
 *
 * @since 10.2.0
 */
final class FhirCodeSystemInitializeFhirUrlsRequest implements Request<TransactionContext, Boolean>, AccessControl {

	private static final long serialVersionUID = 1L;

	// Tooling ID constants (inlined to avoid dependency on tooling bundles)
	private static final String SNOMED_TOOLING_ID = "snomed";
	private static final String LOINC_TOOLING_ID = "loinc";

	// FHIR URL values (inlined to avoid dependency on tooling bundles)
	private static final String SNOMED_FHIR_URL = "http://snomed.info/sct";
	private static final String LOINC_FHIR_URL = "http://loinc.org";

	/** Mapping from tooling ID to the FHIR URL that should be assigned */
	private static final Map<String, String> FHIR_URL_BY_TOOLING_ID = Map.of(
		SNOMED_TOOLING_ID, SNOMED_FHIR_URL,
		LOINC_TOOLING_ID, LOINC_FHIR_URL
	);

	/** Mapping from tooling ID to the FHIR version property that should be assigned */
	private static final Map<String, String> FHIR_VERSION_PROPERTY_BY_TOOLING_ID = Map.of(
		SNOMED_TOOLING_ID, ResourceDocument.Fields.URL,
		LOINC_TOOLING_ID, VersionDocument.Fields.VERSION
	);

	@JsonProperty
	private final Set<String> codeSystemIds;

	@JsonProperty
	private final boolean overwrite;

	// Cached list of target code systems (used for access control and execution)
	private transient List<CodeSystem> targetCodeSystems;

	FhirCodeSystemInitializeFhirUrlsRequest(final Set<String> codeSystemIds, final boolean overwrite) {
		this.codeSystemIds = codeSystemIds;
		this.overwrite = overwrite;
	}

	private List<CodeSystem> getTargetCodeSystems(final ServiceProvider context) {
		if (targetCodeSystems == null) {
			var searchBuilder = CodeSystemRequests.prepareSearchCodeSystem()
				.setFields(
					ResourceDocument.Fields.ID,
					ResourceDocument.Fields.RESOURCE_TYPE,
					ResourceDocument.Fields.URL,
					ResourceDocument.Fields.TOOLING_ID,
					ResourceDocument.Fields.BUNDLE_ID,
					ResourceDocument.Fields.BUNDLE_ANCESTOR_IDS,
					ResourceDocument.Fields.SETTINGS
				)
				.setLimit(context.getPageSize());

			if (codeSystemIds != null && !codeSystemIds.isEmpty()) {
				searchBuilder.filterByIds(codeSystemIds);
			}

			targetCodeSystems = searchBuilder
				.stream(context, rb -> rb.buildAsync())
				.flatMap(CodeSystems::stream)
				.collect(Collectors.toList());
		}

		return targetCodeSystems;
	}

	private boolean needsUpdate(final CodeSystem codeSystem) {
		final Map<String, Object> settings = codeSystem.getSettings();
		if (settings == null) {
			return true;
		}
		
		if (overwrite) {
			// In overwrite mode, we still skip if the URL and/or version property is already set to the expected value
			final String targetFhirUrl = FHIR_URL_BY_TOOLING_ID.getOrDefault(
				codeSystem.getToolingId(),
				codeSystem.getUrl());
			
			final String targetFhirVersionProperty = FHIR_VERSION_PROPERTY_BY_TOOLING_ID.getOrDefault(
				codeSystem.getToolingId(),
				VersionDocument.Fields.VERSION);

			final String currentFhirUrl = (String) settings.get(CodeSystem.Settings.FHIR_URL);
			
			// FHIR version property may be absent, in which case it defaults to "version"
			String currentFhirVersionProperty = (String) settings.get(CodeSystem.Settings.FHIR_VERSION_PROPERTY);
			if (StringUtils.isEmpty(currentFhirVersionProperty)) {
				currentFhirVersionProperty = VersionDocument.Fields.VERSION;
			}
			
			if (targetFhirUrl.equals(currentFhirUrl) && targetFhirVersionProperty.equals(currentFhirVersionProperty)) {
				return false;
			}
			
			return true;
		}
		
		// In non-overwrite mode, skip resources that already have either setting present
		return !settings.containsKey(CodeSystem.Settings.FHIR_URL)
			&& !settings.containsKey(CodeSystem.Settings.FHIR_VERSION_PROPERTY);
	}

	private void updateVersionDocument(final TransactionContext context, final String versionId, final String fhirUrl, final String fhirVersionProperty) {
		final VersionDocument existingDoc = context.lookup(versionId, VersionDocument.class);
		final Map<String, Object> currentSettings = existingDoc.getSettings();

		final String currentFhirUrl;
		String currentFhirVersionProperty;

		if (currentSettings == null) {
			currentFhirUrl = null;
			currentFhirVersionProperty = null;
		} else {
			currentFhirUrl = (String) currentSettings.get(CodeSystem.Settings.FHIR_URL);
			
			currentFhirVersionProperty = (String) currentSettings.get(CodeSystem.Settings.FHIR_VERSION_PROPERTY);
			if (StringUtils.isEmpty(currentFhirVersionProperty)) {
				currentFhirVersionProperty = VersionDocument.Fields.VERSION;
			}
		}

		// In non-overwrite mode, don't update if either setting is already present (consistent with needsUpdate())
		if (!overwrite && (currentFhirUrl != null || currentFhirVersionProperty != null)) {
			return;
		}

		final boolean urlChanged = !fhirUrl.equals(currentFhirUrl);
		final boolean propertyChanged = !fhirVersionProperty.equals(currentFhirVersionProperty);
		if (!urlChanged && !propertyChanged) {
			return;
		}

		final Map<String, Object> newSettings = new HashMap<>(currentSettings != null ? currentSettings : Map.of());
		if (urlChanged) {
			newSettings.put(CodeSystem.Settings.FHIR_URL, fhirUrl);
		}
		
		if (propertyChanged) {
			newSettings.put(CodeSystem.Settings.FHIR_VERSION_PROPERTY, fhirVersionProperty);
		}

		context.add(VersionDocument.builder(existingDoc)
			.settings(newSettings)
			.build());
	}

	@Override
	public Boolean execute(final TransactionContext context) {
		final List<CodeSystem> allTargets = getTargetCodeSystems(context);

		// Filter down to resources that actually require an update
		final List<CodeSystem> modifiableCodeSystems = allTargets.stream()
			.filter(this::needsUpdate)
			.collect(Collectors.toList());

		if (modifiableCodeSystems.isEmpty()) {
			return Boolean.FALSE;
		}

		final Set<String> modifiableIdSet = modifiableCodeSystems.stream()
			.map(CodeSystem::getId)
			.collect(Collectors.toSet());

		// Fetch version IDs for all modifiable resources
		final Multimap<String, String> versionIdsByResourceId = ResourceRequests.prepareSearchVersion()
			.filterByResources(modifiableIdSet)
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

		context.lookup(versionIdsByResourceId.values(), VersionDocument.class);
		context.ensurePresent(ResourceDocument.class, modifiableIdSet);

		for (final CodeSystem modifiableCodeSystem : modifiableCodeSystems) {
			final String toolingId = modifiableCodeSystem.getToolingId();
			
			// Default URL is the resource URL
			final String fhirUrl = FHIR_URL_BY_TOOLING_ID.getOrDefault(toolingId, modifiableCodeSystem.getUrl());
			// Default FHIR version property is null i.e. "version" which does not need to be explicitly set
			final String fhirVersionProperty = FHIR_VERSION_PROPERTY_BY_TOOLING_ID.get(toolingId);

			final ResourceDocument targetResource = context.lookup(modifiableCodeSystem.getId(), ResourceDocument.class);
			final Map<String, Object> existingSettings = targetResource.getSettings();
			final Map<String, Object> newSettings = new HashMap<>(existingSettings != null ? existingSettings : Map.of());
			newSettings.put(CodeSystem.Settings.FHIR_URL, fhirUrl);
			newSettings.put(CodeSystem.Settings.FHIR_VERSION_PROPERTY, fhirVersionProperty);

			context.update(targetResource, ResourceDocument.builder(targetResource)
				.settings(newSettings)
				.build());

			final Collection<String> versionIds = versionIdsByResourceId.get(targetResource.getId());
			for (final String versionId : versionIds) {
				updateVersionDocument(context, versionId, fhirUrl, fhirVersionProperty);
			}
		}

		return Boolean.TRUE;
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

			permissions.add(Permission.requireAny(getOperation(), uniqueUris));
		}

		return permissions;
	}

	@Override
	public void collectAccessedResources(final ServiceProvider context, final Request<ServiceProvider, ?> req, final List<String> accessedResources) {
		throw new UnsupportedOperationException("Access control is handled by getPermissions() in this request");
	}
}
