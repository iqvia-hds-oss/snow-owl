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
package com.b2international.snowowl.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.Test;

import com.b2international.snowowl.core.codesystem.CodeSystem;

/**
 * @since 10.4.0
 */
public class DependencyTest {

	@Test
	public void findAll_matchesInputOrder() {
		final Dependency first = Dependency.of(CodeSystem.uri("FIRST"), "domain");
		final Dependency other = Dependency.of(CodeSystem.uri("OTHER"), "extensionOf");
		final Dependency second = Dependency.of(CodeSystem.uri("SECOND"), "domain");

		assertThat(Dependency.findAll(List.of(first, other, second), "domain"))
			.containsExactly(first, second);
	}

	@Test
	public void findAll_emptyListForNullOrEmptyInput() {
		assertThat(Dependency.findAll(null, "domain")).isEmpty();
		assertThat(Dependency.findAll(List.of(), "domain")).isEmpty();
	}

	@Test
	public void getDependencies_exposesAllByScope() {
		final Dependency first = Dependency.of(CodeSystem.uri("FIRST"), "domain");
		final Dependency other = Dependency.of(CodeSystem.uri("OTHER"), "extensionOf");
		final Dependency second = Dependency.of(CodeSystem.uri("SECOND"), "domain");

		final CodeSystem codeSystem = new CodeSystem();
		codeSystem.setDependencies(List.of(first, other, second));

		assertThat(codeSystem.getDependencies("domain"))
			.containsExactly(first, second);
	}
}
