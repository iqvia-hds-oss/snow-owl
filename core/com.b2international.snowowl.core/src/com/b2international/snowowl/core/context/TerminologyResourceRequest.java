/*
 * Copyright 2021-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.context;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import com.b2international.commons.exceptions.BadRequestException;
import com.b2international.commons.exceptions.NotFoundException;
import com.b2international.index.revision.RevisionIndex;
import com.b2international.snowowl.core.*;
import com.b2international.snowowl.core.branch.Branch;
import com.b2international.snowowl.core.events.DelegatingRequest;
import com.b2international.snowowl.core.events.Request;
import com.b2international.snowowl.core.events.RequestInitializationRequired;
import com.b2international.snowowl.core.repository.PathTerminologyResourceResolver;
import com.b2international.snowowl.core.request.ResourceRequests;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import jakarta.validation.constraints.NotEmpty;

/**
 * @since 8.0
 * @param <R>
 */
public final class TerminologyResourceRequest<R> extends DelegatingRequest<ServiceProvider, TerminologyResourceContext, R> implements RequestInitializationRequired {

	private static final long serialVersionUID = 1L;
	
	@VisibleForTesting
	/*package*/ static interface ResourceFetcher extends BiFunction<ServiceProvider, ResourceURI, Resource> {
		ResourceFetcher DEFAULT = (ServiceProvider context, ResourceURI resourceUri) -> {
			return ResourceRequests.prepareGet(resourceUri)
				.buildAsync()
				.getRequest()
				.execute(context);
		};
	}
	
	private final String toolingId;
	
	@NotEmpty
	@JsonProperty
	private final String resourcePath;

	@JsonProperty
	private transient ResourceURI resourceUri;
	private transient TerminologyResource resource;

	private transient ResourceFetcher resourceFetcher;
	
	public TerminologyResourceRequest(
		final String toolingId, 
		final String resourcePath, 
		final Request<TerminologyResourceContext, R> next
	) {
		this(toolingId, resourcePath, next, ResourceFetcher.DEFAULT);
	}
	
	@VisibleForTesting
	/*package*/ TerminologyResourceRequest(
		final String toolingId,
		final String resourcePath,
		final Request<TerminologyResourceContext, R> next,
		final ResourceFetcher resourceFetcher
	) {
		validateResourcePath(resourcePath, toolingId);
		super(next);
		this.toolingId = toolingId;
		this.resourcePath = resourcePath;
		this.resourceFetcher = resourceFetcher;
	}

	private static void validateResourcePath(final String resourcePath, final String toolingId) {
		checkArgument(!Strings.isNullOrEmpty(resourcePath), "Resource path may not be null or empty");
		
		final String[] pathsToCheck = RevisionIndex.isRevRangePath(resourcePath)
			? RevisionIndex.getRevisionRangePaths(resourcePath)
			: new String[] { resourcePath };
		
		if (pathsToCheck.length < 1 || pathsToCheck.length > 2) {
			throw new BadRequestException("Invalid resource path '%s'.", resourcePath);
		}
		
		for (final String currentPath : pathsToCheck) {
			if (Strings.isNullOrEmpty(currentPath)) {
				throw new BadRequestException("Invalid resource path '%s'.", resourcePath);
			}
			
			if (currentPath.startsWith(Branch.MAIN_PATH) && Strings.isNullOrEmpty(toolingId)) {
				throw new BadRequestException("Reflective access ('repositoryId/path') to terminology resource content is not supported in this API.")
					.withDeveloperMessage("No toolingId is specified on API level to ensure the correct reflective access to underlying terminology.");
			}
		}
	}
	
	public ResourceURI getResourceURI(ServiceProvider context) {
		if (resourceUri == null) {
			initializeRequestContext(context);
		}
		return resourceUri;
	}
	
	@Override
	public R execute(ServiceProvider context) {
		final TerminologyResource resource = getResource(context);
		return next(new DefaultTerminologyResourceContext(context, resourceUri, resource));
	}
	
	public TerminologyResource getResource(ServiceProvider context) {
		if (resource == null) {
			initializeRequestContext(context);
		}
		return resource;
	}
	
	@Override
	public void initializeRequestContext(ServiceProvider context) {
		final String[] resourcePathsToCheck;
		
		if (RevisionIndex.isRevRangePath(resourcePath)) {
			resourcePathsToCheck = RevisionIndex.getRevisionRangePaths(resourcePath);
		} else {
			resourcePathsToCheck = new String[] { resourcePath };
		}
		
		final ResourceWithUri[] resolvedResources = new ResourceWithUri[resourcePathsToCheck.length];

		// for ranges the first path should reveal which resource we are working with
		// the second path should be either absolute or only contain a relative path 
		// (and we will not consider the first segment to be a resource ID)
		TerminologyResource candidate = null;
		
		for (int i = 0; i < resourcePathsToCheck.length; i++) {
			resolvedResources[i] = resolveResource(context, resourcePathsToCheck[i], candidate);
			candidate = resolvedResources[i].resource();
		}

		// we will use the last resource appearing in the iteration, since it might be resolving to a different point in time 
		this.resource = candidate;
		
		if (resolvedResources.length == 1) {
			this.resourceUri = resolvedResources[0].uri();
		} else {
			final String rangePath = Arrays.stream(resolvedResources)
				.map(rwu -> rwu.uri().getPath() + rwu.uri().getTimestampPart()) // include special ID part as well?
				.collect(Collectors.joining(RevisionIndex.REV_RANGE));
			
			this.resourceUri = ResourceURI.branch(resource.getResourceType(), resource.getId(), rangePath);
		}
	}
	
	private ResourceWithUri resolveResource(final ServiceProvider context, final String resourcePath, final TerminologyResource candidate) {

		// If we receive an absolute path, we will resolve it using PathTerminologyResourceResolver
		if (resourcePath.startsWith(Branch.MAIN_PATH)) {
			// XXX remove support for querying content via raw branch path in high-level API 
			context.log().warn("Reflective access of terminology resources ('{}/{}') is not the recommended way of accessing resources. Consider using Resource IDs and relative branch path expressions.", toolingId, resourcePath);
			
			final TerminologyResource currentResource = context.service(PathTerminologyResourceResolver.class).resolve(context, toolingId, resourcePath);

			// It's possible that two absolute paths resolve to different resources, in which case we will throw an exception
			if (candidate != null && !currentResource.getId().equals(candidate.getId())) {
				throw new BadRequestException("The resolved resource '%s' does not match the expected resource '%s'.", currentResource.getId(), candidate.getId());
			}
			
			final ResourceURI resolvedUri = currentResource.getResourceURI(resourcePath);
			
			return new ResourceWithUri(currentResource, resolvedUri);
		}
		
		// If candidate is not null, we are resolving the second path of a range which should be treated as a relative path; we also need to fetch the resource again
		if (candidate != null) {
			final ResourceURI referenceResourceUri = candidate.getResourceURI().withPath(resourcePath);
			final Resource fetchedResource = resourceFetcher.apply(context, referenceResourceUri);

			if (!(fetchedResource instanceof TerminologyResource terminologyResource)) {
				throw new NotFoundException("Terminology Resource", referenceResourceUri.getResourceId());
			}
			
			final ResourceURI resolvedUri = fetchedResource.getResourceURI()
				.withSpecialResourceIdPart(referenceResourceUri.getSpecialIdPart())
				.withPath(referenceResourceUri.getPath())
				.withTimestampPart(referenceResourceUri.getTimestampPart());
			
			return new ResourceWithUri(terminologyResource, resolvedUri);
		}
		
		// Otherwise we are resolving the first path of a range or a single path
		final ResourceURI referenceResourceUri = ResourceURI.of("any", resourcePath);
		TerminologyResource terminologyResource = null;
		
		// First check if a cached ResourceFragment is available in the context, if so, convert it to a TerminologyResource
		final ResourceFragment fragment = context.optionalService(ResourceFragment.class).orElse(null);
		if (fragment != null) {
			final ResourceTypeConverter.Registry registry = context.service(ResourceTypeConverter.Registry.class);
			final Resource convertedResource = registry.toResource(fragment);
			if (convertedResource instanceof TerminologyResource convertedTerminologyResource) {
				terminologyResource = convertedTerminologyResource;
			}
		}
		
		// Otherwise retrieve it using the resource fetcher
		if (terminologyResource == null) {
			final Resource fetchedResource = resourceFetcher.apply(context, referenceResourceUri);
			if (fetchedResource instanceof TerminologyResource fetchedTerminologyResource) {
				terminologyResource = fetchedTerminologyResource;
			}
		}
		
		// We have exhausted all options if the resource is still not found
		if (terminologyResource == null) {
			throw new NotFoundException("Terminology Resource", referenceResourceUri.getResourceId());
		}
		
		final ResourceURI resolvedUri = terminologyResource.getResourceURI()
			.withSpecialResourceIdPart(referenceResourceUri.getSpecialIdPart())
			.withPath(referenceResourceUri.getPath())
			.withTimestampPart(referenceResourceUri.getTimestampPart());
		
		return new ResourceWithUri(terminologyResource, resolvedUri);
	}

	private record ResourceWithUri(
		TerminologyResource resource, 
		ResourceURI uri
	) {
		// Empty record body
	}
	
	@VisibleForTesting
	/*package*/ ResourceFetcher getResourceFetcher() {
		return resourceFetcher;
	}
	
	private void readObject(final ObjectInputStream stream) throws IOException, ClassNotFoundException {
		// Use the default resource fetcher strategy for de-serialized instances
		stream.defaultReadObject();
		this.resourceFetcher = ResourceFetcher.DEFAULT;
	}
}
