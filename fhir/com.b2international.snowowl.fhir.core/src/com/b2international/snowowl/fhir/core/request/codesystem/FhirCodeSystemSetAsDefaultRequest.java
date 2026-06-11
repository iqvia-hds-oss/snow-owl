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

import com.b2international.index.query.Expressions;
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
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import jakarta.validation.constraints.NotEmpty;

/**
 * Sets the specified code system as the FHIR default for its URL. Any other
 * code system sharing the same effective FHIR URL that currently carries
 * <code>fhirUseAsDefault = "true"</code> is set to <code>"false"</code> so the
 * flag doesn't silently disappear.
 * <p>
 * Version documents for all modified code systems are updated to propagate the setting.
 *
 * @since 10.2.0
 */
final class FhirCodeSystemSetAsDefaultRequest implements Request<TransactionContext, Boolean>, AccessControl {

	private static final long serialVersionUID = 1L;

	@NotEmpty
	@JsonProperty
	private final String codeSystemId;

	private transient List<CodeSystem> siblingsToUnset;

	FhirCodeSystemSetAsDefaultRequest(final String codeSystemId) {
		this.codeSystemId = codeSystemId;
	}

	private CodeSystem getCodeSystem(final ServiceProvider context) {
		return CodeSystemRequests.prepareGetCodeSystem(codeSystemId)
			.setFields(
				ResourceDocument.Fields.ID,
				ResourceDocument.Fields.RESOURCE_TYPE,
				ResourceDocument.Fields.BUNDLE_ID,
				ResourceDocument.Fields.BUNDLE_ANCESTOR_IDS,
				ResourceDocument.Fields.SETTINGS
			)
			.buildAsync()
			.getRequest()
			.execute(context);
	}
	
	private boolean getCurrentValue(final Map<String, Object> settings) {
		final String currentValue = Optional.ofNullable(settings)
			.map(s -> (String) s.get(TerminologyResource.Settings.FHIR_USE_AS_DEFAULT))
			.orElse("false");
	
		return "true".equals(currentValue);
	}

	private String getEffectiveFhirUrl(final CodeSystem resource) {
		return Optional.ofNullable(resource.getSettings())
			.map(settings -> (String) settings.get(TerminologyResource.Settings.FHIR_URL))
			.filter(fhirUrl -> !Strings.isNullOrEmpty(fhirUrl))
			.orElse(resource.getUrl());
	}

	private List<CodeSystem> getSiblingsToUnset(final CodeSystem resource, final ServiceProvider context) {
		if (siblingsToUnset == null) {
			final String codeSystemId = resource.getId();
			final String toolingId = resource.getToolingId();
			final String effectiveFhirUrl = getEffectiveFhirUrl(resource);

			siblingsToUnset = CodeSystemRequests.prepareSearchCodeSystem()
				.filterByToolingId(toolingId)
				.filterBySettings(List.of(
					Expressions.toDynamicFieldFilter(TerminologyResource.Settings.FHIR_URL, effectiveFhirUrl),
					Expressions.toDynamicFieldFilter(TerminologyResource.Settings.FHIR_USE_AS_DEFAULT, "true")
				))
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
				.filter(sibling -> !sibling.getId().equals(codeSystemId))
				.collect(Collectors.toList());
		}

		return siblingsToUnset;
	}

	@Override
	public String getOperation() {
		return Permission.OPERATION_EDIT;
	}

	@Override
	public List<Permission> getPermissions(final ServiceProvider context, final Request<ServiceProvider, ?> req) {
		final CodeSystem codeSystem = getCodeSystem(context);
		final List<Permission> permissions = new ArrayList<>();
		
		final Set<String> uniqueUris = new HashSet<>();
		uniqueUris.add(codeSystem.getResourceURI().getUri());
		uniqueUris.add(codeSystem.getResourceURI().withoutResourceType());
		uniqueUris.add(codeSystem.getBundleId());
		uniqueUris.addAll(codeSystem.getBundleAncestorIds());
		uniqueUris.remove(IComponent.ROOT_ID);
		
		// OR-combine all relevant URIs for a single permission
		permissions.add(Permission.requireAny(getOperation(), uniqueUris));
		
		// If the code system is already set as default, we can skip collecting sibling resources because no changes will be made to them
		if (getCurrentValue(codeSystem.getSettings())) {
			return permissions;
		}

		// Otherwise the user needs to have edit permission for all siblings that will be updated to unset the flag
		final List<CodeSystem> siblingsToUnset = getSiblingsToUnset(codeSystem, context);
		for (final CodeSystem sibling : siblingsToUnset) {
			final Set<String> uniqueSiblingUris = new HashSet<>();
			uniqueSiblingUris.add(sibling.getResourceURI().getUri());
			uniqueSiblingUris.add(sibling.getResourceURI().withoutResourceType());
			uniqueSiblingUris.add(sibling.getBundleId());
			uniqueSiblingUris.addAll(sibling.getBundleAncestorIds());
			uniqueSiblingUris.remove(IComponent.ROOT_ID);
			
			permissions.add(Permission.requireAny(getOperation(), uniqueSiblingUris));
		}
		
		// AND-combine permissions for all resources for the request
		return permissions;
	}
	
	@Override
	public void collectAccessedResources(final ServiceProvider context, final Request<ServiceProvider, ?> req, final List<String> accessedResources) {
		throw new UnsupportedOperationException("Access control is handled by getPermissions() in this request");
	}

	private static Map<String, Object> toMutableMap(final Map<String, Object> settings) {
		return new HashMap<>(settings != null ? settings : Map.of());
	}

	private void updateVersionDocument(final TransactionContext context, final String versionId, final String newValue) {
		final VersionDocument versionDocument = context.lookup(versionId, VersionDocument.class);
	
		// Skip if the setting value is already correct
		final Map<String, Object> versionSettings = versionDocument.getSettings();
		final String currentValue;
	
		if (versionSettings != null) {
			currentValue = (String) versionSettings.get(TerminologyResource.Settings.FHIR_USE_AS_DEFAULT);
		} else {
			currentValue = null;
		}
	
		if (newValue.equals(currentValue)) {
			return;
		}
	
		final Map<String, Object> newVersionSettings = toMutableMap(versionSettings);
		newVersionSettings.put(TerminologyResource.Settings.FHIR_USE_AS_DEFAULT, newValue);
	
		/*
		 * XXX: We are using context.add() instead of context.update() because
		 * VersionDocument is not a true revision document. Since the version identifier
		 * is preserved, the old document should be replaced with the updated one in
		 * such cases.
		 */
		context.add(VersionDocument.builder(versionDocument)
			.settings(newVersionSettings)
			.build());
	}

	@Override
	public Boolean execute(final TransactionContext context) {
		// Get the code system to be updated and its effective FHIR URL
		final CodeSystem codeSystem = getCodeSystem(context);

		// Check if the code system is already set as default, in which case we can skip the update without making changes to the index
		if (getCurrentValue(codeSystem.getSettings())) {
			return Boolean.FALSE;
		}

		// Collect any siblings where the "use as default" setting needs to be unset
		final List<CodeSystem> siblingsToUnset = getSiblingsToUnset(codeSystem, context);

		// Collect all modified resource IDs (the target code system and any siblings)
		final Set<String> allModifiedIds = new HashSet<>();
		allModifiedIds.add(codeSystem.getId());
		siblingsToUnset.forEach(sibling -> allModifiedIds.add(sibling.getId()));

		context.ensurePresent(ResourceDocument.class, allModifiedIds);

		// Pre-fetch version IDs for all modified resources (the full VersionDocument
		// will be looked up inside the transaction using the version's ID)
		final Multimap<String, String> versionIdsByResourceId = ResourceRequests.prepareSearchVersion()
			.filterByResources(allModifiedIds) // TODO: check if this filter actually accepts resource identifiers
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

		// Set FHIR_USE_AS_DEFAULT="true" on the target code system
		final ResourceDocument targetResource = context.lookup(codeSystemId, ResourceDocument.class);
		final Map<String, Object> targetSettings = targetResource.getSettings();
		final Map<String, Object> newTargetSettings = toMutableMap(targetSettings);
		newTargetSettings.put(TerminologyResource.Settings.FHIR_USE_AS_DEFAULT, "true");
		
		context.update(targetResource, ResourceDocument.builder(targetResource)
			.settings(newTargetSettings)
			.build());

		/*
		 * Set FHIR_USE_AS_DEFAULT="false" on any sibling code systems that had "true"
		 * (don't remove it entirely so we can keep track of the fact that they were
		 * previously set as default)
		 */
		for (final TerminologyResource sibling : siblingsToUnset) {
			final ResourceDocument siblingDocument = context.lookup(sibling.getId(), ResourceDocument.class);
			final Map<String, Object> siblingSettings = siblingDocument.getSettings();
			final Map<String, Object> newSiblingSettings = toMutableMap(siblingSettings);
			newSiblingSettings.put(TerminologyResource.Settings.FHIR_USE_AS_DEFAULT, "false");

			context.update(siblingDocument, ResourceDocument.builder(siblingDocument)
				.settings(newSiblingSettings)
				.build());
		}

		// Prefetch all version documents to avoid multiple lookups below (this can't be ensurePresent because that only works with Revisions)
		context.lookup(versionIdsByResourceId.values(), VersionDocument.class);

		// Propagate FHIR_USE_AS_DEFAULT to version documents of all modified resources
		for (final String modifiedId : allModifiedIds) {
			final String newValue = codeSystemId.equals(modifiedId) ? "true" : "false";
			final Collection<String> versionIds = versionIdsByResourceId.get(modifiedId);

			for (final String versionId : versionIds) {
				updateVersionDocument(context, versionId, newValue);
			}
		}

		return Boolean.TRUE;
	}
}
