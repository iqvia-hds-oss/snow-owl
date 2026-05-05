/*
 * Copyright 2024-2026 B2i Healthcare, https://b2ihealthcare.com
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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.BeforeClass;
import org.junit.Test;

import com.b2international.snowowl.core.config.SnowOwlConfiguration;
import com.b2international.snowowl.core.setup.Environment;
import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.core.domain.*;
import com.b2international.snowowl.snomed.datastore.internal.rf2.ComponentIdSnomedDsvExportItem;
import com.b2international.snowowl.snomed.datastore.internal.rf2.SimpleSnomedDsvExportItem;
import com.b2international.snowowl.snomed.datastore.internal.rf2.SnomedDsvExportItemType;
import com.b2international.snowowl.snomed.datastore.internal.rf2.SnomedRefSetDSVExportModel;
import com.b2international.snowowl.snomed.datastore.request.dsv.SnomedSimpleTypeRefSetDSVExporter.AncestorCollector;
import com.b2international.snowowl.snomed.datastore.request.dsv.SnomedSimpleTypeRefSetDSVExporter.ConceptStreamFactory;

public class SnomedSimpleTypeRefSetDSVExporterTest {

	private static final String LS = System.lineSeparator();

	private static String getContentsAndDelete(final SnomedSimpleTypeRefSetDSVExporter exporter) throws IOException {
		final File exportFile = exporter.executeDSVExport(new NullProgressMonitor());
		final String exportContents = Files.readString(exportFile.toPath());
		exportFile.delete();
		return exportContents;
	}

	@BeforeClass
	public static void setupMinimalEnv() {
		Path root = Paths.get("target");
		final Environment env = new Environment(root, root, root);
		final SnowOwlConfiguration configuration = new SnowOwlConfiguration();
		
		env.services().registerService(SnowOwlConfiguration.class, configuration);
	}
	
	@Test
	public void exportEmptyFile() throws IOException {
		ConceptStreamFactory conceptStreamFactory = (expand, locales, context, includeInactiveMembers, refSetId) -> Stream.empty();
		AncestorCollector ancestorCollector = (locales, ancestorId, context) -> new SnomedConcepts(0, 0);

		SnomedRefSetDSVExportModel exportSetting = new SnomedRefSetDSVExportModel();
		exportSetting.setDelimiter("\t");
		
		final var exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		final String exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have a single line separator", LS, exportContents);
	}
	
	@Test
	public void exportSingleConcept() throws IOException {
		SnomedConcept concept = new SnomedConcept("c1");
		
		SnomedConcepts chunk = new SnomedConcepts(List.of(concept), null, 1, 1);
		ConceptStreamFactory conceptStreamFactory = (expand, locales, context, includeInactiveMembers, refSetId) -> Stream.of(chunk);
		AncestorCollector ancestorCollector = (locales, ancestorId, context) -> new SnomedConcepts(0, 0);
		
		SnomedRefSetDSVExportModel exportSetting = new SnomedRefSetDSVExportModel();
		exportSetting.setDelimiter("\t");
		
		final var exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		final String exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have two line separators (header and data row)", LS + LS, exportContents);
	}
	
	@Test
	public void exportSingleConceptSimpleFields() throws IOException {
		SnomedConcept concept = new SnomedConcept("c1");
		concept.setActive(false);
		concept.setModuleId("m1");
		concept.setEffectiveTime(LocalDate.of(2024, 11, 27));
		concept.setDefinitionStatusId(Concepts.FULLY_DEFINED);
		
		SnomedConcepts chunk = new SnomedConcepts(List.of(concept), null, 1, 1);
		ConceptStreamFactory conceptStreamFactory = (expand, locales, context, includeInactiveMembers, refSetId) -> Stream.of(chunk);
		AncestorCollector ancestorCollector = (locales, ancestorId, context) -> new SnomedConcepts(0, 0);
		
		SnomedRefSetDSVExportModel exportSetting = new SnomedRefSetDSVExportModel();
		exportSetting.setDelimiter("\t");
		exportSetting.addExportItem(new SimpleSnomedDsvExportItem(SnomedDsvExportItemType.CONCEPT_ID));
		exportSetting.addExportItem(new SimpleSnomedDsvExportItem(SnomedDsvExportItemType.STATUS_LABEL));
		exportSetting.addExportItem(new SimpleSnomedDsvExportItem(SnomedDsvExportItemType.MODULE));
		exportSetting.addExportItem(new SimpleSnomedDsvExportItem(SnomedDsvExportItemType.EFFECTIVE_TIME));
		exportSetting.addExportItem(new SimpleSnomedDsvExportItem(SnomedDsvExportItemType.DEFINITION_STATUS));
		
		final String expectedHeader = exportSetting.getExportItems()
			.stream()
			.map(i -> i.getDisplayName())
			.collect(Collectors.joining(exportSetting.getDelimiter()));
		
		final String expectedData = String.join(exportSetting.getDelimiter(),
			concept.getId(),
			"Inactive",
			concept.getModuleId(),
			"2024-11-27",
			concept.getDefinitionStatusId());
		
		final var exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		final String exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have a header and a data row with simple item values", String.join(LS, expectedHeader, expectedData) + LS, exportContents);
	}
	
	@Test
	public void exportSingleConceptPreferredTerm() throws IOException {
		SnomedDescription pt = new SnomedDescription("d1");
		pt.setTerm("PT term");
		
		SnomedConcept concept = new SnomedConcept("c1");
		concept.setPt(pt);
		
		SnomedConcepts chunk = new SnomedConcepts(List.of(concept), null, 1, 1);
		ConceptStreamFactory conceptStreamFactory = (expand, locales, context, includeInactiveMembers, refSetId) -> Stream.of(chunk);
		AncestorCollector ancestorCollector = (locales, ancestorId, context) -> new SnomedConcepts(0, 0);
		
		SnomedRefSetDSVExportModel exportSetting = new SnomedRefSetDSVExportModel();
		exportSetting.setDelimiter("\t");
		exportSetting.addExportItem(new SimpleSnomedDsvExportItem(SnomedDsvExportItemType.CONCEPT_ID));
		exportSetting.addExportItem(new SimpleSnomedDsvExportItem(SnomedDsvExportItemType.PREFERRED_TERM));
		
		String expectedHeader = exportSetting.getExportItems()
			.stream()
			.map(i -> i.getDisplayName())
			.collect(Collectors.joining(exportSetting.getDelimiter()));
		
		String expectedData = String.join(exportSetting.getDelimiter(),
			concept.getId(),
			pt.getTerm());
		
		var exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		String exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have a header and a data row with PT term", String.join(LS, expectedHeader, expectedData) + LS, exportContents);
		
		// Second run: include the SCTID for the preferred term
		exportSetting.setIncludeDescriptionId(true);
		
		// As a result of the above setting, "Preferred term" appears twice in the header (once for the ID and once for the description term)
		expectedHeader = String.join(exportSetting.getDelimiter(), "Concept ID", "Preferred term", "Preferred term");
		String expectedDetails = String.join(exportSetting.getDelimiter(), "", "ID", "Term");
		expectedData = String.join(exportSetting.getDelimiter(), concept.getId(), pt.getId(), pt.getTerm());

		exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have a header and a data row with PT SCTID and term", String.join(LS, expectedHeader, expectedDetails, expectedData) + LS, exportContents);
	}
	
	@Test
	public void exportSingleConceptFsn() throws IOException {
		SnomedDescription fsn = new SnomedDescription("d1");
		fsn.setTerm("FSN term");
		fsn.setTypeId(Concepts.FULLY_SPECIFIED_NAME);
		
		SnomedDescription decoy = new SnomedDescription("d2");
		decoy.setTerm("Should not appear in the output");
		decoy.setTypeId(Concepts.SYNONYM);
		
		SnomedConcept concept = new SnomedConcept("c1");
		concept.setDescriptions(new SnomedDescriptions(List.of(fsn, decoy), null, 2, 2));
		
		SnomedConcepts chunk = new SnomedConcepts(List.of(concept), null, 1, 1);
		ConceptStreamFactory conceptStreamFactory = (expand, locales, context, includeInactiveMembers, refSetId) -> Stream.of(chunk);
		AncestorCollector ancestorCollector = (locales, ancestorId, context) -> new SnomedConcepts(0, 0);
		
		SnomedRefSetDSVExportModel exportSetting = new SnomedRefSetDSVExportModel();
		exportSetting.setDelimiter("\t");
		exportSetting.addExportItem(new SimpleSnomedDsvExportItem(SnomedDsvExportItemType.CONCEPT_ID));
		exportSetting.addExportItem(new ComponentIdSnomedDsvExportItem(SnomedDsvExportItemType.DESCRIPTION, Concepts.FULLY_SPECIFIED_NAME, "Fully specified name"));
		
		String expectedHeader = exportSetting.getExportItems()
			.stream()
			.map(i -> i.getDisplayName())
			.collect(Collectors.joining(exportSetting.getDelimiter()));
		
		String expectedData = String.join(exportSetting.getDelimiter(),
			concept.getId(),
			fsn.getTerm());
		
		var exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		String exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have a header and a data row with FSN term", String.join(LS, expectedHeader, expectedData) + LS, exportContents);
		
		// Second run: include the SCTID for the FSN
		exportSetting.setIncludeDescriptionId(true);
		
		// As a result of the above setting, "Fully specified name" appears twice in the header (once for the ID and once for the description term)
		expectedHeader = String.join(exportSetting.getDelimiter(), "Concept ID", "Fully specified name", "Fully specified name");
		String expectedDetails = String.join(exportSetting.getDelimiter(), "", "ID", "Term");
		expectedData = String.join(exportSetting.getDelimiter(), concept.getId(), fsn.getId(), fsn.getTerm());

		exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have a header and a data row with FSN SCTID and term", String.join(LS, expectedHeader, expectedDetails, expectedData) + LS, exportContents);
	}	
	
	@Test
	public void exportTwoConceptsFsn() throws IOException {
		SnomedDescription fsn1 = new SnomedDescription("d1");
		fsn1.setTerm("FSN term 1");
		fsn1.setTypeId(Concepts.FULLY_SPECIFIED_NAME);
		
		SnomedDescription decoy1 = new SnomedDescription("d2");
		decoy1.setTerm("Should not appear in the output");
		decoy1.setTypeId(Concepts.SYNONYM);
		
		SnomedConcept concept1 = new SnomedConcept("c1");
		concept1.setDescriptions(new SnomedDescriptions(List.of(fsn1, decoy1), null, 2, 2));
		
		// ------------------------------------
		
		SnomedDescription fsn2 = new SnomedDescription("d3");
		fsn2.setTerm("FSN term 2.1");
		fsn2.setTypeId(Concepts.FULLY_SPECIFIED_NAME);
		
		SnomedDescription fsn3 = new SnomedDescription("d4");
		fsn3.setTerm("FSN term 2.2");
		fsn3.setTypeId(Concepts.FULLY_SPECIFIED_NAME);
		
		SnomedDescription decoy2 = new SnomedDescription("d5");
		decoy2.setTerm("Should not appear in the output either");
		decoy2.setTypeId(Concepts.SYNONYM);
		
		SnomedConcept concept2 = new SnomedConcept("c2");
		concept2.setDescriptions(new SnomedDescriptions(List.of(fsn2, fsn3, decoy2), null, 3, 3));
		
		SnomedConcepts chunk = new SnomedConcepts(List.of(concept1, concept2), null, 2, 2);
		ConceptStreamFactory conceptStreamFactory = (expand, locales, context, includeInactiveMembers, refSetId) -> Stream.of(chunk);
		AncestorCollector ancestorCollector = (locales, ancestorId, context) -> new SnomedConcepts(0, 0);
		
		SnomedRefSetDSVExportModel exportSetting = new SnomedRefSetDSVExportModel();
		exportSetting.setDelimiter("\t");
		exportSetting.addExportItem(new SimpleSnomedDsvExportItem(SnomedDsvExportItemType.CONCEPT_ID));
		exportSetting.addExportItem(new ComponentIdSnomedDsvExportItem(SnomedDsvExportItemType.DESCRIPTION, Concepts.FULLY_SPECIFIED_NAME, "Fully specified name"));
		
		// We get numbered columns because c2 has two FSNs
		String expectedHeader = String.join(exportSetting.getDelimiter(), "Concept ID", "Fully specified name (1)", "Fully specified name (2)");
		String expectedData1 = String.join(exportSetting.getDelimiter(), concept1.getId(), fsn1.getTerm(), "");
		String expectedData2 = String.join(exportSetting.getDelimiter(), concept2.getId(), fsn2.getTerm(), fsn3.getTerm());
		
		var exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		String exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have a header and two data rows with FSN terms", String.join(LS, expectedHeader, expectedData1, expectedData2) + LS, exportContents);
		
		// Second run: include the SCTID for the FSN
		exportSetting.setIncludeDescriptionId(true);
		
		// As a result of the above setting, "Fully specified name" appears four times in the header (once for the ID and once for the description term)
		expectedHeader = String.join(exportSetting.getDelimiter(), "Concept ID", "Fully specified name (1)", "Fully specified name (1)", "Fully specified name (2)", "Fully specified name (2)");
		String expectedDetails = String.join(exportSetting.getDelimiter(), "", "ID", "Term", "ID", "Term");
		expectedData1 = String.join(exportSetting.getDelimiter(), concept1.getId(), fsn1.getId(), fsn1.getTerm(), "", "");
		expectedData2 = String.join(exportSetting.getDelimiter(), concept2.getId(), fsn2.getId(), fsn2.getTerm(), fsn3.getId(), fsn3.getTerm());

		exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have a header and two data rows with FSN SCTIDs and terms", String.join(LS, expectedHeader, expectedDetails, expectedData1, expectedData2) + LS, exportContents);
	}
	
	@Test
	public void exportSingleConceptRelationship() throws IOException {
		SnomedDescription handStructurePt = new SnomedDescription("d1");
		handStructurePt.setTerm("Hand structure");

		SnomedConcept handStructure = new SnomedConcept("c1");
		handStructure.setPt(handStructurePt);
		
		SnomedDescription legStructurePt = new SnomedDescription("d2");
		legStructurePt.setTerm("Leg structure");
		
		SnomedConcept legStructure = new SnomedConcept("c2");
		legStructure.setPt(legStructurePt);
		
		SnomedRelationship findingSite = new SnomedRelationship("r1");
		findingSite.setRelationshipGroup(0);
		findingSite.setTypeId(Concepts.FINDING_SITE);
		findingSite.setDestination(handStructure);
		findingSite.setCharacteristicTypeId(Concepts.INFERRED_RELATIONSHIP);
		
		// Grouped additional relationships are ignored
		SnomedRelationship decoy1 = new SnomedRelationship("r2");
		decoy1.setRelationshipGroup(1);
		decoy1.setTypeId(Concepts.FINDING_SITE);
		decoy1.setDestination(legStructure);
		decoy1.setCharacteristicTypeId(Concepts.ADDITIONAL_RELATIONSHIP);
		
		// Relationships with a different type are also ignored (because no export item is sent in the request for it)
		SnomedRelationship decoy2 = new SnomedRelationship("r3");
		decoy2.setRelationshipGroup(0);
		decoy2.setTypeId(Concepts.HAS_ACTIVE_INGREDIENT);
		decoy2.setDestination(legStructure);
		decoy2.setCharacteristicTypeId(Concepts.INFERRED_RELATIONSHIP);
		
		SnomedConcept concept = new SnomedConcept("c3");
		concept.setRelationships(new SnomedRelationships(List.of(findingSite, decoy1, decoy2), null, 3, 3));
		
		SnomedConcepts chunk = new SnomedConcepts(List.of(concept), null, 1, 1);
		ConceptStreamFactory conceptStreamFactory = (expand, locales, context, includeInactiveMembers, refSetId) -> Stream.of(chunk);
		AncestorCollector ancestorCollector = (locales, ancestorId, context) -> new SnomedConcepts(0, 0);
		
		SnomedRefSetDSVExportModel exportSetting = new SnomedRefSetDSVExportModel();
		exportSetting.setDelimiter("\t");
		exportSetting.addExportItem(new SimpleSnomedDsvExportItem(SnomedDsvExportItemType.CONCEPT_ID));
		exportSetting.addExportItem(new ComponentIdSnomedDsvExportItem(SnomedDsvExportItemType.RELATIONSHIP, Concepts.FINDING_SITE, "Finding site"));
		
		String expectedHeader = exportSetting.getExportItems()
			.stream()
			.map(i -> i.getDisplayName())
			.collect(Collectors.joining(exportSetting.getDelimiter()));
		
		String expectedData = String.join(exportSetting.getDelimiter(),
			concept.getId(),
			handStructurePt.getTerm());
		
		var exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		String exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have a header and a data row with relationship destination term", String.join(LS, expectedHeader, expectedData) + LS, exportContents);
		
		// Second run: include the SCTID for destination concepts
		exportSetting.setIncludeRelationshipTargetId(true);
		
		// As a result of the above setting, "Finding site" appears twice in the header (once for the ID and once for the description term)
		expectedHeader = String.join(exportSetting.getDelimiter(), "Concept ID", "Finding site", "Finding site");
		String expectedDetails = String.join(exportSetting.getDelimiter(), "", "ID", "Destination");
		expectedData = String.join(exportSetting.getDelimiter(), concept.getId(), findingSite.getDestinationId(), handStructurePt.getTerm());

		exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have a header and a data row with relationship destination SCTID and term", String.join(LS, expectedHeader, expectedDetails, expectedData) + LS, exportContents);
	}

	@Test
	public void exportSingleConceptGroupedRelationship() throws IOException {
		SnomedDescription handStructurePt = new SnomedDescription("d1");
		handStructurePt.setTerm("Hand structure");
	
		SnomedConcept handStructure = new SnomedConcept("c1");
		handStructure.setPt(handStructurePt);
		
		SnomedDescription legStructurePt = new SnomedDescription("d2");
		legStructurePt.setTerm("Leg structure");
		
		SnomedConcept legStructure = new SnomedConcept("c2");
		legStructure.setPt(legStructurePt);
		
		SnomedRelationship findingSite = new SnomedRelationship("r1");
		findingSite.setRelationshipGroup(2);
		findingSite.setTypeId(Concepts.FINDING_SITE);
		findingSite.setDestination(handStructure);
		findingSite.setCharacteristicTypeId(Concepts.INFERRED_RELATIONSHIP);
		
		SnomedConcept concept = new SnomedConcept("c3");
		concept.setRelationships(new SnomedRelationships(List.of(findingSite), null, 1, 1));
		
		SnomedConcepts chunk = new SnomedConcepts(List.of(concept), null, 1, 1);
		ConceptStreamFactory conceptStreamFactory = (expand, locales, context, includeInactiveMembers, refSetId) -> Stream.of(chunk);
		AncestorCollector ancestorCollector = (locales, ancestorId, context) -> new SnomedConcepts(0, 0);
		
		SnomedRefSetDSVExportModel exportSetting = new SnomedRefSetDSVExportModel();
		exportSetting.setDelimiter("\t");
		exportSetting.addExportItem(new SimpleSnomedDsvExportItem(SnomedDsvExportItemType.CONCEPT_ID));
		exportSetting.addExportItem(new ComponentIdSnomedDsvExportItem(SnomedDsvExportItemType.RELATIONSHIP, Concepts.FINDING_SITE, "Finding site"));
		
		String expectedHeader = String.join(exportSetting.getDelimiter(), "Concept ID", "Finding site (AG2)");
		String expectedData = String.join(exportSetting.getDelimiter(), concept.getId(), handStructurePt.getTerm());
		
		var exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		String exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have a header (with group number) and a data row with relationship destination term", 
			String.join(LS, expectedHeader, expectedData) + LS, exportContents);
		
		// Second run: include the SCTID for destination concepts
		exportSetting.setIncludeRelationshipTargetId(true);
		
		// As a result of the above setting, "Finding site" appears twice in the header (once for the ID and once for the description term)
		expectedHeader = String.join(exportSetting.getDelimiter(), "Concept ID", "Finding site (AG2)", "Finding site (AG2)");
		String expectedDetails = String.join(exportSetting.getDelimiter(), "", "ID", "Destination");
		expectedData = String.join(exportSetting.getDelimiter(), concept.getId(), findingSite.getDestinationId(), handStructurePt.getTerm());
	
		exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have a header (with group number) and a data row with relationship destination SCTID and term", 
			String.join(LS, expectedHeader, expectedDetails, expectedData) + LS, exportContents);
	}

	@Test
	public void exportTwoConceptsNonOverlappingGroupedRelationships() throws IOException {
		SnomedDescription handStructurePt = new SnomedDescription("d1");
		handStructurePt.setTerm("Hand structure");
	
		SnomedConcept handStructure = new SnomedConcept("c1");
		handStructure.setPt(handStructurePt);
		
		SnomedDescription legStructurePt = new SnomedDescription("d2");
		legStructurePt.setTerm("Leg structure");
		
		SnomedConcept legStructure = new SnomedConcept("c2");
		legStructure.setPt(legStructurePt);
		
		SnomedRelationship findingSiteAG2 = new SnomedRelationship("r1");
		findingSiteAG2.setRelationshipGroup(2);
		findingSiteAG2.setTypeId(Concepts.FINDING_SITE);
		findingSiteAG2.setDestination(handStructure);
		findingSiteAG2.setCharacteristicTypeId(Concepts.INFERRED_RELATIONSHIP);
		
		SnomedRelationship findingSiteAG3 = new SnomedRelationship("r2");
		findingSiteAG3.setRelationshipGroup(3);
		findingSiteAG3.setTypeId(Concepts.FINDING_SITE);
		findingSiteAG3.setDestination(legStructure);
		findingSiteAG3.setCharacteristicTypeId(Concepts.INFERRED_RELATIONSHIP);
		
		SnomedConcept concept1 = new SnomedConcept("c3");
		concept1.setRelationships(new SnomedRelationships(List.of(findingSiteAG2), null, 1, 1));
		
		SnomedConcept concept2 = new SnomedConcept("c4");
		concept2.setRelationships(new SnomedRelationships(List.of(findingSiteAG3), null, 1, 1));
		
		SnomedConcepts chunk = new SnomedConcepts(List.of(concept1, concept2), null, 2, 2);
		ConceptStreamFactory conceptStreamFactory = (expand, locales, context, includeInactiveMembers, refSetId) -> Stream.of(chunk);
		AncestorCollector ancestorCollector = (locales, ancestorId, context) -> new SnomedConcepts(0, 0);
		
		SnomedRefSetDSVExportModel exportSetting = new SnomedRefSetDSVExportModel();
		exportSetting.setDelimiter("\t");
		exportSetting.addExportItem(new SimpleSnomedDsvExportItem(SnomedDsvExportItemType.CONCEPT_ID));
		exportSetting.addExportItem(new ComponentIdSnomedDsvExportItem(SnomedDsvExportItemType.RELATIONSHIP, Concepts.FINDING_SITE, "Finding site"));
		
		String expectedHeader = String.join(exportSetting.getDelimiter(), "Concept ID", "Finding site (AG2)", "Finding site (AG3)");
		String expectedData1 = String.join(exportSetting.getDelimiter(), concept1.getId(), handStructurePt.getTerm(), "");
		String expectedData2 = String.join(exportSetting.getDelimiter(), concept2.getId(), "", legStructurePt.getTerm());
		
		var exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		String exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have a header (with group numbers) and data rows with relationship destination term", 
			String.join(LS, expectedHeader, expectedData1, expectedData2) + LS, exportContents);
		
		// Second run: include the SCTID for destination concepts
		exportSetting.setIncludeRelationshipTargetId(true);
		
		// As a result of the above setting, "Finding site" appears twice in the header (once for the ID and once for the description term)
		expectedHeader = String.join(exportSetting.getDelimiter(), "Concept ID", "Finding site (AG2)", "Finding site (AG2)", "Finding site (AG3)", "Finding site (AG3)");
		String expectedDetails = String.join(exportSetting.getDelimiter(), "", "ID", "Destination", "ID", "Destination");
		expectedData1 = String.join(exportSetting.getDelimiter(), concept1.getId(), findingSiteAG2.getDestinationId(), handStructurePt.getTerm(), "", "");
		expectedData2 = String.join(exportSetting.getDelimiter(), concept2.getId(), "", "", findingSiteAG3.getDestinationId(), legStructurePt.getTerm());
	
		exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have a header (with group numbers) and data rows with relationship destination SCTID and term", 
			String.join(LS, expectedHeader, expectedDetails, expectedData1, expectedData2) + LS, exportContents);
	}

	@Test
	public void exportTwoConceptsNonOverlappingTypes() throws IOException {
		SnomedDescription handStructurePt = new SnomedDescription("d1");
		handStructurePt.setTerm("Hand structure");
	
		SnomedConcept handStructure = new SnomedConcept("c1");
		handStructure.setPt(handStructurePt);
		
		SnomedDescription legStructurePt = new SnomedDescription("d2");
		legStructurePt.setTerm("Leg structure");
		
		SnomedConcept legStructure = new SnomedConcept("c2");
		legStructure.setPt(legStructurePt);
		
		SnomedRelationship findingSite = new SnomedRelationship("r1");
		findingSite.setRelationshipGroup(0);
		findingSite.setTypeId(Concepts.FINDING_SITE);
		findingSite.setDestination(handStructure);
		findingSite.setCharacteristicTypeId(Concepts.INFERRED_RELATIONSHIP);
		
		SnomedRelationship morphology = new SnomedRelationship("r2");
		morphology.setRelationshipGroup(0);
		morphology.setTypeId(Concepts.MORPHOLOGY);
		morphology.setDestination(legStructure);
		morphology.setCharacteristicTypeId(Concepts.INFERRED_RELATIONSHIP);
		
		SnomedConcept concept1 = new SnomedConcept("c3");
		concept1.setRelationships(new SnomedRelationships(List.of(findingSite), null, 1, 1));
		
		SnomedConcept concept2 = new SnomedConcept("c4");
		concept2.setRelationships(new SnomedRelationships(List.of(morphology), null, 1, 1));
		
		SnomedConcepts chunk = new SnomedConcepts(List.of(concept1, concept2), null, 2, 2);
		ConceptStreamFactory conceptStreamFactory = (expand, locales, context, includeInactiveMembers, refSetId) -> Stream.of(chunk);
		AncestorCollector ancestorCollector = (locales, ancestorId, context) -> new SnomedConcepts(0, 0);
		
		SnomedRefSetDSVExportModel exportSetting = new SnomedRefSetDSVExportModel();
		exportSetting.setDelimiter("\t");
		exportSetting.addExportItem(new SimpleSnomedDsvExportItem(SnomedDsvExportItemType.CONCEPT_ID));
		exportSetting.addExportItem(new ComponentIdSnomedDsvExportItem(SnomedDsvExportItemType.RELATIONSHIP, Concepts.FINDING_SITE, "Finding site"));
		exportSetting.addExportItem(new ComponentIdSnomedDsvExportItem(SnomedDsvExportItemType.RELATIONSHIP, Concepts.MORPHOLOGY, "Morphology"));
		
		String expectedHeader = String.join(exportSetting.getDelimiter(), "Concept ID", "Finding site", "Morphology");
		String expectedData1 = String.join(exportSetting.getDelimiter(), concept1.getId(), handStructurePt.getTerm(), "");
		String expectedData2 = String.join(exportSetting.getDelimiter(), concept2.getId(), "", legStructurePt.getTerm());
		
		var exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		String exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have a header and data rows with relationship destination term", 
			String.join(LS, expectedHeader, expectedData1, expectedData2) + LS, exportContents);
		
		// Second run: include the SCTID for destination concepts
		exportSetting.setIncludeRelationshipTargetId(true);
		
		// As a result of the above setting, "Finding site" appears twice in the header (once for the ID and once for the description term)
		expectedHeader = String.join(exportSetting.getDelimiter(), "Concept ID", "Finding site", "Finding site", "Morphology", "Morphology");
		String expectedDetails = String.join(exportSetting.getDelimiter(), "", "ID", "Destination", "ID", "Destination");
		expectedData1 = String.join(exportSetting.getDelimiter(), concept1.getId(), findingSite.getDestinationId(), handStructurePt.getTerm(), "", "");
		expectedData2 = String.join(exportSetting.getDelimiter(), concept2.getId(), "", "", morphology.getDestinationId(), legStructurePt.getTerm());
	
		exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have a header, a detail row and data rows with relationship destination SCTID and term", 
			String.join(LS, expectedHeader, expectedDetails, expectedData1, expectedData2) + LS, exportContents);
	}

	private void exportSingleConceptMultipleRelationships(int relationshipGroup, String groupSuffix) throws IOException {
		SnomedDescription handStructurePt = new SnomedDescription("d1");
		handStructurePt.setTerm("Hand structure");
	
		SnomedConcept handStructure = new SnomedConcept("c1");
		handStructure.setPt(handStructurePt);
		
		SnomedDescription legStructurePt = new SnomedDescription("d2");
		legStructurePt.setTerm("Leg structure");
		
		SnomedConcept legStructure = new SnomedConcept("c2");
		legStructure.setPt(legStructurePt);
		
		SnomedRelationship findingSite1 = new SnomedRelationship("r1");
		findingSite1.setRelationshipGroup(relationshipGroup);
		findingSite1.setTypeId(Concepts.FINDING_SITE);
		findingSite1.setDestination(handStructure);
		findingSite1.setCharacteristicTypeId(Concepts.INFERRED_RELATIONSHIP);
		
		SnomedRelationship findingSite2 = new SnomedRelationship("r1");
		findingSite2.setRelationshipGroup(relationshipGroup);
		findingSite2.setTypeId(Concepts.FINDING_SITE);
		findingSite2.setDestination(legStructure);
		findingSite2.setCharacteristicTypeId(Concepts.INFERRED_RELATIONSHIP);
		
		SnomedConcept concept = new SnomedConcept("c3");
		concept.setRelationships(new SnomedRelationships(List.of(findingSite1, findingSite2), null, 2, 2));
		
		SnomedConcepts chunk = new SnomedConcepts(List.of(concept), null, 1, 1);
		ConceptStreamFactory conceptStreamFactory = (expand, locales, context, includeInactiveMembers, refSetId) -> Stream.of(chunk);
		AncestorCollector ancestorCollector = (locales, ancestorId, context) -> new SnomedConcepts(0, 0);
		
		SnomedRefSetDSVExportModel exportSetting = new SnomedRefSetDSVExportModel();
		exportSetting.setDelimiter("\t");
		exportSetting.addExportItem(new SimpleSnomedDsvExportItem(SnomedDsvExportItemType.CONCEPT_ID));
		exportSetting.addExportItem(new ComponentIdSnomedDsvExportItem(SnomedDsvExportItemType.RELATIONSHIP, Concepts.FINDING_SITE, "Finding site"));
		
		String expectedHeader = String.join(exportSetting.getDelimiter(), "Concept ID", "Finding site " + groupSuffix + "(1)", "Finding site " + groupSuffix + "(2)");
		String expectedData = String.join(exportSetting.getDelimiter(), concept.getId(), handStructurePt.getTerm(), legStructurePt.getTerm());
		
		var exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		String exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have a header (with group number if non-zero) and a data row with relationship destination terms", 
			String.join(LS, expectedHeader, expectedData) + LS, exportContents);
		
		// Second run: include the SCTID for destination concepts
		exportSetting.setIncludeRelationshipTargetId(true);
		
		// As a result of the above setting, "Finding site" appears twice in the header (once for the ID and once for the description term)
		expectedHeader = String.join(exportSetting.getDelimiter(), "Concept ID", 
			"Finding site " + groupSuffix + "(1)", "Finding site " + groupSuffix + "(1)", 
			"Finding site " + groupSuffix + "(2)", "Finding site " + groupSuffix + "(2)");
		
		String expectedDetails = String.join(exportSetting.getDelimiter(), "", "ID", "Destination", "ID", "Destination");
		expectedData = String.join(exportSetting.getDelimiter(), concept.getId(), 
			findingSite1.getDestinationId(), handStructurePt.getTerm(),
			findingSite2.getDestinationId(), legStructurePt.getTerm());
	
		exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have a header (with group number if non-zero) and a data row with relationship destination SCTIDs and terms", 
			String.join(LS, expectedHeader, expectedDetails, expectedData) + LS, exportContents);
	}	
	
	@Test
	public void exportSingleConceptMultipleZeroGroupRelationships() throws IOException {
		exportSingleConceptMultipleRelationships(0, "");
	}
	
	@Test
	public void exportSingleConceptMultipleGroupedRelationships() throws IOException {
		exportSingleConceptMultipleRelationships(2, "(AG2) ");
	}
	
	@Test
	public void exportSingleConceptRelationshipValue() throws IOException {
		
		SnomedRelationship numberOfIngredients = new SnomedRelationship("r1");
		numberOfIngredients.setRelationshipGroup(0);
		numberOfIngredients.setTypeId(Concepts.HAS_ACTIVE_INGREDIENT); // close enough
		numberOfIngredients.setValue("#5");
		numberOfIngredients.setCharacteristicTypeId(Concepts.INFERRED_RELATIONSHIP);
		
		SnomedConcept concept = new SnomedConcept("c1");
		concept.setRelationships(new SnomedRelationships(List.of(numberOfIngredients), null, 1, 1));
		
		SnomedConcepts chunk = new SnomedConcepts(List.of(concept), null, 1, 1);
		ConceptStreamFactory conceptStreamFactory = (expand, locales, context, includeInactiveMembers, refSetId) -> Stream.of(chunk);
		AncestorCollector ancestorCollector = (locales, ancestorId, context) -> new SnomedConcepts(0, 0);
		
		SnomedRefSetDSVExportModel exportSetting = new SnomedRefSetDSVExportModel();
		exportSetting.setDelimiter("\t");
		exportSetting.addExportItem(new SimpleSnomedDsvExportItem(SnomedDsvExportItemType.CONCEPT_ID));
		exportSetting.addExportItem(new ComponentIdSnomedDsvExportItem(SnomedDsvExportItemType.RELATIONSHIP, Concepts.HAS_ACTIVE_INGREDIENT, "Ingredient count"));
		
		String expectedHeader = exportSetting.getExportItems()
			.stream()
			.map(i -> i.getDisplayName())
			.collect(Collectors.joining(exportSetting.getDelimiter()));
		
		String expectedData = String.join(exportSetting.getDelimiter(),
			concept.getId(),
			numberOfIngredients.getValue());
		
		var exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		String exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have a header and a data row with relationship value", String.join(LS, expectedHeader, expectedData) + LS, exportContents);
		
		// Second run: try and include the destination SCTID for a relationship value (which it doesn't have)
		exportSetting.setIncludeRelationshipTargetId(true);
		
		// As a result of the above setting, "Ingredient count" appears twice in the header (once for the ID and once for the description term)
		expectedHeader = String.join(exportSetting.getDelimiter(), "Concept ID", "Ingredient count", "Ingredient count");
		String expectedDetails = String.join(exportSetting.getDelimiter(), "", "ID", "Destination");
		expectedData = String.join(exportSetting.getDelimiter(), concept.getId(), "", numberOfIngredients.getValue());

		exporter = new SnomedSimpleTypeRefSetDSVExporter(null, conceptStreamFactory, ancestorCollector, exportSetting);
		exportContents = getContentsAndDelete(exporter);
		assertEquals("Export file should have a header and a data row with an empty relationship destination SCTID and populated value", String.join(LS, expectedHeader, expectedDetails, expectedData) + LS, exportContents);
	}	
}
