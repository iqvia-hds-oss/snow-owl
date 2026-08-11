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

import java.util.Map;

import org.elasticsearch.common.Strings;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;
import org.hl7.fhir.r5.model.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import com.b2international.snowowl.core.ResourceFragment;
import com.b2international.snowowl.fhir.core.R5ObjectFields;
import com.b2international.snowowl.fhir.core.request.FhirResourceUpdateResult;

/**
 * @since 10.2.0
 */
public abstract class AbstractFhirResourceController extends AbstractFhirController {
	protected ResponseEntity<byte[]> toResponseEntity(
			final FhirResourceUpdateResult result, 
			final HttpStatus successStatus,
			final String accept, 
			final String _format,
			final Boolean _pretty
	) {
		if (result.isCreated() || result.isUpdated()) {
			return toResponseEntity(
					successStatus,
					Map.of(HttpHeaders.LOCATION, toLocation(result.getId(), result.getVersion())),
					asSuccessOperationOutcome(result),
					accept,
					_format,
					_pretty
				);
		} else {
			return toResponseEntity(
				HttpStatus.BAD_REQUEST,
				asSkippedOperationOutcome(result),
				accept,
				_format,
				_pretty
			);
		}
	}
	
	private String toLocation(String id, String version) {
		UriComponentsBuilder builder = MvcUriComponentsBuilder.fromController(getClass())
				.pathSegment(id);

		if (!Strings.isNullOrEmpty(version)) {
			builder
				.pathSegment("_history")
				.pathSegment(version);
		}
		return builder.build().toUri().toString();
	}
	
	private Resource asSuccessOperationOutcome(FhirResourceUpdateResult result) {
		return new OperationOutcome()
			.addIssue(
				new OperationOutcome.OperationOutcomeIssueComponent()
					.setSeverity(IssueSeverity.SUCCESS)
					.setCode(IssueType.SUCCESS)
					.setDiagnostics(result.getMessage())
			);
	}
	
	private Resource asSkippedOperationOutcome(FhirResourceUpdateResult result) {
		return new OperationOutcome()
			.addIssue(
				new OperationOutcome.OperationOutcomeIssueComponent()
					.setSeverity(IssueSeverity.ERROR)
					.setCode(IssueType.BUSINESSRULE)
					.setDiagnostics(result.getMessage())
			);
	}
	
	protected Map<String, String> expandVersion(CanonicalResource resource) {
		String version = resource.getVersion();
		
		// SNOMED version can return overwrite the real version, so look for user data containing original version
		if (resource.hasUserData(R5ObjectFields.MetadataResource.UserData.INTERNAL_RESOURCE)) {
			Object userData = resource.getUserData(R5ObjectFields.MetadataResource.UserData.INTERNAL_RESOURCE);
			if (userData instanceof ResourceFragment fragment) {
				version = fragment.getVersion();
			}
		}
		
		return Map.of("version", version);
	}
}
