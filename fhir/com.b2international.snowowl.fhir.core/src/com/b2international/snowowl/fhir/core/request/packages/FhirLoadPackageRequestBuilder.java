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
package com.b2international.snowowl.fhir.core.request.packages;

import java.time.LocalDate;

import com.b2international.commons.StringUtils;
import com.b2international.fhir.r5.operations.LoadPackageParameters;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.attachments.Attachment;
import com.b2international.snowowl.core.domain.IComponent;
import com.b2international.snowowl.core.events.BaseRequestBuilder;
import com.b2international.snowowl.core.events.Request;
import com.b2international.snowowl.core.request.SystemRequestBuilder;

/**
 * Builder for FHIR package loading requests.
 * 
 * @since 10.1.0
 */
public final class FhirLoadPackageRequestBuilder
		extends BaseRequestBuilder<FhirLoadPackageRequestBuilder, ServiceProvider, FhirLoadPackageResultParameters>
		implements SystemRequestBuilder<FhirLoadPackageResultParameters> {

	public static final String DEFAULT_PACKAGE_REGISTRY = "https://packages.fhir.org";
	
	private Attachment packageToLoad;
	
	private String author;
	private String owner;
	private String ownerProfileName;
	private LocalDate defaultEffectiveDate;
	private String bundleId = IComponent.ROOT_ID;
	
	private LoadPackageParameters parameters;
	
	public FhirLoadPackageRequestBuilder setPackageToLoad(Attachment packageToLoad) {
		this.packageToLoad = packageToLoad;
		return getSelf();
	}
	
	public FhirLoadPackageRequestBuilder setAuthor(String author) {
		this.author = author;
		return this;
	}
	
	public FhirLoadPackageRequestBuilder setOwner(final String owner) {
		this.owner = owner;
		return this;
	}
	
	public FhirLoadPackageRequestBuilder setOwnerProfileName(final String ownerProfileName) {
		this.ownerProfileName = ownerProfileName;
		return this;
	}
	
	public FhirLoadPackageRequestBuilder setDefaultEffectiveDate(final LocalDate defaultEffectiveDate) {
		this.defaultEffectiveDate = defaultEffectiveDate;
		return this;
	}

	public FhirLoadPackageRequestBuilder setBundleId(final String bundleId) {
		if (!StringUtils.isEmpty(bundleId)) {
			this.bundleId = bundleId;
		} else {
			this.bundleId = IComponent.ROOT_ID;
		}
		return this;
	}
	
	public FhirLoadPackageRequestBuilder setParameters(LoadPackageParameters parameters) {
		this.parameters = parameters;
		return getSelf();
	}
	
	@Override
	protected Request<ServiceProvider, FhirLoadPackageResultParameters> doBuild() {
		FhirLoadPackageRequest req = new FhirLoadPackageRequest(author, owner, ownerProfileName, defaultEffectiveDate, bundleId);
		
		// if package registry is not defined, set it to default
		if (!parameters.hasRegistryValue()) {
			parameters.setRegistry(DEFAULT_PACKAGE_REGISTRY);
		}
		
		req.setPackageToLoad(packageToLoad);
		req.setParameters(parameters);
		return req;
	}

}