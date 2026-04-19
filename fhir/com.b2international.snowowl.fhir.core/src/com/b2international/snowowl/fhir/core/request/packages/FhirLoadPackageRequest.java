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
package com.b2international.snowowl.fhir.core.request.packages;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.function.Predicate;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.api.SnowowlRuntimeException;
import com.b2international.snowowl.core.attachments.Attachment;
import com.b2international.snowowl.core.attachments.AttachmentRegistry;
import com.b2international.snowowl.core.events.Request;
import com.b2international.snowowl.core.setup.Environment;
import com.b2international.snowowl.fhir.core.exceptions.BadRequestException;
import com.b2international.snowowl.fhir.core.request.codesystem.FhirCodeSystemWriteSupport;
import com.b2international.snowowl.fhir.core.request.conceptmap.FhirConceptMapWriteSupport;
import com.b2international.snowowl.fhir.core.request.valueset.FhirValueSetWriteSupport;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Request class for loading FHIR packages.
 * Handles downloading from registry or accepting local uploads,
 * extracting the package, parsing resources, and importing them.
 * 
 * @since 10.1.0
 */
public final class FhirLoadPackageRequest implements Request<ServiceProvider, FhirLoadPackageResultParameters> {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = LoggerFactory.getLogger(FhirLoadPackageRequest.class);
	
	private static final String CODE_SYSTEM = "CodeSystem";
	private static final String VALUE_SET = "ValueSet";
	private static final String CONCEPT_MAP = "ConceptMap";
	
	private static final Predicate<String> RECOGNIZED_PACKAGE_FILE_FILTER = 
			packageName -> false 
				|| packageName.startsWith(CODE_SYSTEM)
				|| packageName.startsWith(VALUE_SET)
				|| packageName.startsWith(CONCEPT_MAP);

	private Attachment packageToLoad;
	
	@JsonProperty
	private FhirLoadPackageParameters parameters;
	
	void setParameters(FhirLoadPackageParameters parameters) {
		this.parameters = parameters;
	}
	
	void setPackageToLoad(Attachment packageToLoad) {
		this.packageToLoad = packageToLoad;
	}

	@Override
	public FhirLoadPackageResultParameters execute(ServiceProvider context) {
		final Path packageFile = fetchFhirPackage(context);

		LOG.info("Parsing FHIR package contents '{}'", packageFile.getFileName());
		final FhirPackage fhirPackage = FhirPackage.parse(packageFile, context.service(ObjectMapper.class), RECOGNIZED_PACKAGE_FILE_FILTER);

		if (!fhirPackage.hasRecognizedEntries()) {
			throw new BadRequestException("FHIR package does not contain any CodeSystem, ValueSet or ConceptMap resources");
		}
		
		// check package.json has the same package info as the one requested in the params, just to ensure the we downloaded the right package
		if (parameters.getName() != null && !Objects.equals(parameters.getName().getValue(), fhirPackage.getPackageJson().name())) {
			throw new BadRequestException(String.format("Received FHIR package '%s' is not the requested one '%s'.", fhirPackage.getPackageJson().name(), parameters.getName().getValue()));
		}
		
		if (parameters.getVersion() != null && !Objects.equals(parameters.getVersion().getValue(), fhirPackage.getPackageJson().version())) {
			throw new BadRequestException(String.format("Received FHIR package '%s' has a different version '%s' that the requested one '%s'.", fhirPackage.getPackageJson().name(), fhirPackage.getPackageJson().version(), parameters.getName().getValue()));
		}
		
		// package.json is valid and there are entries to import, extract all recognized files
		LOG.info("Extracting FHIR package '{}'", packageFile.getFileName());
		Path packageFolder;
		try {
			packageFolder = Files.createTempDirectory(packageFile.getFileName().toString());
		} catch (IOException e) {
			throw new SnowowlRuntimeException("Failed to create temporary directory for FHIR package extraction", e);
		}
		
//		boolean loadDependencies = parameters.getDependencies() != null && parameters.getDependencies().getValue();
		// TODO fetch and load dependencies in before loading in the current package
		// TODO should we generate a bundle for the entire FHIR Package
		
		fhirPackage.extractTo(packageFolder);
		
		try {
			importFiles(context, packageFolder);
		} finally {
			try {
				Files.delete(packageFolder);
			} catch (IOException e) {
				throw new SnowowlRuntimeException("Failed to delete temporary directory of FHIR package", e);
			}
		}
		
		final FhirLoadPackageResultParameters result = new FhirLoadPackageResultParameters();
		result.setSuccess(true);
		return result;
	}
	
	private void importFiles(ServiceProvider context, Path packageFolder) {
		FhirCodeSystemWriteSupport codeSystemOps = context.service(FhirCodeSystemWriteSupport.class);
		FhirValueSetWriteSupport valueSetOps = context.service(FhirValueSetWriteSupport.class);
		FhirConceptMapWriteSupport conceptMapOps = context.service(FhirConceptMapWriteSupport.class);
		try {
			Files.list(packageFolder.resolve(FhirPackage.PACKAGE_FOLDER))
				.forEach(pathToImport -> {
					// TODO resource conversion to proper FHIR format
					// parse into raw JSON, read fhirVersion parameter then based on that parse the content into proper model?
//					org.hl7.fhir.r5.model.CodeSystem cs = mapper.convertValue(resourceNode, org.hl7.fhir.r5.model.CodeSystem.class);
					
					if (pathToImport.getFileName().startsWith(CODE_SYSTEM)) {
//						codeSystemOps.update(context, cs, "system", null, null, null, null);
//						codeSystemCount++;
					} else if (pathToImport.getFileName().startsWith(VALUE_SET)) {
//						valueSetOps.update(context, vs, "system", null, null, null, null);
//						valueSetCount++;
					} else if (pathToImport.getFileName().startsWith(CONCEPT_MAP)) {
//						conceptMapOps.update(context, cm, "system", null, null, null, null);
//						conceptMapCount++;
					} else {
						// raise warning about extract but not handled file
					}
				});
		} catch (IOException e) {
			throw new SnowowlRuntimeException("Failed to list FHIR package directory", e);
		}
	}

	private Path fetchFhirPackage(ServiceProvider context) {
			// using a local Snowy data path folder for future reuse of downloaded packages
			Path packagesDirectory = context.service(Environment.class).getDataPath().resolve("fhir-packages");
			// if there is an attachment attached to the request use that and copy it to the fhir-packages dir and remove it from attachments
			if (packageToLoad != null) {
				// check if package data is specified in the parameters object and raise errors if yes
				if (parameters.hasPackageInfo()) {
					throw new BadRequestException(String.format("Unable to fulfill request as both an attachment (multipart=file) and package information (name and/or version) are available."));
				}
				
				// ensure packagesDirectory exists
				try {
					Files.createDirectories(packagesDirectory);
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				final Path packageFile = packagesDirectory.resolve(packageToLoad.getFileName());
				try (OutputStream out = Files.newOutputStream(packageFile, StandardOpenOption.CREATE)) {
					context.service(AttachmentRegistry.class).download(packageToLoad.getAttachmentId(), out);
				} catch (IOException e) {
					throw new SnowowlRuntimeException("Couldn't download FHIR package from attachments: " + packageToLoad.getFileName());
				} finally {
					// make sure we always delete the attachment
					context.service(AttachmentRegistry.class).delete(packageToLoad.getAttachmentId());
				}
				
				return packageFile;
				
			} else if (parameters.hasPackageInfo()) {
				String packageInfo = parameters.extractPackageInfo();
				String registry = parameters.getRegistry().getValue();
				if (registry.endsWith("/")) {
					registry = registry.substring(0, registry.length());
				}
				// TODO [security] verify that registry is from an allowed set of registry URLs to avoid downloading malicious packages?
				
				String packageUrl = String.join("/", registry, packageInfo.replaceFirst("@", "/"));
					
				try {
					LOG.info("Downloading FHIR package '{}' from registry '{}'", packageInfo, registry);
					// TODO [caching][syndication] allow caching of packages in the Snow Owl data folder to avoid redownloading the same package, also offer them via our own package registry URL for downstream servers?
					final Path packageFile = packagesDirectory.resolve(packageInfo + ".tgz");
					// TODO configurable download timeouts
					FileUtils.copyURLToFile(new URI(packageUrl).toURL(), packageFile.toFile(), 30000, 60000);
					return packageFile;
				} catch (IOException e) {
					throw new BadRequestException(String.format("Package '%s' cannot be downloaded from registry URL '%s'", packageInfo, packageUrl));
				} catch (URISyntaxException e) {
					throw new BadRequestException("Malformed package location: " + packageUrl);
				}
			} else {
				// neither attachment nor package info is available, raise error
				throw new BadRequestException("FHIR Package coordinates are missing. Either specify them via name and version parameters or upload a file as a request attachment.");
			}
			
	}

}