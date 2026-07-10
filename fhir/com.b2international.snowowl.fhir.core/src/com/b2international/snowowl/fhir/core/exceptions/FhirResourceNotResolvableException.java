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
package com.b2international.snowowl.fhir.core.exceptions;

import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.ResourceType;


/**
 * Exception thrown when a FHIR resource referenced by a canonical URL cannot be resolved.
 * <p>
 * The exception is intended for cases where a request refers to a canonical resource,
 * such as a {@link ResourceType#CodeSystem}, {@link ResourceType#ValueSet}, or
 * {@link ResourceType#ConceptMap}, but no usable matching resource can be found.
 * </p>
 * <p>
 * The reported URL is taken from the supplied {@link CanonicalType}, with its
 * version component when present.
 * </p>
 *
 * @since 10.3.0
 */
public class FhirResourceNotResolvableException extends FhirException {
	
	private static final long serialVersionUID = 3L;
	
	public FhirResourceNotResolvableException(ResourceType resource, CanonicalType canonicalType) {
		
		final String message = String.format("A usable %s with URL '%s' could not be resolved.", resource.name(), canonicalType.getValueAsString());
		
		super(message, org.hl7.fhir.r4.model.codesystems.OperationOutcome.MSGNOEXIST, null);
	}
	
}
