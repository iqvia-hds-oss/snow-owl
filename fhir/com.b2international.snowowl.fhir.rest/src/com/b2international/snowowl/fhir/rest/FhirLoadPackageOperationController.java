/*
 * Copyright 2026 B2i Healthcare, https://b2ihealthcare.com
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

import static com.b2international.snowowl.fhir.rest.FhirMediaType.APPLICATION_FHIR_JSON_4_0_1_VALUE;
import static com.b2international.snowowl.fhir.rest.FhirMediaType.APPLICATION_FHIR_JSON_4_0_VALUE;
import static com.b2international.snowowl.fhir.rest.FhirMediaType.APPLICATION_FHIR_JSON_4_3_0_VALUE;
import static com.b2international.snowowl.fhir.rest.FhirMediaType.APPLICATION_FHIR_JSON_4_3_VALUE;
import static com.b2international.snowowl.fhir.rest.FhirMediaType.APPLICATION_FHIR_JSON_5_0_0_VALUE;
import static com.b2international.snowowl.fhir.rest.FhirMediaType.APPLICATION_FHIR_JSON_5_0_VALUE;
import static com.b2international.snowowl.fhir.rest.FhirMediaType.APPLICATION_FHIR_JSON_VALUE;
import static com.b2international.snowowl.fhir.rest.FhirMediaType.APPLICATION_FHIR_XML_4_0_1_VALUE;
import static com.b2international.snowowl.fhir.rest.FhirMediaType.APPLICATION_FHIR_XML_4_0_VALUE;
import static com.b2international.snowowl.fhir.rest.FhirMediaType.APPLICATION_FHIR_XML_4_3_0_VALUE;
import static com.b2international.snowowl.fhir.rest.FhirMediaType.APPLICATION_FHIR_XML_4_3_VALUE;
import static com.b2international.snowowl.fhir.rest.FhirMediaType.APPLICATION_FHIR_XML_5_0_0_VALUE;
import static com.b2international.snowowl.fhir.rest.FhirMediaType.APPLICATION_FHIR_XML_5_0_VALUE;
import static com.b2international.snowowl.fhir.rest.FhirMediaType.APPLICATION_FHIR_XML_VALUE;
import static com.b2international.snowowl.fhir.rest.FhirMediaType.APPLICATION_JSON_VALUE;
import static com.b2international.snowowl.fhir.rest.FhirMediaType.APPLICATION_XML_VALUE;
import static com.b2international.snowowl.fhir.rest.FhirMediaType.TEXT_JSON_VALUE;
import static com.b2international.snowowl.fhir.rest.FhirMediaType.TEXT_XML_VALUE;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.b2international.snowowl.core.attachments.Attachment;
import com.b2international.snowowl.core.attachments.AttachmentRegistry;
import com.b2international.snowowl.core.events.util.Promise;
import com.b2international.snowowl.core.rest.PreferHandlingInterceptor;
import com.b2international.snowowl.fhir.core.request.FhirRequests;
import com.b2international.snowowl.fhir.core.request.packages.FhirLoadPackageParameters;
import com.b2international.snowowl.fhir.core.request.packages.FhirLoadPackageParametersFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller for the FHIR $load-package operation.
 * 
 * @since 10.1.0
 */
@Tag(description = "Packages", name = "Packages")
@RestController
@RequestMapping(value = "/$load-package")
public class FhirLoadPackageOperationController extends AbstractFhirController {

	@Autowired
	private AttachmentRegistry attachments;
	
	/**
	 * <code><b>POST /$load-package</b></code> - Registry download mode
	 * <p>
	 * Downloads a FHIR package from the specified NPM registry and imports
	 * the terminology resources (CodeSystem, ValueSet, ConceptMap).
	 * 
	 * @param requestBody - FHIR Parameters resource containing package details
	 * @param contentType - Content-Type header
	 * @param accept - Accept header
	 * @param prefer - Prefer header for handling mode
	 * @param _format - Alternative response format
	 * @param _pretty - Pretty print flag
	 * @return Job ID in Location header with 201 Created
	 */
	@Operation(
		summary = "Load FHIR Package from registry",
		description = "Downloads a FHIR package from the specified registry and imports terminology resources."
	)
	@ApiResponse(responseCode = "201", description = "Package loading job created")
	@ApiResponse(responseCode = "400", description = "Bad request")
	@PostMapping(
		consumes = {
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
		},
		produces = {
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
		}
	)
	public Promise<ResponseEntity<byte[]>> loadPackageFromRegistry(
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The operation's input parameters", content = {
			@Content(mediaType = APPLICATION_FHIR_JSON_5_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_5_0_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_4_3_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_4_3_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_4_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_4_0_1_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = TEXT_JSON_VALUE, schema = @Schema(type = "object"))
		})
		final InputStream requestBody,
		
		@Parameter(hidden = true)
		@RequestHeader(value = HttpHeaders.CONTENT_TYPE)
		final String contentType,
		
		@Parameter(hidden = true)
		@RequestHeader(value = HttpHeaders.ACCEPT)
		final String accept,
		
		@Parameter(description = "Prefer header", schema = @Schema(
			allowableValues = { PreferHandlingInterceptor.PREFER_HANDLING_STRICT, PreferHandlingInterceptor.PREFER_HANDLING_LENIENT }, 
			defaultValue = PreferHandlingInterceptor.PREFER_HANDLING_LENIENT
		))
		@RequestHeader(value = PreferHandlingInterceptor.PREFER_HEADER, required = false)
		final String prefer,

		@Parameter(description = "Alternative response format", schema = @Schema(allowableValues = {
			APPLICATION_FHIR_JSON_5_0_VALUE,
			APPLICATION_FHIR_JSON_5_0_0_VALUE,
			APPLICATION_FHIR_JSON_4_3_VALUE,
			APPLICATION_FHIR_JSON_4_3_0_VALUE,
			APPLICATION_FHIR_JSON_4_0_VALUE,
			APPLICATION_FHIR_JSON_4_0_1_VALUE,
			APPLICATION_FHIR_JSON_VALUE,
			APPLICATION_JSON_VALUE,
			TEXT_JSON_VALUE
		}))
		@RequestParam(value = "_format", required = false)
		final String _format,
		
		@Parameter(description = "Controls pretty-printing of response")
		@RequestParam(value = "_pretty", required = false)
		final Boolean _pretty
	) {
		FhirLoadPackageParameters parameters = toFhirParameters(requestBody, contentType, prefer, FhirLoadPackageParametersFactory.INSTANCE);
		
		return loadPackage(parameters, null, accept, _format, _pretty);
	}

	/**
	 * <code><b>POST /$load-package</b></code> - Local upload mode
	 * <p>
	 * Uploads a local FHIR package (.tgz file) and imports the terminology
	 * resources (CodeSystem, ValueSet, ConceptMap).
	 * 
	 * @param file - The FHIR package tarball file
	 * @param dependencies - Whether to load dependencies (default: true)
	 * @param accept - Accept header
	 * @param _format - Alternative response format
	 * @param _pretty - Pretty print flag
	 * @return Job ID in Location header with 201 Created
	 */
	@Operation(
		summary = "Load FHIR Package from local file",
		description = "Uploads a local FHIR package (.tgz file) and imports terminology resources."
	)
	@ApiResponse(responseCode = "201", description = "Package loading job created")
	@ApiResponse(responseCode = "400", description = "Bad request")
	@PostMapping(
		consumes = { MULTIPART_MEDIA_TYPE },
		produces = {
			APPLICATION_FHIR_JSON_5_0_VALUE,
			APPLICATION_FHIR_JSON_5_0_0_VALUE,
			APPLICATION_FHIR_JSON_4_3_VALUE,
			APPLICATION_FHIR_JSON_4_3_0_VALUE,
			APPLICATION_FHIR_JSON_4_0_VALUE,
			APPLICATION_FHIR_JSON_4_0_1_VALUE,
			APPLICATION_FHIR_JSON_VALUE,
			APPLICATION_JSON_VALUE,
			TEXT_JSON_VALUE
		}
	)
	public Promise<ResponseEntity<byte[]>> loadPackageFromUpload(
		@Parameter(description = "FHIR package file (.tgz)", required = true)
		@RequestPart("file")
		final MultipartFile file,
		
		@Parameter(description = "Load dependencies (default: true)")
		@RequestParam(value = "dependencies", defaultValue = "true")
		final Boolean dependencies,
		
		@Parameter(hidden = true)
		@RequestHeader(value = HttpHeaders.ACCEPT, required = false)
		final String accept,
		
		@Parameter(description = "Alternative response format", schema = @Schema(allowableValues = {
			APPLICATION_FHIR_JSON_5_0_VALUE,
			APPLICATION_FHIR_JSON_5_0_0_VALUE,
			APPLICATION_FHIR_JSON_4_3_VALUE,
			APPLICATION_FHIR_JSON_4_3_0_VALUE,
			APPLICATION_FHIR_JSON_4_0_VALUE,
			APPLICATION_FHIR_JSON_4_0_1_VALUE,
			APPLICATION_FHIR_JSON_VALUE,
			APPLICATION_JSON_VALUE,
			TEXT_JSON_VALUE
		}))
		@RequestParam(value = "_format", required = false)
		final String _format,
		
		@Parameter(description = "Controls pretty-printing of response")
		@RequestParam(value = "_pretty", required = false)
		final Boolean _pretty
	) throws IOException {
		
		final UUID fhirPackageAttachmentId = UUID.randomUUID();
		attachments.upload(fhirPackageAttachmentId, file.getInputStream());
		
		FhirLoadPackageParameters params = new FhirLoadPackageParameters();
		params.setDependencies(dependencies);
		return loadPackage(params, new Attachment(fhirPackageAttachmentId, file.getOriginalFilename()), accept, _format, _pretty);
	}

	private Promise<ResponseEntity<byte[]>> loadPackage(
		final FhirLoadPackageParameters parameters,
		final Attachment packageToLoad,
		final String accept, 
		final String _format, 
		final Boolean _pretty
	) {
		return FhirRequests.loadPackage()
			.setParameters(parameters)
			.setPackageToLoad(packageToLoad)
			.buildAsync()
			.execute(getBus())
			.then(result -> {
				return toResponseEntity(result, accept, _format, _pretty);
			});
	}
}