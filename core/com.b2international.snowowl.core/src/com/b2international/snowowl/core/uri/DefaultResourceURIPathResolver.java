/*
 * Copyright 2020-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.uri;

import java.util.*;
import java.util.stream.Collectors;

import com.b2international.commons.CompareUtils;
import com.b2international.commons.collections.Collections3;
import com.b2international.commons.exceptions.BadRequestException;
import com.b2international.index.revision.RevisionIndex;
import com.b2international.snowowl.core.Resource;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.TerminologyResource;
import com.b2international.snowowl.core.branch.Branch;
import com.b2international.snowowl.core.request.ResourceRequests;
import com.b2international.snowowl.core.request.SearchResourceRequest;
import com.b2international.snowowl.core.request.resource.BaseResourceSearchRequest.ResourceHiddenFilter;
import com.b2international.snowowl.core.request.version.VersionSearchRequestBuilder;
import com.b2international.snowowl.core.version.Version;
import com.b2international.snowowl.core.version.VersionDocument;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

/**
 * @since 7.12
 */
public final class DefaultResourceURIPathResolver implements ResourceURIPathResolver {
	
	@VisibleForTesting
	/*package*/ static interface VersionSearcher {
		Optional<Version> findVersion(ResourceURI resourceUri, ResourceURI uriToResolve, ServiceProvider context);
		
		VersionSearcher DEFAULT = (resourceUri, uriToResolve, context) -> {
			final VersionSearchRequestBuilder versionSearch = ResourceRequests.prepareSearchVersion()
				.one()
				.filterByResource(resourceUri)
				.setFields(VersionDocument.Fields.ID, VersionDocument.Fields.BRANCH_PATH, VersionDocument.Fields.RESOURCE, VersionDocument.Fields.VERSION);
				
			if (uriToResolve.isLatest()) {
				// fetch the latest resource version if LATEST is specified in the URI
				versionSearch.sortBy(SearchResourceRequest.Sort.fieldDesc(VersionDocument.Fields.EFFECTIVE_TIME));
			} else {
				// try to fetch the path as exact version if not the special LATEST is specified in the URI
				versionSearch.filterByVersionId(uriToResolve.getPath());
			}
			
			return versionSearch.buildAsync()
				.getRequest()
				.execute(context)
				.first();
		};
	}
	
	private final List<TerminologyResourceURIPathResolver> terminologyResourceURIPathResolvers;
	private final boolean allowBranches;
	private final VersionSearcher versionSearcher;
	
	public DefaultResourceURIPathResolver(
		final Collection<TerminologyResourceURIPathResolver> terminologyResourceURIPathResolvers, 
		final boolean allowBranches
	) {
		this(terminologyResourceURIPathResolvers, allowBranches, VersionSearcher.DEFAULT);
	}

	@VisibleForTesting
	/*package*/ DefaultResourceURIPathResolver(
		final Collection<TerminologyResourceURIPathResolver> terminologyResourceURIPathResolvers,
		final boolean allowBranches,
		final VersionSearcher versionSearcher
	) {
		this.terminologyResourceURIPathResolvers = Collections3.toImmutableList(terminologyResourceURIPathResolvers);
		this.allowBranches = allowBranches;
		this.versionSearcher = versionSearcher;
	}
	
	@Override
	public boolean isSpecialURI(ResourceURI resourceUri) {
		return resourceUri.isLatest() || terminologyResourceURIPathResolvers.stream().anyMatch(r -> r.canResolve(resourceUri));
	}
	
	@Override
	public List<String> resolve(ServiceProvider context, List<ResourceURI> codeSystemURIs) {
		if (CompareUtils.isEmpty(codeSystemURIs)) {
			return Collections.emptyList();
		}
		
		final Set<String> resourceIds = codeSystemURIs.stream()
			.map(ResourceURI::getResourceId)
			.collect(Collectors.toSet());
		
		// XXX: We fetch the most recent resource state for the identifier portions found in the input URIs
		final Map<String, Resource> resourcesById = ResourceRequests.prepareSearch()
			.filterByIds(resourceIds)
			// make sure we fetch both visible and hidden resources as well, so that URI resolution works correctly
			.filterByHidden(ResourceHiddenFilter.ALL)
			.buildAsync()
			.getRequest()
			.execute(context)
			.stream()
			.collect(Collectors.toMap(r -> r.getId(), r -> r));
		
		return codeSystemURIs.stream()
			// the default implementation will land in resolveWithVersion below and uses the path from the response
			.map(uri -> resolve(context, uri, resourcesById.get(uri.getResourceId())))
			.collect(Collectors.toList());
	}

	@Override
	public PathWithVersion resolveWithVersion(ServiceProvider context, ResourceURI uri, Resource resource) {
		
		if (uri.getPath() != null && RevisionIndex.isRevRangePath(uri.getPath())) {
			final String[] pathsToCheck = RevisionIndex.getRevisionRangePaths(uri.getPath());
			
			if (pathsToCheck.length != 2) {
				throw new BadRequestException("Invalid resource path range in URI '%s'.", uri.getUri());
			}
				
			for (final String currentPath : pathsToCheck) {
				if (Strings.isNullOrEmpty(currentPath)) {
					throw new BadRequestException("Invalid resource path range in URI '%s'.", uri.getUri());
				}
			}
			
			// Remove timestamp part for the first URI (the first path segment currently can not have a timestamp)
			final ResourceURI firstUri = uri.withTimestampPart("").withPath(pathsToCheck[0]);
			// Keep timestamp part for the second URI
			final ResourceURI secondUri = uri.withPath(pathsToCheck[1]);
			
			final String resolvedRangePath = RevisionIndex.toRevisionRange(
				resolveWithVersionSingle(context, firstUri, resource).getPath(),
				resolveWithVersionSingle(context, secondUri, resource).getPath()
			);
					
			return new PathWithVersion(resolvedRangePath);
		}
		
		return resolveWithVersionSingle(context, uri, resource);
	}

	private PathWithVersion resolveWithVersionSingle(ServiceProvider context, ResourceURI uri, Resource resource) {
		if (!(resource instanceof TerminologyResource terminologyResource)) {
			return new PathWithVersion("");
		}
		
		// convert special URIs to regular ones (the special identifier moves to a path segment)
		final ResourceURI uriToResolve = uri.toRegularURI();
			
		// use code system working branch directly when HEAD is specified
		if (Strings.isNullOrEmpty(uriToResolve.getPath()) || uriToResolve.isHead()) {
			return getResourceHeadBranch(uriToResolve, terminologyResource);
		}
			
		final String relativeBranchPath = terminologyResource.getRelativeBranchPath(uriToResolve.getPath());
		
		// prevent running special URI resolution if path is not a single path segment
		// XXX: this may fail to resolve nested branches which are not on the current working branch
		if (uriToResolve.getPath().contains(Branch.SEPARATOR)) {
			final String absoluteBranchPath = relativeBranchPath + uriToResolve.getTimestampPart();
			return new PathWithVersion(absoluteBranchPath);
		}
			
		// perform URI resolution for special single path segments 
		// (note that if a special ID part is present, it has been converted to a path in toRegularURI)
		// first use the plugged in URI resolvers 
		for (TerminologyResourceURIPathResolver resolver : terminologyResourceURIPathResolvers) {
			if (resolver.canResolve(uriToResolve)) {
				return resolver.resolve(context, uriToResolve, terminologyResource);
			}
		}
			
		// if based on the version search we find a version, then use that, otherwise use the defined path 
		// as relative branch of the code system working branch
		Optional<Version> resolvedVersion = versionSearcher.findVersion(terminologyResource.getResourceURI(), uriToResolve, context);

		return resolvedVersion.map(v -> {
			final String versionBranchPath = v.getBranchPath() + uriToResolve.getTimestampPart();
			final ResourceURI versionResourceURI = v.getVersionResourceURI().withTimestampPart(uriToResolve.getTimestampPart());
			return new PathWithVersion(versionBranchPath, versionResourceURI);
		})
		.orElseGet(() -> {

			// LATEST should be using the latest version of the resource, but there aren't any
			if (uriToResolve.isLatest()) {
				if (Resource.STATUS_DRAFT.equals(terminologyResource.getStatus())) {
					// exception is granted to look at the working branch of the resource if it is in DRAFT status
					return getResourceHeadBranch(uriToResolve, terminologyResource);
				} else {
					// otherwise this becomes a bad request, there is no version to resolve
					throw new BadRequestException("No Resource version is present in '%s'. Explicit '%s' can be used to retrieve the latest work in progress version of the Resource.", 
						terminologyResource.getId(), ResourceURI.HEAD);
				}
			}
			
			if (!allowBranches) {
				throw new BadRequestException("No Resource version is present in '%s' with ID '%s' and branches are not allowed.", 
					terminologyResource.getId(), uriToResolve.getPath());
			}
			
			// Single-segment paths are allowed, so treat this path the same way as a multi-segment path above
			final String absoluteBranchPath = relativeBranchPath + uriToResolve.getTimestampPart();
			return new PathWithVersion(absoluteBranchPath); 
		});
	}

	private PathWithVersion getResourceHeadBranch(ResourceURI uriToResolve, TerminologyResource terminologyResource) {
		final String workingPath = terminologyResource.getBranchPath() + uriToResolve.getTimestampPart();
		return new PathWithVersion(workingPath);
	}
}
