/*
 * Copyright 2011-2026 B2i Healthcare, https://b2ihealthcare.com
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.OperationOutcome;

import com.b2international.commons.CompareUtils;
import com.b2international.commons.exceptions.ApiException;
import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;

/**
 * Sealed sole Exception implementation responsible for delivering {@link OperationOutcome} responses from internal request implementations to
 * controllers and API callers.
 * 
 * Use the {@link #builder()} method to initiate the construction of this exception and specify your issues as needed. At least one FHIR specific
 * issues is required to complete the creation of an instance.
 * 
 * For well-known issues and errors, see and use the available static factory methods from {@link FhirExceptions}.
 * 
 * @since 6.3
 * @see FhirExceptions
 */
public final class FhirException extends ApiException {

	private static final long serialVersionUID = 3L;

	private final OperationOutcome operationOutcome;
	
	FhirException(OperationOutcome operationOutcome) {
		Preconditions.checkArgument(operationOutcome.hasIssue(), "At least one OperationOutcome.issue is required");
		this.operationOutcome = operationOutcome;
		super(Optional.ofNullable(operationOutcome.getIssueFirstRep().getText()).orElse(operationOutcome.getIssueFirstRep().getDiagnostics()));
	}
	
	public OperationOutcome getOperationOutcome() {
		return operationOutcome;
	}
	
	@Override
	protected Integer getStatus() {
		return 0;
	}
	
	/**
	 * @since 10.5.0
	 */
	public static final class Builder {

		private List<OperationOutcome.OperationOutcomeIssueComponent> issues;
		
		/**
		 * Creates an {@link OperationOutcome} from the currently registered issues and construct a {@link FhirException} to deliver it to callers when an error occurs.
		 * @return
		 */
		public FhirException build() {
			final OperationOutcome operationOutcome = new OperationOutcome();
			
			if (!CompareUtils.isEmpty(issues)) {
				operationOutcome.setIssue(issues);
			}
			
			return new FhirException(operationOutcome);
		}
		
		/**
		 * Register an issue to the underlying {@link OperationOutcome} instance.
		 * 
		 * @param issueSeverity
		 * @param issueType
		 * @param operationOutcomeCode
		 * @param message
		 * @param location
		 * @return
		 */
		public Builder addIssue(OperationOutcome.IssueSeverity issueSeverity, OperationOutcome.IssueType issueType, TerminologyIssueType txIssueType, String message, String location) {
			if (issues == null) {
				issues = new ArrayList<>(2);
			}
			
			var details = new CodeableConcept()
					.setText(message);
			
			if (txIssueType != null) {
				details.addCoding(new Coding()
					.setCode(txIssueType.getCode())
					.setSystem(txIssueType.getSystem())
				);
			}
			
			this.issues.add(new OperationOutcome.OperationOutcomeIssueComponent()
					.setCode(issueType)
					.setSeverity(issueSeverity)
					.setDetails(details)
					// XXX keeping this as commented out statement
					// diagnostics should not be returned otherwise the FHIR Validator reports an error
					// details.text should contain the error message in all cases
					// .setDiagnostics(message)
					.addLocation(location));
			
			return this;
		}
		
	}
	
	public static FhirException.Builder builder() {
		return new FhirException.Builder();
	}
	
	/**
	 * Official tx specific issue types to use for OperationOutcome.issue.details.
	 * 
	 * @since 10.5.0
	 */
	public enum TerminologyIssueType {
		NOT_FOUND("Not Found", "An issue that identifies a code system or value set that was not found validating the code(s)"),
		NOT_IN_VS("Not in ValueSet", "An issue that represents the value set validation failure"),
		THIS_CODE_NOT_IN_VS("This code not in valueset", "An issue that indicates that a particular code as not in the value set (but others might be)"),
		INVALID_DATA("Invalid Data", "An issue that indicates that the code/Coding/CodeableConcept provided was invalid"),
		INVALID_CODE("Invalid Code", "An issue that indicates that a provided code is not valid in a code system"),
		CODE_COMMENT("Code Comment", "An issue that provides some hints or warnings about the use of the code, but does not indicate that is invalid"),
		INVALID_DISPLAY("Invalid Display", "An issue relating to the display provided for a code"),
		DISPLAY_COMMENT("Display Comment", "An issue that provides some hints or warnings about the use of the display, but does not indicate that is invalid"),
		CANNOT_INFER("Cannot Infer System", "An issue indicating why inferring the code system failed (for type=code)"),
		CODE_RULE("Code Rule", "An issue indicating that a valid code was not valid in this context"),
		VS_INVALID("ValueSet Invalid", "An issue relating to a problem processing a value set while validating"),
		STATUS_CHECK("Status Check", "An issue pertaining to the status of the underlying resources");
		
		private final String code;
		private final String display;
		private final String definition;

		TerminologyIssueType(String display, String definition) {
			this.code = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, name());
			this.display = display;
			this.definition = definition;
		}
		
		public String getCode() {
			return code;
		}
		
		public String getDefinition() {
			return definition;
		}
		
		public String getDisplay() {
			return display;
		}
		
		public String getSystem() {
			return "http://hl7.org/fhir/tools/CodeSystem/tx-issue-type";
		}
		
	}
	
}
