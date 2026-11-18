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
import com.b2international.commons.StringUtils;
import com.b2international.commons.exceptions.ApiException;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

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
		public Builder addIssue(OperationOutcome.IssueSeverity issueSeverity, OperationOutcome.IssueType issueType, org.hl7.fhir.r4.model.codesystems.OperationOutcome operationOutcomeCode, String message, String location) {
			if (issues == null) {
				issues = new ArrayList<>(2);
			}
			
			var details = operationOutcomeCode == null ? new CodeableConcept().setText(message) : toDetails(operationOutcomeCode, location);
			
			this.issues.add(new OperationOutcome.OperationOutcomeIssueComponent()
					.setCode(issueType)
					.setSeverity(issueSeverity)
					.setDetails(details)
					.setDiagnostics(message)
					.addLocation(location));
			
			return this;
		}
		
	}
	
	public static FhirException.Builder builder() {
		return new FhirException.Builder();
	}
	
	private static CodeableConcept toDetails(org.hl7.fhir.r4.model.codesystems.OperationOutcome operationOutcomeCode, String location) {
		String operationOutcomeCodeDisplay = operationOutcomeCode.getDisplay();
		
		// A single placeholder will split the original input in two, so we need to subtract 1 from the count
		final long placeholderCount = Splitter.on("%s")
			.limit(3)
			.splitToStream(operationOutcomeCodeDisplay)
			.count() - 1L;
		
		if (placeholderCount == 1 && !StringUtils.isEmpty(location)) {
			operationOutcomeCodeDisplay = String.format(operationOutcomeCodeDisplay, location);
		} else {
			operationOutcomeCodeDisplay = getDisplayWithoutPlaceholders(operationOutcomeCode);
		}
		
		return new CodeableConcept()
			.addCoding(new Coding()
				.setCode(operationOutcomeCode.toCode())
				.setSystem(operationOutcomeCode.getSystem())
				// XXX: The display for the outcome code should _not_ be interpolated, as that is not the original label
				// .setDisplay(operationOutcomeCodeDisplay) 
			)
			.setText(operationOutcomeCodeDisplay);
	}
	
	/**
	 * Returns a display string for the given {@link org.hl7.fhir.r4.model.codesystems.OperationOutcome} 
	 * code with any <code>%s</code> placeholders removed, producing correct English for those literals
	 * that originally contained format arguments. For the rest of the codes the original display is 
	 * preserved.
	 *
	 * @param operationOutcomeCode the code whose placeholder-free display is requested
	 * @return a display string without placeholders, never <code>null</code>
	 */
	private static String getDisplayWithoutPlaceholders(org.hl7.fhir.r4.model.codesystems.OperationOutcome operationOutcomeCode) {
		switch (operationOutcomeCode) {
			case MSGBADFORMAT:              return "Bad Format";
			case MSGBADSYNTAX:              return "Bad Syntax";
			case MSGCANTPARSECONTENT:       return "Unable to parse content type of feed";
			case MSGCANTPARSEROOT:          return "Unable to parse element root of feed";
			case MSGDATEFORMAT:             return "The Date value is not in the correct format (XML Date Format required)";
			case MSGDELETEDID:              return "The resource has been deleted";
			case MSGDUPLICATEID:            return "Duplicate ID for resource type";
			case MSGERRORPARSING:           return "Error parsing resource XML";
			case MSGIDINVALID:              return "ID has an invalid character";
			case MSGIDTOOLONG:              return "ID is too long (length limit 36)";
			case MSGLOCALFAIL:              return "Unable to resolve local reference to resource";
			case MSGNOEXIST:                return "Resource ID does not exist";
			case MSGNOMATCH:                return "No Resource found matching the query";
			case MSGNOMODULE:               return "No module could be found to handle the request";
			case MSGOPNOTALLOWED:           return "Operation not allowed for resource (due to local configuration)";
			case MSGPARAMCHAINED:           return "Unknown chained parameter name";
			case MSGPARAMINVALID:           return "Parameter content is invalid";
			case MSGPARAMMODIFIERINVALID:   return "Parameter modifier is invalid";
			case MSGPARAMNOREPEAT:          return "Parameter is not allowed to repeat";
			case MSGPARAMUNKNOWN:           return "Parameter not understood";
			case MSGSORTUNKNOWN:            return "Unknown sort parameter name";
			case MSGTRANSACTIONDUPLICATEID: return "Duplicate Identifier in transaction";
			case MSGUNHANDLEDNODETYPE:      return "Unhandled XML node type";
			case MSGUNKNOWNCONTENT:         return "Unknown Content";
			case MSGUNKNOWNTYPE:            return "Resource Type not recognised";
			case MSGVERSIONAWARECONFLICT:   return "Update Conflict";
			case MSGWRONGNS:                return "This does not appear to be a FHIR element or resource (wrong namespace)";
			case SEARCHMULTIPLE:            return "Error: Multiple matches exist for search parameters";
			case SEARCHNONE:                return "Error: no processable search found for search parameters";
			default:                        return operationOutcomeCode.getDisplay();
		}
	}

}
