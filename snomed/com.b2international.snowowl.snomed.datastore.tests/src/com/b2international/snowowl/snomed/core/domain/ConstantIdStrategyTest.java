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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.b2international.snowowl.snomed.cis.SnomedIdentifiers;

/**
 * @since 9.8.0
 */
public class ConstantIdStrategyTest {

	@Test(expected = IllegalArgumentException.class)
	public void testNullId() {
		// ID retrieval is possible but namespace (key) extraction will fail, so storing a null ID is not allowed
		new ConstantIdStrategy(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testEmptyId() {
		// ID retrieval is possible but namespace (key) extraction will fail, so storing an empty ID is not allowed
		new ConstantIdStrategy("");
	}
	
	@Test
	public void testSimpleId() {
		final String id = "8855730050";
		final ConstantIdStrategy strategy = new ConstantIdStrategy(id);
		assertEquals("Constructor should store the provided ID", id, strategy.getId());
	}

	@Test
	public void testIntNamespaceKey() {
		final String id = "8014170051";
		final ConstantIdStrategy strategy = new ConstantIdStrategy(id);
		assertEquals("INT ID should return 'INT' namespace key", 
			SnomedIdentifiers.INT_NAMESPACE, strategy.getNamespaceKey());
	}
	
	@Test
	public void testExtensionNamespaceKey() {
		final String id = "38491000997101";
		final ConstantIdStrategy strategy = new ConstantIdStrategy(id);
		assertEquals("Extension ID should return the namespace as the key", 
			"1000997", strategy.getNamespaceKey());
	}
	
	@Test
	public void testStrategyWithIntId() {
		final String id = "8855730050";
		final ConstantIdStrategy strategy = new ConstantIdStrategy(id);
		final IdGenerationStrategy namespaceStrategy = strategy.toNamespaceStrategy();
		
		assertNotNull("toNamespaceStrategy() should not return null", namespaceStrategy);
		assertTrue("toNamespaceStrategy() should return NamespaceIdStrategy instance", 
			namespaceStrategy instanceof NamespaceIdStrategy);
		assertEquals("toNamespaceStrategy() should have correct namespace key",
			SnomedIdentifiers.INT_NAMESPACE, namespaceStrategy.getNamespaceKey());
	}
	
	@Test
	public void testStrategyWithExtensionId() {
		final String id = "38491000997101";
		final ConstantIdStrategy strategy = new ConstantIdStrategy(id);
		final IdGenerationStrategy namespaceStrategy = strategy.toNamespaceStrategy();
		
		assertNotNull("toNamespaceStrategy() should not return null", namespaceStrategy);
		assertTrue("toNamespaceStrategy() should return NamespaceIdStrategy instance", 
			namespaceStrategy instanceof NamespaceIdStrategy);
		assertEquals("toNamespaceStrategy() should have correct namespace key",
			"1000997", namespaceStrategy.getNamespaceKey());
	}

}
