/*
 * Copyright 2018-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.fhir.rest.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import com.b2international.snowowl.fhir.core.FhirException;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.fhir.SnomedUri;
import com.b2international.snowowl.snomed.fhir.SnomedUri.QueryPartDefinition;

/**
 * @since 6.7
 */
public class SnomedUriParsingTest {
	
	@Test
	public void testInvalidBase() {
		assertThrows(
			"URI 'invalid uri' is not a valid SNOMED CT URI. It should start as 'http://snomed.info/sct'", 
			FhirException.class, 
			() -> SnomedUri.fromUriString("invalid uri", "uri")
		);
	}
	
	@Test
	public void testInvalidModule() {
		assertThrows(
			"Invalid SNOMED CT extension module ID [a] defined.", 
			FhirException.class, 
			() -> SnomedUri.fromUriString(FhirModelHelpers.SNOMED_BASE_URI_STRING + "/a", "uri")
		);
	}
	
	@Test
	public void testInvalidVersionTag() {
		assertThrows(
			"Invalid path segment [invalidTag], 'version' expected.", 
			FhirException.class, 
			() -> SnomedUri.fromUriString(FhirModelHelpers.SNOMED_BASE_URI_STRING + "/900000000000207008/invalidTag", "uri")
		);
	}
	
	@Test
	public void testMissingVersionTag() {
		assertThrows(
			"No version tag is specified after the 'version' parameter.", 
			FhirException.class, 
			() -> SnomedUri.fromUriString(FhirModelHelpers.SNOMED_BASE_URI_STRING + "/900000000000207008/version", "uri")
		);
	}

	//correct paths
	@Test
	public void testIntEditionUri() {
		
		String uriString = FhirModelHelpers.SNOMED_BASE_URI_STRING;
		
		SnomedUri snomedUri = SnomedUri.fromUriString(uriString, "uri");
		
		assertNull(snomedUri.getExtensionModuleId());
		assertNull(snomedUri.getVersionTag());
		
	}
	
	@Test
	public void testIntEditionWithModuleUri() {
		
		String uriString = FhirModelHelpers.SNOMED_BASE_URI_STRING + "/" + Concepts.MODULE_SCT_CORE;
		
		SnomedUri snomedUri = SnomedUri.fromUriString(uriString, "uri");
		
		assertEquals(Concepts.MODULE_SCT_CORE, snomedUri.getExtensionModuleId());
		assertNull(snomedUri.getVersionTag());
	}
	
	@Test
	public void testIntEditionWithVersionUri() {
		
		String uriString = FhirModelHelpers.SNOMED_BASE_URI_STRING + "/" + Concepts.MODULE_SCT_CORE + "/version/20170131";
		
		SnomedUri snomedUri = SnomedUri.fromUriString(uriString, "uri");
		
		assertEquals(Concepts.MODULE_SCT_CORE, snomedUri.getExtensionModuleId());
		assertEquals("20170131", snomedUri.getVersionTag());
	}
	
	@Test
	public void testUriWithQuery() {
		
		String uriString = "http://snomed.info/sct?fhir_vs";
		SnomedUri snomedUri = SnomedUri.fromUriString(uriString, "uri");
		
		assertEquals(null, snomedUri.getExtensionModuleId());
		assertEquals(null, snomedUri.getVersionTag());
		assertEquals("fhir_vs", snomedUri.getQueryPart().getQueryParameter());
		assertEquals(QueryPartDefinition.NONE, snomedUri.getQueryPart().getQueryPartDefinition());
		assertEquals(null, snomedUri.getQueryPart().getQueryValue());
		
		uriString = "http://snomed.info/sct?fhir_vs=isa/50697003";
		snomedUri = SnomedUri.fromUriString(uriString, "uri");
		
		assertEquals(null, snomedUri.getExtensionModuleId());
		assertEquals(null, snomedUri.getVersionTag());
		assertEquals("fhir_vs", snomedUri.getQueryPart().getQueryParameter());
		assertEquals(QueryPartDefinition.ISA, snomedUri.getQueryPart().getQueryPartDefinition());
		assertEquals("50697003", snomedUri.getQueryPart().getQueryValue());
		
		uriString = "http://snomed.info/sct?fhir_vs=refset";
		snomedUri = SnomedUri.fromUriString(uriString, "uri");
		
		assertEquals(null, snomedUri.getExtensionModuleId());
		assertEquals(null, snomedUri.getVersionTag());
		assertEquals("fhir_vs", snomedUri.getQueryPart().getQueryParameter());
		assertEquals(QueryPartDefinition.REFSETS, snomedUri.getQueryPart().getQueryPartDefinition());
		
		uriString = "http://snomed.info/sct?fhir_vs=refset/50697003";
		snomedUri = SnomedUri.fromUriString(uriString, "uri");
		
		assertEquals(null, snomedUri.getExtensionModuleId());
		assertEquals(null, snomedUri.getVersionTag());
		assertEquals("fhir_vs", snomedUri.getQueryPart().getQueryParameter());
		assertEquals(QueryPartDefinition.REFSET, snomedUri.getQueryPart().getQueryPartDefinition());
		assertEquals("50697003", snomedUri.getQueryPart().getQueryValue());
		
		uriString = "http://snomed.info/sct?fhir_cm=50697003";
		snomedUri = SnomedUri.fromUriString(uriString, "uri");
		
		assertEquals(null, snomedUri.getExtensionModuleId());
		assertEquals(null, snomedUri.getVersionTag());
		assertEquals("fhir_cm", snomedUri.getQueryPart().getQueryParameter());
		assertEquals(QueryPartDefinition.NONE, snomedUri.getQueryPart().getQueryPartDefinition());
		assertEquals("50697003", snomedUri.getQueryPart().getQueryValue());
		
	}
	
	@Test
	public void testComplexUriWithQueryPart() {
		
		String uriString = FhirModelHelpers.SNOMED_BASE_URI_STRING + "/" + Concepts.MODULE_SCT_CORE + "/version/20170131?fhir_vs=isa/50697003";
		
		SnomedUri snomedUri = SnomedUri.fromUriString(uriString, "uri");
		
		assertEquals(Concepts.MODULE_SCT_CORE, snomedUri.getExtensionModuleId());
		assertEquals("20170131", snomedUri.getVersionTag());
		assertEquals("fhir_vs", snomedUri.getQueryPart().getQueryParameter());
		assertEquals(QueryPartDefinition.ISA, snomedUri.getQueryPart().getQueryPartDefinition());
		assertEquals("50697003", snomedUri.getQueryPart().getQueryValue());
	}
	
	@Test
	public void testToUri() {
		
		SnomedUri baseUri = SnomedUri.builder().build();
		assertEquals(FhirModelHelpers.SNOMED_BASE_URI_STRING, baseUri.toString());
		
		SnomedUri uri = SnomedUri.builder().version("20180131").build();
		assertEquals(FhirModelHelpers.SNOMED_BASE_URI_STRING + "/version/20180131", uri.toString());
		
		uri = SnomedUri.builder()
				.extensionModuleId(Concepts.MODULE_SCT_CORE)
				.version("20180131").build();
		assertEquals(FhirModelHelpers.SNOMED_BASE_URI_STRING + "/" + Concepts.MODULE_SCT_CORE + "/version/20180131", uri.toString());
	}
	
	@Test
	public void testToUriWithQuery_fhir_cm() {
		SnomedUri uri = SnomedUri.builder().conceptMapQuery(Concepts.ROOT_CONCEPT).build();
		assertEquals(FhirModelHelpers.SNOMED_BASE_URI_STRING + "?fhir_cm=" + Concepts.ROOT_CONCEPT, uri.toString());
	}

	@Test
	public void testToUriWithQuery_fhir_vs() {
		SnomedUri uri = SnomedUri.builder().valueSetsQuery().build();
		assertEquals(FhirModelHelpers.SNOMED_BASE_URI_STRING + "?fhir_vs", uri.toString());
		assertEquals(true, FhirModelHelpers.isSnomedImplicitValueSetUrl(uri.toString()));
	}

	@Test
	public void testToUriWithQuery_fhir_vs_isa() {
		SnomedUri uri = SnomedUri.builder().isAQuery(Concepts.ROOT_CONCEPT).build();
		assertEquals(FhirModelHelpers.SNOMED_BASE_URI_STRING + "?fhir_vs=isa/" + Concepts.ROOT_CONCEPT, uri.toString());
		assertEquals(true, FhirModelHelpers.isSnomedImplicitValueSetUrl(uri.toString()));
	}

	@Test
	public void testToUriWithQuery_fhir_vs_refset() {
		SnomedUri uri = SnomedUri.builder().refsetsQuery().build();
		assertEquals(FhirModelHelpers.SNOMED_BASE_URI_STRING + "?fhir_vs=refset", uri.toString());
		assertEquals(true, FhirModelHelpers.isSnomedImplicitValueSetUrl(uri.toString()));
	}
	
	@Test
	public void testToUriWithQuery_fhir_vs_refset_slash() {
		SnomedUri uri = SnomedUri.builder().refsetQuery(Concepts.ROOT_CONCEPT).build();
		assertEquals(FhirModelHelpers.SNOMED_BASE_URI_STRING + "?fhir_vs=refset/" + Concepts.ROOT_CONCEPT, uri.toString());
		assertEquals(true, FhirModelHelpers.isSnomedImplicitValueSetUrl(uri.toString()));
	}
	
}
