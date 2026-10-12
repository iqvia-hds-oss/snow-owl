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
package com.b2international.snowowl.core.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.elasticsearch.core.List;
import org.junit.Before;
import org.junit.Test;

import com.b2international.commons.exceptions.BadRequestException;
import com.b2international.index.admin.IndexAdmin;
import com.b2international.index.es.admin.IndexMapping;
import com.b2international.index.mapping.Mappings;
import com.b2international.index.revision.BaseRevisionBranching;
import com.b2international.index.revision.Hooks;
import com.b2international.index.revision.RevisionIndex;
import com.b2international.index.revision.RevisionIndexAdmin;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.TerminologyResource;
import com.b2international.snowowl.core.codesystem.CodeSystem;
import com.b2international.snowowl.core.internal.ResourceRepository;
import com.b2international.snowowl.core.repository.PathTerminologyResourceResolver;
import com.b2international.snowowl.core.uri.DefaultResourceURIPathResolver;
import com.b2international.snowowl.core.uri.ResourceURIPathResolver.PathWithVersion;
import com.b2international.snowowl.core.version.Versions;
import com.google.common.collect.Lists;

/**
 * Tests resolution of terminology resource paths and revision ranges.
 * 
 * @since 10.4
 */
public class TerminologyResourceRequestTest {

	private CodeSystem codeSystem;
	private ServiceProvider context;
	private ResourceRepository resourceRepository;

	@Before
	public void setup() {
		codeSystem = codeSystem("CS1", "MAIN/CS1");
		resourceRepository = resourceRepository();
		context = ServiceProvider.EMPTY.inject()
			.bind(ResourceRepository.class, resourceRepository)
			.bind(PathTerminologyResourceResolver.class, resolverFor(codeSystem))
			.build();
	}

	@Test
	public void resolve_singleBranchPath() {
		final ResourceURI resolvedUri = resolve("MAIN/CS1/child");
		assertThat(resolvedUri).isEqualTo(CodeSystem.uri("CS1/child"));
	}

	@Test
	public void reject_branchPathWithoutToolingId() {
		assertThatThrownBy(() -> new TerminologyResourceRequest<>(null, "MAIN/CS1/child", ctx -> ctx.resourceURI()))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("Reflective access");
	}

	@Test
	public void resolve_singleResourceBranchRange() {
		final ResourceURI resolvedUri = resolve("MAIN/CS1/from...MAIN/CS1/to");
		assertThat(resolvedUri).isEqualTo(CodeSystem.uri("CS1/from...to"));
	}

	@Test
	public void reject_crossResourceBranchRange() {
		final CodeSystem otherCodeSystem = codeSystem("CS2", "MAIN/CS2");
		final ServiceProvider context = this.context.inject()
			.bind(PathTerminologyResourceResolver.class, resolverFor(codeSystem, otherCodeSystem))
			.build();

		assertThatThrownBy(() -> new TerminologyResourceRequest<>("tooling", "MAIN/CS1/from...MAIN/CS2/to", ctx -> ctx.resourceURI()).execute(context))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("must refer to the same terminology resource");
	}

	@Test
	public void resolve_singleResourceUriRange() {
		final DefaultResourceURIPathResolver resolver = new DefaultResourceURIPathResolver(Collections.emptyList(), true);
		final PathWithVersion resolvedPath = resolver.resolveWithVersion(context, CodeSystem.uri("CS1/from...to"), codeSystem);
		assertThat(resolvedPath.getPath()).isEqualTo("MAIN/CS1/from...MAIN/CS1/to");
	}

	private static CodeSystem codeSystem(final String id, final String branchPath) {
		final CodeSystem resource = new CodeSystem();
		resource.setId(id);
		resource.setBranchPath(branchPath);
		resource.setToolingId("tooling");
		return resource;
	}

	private static ResourceRepository resourceRepository() {
		final RevisionIndex revisionIndex = mock(RevisionIndex.class);
		// We know that there is only a single read operation; this might need to get more sophisticated if the tested code changes 
		when(revisionIndex.read(anyString(), any())).thenAnswer(_ -> new Versions(List.of(), null, 0, 0));

		final Mappings mappings = new Mappings();
		final IndexMapping indexMapping = new IndexMapping(mappings);
		
		final IndexAdmin rawIndexAdmin = mock(IndexAdmin.class);
		when(rawIndexAdmin.getIndexMapping()).thenReturn(indexMapping);
		when(revisionIndex.hooks()).thenReturn(mock(Hooks.class));
		when(revisionIndex.branching()).thenReturn(mock(BaseRevisionBranching.class));

		// As ResourceRepository calls lifecycle methods on RevisionIndex, we need to provide a RevisionIndexAdmin implementation
		final RevisionIndexAdmin revisionIndexAdmin = new RevisionIndexAdmin(revisionIndex, rawIndexAdmin);
		when(revisionIndex.admin()).thenReturn(revisionIndexAdmin);

		return new ResourceRepository(revisionIndex);
	}

	private static PathTerminologyResourceResolver resolverFor(final CodeSystem firstResource, final CodeSystem... restResources) {
		return new PathTerminologyResourceResolver() {
			@Override
			@SuppressWarnings("unchecked")
			public <T extends TerminologyResource> T resolve(final ServiceProvider context, final String toolingId, final String branchPath) {
				for (final CodeSystem otherResource : Lists.asList(firstResource, restResources)) {
					if (branchPath.startsWith(otherResource.getBranchPath())) {
						return (T) otherResource;
					}
				}
				
				throw new IllegalArgumentException("No terminology resource found for branch path: " + branchPath);
			}
		};
	}

	private ResourceURI resolve(final String path) {
		return new TerminologyResourceRequest<>("tooling", path, ctx -> ctx.resourceURI()).execute(context);
	}
}
