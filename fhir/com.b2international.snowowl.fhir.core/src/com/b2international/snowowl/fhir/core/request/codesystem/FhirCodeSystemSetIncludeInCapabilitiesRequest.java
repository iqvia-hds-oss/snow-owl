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

import com.b2international.commons.exceptions.NotFoundException;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.TerminologyResource;
import com.b2international.snowowl.core.authorization.AccessControl;
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
 * Sets the <code>fhirIncludeInCapabilities</code> setting to
 * <code>"true"</code> or <code>"false"</code> for one or more code systems
 * identified by their IDs.
 * <p>
 * Version documents of all modified code systems are updated to propagate the
 * setting.
 *
 * @since 10.2.0
 */
final class FhirCodeSystemSetIncludeInCapabilitiesRequest implements Request<TransactionContext, Boolean>, AccessControl {

	private static final long serialVersionUID = 1L;

	@NotEmpty
	@JsonProperty
	private final List<String> codeSystemIds;

	@JsonProperty
	private final boolean includeInCapabilities;

	private Collection<ResourceDocument> targetResources;

	FhirCodeSystemSetIncludeInCapabilitiesRequest(final List<String> codeSystemIds, final boolean includeInCapabilities) {
		this.codeSystemIds = codeSystemIds;
		this.includeInCapabilities = includeInCapabilities;
	}

	private Collection<ResourceDocument> getTargetResources(final TransactionContext context) {
		if (targetResources == null) {
			final Set<String> uniqueIds = new HashSet<>(codeSystemIds);
			final Map<String, ResourceDocument> resourcesById = context.lookup(uniqueIds, ResourceDocument.class);
			
			if (uniqueIds.size() != resourcesById.size()) {
				// Change unique IDs to missing IDs for the error message
				uniqueIds.removeAll(resourcesById.keySet());
				throw new NotFoundException("Code system(s)",  uniqueIds.toString())
					.withDeveloperMessage("");
			}
			
			targetResources = resourcesById.values();
		}
		
		return targetResources;
	}

	@Override
	public String getOperation() {
		return Permission.OPERATION_EDIT;
	}

	@Override
	public void collectAccessedResources(final ServiceProvider context, final Request<ServiceProvider, ?> req, final List<String> accessedResources) {
		final Collection<ResourceDocument> resources = getTargetResources((TransactionContext) context);
		final Set<String> uniqueUris = new HashSet<>();
		
		for (final ResourceDocument resource : resources) {
			uniqueUris.add(resource.getResourceURI().getUri());
			uniqueUris.add(resource.getResourceURI().withoutResourceType());
			uniqueUris.add(resource.getBundleId());
			uniqueUris.addAll(resource.getBundleAncestorIds());
		}
		
		uniqueUris.remove(IComponent.ROOT_ID);
		accessedResources.addAll(uniqueUris);
	}

	private static Map<String, Object> toMutableMap(final Map<String, Object> settings) {
		return new HashMap<>(settings != null ? settings : Map.of());
	}

	private boolean updateVersionDocument(final TransactionContext context, final String versionId, final String newValue) {
		final VersionDocument versionDocument = context.lookup(versionId, VersionDocument.class);
		final Map<String, Object> versionSettings = versionDocument.getSettings();
		final String currentValue;
		
		if (versionSettings == null) {
			currentValue = null;
		} else {
			currentValue = (String) versionSettings.get(TerminologyResource.Settings.FHIR_INCLUDE_IN_CAPABILITIES);
		}
	
		if (newValue.equals(currentValue)) {
			return false;
		}
	
		final Map<String, Object> newVersionSettings = toMutableMap(versionSettings);
		newVersionSettings.put(TerminologyResource.Settings.FHIR_INCLUDE_IN_CAPABILITIES, newValue);
	
		/*
		 * XXX: We are using context.add() instead of context.update() because
		 * VersionDocument is not a true revision document. Since the version identifier
		 * is preserved, the old document should be replaced with the updated one in
		 * such cases.
		 */
		context.add(VersionDocument.builder(versionDocument)
			.settings(newVersionSettings)
			.build());
	
		return true;
	}

	@Override
	public Boolean execute(final TransactionContext context) {
		final Collection<ResourceDocument> targetResources = getTargetResources(context);
		final String newValue = Boolean.toString(includeInCapabilities);

		// Pre-fetch version IDs for all modified resources
		final Multimap<String, String> versionIdsByResourceId = ResourceRequests.prepareSearchVersion()
			.filterByResources(codeSystemIds)
			.setLimit(context.getPageSize())
			.stream(context)
			.flatMap(Versions::stream)
			.collect(Multimaps.toMultimap(
				v -> v.getResource().getResourceId(),
				v -> v.getId(),
				HashMultimap::create));

		// Pre-fetch all version documents in a single batch
		context.lookup(versionIdsByResourceId.values(), VersionDocument.class);

		boolean anyChanged = false;

		for (final ResourceDocument targetResource : targetResources) {
			final Map<String, Object> existingSettings = targetResource.getSettings();
			final String currentValue;
			
			if (existingSettings == null) {
				currentValue = null;
			} else {
				currentValue = (String) existingSettings.get(TerminologyResource.Settings.FHIR_INCLUDE_IN_CAPABILITIES);
			}

			if (!newValue.equals(currentValue)) {
				final Map<String, Object> newSettings = toMutableMap(existingSettings);
				newSettings.put(TerminologyResource.Settings.FHIR_INCLUDE_IN_CAPABILITIES, newValue);

				context.update(targetResource, ResourceDocument.builder(targetResource)
					.settings(newSettings)
					.build());

				anyChanged |= true;
			}

			final Collection<String> versionIds = versionIdsByResourceId.get(targetResource.getId());
			for (final String versionId : versionIds) {
				anyChanged |= updateVersionDocument(context, versionId, newValue);
			}
		}

		return anyChanged;
	}
}
