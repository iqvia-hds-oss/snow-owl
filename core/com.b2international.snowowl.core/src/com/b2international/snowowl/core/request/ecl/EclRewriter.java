/* Copyright 2019-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.request.ecl;

import static com.google.common.collect.Sets.newHashSet;

import java.util.List;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;

import com.b2international.snomed.ecl.ecl.*;
import com.b2international.snomed.ecl.ecl.util.EclSwitch;

/** 
 * @since 5.4
 */
public class EclRewriter extends EclSwitch<EObject> {

	// Method ordering follows order of appearance in ECL.xtext
	
	@Override
	public EObject caseScript(Script object) {
		object.setConstraint(rewrite(object.getConstraint()));
		return object;
	}
	
	@Override
	public EObject caseOrExpressionConstraint(OrExpressionConstraint object) {
		// Consecutive OR constraints are parsed to one side, rewrite the other
		ExpressionConstraint left = object;
		OrExpressionConstraint current = null;
		
		while (left instanceof OrExpressionConstraint) {
			current = (OrExpressionConstraint) left;
			current.setRight(rewrite(current.getRight()));
			left = current.getLeft();
		}

		// Rewrite the left side of the last "OR" constraint that we have visited
		current.setLeft(rewrite(left));
		return object;
	}

	@Override
	public EObject caseAndExpressionConstraint(AndExpressionConstraint object) {
		// Consecutive AND constraints are parsed to one side, rewrite the other
		ExpressionConstraint left = object;
		AndExpressionConstraint current = null;
		
		while (left instanceof AndExpressionConstraint) {
			current = (AndExpressionConstraint) left;
			current.setRight(rewrite(current.getRight()));
			left = current.getLeft();
		}
		
		current.setLeft(rewrite(left));
		return object;
	}

	@Override
	public EObject caseExclusionExpressionConstraint(ExclusionExpressionConstraint object) {
		// Consecutive MINUS constraints are parsed to one side, rewrite the other
		ExpressionConstraint left = object;
		ExclusionExpressionConstraint current = null;
		
		while (left instanceof ExclusionExpressionConstraint) {
			current = (ExclusionExpressionConstraint) left;
			current.setRight(rewrite(current.getRight()));
			left = current.getLeft();
		}
		
		current.setLeft(rewrite(left));
		return object;
	}

	@Override
	public EObject caseRefinedExpressionConstraint(RefinedExpressionConstraint object) {
		object.setConstraint(rewrite(object.getConstraint()));
		object.setRefinement(rewrite(object.getRefinement()));
		return object;
	}

	@Override
	public EObject caseDottedExpressionConstraint(DottedExpressionConstraint object) {
		object.setAttribute(rewrite(object.getAttribute()));
		object.setConstraint(rewrite(object.getConstraint()));
		return object;
	}

	@Override
	public EObject caseSupplementExpressionConstraint(SupplementExpressionConstraint object) {
		object.setConstraint(rewrite(object.getConstraint()));
		object.setSupplement(rewrite(object.getSupplement()));
		return object;
	}

	@Override
	public EObject caseFilteredExpressionConstraint(FilteredExpressionConstraint object) {
		object.setConstraint(rewrite(object.getConstraint()));
		object.setFilter(rewrite(object.getFilter()));
		return object;
	}

	// SubExpressionConstraint is "purely abstract", it has no corresponding Java class
	
	// EclFocusConcept is "purely abstract" as well
	
	@Override
	public EObject caseChildOf(ChildOf object) {
		object.setConstraint(rewrite(object.getConstraint()));
		return object;
	}

	@Override
	public EObject caseChildOrSelfOf(ChildOrSelfOf object) {
		object.setConstraint(rewrite(object.getConstraint()));
		return object;
	}

	@Override
	public EObject caseDescendantOf(DescendantOf object) {
		object.setConstraint(rewrite(object.getConstraint()));
		return object;
	}

	@Override
	public EObject caseDescendantOrSelfOf(DescendantOrSelfOf object) {
		object.setConstraint(rewrite(object.getConstraint()));
		return object;
	}

	@Override
	public EObject caseParentOf(ParentOf object) {
		object.setConstraint(rewrite(object.getConstraint()));
		return object;
	}

	@Override
	public EObject caseParentOrSelfOf(ParentOrSelfOf object) {
		object.setConstraint(rewrite(object.getConstraint()));
		return object;
	}

	@Override
	public EObject caseAncestorOf(AncestorOf object) {
		object.setConstraint(rewrite(object.getConstraint()));
		return object;
	}

	@Override
	public EObject caseAncestorOrSelfOf(AncestorOrSelfOf object) {
		object.setConstraint(rewrite(object.getConstraint()));
		return object;
	}

	@Override
	public EObject caseTop(Top object) {
		object.setConstraint(rewrite(object.getConstraint()));
		return object;
	}

	@Override
	public EObject caseBottom(Bottom object) {
		object.setConstraint(rewrite(object.getConstraint()));
		return object;
	}

	@Override
	public EObject caseMemberOf(MemberOf object) {
		object.setConstraint(rewrite(object.getConstraint()));
		return object;
	}

	@Override
	public EObject caseEclConceptReference(EclConceptReference object) {
		// Remove term from concept references
		object.setTerm(null);
		return object;
	}

	@Override
	public EObject caseAlternateIdentifier(AlternateIdentifier object) {
		// Remove term from alternate identifiers as well
		object.setTerm(null);
		return object;
	}

	@Override
	public EObject caseEclConceptReferenceSet(EclConceptReferenceSet object) {
		// Make referenced SCTIDs unique (remove reference if its SCTID was already in the set)
		final List<EclConceptReference> conceptReferences = object.getConcepts();
		final Set<String> conceptIds = newHashSet();
		conceptReferences.removeIf(ref -> !conceptIds.add(ref.getId()));
		
		// Rewrite remaining references
		for (int i = 0; i < conceptReferences.size(); i++) {
			conceptReferences.set(i, rewrite(conceptReferences.get(i)));
		}
		
		return object;
	}

	@Override
	public EObject caseAny(Any object) {
		// Nothing to rewrite on "Any"
		return object;
	}

	// EclRefinement exists but has no properties to act on
	
	@Override
	public EObject caseOrRefinement(OrRefinement object) {
		EclRefinement left = object;
		OrRefinement current = null;
		
		// Since "object" is an "OR" refinement itself we will enter this loop at least once and rewrite its right side
		while (left instanceof OrRefinement) {
			current = (OrRefinement) left;
			current.setRight(rewrite(current.getRight()));
			left = current.getLeft();
		}
		
		// Rewrite the left side of the last "OR" refinement that we have visited (which may be "object" itself)
		current.setLeft(rewrite(left));
		return object;
	}

	@Override
	public EObject caseAndRefinement(AndRefinement object) {
		EclRefinement left = object;
		AndRefinement current = null;
		
		while (left instanceof AndRefinement) {
			current = (AndRefinement) left;
			current.setRight(rewrite(current.getRight()));
			left = current.getLeft();
		}
		
		current.setLeft(rewrite(left));
		return object;
	}
	
	// SubRefinement is "purely abstract", it has no corresponding Java class

	@Override
	public EObject caseNestedRefinement(NestedRefinement object) {
		object.setNested(rewrite(object.getNested()));
		return object;
	}

	@Override
	public EObject caseEclAttributeGroup(EclAttributeGroup object) {
		object.setRefinement(rewrite(object.getRefinement()));
		return object;
	}

	// EclAttributeSet is "purely abstract", it has no corresponding Java class
	
	// OrAttributeSet and AndAttributeSet are skipped (for now)
	
	@Override
	public EObject caseAttributeConstraint(AttributeConstraint object) {
		object.setAttribute(rewrite(object.getAttribute()));
		object.setComparison(rewrite(object.getComparison()));
		return object;
	}
	
	// Cardinality has no properties to act on
	
	// Comparison is "purely abstract", it has no corresponding Java class

	@Override
	public EObject caseAttributeComparison(AttributeComparison object) {
		final String op = object.getOp();
		final Operator operator = Operator.fromString(op);
		final ExpressionConstraint rewrittenValue = rewrite(object.getValue());
		
		if (Operator.NOT_EQUALS.equals(operator)) {
			// replace "!= XYZ" with "= (* MINUS XYZ)"
			final ExclusionExpressionConstraint newExclusion = EclFactory.eINSTANCE.createExclusionExpressionConstraint();
			newExclusion.setLeft(EclFactory.eINSTANCE.createAny());
			newExclusion.setRight(rewrittenValue);
			
			final NestedExpression newNestedExpression = EclFactory.eINSTANCE.createNestedExpression();
			newNestedExpression.setNested(newExclusion);
			
			object.setOp(Operator.EQUALS.toString());
			object.setValue(newNestedExpression);
		} else {
			// rewrite the value only otherwise
			object.setValue(rewrittenValue);
		}
		
		return object;
	}
	
	// DataTypeComparison and subtypes have no properties to rewrite

	@Override
	public EObject caseNestedExpression(NestedExpression object) {
		object.setNested(rewrite(object.getNested()));
		return object;
	}

	@Override
	public EObject caseFilterConstraint(FilterConstraint object) {
		object.setFilter(rewrite(object.getFilter()));
		return object;
	}

	@Override
	public EObject caseDisjunctionFilter(DisjunctionFilter object) {
		Filter left = object;
		DisjunctionFilter current = null;
		
		while (left instanceof DisjunctionFilter) {
			current = (DisjunctionFilter) left;
			current.setRight(rewrite(current.getRight()));
			left = current.getLeft();
		}
		
		current.setLeft(rewrite(left));
		return object;
	}

	@Override
	public EObject caseConjunctionFilter(ConjunctionFilter object) {
		Filter left = object;
		ConjunctionFilter current = null;
		
		while (left instanceof ConjunctionFilter) {
			current = (ConjunctionFilter) left;
			current.setRight(rewrite(current.getRight()));
			left = current.getLeft();
		}
		
		current.setLeft(rewrite(left));
		return object;
	}

	@Override
	public EObject caseNestedFilter(NestedFilter object) {
		object.setNested(rewrite(object.getNested()));
		return object;
	}
	
	// PropertyFilter has no properties to rewrite
	
	// TermFilter has no properties to rewrite
	
	@Override
	public EObject caseLanguageFilter(LanguageFilter object) {
		// Make referenced language codes unique
		final List<String> languageCodes = object.getLanguageCodes();
		final Set<String> uniqueLanguageCodes = newHashSet();
		languageCodes.removeIf(lc -> !uniqueLanguageCodes.add(lc));
		return object;
	}
	
	@Override
	public EObject caseTypeIdFilter(TypeIdFilter object) {
		object.setType(rewrite(object.getType()));
		return object;
	}

	@Override
	public EObject caseTypeTokenFilter(TypeTokenFilter object) {
		// Make referenced description type tokens unique
		final List<String> tokens = object.getTokens();
		final Set<String> uniqueTokens = newHashSet();
		tokens.removeIf(t -> !uniqueTokens.add(t));
		return object;
	}

	@Override
	public EObject caseDialectIdFilter(DialectIdFilter object) {
		// TODO: Make language reference set ID - acceptability pairs unique as well?
		final List<Dialect> dialects = object.getDialects();
		for (int i = 0; i < dialects.size(); i++) {
			dialects.set(i, rewrite(dialects.get(i)));
		}
		return object;
	}

	@Override
	public EObject caseDialectAliasFilter(DialectAliasFilter object) {
		// TODO: Make dialect alias - acceptability pairs unique as well?		
		final List<DialectAlias> dialects = object.getDialects();
		for (int i = 0; i < dialects.size(); i++) {
			dialects.set(i, rewrite(dialects.get(i)));
		}
		return object;
	}

	// IdFilter has no properties to rewrite (IDs are too vague to act on them)
	
	@Override
	public EObject caseDefinitionStatusIdFilter(DefinitionStatusIdFilter object) {
		object.setDefinitionStatus(rewrite(object.getDefinitionStatus()));
		return object;
	}
	
	@Override
	public EObject caseDefinitionStatusTokenFilter(DefinitionStatusTokenFilter object) {
		// TODO: Make referenced definition status tokens unique?
		return super.caseDefinitionStatusTokenFilter(object);
	}

	@Override
	public EObject caseModuleFilter(ModuleFilter object) {
		object.setModuleId(rewrite(object.getModuleId()));
		return object;
	}

	// EffectiveTimeFilter has no properties to rewrite
	
	// ActiveFilter has no properties to rewrite
	
	// SemanticTagFilter has no properties to rewrite
	
	@Override
	public EObject casePreferredInFilter(PreferredInFilter object) {
		object.setLanguageRefSetId(rewrite(object.getLanguageRefSetId()));
		return object;
	}

	@Override
	public EObject caseAcceptableInFilter(AcceptableInFilter object) {
		object.setLanguageRefSetId(rewrite(object.getLanguageRefSetId()));
		return object;
	}

	@Override
	public EObject caseLanguageRefSetFilter(LanguageRefSetFilter object) {
		object.setLanguageRefSetId(rewrite(object.getLanguageRefSetId()));
		return object;
	}

	@Override
	public EObject caseCaseSignificanceFilter(CaseSignificanceFilter object) {
		object.setCaseSignificanceId(rewrite(object.getCaseSignificanceId()));
		return object;
	}
	
	@Override
	public EObject caseMemberFieldFilter(MemberFieldFilter object) {
		object.setComparison(rewriteMemberFieldComparison(object.getComparison()));
		return object;
	}

	private Comparison rewriteMemberFieldComparison(Comparison comparison) {
		/*
		 * XXX: For AttributeComparisons the "!=" operator should _not_ be rewritten to
		 * "= (* MINUS ...)" in this context, so we will not be calling "rewrite" on the
		 * entire instance. This would in turn lead to caseAttributeComparison() getting called.
		 */
		if (comparison instanceof AttributeComparison ac) {
			ac.setValue(rewrite(ac.getValue()));
			return ac;
		} else {
			return rewrite(comparison);
		}
	}

	@Override
	public EObject caseDialect(Dialect object) {
		object.setLanguageRefSetId(rewrite(object.getLanguageRefSetId()));
		object.setAcceptability(rewrite(object.getAcceptability()));
		return object;
	}

	@Override
	public EObject caseDialectAlias(DialectAlias object) {
		object.setAcceptability(rewrite(object.getAcceptability()));
		return object;
	}

	@Override
	public EObject caseAcceptability(Acceptability object) {
		object.setAcceptabilities(rewrite(object.getAcceptabilities()));
		return object;
	}

	@Override
	public EObject caseHistorySupplement(HistorySupplement object) {
		object.setHistory(rewrite(object.getHistory()));
		return object;
	}

	@Override
	public EObject defaultCase(EObject object) {
		return object;
	}

	// Convenience method that casts the result back to the input object's type
	@SuppressWarnings("unchecked")
	public <T extends EObject> T rewrite(T object) {
		return (object == null) ? null : (T) doSwitch(object);
	}
	
}
