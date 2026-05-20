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
package com.b2international.snowowl.core.identity;

import java.time.InstantSource;
import java.util.*;

import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.SnowOwl;
import com.b2international.snowowl.core.config.SnowOwlConfiguration;
import com.b2international.snowowl.core.identity.jwks.JwksIdentityProvider;
import com.b2international.snowowl.core.identity.jwks.JwksIdentityProviderConfig;
import com.b2international.snowowl.core.plugin.ClassPathScanner;
import com.b2international.snowowl.core.plugin.Component;
import com.b2international.snowowl.core.setup.ConfigurationRegistry;
import com.b2international.snowowl.core.setup.Environment;
import com.b2international.snowowl.core.setup.Plugin;
import com.b2international.snowowl.core.setup.Plugins;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * @since 5.11
 */
@Component
public final class IdentityPlugin extends Plugin {

	@Override
	public void addConfigurations(ConfigurationRegistry registry) {
		registry.add("identity", IdentityConfiguration.class);
	}
	
	@Override
	public void init(SnowOwlConfiguration configuration, Environment env, ClassPathScanner scanner) throws Exception {
		final IdentityConfiguration conf = configuration.getModuleConfig(IdentityConfiguration.class);
		IdentityProvider identityProvider = initIdentityProvider(env, conf);
		
		// ensure that identity providers are initialized
		identityProvider.init(env);
		
		JWTSupport jwtSupport = initJWT(env, conf);
		// JWTSupport for token generation and verification on a global level
		env.services().registerService(JWTSupport.class, jwtSupport);
		
		IdentityProvider.LOG.info("Configured identity providers [{}]", identityProvider.getInfo());
		// the main identity provider instance
		env.services().registerService(IdentityProvider.class, identityProvider);
		// HTTP Authorization header verification using global and identity specific JWT support
		env.services().registerService(AuthorizationHeaderVerifier.class, new AuthorizationHeaderVerifier(jwtSupport, identityProvider));
	}

	@VisibleForTesting
	/*package*/ JWTSupport initJWT(Environment env, final IdentityConfiguration conf) throws Exception {
		return initJWT(env, conf, InstantSource.system());
	}
	
	@VisibleForTesting
	/*package*/ JWTSupport initJWT(Environment env, final IdentityConfiguration conf, InstantSource instantSource) throws Exception {
		// attach global JWT support to the current identity provider configured via snowowl.yml
		JWTSupport jwtSupport = new JWTSupport(conf, instantSource);
		jwtSupport.init();
		return jwtSupport;
	}

	@VisibleForTesting
	/*package*/ IdentityProvider initIdentityProvider(ServiceProvider context, final IdentityConfiguration conf) throws Exception {
		final List<IdentityProvider> providers = createProviders(context, conf.getProviderConfigurations() == null ? List.of() : conf.getProviderConfigurations());

		if (!Strings.isNullOrEmpty(conf.getJwksUrl())) {
			// if jwks identity provider is present amongst the configured ones, fail startup with clear error message
			if (isJwksIdentityProviderSet(providers)) {
				throw new SnowOwl.InitializationException("Both 'identity.jwksUrl' and 'identity.providers.jwks' are configured. Remove the deprecated 'identity.jwksUrl' in favor of the dedicated 'jwks' identity provider.");
			}
			
			// if only the old deprecated JWKS URL is set, raise deprecation warning message
			context.deprecationLog().log("'identity.jwksUrl' configuration option is deprecated. Change your configuration setting to use the new 'jwks' identity provider configuration.");
			
			// prepare backward compatible JWKS URL configuration
			JwksIdentityProviderConfig jwksConfig = new JwksIdentityProviderConfig();
			jwksConfig.setJwksUrl(conf.getJwksUrl());
			jwksConfig.setJws(conf.getJws());
			jwksConfig.setIssuer(conf.getIssuer());
			jwksConfig.setUserIdClaimProperty(conf.getUserIdClaimProperty());
			jwksConfig.setPermissionsClaimProperty(conf.getPermissionsClaimProperty());
			
			// add a JWKS URL identity provider
			providers.add(new JwksIdentityProvider(jwksConfig));
		}
		
		IdentityProvider identityProvider = null;
		if (providers.isEmpty()) {
			identityProvider = IdentityProvider.UNPROTECTED;
		} else if (providers.size() == 1) {
			identityProvider = Iterables.getOnlyElement(providers);
		} else {
			identityProvider = new MultiIdentityProvider(providers);
		}
		
		if (conf.isAdminParty()) {
			identityProvider = new AdminPartyIdentityProvider(identityProvider);
		}
		
		return identityProvider;
	}

	private boolean isJwksIdentityProviderSet(Collection<IdentityProvider> identityProviders) {
		return identityProviders.stream().anyMatch(ip -> ip instanceof JwksIdentityProvider);
	}

	private List<IdentityProvider> createProviders(ServiceProvider ctx, List<IdentityProviderConfig> providerConfigurations) {
		final List<IdentityProvider> providers = new ArrayList<>(3);
		final List<IdentityProviderFactory> factories = ctx.service(Plugins.class).getPlugins().stream()
		.filter(IdentityProviderFactory.class::isInstance)
		.map(IdentityProviderFactory.class::cast)
		.toList();
		
		providerConfigurations.forEach(providerConfig -> {
			Optional<IdentityProviderFactory> factory = factories.stream().filter(f -> f.getConfigType() == providerConfig.getClass()).findFirst();
			if (factory.isPresent()) {
				try {
					providers.add(factory.get().create(providerConfig));
				} catch (Exception e) {
					throw new SnowOwl.InitializationException(String.format("Couldn't initialize '%s' identity provider", factory), e);
				}
			} else {
				throw new IllegalStateException(String.format("Couldn't find identity provider factory for '%s'.", providerConfig.getClass()));
			}
		});
		return providers;
	}

	static Collection<Class<? extends IdentityProviderConfig>> getAvailableConfigClasses(ClassPathScanner scanner) {
		final ImmutableList.Builder<Class<? extends IdentityProviderConfig>> configs = ImmutableList.builder();
		final Iterator<IdentityProviderFactory> it = scanner.getComponentsByInterface(IdentityProviderFactory.class).iterator();
		while (it.hasNext()) {
			configs.add(it.next().getConfigType());
		}
		return configs.build();
	}
	
}
