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
package com.b2international.snowowl.core.uri;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;

import com.b2international.commons.exceptions.BadRequestException;
import com.b2international.snowowl.core.Resource;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.codesystem.CodeSystem;
import com.b2international.snowowl.core.uri.DefaultResourceURIPathResolver.VersionSearcher;
import com.b2international.snowowl.core.uri.ResourceURIPathResolver.PathWithVersion;
import com.b2international.snowowl.core.version.Version;

/**
 * Tests resolution of terminology resource URIs to branch paths.
 * 
 * @since 10.4.0
 */
public class DefaultResourceURIPathResolverTest {

	private CodeSystem codeSystem;
	private ServiceProvider context;

	@Before
	public void setup() {
		codeSystem = createCodeSystem("CS1", "MAIN/CS1");
		context = ServiceProvider.EMPTY;
	}

	@Test
	public void resolve_singleResourceRange() {
		final DefaultResourceURIPathResolver resolver = new DefaultResourceURIPathResolver(Collections.emptyList(), true, emptyVersionSearcher());
		final PathWithVersion resolvedPath = resolver.resolveWithVersion(context, CodeSystem.uri("CS1/from...to"), codeSystem);
		assertThat(resolvedPath.getPath()).isEqualTo("MAIN/CS1/from...MAIN/CS1/to");
	}

	@Test
	public void resolve_singleResourceRangeWithTimestamp() {
		final DefaultResourceURIPathResolver resolver = new DefaultResourceURIPathResolver(Collections.emptyList(), true, emptyVersionSearcher());
		final PathWithVersion resolvedPath = resolver.resolveWithVersion(context, CodeSystem.uri("CS1/from...to@123"), codeSystem);
		assertThat(resolvedPath.getPath()).isEqualTo("MAIN/CS1/from...MAIN/CS1/to@123");
	}

	@Test
	public void resolve_headUriToWorkingBranch() {
		final DefaultResourceURIPathResolver resolver = new DefaultResourceURIPathResolver(Collections.emptyList(), true, emptyVersionSearcher());
		final PathWithVersion resolvedPath = resolver.resolveWithVersion(context, CodeSystem.uri("CS1/HEAD"), codeSystem);
		assertThat(resolvedPath.getPath()).isEqualTo("MAIN/CS1");
	}

	@Test
	public void resolve_nestedBranchPath() {
		final DefaultResourceURIPathResolver resolver = new DefaultResourceURIPathResolver(Collections.emptyList(), true, emptyVersionSearcher());
		final PathWithVersion resolvedPath = resolver.resolveWithVersion(context, CodeSystem.uri("CS1/feature/fix"), codeSystem);
		assertThat(resolvedPath.getPath()).isEqualTo("MAIN/CS1/feature/fix");
	}

	@Test
	public void resolve_latestVersion() {
		final Version version = createVersion("v1", "MAIN/CS1/2024-01-01");
		final VersionSearcher versionSearcher = (_, _, _) -> Optional.of(version);
		final DefaultResourceURIPathResolver resolver = new DefaultResourceURIPathResolver(Collections.emptyList(), true, versionSearcher);
		
		final PathWithVersion resolvedPath = resolver.resolveWithVersion(context, CodeSystem.uri("CS1/LATEST"), codeSystem);
		assertThat(resolvedPath.getPath()).isEqualTo("MAIN/CS1/2024-01-01");
		assertThat(resolvedPath.getVersionResourceURI()).isEqualTo(ResourceURI.of("codesystems", "CS1/v1"));
	}

	@Test
	public void resolve_latestVersionForDraftResource() {
		codeSystem.setStatus(Resource.STATUS_DRAFT);
		final DefaultResourceURIPathResolver resolver = new DefaultResourceURIPathResolver(Collections.emptyList(), true, emptyVersionSearcher());
		final PathWithVersion resolvedPath = resolver.resolveWithVersion(context, CodeSystem.uri("CS1/LATEST"), codeSystem);
		assertThat(resolvedPath.getPath()).isEqualTo("MAIN/CS1");
	}

	@Test
	public void reject_latestVersionForNonDraftResource() {
		final DefaultResourceURIPathResolver resolver = new DefaultResourceURIPathResolver(Collections.emptyList(), false, emptyVersionSearcher());
		assertThatThrownBy(() -> resolver.resolveWithVersion(context, CodeSystem.uri("CS1/LATEST"), codeSystem))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("No Resource version is present");
	}

	@Test
	public void resolve_singleSegmentBranchPath() {
		final DefaultResourceURIPathResolver resolver = new DefaultResourceURIPathResolver(Collections.emptyList(), true, emptyVersionSearcher());
		final PathWithVersion resolvedPath = resolver.resolveWithVersion(context, CodeSystem.uri("CS1/feature"), codeSystem);
		assertThat(resolvedPath.getPath()).isEqualTo("MAIN/CS1/feature");
	}

	@Test
	public void reject_singleSegmentBranchPathWhenDisallowed() {
		final DefaultResourceURIPathResolver resolver = new DefaultResourceURIPathResolver(Collections.emptyList(), false, emptyVersionSearcher());
		assertThatThrownBy(() -> resolver.resolveWithVersion(context, CodeSystem.uri("CS1/feature"), codeSystem))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("No Resource version is present");
	}

	@Test
	public void resolve_customResolverPrecedesVersionLookup() {
		final AtomicBoolean versionLookupInvoked = new AtomicBoolean(false);
		
		final TerminologyResourceURIPathResolver customResolver = new TerminologyResourceURIPathResolver() {
			@Override
			public boolean canResolve(final ResourceURI uri) {
				return "MAGIC".equals(uri.getPath());
			}

			@Override
			public PathWithVersion resolve(final ServiceProvider context, final ResourceURI uriToResolve, final com.b2international.snowowl.core.TerminologyResource resource) {
				return new PathWithVersion("MAIN/CS1/custom-magic");
			}
		};
		
		final VersionSearcher versionSearcher = (_, _, _) -> {
			versionLookupInvoked.set(true);
			return Optional.empty();
		};
		
		final DefaultResourceURIPathResolver resolver = new DefaultResourceURIPathResolver(Collections.singletonList(customResolver), true, versionSearcher);
		final PathWithVersion resolvedPath = resolver.resolveWithVersion(context, CodeSystem.uri("CS1/MAGIC"), codeSystem);
		assertThat(resolvedPath.getPath()).isEqualTo("MAIN/CS1/custom-magic");
		assertThat(versionLookupInvoked).isFalse();
	}

	@Test
	public void resolve_simulatedVersionSearcher() {
		final VersionSearcher versionSearcher = (_, uriToResolve, _) -> {
			if (uriToResolve.isLatest()) {
				return Optional.of(createVersion("V2", "MAIN/CS1/V2"));
			} else if ("V1".equals(uriToResolve.getPath())) {
				return Optional.of(createVersion("V1", "MAIN/CS1/V1"));
			} else if ("V2".equals(uriToResolve.getPath())) {
				return Optional.of(createVersion("V2", "MAIN/CS1/V2"));
			} else {
				return Optional.empty();
			}
		};
		
		final DefaultResourceURIPathResolver resolver = new DefaultResourceURIPathResolver(Collections.emptyList(), true, versionSearcher);
		assertThat(resolver.resolveWithVersion(context, CodeSystem.uri("CS1/V1"), codeSystem).getPath()).isEqualTo("MAIN/CS1/V1");
		assertThat(resolver.resolveWithVersion(context, CodeSystem.uri("CS1/V2"), codeSystem).getPath()).isEqualTo("MAIN/CS1/V2");
		assertThat(resolver.resolveWithVersion(context, CodeSystem.uri("CS1/LATEST"), codeSystem).getPath()).isEqualTo("MAIN/CS1/V2");
	}

	private static CodeSystem createCodeSystem(final String id, final String branchPath) {
		final CodeSystem resource = new CodeSystem();
		resource.setId(id);
		resource.setBranchPath(branchPath);
		resource.setToolingId("tooling");
		return resource;
	}

	private static Version createVersion(final String version, final String branchPath) {
		final Version resourceVersion = new Version();
		resourceVersion.setResource(CodeSystem.uri("CS1"));
		resourceVersion.setVersion(version);
		resourceVersion.setBranchPath(branchPath);
		return resourceVersion;
	}

	private static DefaultResourceURIPathResolver.VersionSearcher emptyVersionSearcher() {
		return (_, _, _) -> Optional.empty();
	}
}