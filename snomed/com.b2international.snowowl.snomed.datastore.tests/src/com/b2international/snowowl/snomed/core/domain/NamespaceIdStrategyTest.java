/*
 * Copyright 2025 B2i Healthcare, https://b2ihealthcare.com
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.b2international.snowowl.snomed.cis.SnomedIdentifiers;

/**
 * @since 9.8.0
 */
public class NamespaceIdStrategyTest {

	@Test
	public void testNullNamespace() {
		final NamespaceIdStrategy strategy = new NamespaceIdStrategy(null);
		assertEquals("Null namespace should return INT namespace", 
			SnomedIdentifiers.INT_NAMESPACE, strategy.getNamespaceKey());
	}

	@Test
	public void testEmptyNamespace() {
		final NamespaceIdStrategy strategy = new NamespaceIdStrategy("");
		assertEquals("Empty namespace should return INT namespace", 
			SnomedIdentifiers.INT_NAMESPACE, strategy.getNamespaceKey());
	}

	@Test
	public void testSimpleNamespace() {
		final String namespace = "1000154";
		final NamespaceIdStrategy strategy = new NamespaceIdStrategy(namespace);
		assertEquals("Constructor should store the provided namespace", 
			namespace, strategy.getNamespaceKey());
	}
	
	@Test
	public void testBlankNamespace() {
		final NamespaceIdStrategy strategy = new NamespaceIdStrategy("   ");
		assertEquals("Whitespace namespace should return INT whitespace", 
			SnomedIdentifiers.INT_NAMESPACE, strategy.getNamespaceKey());
	}
	
	@Test
	public void testToNamespaceStrategy() {
		final String namespace = "1000154";
		final NamespaceIdStrategy strategy = new NamespaceIdStrategy(namespace);
		final IdGenerationStrategy namespaceStrategy = strategy.toNamespaceStrategy();
		assertSame("toNamespaceStrategy() should return the same instance", 
			strategy, namespaceStrategy);
	}

}
