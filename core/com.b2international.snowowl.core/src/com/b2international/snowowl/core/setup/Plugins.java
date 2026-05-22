/*
 * Copyright 2018-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.setup;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import com.b2international.commons.CompositeClassLoader;
import com.b2international.snowowl.core.config.SnowOwlConfiguration;
import com.b2international.snowowl.core.plugin.ClassPathScanner;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * @since 7.0
 */
public final class Plugins implements Iterable<Plugin> {

	private final Collection<Plugin> plugins;
	private final CompositeClassLoader compositeClassLoader;
	
	@FunctionalInterface
	interface PluginTask {
		void run(Plugin plugin) throws Exception;
	}
	
	/**
	 * Constructs a new {@link Plugins} instance with the given set of Plug-ins.
	 * 
	 * @param plugins
	 */
	public Plugins(Collection<Plugin> plugins) {
		this.plugins = List.copyOf(plugins);
		final CompositeClassLoader classLoader = new CompositeClassLoader();
		plugins.stream().map(Plugin::getClass).map(Class::getClassLoader).forEach(classLoader::add);
		this.compositeClassLoader = classLoader;
	}

	/**
	 * Initializes all currently existing {@link Plugin}s within the given {@link Environment}.
	 * 
	 * @param configuration
	 * @param environment
	 * @param scanner
	 *            - the scanner that can be used to dependency inject various implementations into services through class path scanning
	 * @throws Exception
	 * @see Plugin#init(Environment)
	 */
	public void init(SnowOwlConfiguration configuration, Environment environment, ClassPathScanner scanner) throws Exception {
		runParallel(plugin -> plugin.init(configuration, environment, scanner));
	}
	
	/**
	 * Executes {@link Plugin#run(SnowOwlConfiguration, Environment, ClassPathScanner)} methods.
	 * 
	 * @param configuration
	 * @param environment
	 * @param scanner
	 *            - the scanner that can be used to dependency inject various implementations into services through class path scanning
	 * @throws Exception
	 * @see Plugin#run(Environment)
	 */
	public void run(SnowOwlConfiguration configuration, Environment environment, ClassPathScanner scanner) throws Exception {
		runParallel(plugin -> plugin.run(configuration, environment, scanner));
	}

	/**
	 * Executes {@link PreRunCapableBootstrapFragment#preRun(SnowOwlConfiguration, Environment)} methods in the currently registered
	 * {@link Plugin}s.
	 * 
	 * @param configuration
	 * @param environment
	 * @param scanner
	 *            - the scanner that can be used to dependency inject various implementations into services through class path scanning
	 * @throws Exception 
	 */
	public void preRun(SnowOwlConfiguration configuration, Environment environment, ClassPathScanner scanner) throws Exception {
		runParallel(plugin -> plugin.preRun(configuration, environment, scanner));
	}
	
	/**
	 * Executes {@link Plugin#postRun(SnowOwlConfiguration, Environment)} methods in the currently registered
	 * {@link Plugin}s.
	 * 
	 * @param configuration
	 * @param environment
	 * @param scanner
	 *            - the scanner that can be used to dependency inject various implementations into services through class path scanning
	 * @throws Exception 
	 */
	public void postRun(SnowOwlConfiguration configuration, Environment environment, ClassPathScanner scanner) throws Exception {
		runParallel(plugin -> plugin.postRun(configuration, environment, scanner));
	}

	/**
	 * @return all {@link Plugin}s.
	 */
	public Collection<Plugin> getPlugins() {
		return plugins;
	}

	@Override
	public Iterator<Plugin> iterator() {
		return plugins.iterator();
	}

	/**
	 * @return a class loader instance that can load classes from all available {@link Plugin} instances.
	 */
	public ClassLoader getCompositeClassLoader() {
		return compositeClassLoader;
	}
	
	private void runParallel(PluginTask task) throws Exception {
		var parallelInit = MoreExecutors.listeningDecorator(Executors.newVirtualThreadPerTaskExecutor());
		var inits = new ArrayList<ListenableFuture<Boolean>>();
		for (Plugin plugin : this.plugins) {
			inits.add(parallelInit.submit(new Callable<Boolean>() {
				@Override
				public Boolean call() throws Exception {
					task.run(plugin);
					return true;
				}
			}));
		}
		Futures.allAsList(inits).get();
	}

}