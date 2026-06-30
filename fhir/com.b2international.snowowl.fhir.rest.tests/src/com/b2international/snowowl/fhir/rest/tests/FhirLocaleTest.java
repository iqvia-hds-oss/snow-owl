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
package com.b2international.snowowl.fhir.rest.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.hl7.fhir.r5.model.CodeType;
import org.junit.Test;

import com.b2international.commons.http.AcceptLanguageHeader;
import com.b2international.snowowl.fhir.core.exceptions.BadRequestException;
import com.b2international.snowowl.fhir.core.request.codesystem.FhirCodeSystemOperationRequest;

public class FhirLocaleTest {

	@Test
	public void compactNull() {
		assertEquals(AcceptLanguageHeader.DEFAULT_ACCEPT_LANGUAGE_HEADER, FhirCodeSystemOperationRequest.compactLocale((CodeType) null));
	}
	
	@Test
	public void compactNullValue() {
		assertEquals(AcceptLanguageHeader.DEFAULT_ACCEPT_LANGUAGE_HEADER, FhirCodeSystemOperationRequest.compactLocale(new CodeType()));
	}
	
	@Test
	public void compactEmptyValue() {
		assertEquals(AcceptLanguageHeader.DEFAULT_ACCEPT_LANGUAGE_HEADER, FhirCodeSystemOperationRequest.compactLocale(new CodeType("")));
	}
	
	@Test
	public void compactValidLanguage() {
		assertEquals("en", FhirCodeSystemOperationRequest.compactLocale(new CodeType("en")));
	}
	
	@Test
	public void compactValidCountry() {
		assertEquals("en-US", FhirCodeSystemOperationRequest.compactLocale(new CodeType("en-US")));
	}
	
	@Test
	public void compactValidPrivateUseExtension() {
		assertEquals("en-US-x-compact", FhirCodeSystemOperationRequest.compactLocale(new CodeType("en-US-x-compact")));
	}
	
	@Test
	public void compactLongPrivateUseExtension() {
		// 19 digits represent the SCTID of a language reference set
		assertEquals("en-US-x-1234567890123456789", FhirCodeSystemOperationRequest.compactLocale(new CodeType("en-US-x-12345678-90123456-789")));
	}
	
	@Test
	public void compactInvalidPrivateUseExtension() {
		// 19 digits as "unbroken" input is not allowed however
		assertThrows(BadRequestException.class, () -> FhirCodeSystemOperationRequest.compactLocale(new CodeType("en-US-x-1234567890123456789")));
	}
	
	@Test
	public void compactInvalidLanguage() {
		// 9 characters long input can not be accepted as a language tag
		assertThrows(BadRequestException.class, () -> FhirCodeSystemOperationRequest.compactLocale(new CodeType("notsubtag")));
	}
	
	@Test
	public void expandNull() {
		assertNull(FhirCodeSystemOperationRequest.expandLocale(null));
	}
	
	@Test
	public void expandEmpty() {
		assertNull(FhirCodeSystemOperationRequest.expandLocale(""));
	}
	
	@Test
	public void expandValidLanguage() {
		assertEquals("en", FhirCodeSystemOperationRequest.expandLocale("en"));
	}
	
	@Test
	public void expandValidCountry() {
		assertEquals("en-US", FhirCodeSystemOperationRequest.expandLocale("en-US"));
	}
	
	@Test
	public void expandShortPrivateUseExtension() {
		// Short private use extensions will work but are not supported
		assertEquals("en-US-x-expand", FhirCodeSystemOperationRequest.expandLocale("en-US-x-expand"));
	}
	
	@Test
	public void expandSplitPrivateUseExtension() {
		/*
		 * Extensions that are already broken up into shorter segments may get split
		 * further when passed through this method.
		 */
		assertEquals("en-US-x-expanded--priv-us-e", FhirCodeSystemOperationRequest.expandLocale("en-US-x-expanded-priv-use"));
	}
	
	@Test
	public void expandInvalidPrivateUseExtension() {
		// 19 digits should be broken up into at most 8 character segments 
		assertEquals("en-US-x-12345678-90123456-789", FhirCodeSystemOperationRequest.expandLocale("en-US-x-1234567890123456789"));
	}
	
}
