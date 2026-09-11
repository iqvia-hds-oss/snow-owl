/*
 * Copyright 2019-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.snomed.datastore.index.change;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;

import com.b2international.snowowl.core.repository.EffectiveTimeChangeProcessorBase;
import com.b2international.snowowl.snomed.datastore.index.entry.*;

import jakarta.validation.UnexpectedTypeException;

/**
 * @since 7.1
 */
public final class ComponentEffectiveTimeRestoreChangeProcessor extends EffectiveTimeChangeProcessorBase<SnomedDocument> {

	protected ComponentEffectiveTimeRestoreChangeProcessor(final Logger log) {
		super("effective time restore", SnomedDocument.class, log);
	}

	@Override
	protected boolean isReleased(final SnomedDocument doc) {
		return doc.isReleased();
	}

	@Override
	protected Long getEffectiveTime(final SnomedDocument doc) {
		return doc.getEffectiveTime();
	}

	@Override
	protected boolean canRestoreEffectiveTime(final SnomedDocument componentToRestore, final SnomedDocument previousVersion) {
		if (!canRestoreEffectiveTime(componentToRestore, previousVersion,
			SnomedDocument::isActive,
			SnomedDocument::getModuleId)) {
			return false;
		}

		if (componentToRestore instanceof SnomedConceptDocument && previousVersion instanceof SnomedConceptDocument) {
			final SnomedConceptDocument conceptToRestore = (SnomedConceptDocument) componentToRestore;
			final SnomedConceptDocument previousConcept = (SnomedConceptDocument) previousVersion;

			return canRestoreEffectiveTime(conceptToRestore, previousConcept,
				SnomedConceptDocument::getDefinitionStatusId);
		}

		if (componentToRestore instanceof SnomedDescriptionIndexEntry && previousVersion instanceof SnomedDescriptionIndexEntry) {
			final SnomedDescriptionIndexEntry descriptionToRestore = (SnomedDescriptionIndexEntry) componentToRestore;
			final SnomedDescriptionIndexEntry previousDescription = (SnomedDescriptionIndexEntry) previousVersion;

			return canRestoreEffectiveTime(descriptionToRestore, previousDescription,
				SnomedDescriptionIndexEntry::getTerm,
				SnomedDescriptionIndexEntry::getCaseSignificanceId);
		}

		if (componentToRestore instanceof SnomedRelationshipIndexEntry && previousVersion instanceof SnomedRelationshipIndexEntry) {
			final SnomedRelationshipIndexEntry relationshipToRestore = (SnomedRelationshipIndexEntry) componentToRestore;
			final SnomedRelationshipIndexEntry previousRelationship = (SnomedRelationshipIndexEntry) previousVersion;

			return canRestoreEffectiveTime(relationshipToRestore, previousRelationship,
				SnomedRelationshipIndexEntry::getRelationshipGroup,
				SnomedRelationshipIndexEntry::getUnionGroup,
				SnomedRelationshipIndexEntry::getCharacteristicTypeId,
				SnomedRelationshipIndexEntry::getModifierId);
		}

		if (componentToRestore instanceof SnomedRefSetMemberIndexEntry && previousVersion instanceof SnomedRefSetMemberIndexEntry) {
			final SnomedRefSetMemberIndexEntry memberToRestore = (SnomedRefSetMemberIndexEntry) componentToRestore;
			final SnomedRefSetMemberIndexEntry previousMember = (SnomedRefSetMemberIndexEntry) previousVersion;

			final Map<String, Object> additionalFieldsToRestore = memberToRestore.getAdditionalFields();
			final Map<String, Object> additionalFieldsFromPrevious = previousMember.getAdditionalFields();

			// We will be using the field names from the current member to restore (they should not change between versions)
			final List<String> additionalFields = additionalFieldsToRestore.keySet()
				.stream()
				.sorted()
				.collect(Collectors.toList());

			// Create a specialized accessor for each named additional field
			@SuppressWarnings("unchecked")
			final Function<SnomedRefSetMemberIndexEntry, Object>[] additionalFieldAccessors = additionalFields.stream()
				.map(fieldName -> (Function<SnomedRefSetMemberIndexEntry, Object>) m -> {
					if (m == memberToRestore) {
						return additionalFieldsToRestore.get(fieldName);
					} else if (m == previousMember) {
						return additionalFieldsFromPrevious.get(fieldName);
					} else {
						throw new IllegalArgumentException("Unexpected member instance: " + m);
					}
				})
				.toArray(Function[]::new);

			return canRestoreEffectiveTime(memberToRestore, previousMember, additionalFieldAccessors);
		}

		throw new UnexpectedTypeException("Unexpected component type '" + componentToRestore.getClass() + "'.");
	}

	@SafeVarargs
	private <T extends SnomedDocument> boolean canRestoreEffectiveTime(final T current, final T previous, final Function<T, Object>... accessors) {
		return Stream.of(accessors).allMatch(acc -> Objects.equals(acc.apply(current), acc.apply(previous)));
	}

	@Override
	protected SnomedDocument copyWithEffectiveTime(final SnomedDocument doc, final long effectiveTime) {
		return toBuilder(doc).effectiveTime(effectiveTime).build();
	}

	private SnomedDocument.Builder<?, ?> toBuilder(final SnomedDocument doc) {
		if (doc instanceof SnomedConceptDocument) {
			return SnomedConceptDocument.builder((SnomedConceptDocument) doc);
		} else if (doc instanceof SnomedDescriptionIndexEntry) {
			return SnomedDescriptionIndexEntry.builder((SnomedDescriptionIndexEntry) doc);
		} else if (doc instanceof SnomedRelationshipIndexEntry) {
			return SnomedRelationshipIndexEntry.builder((SnomedRelationshipIndexEntry) doc);
		} else if (doc instanceof SnomedRefSetMemberIndexEntry) {
			return SnomedRefSetMemberIndexEntry.builder((SnomedRefSetMemberIndexEntry) doc);
		} else {
			throw new UnsupportedOperationException("Not implemented for: " + doc);
		}
	}

}
