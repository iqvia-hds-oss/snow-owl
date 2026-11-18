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
package com.b2international.snowowl.fhir.core;

import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.ResourceType;

/**
 * @since 10.5.0
 */
public abstract class FhirExceptions {

	public static FhirException paramInvalid(String message) {
		return paramInvalid(message, null);
	}
	
	public static FhirException paramInvalid(String message, String location) {
		return FhirException.builder()
				.addIssue(OperationOutcome.IssueSeverity.ERROR, OperationOutcome.IssueType.INVALID, org.hl7.fhir.r4.model.codesystems.OperationOutcome.MSGPARAMINVALID, message, null)
				.build();
	}
	
	public static FhirException fhirResourceNotResolvable(ResourceType resourceType, CanonicalType canonicalType) {
		return noExist(String.format("A usable %s with URL '%s' could not be resolved.", resourceType.name(), canonicalType.getValueAsString()));
	}
	
	public static FhirException noExist(String message) {
		return FhirException.builder()
				.addIssue(OperationOutcome.IssueSeverity.ERROR, OperationOutcome.IssueType.NOTFOUND, org.hl7.fhir.r4.model.codesystems.OperationOutcome.MSGNOEXIST, message, null)
				.build();
	}
	
	public static FhirException badSyntax(String message) {
		return FhirException.builder()
				.addIssue(OperationOutcome.IssueSeverity.ERROR, OperationOutcome.IssueType.INVALID, org.hl7.fhir.r4.model.codesystems.OperationOutcome.MSGBADSYNTAX, message, null)
				.build();
	}

	public static FhirException unsupported(String message) {
		return FhirException.builder()
				.addIssue(OperationOutcome.IssueSeverity.ERROR, OperationOutcome.IssueType.NOTSUPPORTED, org.hl7.fhir.r4.model.codesystems.OperationOutcome.MSGPARAMINVALID, message, null)
				.build();
	}

	public static FhirException parsingError(String message) {
		return FhirException.builder()
				.addIssue(OperationOutcome.IssueSeverity.ERROR, OperationOutcome.IssueType.EXCEPTION, org.hl7.fhir.r4.model.codesystems.OperationOutcome.MSGERRORPARSING, message, null)
				.build();
	}

	public static FhirException authRequired(String message) {
		return FhirException.builder()
				.addIssue(OperationOutcome.IssueSeverity.ERROR, OperationOutcome.IssueType.LOGIN, org.hl7.fhir.r4.model.codesystems.OperationOutcome.MSGAUTHREQUIRED, message, null)
				.build();
	}

	public static FhirException cantParseContent(String message) {
		return processingError(message, org.hl7.fhir.r4.model.codesystems.OperationOutcome.MSGCANTPARSECONTENT);
	}
	
	public static FhirException processingError(String message, org.hl7.fhir.r4.model.codesystems.OperationOutcome operationOutcomeCode) {
		return FhirException.builder()
				.addIssue(OperationOutcome.IssueSeverity.ERROR, OperationOutcome.IssueType.PROCESSING, operationOutcomeCode, message, null)
				.build();
	}

}
