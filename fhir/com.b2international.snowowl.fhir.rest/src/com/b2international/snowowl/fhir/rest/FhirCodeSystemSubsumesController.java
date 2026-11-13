/*
 * Copyright 2023-2026 B2i Healthcare, https://b2ihealthcare.com
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

import static com.b2international.snowowl.fhir.rest.FhirMediaType.*;

import java.io.InputStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.b2international.fhir.operations.OperationParametersFactory;
import com.b2international.fhir.r5.operations.CodeSystemSubsumptionParameters;
import com.b2international.snowowl.core.events.util.Promise;
import com.b2international.snowowl.core.rest.FhirApiConfig;
import com.b2international.snowowl.core.rest.PreferHandlingInterceptor;
import com.b2international.snowowl.fhir.core.exceptions.BadRequestException;
import com.b2international.snowowl.fhir.core.request.FhirRequests;
import com.google.common.base.Strings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @since 9.0.0
 */
@Tag(description = "CodeSystem", name= FhirApiConfig.CODESYSTEM)
@RestController
@RequestMapping(value = "/CodeSystem")
public class FhirCodeSystemSubsumesController extends AbstractFhirController {

	/**
	 * <code><b>GET /CodeSystem/$subsumes</b></code>
	 * 
	 * @param codeA
	 * @param codeB
	 * @param system
	 * @param version
	 * @param accept
	 * @param _format
	 * @param _pretty
	 * @return
	 */
	@Operation(
		summary = "Subsumption testing",
		description = "Test the subsumption relationship between code/Coding A and code/Coding B given the semantics of subsumption in the underlying code system (see hierarchyMeaning)."
	)
	@ApiResponse(responseCode = "200", description = "OK")
	@ApiResponse(responseCode = "400", description = "Bad request")
	@ApiResponse(responseCode = "404", description = "CodeSystem not found")
	@GetMapping(value = "/$subsumes", produces = {
		APPLICATION_FHIR_JSON_5_0_0_VALUE,
		APPLICATION_FHIR_JSON_4_3_0_VALUE,
		APPLICATION_FHIR_JSON_4_0_1_VALUE,
		APPLICATION_FHIR_JSON_VALUE,
		APPLICATION_JSON_VALUE,
		TEXT_JSON_VALUE,
		
		APPLICATION_FHIR_XML_5_0_0_VALUE,
		APPLICATION_FHIR_XML_4_3_0_VALUE,
		APPLICATION_FHIR_XML_4_0_1_VALUE,
		APPLICATION_FHIR_XML_VALUE,
		APPLICATION_XML_VALUE,
		TEXT_XML_VALUE
	})
	public Promise<ResponseEntity<byte[]>> subsumes(
			
		@Parameter(description = "The \"A\" code that is to be tested") 
		@RequestParam(value = "codeA") 
		final String codeA,
		
		@Parameter(description = "The \"B\" code that is to be tested") 
		@RequestParam(value="codeB") 
		final String codeB,
		
		@Parameter(description = "The code system's uri") 
		@RequestParam(value="system") 
		final String system,
		
		@Parameter(description = "The code system version") 
		@RequestParam(value="version", required = false) 
		final String version,
		
		@Parameter(hidden = true)
		@RequestHeader(value = HttpHeaders.ACCEPT)
		final String accept,

		@Parameter(description = "Alternative response format", schema = @Schema(allowableValues = {
			APPLICATION_FHIR_JSON_5_0_0_VALUE,
			APPLICATION_FHIR_JSON_4_3_0_VALUE,
			APPLICATION_FHIR_JSON_4_0_1_VALUE,
			APPLICATION_FHIR_JSON_VALUE,
			APPLICATION_JSON_VALUE,
			TEXT_JSON_VALUE,
			
			APPLICATION_FHIR_XML_5_0_0_VALUE,
			APPLICATION_FHIR_XML_4_3_0_VALUE,
			APPLICATION_FHIR_XML_4_0_1_VALUE,
			APPLICATION_FHIR_XML_VALUE,
			APPLICATION_XML_VALUE,
			TEXT_XML_VALUE
		}))
		@RequestParam(value = "_format", required = false)
		final String _format,
		
		@Parameter(description = "Controls pretty-printing of response")
		@RequestParam(value = "_pretty", required = false)
		final Boolean _pretty
		
	) {
		
		var parameters = new CodeSystemSubsumptionParameters()
				.setCodeA(codeA)
				.setCodeB(codeB)
				.setSystem(system)
				.setVersion(version);
		
		validateSubsumptionRequest(parameters);
	
		return subsumes(parameters, accept, _format, _pretty);
	}

	/**
	 * <code><b>GET /CodeSystem/{id}/$subsumes</b></code>
	 * 
	 * @param codeSystemId
	 * @param codeA
	 * @param codeB
	 * @param accept
	 * @param _format
	 * @param _pretty
	 * @return
	 */
	@Operation(
		summary = "Subsumption testing",
		description = "Test the subsumption relationship between code/Coding A and code/Coding B given the semantics of subsumption in the underlying code system (see hierarchyMeaning)."
	)
	@ApiResponse(responseCode = "200", description = "OK")
	@ApiResponse(responseCode = "400", description = "Bad request")
	@ApiResponse(responseCode = "404", description = "Code system not found")
	@GetMapping(value = "/{id:**}/$subsumes", produces = {
		APPLICATION_FHIR_JSON_5_0_0_VALUE,
		APPLICATION_FHIR_JSON_4_3_0_VALUE,
		APPLICATION_FHIR_JSON_4_0_1_VALUE,
		APPLICATION_FHIR_JSON_VALUE,
		APPLICATION_JSON_VALUE,
		TEXT_JSON_VALUE,
		
		APPLICATION_FHIR_XML_5_0_0_VALUE,
		APPLICATION_FHIR_XML_4_3_0_VALUE,
		APPLICATION_FHIR_XML_4_0_1_VALUE,
		APPLICATION_FHIR_XML_VALUE,
		APPLICATION_XML_VALUE,
		TEXT_XML_VALUE
	})
	public Promise<ResponseEntity<byte[]>> subsumes(
			
		@Parameter(description = "The id of the code system to invoke the operation on")
		@PathVariable("id") 
		String codeSystemId,
		
		@Parameter(description = "The \"A\" code that is to be tested") 
		@RequestParam(value = "codeA") 
		final String codeA,
		
		@Parameter(description = "The \"B\" code that is to be tested") 
		@RequestParam(value = "codeB") 
		final String codeB,
		
		@Parameter(hidden = true)
		@RequestHeader(value = HttpHeaders.ACCEPT)
		final String accept,

		@Parameter(description = "Alternative response format", schema = @Schema(allowableValues = {
			APPLICATION_FHIR_JSON_5_0_0_VALUE,
			APPLICATION_FHIR_JSON_4_3_0_VALUE,
			APPLICATION_FHIR_JSON_4_0_1_VALUE,
			APPLICATION_FHIR_JSON_VALUE,
			APPLICATION_JSON_VALUE,
			TEXT_JSON_VALUE,
			
			APPLICATION_FHIR_XML_5_0_0_VALUE,
			APPLICATION_FHIR_XML_4_3_0_VALUE,
			APPLICATION_FHIR_XML_4_0_1_VALUE,
			APPLICATION_FHIR_XML_VALUE,
			APPLICATION_XML_VALUE,
			TEXT_XML_VALUE
		}))
		@RequestParam(value = "_format", required = false)
		final String _format,
		
		@Parameter(description = "Controls pretty-printing of response")
		@RequestParam(value = "_pretty", required = false)
		final Boolean _pretty
		
	) {
		var parameters = new CodeSystemSubsumptionParameters()
				.setCodeA(codeA)
				.setCodeB(codeB)
				.setSystem(codeSystemId);
		
		validateSubsumptionRequest(parameters);
		
		return subsumes(parameters, accept, _format, _pretty);		
	}
	
	/**
	 * <code><b>POST /CodeSystem/$subsumes</b></code>
	 * 
	 * @param requestBody - an {@link InputStream} whose contents can be deserialized to FHIR parameters
	 * @param contentType
	 * @param accept
	 * @param _format
	 * @param _pretty
	 * @return
	 */
	@Operation(
		summary = "Subsumption testing", 
		description = "Test the subsumption relationship between code/Coding A and code/Coding B given the semantics of subsumption in the underlying code system (see hierarchyMeaning)."
	)
	@ApiResponse(responseCode = "200", description = "OK")
	@ApiResponse(responseCode = "404", description = "Not found")
	@ApiResponse(responseCode = "400", description = "Bad request")
	@PostMapping(
		value = "/$subsumes", 
		consumes = {
			APPLICATION_FHIR_JSON_5_0_0_VALUE,
			APPLICATION_FHIR_JSON_4_3_0_VALUE,
			APPLICATION_FHIR_JSON_4_0_1_VALUE,
			APPLICATION_FHIR_JSON_VALUE,
			APPLICATION_JSON_VALUE,
			TEXT_JSON_VALUE,
			
			APPLICATION_FHIR_XML_5_0_0_VALUE,
			APPLICATION_FHIR_XML_4_3_0_VALUE,
			APPLICATION_FHIR_XML_4_0_1_VALUE,
			APPLICATION_FHIR_XML_VALUE,
			APPLICATION_XML_VALUE,
			TEXT_XML_VALUE
		},
		produces = {
			APPLICATION_FHIR_JSON_5_0_0_VALUE,
			APPLICATION_FHIR_JSON_4_3_0_VALUE,
			APPLICATION_FHIR_JSON_4_0_1_VALUE,
			APPLICATION_FHIR_JSON_VALUE,
			APPLICATION_JSON_VALUE,
			TEXT_JSON_VALUE,
			
			APPLICATION_FHIR_XML_5_0_0_VALUE,
			APPLICATION_FHIR_XML_4_3_0_VALUE,
			APPLICATION_FHIR_XML_4_0_1_VALUE,
			APPLICATION_FHIR_XML_VALUE,
			APPLICATION_XML_VALUE,
			TEXT_XML_VALUE
		}
	)
	public Promise<ResponseEntity<byte[]>> subsumes(
			
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The operation's input parameters", content = { 
			@Content(mediaType = APPLICATION_FHIR_JSON_5_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_5_0_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_4_3_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_4_3_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_4_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_4_0_1_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = TEXT_JSON_VALUE, schema = @Schema(type = "object")),

			@Content(mediaType = APPLICATION_FHIR_XML_5_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_5_0_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_4_3_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_4_3_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_4_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_4_0_1_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_XML_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = TEXT_XML_VALUE, schema = @Schema(type = "object"))
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
			APPLICATION_FHIR_JSON_5_0_0_VALUE,
			APPLICATION_FHIR_JSON_4_3_0_VALUE,
			APPLICATION_FHIR_JSON_4_0_1_VALUE,
			APPLICATION_FHIR_JSON_VALUE,
			APPLICATION_JSON_VALUE,
			TEXT_JSON_VALUE,
			
			APPLICATION_FHIR_XML_5_0_0_VALUE,
			APPLICATION_FHIR_XML_4_3_0_VALUE,
			APPLICATION_FHIR_XML_4_0_1_VALUE,
			APPLICATION_FHIR_XML_VALUE,
			APPLICATION_XML_VALUE,
			TEXT_XML_VALUE
		}))
		@RequestParam(value = "_format", required = false)
		final String _format,
		
		@Parameter(description = "Controls pretty-printing of response")
		@RequestParam(value = "_pretty", required = false)
		final Boolean _pretty		
				
	) {
		
		final CodeSystemSubsumptionParameters parameters = toFhirParameters(requestBody, contentType, prefer, OperationParametersFactory.CodeSystemSubsumptionParametersFactory.INSTANCE);
		
		validateSubsumptionRequest(parameters);
		
		return subsumes(parameters, accept, _format, _pretty);
	}
	
	/**
	 * <code><b>POST /CodeSystem/{id}/$subsumes</b></code>
	 * 
	 * @param codeSystemId
	 * @param requestBody - an {@link InputStream} whose contents can be deserialized to FHIR parameters
	 * @param contentType
	 * @param accept
	 * @param _format
	 * @param _pretty
	 * @return
	 */
	@Operation(
		summary = "Subsumption testing", 
		description = "Test the subsumption relationship between code/Coding A and code/Coding B given the semantics of subsumption in the underlying code system (see hierarchyMeaning)."
	)
	@ApiResponse(responseCode = "200", description = "OK")
	@ApiResponse(responseCode = "404", description = "Not found")
	@ApiResponse(responseCode = "400", description = "Bad request")
	@PostMapping(
		value = "/{id:**}/$subsumes", 
		consumes = {
			APPLICATION_FHIR_JSON_5_0_0_VALUE,
			APPLICATION_FHIR_JSON_4_3_0_VALUE,
			APPLICATION_FHIR_JSON_4_0_1_VALUE,
			APPLICATION_FHIR_JSON_VALUE,
			APPLICATION_JSON_VALUE,
			TEXT_JSON_VALUE,
			
			APPLICATION_FHIR_XML_5_0_0_VALUE,
			APPLICATION_FHIR_XML_4_3_0_VALUE,
			APPLICATION_FHIR_XML_4_0_1_VALUE,
			APPLICATION_FHIR_XML_VALUE,
			APPLICATION_XML_VALUE,
			TEXT_XML_VALUE
		},
		produces = {
			APPLICATION_FHIR_JSON_5_0_0_VALUE,
			APPLICATION_FHIR_JSON_4_3_0_VALUE,
			APPLICATION_FHIR_JSON_4_0_1_VALUE,
			APPLICATION_FHIR_JSON_VALUE,
			APPLICATION_JSON_VALUE,
			TEXT_JSON_VALUE,
			
			APPLICATION_FHIR_XML_5_0_0_VALUE,
			APPLICATION_FHIR_XML_4_3_0_VALUE,
			APPLICATION_FHIR_XML_4_0_1_VALUE,
			APPLICATION_FHIR_XML_VALUE,
			APPLICATION_XML_VALUE,
			TEXT_XML_VALUE
		}
	)
	public Promise<ResponseEntity<byte[]>> subsumes(

		@Parameter(description = "The id of the code system to invoke the operation on")
		@PathVariable("id") 
		String codeSystemId,
		
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The operation's input parameters", content = {
			@Content(mediaType = APPLICATION_FHIR_JSON_5_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_5_0_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_4_3_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_4_3_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_4_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_4_0_1_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = TEXT_JSON_VALUE, schema = @Schema(type = "object")),

			@Content(mediaType = APPLICATION_FHIR_XML_5_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_5_0_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_4_3_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_4_3_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_4_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_4_0_1_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_XML_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = TEXT_XML_VALUE, schema = @Schema(type = "object"))
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
			APPLICATION_FHIR_JSON_5_0_0_VALUE,
			APPLICATION_FHIR_JSON_4_3_0_VALUE,
			APPLICATION_FHIR_JSON_4_0_1_VALUE,
			APPLICATION_FHIR_JSON_VALUE,
			APPLICATION_JSON_VALUE,
			TEXT_JSON_VALUE,
			
			APPLICATION_FHIR_XML_5_0_0_VALUE,
			APPLICATION_FHIR_XML_4_3_0_VALUE,
			APPLICATION_FHIR_XML_4_0_1_VALUE,
			APPLICATION_FHIR_XML_VALUE,
			APPLICATION_XML_VALUE,
			TEXT_XML_VALUE
		}))
		@RequestParam(value = "_format", required = false)
		final String _format,
		
		@Parameter(description = "Controls pretty-printing of response")
		@RequestParam(value = "_pretty", required = false)
		final Boolean _pretty		
		
	) {
		
		final CodeSystemSubsumptionParameters parameters = toFhirParameters(requestBody, contentType, prefer, OperationParametersFactory.CodeSystemSubsumptionParametersFactory.INSTANCE);
		
		// Overwrite system with codeSystemId
		parameters.setSystem(codeSystemId);
		validateSubsumptionRequest(parameters);
		
		return subsumes(parameters, accept, _format, _pretty);
	}
	
	private Promise<ResponseEntity<byte[]>> subsumes(
		CodeSystemSubsumptionParameters req, 
		String accept, 
		String _format, 
		Boolean _pretty
	) {
		return FhirRequests.codeSystems().prepareSubsumes()
			.setParameters(req)
			.buildAsync()
			.execute(getBus())
			.then(result -> {
				return toResponseEntity(result, accept, _format, _pretty);
			});
	}
	
	private void validateSubsumptionRequest(CodeSystemSubsumptionParameters parameters) {
		// Note this system value can come from either the specific system parameter or either codings if provided
		if (Strings.isNullOrEmpty(parameters.extractSystem())) {
			throw new BadRequestException("Parameter 'system' is not specified for subsumption testing.", "SubsumptionRequest.system");
		}
		
		// We do allow using codes and codings together as long as there is only one for A and B
		final String codeA = parameters.getCodeA() != null ? parameters.getCodeA().getValue() : null;
		final String codeB = parameters.getCodeB() != null ? parameters.getCodeB().getValue() : null;
		final String codingAValue = parameters.getCodingA() != null ? parameters.getCodingA().getCode() : null;
		final String codingBValue = parameters.getCodingB() != null ? parameters.getCodingB().getCode() : null;
		
		if (!Strings.isNullOrEmpty(codeA) && !Strings.isNullOrEmpty(codingAValue)) {
			throw new BadRequestException("Provide either codeA or codingA not both.", "SubsumptionRequest");
		}
		if (Strings.isNullOrEmpty(codeA) && Strings.isNullOrEmpty(codingAValue)) {
			throw new BadRequestException("Provide either codeA or codingA.", "SubsumptionRequest");
		}
		if (!Strings.isNullOrEmpty(codeB) && !Strings.isNullOrEmpty(codingBValue)) {
			throw new BadRequestException("Provide either codeB or codingB not both.", "SubsumptionRequest");
		}
		if (Strings.isNullOrEmpty(codeB) && Strings.isNullOrEmpty(codingBValue)) {
			throw new BadRequestException("Provide either codeB or codingB.", "SubsumptionRequest");
		}
	}
}
