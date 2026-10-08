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
package com.b2international.snowowl.core.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.b2international.commons.exceptions.*;
import com.b2international.snowowl.core.util.PlatformUtil;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;

/**
 * @since 4.1
 */
@RestControllerAdvice
public class ControllerExceptionMapper {

	private static final Logger LOG = LoggerFactory.getLogger(ControllerExceptionMapper.class);
	private static final String GENERIC_USER_MESSAGE = "Something went wrong during the processing of your request.";
	
	/**
	 * Generic <b>Internal Server Error</b> exception handler, serving as a fallback for RESTful client calls.
	 * 
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final Exception ex) {
		final String message = Throwables.getRootCause(ex).getMessage();
		if (!Strings.isNullOrEmpty(message) && message.toLowerCase().contains("broken pipe")) {
	        return null; // socket is closed, cannot return any response    
	    } else {
    		LOG.error("Exception during request processing", ex);
	    	return jsonError(HttpStatus.INTERNAL_SERVER_ERROR, ApiError.builder(GENERIC_USER_MESSAGE).build());
	    }
	}
	
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final AsyncRequestTimeoutException e) {
		return jsonError(HttpStatus.GATEWAY_TIMEOUT, ApiError.builder("Request is taking longer than expected to complete. Retry again in a few minutes.").build());
	}
	
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final MaxUploadSizeExceededException e) {
		return jsonError(HttpStatus.BAD_REQUEST, ApiError.builder(e.getMessage()).build());
	}
	
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final MultipartException e) {
		return jsonError(HttpStatus.BAD_REQUEST, ApiError.builder("Couldn't process multipart request: " + e.getMostSpecificCause().getMessage()).build());
	}
	
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final HttpMediaTypeNotSupportedException e) {
		return jsonError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ApiError.builder("HTTP Media Type " + e.getContentType() + " is not supported. Supported media types are: " + e.getSupportedMediaTypes()).build());
	}
	
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final HttpRequestMethodNotSupportedException e) {
		return jsonError(HttpStatus.METHOD_NOT_ALLOWED, ApiError.builder("Method " + e.getMethod() + " is not allowed").build());
	}
	
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final BindException e) {
		return jsonError(HttpStatus.BAD_REQUEST, ApiError.builder("Invalid  parameter: '" + e.getMessage() + "'.").build());
	}
	
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final MissingPathVariableException e) {
		return jsonError(HttpStatus.BAD_REQUEST, ApiError.builder("Missing path parameter: '" + e.getVariableName() + "'.").build());
	}
	
	@ExceptionHandler
	public ResponseEntity<Void> handle(final NotModifiedException e) {
		return new ResponseEntity<>(HttpStatus.NOT_MODIFIED);
	}
	
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final UnauthorizedException ex) {
		final HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add("WWW-Authenticate", "Basic");
		headers.add("WWW-Authenticate", "Bearer");
		return new ResponseEntity<>(RestApiError.of(ex.toApiError()).build(HttpStatus.UNAUTHORIZED.value()), headers, HttpStatus.UNAUTHORIZED);
	}
	
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final ForbiddenException ex) {
		return jsonError(HttpStatus.FORBIDDEN, ex.toApiError());
	}
	
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final RequestTimeoutException ex) {
		if (PlatformUtil.isDevVersion()) {
    		LOG.error("Timeout during request processing", ex);
    	} else {
    		LOG.trace("Timeout during request processing", ex);
    	}
		return jsonError(HttpStatus.REQUEST_TIMEOUT, ApiError.builder(GENERIC_USER_MESSAGE).build());
	}
	
	/**
	 * Exception handler converting any {@link JsonMappingException} to an <em>HTTP 400</em>.
	 * 
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final HttpMessageNotReadableException ex) {
		LOG.trace("Exception during processing of a JSON document", ex);
		return jsonError(HttpStatus.BAD_REQUEST, ApiError.builder("Invalid JSON representation").developerMessage(ex.getMessage()).build());
	}
	
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final ApiErrorException ex) {
		final ApiError error = ex.toApiError();
		return jsonError(HttpStatus.valueOf(error.getStatus()), error);
	}

	/**
	 * <b>Not Found</b> exception handler. All {@link NotFoundException not found exception}s are mapped to {@link HttpStatus#NOT_FOUND
	 * <em>404 Not Found</em>} in case of the absence of an instance resource.
	 * 
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final NotFoundException ex) {
		return jsonError(HttpStatus.NOT_FOUND, ex.toApiError());
	}

	/**
	 * Exception handler to return <b>Not Implemented</b> when an {@link UnsupportedOperationException} is thrown from the underlying system.
	 * 
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final NotImplementedException ex) {
		return jsonError(HttpStatus.NOT_IMPLEMENTED, ex.toApiError());
	}

	/**
	 * Exception handler to return <b>Bad Request</b> when an {@link BadRequestException} is thrown from the underlying system.
	 * 
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final BadRequestException ex) {
		return jsonError(HttpStatus.BAD_REQUEST, ex.toApiError());
	}
	
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final IllegalArgumentException ex) {
		ex.printStackTrace();
		return jsonError(HttpStatus.BAD_REQUEST, ApiError.builder(ex.getMessage()).build());
	}
	
	/**
	 * Exception handler to return <b>Bad Request</b> when an {@link BadRequestException} is thrown from the underlying system.
	 * 
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final ConflictException ex) {
		if (ex.getCause() != null) {
			LOG.info("Conflict with cause", ex);
		}
		return jsonError(HttpStatus.CONFLICT, ex.toApiError());
	}
	
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final TooManyRequestsException ex) {
		final HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add("X-Rate-Limit-Retry-After-Seconds", Long.toString(ex.getSecondsToWait()));
		return new ResponseEntity<>(RestApiError.of(ex.toApiError()).build(HttpStatus.TOO_MANY_REQUESTS.value()), headers, HttpStatus.TOO_MANY_REQUESTS);
	}
	
	/**
	 * Exception handler for exceptions thrown by conversions performed by {@link Converter}
	 * implementations.
	 * @param ex
	 * @return
	 */
	@ExceptionHandler
    public ResponseEntity<RestApiError> handle(final ConversionFailedException ex) {
		return jsonError(HttpStatus.BAD_REQUEST, ApiError.builder(ex.getMessage()).build());
    }

	/**
	 * Exception handler for exceptions thrown due to incorrect arguments.
	 * @param ex
	 * @return
	 */
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final MethodArgumentTypeMismatchException ex) {
		return jsonError(HttpStatus.BAD_REQUEST, ApiError.builder(ex.getMessage()).build());
	}
	
	/**
	 * Exception handler for exceptions thrown due to missing multipart files.
	 * @param ex
	 * @return
	 */
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final MissingServletRequestPartException ex) {
		return jsonError(HttpStatus.BAD_REQUEST, ApiError.builder(ex.getMessage()).build());
	}
	
	/**
	 * Exception handler for exceptions thrown due to required but missing query parameters.
	 * @param ex
	 * @return
	 */
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final MissingServletRequestParameterException ex) {
		return jsonError(HttpStatus.BAD_REQUEST, ApiError.builder(ex.getMessage()).build());
	}

	/**
	 * Explicitly use {@link MediaType#APPLICATION_JSON} as return content type even if other {@link HttpsHeaders#ACCEPT} were used.
	 * If we do not specify it, then spring will try to convert it for example using {@link MediaType#APPLICATION_OCTET_STREAM}. 
	 */
	private ResponseEntity<RestApiError> jsonError(final HttpStatus status, final ApiError error) {
		return ResponseEntity
			.status(status)
			.contentType(MediaType.APPLICATION_JSON)
			.body(RestApiError.of(error).build(status.value()));
	}
	
}
