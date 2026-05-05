/*
 * Copyright 2011-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.snomed.datastore.request.dsv;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IProgressMonitor;

import com.b2international.commons.http.ExtendedLocale;
import com.b2international.snowowl.core.date.Dates;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.core.domain.BranchContext;
import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.core.domain.SnomedConcept;
import com.b2international.snowowl.snomed.core.domain.SnomedConcepts;
import com.b2international.snowowl.snomed.core.domain.SnomedRelationship;
import com.b2international.snowowl.snomed.datastore.internal.rf2.AbstractSnomedDsvExportItem;
import com.b2international.snowowl.snomed.datastore.internal.rf2.ComponentIdSnomedDsvExportItem;
import com.b2international.snowowl.snomed.datastore.internal.rf2.SnomedDsvExportItemType;
import com.b2international.snowowl.snomed.datastore.internal.rf2.SnomedRefSetDSVExportModel;
import com.b2international.snowowl.snomed.datastore.request.SnomedConceptRequestCache;
import com.b2international.snowowl.snomed.datastore.request.SnomedConceptSearchRequestBuilder;
import com.b2international.snowowl.snomed.datastore.request.SnomedRequests;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.*;

/**
 * Implements the export process of the DSV export for simple type reference sets. 
 */
public class SnomedSimpleTypeRefSetDSVExporter implements IRefSetDSVExporter {

	// Keep keys sorted but allow non-unique values
	private static final Supplier<ListMultimap<String, String>> MULTIMAP_FACTORY = () -> MultimapBuilder
		.treeKeys()
		.arrayListValues()
		.build();

	private static final String HEADER_EXPAND = "descriptions(active:true), "
		+ "relationships(active:true)";
	
	private static final String DATA_EXPAND = "pt(), "
		+ "descriptions(active:true), "
		+ "relationships(active:true, expand(destination(expand(pt()))))";

	private static final SortedMultiset<Integer> NO_OCCURRENCES = ImmutableSortedMultiset.of();
	
	static interface ConceptStreamFactory {
		
		Stream<SnomedConcepts> getConceptStream(
			String expand, 
			List<ExtendedLocale> locales, 
			BranchContext context, 
			boolean includeInactiveMembers,
			String refSetId
		);
		
		ConceptStreamFactory DEFAULT = (expand, locales, context, includeInactiveMembers, refSetId) -> {
			final SnomedConceptSearchRequestBuilder builder = SnomedRequests.prepareSearchConcept()
				.setLocales(locales)
				.setExpand(expand)
				.setLimit(context.getPageSize());
			
			if (includeInactiveMembers) {
				builder.isMemberOf(refSetId);
			} else {
				builder.isActiveMemberOf(refSetId);
			}
			
			return builder.stream(context);
		};
	}
	
	static interface AncestorCollector {
		
		SnomedConcepts getAncestors(
			List<ExtendedLocale> locales,
			String ancestorId,
			BranchContext context 
		);
		
		AncestorCollector DEFAULT = (locales, ancestorId, context) -> {
			return SnomedRequests.prepareSearchConcept()
				.all()
				.setLocales(locales)
				.filterByAncestor(ancestorId)
				.setExpand("pt()")
				.build()
				.execute(context);
		};
	}
	
	private final BranchContext context;
	private final ConceptStreamFactory conceptStreamFactory;
	private final AncestorCollector ancestorCollector;
	
	private final String refSetId;
	private final boolean includeDescriptionId;
	private final boolean includeRelationshipId;
	private final boolean includeInactiveMembers;
	private final List<AbstractSnomedDsvExportItem> exportItems;
	private final List<ExtendedLocale> locales;
	private final Joiner joiner;
	private final String lineSeparator;

	private Set<String> descriptionTypeIds;
	private Set<String> relationshipTypeIds;

	private Multiset<String> descriptionCount; // maximum number of descriptions by type ID
	private Map<String, SortedMultiset<Integer>> relationshipCount; // maximum number of properties by type ID and relationship group

	/**
	 * Creates a new instance with the export parameters.
	 * 
	 * @param exportSetting
	 */
	public SnomedSimpleTypeRefSetDSVExporter(final BranchContext context, final SnomedRefSetDSVExportModel exportSetting) {
		this(context, ConceptStreamFactory.DEFAULT, AncestorCollector.DEFAULT, exportSetting);
	}
	
	@VisibleForTesting
	SnomedSimpleTypeRefSetDSVExporter(
		final BranchContext context, 
		final ConceptStreamFactory conceptStreamFactory, 
		final AncestorCollector ancestorCollector,
		final SnomedRefSetDSVExportModel exportSetting
	) { 
		this.context = context;
		this.conceptStreamFactory = conceptStreamFactory;
		this.ancestorCollector = ancestorCollector;
		
		this.refSetId = exportSetting.getRefSetId();
		this.includeDescriptionId = exportSetting.includeDescriptionId();
		this.includeRelationshipId = exportSetting.includeRelationshipTargetId();
		this.includeInactiveMembers = exportSetting.includeInactiveMembers();
		this.exportItems = exportSetting.getExportItems();
		this.locales = exportSetting.getLocales();
		this.joiner = Joiner.on(exportSetting.getDelimiter());
		this.lineSeparator = System.lineSeparator();
	}

	/**
	 * Executes the export to delimiter separated values.
	 * 
	 * @param monitor
	 * @return The file with the exported values.
	 */
	@Override
	public File executeDSVExport(final IProgressMonitor monitor) throws IOException {
		monitor.beginTask("Export RefSet to DSV...", 100);
		final Path exportPath = Files.createTempFile("dsv-export-" + refSetId + Dates.now(), ".csv");
		try {
			try (BufferedWriter writer = Files.newBufferedWriter(exportPath, StandardCharsets.UTF_8)) {
				computeHeader();
				writeHeader(writer);
				writeValues(monitor, writer);
			}
			return exportPath.toFile();
		} finally {
			if (null != monitor) { 
				monitor.done(); 
			}
		}
	}

	/*
	 * Fetches members of the specified reference set
	 */
	private Stream<SnomedConcepts> getConceptStream(final String expand) {
		return conceptStreamFactory.getConceptStream(expand, locales, context, includeInactiveMembers, refSetId);
	}

	/*
	 * Finds the maximum number of occurrences for each description, relationship; generates headers. 
	 */
	private void computeHeader() {
		final SetMultimap<SnomedDsvExportItemType, String> relevantTypeIds = HashMultimap.create(); 

		for (final AbstractSnomedDsvExportItem exportItem : exportItems) {
			switch (exportItem.getType()) {
				case DESCRIPTION:
				case RELATIONSHIP:
					final ComponentIdSnomedDsvExportItem componentIdItem = (ComponentIdSnomedDsvExportItem) exportItem;
					relevantTypeIds.put(exportItem.getType(), componentIdItem.getComponentId());
					break;
				
				default:
					// We are not interested in any other export item type at this time
					break;
			}
		}
		
		descriptionTypeIds = relevantTypeIds.get(SnomedDsvExportItemType.DESCRIPTION);
		relationshipTypeIds = relevantTypeIds.get(SnomedDsvExportItemType.RELATIONSHIP);
		
		descriptionCount = HashMultiset.create();
		relationshipCount = newHashMap();
		
		getConceptStream(HEADER_EXPAND).forEachOrdered(this::computeHeader);
	}
	
	private void computeHeader(final SnomedConcepts chunk) {
		final Multiset<String> descriptionCountInChunk = HashMultiset.create(); 
		final Map<String, Multiset<Integer>> relationshipCountInChunk = newHashMap();
		
		for (final SnomedConcept concept : chunk) {

			// Collect description occurrence counts by type ID
			if (!descriptionTypeIds.isEmpty()) {
				concept.getDescriptions()
					.stream()
					.map(d -> d.getTypeId())
					.filter(typeId -> descriptionTypeIds.contains(typeId))
					.forEachOrdered(descriptionCountInChunk::add);
				
				if (!descriptionCountInChunk.isEmpty()) {
					updateOccurrences(descriptionCountInChunk, descriptionCount);
					descriptionCountInChunk.clear();
				}
			}

			// Collect relationship occurrence counts by type ID and group
			if (!relationshipTypeIds.isEmpty()) {
				concept.getRelationships()
					.stream()
					.filter(r -> {
						if (!isApplicableRelationship(r)) {
							return false;
						}
						
						final String typeId = r.getTypeId();
						return relationshipTypeIds.contains(typeId);
					})
					.forEachOrdered(r -> {
						final String typeId = r.getTypeId();
						final Integer relationshipGroup = r.getRelationshipGroup();
						registerProperty(relationshipCountInChunk, typeId, relationshipGroup);
					});
	
				if (!relationshipCountInChunk.isEmpty()) {
					updateOccurrences(relationshipCountInChunk, relationshipCount);
					relationshipCountInChunk.clear();
				}
			}
			
		}
	}

	private boolean isApplicableRelationship(final SnomedRelationship r) {
		// Allow inferred relationships regardless of group number
		final String characteristicTypeId = r.getCharacteristicTypeId();
		if (Concepts.INFERRED_RELATIONSHIP.equals(characteristicTypeId)) {
			return true;
		}
		
		// Allow additional relationships but only from group 0
		if (Concepts.ADDITIONAL_RELATIONSHIP.equals(characteristicTypeId)) {
			final int relationshipGroup = r.getRelationshipGroup();
			return (relationshipGroup == 0);
		}
	
		return false;
	}

	private static void updateOccurrences(
		final Map<String, Multiset<Integer>> source,
		final Map<String, SortedMultiset<Integer>> destination
	) {
		for (final Map.Entry<String, Multiset<Integer>> entry : source.entrySet()) {
			final String typeId = entry.getKey();
			final Multiset<Integer> sourceCountForTypeId = entry.getValue();
			final SortedMultiset<Integer> destinationCountForTypeId = destination.computeIfAbsent(typeId, key -> TreeMultiset.create());
			updateOccurrences(sourceCountForTypeId, destinationCountForTypeId);
		}
	}

	private static <T> void updateOccurrences(final Multiset<T> source, final Multiset<T> destination) {
		for (final Multiset.Entry<T> entry : source.entrySet()) {
			final T element = entry.getElement();
			final int sourceOccurrence = entry.getCount();
			final int destinationOccurrence = destination.count(element);
			
			if (sourceOccurrence > destinationOccurrence) {
				destination.setCount(element, sourceOccurrence);
			}
		}
	}

	private void registerProperty(
		final Map<String, Multiset<Integer>> propertyCountInChunk, 
		final String typeId,
		final Integer relationshipGroup
	) {
		// Multiset does not need to be sorted as maximum occurrence counts can be updated in arbitrary group order
		final Multiset<Integer> propertyCountForGroup = propertyCountInChunk.computeIfAbsent(typeId, key -> HashMultiset.create());
		propertyCountForGroup.add(relationshipGroup);
	}
	
	private void writeHeader(final BufferedWriter writer) throws IOException {
		final Map<String, String> descriptionTypeIdMap = createTypeIdMap(Concepts.DESCRIPTION_TYPE_ROOT_CONCEPT);

		// Includes both object and data attributes
		final Map<String, String> propertyTypeIdMap = createTypeIdMap(Concepts.CONCEPT_MODEL_ATTRIBUTE); 
		
		// First header row describes the export item itself
		final List<String> propertyHeader = newArrayList();
		
		/* 
		 * The second header row is only used when ID inclusion is requested - in this case the ID and the value to 
		 * be displayed are shown side-by-side
		 */
		final List<String> detailHeader = newArrayList();
		
		for (final AbstractSnomedDsvExportItem exportItem : exportItems) {
			switch (exportItem.getType()) {

				case DESCRIPTION: {
					final ComponentIdSnomedDsvExportItem descriptionItem = (ComponentIdSnomedDsvExportItem) exportItem;
					final String typeId = descriptionItem.getComponentId();
					final String displayName = descriptionTypeIdMap.getOrDefault(typeId, descriptionItem.getDisplayName());
					final int occurrences = descriptionCount.count(typeId);
					writeHeader(occurrences, propertyHeader, detailHeader, includeDescriptionId, displayName, "Term");
					break;
				}
					
				case RELATIONSHIP: {
					final ComponentIdSnomedDsvExportItem itemWithTypeId = (ComponentIdSnomedDsvExportItem) exportItem;
					final String typeId = itemWithTypeId.getComponentId();
					final String displayName = propertyTypeIdMap.getOrDefault(typeId, itemWithTypeId.getDisplayName());
					
					
					// ID can only be included for relationships (and only for these items will "Destination" appear in the second row)
					final Multiset<Integer> propertyCountForType = relationshipCount.getOrDefault(typeId, NO_OCCURRENCES);
					
					for (final Multiset.Entry<Integer> groupAndCount : propertyCountForType.entrySet()) {
						final int group = groupAndCount.getElement();
						final int occurrences = groupAndCount.getCount();
						final String groupTag = (group == 0) ? "" : String.format(" (AG%s)", group);
						final String groupedDisplayName = displayName + groupTag;
						writeHeader(occurrences, propertyHeader, detailHeader, includeRelationshipId, groupedDisplayName, "Destination");
					}
					
					break;
				}
				
				case PREFERRED_TERM:
					writeHeader(propertyHeader, detailHeader, includeDescriptionId, exportItem.getDisplayName(), "Term");
					break;
				
				default:
					writeHeader(propertyHeader, detailHeader, exportItem.getDisplayName());
					break;
			}
		}
		
		// Write the first row header to the file
		writer.write(joiner.join(propertyHeader));
		writer.write(lineSeparator);
		
		if (includeDescriptionId || includeRelationshipId) {
			// Add the second row header only if IDs were requested in the export
			writer.write(joiner.join(detailHeader));
			writer.write(lineSeparator);
		}
	}

	private void writeHeader(final List<String> propertyHeader, final List<String> detailHeader, final String propertyName) {
		writeHeader(propertyHeader, detailHeader, false, propertyName, "");
	}

	private void writeHeader(
		final int occurrences, 
		final List<String> propertyHeader, 
		final List<String> detailHeader, 
		final boolean includeId,
		final String propertyName, 
		final String detailName
	) {
		if (occurrences < 2) {
			// No numbered suffix required
			writeHeader(propertyHeader, detailHeader, includeId, propertyName, detailName);
		} else {
			// Add numbered suffixes to the property name (it should start at index 1)
			for (int j = 1; j <= occurrences; j++) {
				final String numberedPropertyName = String.format("%s (%s)", propertyName, j);
				writeHeader(propertyHeader, detailHeader, includeId, numberedPropertyName, detailName);								
			}
		}
	}
	
	private void writeHeader(
		final List<String> propertyHeader, 
		final List<String> detailHeader, 
		final boolean includeId, 
		final String propertyName, 
		final String detailName
	) {
		if (includeId) {
			// Add property name twice in the first row for each column when IDs need to be included
			propertyHeader.add(propertyName);
			propertyHeader.add(propertyName);
			// Add e.g. "ID" and "Term" for description columns in the second row  
			detailHeader.add("ID");
			detailHeader.add(detailName);
		} else {
			propertyHeader.add(propertyName);
			detailHeader.add("");
		}
	}

	private Map<String, String> createTypeIdMap(final String ancestorId) {
		return createTypeIdMap(ancestorCollector.getAncestors(locales, ancestorId, context));
	}

	private Map<String, String> createTypeIdMap(final SnomedConcepts concepts) {
		return concepts.stream()
			.collect(Collectors.toMap(
				c -> c.getId(), 
				c -> getPreferredTerm(c)));
	}

	private void writeValues(final IProgressMonitor monitor, final BufferedWriter writer) throws IOException {
		final Iterable<SnomedConcepts> chunks = () -> getConceptStream(DATA_EXPAND).iterator();
		final Optional<SnomedConceptRequestCache> cache = Optional.ofNullable(context)
			.flatMap(v -> v.optionalService(SnomedConceptRequestCache.class));
		
		for (final SnomedConcepts chunk : chunks) {
			// make sure we compute all requested expansions before we move forward
			cache.ifPresent(service -> service.compute(context));
			writeValues(writer, chunk);
			monitor.worked(chunk.getItems().size());
		}
	}
		
	private void writeValues(final BufferedWriter writer, final SnomedConcepts chunk) throws IOException {
		final List<String> dataRow = newArrayList();
		
		for (final SnomedConcept concept : chunk) {
			for (final AbstractSnomedDsvExportItem exportItem : exportItems) {
				switch (exportItem.getType()) {
				
					case DESCRIPTION: {
						final ComponentIdSnomedDsvExportItem descriptionItem = (ComponentIdSnomedDsvExportItem) exportItem;
						final String typeId = descriptionItem.getComponentId();
						final int occurrences = descriptionCount.count(typeId);
						
						// Description ID keys, description term for values
						
						final Multimap<String, String> termsById = concept.getDescriptions()
							.stream()
							.filter(d -> typeId.equals(d.getTypeId()))
							.collect(Multimaps.toMultimap(
								d -> d.getId(),
								d -> d.getTerm(),
								MULTIMAP_FACTORY
							));
						
						addCells(occurrences, dataRow, includeDescriptionId, termsById);
						break;
					}

					case RELATIONSHIP: {
						final ComponentIdSnomedDsvExportItem relationshipItem = (ComponentIdSnomedDsvExportItem) exportItem;
						final String typeId = relationshipItem.getComponentId();
						final Multiset<Integer> propertyCountForType = relationshipCount.getOrDefault(typeId, NO_OCCURRENCES);
						
						for (final Multiset.Entry<Integer> groupAndCount : propertyCountForType.entrySet()) {
							final Integer group = groupAndCount.getElement();
							final int occurrences = groupAndCount.getCount();

							// Destination ID as key, destination concept terms for values
							// - OR -
							// Empty string as key, relationship value literals for values
							
							final Multimap<String, String> destinationsById = concept.getRelationships()
								.stream()
								.filter(r -> {
									if (!isApplicableRelationship(r)) {
										return false;	
									}
									
									return Objects.equals(r.getRelationshipGroup(), group) 
										&& Objects.equals(r.getTypeId(), typeId);
								})
								.collect(Multimaps.toMultimap(
									r -> r.hasValue() ? "" : r.getDestinationId(),
									r -> r.hasValue() ? r.getValue() : getPreferredTerm(r.getDestination()),
									MULTIMAP_FACTORY
								));
							
							addCells(occurrences, dataRow, includeRelationshipId, destinationsById);
						}
						
						break;
					}
					
					case PREFERRED_TERM:
						if (includeDescriptionId) {
							dataRow.add(getPreferredTermId(concept));
							dataRow.add(getPreferredTerm(concept));
						} else {
							dataRow.add(getPreferredTerm(concept));
						}
						
						break;

					case CONCEPT_ID:
						dataRow.add(concept.getId());
						break;

					case MODULE: 
						dataRow.add(concept.getModuleId());
						break;

					case EFFECTIVE_TIME:
						dataRow.add(EffectiveTimes.format(concept.getEffectiveTime()));
						break;

					case STATUS_LABEL:
						dataRow.add(concept.isActive() ? "Active" : "Inactive");
						break;

					case DEFINITION_STATUS: 
						dataRow.add(concept.getDefinitionStatusId());
						break;

					default:
						break;
				}
			}
			
			writer.write(joiner.join(dataRow));
			writer.write(lineSeparator);
			dataRow.clear();
		}
	}

	private void addCells(int occurrences, final List<String> dataRow, final boolean includeId, final Multimap<String, String> valuesById) {
		if (includeId) {
			
			for (final String id : valuesById.keySet()) {
				final List<String> valuesForId = Ordering.natural().sortedCopy(valuesById.get(id));
				for (final String value : valuesForId) {
					dataRow.add(id);
					dataRow.add(value);
					occurrences--;
				}
			}
			
			while (occurrences > 0) {
				dataRow.add("");
				dataRow.add("");
				occurrences--;
			}
			
		} else {
			
			final List<String> sortedValues = Ordering.natural().sortedCopy(valuesById.values());
			for (final String value : sortedValues) {
				dataRow.add(value);
				occurrences--;
			}
			
			while (occurrences > 0) {
				dataRow.add("");
				occurrences--;
			}
		}
	}

	private static String getPreferredTerm(final SnomedConcept concept) {
		return (concept.getPt() == null) ? "" : concept.getPt().getTerm();
	}
	
	private static String getPreferredTermId(final SnomedConcept concept) {
		return (concept.getPt() == null) ? "" : concept.getPt().getId();
	}
}
