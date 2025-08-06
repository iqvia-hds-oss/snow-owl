/*
 * Copyright 2020-2025 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.snomed.core.rest.domain;

import java.util.Collection;
import java.util.List;

import com.b2international.snowowl.snomed.core.domain.Rf2ReleaseType;
import com.b2international.snowowl.snomed.core.domain.SnomedConcept;
import com.b2international.snowowl.snomed.core.domain.SnomedDescription;
import com.b2international.snowowl.snomed.core.domain.SnomedRelationship;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @since 7.5
 */
public final class SnomedRf2ExportConfiguration {

	/**
	 * Holds constants corresponding to configuration property names used in the enclosing class.
	 */
	public static final class Fields {
		
		// suppress instantiation
		private Fields() { }
		
		public static final String TYPE = "type";
		public static final String NAMESPACE_ID = "namespaceId";
		public static final String COUNTRY_NAMESPACE_ELEMENT = "countryNamespaceElement";
		public static final String MODULE_IDS = "moduleIds";
		public static final String REF_SET_IDS = "refSetIds";
		public static final String START_EFFECTIVE_TIME = "startEffectiveTime";
		public static final String END_EFFECTIVE_TIME = "endEffectiveTime";
		public static final String TRANSIENT_EFFECTIVE_TIME = "transientEffectiveTime";
		public static final String INCLUDE_UNPUBLISHED = "includeUnpublished";
		public static final String EXTENSION_ONLY = "extensionOnly";
		public static final String REFSET_LAYOUT = "refSetLayout";
		public static final String NRC_COUNTRY_CODE = "nrcCountryCode";
		public static final String MAINTAINER_TYPE = "maintainerType";
		public static final String COMPONENT_TYPES = "componentTypes";
	}
	
	@Parameter(description = "The expected RF2 release type", schema = @Schema(allowableValues = { "full", "snapshot", "delta" }, defaultValue = "snapshot"))
	private String type = Rf2ReleaseType.SNAPSHOT.name();
	
	@Parameter(description = "The namespaceId to use in the release archive name. Deprecated, use 'countryNamespaceElement' instead.", deprecated = true)
	private String namespaceId = "";
	
	@Parameter(description = "The country-namespace element to use in the release archive name")
	private String countryNamespaceElement = "";
	
	@Parameter(description = "Optional moduleIds to restrict the exported content")
	private Collection<String> moduleIds;
	
	@Parameter(description = "Optional refSetIds to restrict the export content")
	private Collection<String> refSetIds;
	
	@Parameter(description = "Delta export start effectiveTime. By default unbounded.")
	private String startEffectiveTime;
	
	@Parameter(description = "Delta export end effectiveTime. By default unbounded.")
	private String endEffectiveTime;
	
	@Parameter(description = "Transient effective time to apply on unpublished content")
	private String transientEffectiveTime;
	
	@Parameter(description = "To include unreleased changes in the export result")
	private boolean includeUnpublished = true;
	
	@Parameter(description = "To export the content of the SNOMED CT Extension only or all dependencies as well forming an Edition Release.")
	private boolean extensionOnly = false;
	
	@Parameter(description = "The RF2 reference set file layout to use. Defaults to the given SNOMED CT code system's 'refSetExportLayout' setting.", schema = @Schema(allowableValues = { "combined", "individual" }, defaultValue = "combined"))
	private String refSetLayout;
	
	@Parameter(description = "The NRC country code to use in the release archive name")
	private String nrcCountryCode = "";
	
	@Parameter(description = "The maintainer type to use in the release archive name")
	private String maintainerType = "";
	
	@Parameter(description = "The component types to export. By default everything is exported.", schema = @Schema(allowableValues = { SnomedConcept.TYPE, SnomedDescription.TYPE, SnomedRelationship.TYPE, SnomedConcept.REFSET_TYPE }))
	private List<String> componentTypes;
	
	/**
	 * Returns with the RF2 release type of the current export configuration.
	 * @return the desired RF2 release type.
	 */
	public String getType() {
		return type;
	}
	
	public void setType(String type) {
		this.type = type;
	}
	
	/**
	 * Returns with a restricting export start effective time. Can be {@code null}.
	 */
	public String getStartEffectiveTime() {
		return startEffectiveTime;
	}
	
	public void setStartEffectiveTime(String startEffectiveTime) {
		this.startEffectiveTime = startEffectiveTime;
	}

	/**
	 * Returns with a restricting export end effective time.May return with {@code null}.
	 */
	public String getEndEffectiveTime() {
		return endEffectiveTime;
	}
	
	public void setEndEffectiveTime(String endEffectiveTime) {
		this.endEffectiveTime = endEffectiveTime;
	}
	
	/**
	 * @deprecated Use {@link #getCountryNamespaceElement()} instead.
	 * @return the country-namespace element to use in the release archive name
	 */
	@Deprecated
	public String getNamespaceId() {
		/*
		 * XXX: The naming of this field is a bit misleading because when it is set it
		 * will override the entire country-namespace portion of the file name!
		 */
		return namespaceId;
	}

	@Deprecated
	public void setNamespaceId(String namespaceId) {
		this.namespaceId = namespaceId;
	}
	
	/**
	 * Returns the country-namespace element to use in the release archive name.
	 * <p>
	 * This element will be used to form the country-namespace portion of the file name
	 * of the RF2 release archive. The country-namespace element is expected to be a
	 * two-letter country code, such as "NL" or "BE" and optionally followed by a
	 * 7-digit namespace, eg. "DK1000005".
	 * <p>
	 * If the country-namespace element is not set, the export will use the
	 * maintainer type and NRC country code values to form the country-namespace
	 * portion of the file name (either from this export configuration or from 
	 * code system settings).
	 * 
	 * @return the country-namespace element to use in the release archive name
	 */
	public String getCountryNamespaceElement() {
		return countryNamespaceElement;
	}
	
	public void setCountryNamespaceElement(String countryNamespaceElement) {
		this.countryNamespaceElement = countryNamespaceElement;
	}
	
	/**
	 * Returns with a collection of SNOMED&nbsp;CT module concept IDs.
	 * <p>This collection of module IDs will define which components will be included in the export.
	 * Components having a module that is not included in the returning set will be excluded from 
	 * the export result.
	 * @return a collection of module dependency IDs.
	 */
	public Collection<String> getModuleIds() {
		return moduleIds;
	}
	
	public void setModuleIds(Collection<String> moduleIds) {
		this.moduleIds = moduleIds;
	}
	
	/**
	 * Returns with a collection of SNOMED&nbsp;CT refset concept IDs.
	 * <p>This collection of refset IDs will define which refsets and their members will be included in the export.
	 * Refsets that are not included in the returning set will be excluded from the export result.
	 * 
	 * @return a collection of refset IDs.
	 */
	public Collection<String> getRefSetIds() {
		return refSetIds;
	}
	
	public void setRefSetIds(Collection<String> refSetIds) {
		this.refSetIds = refSetIds;
	}
	
	/**
	 * Returns the transient effective time to use for unpublished components.
	 * 
	 * @return the transient effective time, or {@code null} if the default {@code UNPUBLISHED} value should be printed
	 * for unpublished components
	 */
	public String getTransientEffectiveTime() {
		return transientEffectiveTime;
	}

	public void setTransientEffectiveTime(String transientEffectiveTime) {
		this.transientEffectiveTime = transientEffectiveTime;
	}
	
	/**
	 * Sets whether unpublished components should be exported
	 * @param includeUnpublished
	 */
	public void setIncludeUnpublished(boolean includeUnpublished) {
		this.includeUnpublished = includeUnpublished;
	}
	
	/**
	 * Returns if unpublished components should be exported 
	 * @return
	 */
	public boolean isIncludeUnpublished() {
		return includeUnpublished;
	}
	
	/**
	 * If set to true only the code system specified by it's short name will be exported. If set to false all versions from parent code systems
	 * will be collected and exported.
	 * 
	 * @param extensionOnly the extensionOnly to set
	 */
	public void setExtensionOnly(boolean extensionOnly) {
		this.extensionOnly = extensionOnly;
	}
	
	/**
	 * Returns true if only the code system specified by it's short name should be exported. If set to false all versions from parent code systems
	 * will be collected and exported.
	 * 
	 * @return the extensionOnly
	 */
	public boolean isExtensionOnly() {
		return extensionOnly;
	}
	
	public String getRefSetLayout() {
		return refSetLayout;
	}
	
	public void setRefSetLayout(String refSetLayout) {
		this.refSetLayout = refSetLayout;
	}
	
	public String getMaintainerType() {
		return maintainerType;
	}
	
	public void setMaintainerType(String maintainerType) {
		this.maintainerType = maintainerType;
	}
	
	public String getNrcCountryCode() {
		return nrcCountryCode;
	}
	
	public void setNrcCountryCode(String nrcCountryCode) {
		this.nrcCountryCode = nrcCountryCode;
	}
	
	public List<String> getComponentTypes() {
		return componentTypes;
	}
	
	public void setComponentTypes(List<String> componentTypes) {
		this.componentTypes = componentTypes;
	}
}
