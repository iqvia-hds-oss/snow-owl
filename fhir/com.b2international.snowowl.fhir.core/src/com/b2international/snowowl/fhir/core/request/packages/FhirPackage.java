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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.hl7.fhir.r4.model.codesystems.OperationOutcome;

import com.b2international.commons.collections.Collections3;
import com.b2international.snowowl.core.api.SnowowlRuntimeException;
import com.b2international.snowowl.fhir.core.exceptions.BadRequestException;
import com.b2international.snowowl.fhir.core.exceptions.FhirException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 10.1.0
 */
public final class FhirPackage {

	static final String PACKAGE_FOLDER = "package/";
	static final String PACKAGE_JSON = "package.json";
	static final String PACKAGE_JSON_PATH = PACKAGE_FOLDER + PACKAGE_JSON;
	
	private final Path packageFile;
	private final FhirPackageJson packageJson;
	private final SortedSet<String> recognizedEntries;

	private FhirPackage(final Path packageFile, final FhirPackageJson packageJson, final SortedSet<String> recognizedEntries) {
		this.packageFile = Objects.requireNonNull(packageFile, "'packageFile' may not be null");
		this.packageJson = Objects.requireNonNull(packageJson, "'packageJson' may not be null");
		this.recognizedEntries = Collections3.toImmutableSortedSet(recognizedEntries);
	}

	public boolean hasRecognizedEntries() {
		return !recognizedEntries.isEmpty();
	}
	
	public FhirPackageJson getPackageJson() {
		return packageJson;
	}
	
	public void extractTo(Path target) {
		try {
			try (InputStream fileIn = Files.newInputStream(packageFile);
					GZIPInputStream gzipIn = new GZIPInputStream(fileIn);
						TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn);) {
				ArchiveEntry entry;
				while ((entry = tarIn.getNextEntry()) != null) {
					Path outFile = target.resolve(entry.getName());
					// [security] extract only the files we need, not everything
					if (recognizedEntries.contains(entry.getName())) {
						if (entry.isDirectory()) {
							Files.createDirectories(outFile);
						} else {
							Files.createDirectories(outFile.getParent());
							try (OutputStream out = Files.newOutputStream(outFile)) {
								tarIn.transferTo(out);
							}
						}
					}
				}
			}
		} catch (IOException e) {
			// XXX FhirException does not allow cause to be specified, so we print out here if we ever fail to extract a package
			e.printStackTrace();
			throw new FhirException(String.format("Failed to extract package '%s'", packageFile.getFileName().toString()), OperationOutcome.MSGERRORPARSING);
		}
	}
	
	/**
	 * Parse the given package file into a {@link FhirPackage} representation. If the received file is not a tar.gz file, or it does not contain a
	 * package.json under the package folder then parsing fails with an error message. Otherwise the method will return a {@link FhirPackage} instance
	 * where based on the given file filter recognized entries are registered for extraction and further processing. instance.
	 * 
	 * @param packageFile - the file to process
	 * @param mapper - mapper to use for package.json processing
	 * @param fileFilter - the filter to use when iterating through the tar file's contents
	 */
	public static FhirPackage parse(Path packageFile, ObjectMapper mapper, Predicate<String> fileFilter) {
		try (InputStream fileIn = Files.newInputStream(packageFile);
				GZIPInputStream gzipIn = new GZIPInputStream(fileIn);
					TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn);) {
			
			FhirPackageJson packageJson = null;
			final SortedSet<String> recognizedEntries = new TreeSet<>();
			ArchiveEntry entry;
			while ((entry = tarIn.getNextEntry()) != null) {
				var packageEntryPath = entry.getName();

				var packageEntryName = packageEntryPath.replace(PACKAGE_FOLDER, "");
				
				// always recognize the package.json file
				
				if (PACKAGE_JSON_PATH.equals(packageEntryPath)) {
					try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
						tarIn.transferTo(baos);
						packageJson = mapper.readValue(baos.toString(StandardCharsets.UTF_8), FhirPackageJson.class);
					} catch (IOException e) {
						throw new SnowowlRuntimeException("Failed to parse package.json contents into object", e);
					}
				} else if (fileFilter.test(packageEntryName)) {
					recognizedEntries.add(packageEntryPath);
				} else {
					// ignore everything that is not recognized
				}
			}
			
			if (packageJson == null) {
				throw new BadRequestException(String.format("'%s' file is not present in the selected FHIR package under the '%s' folder.", PACKAGE_JSON, PACKAGE_FOLDER));
			}
			
			return new FhirPackage(packageFile, packageJson, recognizedEntries);
			
		} catch (IOException e) {
			// XXX FhirException does not allow cause to be specified, so we print out here if we ever fail to parse a package
			e.printStackTrace();
			throw new FhirException(String.format("Failed to parse FHIR package contents '%s'", packageFile.getFileName().toString()),
					OperationOutcome.MSGERRORPARSING);
		}
	}
	
	@Override
	public String toString() {
		return packageFile.getFileName().toString();
	}
	
}