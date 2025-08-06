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
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.b2international.snowowl.snomed.cis.SnomedIdentifiers;
import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts;

/**
 * @since 9.8.0
 */
public class NamespaceConceptIdStrategyTest {

	@Test(expected = NullPointerException.class)
	public void testNullConceptId() {
		// Constructor should reject null namespace concept ID
		new NamespaceConceptIdStrategy(null);
	}
	
	@Test
	public void testEmptyConceptId() {
		final String emptyId = "";
		final NamespaceConceptIdStrategy strategy = new NamespaceConceptIdStrategy(emptyId);
		// This is a characterization test, not expected or prescribed behavior
		assertEquals("Empty string namespace concept ID should return itself as namespace key", 
			emptyId, strategy.getNamespaceKey());
	}

	@Test
	public void testBlankConceptId() {
		final String blankId = "   ";
		final NamespaceConceptIdStrategy strategy = new NamespaceConceptIdStrategy(blankId);
		// This is a characterization test, not expected or prescribed behavior
		assertEquals("Blank namespace concept ID should return itself as namespace key", 
			blankId, strategy.getNamespaceKey());
	}

	@Test
	public void testValidConceptId() {
		// Made-up namespace concept with FSN "Extension namespace {1000997} (namespace concept)"
		final String conceptId = "38491000997101";
		final NamespaceConceptIdStrategy strategy = new NamespaceConceptIdStrategy(conceptId);
		assertNotNull("Strategy should be created successfully", strategy);
		assertEquals("Extension namespace concept ID should return the concept ID as namespace key", 
			conceptId, strategy.getNamespaceKey());
	}
	
	@Test
	public void testCoreNamespaceConceptId() {
		final NamespaceConceptIdStrategy strategy = new NamespaceConceptIdStrategy(Concepts.CORE_NAMESPACE_ID);
		assertEquals("Core namespace concept ID should return the 'INT' placeholder as namespace key", 
			SnomedIdentifiers.INT_NAMESPACE, strategy.getNamespaceKey());
	}
	
	@Test
	public void testToNamespaceStrategy() {
		final String conceptId = "38491000997101";
		final NamespaceConceptIdStrategy strategy = new NamespaceConceptIdStrategy(conceptId);
		final IdGenerationStrategy namespaceStrategy = strategy.toNamespaceStrategy();
		assertSame("toNamespaceStrategy() should return the same instance", 
			strategy, namespaceStrategy);
	}

}
