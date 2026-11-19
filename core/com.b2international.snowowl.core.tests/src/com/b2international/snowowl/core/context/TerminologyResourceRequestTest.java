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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

import com.b2international.commons.exceptions.BadRequestException;
import com.b2international.commons.exceptions.NotFoundException;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.TerminologyResource;
import com.b2international.snowowl.core.codesystem.CodeSystem;
import com.b2international.snowowl.core.repository.PathTerminologyResourceResolver;
import com.google.common.collect.Lists;

/**
 * Tests resolution of terminology resource paths and revision ranges.
 *
 * @since 10.4.0
 */
public class TerminologyResourceRequestTest {

	private CodeSystem codeSystem;
	private ServiceProvider context;

	@Before
	public void setup() {
		codeSystem = createCodeSystem("CS1", "MAIN/CS1");
		context = ServiceProvider.EMPTY.inject()
			.bind(PathTerminologyResourceResolver.class, pathResolverFor(codeSystem))
			.build();
	}

	@Test
	public void resolve_singleBranchPath() {
		final ResourceURI resolvedUri = executeRequest("tooling", "MAIN/CS1/child", resourceFetcherFor(codeSystem));
		assertThat(resolvedUri).isEqualTo(CodeSystem.uri("CS1/child"));
	}

	@Test
	public void resolve_singleResourceUri() {
		final ResourceURI resolvedUri = executeRequest("tooling", "CS1/child", resourceFetcherFor(codeSystem));
		assertThat(resolvedUri).isEqualTo(CodeSystem.uri("CS1/child"));
	}

	@Test
	public void reject_branchPathWithoutToolingId() {
		assertThatThrownBy(() -> executeRequest(null, "MAIN/CS1/child", resourceFetcherFor(codeSystem)))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("Reflective access");
	}

	@Test
	public void resolve_relativePathWithoutToolingId() {
		// If the first segment of the path is a resource ID, the tooling ID is not required to resolve the resource
		final ResourceURI resolvedUri = executeRequest(null, "CS1/from...to", resourceFetcherFor(codeSystem));
		assertThat(resolvedUri).isEqualTo(CodeSystem.uri("CS1/from...to"));
	}

	@Test
	public void resolve_singleResourceBranchRange() {
		final ResourceURI resolvedUri = executeRequest("tooling", "MAIN/CS1/from...MAIN/CS1/to", resourceFetcherFor(codeSystem));
		assertThat(resolvedUri).isEqualTo(CodeSystem.uri("CS1/from...to"));
	}

	@Test
	public void resolve_singleResourceUriRange() {
		final ResourceURI resolvedUri = executeRequest(null, "CS1/from...to", resourceFetcherFor(codeSystem));
		assertThat(resolvedUri).isEqualTo(CodeSystem.uri("CS1/from...to"));
	}

	@Test
	public void resolve_singleResourceUriRange_special() {
		final ResourceURI resolvedUri = executeRequest(null, "CS1/from...HEAD", resourceFetcherFor(codeSystem));
		assertThat(resolvedUri).isEqualTo(CodeSystem.uri("CS1/from...HEAD"));
	}

	@Test
	public void resolve_mixedBranchAndResourceUriRange() {
		// The second part of the range is treated as a relative path anchored to the resource resolved from the first part of the range;
		// here this means a path named "CS1" exists as a child of the working branch of code system "CS1".
		final ResourceURI resolvedUri = executeRequest("tooling", "MAIN/CS1/from...CS1/to", resourceFetcherFor(codeSystem));
		assertThat(resolvedUri).isEqualTo(CodeSystem.uri("CS1/from...CS1/to"));
	}

	@Test
	public void reject_crossResourceBranchRange() {
		final CodeSystem otherCodeSystem = createCodeSystem("CS2", "MAIN/CS2");

		context = context.inject()
			.bind(PathTerminologyResourceResolver.class, pathResolverFor(codeSystem, otherCodeSystem))
			.build();

		assertThatThrownBy(() -> executeRequest("tooling", "MAIN/CS1/from...MAIN/CS2/to", resourceFetcherFor(codeSystem, otherCodeSystem)))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("does not match the expected resource");
	}

	@Test
	public void resolve_attemptedCrossResourceRange_branchPath() {
		final CodeSystem otherCodeSystem = createCodeSystem("CS2", "MAIN/CS2");

		context = context.inject()
			.bind(PathTerminologyResourceResolver.class, pathResolverFor(codeSystem, otherCodeSystem))
			.build();

		// The second part of the range is treated as a relative path anchored to the resource resolved from the first part of the range;
		// therefore it is impossible to trigger a cross-resource resolution this way.
		final ResourceURI resourceURI = executeRequest("tooling", "MAIN/CS1/from...CS2/to", resourceFetcherFor(codeSystem, otherCodeSystem));
		assertThat(resourceURI).isEqualTo(CodeSystem.uri("CS1/from...CS2/to"));
	}

	@Test
	public void resolve_attemptedCrossResourceRange_resourceUri() {
		final CodeSystem otherCodeSystem = createCodeSystem("CS2", "MAIN/CS2");

		context = context.inject()
			.bind(PathTerminologyResourceResolver.class, pathResolverFor(codeSystem, otherCodeSystem))
			.build();

		// The same holds true if the first part of the range is a resource URI
		final ResourceURI resourceURI = executeRequest("tooling", "CS1/from...CS2", resourceFetcherFor(codeSystem, otherCodeSystem));
		assertThat(resourceURI).isEqualTo(CodeSystem.uri("CS1/from...CS2"));
	}

	@Test
	public void initializeRequestContext_cachesResourceOnce() {
		final AtomicInteger fetchCount = new AtomicInteger();
		final TerminologyResourceRequest.ResourceFetcher fetcher = (_, resourceUri) -> {
			fetchCount.incrementAndGet();
			if ("CS1".equals(resourceUri.getResourceId())) {
				return codeSystem;
			}
			throw new NotFoundException("Resource", resourceUri.getResourceId());
		};

		final TerminologyResourceRequest<ResourceURI> request = new TerminologyResourceRequest<>("tooling", "CS1/child", ctx -> ctx.resourceURI(), fetcher);
		// Three separate calls to initialize the request context should only fetch the resource once
		assertThat(request.getResourceURI(context)).isEqualTo(CodeSystem.uri("CS1/child"));
		assertThat(request.getResource(context)).isSameAs(codeSystem);
		assertThat(request.execute(context)).isEqualTo(CodeSystem.uri("CS1/child"));
		assertThat(fetchCount.get()).isEqualTo(1);
	}

	@Test
	public void reject_missingResource() {
		final TerminologyResourceRequest.ResourceFetcher fetcher = (_, resourceUri) -> {
			throw new NotFoundException("Resource", resourceUri.getResourceId());
		};

		assertThatThrownBy(() -> executeRequest("tooling", "CS1/child", fetcher))
			.isInstanceOf(NotFoundException.class)
			.hasMessageContaining("Resource");
	}

	@Test
	@SuppressWarnings({ "rawtypes" })
	public void serialization_restoresDefaultResourceFetcher() throws Exception {
		final TerminologyResourceRequest<ResourceURI> request = new TerminologyResourceRequest<>("tooling", "CS1/child", ctx -> ctx.resourceURI(), null);

		final byte[] serialized;
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bos)) {
			out.writeObject(request);
			serialized = bos.toByteArray();
		}

		TerminologyResourceRequest<?> deserialized;
		try (ByteArrayInputStream bis = new ByteArrayInputStream(serialized); ObjectInputStream in = new ObjectInputStream(bis)) {
			deserialized = (TerminologyResourceRequest) in.readObject();
		}

		assertThat(deserialized.getResourceFetcher()).isSameAs(TerminologyResourceRequest.ResourceFetcher.DEFAULT);
	}

	private static CodeSystem createCodeSystem(final String id, final String branchPath) {
		final CodeSystem resource = new CodeSystem();
		resource.setId(id);
		resource.setBranchPath(branchPath);
		resource.setToolingId("tooling");
		return resource;
	}

	private static TerminologyResourceRequest.ResourceFetcher resourceFetcherFor(final CodeSystem firstResource, final CodeSystem... restResources) {
		return (_, resourceUri) -> {
			for (final CodeSystem resource : Lists.asList(firstResource, restResources)) {
				if (resource.getId().equals(resourceUri.getResourceId())) {
					return resource;
				}
			}

			throw new NotFoundException("Resource", resourceUri.getResourceId());
		};
	}

	private static PathTerminologyResourceResolver pathResolverFor(final CodeSystem firstResource, final CodeSystem... restResources) {
		return new PathTerminologyResourceResolver() {
			@Override
			@SuppressWarnings("unchecked")
			public <T extends TerminologyResource> T resolve(final ServiceProvider context, final String toolingId, final String branchPath) {
				for (final CodeSystem otherResource : Lists.asList(firstResource, restResources)) {
					if (branchPath.startsWith(otherResource.getBranchPath())) {
						return (T) otherResource;
					}
				}
				throw new NotFoundException("Terminology Resource", branchPath);
			}
		};
	}

	private ResourceURI executeRequest(final String toolingId, final String path, final TerminologyResourceRequest.ResourceFetcher resourceFetcher) {
		// The anonymous "next" request extracts the resolved resource URI
		return new TerminologyResourceRequest<ResourceURI>(toolingId, path, ctx -> ctx.resourceURI(), resourceFetcher).execute(context);
	}
}
