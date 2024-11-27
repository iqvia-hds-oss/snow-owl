/*
 * Copyright 2024 B2i Healthcare, https://b2ihealthcare.com
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
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.Test;

import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.core.domain.SnomedConcept;
import com.b2international.snowowl.snomed.core.domain.SnomedConcepts;
import com.b2international.snowowl.snomed.core.domain.SnomedDescription;
import com.b2international.snowowl.snomed.core.domain.SnomedDescriptions;
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
		assertEquals("Export file should have a header and a data row with PT SCTID and term", String.join(LS, expectedHeader, expectedDetails, expectedData) + LS, exportContents);
	}	
}
