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
package com.b2international.snowowl.fhir.core.request.packages.r4;

import java.util.List;
import java.util.SortedSet;
import java.util.stream.StreamSupport;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;

import com.b2international.fhir.r4.operations.BaseParameters;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSortedSet;

/**
 * Parameters class for the $load-package operation in the FHIR R4 format.
 * 
 * @since 10.1.0
 */
public final class FhirLoadPackageParameters extends BaseParameters {

	private static final String PARAM_NAME = "name";
	private static final String PARAM_VERSION = "version";
	private static final String PARAM_REGISTRY = "registry";
	private static final String PARAM_RESOLVE_DEPENDENCIES = "resolveDependencies";
	private static final String PARAM_RESOURCE_URL = "resourceUrl";

	private static final SortedSet<String> ACCEPTED_PARAMETER_NAMES = ImmutableSortedSet.of(
		PARAM_NAME,
		PARAM_VERSION,
		PARAM_REGISTRY,
		PARAM_RESOLVE_DEPENDENCIES,
		PARAM_RESOURCE_URL
	);

	public FhirLoadPackageParameters() {
		this(new Parameters());
	}

	public FhirLoadPackageParameters(Parameters parameters) {
		super(parameters);
	}

	public StringType getName() {
		return (StringType) getParameterValue(PARAM_NAME, Parameters.ParametersParameterComponent::getValue);
	}

	public FhirLoadPackageParameters setName(String name) {
		return setName(new StringType(name));
	}

	public FhirLoadPackageParameters setName(StringType name) {
		addParameter(PARAM_NAME, name);
		return this;
	}

	public StringType getVersion() {
		return (StringType) getParameterValue(PARAM_VERSION, Parameters.ParametersParameterComponent::getValue);
	}

	public FhirLoadPackageParameters setVersion(String version) {
		return setVersion(new StringType(version));
	}

	public FhirLoadPackageParameters setVersion(StringType version) {
		addParameter(PARAM_VERSION, version);
		return this;
	}

	public StringType getRegistry() {
		return (StringType) getParameterValue(PARAM_REGISTRY, Parameters.ParametersParameterComponent::getValue);
	}

	public FhirLoadPackageParameters setRegistry(String registry) {
		return setRegistry(new StringType(registry));
	}

	public FhirLoadPackageParameters setRegistry(StringType registry) {
		addParameter(PARAM_REGISTRY, registry);
		return this;
	}

	public BooleanType getResolveDependencies() {
		return (BooleanType) getParameterValue(PARAM_RESOLVE_DEPENDENCIES, Parameters.ParametersParameterComponent::getValue);
	}

	public FhirLoadPackageParameters setResolveDependencies(Boolean resolveDependencies) {
		return setResolveDependencies(new BooleanType(resolveDependencies));
	}

	public FhirLoadPackageParameters setResolveDependencies(BooleanType resolveDependencies) {
		addParameter(PARAM_RESOLVE_DEPENDENCIES, resolveDependencies);
		return this;
	}
	
	public List<UriType> getResourceUrl() {
		return getParameters(PARAM_RESOURCE_URL).stream().map(Parameters.ParametersParameterComponent::getValue).map(UriType.class::cast).toList();
	}
	
	public FhirLoadPackageParameters setResourceUrl(Iterable<String> resourceUrl) {
		return resourceUrl == null ? this : setResourceUrl(StreamSupport.stream(resourceUrl.spliterator(), false).map(UriType::new).toList());
	}
	
	public FhirLoadPackageParameters setResourceUrl(List<UriType> resourceUrl) {
		if (resourceUrl != null) {
			resourceUrl.forEach(url -> addParameter(PARAM_RESOURCE_URL, url));
		}
		return this;
	}

	public FhirLoadPackageParameters setResourceUrl(BooleanType resolveDependencies) {
		addParameter(PARAM_RESOLVE_DEPENDENCIES, resolveDependencies);
		return this;
	}

	@Override
	protected SortedSet<String> getAcceptedParameterNames() {
		return ACCEPTED_PARAMETER_NAMES;
	}
	
	/**
	 * @return whether this parameters object has a registry value set or not.
	 */
	public boolean hasRegistryValue() {
		return getRegistry() != null && !getRegistry().isEmpty();
	}

	/**
	 * @return whether this parameters object has both the package name and version set, returns <code>false</code> if any of the two or both are unset. 
	 */
	public boolean hasPackageInfo() {
		return getName() != null && !getName().isEmpty() && getVersion() != null && !getVersion().isEmpty();
	}
	
	/**
	 * @return the package info in the form of name@version.
	 */
	public String extractPackageInfo() {
		Preconditions.checkArgument(hasPackageInfo(), "No package info available in this parameters object");
		return String.join("@", getName().getValue(), getVersion().getValue());
	}

}