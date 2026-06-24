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
package com.b2international.snowowl.snomed.core.ecl;

import static com.b2international.index.revision.Revision.Expressions.id;
import static com.b2international.index.revision.Revision.Expressions.ids;
import static com.b2international.snowowl.snomed.datastore.index.entry.SnomedConceptDocument.Expressions.ancestors;
import static com.b2international.snowowl.snomed.datastore.index.entry.SnomedConceptDocument.Expressions.parents;
import static com.b2international.snowowl.snomed.datastore.index.entry.SnomedConceptDocument.Expressions.statedAncestors;
import static com.b2international.snowowl.snomed.datastore.index.entry.SnomedConceptDocument.Expressions.statedParents;
import static com.b2international.snowowl.test.commons.snomed.DocumentBuilders.concept;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.b2international.index.query.Expression;
import com.b2international.index.query.Expressions;
import com.b2international.index.revision.StagingArea;
import com.b2international.snowowl.snomed.core.tree.Trees;
import com.b2international.snowowl.test.commons.snomed.RandomSnomedIdentiferGenerator;

/**
 * @since 10.2
 */
@RunWith(Parameterized.class)
public class SnomedEclEvaluationRequestRefinementIsaTest extends BaseSnomedEclEvaluationRequestTest {
	// random IDs
	private static final String DRUG_ROOT = RandomSnomedIdentiferGenerator.generateConceptId();
	
	private static final String DRUG_1 = RandomSnomedIdentiferGenerator.generateConceptId();
	private static final String DRUG_2 = RandomSnomedIdentiferGenerator.generateConceptId();
	private static final String DRUG_3 = RandomSnomedIdentiferGenerator.generateConceptId();
	private static final String DRUG_1_1 = RandomSnomedIdentiferGenerator.generateConceptId();
	private static final String DRUG_23_1 = RandomSnomedIdentiferGenerator.generateConceptId();
	
	private static final long DRUG_ROOTL = Long.parseLong(DRUG_ROOT);
	
	private static final long DRUG_1L = Long.parseLong(DRUG_1);
	private static final long DRUG_2L = Long.parseLong(DRUG_2);
	private static final long DRUG_3L = Long.parseLong(DRUG_3);

	private final String expressionForm;
	
	public SnomedEclEvaluationRequestRefinementIsaTest(String expressionForm) {
		this.expressionForm = expressionForm;
	}
	
	@Parameters(name = "{0}")
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] {
			// New statements with value are expected to 
			// appear in axiom and inferred form only
			{ Trees.INFERRED_FORM },
			{ AXIOM }, 
		});
	}
	
	protected final boolean isAxiom() {
		return AXIOM.equals(expressionForm);
	}

	@Override
	protected final boolean isInferred() {
		return Trees.INFERRED_FORM.equals(expressionForm);
	}
	
	@Test
	public void attributeWithIsaRoot() throws Exception {
		generateDrugHierarchy();
		final Expression actual = eval(String.format("%s: %s = %s", DRUG_1, IS_A, DRUG_ROOT));
		final Expression expected = and(
			id(DRUG_1),
			id(DRUG_1)
		);
		assertEquals(expected, actual);
	}
	
	@Test
	public void attributeWithIsaRootReversed() throws Exception {
		generateDrugHierarchy();
		final Expression actual = eval(String.format("%s: R %s = %s", DRUG_ROOT, IS_A, DRUG_1));
		final Expression expected = and(
			id(DRUG_ROOT),
			id(DRUG_ROOT)
		);
		assertEquals(expected, actual);
	}
	
	@Test
	public void attributeWithDescendantsOrSelfIsaRoot() throws Exception {
		generateDrugHierarchy();
		final Expression actual = eval(String.format("<<%s: %s = %s", DRUG_ROOT, IS_A, DRUG_ROOT));
		final Expression expected = and(
			descendantsOrSelfOf(DRUG_ROOT),
			ids(Set.of(DRUG_1, DRUG_2, DRUG_3))
		);
		assertEquals(expected, actual);
	}
	
	@Test
	public void attributeWithDescendantsOrSelfIsaDescendantOrSelf() throws Exception {
		generateDrugHierarchy();
		final Expression actual = eval(String.format("<<%s: %s = <<%s", DRUG_ROOT, IS_A, DRUG_ROOT));
		final Expression expected = and(
			descendantsOrSelfOf(DRUG_ROOT),
			ids(Set.of(DRUG_1, DRUG_2, DRUG_3, DRUG_1_1, DRUG_23_1))
		);
		assertEquals(expected, actual);
	}
	
	@Test
	public void attributeWithDescendantsOrSelfIsaRootReversed() throws Exception {
		generateDrugHierarchy();
		final Expression actual = eval(String.format("<<%s: R %s = %s", DRUG_ROOT, IS_A, DRUG_1_1));
		final Expression expected = and(
			descendantsOrSelfOf(DRUG_ROOT),
			ids(Set.of(DRUG_1))
		);
		assertEquals(expected, actual);
	}
	
	@Test
	public void attributeWithMultipleParents() throws Exception {
		generateDrugHierarchy();
		final Expression actual = eval(String.format("<<%s: R %s = %s", DRUG_ROOT, IS_A, DRUG_23_1));
		final Expression expected = and(
			descendantsOrSelfOf(DRUG_ROOT),
			ids(Set.of(DRUG_2, DRUG_3))
		);
		assertEquals(expected, actual);
	}
	
	@Test
	public void attributeWithMultipleParentsUnderGivenParent() throws Exception {
		generateDrugHierarchy();
		final Expression actual = eval(String.format("<<%s: R %s = %s", DRUG_2, IS_A, DRUG_23_1));
		final Expression expected = and(
			descendantsOrSelfOf(DRUG_2),
			ids(Set.of(DRUG_2))
		);
		assertEquals(expected, actual);
	}
	
	@Test
	public void dottedWithIsa() throws Exception {
		generateDrugHierarchy();
		final Expression actual = eval(String.format("%s.%s", DRUG_1_1, IS_A));
		final Expression expected = id(DRUG_1);
		assertEquals(expected, actual);
	}
	
	@Test
	public void dottedWithIsaMultipleParents() throws Exception {
		generateDrugHierarchy();
		final Expression actual = eval(String.format("%s.%s", DRUG_23_1, IS_A));
		final Expression expected = ids(Set.of(DRUG_2, DRUG_3));
		assertEquals(expected, actual);
	}

	/**
	 * Builds following structure:
	 * 
	 * <pre>
	 * Drug Root
	 * ├─ Drug 1
	 * │  └─ Drug-1-1
	 * ├─ Drug 2
	 * │  ├─ Drug-23-1
	 * └─ Drug 3   
	 * </pre>
	 */
	private void generateDrugHierarchy() {
		StagingArea staging = index()
			.prepareCommit(MAIN)
			// drugs
			.stageNew(concept(DRUG_ROOT)
					.build())
			.stageNew(concept(DRUG_1)
					.parents(DRUG_ROOTL)
					.statedParents(DRUG_ROOTL)
					.build())
			.stageNew(concept(DRUG_2)
					.parents(DRUG_ROOTL)
					.statedParents(DRUG_ROOTL)
					.build())
			.stageNew(concept(DRUG_3)
					.parents(DRUG_ROOTL)
					.statedParents(DRUG_ROOTL)
					.build())
			.stageNew(concept(DRUG_1_1)
					.parents(DRUG_1L)
					.statedParents(DRUG_1L)
					.ancestors(DRUG_ROOTL)
					.statedAncestors(DRUG_ROOTL)
					.build())
			.stageNew(concept(DRUG_23_1)
					.parents(DRUG_2L, DRUG_3L)
					.statedParents(DRUG_2L, DRUG_3L)
					.ancestors(DRUG_ROOTL)
					.statedAncestors(DRUG_ROOTL)
					.build());
		
		staging.commit(currentTime(), UUID.randomUUID().toString(), "Initialize generated drugs");
	}
	
	private static Expression and(Expression left, Expression right) {
		return Expressions.bool().filter(left).filter(right).build();
	}
	
	private Expression descendantsOrSelfOf(String...conceptIds) {
		if (isInferred()) {
			return Expressions.bool()
					.should(ids(Set.of(conceptIds)))
					.should(parents(Set.of(conceptIds)))
					.should(ancestors(Set.of(conceptIds)))
					.build();
		} else {
			return Expressions.bool()
					.should(ids(Set.of(conceptIds)))
					.should(statedParents(Set.of(conceptIds)))
					.should(statedAncestors(Set.of(conceptIds)))
					.build();
		}
	}
}
