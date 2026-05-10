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
package com.b2international.snowowl.core.plugin;

import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;

import com.b2international.snowowl.core.api.SnowowlRuntimeException;

import net.jodah.typetools.TypeResolver;

/**
 * @since 10.2.0
 * @param <T>
 */
public abstract class PluggableServiceRegistry<T> {
	
	private final Collection<Class<?>> classes;
	
	public PluggableServiceRegistry(ClassPathScanner scanner) {
		final Class<?>[] types = TypeResolver.resolveRawArguments(PluggableServiceRegistry.class, getClass());
		checkState(TypeResolver.Unknown.class != types[0], "Couldn't resolve type for class %s", getClass().getSimpleName());
		var pluggableClass = (Class<T>) types[0];
		if (pluggableClass.isInterface()) {
			this.classes = scanner.getComponentsClassesByInterface(pluggableClass);
		} else {
			this.classes = scanner.getComponentsClassesBySuperclass(pluggableClass);
		}
	}
	
	public Collection<T> get() {
		return classes.stream().map(klass -> {
			try {
				return (T) klass.getConstructor().newInstance();
			} catch (Throwable e) {
				throw new SnowowlRuntimeException("Unable to instantiate registry class: " + klass, e);
			}
		}).toList();
	}
	
}
