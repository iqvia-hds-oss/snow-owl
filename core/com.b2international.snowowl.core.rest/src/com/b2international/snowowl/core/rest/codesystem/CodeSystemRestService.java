/*
 * Copyright 2011-2025 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.rest.codesystem;

import java.util.concurrent.TimeUnit;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.b2international.commons.exceptions.NotFoundException;
import com.b2international.snowowl.core.codesystem.CodeSystem;
import com.b2international.snowowl.core.codesystem.CodeSystemRequests;
import com.b2international.snowowl.core.codesystem.CodeSystems;
import com.b2international.snowowl.core.events.util.Promise;
import com.b2international.snowowl.core.request.ResourceRequests;
import com.b2international.snowowl.core.rest.AbstractRestService;
import com.b2international.snowowl.core.rest.CoreApiConfig;
import com.b2international.snowowl.core.rest.domain.ResourceSelectors;
import com.b2international.snowowl.core.rest.resource.TerminologyResourceRestSearch;
import com.google.common.base.Strings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @since 1.0
 */
@Tag(description = "CodeSystems", name = CoreApiConfig.CODESYSTEMS)
@RestController
@RequestMapping("/codesystems")
public class CodeSystemRestService extends AbstractRestService {

	@Operation(
		summary="Retrieve a list of code systems", 
		description="""
			Returns a collection resource containing all/filtered registered code systems.
			Results are sorted by ID by default.
			
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
	public Promise<CodeSystems> searchByGet(@ParameterObject final TerminologyResourceRestSearch params) {
		
		return CodeSystemRequests.prepareSearchCodeSystem()
			.filterByIds(params.getId())
			.filterByOids(params.getOid())
			.filterByUrls(params.getUrl())
			.filterByTitle(params.getTitle())
			.filterByTitleExact(params.getTitleExact())
			.filterByToolingIds(params.getToolingId())
			.filterByBundleIds(params.getBundleId())
			.filterByBundleAncestorIds(params.getBundleAncestorId())
			.filterByStatus(params.getStatus())
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
		summary="Retrieve a list of code systems", 
		description="""
			Returns a collection resource containing all/filtered registered code systems.
			Results are sorted by ID by default.
			
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
	@PostMapping(value="/search", produces = { AbstractRestService.JSON_MEDIA_TYPE })
	public Promise<CodeSystems> searchByPost(@RequestBody(required = false) final TerminologyResourceRestSearch params) {
		return searchByGet(params);
	}

	@Operation(
		summary="Retrieve a code system by id",
		description="Returns metadata information about a single code system associated with the given unique identifier."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "OK"),
		@ApiResponse(responseCode = "404", description = "Not found")
	})
	@GetMapping(value = "/{id}", produces = { AbstractRestService.JSON_MEDIA_TYPE })
	public Promise<CodeSystem> get(
		@Parameter(description="The code system identifier")
		@PathVariable(value="id", required = true) 
		final String id,
		
		@ParameterObject
		final ResourceSelectors selectors) {
		
		return CodeSystemRequests.prepareGetCodeSystem(CodeSystem.uri(id))
			.setExpand(selectors.getExpand())
			.setFields(selectors.getField())
			.buildAsync()
			.execute(getBus());
	}
	
	@Operation(
		summary="Retrieve a versioned code system by id and version",
		description="Returns metadata information about a single code system associated with the given unique identifier and version."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "OK"),
		@ApiResponse(responseCode = "404", description = "Not found")
	})
	@GetMapping(value = "/{id}/{versionId}", produces = { AbstractRestService.JSON_MEDIA_TYPE })
	public Promise<CodeSystem> getVersioned(
		@Parameter(description="The code system identifier")
		@PathVariable(value="id", required = true) 
		final String id,
		
		@Parameter(description="The code system version")
		@PathVariable(value="versionId", required = true) 
		final String versionId,
		
		@ParameterObject
		final ResourceSelectors selectors) {
		
		return CodeSystemRequests.prepareGetCodeSystem(CodeSystem.uri(id, versionId))
			.setExpand(selectors.getExpand())
			.setFields(selectors.getField())
			.buildAsync()
			.execute(getBus());
	}
	
	@Operation(
		summary="Create a code system",
		description="Create a new Code System with the given parameters"
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
		final CodeSystemRestCreate body,
		
		@RequestHeader(value = X_AUTHOR, required = false)
		final String author) {

		
		final String commitComment = Strings.isNullOrEmpty(body.getCommitComment()) ? String.format("Created new Code System %s", body.getId()) : body.getCommitComment();
		final String codeSystemId = body.toCreateRequest()
				.build(author, commitComment)
				.execute(getBus())
				.getSync(COMMIT_TIMEOUT, TimeUnit.MINUTES)
				.getResultAs(String.class);
		
		return ResponseEntity.created(getResourceLocationURI(codeSystemId)).build();
	}
	
	@Operation(
		summary = "Update a code system by id",
		description = "Update a code system with the given parameters"
	)
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "No content"),
		@ApiResponse(responseCode = "400", description = "Bad Request")
	})
	@PutMapping(value = "/{id}", consumes = { AbstractRestService.JSON_MEDIA_TYPE })
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void update(
			@Parameter(description = "The code system identifier")
			@PathVariable(value="id") 
			final String id,
			
			@RequestBody
			final CodeSystemUpdateRestInput body,
			
			@RequestHeader(value = X_AUTHOR, required = false)
			final String author) {
		final String commitComment = Strings.isNullOrEmpty(body.getCommitComment()) ? String.format("Updated Code System %s", id) : body.getCommitComment();
		body.toCodeSystemUpdateRequest(id)
				.build(author, commitComment)
				.execute(getBus())
				.getSync(COMMIT_TIMEOUT, TimeUnit.MINUTES);
	}
	
	@Operation(
			summary="Delete a code system by id",
			description="""
				The associated content branch will be marked deleted.
			""")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "No content"),
		@ApiResponse(responseCode = "400", description = "Bad Request"),
		@ApiResponse(responseCode = "409", description = "CodeSystem cannot be deleted")
	})
	@DeleteMapping(value = "/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(
			@Parameter(description = "The code system identifier")
			@PathVariable(value="id") 
			final String id,
			
			@RequestHeader(value = X_AUTHOR, required = false)
			final String author) {
		try {
			final CodeSystem codeSystem = CodeSystemRequests.prepareGetCodeSystem(id)
					.buildAsync()
					.execute(getBus())
					.getSync(1, TimeUnit.MINUTES);
			
			ResourceRequests.prepareDelete(codeSystem.getResourceURI())
				.build(author, String.format("Deleted Code System %s", codeSystem.getTitle()))
				.execute(getBus())
				.getSync(COMMIT_TIMEOUT, TimeUnit.MINUTES);
		} catch(NotFoundException e) {
			// already deleted, ignore error
		}
	}
	
}
