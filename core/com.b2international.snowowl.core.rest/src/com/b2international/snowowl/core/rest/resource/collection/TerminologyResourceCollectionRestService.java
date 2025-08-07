/*
 * Copyright 2023-2025 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.rest.resource.collection;

import java.util.concurrent.TimeUnit;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.b2international.commons.exceptions.NotFoundException;
import com.b2international.snowowl.core.collection.TerminologyResourceCollection;
import com.b2international.snowowl.core.collection.TerminologyResourceCollectionRequests;
import com.b2international.snowowl.core.collection.TerminologyResourceCollections;
import com.b2international.snowowl.core.events.util.Promise;
import com.b2international.snowowl.core.rest.AbstractRestService;
import com.b2international.snowowl.core.rest.domain.ResourceSelectors;
import com.b2international.snowowl.core.rest.resource.ResourceRestUpdate;
import com.b2international.snowowl.core.rest.resource.TerminologyResourceRestSearch;
import com.google.common.base.Strings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @since 9.0.0
 */
@Tag(description = "Collections", name = "collections")
@RestController
@RequestMapping(value = "/collections")
public class TerminologyResourceCollectionRestService extends AbstractRestService {

	public TerminologyResourceCollectionRestService() {
		super(TerminologyResourceCollection.Fields.ALL);
	}
	
	@Operation(
		summary = "Retrieve a list of terminology resource collections", 
		description = """
			Returns a collection resource containing all/filtered registered terminology resource collections. Results are sorted by ID by default.
			
			The following additional data can be expanded:
			* `versions` - a list of versions created so far for a resource 
			* `commits` - a list of commits pushed to the repository changing a resource's contents
			* `branch` - the current working branch state from the underlying revision control repository
			* `dependencies_upgrades()` - expands the possible upgrade versions available for each dependency reference
			* `dependencies_resource()` - expands the resource of each dependency reference
		"""
	)
	@ApiResponses({ 
		@ApiResponse(responseCode = "200", description = "OK"),
		@ApiResponse(responseCode = "400", description = "Bad Request") 
	})
	@GetMapping(produces = { AbstractRestService.JSON_MEDIA_TYPE })
	public Promise<TerminologyResourceCollections> searchByGet(@ParameterObject final TerminologyResourceRestSearch params) {
		return TerminologyResourceCollectionRequests.prepareSearch()
			.filterByIds(params.getId())
			.filterByTitleExact(params.getTitleExact())
			.filterByTitle(params.getTitle())
			.filterByOids(params.getOid())
			.filterByToolingIds(params.getToolingId())
			.filterByBundleIds(params.getBundleId())
			.filterByBundleAncestorIds(params.getBundleAncestorId())
			.filterByStatus(params.getStatus())
			.filterByUrls(params.getUrl())
			.filterByOwner(params.getOwner())
			.filterBySettings(params.getSettings())
			.filterByDependency(params.getDependency())
			.setLimit(params.getLimit())
			.setExpand(params.getExpand())
			.setFields(params.getField())
			.setSearchAfter(params.getSearchAfter())
			.sortBy(extractSortFields(params.getSort()))
			.buildAsync(params.getTimestamp())
			.execute(getBus());
	}
	
	@Operation(
		summary = "Retrieve a list of terminology resource collections", 
		description = """
			Returns a collection resource containing all/filtered registered terminology resource collection. Results are sorted by ID by default.
			
			The following additional data can be expanded:
			* `versions` - a list of versions created so far for a resource 
			* `commits` - a list of commits pushed to the repository changing a resource's contents
			* `branch` - the current working branch state from the underlying revision control repository
			* `dependencies_upgrades()` - expands the possible upgrade versions available for each dependency reference
			* `dependencies_resource()` - expands the resource of each dependency reference
		"""
	)
	@ApiResponses({ 
		@ApiResponse(responseCode = "200", description = "OK"),
		@ApiResponse(responseCode = "400", description = "Bad Request") 
	})
	@PostMapping(value = "/search", consumes = { AbstractRestService.JSON_MEDIA_TYPE }, produces = { AbstractRestService.JSON_MEDIA_TYPE })
	public Promise<TerminologyResourceCollections> searchByPost(@RequestBody(required = false) final TerminologyResourceRestSearch params) {
		return searchByGet(params);
	}
	
	@Operation(
		summary = "Retrieve a terminology resource collection by id", 
		description = "Returns metadata information about a single terminology resource collection associated with the given unique identifier."
	)
	@ApiResponses({ 
		@ApiResponse(responseCode = "200", description = "OK"),
		@ApiResponse(responseCode = "400", description = "Bad Request"), 
		@ApiResponse(responseCode = "404", description = "Not Found") 
	})
	@GetMapping(value = "/{id}", produces = { AbstractRestService.JSON_MEDIA_TYPE })
	public Promise<TerminologyResourceCollection> get(
		@Parameter(description = "The collection resource identifier") 
		@PathVariable(value = "id") 
		final String id,
		
		@ParameterObject
		final ResourceSelectors selectors) {
		
		return TerminologyResourceCollectionRequests
			.prepareGet(id)
			.setExpand(selectors.getExpand())
			.setFields(selectors.getField())
			.buildAsync()
			.execute(getBus());
	}
	
	@Operation(
		summary="Retrieve a versioned terminology resource collection by id and version",
		description="Returns metadata information about a single terminology resource collection associated with the by the given unique identifier."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "OK"),
		@ApiResponse(responseCode = "404", description = "Not found")
	})
	@GetMapping(value = "/{id}/{versionId}", produces = { AbstractRestService.JSON_MEDIA_TYPE })
	public Promise<TerminologyResourceCollection> getVersioned(
		@Parameter(description="The resource collection identifier")
		@PathVariable(value="id", required = true) 
		final String id,
		
		@Parameter(description="The resource collection version")
		@PathVariable(value="versionId", required = true) 
		final String versionId,
		
		@ParameterObject
		final ResourceSelectors selectors) {
		
		return TerminologyResourceCollectionRequests.prepareGet(TerminologyResourceCollection.uri(id, versionId))
			.setExpand(selectors.getExpand())
			.setFields(selectors.getField())
			.buildAsync()
			.execute(getBus());
	}
	
	@Operation(
		summary="Create a terminology resource collection",
		description="Create a new Terminology Resource Collection with the given parameters"
	)
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "Created"),
		@ApiResponse(responseCode = "400", description = "Bad Request"),
		@ApiResponse(responseCode = "409", description = "Already exists")
	})
	@PostMapping(consumes = { AbstractRestService.JSON_MEDIA_TYPE })
	@ResponseStatus(HttpStatus.CREATED)
	public ResponseEntity<Void> create(
		@RequestBody
		final TerminologyResourceCollectionRestCreate body,
		
		@RequestHeader(value = X_AUTHOR, required = false)
		final String author) {

		
		final String commitComment = Strings.isNullOrEmpty(body.getCommitComment()) ? String.format("Created new Resource Collection %s", body.getId()) : body.getCommitComment();
		final String collectionId = body.toCreateRequest()
				.build(author, commitComment)
				.execute(getBus())
				.getSync(COMMIT_TIMEOUT, TimeUnit.MINUTES)
				.getResultAs(String.class);
		
		return ResponseEntity.created(getResourceLocationURI(collectionId)).build();
	}
	
	@Operation(
		summary = "Update a terminology resource collection by id", 
		description = "Updates a terminology resource collection with the given parameters"
	)
	@ApiResponses({ 
		@ApiResponse(responseCode = "204", description = "No Content"),
		@ApiResponse(responseCode = "400", description = "Bad Request"), 
	})
	@PutMapping(value = "/{id}", consumes = { AbstractRestService.JSON_MEDIA_TYPE })
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void update(
			@Parameter(description = "The collection resource identifier") 
			@PathVariable(value = "id") 
			final String id,
			
			@RequestBody
			final ResourceRestUpdate body,
			
			@RequestHeader(value = X_AUTHOR, required = false)
			final String author) {
		final String commitComment = Strings.isNullOrEmpty(body.getCommitComment()) ? String.format("Updated Terminology Collection Resource %s", id) : body.getCommitComment();
		body.toUpdateRequest(id)
				.build(author, commitComment)
				.execute(getBus())
				.getSync(COMMIT_TIMEOUT, TimeUnit.MINUTES);
	}
	
	@Operation(
			summary="Delete a terminology resource collection by id",
			description="""
				If there is an associated content branch, then that will be marked deleted. NOTE: The contained resources are currently not deleted.
			""")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "No content"),
		@ApiResponse(responseCode = "400", description = "Bad Request"),
		@ApiResponse(responseCode = "409", description = "Resource cannot be deleted")
	})
	@DeleteMapping(value = "/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(
			@Parameter(description = "The collection resource identifier")
			@PathVariable(value="id") 
			final String id,
			
			@RequestHeader(value = X_AUTHOR, required = false)
			final String author) {
		try {
			final TerminologyResourceCollection resource = TerminologyResourceCollectionRequests.prepareGet(id)
					.buildAsync()
					.execute(getBus())
					.getSync(1, TimeUnit.MINUTES);
			
			TerminologyResourceCollectionRequests.prepareDelete(id)
				.build(author, String.format("Deleted resource %s", resource.getTitle()))
				.execute(getBus())
				.getSync(COMMIT_TIMEOUT, TimeUnit.MINUTES);
		} catch(NotFoundException e) {
			// already deleted, ignore error
		}
	}
	
}
