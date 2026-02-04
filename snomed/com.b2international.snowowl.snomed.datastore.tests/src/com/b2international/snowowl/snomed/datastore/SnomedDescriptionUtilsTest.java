/*
 * Copyright 2020-2023 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.snomed.datastore;

import static com.b2international.snowowl.snomed.datastore.CodeSystemResource.GB_LOCALE;
import static com.b2international.snowowl.snomed.datastore.CodeSystemResource.SG_LOCALE;
import static com.b2international.snowowl.snomed.datastore.CodeSystemResource.US_LOCALE;
import static com.b2international.snowowl.snomed.datastore.SnomedDescriptionUtils.indexBestPreferredByConceptId;
import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.b2international.commons.http.AcceptLanguageHeader;
import com.b2international.commons.http.ExtendedLocale;
import com.b2international.snowowl.core.branch.Branch;
import com.b2international.snowowl.core.domain.BranchContext;
import com.b2international.snowowl.core.repository.JsonSupport;
import com.b2international.snowowl.core.terminology.TerminologyRegistry;
import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.core.domain.Acceptability;
import com.b2international.snowowl.snomed.core.domain.SnomedDescription;
import com.b2international.snowowl.test.commons.snomed.TestBranchContext;
import com.b2international.snowowl.test.commons.snomed.TestBranchContext.Builder;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 7.10.0
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SnomedDescriptionUtilsTest {

	private static final ExtendedLocale UNRECOGNIZED_LOCALE = new ExtendedLocale("en", "", "1234");
	private static final ExtendedLocale WILDCARD_LOCALE = new ExtendedLocale(AcceptLanguageHeader.WILDCARD, "", "");

	private static List<SnomedDescription> descriptions;

	private static SnomedDescription gbPreferredDescription;
	private static SnomedDescription usPreferredDescription;
	private static SnomedDescription sgPreferredDescription;
	private static SnomedDescription gbUsAcceptableDescription;
	private static SnomedDescription sgAcceptableDescription;
	private static BranchContext context;

	@BeforeClass
	public static void setup() {
		Builder contextBuilder = TestBranchContext.on(Branch.MAIN_PATH)
			.with(ClassLoader.class, SnomedDescriptionUtilsTest.class.getClassLoader())
			.with(ObjectMapper.class, JsonSupport.getDefaultObjectMapper())
			.with(TerminologyRegistry.class, TerminologyRegistry.INSTANCE);
		
		CodeSystemResource.configureCodeSystem(contextBuilder);
		context = contextBuilder.build();
		
		gbPreferredDescription = createDescription("1", "first description", Map.of(
			Concepts.REFSET_LANGUAGE_TYPE_UK, Acceptability.PREFERRED,
			Concepts.REFSET_LANGUAGE_TYPE_US, Acceptability.ACCEPTABLE
		));

		usPreferredDescription = createDescription("2", "second description", Map.of(
			Concepts.REFSET_LANGUAGE_TYPE_UK, Acceptability.ACCEPTABLE,
			Concepts.REFSET_LANGUAGE_TYPE_US, Acceptability.PREFERRED
		));

		sgPreferredDescription = createDescription("3", "third description", Map.of(
			Concepts.REFSET_LANGUAGE_TYPE_SG, Acceptability.PREFERRED
		));

		gbUsAcceptableDescription = createDescription("4", "fourth description", Map.of(
			Concepts.REFSET_LANGUAGE_TYPE_UK, Acceptability.ACCEPTABLE,
			Concepts.REFSET_LANGUAGE_TYPE_US, Acceptability.ACCEPTABLE
		));

		sgAcceptableDescription = createDescription("5", "fifth description", Map.of(
			Concepts.REFSET_LANGUAGE_TYPE_SG, Acceptability.ACCEPTABLE
		));

		descriptions = List.of(
			gbPreferredDescription, 
			usPreferredDescription, 
			gbUsAcceptableDescription, 
			sgPreferredDescription, 
			sgAcceptableDescription
		);
	}

	private static void assertBestPreferred(final SnomedDescription expected, final ExtendedLocale... locales) {
		assertEquals(expected, indexBestPreferredByConceptId(context, descriptions, List.of(locales)).get(Concepts.ROOT_CONCEPT));
	}

	@Test
	public void testGBEnglishOrdering1() {
		assertBestPreferred(gbPreferredDescription, GB_LOCALE, US_LOCALE, SG_LOCALE);
	}

	
	@Test
	public void testGBEnglishOrdering2() {
		assertBestPreferred(gbPreferredDescription, GB_LOCALE, SG_LOCALE);
	}

	@Test
	public void testGBEnglishOrdering3() {
		assertBestPreferred(gbPreferredDescription, GB_LOCALE, US_LOCALE);
	}

	@Test
	public void testGBEnglishOrdering4() {
		assertBestPreferred(gbPreferredDescription, GB_LOCALE);
	}

	@Test
	public void testUSEnglishOrdering1() {
		assertBestPreferred(usPreferredDescription, US_LOCALE, GB_LOCALE, SG_LOCALE);
	}

	@Test
	public void testUSEnglishOrdering2() {
		assertBestPreferred(usPreferredDescription, US_LOCALE, GB_LOCALE);
	}

	@Test
	public void testUSEnglishOrdering3() {
		assertBestPreferred(usPreferredDescription, US_LOCALE, SG_LOCALE);
	}

	@Test
	public void testUSEnglishOrdering4() {
		assertBestPreferred(usPreferredDescription, US_LOCALE);
	}

	@Test
	public void testSgEnglishOrdering1() {
		assertBestPreferred(sgPreferredDescription, SG_LOCALE, US_LOCALE, GB_LOCALE);
	}

	@Test
	public void testSgEnglishOrdering2() {
		assertBestPreferred(sgPreferredDescription, SG_LOCALE, GB_LOCALE);
	}

	@Test
	public void testSgEnglishOrdering3() {
		assertBestPreferred(sgPreferredDescription, SG_LOCALE, US_LOCALE);
	}

	@Test
	public void testSgEnglishOrdering4() {
		assertBestPreferred(sgPreferredDescription, SG_LOCALE);
	}

	@Test
	public void testCustomLanguageRefsetOrdering1() {
		assertBestPreferred(sgPreferredDescription, UNRECOGNIZED_LOCALE, SG_LOCALE);
	}

	@Test
	public void testCustomLanguageRefsetOrdering2() {
		assertBestPreferred(usPreferredDescription, UNRECOGNIZED_LOCALE, US_LOCALE, SG_LOCALE);
	}

	@Test
	public void testCustomLanguageRefsetOrdering3() {
		assertBestPreferred(gbPreferredDescription, UNRECOGNIZED_LOCALE, GB_LOCALE, US_LOCALE, SG_LOCALE);
	}

	@Test
	public void testCustomLanguageRefsetOrdering4() {
		assertBestPreferred(null, UNRECOGNIZED_LOCALE);
	}

	@Test
	public void testWildcardOrdering1() {
		// Ordering is established in CodeSystemResource#configureCodeSystem (SG, GB, US) - SG wins
		assertBestPreferred(sgPreferredDescription, WILDCARD_LOCALE);
	}

	@Test
	public void testWildcardOrdering2() {
		// Custom locale first (unrecognized), then wildcard (which expands to SG, GB, US) - SG wins
		assertBestPreferred(sgPreferredDescription, UNRECOGNIZED_LOCALE, WILDCARD_LOCALE);
	}

	@Test
	public void testWildcardOrdering3() {
		// Custom locale, then US, then wildcard (which expands to SG, GB, US without duplication) - US wins
		assertBestPreferred(usPreferredDescription, UNRECOGNIZED_LOCALE, US_LOCALE, WILDCARD_LOCALE);
	}

	@SuppressWarnings("deprecation")
	private static SnomedDescription createDescription(String id, String term, Map<String, Acceptability> acceptabilityMap) {
		SnomedDescription description = new SnomedDescription(id);
		description.setTerm(term);
		description.setActive(true);
		description.setConceptId(Concepts.ROOT_CONCEPT);
		description.setAcceptabilityMap(acceptabilityMap);
		return description;
	}
}
