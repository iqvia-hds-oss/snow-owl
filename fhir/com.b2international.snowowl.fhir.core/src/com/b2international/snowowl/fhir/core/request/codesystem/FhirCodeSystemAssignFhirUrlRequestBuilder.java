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
package com.b2international.snowowl.fhir.core.request.codesystem;

import java.util.List;

import com.b2international.snowowl.core.context.ResourceRepositoryTransactionRequestBuilder;
import com.b2international.snowowl.core.domain.TransactionContext;
import com.b2international.snowowl.core.events.BaseRequestBuilder;
import com.b2international.snowowl.core.events.Request;

/**
 * @since 10.2.0
 */
public final class FhirCodeSystemAssignFhirUrlRequestBuilder
	extends BaseRequestBuilder<FhirCodeSystemAssignFhirUrlRequestBuilder, TransactionContext, Boolean>
	implements ResourceRepositoryTransactionRequestBuilder<Boolean> {

	private List<String> codeSystemIds;
	private String fhirUrl;
	private String fhirVersionProperty;

	public FhirCodeSystemAssignFhirUrlRequestBuilder setCodeSystemId(final String codeSystemId) {
		this.codeSystemIds = List.of(codeSystemId);
		return getSelf();
	}

	public FhirCodeSystemAssignFhirUrlRequestBuilder setCodeSystemIds(final List<String> codeSystemIds) {
		this.codeSystemIds = codeSystemIds;
		return getSelf();
	}

	public FhirCodeSystemAssignFhirUrlRequestBuilder setFhirUrl(final String fhirUrl) {
		this.fhirUrl = fhirUrl;
		return getSelf();
	}

	public FhirCodeSystemAssignFhirUrlRequestBuilder setFhirVersionProperty(final String fhirVersionProperty) {
		this.fhirVersionProperty = fhirVersionProperty;
		return getSelf();
	}

	@Override
	protected Request<TransactionContext, Boolean> doBuild() {
		return new FhirCodeSystemAssignFhirUrlRequest(codeSystemIds, fhirUrl, fhirVersionProperty);
	}
}
