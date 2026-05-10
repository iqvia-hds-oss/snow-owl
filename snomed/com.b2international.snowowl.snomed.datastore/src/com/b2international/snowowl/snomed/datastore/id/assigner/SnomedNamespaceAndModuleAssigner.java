/*
 * Copyright 2017-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.snomed.datastore.id.assigner;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.b2international.commons.exceptions.FormattedRuntimeException;
import com.b2international.snowowl.core.api.SnowowlRuntimeException;
import com.b2international.snowowl.core.domain.BranchContext;
import com.b2international.snowowl.core.plugin.ClassPathScanner;

/**
 * Common interface for a namespace-module allocator which:
 * <ul>
 * <li>For each inferred relationship, returns the expected namespace and module ID, given the source concept ID;
 * </ul>
 * Subclasses must be annotated with {@link AssignerType} annotation to get an identifiable name for the registry.
 * 
 * @since 5.11.5
 */
public interface SnomedNamespaceAndModuleAssigner {

	/**
	 * Initialize the assigner with default module and namespace values.
	 * 
	 * @param defaultNamespace
	 * @param defaultModule
	 * @param context
	 */
	void init(String defaultNamespace, String defaultModule, BranchContext context);

	/**
	 * Returns an SCTID to be registered to a relationship based on its source concept ID.
	 * 
	 * @param sourceConceptId
	 *            the concept ID of the relationship to allocate the SCTID to
	 * @return the namespace for the relationship
	 */
	String getRelationshipNamespace(String sourceConceptId);

	/**
	 * Returns a module concept to be assigned to a relationship based on its source concept ID.
	 * 
	 * @param sourceConceptId
	 *            the concept ID of the relationship to determine the module for
	 * @return the module ID for the relationship
	 */
	String getRelationshipModuleId(String sourceConceptId);

	/**
	 * @param conceptIds
	 */
	void collectRelationshipModules(Set<String> conceptIds);

	/**
	 * Clears the internal maps of this assigner.
	 */
	void clear();

	class Registry {
		
		private final Map<String, Class<?>> registry;

		public Registry(ClassPathScanner scanner) {
			this.registry = scanner.getComponentsClassesByInterface(SnomedNamespaceAndModuleAssigner.class)
				.stream()
				.collect(Collectors.toMap(this::getAssignerType, a -> a));
		}
		
		private String getAssignerType(Class<?> assignerClass) {
			return assignerClass.getAnnotation(AssignerType.class).name();
		}
		
		/**
		 * Instantiate a namespace and module assigner instance based on the given type.
		 * 
		 * @param context
		 * @param assignerType
		 * @param moduleId
		 * @param namespace
		 * @return
		 */
		public SnomedNamespaceAndModuleAssigner getAssigner(BranchContext context, String assignerType, String moduleId, String namespace) {
			final Class<?> assignerClass = this.registry.get(assignerType);
			
			if (assignerClass == null) {
				throw new FormattedRuntimeException("Couldn't find namespace and module assigner '%s'.", assignerType);
			}
			
			try {
				// the default constructor should exist and should be public, otherwise fail
				SnomedNamespaceAndModuleAssigner assigner = (SnomedNamespaceAndModuleAssigner) assignerClass.getConstructor().newInstance();
				assigner.init(namespace, moduleId, context);
				return assigner;
			} catch (Exception e) {
				throw new SnowowlRuntimeException("Unable to instantiate module and namespace assigner of type: " + assignerType, e);
			}
		}
	}
	

}
