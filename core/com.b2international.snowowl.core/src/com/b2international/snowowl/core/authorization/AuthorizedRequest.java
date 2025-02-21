/*
 * Copyright 2019-2025 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.authorization;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.b2international.commons.exceptions.UnauthorizedException;
import com.b2international.snowowl.core.RequestContext;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.events.DelegatingRequest;
import com.b2international.snowowl.core.events.Request;
import com.b2international.snowowl.core.events.util.RequestHeaders;
import com.b2international.snowowl.core.identity.AuthorizationHeaderVerifier;
import com.b2international.snowowl.core.identity.IdentityProvider;
import com.b2international.snowowl.core.identity.Permission;
import com.b2international.snowowl.core.identity.User;
import com.b2international.snowowl.core.monitoring.MonitoredRequest;
import com.b2international.snowowl.core.util.PlatformUtil;
import com.b2international.snowowl.eventbus.IEventBus;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

/**
 * @since 7.2
 * @param <R> - the return value's type
 */
public final class AuthorizedRequest<R> extends DelegatingRequest<ServiceProvider, ServiceProvider, R> {

	private static final long serialVersionUID = 1L;
	
	public static final String AUTHORIZATION_HEADER = "Authorization";
	
	public AuthorizedRequest(Request<ServiceProvider, R> next) {
		super(next);
	}

	@Override
	public R execute(ServiceProvider context) {
		final Collection<Request<?, ?>> requests = getNestedRequests();
		final ServiceProvider userContext = getUserContext(context, requests);
		
		/*
		 * XXX: the User instance can be attached either to the initial context (case #1 in getUserContext)
		 * or the request context, look in both places.
		 */
		final RequestContext requestContext = context.service(RequestContext.class);
		final User user = context.optionalService(User.class)
			.orElseGet(() -> requestContext.service(User.class));
		
		if (!User.SYSTEM.equals(user) && !user.isAdministrator()) {
			// authorize user whether it is permitted to execute the request(s) or not
			List<Permission> requiredPermissionsToExecute = requests.stream()
				.filter(AccessControl.class::isInstance)
				.map(AccessControl.class::cast)
				.flatMap(ac -> {
					List<Permission> permissions = ac.getPermissions(userContext, next());
					if (permissions.isEmpty()) {
						context.log().warn("No permissions required to execute request '{}'.", MonitoredRequest.toJson(context, next(), Map.of()));
					}
					return permissions.stream();
				})
				.collect(Collectors.toList());
			
			if (!requiredPermissionsToExecute.isEmpty()) {
				// throw a Forbidden Exception if the user does not have permission to perform the request
				context.optionalService(AuthorizationService.class)
					.orElse(AuthorizationService.DEFAULT)
					.checkPermission(context, user, requiredPermissionsToExecute);
			}
		}
		
		return next(userContext);
		
	}

	private ServiceProvider getUserContext(final ServiceProvider originalContext, final Collection<Request<?, ?>> requests) {
		final RequestHeaders requestHeaders = originalContext.service(RequestHeaders.class);

		// 1. If a User object is already attached to the context, we have already authenticated the user, no need to do it again
		final Optional<User> user = originalContext.optionalService(User.class);
		if (user.isPresent()) {
			return originalContext;
		}

		// 2. Unprotected requests do not require an authenticated user (if all nested requests are unprotected)
		if (requests.stream().allMatch(req -> req.getClass().isAnnotationPresent(Unprotected.class))) {
			return impersonatedContext(originalContext, requestHeaders, User.SYSTEM);
		}
		
		// 3. "Open house" identity providers do not require an authenticated user either
		final IdentityProvider identityProvider = originalContext.service(IdentityProvider.class);
		if (IdentityProvider.UNPROTECTED == identityProvider) {
			return impersonatedContext(originalContext, requestHeaders, User.SYSTEM);
		}

		// 4. All other requests require authorization in the form of a token
		final String authorizationToken = requestHeaders.header(AUTHORIZATION_HEADER);
		if (Strings.isNullOrEmpty(authorizationToken)) {
			Request<?, ?> request = Iterables.getFirst(requests, null);
			
			if (PlatformUtil.isDevVersion()) {
				System.err.println(request);
			}
			
			throw new UnauthorizedException("Missing authorization token")
				.withDeveloperMessage("Unable to execute request '%s' without a proper authorization token. "
					+ "Supply one either as a standard HTTP Authorization header or via the token query parameter.", request.getType());
		}
		
		// 5. If a token is provided it must be valid
		final AuthorizationHeaderVerifier headerVerifier = originalContext.service(AuthorizationHeaderVerifier.class);
		final User userFromToken = headerVerifier.auth(authorizationToken);
		if (userFromToken != null) {
			return impersonatedContext(originalContext, requestHeaders, userFromToken);
		}
		
		// 6. In any other case authentication has failed
		throw new UnauthorizedException("Incorrect authorization token");
	}

	private ServiceProvider impersonatedContext(final ServiceProvider originalContext, final RequestHeaders requestHeaders, final User user) {
		
		// Inject the user to the current RequestContext so that the entire request execution tree can access it
		originalContext.service(RequestContext.class)
			.bind(User.class, user);
		
		// Return a new context with an authorized event bus instance; include all headers from the original request
		final IEventBus originalBus = originalContext.service(IEventBus.class);
		final Map<String, String> originalHeaders = requestHeaders.headers();
		
		return originalContext.inject()
			.bind(IEventBus.class, new AuthorizedEventBus(originalBus, originalHeaders))
			.build();
	}
}
