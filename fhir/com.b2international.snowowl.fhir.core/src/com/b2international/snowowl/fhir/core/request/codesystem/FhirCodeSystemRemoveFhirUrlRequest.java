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

import com.b2international.commons.exceptions.NotFoundException;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.TerminologyResource;
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

import jakarta.validation.constraints.NotEmpty;

/**
 * Removes the {@code fhirUrl} and {@code fhirVersionProperty} settings from one
 * or more code systems identified by their IDs. Code systems that do not carry
 * either setting are left unchanged. Version documents of modified code systems
 * are updated accordingly.
 *
 * @since 10.2.0
 */
final class FhirCodeSystemRemoveFhirUrlRequest implements Request<TransactionContext, Boolean>, AccessControl {

	private static final long serialVersionUID = 1L;

	@NotEmpty
	@JsonProperty
	private final List<String> codeSystemIds;

	private transient List<CodeSystem> targetCodeSystems;

	FhirCodeSystemRemoveFhirUrlRequest(final List<String> codeSystemIds) {
		this.codeSystemIds = codeSystemIds;
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

	private static boolean hasFhirUrlOrVersionProperty(final Map<String, Object> settings) {
		if (settings == null) {
			return false;
		}
		
		return settings.containsKey(TerminologyResource.Settings.FHIR_URL)
			|| settings.containsKey(TerminologyResource.Settings.FHIR_VERSION_PROPERTY);
	}

	private void updateVersionDocument(final TransactionContext context, final String versionId) {
		final VersionDocument existingDoc = context.lookup(versionId, VersionDocument.class);
		
		final Map<String, Object> currentSettings = existingDoc.getSettings();
		if (currentSettings == null) {
			return;
		}
		
		if (!currentSettings.containsKey(TerminologyResource.Settings.FHIR_URL)
			&& !currentSettings.containsKey(TerminologyResource.Settings.FHIR_VERSION_PROPERTY)) {
			return;
		}
	
		final Map<String, Object> newSettings = new HashMap<>(currentSettings);
		newSettings.remove(TerminologyResource.Settings.FHIR_URL);
		newSettings.remove(TerminologyResource.Settings.FHIR_VERSION_PROPERTY);
	
		context.add(VersionDocument.builder(existingDoc)
			.settings(newSettings.isEmpty() ? null : newSettings)
			.build());
	}

	@Override
	public Boolean execute(final TransactionContext context) {
		final List<CodeSystem> targetCodeSystems = getTargetCodeSystems(context);

		// Keep only those that actually have at least one of the settings to remove
		final List<CodeSystem> modifiableTargetCodeSystems = targetCodeSystems.stream()
			.filter(cs -> hasFhirUrlOrVersionProperty(cs.getSettings()))
			.collect(Collectors.toList());

		if (modifiableTargetCodeSystems.isEmpty()) {
			return Boolean.FALSE;
		}

		final Set<String> modifiableIdSet = modifiableTargetCodeSystems.stream()
			.map(CodeSystem::getId)
			.collect(Collectors.toSet());

		// Pre-fetch version IDs for all modified resources
		final Multimap<String, String> versionIdsByResourceId = ResourceRequests.prepareSearchVersion()
			.filterByResources(modifiableIdSet) // TODO: check if this filter actually accepts resource identifiers
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

		context.ensurePresent(ResourceDocument.class, modifiableIdSet);
					
		for (final CodeSystem modifiableCodeSystem : modifiableTargetCodeSystems) {
			final ResourceDocument targetResource = context.lookup(modifiableCodeSystem.getId(), ResourceDocument.class);
			final Map<String, Object> existingSettings = targetResource.getSettings();
			final Map<String, Object> newSettings = new HashMap<>(existingSettings != null ? existingSettings : Map.of());
			newSettings.remove(TerminologyResource.Settings.FHIR_URL);
			newSettings.remove(TerminologyResource.Settings.FHIR_VERSION_PROPERTY);
			
			context.update(targetResource, ResourceDocument.builder(targetResource)
				.settings(newSettings.isEmpty() ? null : newSettings)
				.build());

			final Collection<String> versionIds = versionIdsByResourceId.get(targetResource.getId());
			for (final String versionId : versionIds) {
				updateVersionDocument(context, versionId);
			}
		}

		return Boolean.TRUE;
	}
}
