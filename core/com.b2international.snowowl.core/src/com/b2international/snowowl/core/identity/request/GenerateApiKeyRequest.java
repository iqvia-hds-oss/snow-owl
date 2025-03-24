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
package com.b2international.snowowl.core.identity.request;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.auth0.jwt.JWT;
import com.b2international.commons.CompareUtils;
import com.b2international.commons.exceptions.BadRequestException;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.authorization.AuthorizedRequest;
import com.b2international.snowowl.core.authorization.Unprotected;
import com.b2international.snowowl.core.events.Request;
import com.b2international.snowowl.core.events.util.RequestHeaders;
import com.b2international.snowowl.core.identity.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;

/**
 * @since 7.2
 */
@Unprotected
final class GenerateApiKeyRequest implements Request<ServiceProvider, User> {

	private static final long serialVersionUID = 2L;

	@JsonProperty
	private String username;
	
	private String password;
	
	private String token;

	@JsonProperty
	private String expiration;
	
	@JsonProperty
	private List<String> permissions;
	
	GenerateApiKeyRequest(String username, String password, String token) {
		this.username = username;
		this.password = password;
		this.token = token;
	}
	
	void setExpiration(String expiration) {
		this.expiration = expiration;
	}
	
	void setExpressions(List<String> permissions) {
		this.permissions = permissions;
	}
	
	@Override
	public User execute(ServiceProvider context) {
		User user = null;
		List<Permission> newTokenPermissions = List.of();
		
		if (!Strings.isNullOrEmpty(username) && !Strings.isNullOrEmpty(password)) {
			user = context.service(IdentityProvider.class).auth(username, password);
			newTokenPermissions = this.permissions == null ? user.getPermissions() : this.permissions.stream().map(Permission::valueOf).collect(Collectors.toList());
		} else if (!Strings.isNullOrEmpty(token)) {
			user = context.service(AuthorizationHeaderVerifier.class).authJWT(token);
			// at this point the token is valid but it might happen that it was issued by another authority allowed via JWKS provider, ensure that we only allow refreshing for the same issuer
			final String serverIssuer = context.config().getModuleConfig(IdentityConfiguration.class).getIssuer();
			final String tokenIssuer = JWT.decode(token).getIssuer();
			if (!Objects.equals(tokenIssuer, serverIssuer)) {
				throw new BadRequestException("Token cannot be refreshed as it was issued by another server: '%s'", tokenIssuer);
			}
			// in case of token based auth, we only allow refreshing the token with the same permissions, to generate new tokens, the user/client has to provide its username and password again
			if (!CompareUtils.isEmpty(this.permissions)) {
				throw new BadRequestException("Token cannot be refreshed when permissions argument is also set.");
			}
			newTokenPermissions = user.getPermissions();
		} else {
			// check if there is an authorization token header and use that to login the user 
			final RequestHeaders requestHeaders = context.service(RequestHeaders.class);
			final String authorizationToken = requestHeaders.header(AuthorizedRequest.AUTHORIZATION_HEADER);
			
			if (!Strings.isNullOrEmpty(authorizationToken)) {
				user = context.service(AuthorizationHeaderVerifier.class).auth(authorizationToken);
				
				// in case of token based auth, we only allow refreshing the token with the same permissions, to generate new tokens, the user/client has to provide its username and password again
				if (!CompareUtils.isEmpty(this.permissions)) {
					throw new BadRequestException("Token cannot be refreshed when permissions argument is also set.");
				}
				
				newTokenPermissions = user.getPermissions();
			}
		}
		
		if (user == null) {
			throw new BadRequestException("Invalid authentication credentials provided.");
		}

		// generate and attach the new token
		user = user.withPermissions(newTokenPermissions);
		return user.withAccessToken(context.service(JWTSupport.class).generate(user, expiration));
	}

}
