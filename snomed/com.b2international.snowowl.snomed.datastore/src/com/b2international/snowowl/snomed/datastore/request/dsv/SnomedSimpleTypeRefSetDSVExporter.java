/*
 * Copyright 2011-2023 B2i Healthcare, https://b2ihealthcare.com
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IProgressMonitor;

import com.b2international.commons.http.ExtendedLocale;
import com.b2international.snowowl.core.date.Dates;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.core.domain.BranchContext;
import com.b2international.snowowl.core.request.SearchResourceRequest.Sort;
import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.common.SnomedRf2Headers;
import com.b2international.snowowl.snomed.core.domain.SnomedConcept;
import com.b2international.snowowl.snomed.core.domain.SnomedConcepts;
import com.b2international.snowowl.snomed.datastore.index.entry.SnomedConceptDocument;
import com.b2international.snowowl.snomed.datastore.internal.rf2.AbstractSnomedDsvExportItem;
import com.b2international.snowowl.snomed.datastore.internal.rf2.ComponentIdSnomedDsvExportItem;
import com.b2international.snowowl.snomed.datastore.internal.rf2.DatatypeSnomedDsvExportItem;
import com.b2international.snowowl.snomed.datastore.internal.rf2.SnomedRefSetDSVExportModel;
import com.b2international.snowowl.snomed.datastore.request.SnomedConceptSearchRequestBuilder;
import com.b2international.snowowl.snomed.datastore.request.SnomedRequests;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.*;

/**
 * Implements the export process of the DSV export for simple type reference sets. 
 */
public class SnomedSimpleTypeRefSetDSVExporter implements IRefSetDSVExporter {

	private static final String HEADER_EXPAND = "descriptions(active:true), "
			+ "relationships(active:true), "
			+ "members(active:true, refSetType:\"CONCRETE_DATA_TYPE\")";
	
	private static final String DATA_EXPAND = "pt(), "
			+ "descriptions(active:true), "
			+ "relationships(active:true, expand(destination(expand(pt())))), "
			+ "members(active:true, refSetType:\"CONCRETE_DATA_TYPE\")";

	private static final Multiset<String> NO_OCCURRENCES = ImmutableMultiset.of();
	
	static interface ConceptStreamFactory {
		
		Stream<SnomedConcepts> getConceptStream(
			String expand, 
			List<ExtendedLocale> locales, 
			BranchContext context, 
			boolean includeInactiveMembers,
			String refSetId
		);
		
		ConceptStreamFactory DEFAULT = (expand, locales, context, includeInactiveMembers, refSetId) -> {
			SnomedConceptSearchRequestBuilder builder = SnomedRequests.prepareSearchConcept()
				.setLocales(locales)
				.setExpand(expand)
				.sortBy(Sort.fieldAsc(SnomedConceptDocument.Fields.ID))
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
	
	private String refSetId;
	private boolean includeDescriptionId;
	private boolean includeRelationshipId;
	private boolean includeInactiveMembers;
	private List<AbstractSnomedDsvExportItem> exportItems;
	private List<ExtendedLocale> locales;
	private Joiner joiner;
	private String lineSeparator;
	
	private Multiset<String> descriptionCount; // maximum number of descriptions by type
	private Map<Integer, Multiset<String>> propertyCountByGroup; // maximum number of properties by group and type

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
	public File executeDSVExport(IProgressMonitor monitor) throws IOException {
		monitor.beginTask("Export RefSet to DSV...", 100);
		Path exportPath = Files.createTempFile("dsv-export-" + refSetId + Dates.now(), ".csv");
		try {
			try (BufferedWriter writer = Files.newBufferedWriter(exportPath, Charsets.UTF_8)) {
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
	private Stream<SnomedConcepts> getConceptStream(String expand) {
		return conceptStreamFactory.getConceptStream(expand, locales, context, includeInactiveMembers, refSetId);
	}

	/*
	 * Finds the maximum number of occurrences for each description, relationship and concrete data type; generates headers. 
	 */
	private void computeHeader() {
		descriptionCount = HashMultiset.create();
		propertyCountByGroup = newHashMap();
		getConceptStream(HEADER_EXPAND).forEachOrdered(this::computeHeader);
	}
	
	private void computeHeader(SnomedConcepts chunk) {
		for (SnomedConcept concept : chunk) {
			// Respect the exported item order that was sent in via the export request
			for (AbstractSnomedDsvExportItem exportItem : exportItems) {
				switch (exportItem.getType()) {
				
					case DESCRIPTION: {
						final ComponentIdSnomedDsvExportItem descriptionItem = (ComponentIdSnomedDsvExportItem) exportItem;
						final String typeId = descriptionItem.getComponentId();
						
						// Find the number of active descriptions with this type ID (status is included in expand filter)
						final int previousDescriptions = descriptionCount.count(typeId);
						final int currentDescriptions = concept.getDescriptions()
							.stream()
							.filter(d -> typeId.equals(d.getTypeId()))
							.collect(Collectors.summingInt(d -> 1));
						
						// Register in description type column counter -- bigger number wins
						descriptionCount.setCount(typeId, Math.max(previousDescriptions, currentDescriptions));
						break;
					}
						
					case RELATIONSHIP: {
						final ComponentIdSnomedDsvExportItem relationshipItem = (ComponentIdSnomedDsvExportItem) exportItem;
						final String typeId = relationshipItem.getComponentId();
						
						// Find the number of active relationships with this type ID (status is included in expand filter)
						// ...but only count inferred relationships and additional relationships in group 0
						Map<Integer, Integer> currentRelationshipsByGroup = concept.getRelationships()
							.stream()
							.filter(r -> {
								final String relationshipTypeId = r.getTypeId();
								final String characteristicTypeId = r.getCharacteristicTypeId();
								final Integer relationshipGroup = r.getRelationshipGroup();
								
								return typeId.equals(relationshipTypeId) 
									&& (Concepts.INFERRED_RELATIONSHIP.equals(characteristicTypeId) 
										|| (Concepts.ADDITIONAL_RELATIONSHIP.equals(characteristicTypeId) && relationshipGroup == 0));
							})
							.collect(Collectors.groupingBy(
								// Results should be collected on a per-group basis
								r -> r.getRelationshipGroup(),
								Collectors.summingInt(r -> 1)
							));

						currentRelationshipsByGroup.entrySet()
							.stream()
							.forEachOrdered(entry -> {
								final int relationshipGroup = entry.getKey();
								// Number of properties encountered so far, for this type _and_ group number
								final Multiset<String> previousRelationshipsByType = propertyCountByGroup.computeIfAbsent(relationshipGroup, HashMultiset::create);
								final int previousRelationships = previousRelationshipsByType.count(typeId);
								final int currentRelationships = entry.getValue();
								
								// Register in relationship type column counter for this group -- bigger number wins
								previousRelationshipsByType.setCount(typeId, Math.max(previousRelationships, currentRelationships));
							});
						
						break;
					}
					
					case DATAYPE: {
						final ComponentIdSnomedDsvExportItem dataTypeItem = (ComponentIdSnomedDsvExportItem) exportItem;
						final String typeId = dataTypeItem.getComponentId();
						
						// Find the number of active CD members with this type ID (refset type and status is included in expand filter)
						// ...but only count inferred members and additional members in group 0
						Map<Integer, Integer> currentMembersByGroup = concept.getMembers()
							.stream()
							.filter(m -> {
								final String memberTypeId = (String) m.getProperties().get(SnomedRf2Headers.FIELD_TYPE_ID);
								final String characteristicTypeId = (String) m.getProperties().get(SnomedRf2Headers.FIELD_CHARACTERISTIC_TYPE_ID);
								final Integer memberGroup = (Integer) m.getProperties().get(SnomedRf2Headers.FIELD_RELATIONSHIP_GROUP);
								
								return typeId.equals(memberTypeId) 
									&& (Concepts.INFERRED_RELATIONSHIP.equals(characteristicTypeId) 
										|| (Concepts.ADDITIONAL_RELATIONSHIP.equals(characteristicTypeId) && memberGroup == 0));
							})
							.collect(Collectors.groupingBy(
								// Results should be collected on a per-group basis
								m -> (Integer) m.getProperties().get(SnomedRf2Headers.FIELD_RELATIONSHIP_GROUP),
								Collectors.summingInt(m -> 1)
							));

						currentMembersByGroup.entrySet()
							.stream()
							.forEach(entry -> {
								final int relationshipGroup = entry.getKey();
								// Number of properties encountered so far, for this type _and_ group number
								final Multiset<String> previousMembersByType = propertyCountByGroup.computeIfAbsent(relationshipGroup, HashMultiset::create);
								final int previousMembers = previousMembersByType.count(typeId);
								final int currentMembers = entry.getValue();

								// Register in relationship type column counter for this group -- bigger number wins
								previousMembersByType.setCount(typeId, Math.max(previousMembers, currentMembers));
							});
						
						break;
					}
					
					default:
						// Single-use fields don't need to be counted in advance
						break;
				}
			}
		}
	}
	
	private void writeHeader(BufferedWriter writer) throws IOException {
		Map<String, String> descriptionTypeIdMap = createTypeIdMap(Concepts.DESCRIPTION_TYPE_ROOT_CONCEPT);
		Map<String, String> propertyTypeIdMap = createTypeIdMap(Concepts.CONCEPT_MODEL_ATTRIBUTE); // includes object and data attributes
		List<String> propertyHeader = newArrayList();
		List<String> detailHeader = newArrayList();
		
		for (AbstractSnomedDsvExportItem exportItem : exportItems) {
			switch (exportItem.getType()) {
			
				case DESCRIPTION: {
					final ComponentIdSnomedDsvExportItem descriptionItem = (ComponentIdSnomedDsvExportItem) exportItem;
					final String typeId = descriptionItem.getComponentId();
					final String displayName = descriptionTypeIdMap.getOrDefault(typeId, descriptionItem.getDisplayName());
					final int occurrences = descriptionCount.count(typeId);
					
					if (occurrences < 2) {
						// No numbered suffix required
						if (includeDescriptionId) {
							propertyHeader.add(displayName);
							detailHeader.add("ID");
							propertyHeader.add(displayName);
							detailHeader.add("Term");
						} else {
							propertyHeader.add(displayName);
							detailHeader.add("");
						}
					} else {
						// Numbered suffixes should start at index 1
						for (int j = 1; j <= occurrences; j++) {
							final String numberedDisplayName = String.format("%s (%s)", displayName, j);
							
							if (includeDescriptionId) {
								propertyHeader.add(numberedDisplayName);
								detailHeader.add("ID");
								propertyHeader.add(numberedDisplayName);
								detailHeader.add("Term");
							} else {
								propertyHeader.add(numberedDisplayName);
								detailHeader.add("");
							}
						}
					}
					
					break;
				}
					
				case RELATIONSHIP: {
					final ComponentIdSnomedDsvExportItem relationshipItem = (ComponentIdSnomedDsvExportItem) exportItem;
					final String typeId = relationshipItem.getComponentId();
					final String displayName = propertyTypeIdMap.getOrDefault(typeId, relationshipItem.getDisplayName());
					
					for (Integer group : propertyCountByGroup.keySet() ) {
						final Multiset<String> occurrencesByType = propertyCountByGroup.getOrDefault(group, NO_OCCURRENCES);
						final int occurrences = occurrencesByType.count(typeId);
						
						/*
						 * It is possible that a particular relationship type does not appear in a
						 * particular group at all, skip if this is the case.
						 */
						if (occurrences < 1) {
							continue;
						}
							
						final String groupTag = (group == 0) ? "" : String.format(" (AG%s)", group);
						final String groupedDisplayName = displayName + groupTag;
						
						if (occurrences < 2) {
							// No numbered suffix required
							if (includeRelationshipId) {
								propertyHeader.add(groupedDisplayName);
								detailHeader.add("ID");
								propertyHeader.add(groupedDisplayName);
								detailHeader.add("Destination");
							} else {
								propertyHeader.add(groupedDisplayName);
								detailHeader.add("");
							}
						} else {
							// Numbered suffixes should start at index 1
							for (int j = 1; j <= occurrences; j++) {
								final String numberedDisplayName = String.format("%s (%s)", groupedDisplayName, j);
								
								if (includeRelationshipId) {
									propertyHeader.add(numberedDisplayName);
									detailHeader.add("ID");
									propertyHeader.add(numberedDisplayName);
									detailHeader.add("Destination");
								} else {
									propertyHeader.add(numberedDisplayName);
									detailHeader.add("");
								}
							}
						}
					}
					
					break;
				}
				
				case DATAYPE: {
					final ComponentIdSnomedDsvExportItem dataTypeItem = (ComponentIdSnomedDsvExportItem) exportItem;
					final String typeId = dataTypeItem.getComponentId();
					final String displayName = propertyTypeIdMap.getOrDefault(typeId, dataTypeItem.getDisplayName());

					for (Integer groupId : propertyCountByGroup.keySet() ) {
						final Multiset<String> occurrencesByType = propertyCountByGroup.getOrDefault(groupId, NO_OCCURRENCES);
						final int occurrences = occurrencesByType.count(typeId);
						
						/*
						 * It is possible that a particular relationship type does not appear in a
						 * particular group at all, skip if this is the case.
						 */
						if (occurrences < 1) {
							continue;
						}
						
						final String groupTag = (groupId == 0) ? "" : String.format(" (AG%s)", groupId);
						final String groupedDisplayName = displayName + groupTag;
						
						if (occurrences < 2) {
							// No numbered suffix required
							propertyHeader.add(groupedDisplayName);
							detailHeader.add("");
						} else {
							// Numbered suffixes should start at index 1
							for (int j = 1; j <= occurrences; j++) {
								final String numberedDisplayName = String.format("%s (%s)", groupedDisplayName, j);
								
								propertyHeader.add(numberedDisplayName);
								detailHeader.add("");
							}
						}						
					}
					
					break;
				}
				
				case PREFERRED_TERM: {
					if (includeDescriptionId) {
						propertyHeader.add(exportItem.getDisplayName());
						detailHeader.add("ID");
						propertyHeader.add(exportItem.getDisplayName());
						detailHeader.add("Term");
					} else {
						propertyHeader.add(exportItem.getDisplayName());
						detailHeader.add("");
					}
					
					break;
				}
	
				default: {
					propertyHeader.add(exportItem.getDisplayName());
					detailHeader.add("");
					
					break;
				}
			}
		}
		
		// write the header to the file
		writer.write(joiner.join(propertyHeader));
		writer.write(lineSeparator);
		
		if (includeDescriptionId || includeRelationshipId) {
			writer.write(joiner.join(detailHeader));
			writer.write(lineSeparator);
		}
	}

	private Map<String, String> createTypeIdMap(String ancestorId) {
		return createTypeIdMap(ancestorCollector.getAncestors(locales, ancestorId, context));
	}

	private Map<String, String> createTypeIdMap(SnomedConcepts concepts) {
		return concepts.stream()
			.collect(Collectors.toMap(
				c -> c.getId(), 
				c -> getPreferredTerm(c)));
	}

	private void writeValues(IProgressMonitor monitor, BufferedWriter writer) throws IOException {
		final Iterable<SnomedConcepts> chunks = () -> getConceptStream(DATA_EXPAND).iterator();
		for (SnomedConcepts chunk : chunks) {
			writeValues(writer, chunk);
			monitor.worked(chunk.getItems().size());
		}
	}
		
	private void writeValues(BufferedWriter writer, SnomedConcepts chunk) throws IOException {
		List<String> dataRow = newArrayList();
		
		for (SnomedConcept concept : chunk) {
			dataRow.clear();

			for (AbstractSnomedDsvExportItem exportItem : exportItems) {
				switch (exportItem.getType()) {
				
					case DESCRIPTION: {
						final ComponentIdSnomedDsvExportItem descriptionItem = (ComponentIdSnomedDsvExportItem) exportItem;
						final String typeId = descriptionItem.getComponentId();
						final int occurrences = descriptionCount.count(typeId);
						
						// Description ID keys, description term values (hopefully a 1:1 mapping, a Multimap is used only to satisfy other use cases)
						final Multimap<String, String> termsById = concept.getDescriptions()
							.stream()
							.filter(d -> typeId.equals(d.getTypeId()))
							.collect(Multimaps.toMultimap(
								d -> d.getId(),
								d -> d.getTerm(),
								ArrayListMultimap::create
							));
						
						addCells(dataRow, occurrences, includeDescriptionId, termsById);
						break;
					}

					case RELATIONSHIP: {
						final ComponentIdSnomedDsvExportItem relationshipItem = (ComponentIdSnomedDsvExportItem) exportItem;
						final String typeId = relationshipItem.getComponentId();
						
						for (Integer propertyGroup : propertyCountByGroup.keySet()) {
							final Multiset<String> groupOccurrences = propertyCountByGroup.getOrDefault(propertyGroup, NO_OCCURRENCES);
							final int occurrences = groupOccurrences.count(typeId);
							
							if (occurrences < 1) {
								break;
							}

							// Destination ID keys, destination concept terms for values
							// - OR -
							// Relationship value as keys, empty strings for values
							final Multimap<String, String> destinationsById = concept.getRelationships()
								.stream()
								.filter(r -> {
									final String relationshipTypeId = r.getTypeId();
									final String characteristicTypeId = r.getCharacteristicTypeId();
									final Integer relationshipGroup = r.getRelationshipGroup();
									
									return typeId.equals(relationshipTypeId) 
										&& Objects.equals(propertyGroup, relationshipGroup) 
										&& (Concepts.INFERRED_RELATIONSHIP.equals(characteristicTypeId) 
											|| (Concepts.ADDITIONAL_RELATIONSHIP.equals(characteristicTypeId) && relationshipGroup == 0));
								})
								.collect(Multimaps.toMultimap(
									r -> r.hasValue() ? r.getValue() : r.getDestinationId(),
									r -> r.hasValue() ? "" : getPreferredTerm(r.getDestination()),
									ArrayListMultimap::create
								));
							
							addCells(dataRow, occurrences, includeRelationshipId, destinationsById);
						}
						
						break;
					}

					case DATAYPE: {
						final DatatypeSnomedDsvExportItem datatypeItem = (DatatypeSnomedDsvExportItem) exportItem;
						final String typeId = datatypeItem.getComponentId();
						
						for (Integer propertyGroup : propertyCountByGroup.keySet()) {
							final Multiset<String> groupOccurrences = propertyCountByGroup.getOrDefault(propertyGroup, NO_OCCURRENCES);
							final int occurrences = groupOccurrences.count(typeId);
							
							if (occurrences < 1) {
								break;
							}
							
							// Empty string for keys, CD member values for values (only collected this way to conform to the method signature below)
							final Multimap<String, String> valuesById = concept.getMembers()
								.stream()
								.filter(m -> {
									final String memberTypeId = (String) m.getProperties().get(SnomedRf2Headers.FIELD_TYPE_ID);
									final String characteristicTypeId = (String) m.getProperties().get(SnomedRf2Headers.FIELD_CHARACTERISTIC_TYPE_ID);
									final Integer memberGroup = (Integer) m.getProperties().get(SnomedRf2Headers.FIELD_RELATIONSHIP_GROUP);
									
									return typeId.equals(memberTypeId) 
										&& Objects.equals(propertyGroup, memberGroup) 
										&& (Concepts.INFERRED_RELATIONSHIP.equals(characteristicTypeId) 
											|| (Concepts.ADDITIONAL_RELATIONSHIP.equals(characteristicTypeId) && memberGroup == 0));
								})
								.collect(Multimaps.toMultimap(
									m -> "",
									m -> {
										final String value = (String) m.getProperties().get(SnomedRf2Headers.FIELD_VALUE);
										if (datatypeItem.isBooleanDatatype()) {
											return "1".equals(value) ? "Yes" : "No";
										} else {
											return value;
										}
									},
									ArrayListMultimap::create
								));
							
							addCells(dataRow, occurrences, false, valuesById);
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
		}
	}

	private void addCells(List<String> dataRow, int occurrences, boolean includeIds, Multimap<String, String> idValuePairs) {
		if (includeIds) {
			SortedSet<String> sortedIds = ImmutableSortedSet.copyOf(idValuePairs.keySet());
			
			for (String id : sortedIds) {
				List<String> sortedValues = Ordering.natural().sortedCopy(idValuePairs.get(id));
				for (String value : sortedValues) {
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
			
			List<String> sortedValues = Ordering.natural().sortedCopy(idValuePairs.values());
			
			for (String value : sortedValues) {
				dataRow.add(value);
				occurrences--;
			}
			
			while (occurrences > 0) {
				dataRow.add("");
				occurrences--;
			}
		}
	}

	private static String getPreferredTerm(SnomedConcept concept) {
		return (concept.getPt() == null) ? "" : concept.getPt().getTerm();
	}
	
	private static String getPreferredTermId(SnomedConcept concept) {
		return (concept.getPt() == null) ? "" : concept.getPt().getId();
	}
}
