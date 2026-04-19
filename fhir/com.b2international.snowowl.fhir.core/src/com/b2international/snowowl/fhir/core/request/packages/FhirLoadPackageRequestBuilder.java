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

import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.attachments.Attachment;
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
	
	private FhirLoadPackageParameters parameters;
	private Attachment packageToLoad;
	
	public FhirLoadPackageRequestBuilder setParameters(FhirLoadPackageParameters parameters) {
		this.parameters = parameters;
		return getSelf();
	}
	
	public FhirLoadPackageRequestBuilder setPackageToLoad(Attachment packageToLoad) {
		this.packageToLoad = packageToLoad;
		return getSelf();
	}
	
	@Override
	protected Request<ServiceProvider, FhirLoadPackageResultParameters> doBuild() {
		FhirLoadPackageRequest req = new FhirLoadPackageRequest();
		
		// if package registry is not defined, set it to default
		if (!parameters.hasRegistryValue()) {
			parameters.setRegistry(DEFAULT_PACKAGE_REGISTRY);
		}
		
		req.setParameters(parameters);
		req.setPackageToLoad(packageToLoad);
		return req;
	}

}