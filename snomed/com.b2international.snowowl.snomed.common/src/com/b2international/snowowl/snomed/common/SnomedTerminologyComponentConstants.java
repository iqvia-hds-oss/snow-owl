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
package com.b2international.snowowl.snomed.common;

import static com.google.common.base.Preconditions.checkState;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts;
import com.google.common.base.Strings;

/**
 * Contains utility methods and constants related to SNOMED CT terminology metadata and components.
 */
public abstract class SnomedTerminologyComponentConstants {
	
	// suppress instantiation
	private SnomedTerminologyComponentConstants() {}

	public static final String TOOLING_ID = "snomed";

	/**
	 * Pattern used to extract the namespace from a namespace concept's FSN which has the following format:<br>
	 * <code>"Extension Namespace <b>{7 digit number}</b> (namespace concept)"</code> (leading and trailing 
	 * parts may vary)
	 */
	public static final String DEFAULT_NAMESPACE_PATTERN = ".*\\{(\\d{7})\\}.*";
	
	public static final String DEFAULT_NAMESPACE_AND_MODULE_ASSIGNER = "default";

	// configuration keys used in CodeSystem settings of SNOMED CT resources
	public static final class Settings {
		
		// suppress instantiation
		private Settings() { }
		
		public static final String MODULE_IDS = "moduleIds";
		public static final String NAMESPACE = "namespace";
		public static final String NAMESPACE_CONCEPT_ID = "namespaceConceptId";
		public static final String DESCRIPTION_COPY_POLICY = "descriptionCopyPolicy";
		public static final String MAINTAINER_TYPE = "maintainerType";
		public static final String RF2_EXPORT_LAYOUT = "refSetExportLayout";
		public static final String NRC_COUNTRY_CODE = "nrcCountryCode";
		public static final String LANGUAGES = "languages";
		public static final String NAMESPACE_MODULE_ASSIGNER = "namespaceModuleAssigner";
		public static final String REASONER_EXCLUDED_MODULE_IDS = "reasonerExcludedModuleIds";
		public static final String ALLOWED_OBJECT_ATTRIBUTE_EXPRESSION = "allowedObjectAttributesExpression";
		public static final String ALLOWED_DATA_ATTRIBUTE_EXPRESSION = "allowedDataAttributesExpression";
	}
	
	/**
	 * @deprecated Use {@link Settings#MODULE_IDS} instead.
	 */
	@Deprecated
	public static final String CODESYSTEM_MODULES_CONFIG_KEY = Settings.MODULE_IDS;
	
	/**
	 * @deprecated Use {@link Settings#NAMESPACE} instead.
	 */
	@Deprecated
	public static final String CODESYSTEM_NAMESPACE_CONFIG_KEY = Settings.NAMESPACE;
	
	/**
	 * @deprecated Use {@link Settings#DESCRIPTION_COPY_POLICY} instead.
	 */
	@Deprecated
	public static final String CODESYSTEM_DESCRIPTION_COPY_POLICY_CONFIG_KEY = Settings.DESCRIPTION_COPY_POLICY;
	
	/**
	 * @deprecated Use {@link Settings#MAINTAINER_TYPE} instead.
	 */
	@Deprecated
	public static final String CODESYSTEM_MAINTAINER_TYPE_CONFIG_KEY = Settings.MAINTAINER_TYPE;
	
	/**
	 * @deprecated Use {@link Settings#RF2_EXPORT_LAYOUT} instead.
	 */
	@Deprecated
	public static final String CODESYSTEM_RF2_EXPORT_LAYOUT_CONFIG_KEY = Settings.RF2_EXPORT_LAYOUT;
	
	/**
	 * @deprecated Use {@link Settings#NRC_COUNTRY_CODE} instead.
	 */
	@Deprecated
	public static final String CODESYSTEM_NRC_COUNTRY_CODE_CONFIG_KEY = Settings.NRC_COUNTRY_CODE;
	
	/**
	 * @deprecated Use {@link Settings#LANGUAGES} instead.
	 */
	@Deprecated
	public static final String CODESYSTEM_LANGUAGE_CONFIG_KEY = Settings.LANGUAGES;
	
	/**
	 * @deprecated Use {@link Settings#NAMESPACE_MODULE_ASSIGNER} instead.
	 */
	@Deprecated
	public static final String NAMESPACE_AND_MODULE_ASSIGNER_CONFIG_KEY = Settings.NAMESPACE_MODULE_ASSIGNER;
	
	/**
	 * @deprecated Use {@link Settings#REASONER_EXCLUDED_MODULE_IDS} instead.
	 */
	@Deprecated
	public static final String REASONER_EXCLUDE_MODULE_IDS = Settings.REASONER_EXCLUDED_MODULE_IDS;

	// FHIR specific constants
	public static final String SNOMED_URI_BASE = "http://snomed.info";
	public static final String SNOMED_URI_SCT = SNOMED_URI_BASE + "/sct";
	public static final String SNOMED_URI_DEV = SNOMED_URI_BASE + "/xsct";
	public static final String SNOMED_URI_ID = SNOMED_URI_BASE + "/id";
	
	// known language dialect aliases, see more information at: https://confluence.ihtsdotools.org/display/DOCECL/Appendix+C+-+Dialect+Aliases
	public static final Map<String, String> LANG_REFSET_DIALECT_ALIASES = Map.ofEntries(
		Map.entry("554461000005103", "da-dk"),
		Map.entry("32570271000036106", "en-au"),
		Map.entry("19491000087109", "en-ca"),
		Map.entry("900000000000508004", "en-gb"),
		Map.entry("21000220103", "en-ie"),
		Map.entry("271000210107", "en-nz"),
		Map.entry("281000210109", "en-nz-pat"),
		Map.entry("900000000000509007", "en-us"),
		Map.entry("608771002", "en-int-gmdn"),
		Map.entry("999001261000000100", "en-nhs-clinical"),
		Map.entry("999000671000001103", "en-nhs-dmd"),
		Map.entry("999000691000001104", "en-nhs-pharmacy"),
		Map.entry("999000681000001101", "en-uk-drug"),
		Map.entry("999001251000000103", "en-uk-ext"),
		/*
		 * XXX: A known difference between our map and the one in the SNOMED CT
		 * documentation is that "es" is associated with the identifier 450828004 in the
		 * linked Confluence page, even though it is not present in the International
		 * Edition.
		 * 
		 * We connect "es" to 448879004 instead, which is the identifier of
		 * "Spanish language reference set" and is part of SNOMED CT INT.
		 */
		Map.entry("448879004", "es"),
		/*
		 * XXX: This identifier is present in Spanish and Argentinian Editions, but 
		 * not the International release. Its preferred term is "conjunto de referencias 
		 * de lenguaje castellano para América Latina" (Spanish language reference set 
		 * for Latin America) and the parent concept is 448879004 (the entry before 
		 * this one).
		 * 
		 * Spanish content will use this reference set even though the term suggests a
		 * more focused use. Content authored in mainland Spain will most likely use the
		 * parent concept or another sibling concept instead.
		 */
		Map.entry("450828004", "es-ar"),
		Map.entry("5641000179103", "es-uy"),
		Map.entry("71000181105", "et-ee"),
		Map.entry("722130004", "de"),
		Map.entry("722131000", "fr"),
		Map.entry("21000172104", "fr-be"),
		Map.entry("20581000087109", "fr-ca"),
		Map.entry("722129009", "ja"),
		Map.entry("31000172101", "nl-be"),
		Map.entry("31000146106", "nl-nl"),
		Map.entry("61000202103", "nb-no"),
		Map.entry("91000202106", "nn-no"),
		Map.entry("46011000052107", "sv-se"),
		Map.entry("722128001", "zh")	
	);
	
	/**
	 * Extracts the 7-digit namespace portion of the given concept FSN using the default
	 * regular expression pattern. 
	 * <p>
	 * An empty string is returned under the following conditions:
	 * <ul>
	 * <li>{@code conceptId} is {@link Concepts#CORE_NAMESPACE_ID} (the core namespace)</li>
	 * <li>{@code conceptId} is null or empty</li>
	 * <li>{@code fsn}  is null or empty</li>
	 * </ul>
	 * 
	 * @param conceptId the concept ID (may be null or empty)
	 * @param fsn the fully specified name (may be null or empty)
	 * @return the namespace extracted from the FSN or an empty string
	 * @throws IllegalStateException if the FSN does not match the namespace pattern
	 */
	public static String getNamespace(String conceptId, String fsn) {
		return getNamespace(DEFAULT_NAMESPACE_PATTERN, conceptId, fsn);
	}

	/**
	 * Extracts the 7-digit namespace portion of the given concept FSN using the specified
	 * regular expression pattern. 
	 * <p>
	 * An empty string is returned under the following conditions:
	 * <ul>
	 * <li>{@code conceptId} is {@link Concepts#CORE_NAMESPACE_ID} (the core namespace)</li>
	 * <li>{@code conceptId} is null or empty</li>
	 * <li>{@code fsn}  is null or empty</li>
	 * </ul>
	 * 
	 * @param namespacePattern the regular expression pattern to use for extracting the 
	 * namespace (in capture group 1)
	 * @param conceptId the concept ID (may be null or empty)
	 * @param fsn the fully specified name (may be null or empty)
	 * @return the namespace extracted from the FSN or an empty string
	 * @throws IllegalStateException if the FSN does not match the namespace pattern
	 */
	public static String getNamespace(String namespacePattern, String conceptId, String fsn) {
		if (Concepts.CORE_NAMESPACE_ID.equals(conceptId) || Strings.isNullOrEmpty(conceptId) || Strings.isNullOrEmpty(fsn)) {
			return "";
		} else {
			final Matcher matcher = Pattern.compile(namespacePattern).matcher(fsn);
			checkState(matcher.matches(), "Pattern %s does not match on input: %s", namespacePattern, fsn);
			return matcher.group(1);
		}
	}
	
}