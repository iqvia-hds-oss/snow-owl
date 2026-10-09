/*
 * Copyright 2023-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.repository;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import com.b2international.commons.ClassUtils;
import com.b2international.index.revision.RevisionIndex;
import com.b2international.index.revision.RevisionSearcher;
import com.b2international.index.revision.StagingArea;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.TerminologyResource;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.core.domain.RepositoryContext;
import com.b2international.snowowl.core.request.ResourceRequests;
import com.b2international.snowowl.core.request.SearchResourceRequest;
import com.b2international.snowowl.core.uri.ResourceURIPathResolver;
import com.b2international.snowowl.core.version.VersionDocument;
import com.google.common.collect.*;

/**
 * @since 8.10.1
 */
public abstract class EffectiveTimeChangeProcessorBase<T extends RevisionDocument> extends ChangeSetProcessorBase {

	private final Class<T> documentClass;
	private final Logger log;

	protected EffectiveTimeChangeProcessorBase(final String description, final Class<T> documentClass, final Logger log) {
		super(description);
		this.documentClass = documentClass;
		this.log = log;
	}
	
	@Override
	public void process(final StagingArea staging, final RevisionSearcher searcher) throws IOException {
		// Collect released components with an unset effective time
		final Multimap<Class<?>, T> componentsByType = ArrayListMultimap.create();
		
		staging.getChangedObjects()
			.filter(documentClass::isInstance)
			.map(documentClass::cast)
			.filter(doc -> isReleased(doc) && EffectiveTimes.isUnset(getEffectiveTime(doc)))
			.forEach(doc -> componentsByType.put(doc.getClass(), doc));

		if (componentsByType.isEmpty())	{
			return;
		}
		
		final List<String> availableVersionPaths = getAvailableVersionPaths(staging);
		if (availableVersionPaths.isEmpty()) {
			return;
		}

		final Set<Class<?>> componentTypes = ImmutableSet.copyOf(componentsByType.keySet());

		for (final String versionPath : availableVersionPaths) {
			for (final Class<?> componentType : componentTypes) {
				final Set<String> componentIdsForType = componentsByType.get(componentType)
					.stream()
					.map(T::getId)
					.collect(Collectors.toSet());
				
				final Map<String, T> previousComponentsById = Maps.uniqueIndex(
					fetchPreviousComponentRevisions(
						staging.getIndex(), 
						versionPath, 
						componentType, 
						componentIdsForType), 
					T::getId
				);
				
				// Guaranteed to be non-empty as componentTypes was derived from componentsByType.keySet()
				final List<? extends T> changedRevisions = ImmutableList.copyOf(componentsByType.get(componentType));
				
				for (final T changedRevision : changedRevisions) {
					final T previousVersion = previousComponentsById.get(changedRevision.getId());
					
					if (previousVersion != null) {
						// A previous version exists, no need to issue a warning for this component
						componentsByType.remove(componentType, changedRevision);
					} else {
						// No previous version found, component will be reported as released content without previous version at the end of this method
						continue;
					}
					
					final boolean canRestore = canRestoreEffectiveTime(changedRevision, previousVersion);
					if (canRestore && getEffectiveTime(changedRevision) < getEffectiveTime(previousVersion)) {
						/*
						 * Current component state matches versioned state, but effective time is
						 * smaller or unset (-1 is smaller than any valid effective time). "Roll forward" to 
						 * match the value stored in versioned state.
						 */
						final T restoredRevision = copyWithEffectiveTime(changedRevision, getEffectiveTime(previousVersion));
						stageChange(changedRevision, restoredRevision);
						continue;
					}
				}
			}
		}

		if (!componentsByType.isEmpty()) {
			log.warn("Released components found which do not have a previous version: {}.", componentsByType.values()
				.stream()
				.map(T::getId)
				.sorted()
				.collect(Collectors.toList()));
		}
	}

	protected abstract boolean isReleased(T doc);

	protected abstract Long getEffectiveTime(T doc);

	@SuppressWarnings("unchecked")
	private Iterable<T> fetchPreviousComponentRevisions(
		RevisionIndex index, 
		String versionPath,
		Class<?> componentType, 
		Set<String> componentIdsForType
	) {
		return (Iterable<T>) index.read(versionPath, searcher -> searcher.get(componentType, componentIdsForType));
	}
	
	protected abstract boolean canRestoreEffectiveTime(T changedRevision, T previousVersion);
	
	protected abstract T copyWithEffectiveTime(T changedRevision, long effectiveTime);

	protected List<String> getAvailableVersionPaths(final StagingArea staging) {
		/*
		 * Version / resource URIs and corresponding branch paths we are interested in:
		 * 
		 * - the latest version of the "extensionOf" CodeSystem
		 * - the "upgradeOf" CodeSystem itself
		 * - the latest version of the current CodeSystem, only if we are not on an upgrade CodeSystem
		 * 
		 * Some examples:
		 * 
		 * 1. SNOMEDCT-EXP -> extensionOf: SNOMEDCT 
		 *    - check latest* version of SNOMEDCT (SNOMEDCT/2026-08-01)
		 *    - check latest* version of SNOMEDCT-EXP
		 * 2. SNOMEDCT-US -> extensionOf: SNOMEDCT/2025-09-01 
		 *    - check the version indicated by extensionOf (SNOMEDCT/2025-09-01)
		 *    - check latest* version of SNOMEDCT-US
		 * 3. SNOMEDCT-US-UPL -> extensionOf: SNOMEDCT/2026-08-01, upgradeOf: SNOMEDCT-US/2025-09-01
		 *    - check the version indicated by extensionOf (SNOMEDCT/2026-08-01)
		 *    - check the version indicated by upgradeOf (SNOMEDCT-US/2025-09-01)
		 *    - don't check latest* version of SNOMEDCT-US nor SNOMEDCT-US-UPL because this is an upgrade CodeSystem
		 *    
		 * A more precise definition of "latest" would mean the latest version entry that is visible from 
		 * the current branch, taking all merge sources (not just branch bases) into account. This is not 
		 * trivial to determine however. Tasks will also need to be synchronized to the most recent state 
		 * before merging so there shouldn't be any issue with taking the latest version overall, but this
		 * is something to keep in mind for future improvements.
		 */
		final String branchPath = staging.getBranchPath();

		final Object stagingContext = staging.getContext();
		final RepositoryContext repositoryContext = ClassUtils.checkAndCast(stagingContext, RepositoryContext.class);
		final String toolingId = repositoryContext.info().id();
		
		// As this change processor now gets called during merges, we need an alternate way to resolve the current CodeSystem
		final TerminologyResource currentCodeSystem = repositoryContext.optionalService(TerminologyResource.class)
			.orElseGet(() -> repositoryContext.service(PathTerminologyResourceResolver.class)
				.resolve(repositoryContext, toolingId, branchPath));

		final List<ResourceURI> resourceUrisToCheck = Lists.newArrayList();
		
		final ResourceURI extensionOf = currentCodeSystem.getExtensionOf();
		if (extensionOf != null) {
			if (extensionOf.isHead()) {
				checkLatestVersion(resourceUrisToCheck, repositoryContext, extensionOf.withoutPath());
			} else {
				resourceUrisToCheck.add(extensionOf);
			}
		}
		
		final ResourceURI upgradeOf = currentCodeSystem.getUpgradeOf();
		if (upgradeOf != null) {
			// final long lastSyncTimestamp = ...? 
			//
			// if (upgradeOf.isHead()) {
			//     checkLatestVersion(resourceUrisToCheck, context, extensionOf.withoutPath(), lastSyncTimestamp); 
			// } else {
			//     resourceUrisToCheck.add(upgradeOf);
			// }
			
			resourceUrisToCheck.add(upgradeOf);
		}

		final ResourceURI codeSystemUri = currentCodeSystem.getResourceURI();
		if (upgradeOf == null) {
			checkLatestVersion(resourceUrisToCheck, repositoryContext, codeSystemUri.withoutPath());
		}
		
		return repositoryContext.service(ResourceURIPathResolver.class).resolve(repositoryContext, resourceUrisToCheck);
	}

	private void checkLatestVersion(
		final List<ResourceURI> codeSystemsToCheck, 
		final RepositoryContext context, 
		final ResourceURI resourceUri
	) {
		ResourceRequests.prepareSearchVersion()
			.one()
			.filterByResource(resourceUri)
			.sortBy(SearchResourceRequest.Sort.fieldDesc(VersionDocument.Fields.EFFECTIVE_TIME))
			.buildAsync()
			.get(context)
			.stream()
			.findFirst()
			.ifPresent(v -> codeSystemsToCheck.add(v.getVersionResourceURI()));
	}
}
