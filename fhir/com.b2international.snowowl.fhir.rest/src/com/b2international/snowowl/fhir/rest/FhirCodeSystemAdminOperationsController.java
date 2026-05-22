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

import static com.b2international.snowowl.fhir.rest.FhirMediaType.*;

import java.io.InputStream;

import org.hl7.fhir.r5.model.BooleanType;
import org.hl7.fhir.r5.model.Parameters;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.b2international.snowowl.core.events.util.Promise;
import com.b2international.snowowl.core.rest.FhirApiConfig;
import com.b2international.snowowl.fhir.core.request.FhirRequests;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller exposing administrative instance-level {@code $operations} for {@code CodeSystem} resources:
 * <ul>
 *   <li>{@code $assign-fhir-url} &ndash; assigns an effective FHIR URL (and optional version property) to a code system</li>
 *   <li>{@code $set-as-default} &ndash; marks a code system as the FHIR default for its effective URL</li>
 *   <li>{@code $set-include-in-capabilities} &ndash; controls whether a code system appears in the TerminologyCapabilities statement</li>
 * </ul>
 *
 * @since 10.2.0
 */
@Tag(description = "CodeSystem", name = FhirApiConfig.CODESYSTEM)
@RestController
@RequestMapping(value = "/CodeSystem")
public class FhirCodeSystemAdminOperationsController extends AbstractFhirController {

	@Operation(
		summary = "Assign a FHIR URL to a code system",
		description = "Assigns a FHIR URL and an optional version property setting to the identified code system."
	)
	@ApiResponse(responseCode = "200", description = "OK")
	@ApiResponse(responseCode = "400", description = "Bad request")
	@ApiResponse(responseCode = "404", description = "Code system not found")
	@GetMapping(value = "/{id:**}/$assign-fhir-url", produces = {
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
	public Promise<ResponseEntity<byte[]>> assignFhirUrlGet(

		@Parameter(description = "The id of the code system to invoke the operation on")
		@PathVariable("id")
		final String codeSystemId,

		@Parameter(description = "The FHIR URL to assign to the code system")
		@RequestParam(value = "fhirUrl")
		final String fhirUrl,

		@Parameter(description = "The FHIR version property to use ('url' results in using the native URL field as version; absent or any other value uses the 'version' field)")
		@RequestParam(value = "fhirVersionProperty", required = false)
		final String fhirVersionProperty,

		@Parameter(description = "The user identifier used for committing the change")
		@RequestHeader(value = X_AUTHOR, required = false)
		final String author,

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
		return assignFhirUrl(codeSystemId, fhirUrl, fhirVersionProperty, author, accept, _format, _pretty);
	}

	@Operation(
		summary = "Assign a FHIR URL to a code system",
		description = "Assigns a FHIR URL and an optional version property setting to the identified code system."
	)
	@ApiResponse(responseCode = "200", description = "OK")
	@ApiResponse(responseCode = "400", description = "Bad request")
	@ApiResponse(responseCode = "404", description = "Code system not found")
	@PostMapping(
		value = "/{id:**}/$assign-fhir-url",
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
	public Promise<ResponseEntity<byte[]>> assignFhirUrlPost(

		@Parameter(description = "The id of the code system to invoke the operation on")
		@PathVariable("id")
		final String codeSystemId,

		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The operation's input parameters", content = {
			@Content(mediaType = APPLICATION_FHIR_JSON_5_0_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_4_3_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_4_0_1_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = TEXT_JSON_VALUE, schema = @Schema(type = "object")),

			@Content(mediaType = APPLICATION_FHIR_XML_5_0_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_4_3_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_4_0_1_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_XML_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = TEXT_XML_VALUE, schema = @Schema(type = "object"))
		})
		final InputStream requestBody,

		@Parameter(hidden = true)
		@RequestHeader(value = HttpHeaders.CONTENT_TYPE)
		final String contentType,

		@Parameter(description = "The user identifier used for committing the change")
		@RequestHeader(value = X_AUTHOR, required = false)
		final String author,

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
		final Parameters parameters = toFhirResource(requestBody, contentType, Parameters.class);
		final String fhirUrl = getStringParameter(parameters, "fhirUrl");
		final String fhirVersionProperty = getStringParameter(parameters, "fhirVersionProperty");
		return assignFhirUrl(codeSystemId, fhirUrl, fhirVersionProperty, author, accept, _format, _pretty);
	}

	private Promise<ResponseEntity<byte[]>> assignFhirUrl(
		final String codeSystemId,
		final String fhirUrl,
		final String fhirVersionProperty,
		final String author,
		final String accept,
		final String _format,
		final Boolean _pretty
	) {
		return FhirRequests.codeSystems()
			.prepareAssignFhirUrl()
			.setCodeSystemId(codeSystemId)
			.setFhirUrl(fhirUrl)
			.setFhirVersionProperty(fhirVersionProperty)
			.build(author, String.format("Assigning FHIR URL '%s' to code system '%s'", fhirUrl, codeSystemId))
			.execute(getBus())
			.then(result -> toResponseEntity(toResultParameters(result.getResultAs(Boolean.class)), accept, _format, _pretty));
	}

	@Operation(
		summary = "Set a code system as the FHIR default for its URL",
		description = "Marks the identified code system as the FHIR default for its effective URL. "
			+ "Any other code system sharing the same tooling ID and effective FHIR URL that currently "
			+ "carries the 'use as default' flag will have it cleared."
	)
	@ApiResponse(responseCode = "200", description = "OK")
	@ApiResponse(responseCode = "400", description = "Bad request")
	@ApiResponse(responseCode = "404", description = "Code system not found")
	@GetMapping(value = "/{id:**}/$set-as-default", produces = {
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
	public Promise<ResponseEntity<byte[]>> setAsDefaultGet(

		@Parameter(description = "The id of the code system to invoke the operation on")
		@PathVariable("id")
		final String codeSystemId,

		@Parameter(description = "The user identifier used for committing the change")
		@RequestHeader(value = X_AUTHOR, required = false)
		final String author,

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
		return setAsDefault(codeSystemId, author, accept, _format, _pretty);
	}

	@Operation(
		summary = "Set a code system as the FHIR default for its URL",
		description = "Marks the identified code system as the FHIR default for its effective URL. "
			+ "Any other code system sharing the same tooling ID and effective FHIR URL that currently "
			+ "carries the 'use as default' flag will have it cleared."
	)
	@ApiResponse(responseCode = "200", description = "OK")
	@ApiResponse(responseCode = "400", description = "Bad request")
	@ApiResponse(responseCode = "404", description = "Code system not found")
	@PostMapping(
		value = "/{id:**}/$set-as-default",
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
	public Promise<ResponseEntity<byte[]>> setAsDefaultPost(

		@Parameter(description = "The id of the code system to invoke the operation on")
		@PathVariable("id")
		final String codeSystemId,

		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The operation's input parameters (no parameters required)", content = {
			@Content(mediaType = APPLICATION_FHIR_JSON_5_0_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_4_3_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_4_0_1_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = TEXT_JSON_VALUE, schema = @Schema(type = "object")),

			@Content(mediaType = APPLICATION_FHIR_XML_5_0_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_4_3_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_4_0_1_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_XML_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = TEXT_XML_VALUE, schema = @Schema(type = "object"))
		})
		final InputStream requestBody,

		@Parameter(hidden = true)
		@RequestHeader(value = HttpHeaders.CONTENT_TYPE)
		final String contentType,

		@Parameter(description = "The user identifier used for committing the change")
		@RequestHeader(value = X_AUTHOR, required = false)
		final String author,

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
		// Parse and validate the body as a Parameters resource (no named parameters will be used for this operation though)
		toFhirResource(requestBody, contentType, Parameters.class);
		return setAsDefault(codeSystemId, author, accept, _format, _pretty);
	}

	private Promise<ResponseEntity<byte[]>> setAsDefault(
		final String codeSystemId,
		final String author,
		final String accept,
		final String _format,
		final Boolean _pretty
	) {
		return FhirRequests.codeSystems()
			.prepareSetAsDefault(codeSystemId)
			.build(author, String.format("Setting code system '%s' as FHIR default", codeSystemId))
			.execute(getBus())
			.then(result -> toResponseEntity(toResultParameters(result.getResultAs(Boolean.class)), accept, _format, _pretty));
	}

	@Operation(
		summary = "Set whether a code system is included in the TerminologyCapabilities statement",
		description = "Sets the 'include in capabilities' flag on the identified code system, controlling whether it appears in the server's TerminologyCapabilities response."
	)
	@ApiResponse(responseCode = "200", description = "OK")
	@ApiResponse(responseCode = "400", description = "Bad request")
	@ApiResponse(responseCode = "404", description = "Code system not found")
	@GetMapping(value = "/{id:**}/$set-include-in-capabilities", produces = {
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
	public Promise<ResponseEntity<byte[]>> setIncludeInCapabilitiesGet(

		@Parameter(description = "The id of the code system to invoke the operation on")
		@PathVariable("id")
		final String codeSystemId,

		@Parameter(description = "Whether to include the code system in the TerminologyCapabilities statement")
		@RequestParam(value = "includeInCapabilities")
		final boolean includeInCapabilities,

		@Parameter(description = "The user identifier used for committing the change")
		@RequestHeader(value = X_AUTHOR, required = false)
		final String author,

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
		return setIncludeInCapabilities(codeSystemId, includeInCapabilities, author, accept, _format, _pretty);
	}

	@Operation(
		summary = "Set whether a code system is included in the TerminologyCapabilities statement",
		description = "Sets the 'include in capabilities' flag on the identified code system, controlling whether it appears in the server's TerminologyCapabilities resource."
	)
	@ApiResponse(responseCode = "200", description = "OK")
	@ApiResponse(responseCode = "400", description = "Bad request")
	@ApiResponse(responseCode = "404", description = "Code system not found")
	@PostMapping(
		value = "/{id:**}/$set-include-in-capabilities",
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
	public Promise<ResponseEntity<byte[]>> setIncludeInCapabilitiesPost(

		@Parameter(description = "The id of the code system to invoke the operation on")
		@PathVariable("id")
		final String codeSystemId,

		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The operation's input parameters", content = {
			@Content(mediaType = APPLICATION_FHIR_JSON_5_0_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_4_3_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_4_0_1_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_JSON_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = TEXT_JSON_VALUE, schema = @Schema(type = "object")),

			@Content(mediaType = APPLICATION_FHIR_XML_5_0_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_4_3_0_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_4_0_1_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_FHIR_XML_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = APPLICATION_XML_VALUE, schema = @Schema(type = "object")),
			@Content(mediaType = TEXT_XML_VALUE, schema = @Schema(type = "object"))
		})
		final InputStream requestBody,

		@Parameter(hidden = true)
		@RequestHeader(value = HttpHeaders.CONTENT_TYPE)
		final String contentType,

		@Parameter(description = "The user identifier used for committing the change")
		@RequestHeader(value = X_AUTHOR, required = false)
		final String author,

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
		final Parameters parameters = toFhirResource(requestBody, contentType, Parameters.class);
		final boolean includeInCapabilities = getBooleanParameter(parameters, "includeInCapabilities");
		return setIncludeInCapabilities(codeSystemId, includeInCapabilities, author, accept, _format, _pretty);
	}

	private Promise<ResponseEntity<byte[]>> setIncludeInCapabilities(
		final String codeSystemId,
		final boolean includeInCapabilities,
		final String author,
		final String accept,
		final String _format,
		final Boolean _pretty
	) {
		return FhirRequests.codeSystems()
			.prepareSetIncludeInCapabilities()
			.setCodeSystemId(codeSystemId)
			.setIncludeInCapabilities(includeInCapabilities)
			.build(author, String.format("Setting 'include in capabilities' to '%s' for code system '%s'", includeInCapabilities, codeSystemId))
			.execute(getBus())
			.then(result -> toResponseEntity(toResultParameters(result.getResultAs(Boolean.class)), accept, _format, _pretty));
	}

	private static Parameters toResultParameters(final boolean result) {
		return new Parameters().addParameter("result", new BooleanType(result));
	}

	private static String getStringParameter(final Parameters parameters, final String name) {
		final var value = parameters.getParameterValue(name);
		return value != null ? value.primitiveValue() : null;
	}

	private static boolean getBooleanParameter(final Parameters parameters, final String name) {
		final var value = parameters.getParameterValue(name);
		return value != null && Boolean.parseBoolean(value.primitiveValue());
	}
}
