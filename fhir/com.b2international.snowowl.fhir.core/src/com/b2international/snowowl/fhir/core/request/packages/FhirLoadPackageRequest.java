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
import java.nio.file.*;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.file.PathUtils;
import org.hl7.fhir.r5.model.*;

import com.b2international.commons.exceptions.NotImplementedException;
import com.b2international.fhir.r5.operations.LoadPackageParameters;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.api.SnowowlRuntimeException;
import com.b2international.snowowl.core.attachments.Attachment;
import com.b2international.snowowl.core.attachments.AttachmentRegistry;
import com.b2international.snowowl.core.events.Request;
import com.b2international.snowowl.core.setup.Environment;
import com.b2international.snowowl.fhir.core.FhirResourceParser;
import com.b2international.snowowl.fhir.core.exceptions.BadRequestException;
import com.b2international.snowowl.fhir.core.request.codesystem.FhirCodeSystemWriteSupport;
import com.b2international.snowowl.fhir.core.request.conceptmap.FhirConceptMapWriteSupport;
import com.b2international.snowowl.fhir.core.request.valueset.FhirValueSetWriteSupport;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;

import jakarta.validation.constraints.NotEmpty;

/**
 * Request class for loading FHIR packages.
 * Handles downloading from registry or accepting local uploads,
 * extracting the package, parsing resources, and importing them.
 * 
 * @since 10.1.0
 */
public final class FhirLoadPackageRequest implements Request<ServiceProvider, FhirLoadPackageResultParameters> {

	private static final long serialVersionUID = 1L;

	private static final String CODE_SYSTEM = "CodeSystem";
	private static final String VALUE_SET = "ValueSet";
	private static final String CONCEPT_MAP = "ConceptMap";
	
	private static final Predicate<String> RECOGNIZED_PACKAGE_FILE_FILTER = 
			packageEntryName -> false 
				|| packageEntryName.startsWith(CODE_SYSTEM)
				|| packageEntryName.startsWith(VALUE_SET)
				|| packageEntryName.startsWith(CONCEPT_MAP);

	
	@JsonProperty
	private final String author;
	@JsonProperty
	private final String owner;
	@JsonProperty
	private final String ownerProfileName;
	@JsonProperty
	private final LocalDate defaultEffectiveDate;
	
	@NotEmpty
	@JsonProperty
	private final String bundleId;
	
	private Attachment packageToLoad;
	
	@JsonProperty
	private LoadPackageParameters parameters;
	
	private transient int numberOfLoadedCodeSystems = 0;
	private transient int numberOfLoadedValueSets = 0;
	private transient int numberOfLoadedConceptMaps = 0;
	
	FhirLoadPackageRequest(final String author, final String owner, final String ownerProfileName, final LocalDate defaultEffectiveDate, final String bundleId) {
		this.author = author;
		this.owner = owner;
		this.ownerProfileName = ownerProfileName;
		this.defaultEffectiveDate = defaultEffectiveDate;
		this.bundleId = bundleId;
	}
	
	void setPackageToLoad(Attachment packageToLoad) {
		this.packageToLoad = packageToLoad;
	}
	
	void setParameters(LoadPackageParameters parameters) {
		this.parameters = parameters;
	}
	
	@Override
	public FhirLoadPackageResultParameters execute(ServiceProvider context) {
		final Path packageFile = fetchFhirPackage(context);

		context.log().info("Parsing FHIR package contents '{}'", packageFile.getFileName());
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
		
		if (fhirPackage.getPackageJson().fhirVersions().isEmpty()) {
			throw new BadRequestException(String.format("Received FHIR package does not declare the fhirVersions property in package.json which is required to properly load and import the resources."));
		}
		
		if (fhirPackage.getPackageJson().fhirVersions().size() > 1) {
			throw new BadRequestException(String.format("Received FHIR package declares more than one value in the fhirVersions property which is currently not supported."));
		}
		
		// package.json is valid and there are entries to import, extract all recognized files
		context.log().info("Extracting FHIR package '{}'", packageFile.getFileName());
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
		
		final FhirResourceParser resourceParser = new FhirResourceParser(FhirResourceParser.FORMAT_JSON, Iterables.getOnlyElement(fhirPackage.getPackageJson().fhirVersions()));
		try {
			importFiles(context, packageFolder, resourceParser);
		} finally {
			try {
				PathUtils.deleteDirectory(packageFolder);
			} catch (IOException e) {
				throw new SnowowlRuntimeException("Failed to delete temporary directory of FHIR package", e);
			}
		}
		
		final FhirLoadPackageResultParameters result = new FhirLoadPackageResultParameters();
		result.setSuccess(true);
		result.setNumberOfLoadedCodeSystems(numberOfLoadedCodeSystems);
		result.setNumberOfLoadedValueSets(numberOfLoadedValueSets);
		result.setNumberOfLoadedConceptMaps(numberOfLoadedConceptMaps);
		return result;
	}
	
	private void importFiles(ServiceProvider context, Path packageFolder, final FhirResourceParser resourceParser) {
		FhirCodeSystemWriteSupport codeSystemWriteSupport = context.optionalService(FhirCodeSystemWriteSupport.class).orElse(null);
		FhirValueSetWriteSupport valueSetWriteSupport = context.optionalService(FhirValueSetWriteSupport.class).orElse(null);
		FhirConceptMapWriteSupport conceptMapWriteSupport = context.optionalService(FhirConceptMapWriteSupport.class).orElse(null);
		
		// current server entitlements does not allow importing any FHIR resource
		if (codeSystemWriteSupport == null && valueSetWriteSupport == null && conceptMapWriteSupport == null) {
			return;
		}
		
		try {
			Iterable<Path> filesToImport = Files.list(packageFolder.resolve(FhirPackage.PACKAGE_FOLDER)).sorted(this::byResourceTypeImportOrder)::iterator;
			for (var pathToImport : filesToImport) {
				// TODO parse into raw JSON, read fhirVersion parameter then based on that parse the content into proper model?
				Resource resource = readResource(resourceParser, pathToImport);
				
				var resourceUrlsToImport = parameters.getResourceUrl().stream().map(UriType::getValue).collect(Collectors.toSet());
				// check if resource is selected to be imported through resourceUrl filter
				if (resource instanceof CanonicalResource canonicalResource && !resourceUrlsToImport.isEmpty() && !resourceUrlsToImport.contains(canonicalResource.getUrl())) {
					// skip resource if not
					continue;
				}
				
				switch (resource.getResourceType()) {
				case CodeSystem:
					if (codeSystemWriteSupport != null) {
						codeSystemWriteSupport.update(context, (CodeSystem) resource, author, owner, ownerProfileName, defaultEffectiveDate, bundleId);
						numberOfLoadedCodeSystems++;
					} else {
						// TODO register that resource cannot be imported via this server due to missing entitlement
					}					
					break;
				case ValueSet:
					if (valueSetWriteSupport != null) {
						valueSetWriteSupport.update(context, (ValueSet) resource, Map.of(), author, owner, ownerProfileName, defaultEffectiveDate, bundleId);
						numberOfLoadedValueSets++;
					} else {
						// TODO register that resource cannot be imported via this server due to missing entitlement
					}
					break;
				case ConceptMap:
					if (conceptMapWriteSupport != null) {
						conceptMapWriteSupport.update(context, (ConceptMap) resource, Map.of(), author, owner, ownerProfileName, defaultEffectiveDate, bundleId);
						numberOfLoadedConceptMaps++;
					} else {
						// TODO register that resource cannot be imported via this server due to missing entitlement
					}
					break;
				default:
					throw new NotImplementedException("Missing implementation for resource type import " + resource.getResourceType());
				}
					
			}
		} catch (IOException e) {
			throw new SnowowlRuntimeException("Failed to list FHIR package directory", e);
		}
	}
	
	private int byResourceTypeImportOrder(Path a, Path b) {
		int rankA = getResourceImportOrder(a);
		int rankB = getResourceImportOrder(a);
		return Ints.compare(rankA, rankB);
	}
	
	private int getResourceImportOrder(Path path) {
		return switch (path.getFileName().toString()) {
			case String s when s.startsWith(CODE_SYSTEM) -> 1;
			// valueset depends on codesystems
			case String s when s.startsWith(VALUE_SET) -> 2;
			// concept maps can depend on codesystems and valuesets
			case String s when s.startsWith(CONCEPT_MAP) -> 3;
			default -> throw new NotImplementedException("Missing import order ranking implementation for " + path);
		};
	}

	private Resource readResource(FhirResourceParser resourceParser, Path pathToImport) {
		try (var reader = Files.newInputStream(pathToImport)) {
			return resourceParser.parseResource(reader);
		} catch (IOException e) {
			// TODO register the error in an error response and keep going? dependents?
			throw new SnowowlRuntimeException("Unable to convert FHIR JSON to resource", e);
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
					throw new SnowowlRuntimeException("Unable to create fhir-packages directory", e);
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
					context.log().info("Downloading FHIR package '{}' from registry '{}'", packageInfo, registry);
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