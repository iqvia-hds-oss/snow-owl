/*
 * Copyright 2022-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.ecl;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.eclipse.xtext.parser.IParser;
import org.eclipse.xtext.serializer.ISerializer;
import org.eclipse.xtext.validation.IResourceValidator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.b2international.snomed.ecl.EclStandaloneSetup;
import com.b2international.snowowl.core.request.ecl.EclRewriter;
import com.google.inject.Injector;

/**
 * @since 5.4
 */
@RunWith(Parameterized.class)
public class EclRewriterTest {

	private static final String ROOT_CONCEPT = "138875005";
	private static final String IS_A = "116680003";
	private static final String FINDING_SITE = "363698007";
	private static final String HAS_ACTIVE_INGREDIENT = "127489000";
	private static final String SUBSTANCE = "105590001";
	private static final String SYNONYM = "900000000000013009";
	private static final String FULLY_SPECIFIED_NAME = "900000000000003001";
	private static final String MODULE_SCT_CORE = "900000000000207008";
	private static final String LANG_REFSET_EN_GB = "900000000000508004";
	private static final String PREFERRED = "900000000000548007";
	private static final String DEFINED = "900000000000073002";
	private static final String ENTIRE_TERM_CASE_SENSITIVE = "900000000000017005";
	private static final String BODY_STRUCTURE = "123037004";
	private static final String REFSET_WAS_A_ASSOCIATION = "900000000000528000";

	// Concrete domain attribute IDs
	private static final String PRESCRIPTION = "260885003";
	private static final String SEROTYPE = "260829007";
	private static final String COUNT_OF_BASE_OF_ACTIVE_INGREDIENT = "1142139005";
	private static final String PRESENTATION_STRENGTH_NUMERATOR_VALUE = "1142135004";

	@Parameters(name = "{0}")
	public static List<Object[]> data() {
		return List.of(
			// the leftmost leaf concept term must be stripped
			new Object[] {
				"caseOrExpressionConstraint",
				ROOT_CONCEPT + " |SNOMED CT Concept| OR " + IS_A + " |Is a| OR " + SUBSTANCE + " |Substance|",
				ROOT_CONCEPT + " OR " + IS_A + " OR " + SUBSTANCE
			},

			// the leftmost leaf concept term must be stripped
			new Object[] {
				"caseAndExpressionConstraint",
				ROOT_CONCEPT + " |SNOMED CT Concept| AND " + IS_A + " |Is a| AND " + SUBSTANCE + " |Substance|",
				ROOT_CONCEPT + " AND " + IS_A + " AND " + SUBSTANCE
			},

			new Object[] {
				"caseAndExpressionConstraint_comma",
				ROOT_CONCEPT + " |SNOMED CT Concept| , " + IS_A + " |Is a|",
				ROOT_CONCEPT + " , " + IS_A
			},

			new Object[] {
				"caseExclusionExpressionConstraint",
				ROOT_CONCEPT + " |SNOMED CT Concept| MINUS " + SUBSTANCE + " |Substance|",
				ROOT_CONCEPT + " MINUS " + SUBSTANCE
			},

			new Object[] {
				"caseRefinedExpressionConstraint",
				"< " + ROOT_CONCEPT + " |SNOMED CT Concept| : " + FINDING_SITE + " |Finding site| = " + BODY_STRUCTURE + " |Body structure|",
				"< " + ROOT_CONCEPT + " : " + FINDING_SITE + " = " + BODY_STRUCTURE
			},

			new Object[] {
				"caseDottedExpressionConstraint",
				ROOT_CONCEPT + " |SNOMED CT Concept| . " + FINDING_SITE + " |Finding site|",
				ROOT_CONCEPT + " . " + FINDING_SITE
			},

			new Object[] {
				"caseFilteredExpressionConstraint",
				"< " + ROOT_CONCEPT + " |SNOMED CT Concept| {{ term = \"finding\" }}",
				"< " + ROOT_CONCEPT + " {{ term = \"finding\" }}"
			},

			new Object[] {
				"caseChildOf",
				"<! " + ROOT_CONCEPT + " |SNOMED CT Concept|",
				"<! " + ROOT_CONCEPT
			},

			new Object[] {
				"caseChildOrSelfOf",
				"<<! " + ROOT_CONCEPT + " |SNOMED CT Concept|",
				"<<! " + ROOT_CONCEPT
			},

			new Object[] {
				"caseDescendantOf",
				"< " + ROOT_CONCEPT + " |SNOMED CT Concept|",
				"< " + ROOT_CONCEPT
			},

			new Object[] {
				"caseDescendantOrSelfOf",
				"<< " + ROOT_CONCEPT + " |SNOMED CT Concept|",
				"<< " + ROOT_CONCEPT
			},

			new Object[] {
				"caseParentOf",
				">! " + ROOT_CONCEPT + " |SNOMED CT Concept|",
				">! " + ROOT_CONCEPT
			},

			new Object[] {
				"caseParentOrSelfOf",
				">>! " + ROOT_CONCEPT + " |SNOMED CT Concept|",
				">>! " + ROOT_CONCEPT
			},

			new Object[] {
				"caseAncestorOf",
				"> " + ROOT_CONCEPT + " |SNOMED CT Concept|",
				"> " + ROOT_CONCEPT
			},

			new Object[] {
				"caseAncestorOrSelfOf",
				">> " + ROOT_CONCEPT + " |SNOMED CT Concept|",
				">> " + ROOT_CONCEPT
			},

			new Object[] {
				"caseTop",
				"!!> " + ROOT_CONCEPT + " |SNOMED CT Concept|",
				"!!> " + ROOT_CONCEPT
			},

			new Object[] {
				"caseBottom",
				"!!< " + ROOT_CONCEPT + " |SNOMED CT Concept|",
				"!!< " + ROOT_CONCEPT
			},

			new Object[] {
				"caseMemberOf",
				"^ " + ROOT_CONCEPT + " |SNOMED CT Concept|",
				"^ " + ROOT_CONCEPT
			},

			new Object[] {
				"caseMemberOf_withRefsetFields",
				"^ [referencedComponentId] " + ROOT_CONCEPT + " |SNOMED CT Concept|",
				"^ [referencedComponentId] " + ROOT_CONCEPT
			},

			new Object[] {
				"caseEclConceptReference",
				ROOT_CONCEPT + " |SNOMED CT Concept|",
				ROOT_CONCEPT
			},

			new Object[] {
				"caseAlternateIdentifier",
				"icd10#A15.0 |Tuberculosis of lung|",
				"icd10#A15.0"
			},

			new Object[] {
				"caseAny",
				"*",
				"*"
			},

			new Object[] {
				"caseNestedExpression",
				"( " + ROOT_CONCEPT + " |SNOMED CT Concept| )",
				"( " + ROOT_CONCEPT + " )"
			},

			// all attribute terms must be stripped, including the leftmost leaf
			new Object[] {
				"caseOrRefinement",
				"* : " + FINDING_SITE + " |Finding site| = * OR " + HAS_ACTIVE_INGREDIENT + " |HAI| = * OR " + IS_A + " |Is a| = *",
				"* : " + FINDING_SITE + " = * OR " + HAS_ACTIVE_INGREDIENT + " = * OR " + IS_A + " = *"
			},

			// all attribute terms must be stripped, including the leftmost leaf
			new Object[] {
				"caseAndRefinement",
				"* : " + FINDING_SITE + " |Finding site| = * AND " + HAS_ACTIVE_INGREDIENT + " |HAI| = * AND " + IS_A + " |Is a| = *",
				"* : " + FINDING_SITE + " = * AND " + HAS_ACTIVE_INGREDIENT + " = * AND " + IS_A + " = *"
			},

			// using comma separator – equivalent to AND
			new Object[] {
				"caseAndRefinement_comma",
				"* : " + FINDING_SITE + " |Finding site| = * , " + HAS_ACTIVE_INGREDIENT + " |HAI| = *",
				"* : " + FINDING_SITE + " = * , " + HAS_ACTIVE_INGREDIENT + " = *"
			},

			new Object[] {
				"caseNestedRefinement",
				"* : ( " + FINDING_SITE + " |Finding site| = " + BODY_STRUCTURE + " |Body structure| )",
				"* : ( " + FINDING_SITE + " = " + BODY_STRUCTURE + " )"
			},

			new Object[] {
				"caseEclAttributeGroup",
				"* : { " + FINDING_SITE + " |Finding site| = " + BODY_STRUCTURE + " |Body structure| }",
				"* : { " + FINDING_SITE + " = " + BODY_STRUCTURE + " }"
			},

			new Object[] {
				"caseEclAttributeGroup_withCardinality",
				"* : [1..1] { " + FINDING_SITE + " |Finding site| = " + BODY_STRUCTURE + " |Body structure| }",
				"* : [1..1] { " + FINDING_SITE + " = " + BODY_STRUCTURE + " }"
			},

			new Object[] {
				"caseAttributeConstraint",
				"* : " + FINDING_SITE + " |Finding site| = " + BODY_STRUCTURE + " |Body structure|",
				"* : " + FINDING_SITE + " = " + BODY_STRUCTURE
			},

			new Object[] {
				"caseAttributeConstraint_withCardinality",
				"* : [0..1] " + FINDING_SITE + " |Finding site| = " + BODY_STRUCTURE + " |Body structure|",
				"* : [0..1] " + FINDING_SITE + " = " + BODY_STRUCTURE
			},

			new Object[] {
				"caseAttributeConstraint_reversed",
				"* : R " + FINDING_SITE + " |Finding site| = " + BODY_STRUCTURE + " |Body structure|",
				"* : R " + FINDING_SITE + " = " + BODY_STRUCTURE
			},

			new Object[] {
				"caseAttributeComparison_equals",
				"< " + ROOT_CONCEPT + " : " + HAS_ACTIVE_INGREDIENT + " = < " + SUBSTANCE + " |Substance|",
				"< " + ROOT_CONCEPT + " : " + HAS_ACTIVE_INGREDIENT + " = < " + SUBSTANCE
			},

			// must be rewritten to = (* MINUS value)
			new Object[] {
				"caseAttributeComparison_notEquals",
				"< " + ROOT_CONCEPT + " : " + HAS_ACTIVE_INGREDIENT + " != < " + SUBSTANCE,
				"< " + ROOT_CONCEPT + " : " + HAS_ACTIVE_INGREDIENT + " = ( * MINUS < " + SUBSTANCE + " )"
			},

			new Object[] {
				"caseBooleanValueComparison",
				"* : " + PRESCRIPTION + " |Prescription| = true",
				"* : " + PRESCRIPTION + " = true"
			},

			new Object[] {
				"caseStringValueComparison",
				"* : " + SEROTYPE + " |Serotype| = \"A-0201\"",
				"* : " + SEROTYPE + " = \"A-0201\""
			},

			new Object[] {
				"caseIntegerValueComparison",
				"* : " + COUNT_OF_BASE_OF_ACTIVE_INGREDIENT + " |Count of base of active ingredient| >= #1",
				"* : " + COUNT_OF_BASE_OF_ACTIVE_INGREDIENT + " >= #1"
			},

			new Object[] {
				"caseDecimalValueComparison",
				"* : " + PRESENTATION_STRENGTH_NUMERATOR_VALUE + " |Presentation strength numerator value| >= #0.5",
				"* : " + PRESENTATION_STRENGTH_NUMERATOR_VALUE + " >= #0.5"
			},

			// the leftmost leaf filter concept term must be stripped
			new Object[] {
				"caseDisjunctionFilter",
				"* {{ typeId = " + SYNONYM + " |Synonym| OR typeId = " + FULLY_SPECIFIED_NAME + " |Fully specified name| OR active = true }}",
				"* {{ typeId = " + SYNONYM + " OR typeId = " + FULLY_SPECIFIED_NAME + " OR active = true }}"
			},

			// the leftmost leaf filter concept term must be stripped
			new Object[] {
				"caseConjunctionFilter",
				"* {{ typeId = " + SYNONYM + " |Synonym| AND typeId = " + FULLY_SPECIFIED_NAME + " |Fully specified name| AND active = true }}",
				"* {{ typeId = " + SYNONYM + " AND typeId = " + FULLY_SPECIFIED_NAME + " AND active = true }}"
			},

			// using comma separator – equivalent to AND
			new Object[] {
				"caseConjunctionFilter_comma",
				"* {{ typeId = " + SYNONYM + " |Synonym| , active = true }}",
				"* {{ typeId = " + SYNONYM + " , active = true }}"
			},

			new Object[] {
				"caseNestedFilter",
				"* {{ ( term = \"finding\" ) }}",
				"* {{ ( term = \"finding\" ) }}"
			},

			new Object[] {
				"caseTermFilter",
				"* {{ term = \"finding\" }}",
				"* {{ term = \"finding\" }}"
			},

			new Object[] {
				"caseTermFilter_matchType",
				"* {{ term = match:\"finding\" }}",
				"* {{ term = match:\"finding\" }}"
			},

			// duplicates must be removed
			new Object[] {
				"caseLanguageFilter_deduplication",
				"* {{ language = ( en en nl ) }}",
				"* {{ language = ( en nl ) }}"
			},

			new Object[] {
				"caseLanguageFilter_noDuplication",
				"* {{ language = en }}",
				"* {{ language = en }}"
			},

			new Object[] {
				"caseTypeIdFilter",
				"* {{ typeId = " + SYNONYM + " |Synonym| }}",
				"* {{ typeId = " + SYNONYM + " }}"
			},

			// duplicate tokens must be removed
			new Object[] {
				"caseTypeTokenFilter",
				"* {{ type = ( syn fsn syn ) }}",
				"* {{ type = ( syn fsn ) }}"
			},

			new Object[] {
				"caseDialectIdFilter",
				"* {{ dialectId = " + LANG_REFSET_EN_GB + " |GB English| }}",
				"* {{ dialectId = " + LANG_REFSET_EN_GB + " }}"
			},

			new Object[] {
				"caseDialectIdFilter_withAcceptability",
				"* {{ dialectId = " + LANG_REFSET_EN_GB + " |GB English| ( " + PREFERRED + " |Preferred| ) }}",
				"* {{ dialectId = " + LANG_REFSET_EN_GB + " ( " + PREFERRED + " ) }}"
			},

			new Object[] {
				"caseDialectAliasFilter",
				"* {{ dialect = en-gb }}",
				"* {{ dialect = en-gb }}"
			},

			new Object[] {
				"caseDialectAliasFilter_withAcceptability",
				"* {{ dialect = en-gb ( " + PREFERRED + " |Preferred| ) }}",
				"* {{ dialect = en-gb ( " + PREFERRED + " ) }}"
			},

			// IDs are too vague to rewrite; unchanged
			new Object[] {
				"caseIdFilter",
				"* {{ id = " + SYNONYM + " }}",
				"* {{ id = " + SYNONYM + " }}"
			},

			new Object[] {
				"caseDefinitionStatusIdFilter",
				"* {{ C definitionStatusId = " + DEFINED + " |Fully defined| }}",
				"* {{ C definitionStatusId = " + DEFINED + " }}"
			},

			// no concept terms to strip (but de-duplication might be applied in the future)
			new Object[] {
				"caseDefinitionStatusTokenFilter",
				"* {{ C definitionStatus = defined }}",
				"* {{ C definitionStatus = defined }}"
			},

			new Object[] {
				"caseModuleFilter",
				"* {{ moduleId = " + MODULE_SCT_CORE + " |SNOMED CT core module| }}",
				"* {{ moduleId = " + MODULE_SCT_CORE + " }}"
			},

			new Object[] {
				"caseEffectiveTimeFilter",
				"* {{ effectiveTime = \"20210131\" }}",
				"* {{ effectiveTime = \"20210131\" }}"
			},

			new Object[] {
				"caseActiveFilter",
				"* {{ active = true }}",
				"* {{ active = true }}"
			},

			new Object[] {
				"caseSemanticTagFilter",
				"* {{ semanticTag = \"finding\" }}",
				"* {{ semanticTag = \"finding\" }}"
			},

			new Object[] {
				"casePreferredInFilter",
				"* {{ preferredIn = " + LANG_REFSET_EN_GB + " |GB English| }}",
				"* {{ preferredIn = " + LANG_REFSET_EN_GB + " }}"
			},

			new Object[] {
				"caseAcceptableInFilter",
				"* {{ acceptableIn = " + LANG_REFSET_EN_GB + " |GB English| }}",
				"* {{ acceptableIn = " + LANG_REFSET_EN_GB + " }}"
			},

			new Object[] {
				"caseLanguageRefSetFilter",
				"* {{ languageRefsetId = " + LANG_REFSET_EN_GB + " |GB English| }}",
				"* {{ languageRefsetId = " + LANG_REFSET_EN_GB + " }}"
			},

			new Object[] {
				"caseCaseSignificanceFilter",
				"* {{ caseSignificanceId = " + ENTIRE_TERM_CASE_SENSITIVE + " |Entire term case sensitive| }}",
				"* {{ caseSignificanceId = " + ENTIRE_TERM_CASE_SENSITIVE + " }}"
			},

			new Object[] {
				"caseMemberFieldFilter",
				"^ " + ROOT_CONCEPT + " {{ M referencedComponentId = " + SUBSTANCE + " |Substance| }}",
				"^ " + ROOT_CONCEPT + " {{ M referencedComponentId = " + SUBSTANCE + " }}"
			},

			// "!=" operator must _not_ be rewritten to "= (* MINUS value)" in this case, but the concept term should still be removed
			new Object[] {
				"caseMemberFieldFilter_noNeRewrite",
				"^ " + ROOT_CONCEPT + " {{ M referencedComponentId != " + SUBSTANCE + " |Substance| }}",
				"^ " + ROOT_CONCEPT + " {{ M referencedComponentId != " + SUBSTANCE + " }}"
			},
			
			new Object[] {
				"caseFilterConstraint_conceptDomain",
				"* {{ C definitionStatusId = " + DEFINED + " |Fully defined| }}",
				"* {{ C definitionStatusId = " + DEFINED + " }}"
			},

			new Object[] {
				"caseFilterConstraint_descriptionDomain",
				"* {{ D typeId = " + SYNONYM + " |Synonym| }}",
				"* {{ D typeId = " + SYNONYM + " }}"
			},

			new Object[] {
				"caseFilterConstraint_memberDomain",
				"* {{ M referencedComponentId = " + SUBSTANCE + " |Substance| }}",
				"* {{ M referencedComponentId = " + SUBSTANCE + " }}"
			},

			new Object[] {
				"caseEclConceptReferenceSet",
				"* {{ D typeId = ( " + SYNONYM + " |Synonym| " + FULLY_SPECIFIED_NAME + " |Fully specified name| " + SYNONYM + " |Synonym| " + ") }}",
				"* {{ D typeId = ( " + SYNONYM + " " + FULLY_SPECIFIED_NAME + " ) }}"
			},

			new Object[] {
				"caseHistorySupplement_withProfile",
				"< " + ROOT_CONCEPT + " |SNOMED CT Concept| {{ + HISTORY-MOD }}",
				"< " + ROOT_CONCEPT + " {{ + HISTORY-MOD }}"
			},

			new Object[] {
				"caseHistorySupplement_noProfile",
				"< " + ROOT_CONCEPT + " |SNOMED CT Concept| {{ + HISTORY }}",
				"< " + ROOT_CONCEPT + " {{ + HISTORY }}"
			},

			new Object[] {
				"caseHistorySupplement_conceptReference",
				"< " + ROOT_CONCEPT + " |SNOMED CT Concept| {{ + HISTORY ( " + REFSET_WAS_A_ASSOCIATION + " |Was a| ) }}",
				"< " + ROOT_CONCEPT + " {{ + HISTORY ( " + REFSET_WAS_A_ASSOCIATION + " ) }}"
			}
		);
	}

	private final String label;
	private final String input;
	private final String expected;

	public EclRewriterTest(String label, String input, String expected) {
		this.label = label;
		this.input = input;
		this.expected = expected;
	}

	private EclRewriter rewriter;
	private EclParser parser;
	private EclSerializer serializer;

	@Before
	public void givenRewriter() {
		final Injector injector = new EclStandaloneSetup().createInjectorAndDoEMFRegistration();
		rewriter = new EclRewriter();
		parser = new DefaultEclParser(injector.getInstance(IParser.class), injector.getInstance(IResourceValidator.class));
		serializer = new DefaultEclSerializer(injector.getInstance(ISerializer.class));
	}

	@Test
	public void testRewrite() {
		assertEquals(label + " rewriting failed", expected, serializer.serialize(rewriter.rewrite(parser.parse(input))));
	}

}
