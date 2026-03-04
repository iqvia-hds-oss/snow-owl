/*
 * Copyright 2018-2023 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.snomed.reasoner.converter;

import static com.b2international.snowowl.snomed.reasoner.index.ConcreteDomainChangeDocument.Fields.REFERENCED_COMPONENT_ID;
import static com.b2international.snowowl.snomed.reasoner.index.RelationshipChangeDocument.Fields.SOURCE_ID;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.b2international.commons.http.ExtendedLocale;
import com.b2international.commons.options.Options;
import com.b2international.snowowl.core.branch.Branch;
import com.b2international.snowowl.core.branch.Branches;
import com.b2international.snowowl.core.domain.RepositoryContext;
import com.b2international.snowowl.core.repository.RepositoryRequests;
import com.b2international.snowowl.core.request.BaseResourceConverter;
import com.b2international.snowowl.snomed.reasoner.domain.*;
import com.b2international.snowowl.snomed.reasoner.index.ClassificationTaskDocument;
import com.b2international.snowowl.snomed.reasoner.request.ClassificationRequests;
import com.b2international.snowowl.snomed.reasoner.request.ConcreteDomainChangeSearchRequestBuilder;
import com.b2international.snowowl.snomed.reasoner.request.EquivalentConceptSetSearchRequestBuilder;
import com.b2international.snowowl.snomed.reasoner.request.RelationshipChangeSearchRequestBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

/**
 * @since 7.0
 */
public final class ClassificationTaskConverter extends BaseResourceConverter<ClassificationTaskDocument, ClassificationTask, ClassificationTasks> {

	private static final Set<ClassificationStatus> SAVE_AND_SCHEDULED_STATUSES = ImmutableSet.of(
			ClassificationStatus.SAVING_IN_PROGRESS,
			ClassificationStatus.SAVED,
			ClassificationStatus.FAILED,
			ClassificationStatus.SCHEDULED);

	public ClassificationTaskConverter(final RepositoryContext context, final Options expand, final List<ExtendedLocale> locales) {
		super(context, expand, locales);
	}
	
	@Override
	protected RepositoryContext context() {
		return (RepositoryContext) super.context();
	}

	@Override
	protected ClassificationTasks createCollectionResource(final List<ClassificationTask> results, final String searchAfter, final int limit, final int total) {
		return new ClassificationTasks(results, searchAfter, limit, total);
	}

	@Override
	protected ClassificationTask toResource(final ClassificationTaskDocument entry) {
		final ClassificationTask resource = new ClassificationTask();
		resource.setId(entry.getId());
		resource.setUserId(entry.getUserId());
		resource.setReasonerId(entry.getReasonerId());
		resource.setBranch(entry.getBranch());
		resource.setTimestamp(entry.getTimestamp());
		resource.setStatus(entry.getStatus());
		resource.setCreationDate(entry.getCreationDate());
		resource.setCompletionDate(entry.getCompletionDate());
		resource.setSaveDate(entry.getSaveDate());
		resource.setInferredRelationshipChangesFound(entry.getHasInferredChanges());
		resource.setRedundantStatedRelationshipsFound(entry.getHasRedundantStatedChanges());
		resource.setEquivalentConceptsFound(entry.getHasEquivalentConcepts());
		return resource;
	}
	
	@Override
	protected void alterResults(List<ClassificationTask> results) {
		final Multimap<String, ClassificationTask> tasksByBranch = Multimaps.index(results, ClassificationTask::getBranch);
		final Branches branches = RepositoryRequests.branching()
				.prepareSearch()
				.setLimit(tasksByBranch.keySet().size())
				.filterByIds(tasksByBranch.keySet())
				.build()
				.execute(context());

		final Map<String, Long> headTimestamps = branches.stream()
				.collect(Collectors.toMap(Branch::path, Branch::headTimestamp));

		// Overwrite stored status if the branch has moved forward in the meantime, except if the task is saved
		for (final ClassificationTask task : results) {
			
			if (SAVE_AND_SCHEDULED_STATUSES.contains(task.getStatus())) {
				continue;
			}

			if (task.getTimestamp() < headTimestamps.get(task.getBranch())) {
				task.setStatus(ClassificationStatus.STALE);
			}
		}
	}

	@Override
	public void expand(final List<ClassificationTask> results) {
		final Set<String> classificationTaskIds = results.stream()
				.map(ClassificationTask::getId)
				.collect(Collectors.toSet());

		expandEquivalentConceptSets(results, classificationTaskIds);
		expandRelationshipChanges(results, classificationTaskIds);
		expandConcreteDomainChanges(results, classificationTaskIds);
	}

	private void expandEquivalentConceptSets(final List<ClassificationTask> results, final Set<String> classificationTaskIds) {
		if (!expand().containsKey(ClassificationTask.Expand.EQUIVALENT_CONCEPT_SETS)) {
			return;
		}

		final Options expandOptions = expand().get(ClassificationTask.Expand.EQUIVALENT_CONCEPT_SETS, Options.class);
		for (final ClassificationTask classificationTask : results) {
			final EquivalentConceptSetSearchRequestBuilder builder = ClassificationRequests.prepareSearchEquivalentConceptSet()
					.filterByClassificationId(classificationTask.getId())
					.setExpand(expandOptions.get("expand", Options.class))
					.setLocales(locales())
					.setLimit(getLimit(expandOptions));			
			final EquivalentConceptSets equivalentConceptSets = builder.build().execute(context());
			classificationTask.setEquivalentConceptSets(equivalentConceptSets);
		}
	}

	private void expandRelationshipChanges(final List<ClassificationTask> results, final Set<String> classificationTaskIds) {
		if (!expand().containsKey(ClassificationTask.Expand.RELATIONSHIP_CHANGES)) {
			return;
		}

		final Options expandOptions = expand().get(ClassificationTask.Expand.RELATIONSHIP_CHANGES, Options.class);
		
		for (final ClassificationTask classificationTask : results) {
			final RelationshipChangeSearchRequestBuilder builder = ClassificationRequests.prepareSearchRelationshipChange()
					.filterByClassificationId(classificationTask.getId());
	
			if (expandOptions.containsKey(SOURCE_ID)) {
				builder.filterBySourceId(expandOptions.getCollection(SOURCE_ID, String.class));
			}
			
			final RelationshipChanges relationshipChanges = builder
					.setExpand(expandOptions.get("expand", Options.class))
					.setLimit(getLimit(expandOptions))
					.setLocales(locales())
					.sortBy(SOURCE_ID)
					.build()
					.execute(context());
			classificationTask.setRelationshipChanges(relationshipChanges);
		}
	}

	private void expandConcreteDomainChanges(final List<ClassificationTask> results, final Set<String> classificationTaskIds) {
		if (!expand().containsKey(ClassificationTask.Expand.CONCRETE_DOMAIN_CHANGES)) {
			return;
		}

		final Options expandOptions = expand().get(ClassificationTask.Expand.CONCRETE_DOMAIN_CHANGES, Options.class);
		
		for (final ClassificationTask classificationTask : results) {
			final ConcreteDomainChangeSearchRequestBuilder builder = ClassificationRequests.prepareSearchConcreteDomainChange()
					.filterByClassificationId(classificationTask.getId())
					.setExpand(expandOptions.get("expand", Options.class))
					.sortBy(REFERENCED_COMPONENT_ID)
					.setLocales(locales())
					.setLimit(getLimit(expandOptions));
			
			if (expandOptions.containsKey(REFERENCED_COMPONENT_ID)) {
				builder.filterByReferencedComponentId(expandOptions.get(REFERENCED_COMPONENT_ID, String.class));
			}

			final ConcreteDomainChanges concreteDomainChanges = builder.build().execute(context());
			classificationTask.setConcreteDomainChanges(concreteDomainChanges);
		}
	}
}
