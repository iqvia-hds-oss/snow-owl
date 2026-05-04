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
package com.b2international.snowowl.fhir.rest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.r5.elementmodel.Manager.FhirFormat;
import org.hl7.fhir.r5.model.Enumerations.FHIRVersion;
import org.hl7.fhir.r5.model.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.server.NotAcceptableStatusException;

import com.b2international.commons.StringUtils;
import com.b2international.fhir.operations.OperationParametersFactory;
import com.b2international.fhir.r5.operations.BaseParameters;
import com.b2international.snowowl.fhir.core.FhirResourceParser;
import com.google.common.collect.Maps;

/**
 * @since 9.4.0
 */
public final class FhirMediaType {

	// FHIR-specific media types should be supplied in "content-type" and "accept" headers
	// This always resolves to the available latest FHIR version in the server 
	public static final String APPLICATION_FHIR_JSON_VALUE = "application/fhir+json";
	public static final String APPLICATION_FHIR_XML_VALUE = "application/fhir+xml";
	
	// Versioned FHIR-specific media types should be supplied in "content-type" and "accept" headers
	public static final String MIME_TYPE_FHIR_VERSION_PARAMETER = "fhirVersion";
	public static final String APPLICATION_FHIR_JSON_4_0_1_VALUE = "application/fhir+json;fhirVersion=4.0.1";
	public static final String APPLICATION_FHIR_XML_4_0_1_VALUE = "application/fhir+xml;fhirVersion=4.0.1";
	public static final String APPLICATION_FHIR_JSON_4_0_VALUE = "application/fhir+json;fhirVersion=4.0";
	public static final String APPLICATION_FHIR_XML_4_0_VALUE = "application/fhir+xml;fhirVersion=4.0";
	
	public static final String APPLICATION_FHIR_JSON_4_3_0_VALUE = "application/fhir+json;fhirVersion=4.3.0";
	public static final String APPLICATION_FHIR_XML_4_3_0_VALUE = "application/fhir+xml;fhirVersion=4.3.0";
	public static final String APPLICATION_FHIR_JSON_4_3_VALUE = "application/fhir+json;fhirVersion=4.3";
	public static final String APPLICATION_FHIR_XML_4_3_VALUE = "application/fhir+xml;fhirVersion=4.3";
	
	public static final String APPLICATION_FHIR_JSON_5_0_0_VALUE = "application/fhir+json;fhirVersion=5.0.0";
	public static final String APPLICATION_FHIR_XML_5_0_0_VALUE = "application/fhir+xml;fhirVersion=5.0.0";
	public static final String APPLICATION_FHIR_JSON_5_0_VALUE = "application/fhir+json;fhirVersion=5.0";
	public static final String APPLICATION_FHIR_XML_5_0_VALUE = "application/fhir+xml;fhirVersion=5.0";

	// Short values are only admitted as _format parameters
	public static final String FORMAT_JSON = FhirResourceParser.FORMAT_JSON;
	public static final String FORMAT_XML = FhirResourceParser.FORMAT_XML;

	// More general media types are allowed both as a _format parameter as well as an "accept" header
	public static final String TEXT_JSON_VALUE = "text/json";
	public static final String TEXT_XML_VALUE = MediaType.TEXT_XML_VALUE;
	
	public static final String APPLICATION_JSON_VALUE = MediaType.APPLICATION_JSON_VALUE;
	public static final String APPLICATION_XML_VALUE = MediaType.APPLICATION_XML_VALUE;

	// Keep parsed forms of our custom media types around as well
	public static final MediaType TEXT_JSON = MediaType.parseMediaType(TEXT_JSON_VALUE);
	
	public static final MediaType APPLICATION_FHIR_JSON = MediaType.parseMediaType(APPLICATION_FHIR_JSON_VALUE);
	public static final MediaType APPLICATION_FHIR_XML = MediaType.parseMediaType(APPLICATION_FHIR_XML_VALUE);
	
	public static final MediaType APPLICATION_FHIR_JSON_4_0 = MediaType.parseMediaType(APPLICATION_FHIR_JSON_4_0_VALUE);
	public static final MediaType APPLICATION_FHIR_JSON_4_0_1 = MediaType.parseMediaType(APPLICATION_FHIR_JSON_4_0_1_VALUE);
	public static final MediaType APPLICATION_FHIR_XML_4_0 = MediaType.parseMediaType(APPLICATION_FHIR_XML_4_0_VALUE);
	public static final MediaType APPLICATION_FHIR_XML_4_0_1 = MediaType.parseMediaType(APPLICATION_FHIR_XML_4_0_1_VALUE);
	
	public static final MediaType APPLICATION_FHIR_JSON_4_3 = MediaType.parseMediaType(APPLICATION_FHIR_JSON_4_3_VALUE);
	public static final MediaType APPLICATION_FHIR_JSON_4_3_0 = MediaType.parseMediaType(APPLICATION_FHIR_JSON_4_3_0_VALUE);
	public static final MediaType APPLICATION_FHIR_XML_4_3 = MediaType.parseMediaType(APPLICATION_FHIR_XML_4_3_VALUE);
	public static final MediaType APPLICATION_FHIR_XML_4_3_0 = MediaType.parseMediaType(APPLICATION_FHIR_XML_4_3_0_VALUE);
	
	public static final MediaType APPLICATION_FHIR_JSON_5_0 = MediaType.parseMediaType(APPLICATION_FHIR_JSON_5_0_VALUE);
	public static final MediaType APPLICATION_FHIR_JSON_5_0_0 = MediaType.parseMediaType(APPLICATION_FHIR_JSON_5_0_0_VALUE);
	public static final MediaType APPLICATION_FHIR_XML_5_0 = MediaType.parseMediaType(APPLICATION_FHIR_XML_5_0_VALUE);
	public static final MediaType APPLICATION_FHIR_XML_5_0_0 = MediaType.parseMediaType(APPLICATION_FHIR_XML_5_0_0_VALUE);

	// Last ditch effort: return JSON when "*/*" media type is accepted by the client
	public static final String ALL_VALUE = MediaType.ALL_VALUE;
	
	/**
	 * All currently supported media type values (versioned and unversioned forms)
	 */
	public static final String[] SUPPORTED_MEDIA_TYPE_VALUES = new String[] {
		APPLICATION_FHIR_JSON_5_0_VALUE,
		APPLICATION_FHIR_JSON_5_0_0_VALUE,
		APPLICATION_FHIR_JSON_4_3_VALUE,
		APPLICATION_FHIR_JSON_4_3_0_VALUE,
		APPLICATION_FHIR_JSON_4_0_VALUE,
		APPLICATION_FHIR_JSON_4_0_1_VALUE,
		APPLICATION_FHIR_JSON_VALUE,
		APPLICATION_JSON_VALUE,
		TEXT_JSON_VALUE,
		
		APPLICATION_FHIR_XML_5_0_VALUE,
		APPLICATION_FHIR_XML_5_0_0_VALUE,
		APPLICATION_FHIR_XML_4_3_VALUE,
		APPLICATION_FHIR_XML_4_3_0_VALUE,
		APPLICATION_FHIR_XML_4_0_VALUE,
		APPLICATION_FHIR_XML_4_0_1_VALUE,
		APPLICATION_FHIR_XML_VALUE,
		APPLICATION_XML_VALUE,
		TEXT_XML_VALUE
	};
	
	/**
	 * All currently supported media types (versioned and unversioned forms)
	 */
	public static final List<MediaType> SUPPORTED_MEDIA_TYPES = List.of(
		APPLICATION_FHIR_JSON_5_0,
		APPLICATION_FHIR_JSON_5_0_0,
		APPLICATION_FHIR_XML_5_0,
		APPLICATION_FHIR_XML_5_0_0,
		
		APPLICATION_FHIR_JSON_4_3,
		APPLICATION_FHIR_JSON_4_3_0,
		APPLICATION_FHIR_XML_4_3,
		APPLICATION_FHIR_XML_4_3_0,
		
		APPLICATION_FHIR_JSON_4_0,
		APPLICATION_FHIR_JSON_4_0_1,
		APPLICATION_FHIR_XML_4_0,
		APPLICATION_FHIR_XML_4_0_1,
		
		APPLICATION_FHIR_JSON,
		APPLICATION_FHIR_XML,
		
		TEXT_JSON,
		MediaType.TEXT_XML,
		
		MediaType.APPLICATION_JSON,
		MediaType.APPLICATION_XML
	);
	
	/**
	 * The supported list of FHIR versions.
	 */
	public static final List<FHIRVersion> SUPPORTED_FHIR_VERSIONS = List.of(
		FHIRVersion._4_0,
		FHIRVersion._4_0_1,
		FHIRVersion._4_3,
		FHIRVersion._4_3_0,
		FHIRVersion._5_0,
		FHIRVersion._5_0_0
	);
	
	// The FHIR Version Snow Owl by default uses (should match the model version implementation used in snowowl.fhir.core)
	public static final FHIRVersion DEFAULT_FHIR_VERSION = FHIRVersion._5_0_0;
	protected static final MediaType CURRENT_JSON_MEDIA_TYPE = APPLICATION_FHIR_JSON_5_0_0;
	protected static final MediaType CURRENT_XML_MEDIA_TYPE = APPLICATION_FHIR_XML_5_0_0;
	
	private final MediaType mediaType;
	private final FhirResourceParser fhirResourceParser;
	
	private FhirMediaType(MediaType mediaType) {
		this.mediaType = Objects.requireNonNull(mediaType);
		// parse the FHIR format from the media type subtype
		final String fhirFormat = mediaType.getSubtype();
		// parse the FHIR version from the media type version parameter
		final String fhirVersionValue = mediaType.getParameter(MIME_TYPE_FHIR_VERSION_PARAMETER);
		this.fhirResourceParser = new FhirResourceParser(fhirFormat, fhirVersionValue);
	}

	public MediaType getMediaType() {
		return this.mediaType;
	}
	
	public FhirFormat getFhirFormat() {
		return fhirResourceParser.getFhirFormat();
	}
	
	public FHIRVersion getFhirVersion() {
		return fhirResourceParser.getFhirVersion();
	}
	
	public Resource parseResource(InputStream in) throws FHIRFormatError, IOException {
		return fhirResourceParser.parseResource(in);
	}
	
	public void writeResource(ByteArrayOutputStream baos, Resource resource, boolean pretty) throws FHIRFormatError, IOException {
		fhirResourceParser.writeResource(baos, resource, pretty);
	}
	
	public BaseParameters parseParameters(InputStream in, OperationParametersFactory factory, boolean strict) throws FHIRFormatError, IOException {
		return fhirResourceParser.parseParameters(in, factory, strict);
	}
	
	public void writeParameters(ByteArrayOutputStream baos, BaseParameters parameters, boolean pretty) throws FHIRFormatError, IOException {
		fhirResourceParser.writeParameters(baos, parameters, pretty);
	}

	public static FhirMediaType parse(String header, String _format) {
		return new FhirMediaType(getMediaType(header, _format));
	}
	
	private static MediaType getMediaType(final String accept, final String _format) {
		/*
		 * The _format query parameter allows overriding whatever comes in as the "accept"
		 * header value (for scenarios where the client has no control over the header).
		 */
		if (!StringUtils.isEmpty(_format)) {
			return getFormat(_format);
		} else if (!StringUtils.isEmpty(accept)) {
			List<MediaType> mediaTypeCandidates = getMediaTypeCandidates(accept);
			if (!mediaTypeCandidates.isEmpty()) {
				return getFormat(mediaTypeCandidates.get(0).toString());
			} else {
				return getFormat(accept);
			}
		} else {
			return CURRENT_JSON_MEDIA_TYPE;
		}
	}
	
	private static List<MediaType> getMediaTypeCandidates(final String accept) {
		// */* should delegate to current JSON format
		if (MediaType.ALL_VALUE.equals(accept)) {
			return List.of(CURRENT_JSON_MEDIA_TYPE);
		}
		
		final List<MediaType> mediaTypeCandidates = MediaType.parseMediaTypes(accept);
		
		if (!mediaTypeCandidates.isEmpty()) {
			MediaType.sortBySpecificityAndQuality(mediaTypeCandidates);
			
			// Remove quality values and other (eg. charset) parameters once the list is sorted
			for (int i = 0; i < mediaTypeCandidates.size(); i++) {
				MediaType oldType = mediaTypeCandidates.get(i);
				
				// keep only the fhirVersion parameter if defined
				var reducedParametersMap = Maps.filterEntries(oldType.getParameters(), (entry) -> MIME_TYPE_FHIR_VERSION_PARAMETER.equals(entry.getKey()));
				mediaTypeCandidates.set(i, new MediaType(oldType, reducedParametersMap));
			}
			
			mediaTypeCandidates.retainAll(SUPPORTED_MEDIA_TYPES);
		}
		
		return mediaTypeCandidates;
	}
	
	private static MediaType getFormat(final String mediaType) {
		switch (mediaType) {
		
			case FORMAT_JSON: //$FALL-THROUGH$
			case TEXT_JSON_VALUE: //$FALL-THROUGH$
			case APPLICATION_FHIR_JSON_VALUE: //$FALL-THROUGH$
			case APPLICATION_JSON_VALUE: //$FALL-THROUGH$
			case ALL_VALUE:
				return CURRENT_JSON_MEDIA_TYPE;
			
			case APPLICATION_FHIR_JSON_4_0_VALUE:
			case APPLICATION_FHIR_JSON_4_0_1_VALUE:
				return APPLICATION_FHIR_JSON_4_0_1;
			
			case APPLICATION_FHIR_JSON_4_3_VALUE:
			case APPLICATION_FHIR_JSON_4_3_0_VALUE:
				return APPLICATION_FHIR_JSON_4_3_0;
			
			case APPLICATION_FHIR_JSON_5_0_VALUE:
			case APPLICATION_FHIR_JSON_5_0_0_VALUE:
				return APPLICATION_FHIR_JSON_5_0_0;
				
			case FORMAT_XML: //$FALL-THROUGH$
			case TEXT_XML_VALUE: //$FALL-THROUGH$
			case APPLICATION_FHIR_XML_VALUE: //$FALL-THROUGH$
			case APPLICATION_XML_VALUE:
				return CURRENT_XML_MEDIA_TYPE;
			
			case APPLICATION_FHIR_XML_4_0_VALUE: 
			case APPLICATION_FHIR_XML_4_0_1_VALUE: 
				return APPLICATION_FHIR_XML_4_0_1;
			
			case APPLICATION_FHIR_XML_4_3_VALUE:
			case APPLICATION_FHIR_XML_4_3_0_VALUE:
				return APPLICATION_FHIR_XML_4_3_0;
			
			case APPLICATION_FHIR_XML_5_0_VALUE:
			case APPLICATION_FHIR_XML_5_0_0_VALUE:
				return APPLICATION_FHIR_XML_5_0_0;
				
			default:
				throw new NotAcceptableStatusException(SUPPORTED_MEDIA_TYPES);
		}
	}

}
