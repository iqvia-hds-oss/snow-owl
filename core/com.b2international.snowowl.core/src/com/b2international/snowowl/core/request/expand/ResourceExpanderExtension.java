/*
 * Copyright 2022-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.request.expand;

import java.util.List;

import com.b2international.commons.http.ExtendedLocale;
import com.b2international.commons.options.Options;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.plugin.ClassPathScanner;
import com.b2international.snowowl.core.plugin.PluggableServiceRegistry;

/**
 * @since 8.1
 */
public interface ResourceExpanderExtension {

	/**
	 * @since 10.2.0
	 */
	class Registry extends PluggableServiceRegistry<ResourceExpanderExtension> {

		public Registry(ClassPathScanner scanner) {
			super(scanner);
		}
	}
	
	<T> boolean canExpand(Class<T> type);
	
	<T> ResourceExpander<T> create(ServiceProvider context, Options expand, List<ExtendedLocale> locales, Class<T> type);
	
}
