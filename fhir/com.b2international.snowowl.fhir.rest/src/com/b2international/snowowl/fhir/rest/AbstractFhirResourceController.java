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

import java.net.URI;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;
import org.hl7.fhir.r5.model.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

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
			final URI locationUri = MvcUriComponentsBuilder.fromController(getClass())
				.pathSegment(result.getId())
				.build()
				.toUri();
			
		return toResponseEntity(
				successStatus,
				Map.of(HttpHeaders.LOCATION, locationUri.toString()),
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
	
	
	private static final Pattern SNOMED_URL_VERSION = Pattern.compile("/version/(?<year>\\d{4})(?<month>\\d{2})(?<day>\\d{2})$");
	
	
	protected Map<String, String> expandVersion(CanonicalResource resource) {
		// XXX: in case of SNOMED url based version this will be incorrect!
		String version = resource.getVersion();
		Matcher matcher = SNOMED_URL_VERSION.matcher(version);
		if (matcher.find()) {
			version = String.format("%s-%s-%s", matcher.group("year"), matcher.group("month"), matcher.group("day"));
		}
		return Map.of("version", version);
	}
}
