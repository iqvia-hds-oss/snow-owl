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

import java.util.Arrays;
import java.util.Optional;
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
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import jakarta.validation.constraints.NotEmpty;

/**
 * @since 8.0
 * @param <R>
 */
public final class TerminologyResourceRequest<R> extends DelegatingRequest<ServiceProvider, TerminologyResourceContext, R> implements RequestInitializationRequired {

	private static final long serialVersionUID = 1L;
	
	private final String toolingId;
	
	@NotEmpty
	@JsonProperty
	private final String resourcePath;

	@JsonProperty
	private transient ResourceURI resourceUri;
	private transient TerminologyResource resource;
	
	public TerminologyResourceRequest(final String toolingId, final String resourcePath, final Request<TerminologyResourceContext, R> next) {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(resourcePath), "Resource path may not be null or empty");
		if (resourcePath.startsWith(Branch.MAIN_PATH) && Strings.isNullOrEmpty(toolingId)) {
			throw new BadRequestException("Reflective access ('repositoryId/path') to terminology resource content is not supported in this API.")
			.withDeveloperMessage("No toolingId is specified on API level to ensure the correct reflective access to underlying terminology.");
		}
		super(next);
		this.toolingId = toolingId;
		this.resourcePath = resourcePath;
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
		
		// verify that all paths refer to the same resource
		final TerminologyResource[] resolvedResources = new TerminologyResource[resourcePathsToCheck.length];
		final ResourceURI[] resolvedUris = new ResourceURI[resourcePathsToCheck.length];

		TerminologyResource candidate = null;
		for (int i = 0; i < resourcePathsToCheck.length; i++) {
			final ResourceWithUri resolved = resolveResource(context, resourcePathsToCheck[i], candidate);
			candidate = resolved.resource;
			resolvedResources[i] = resolved.resource;
			resolvedUris[i] = resolved.uri;
		}

		// we will use the last resource appearing in the iteration 
		this.resource = candidate;
		
		if (resolvedUris.length == 1) {
			this.resourceUri = resolvedUris[0];
		} else {
			final String rangePath = Arrays.stream(resolvedUris)
				.map(uri -> uri.getPath() + uri.getTimestampPart())
				.collect(Collectors.joining(RevisionIndex.REV_RANGE));
			
			this.resourceUri = ResourceURI.branch(candidate.getResourceURI().getResourceType(), candidate.getId(), rangePath);
		}
	}
	
	private ResourceWithUri resolveResource(final ServiceProvider context, final String resourcePath, final TerminologyResource candidate) {
		final TerminologyResource currentResource;
		final ResourceURI resolvedUri;
		
		if (resourcePath.startsWith(Branch.MAIN_PATH)) {
			// XXX remove support for querying content via raw branch path in high-level API 
			context.log().warn("Reflective access of terminology resources ('{}/{}') is not the recommended way of accessing resources. Consider using Resource IDs and relative branch path expressions.", toolingId, resourcePath);
			currentResource = context.service(PathTerminologyResourceResolver.class).resolve(context, toolingId, resourcePath);
			resolvedUri = currentResource.getResourceURI(resourcePath);
		} else {
			// use the state of the resource at the given path
			final ResourceURI referenceResourceUri = ResourceURI.of("any", resourcePath);
			currentResource = context.optionalService(ResourceFragment.class)
				.flatMap(fragment -> {
					// if a fragment is cached then use it and prevent refetching existing data, but only for the first path parameter
					// TODO it would be better to support other model types as well, but this was the faster to get this done for 10.1.0
					if (candidate != null) {
						return Optional.empty();
					}
					
					final Resource convertedResource = context.service(ResourceTypeConverter.Registry.class)
						.toResource(fragment);
					
					if (convertedResource instanceof TerminologyResource terminologyResource) {
						return Optional.of(terminologyResource);
					} else {
						return Optional.empty();
					}
				})
				.or(() -> {
					// XXX intentionally not fetching using the full resourceUri here, this might change in the future
					final Resource fetchedResource = ResourceRequests.prepareGet(referenceResourceUri)
						.buildAsync()
						.getRequest()
						.execute(context);
					
					if (fetchedResource instanceof TerminologyResource terminologyResource) {
						return Optional.of(terminologyResource);
					} else {
						return Optional.empty();
					}
				})
				.orElseThrow(() -> new NotFoundException("Terminology Resource", referenceResourceUri.getResourceId()));
			
			resolvedUri = currentResource.getResourceURI()
				.withSpecialResourceIdPart(referenceResourceUri.getSpecialIdPart())
				.withPath(referenceResourceUri.getPath())
				.withTimestampPart(referenceResourceUri.getTimestampPart());
		}
		
		if (candidate != null && !candidate.getId().equals(currentResource.getId())) {
			throw new BadRequestException("All resource paths must refer to the same terminology resource. Found '%s' and '%s'.", candidate.getId(), currentResource.getId());
		}
		
		return new ResourceWithUri(currentResource, resolvedUri);
	}

	private record ResourceWithUri(
		TerminologyResource resource, 
		ResourceURI uri
	) {
		// Empty record body
	}
}
