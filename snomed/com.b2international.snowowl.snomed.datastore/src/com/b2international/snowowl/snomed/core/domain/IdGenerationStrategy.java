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
package com.b2international.snowowl.snomed.core.domain;

import java.io.Serializable;

/**
 * Implementations allow clients to generate component identifiers conforming to
 * the specified constraints.
 */
public interface IdGenerationStrategy extends Serializable {

	/**
	 * Returns one of the following values:
	 * <ul>
	 * <li>"INT" for the International (core) namespace (to avoid having to use a <code>null</code> key in maps or multimaps)
	 * <li>A 7-digit namespace identifier for a specific namespace e.g. "1000000"
	 * <li>An SCTID corresponding to a namespace concept e.g. "370137002" which is 
	 * the identifier of "Extension namespace {1000000}"
	 * </ul>
	 * 
	 * @return a "namespace key" that uniquely identifies the namespace associated with this strategy
	 */
	String getNamespaceKey();

	/**
	 * @return an identifier generation strategy that uses the same namespace key as this strategy
	 */
	IdGenerationStrategy toNamespaceStrategy();
}
