/*
 * Copyright 2021-2025 B2i Healthcare, https://b2ihealthcare.com
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.b2international.snowowl.snomed.cis.SnomedIdentifiers;
import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts;
import com.google.common.base.MoreObjects;

/**
 * An identifier generation strategy that uses a specific namespace (identified
 * by its concept ID) to generate identifiers.
 * 
 * @since 8.0
 */
public final class NamespaceConceptIdStrategy implements IdGenerationStrategy {

	private static final long serialVersionUID = 1L;
	
	private final String namespaceConceptId;

	public NamespaceConceptIdStrategy(final String namespaceConceptId) {
		this.namespaceConceptId = checkNotNull(namespaceConceptId);
	}
	
	@Override
	public String getNamespaceKey() {
		// Fold the core namespace concept ID into the "INT" key to save a concept lookup
		return !Concepts.CORE_NAMESPACE_ID.equals(namespaceConceptId) 
			? namespaceConceptId 
			: SnomedIdentifiers.INT_NAMESPACE;
	}
	
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
			.add("namespaceConceptId", namespaceConceptId)
			.toString();
	}
	
	@Override
	public IdGenerationStrategy toNamespaceStrategy() {
		return this;
	}

}
