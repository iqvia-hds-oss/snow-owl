package scripts;

import com.b2international.index.query.Expressions
import com.b2international.index.query.Query
import com.b2international.index.query.Expressions.ExpressionBuilder
import com.b2international.index.revision.RevisionSearcher
import com.b2international.snowowl.core.ComponentIdentifier
import com.b2international.snowowl.core.date.EffectiveTimes
import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts
import com.b2international.snowowl.snomed.core.domain.SnomedConcept
import com.b2international.snowowl.snomed.datastore.index.entry.*
import com.google.common.collect.HashMultimap
import com.google.common.collect.Lists
import com.google.common.collect.Multimap

RevisionSearcher searcher = ctx.service(RevisionSearcher.class)

List<ComponentIdentifier> issues = Lists.newArrayList();

ExpressionBuilder filterExpressionBuilder = Expressions.bool()
	.filter(SnomedComponentDocument.Expressions.active())
	.filter(SnomedComponentDocument.Expressions.modules([Concepts.MODULE_SCT_CORE, Concepts.MODULE_SCT_MODEL_COMPONENT]))

if (params.isUnpublishedOnly) {
	filterExpressionBuilder.filter(SnomedDocument.Expressions.effectiveTime(EffectiveTimes.UNSET_EFFECTIVE_TIME))
}

searcher.stream(
	Query.select(Object.class)
		.from(SnomedConceptDocument.class)
		.fields(SnomedConceptDocument.Fields.ID, SnomedConceptDocument.Fields.PARENTS, SnomedConceptDocument.Fields.STATED_PARENTS)
		.where(filterExpressionBuilder.build())
		.limit(10_000)
		.build())
.each { hits ->
	Multimap<String, String> parentMap = HashMultimap.create();
	Set<String> conceptIds = [];
	hits.each { hit ->
		final String id = hit.id;
		List<String> parentIds = hit.parents.collect { String.valueOf(it) }
		List<String> statedParentIds = hit.statedParents.collect { String.valueOf(it) }
		parentIds.each { parentMap.put(it, id) };
		statedParentIds.each { parentMap.put(it, id) };
		conceptIds.add(id);
	}

	Set<String> coreParents =
		searcher.search(
			Query.select(String.class)
				.from(SnomedConceptDocument.class)
				.fields(SnomedConceptDocument.Fields.ID)
				.where(Expressions.bool()
					.filter(SnomedConceptDocument.Expressions.ids(parentMap.keySet()))
					.filter(SnomedComponentDocument.Expressions.active())
					.filter(SnomedComponentDocument.Expressions.modules([Concepts.MODULE_SCT_CORE, Concepts.MODULE_SCT_MODEL_COMPONENT]))
					.build())
				.limit(parentMap.keySet().size())
				.build()).toSet();
	coreParents.each { conceptIds.removeAll(parentMap.get(it)) }
	conceptIds.remove(Concepts.ROOT_CONCEPT)
	conceptIds.each { 
		issues.add(ComponentIdentifier.of(SnomedConcept.TYPE, it)) 
	}
}

return issues