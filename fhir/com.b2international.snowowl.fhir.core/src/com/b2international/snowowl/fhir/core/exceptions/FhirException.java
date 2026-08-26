/*
 * Copyright 2011-2024 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.fhir.core.exceptions;

import java.util.List;

import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;
import org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent;

import com.b2international.commons.StringUtils;
import com.b2international.commons.exceptions.ApiException;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSortedSet;

/**
 * @since 6.3
 */
public class FhirException extends ApiException {

	private static final long serialVersionUID = 2L;
	
	private final IssueSeverity issueSeverity;
	private final IssueType issueType;
	private final org.hl7.fhir.r4.model.codesystems.OperationOutcome operationOutcomeCode;
	private final String location;
		
	public FhirException(String message, org.hl7.fhir.r4.model.codesystems.OperationOutcome operationOutcomeCode) {
		this(message, operationOutcomeCode, null);
	}
	
	public FhirException(String message, org.hl7.fhir.r4.model.codesystems.OperationOutcome operationOutcomeCode, String location) {
		this(IssueSeverity.ERROR, IssueType.EXCEPTION, message, operationOutcomeCode, location);
	}
	
	public FhirException(IssueSeverity issueSeverity, IssueType issueType, String message, org.hl7.fhir.r4.model.codesystems.OperationOutcome operationOutcomeCode) {
		this(issueSeverity, issueType, message, operationOutcomeCode, null);
	}
	
	public FhirException(IssueSeverity issueSeverity, IssueType issueType, String message, org.hl7.fhir.r4.model.codesystems.OperationOutcome operationOutcomeCode, String location) {
		super(message);
		this.issueSeverity = issueSeverity;
		this.issueType = issueType;
		this.operationOutcomeCode = operationOutcomeCode;
		this.location = location;
	}

	protected final OperationOutcomeIssueComponent buildIssue(IssueSeverity issueSeverity, IssueType issueType, String message, org.hl7.fhir.r4.model.codesystems.OperationOutcome operationOutcomeCode, String location) {
		return new OperationOutcome.OperationOutcomeIssueComponent()
			.setSeverity(issueSeverity)
			.setCode(issueType)
			.setDetails(toDetails(operationOutcomeCode, location))
			.setDiagnostics(message)
			.addLocation(location);
	}
	
	protected final OperationOutcomeIssueComponent buildIssue(IssueSeverity issueSeverity, IssueType issueType, String message) {
		return new OperationOutcome.OperationOutcomeIssueComponent()
			.setSeverity(issueSeverity)
			.setCode(issueType)
			.setDetails(toDetails(message))
			.setDiagnostics(message)
			.addLocation(location);
	}
	
	@Override
	protected Integer getStatus() {
		return 0;
	}
	
	/**
	 * @return the {@link IssueType} associated with this exception.
	 */
	public final IssueType getIssueType() {
		return issueType;
	}
	
	/**
	 * @return the {@link org.hl7.fhir.r4.model.codesystems.OperationOutcome} code associated with this exception.
	 */
	public final org.hl7.fhir.r4.model.codesystems.OperationOutcome getOperationOutcomeCode() {
		return operationOutcomeCode;
	}
	
	/**
	 * Creates an OperationOutcome representation from this exception. Useful when the exception must be propagated through protocols where Java serialization
	 * cannot be used (eg. HTTP), or the possible receiver cannot understand serialized Java class and object byte sequences.
	 * 
	 * @return {@link OperationOutcome} representation of this {@link FhirException}, never <code>null</code>.
	 */
	public final OperationOutcome toOperationOutcome() {
		
		var operationOutcome = new OperationOutcome();
		
		// attach this exception as issue
		operationOutcome.addIssue(buildIssue(issueSeverity, issueType, getMessage(), operationOutcomeCode, location));
		
		// attach additional info as issues separately
		if (getAdditionalInfo() != null) {
			for (String key : ImmutableSortedSet.copyOf(getAdditionalInfo().keySet())) {
				Object value = getAdditionalInfo().get(key);
				if (value instanceof String) {
					operationOutcome.addIssue(buildIssue(IssueSeverity.ERROR, IssueType.INFORMATIONAL, (String) value, operationOutcomeCode, null));
				}
			}
		}
		
		// attach any additional custom issues from subclasses
		getAdditionalIssues().forEach(operationOutcome::addIssue);
		
		return operationOutcome;
	}
	
	/**
	 * Creates a simple OperationOutcome representation which only contains an error message from this exception. Useful when the exception must be propagated through protocols where Java serialization
	 * cannot be used (eg. HTTP), or the possible receiver cannot understand serialized Java class and object byte sequences.
	 * 
	 * @return {@link OperationOutcome} representation of this {@link FhirException}, never <code>null</code>.
	 */
	public final OperationOutcome toSimpleOperationOutcome() {
		
		var operationOutcome = new OperationOutcome();
		
		// attach this exception as a simple issue
		operationOutcome.addIssue(buildIssue(issueSeverity, issueType, getMessage()));
		
		// attach additional info as issues separately
		if (getAdditionalInfo() != null) {
			for (String key : ImmutableSortedSet.copyOf(getAdditionalInfo().keySet())) {
				Object value = getAdditionalInfo().get(key);
				if (value instanceof String) {
					operationOutcome.addIssue(buildIssue(IssueSeverity.ERROR, IssueType.INFORMATIONAL, (String) value));
				}
			}
		}
		
		// attach any additional custom issues from subclasses
		getAdditionalIssues().forEach(operationOutcome::addIssue);
		
		return operationOutcome;
	}

	/**
	 * Subclasses may optionally provide {@link OperationOutcomeIssueComponent} instance that needs to be reported in the final {@link OperationOutcome} when built via {@link #toOperationOutcome()}.
	 */
	protected List<OperationOutcomeIssueComponent> getAdditionalIssues() {
		return List.of();
	}
	
	public static CodeableConcept toDetails(org.hl7.fhir.r4.model.codesystems.OperationOutcome operationOutcomeCode, String location) {
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
	public static String getDisplayWithoutPlaceholders(org.hl7.fhir.r4.model.codesystems.OperationOutcome operationOutcomeCode) {
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
			case MSGUNHANDLEDNODETYPE:      return "Unhandled xml node type";
			case MSGUNKNOWNCONTENT:         return "Unknown Content";
			case MSGUNKNOWNTYPE:            return "Resource Type not recognised";
			case MSGVERSIONAWARECONFLICT:   return "Update Conflict";
			case MSGWRONGNS:                return "This does not appear to be a FHIR element or resource (wrong namespace)";
			case SEARCHMULTIPLE:            return "Error: Multiple matches exist for search parameters";
			case SEARCHNONE:                return "Error: no processable search found for search parameters";
			default:                        return operationOutcomeCode.getDisplay();
		}
	}

	/**
	 * Returns a CodeableConcept with only the given message in the text field. This is used when an error message does not have to contain a full {@link Coding}
	 * 
	 * @param message
	 * @return {@link CodeableConcept} with the given message in the text field.
	 */
	public static CodeableConcept toDetails(String message) {
		return new CodeableConcept()
			.setText(message);
	}
}
