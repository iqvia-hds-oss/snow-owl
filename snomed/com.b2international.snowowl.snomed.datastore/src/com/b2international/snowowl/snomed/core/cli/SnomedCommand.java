/*
 * Copyright 2018-2025 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.snomed.core.cli;

import java.io.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.b2international.commons.exceptions.NotFoundException;
import com.b2international.commons.time.TimeUtil;
import com.b2international.index.Hits;
import com.b2international.index.query.Expressions;
import com.b2international.index.query.Query;
import com.b2international.index.revision.Commit;
import com.b2international.index.revision.RevisionBranch;
import com.b2international.index.revision.RevisionBranchPoint;
import com.b2international.index.revision.RevisionIndex;
import com.b2international.index.util.JsonDiff;
import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.RepositoryManager;
import com.b2international.snowowl.core.api.SnowowlRuntimeException;
import com.b2international.snowowl.core.attachments.AttachmentRegistry;
import com.b2international.snowowl.core.codesystem.CodeSystem;
import com.b2international.snowowl.core.codesystem.CodeSystemRequests;
import com.b2international.snowowl.core.console.Command;
import com.b2international.snowowl.core.console.CommandLineStream;
import com.b2international.snowowl.core.date.Dates;
import com.b2international.snowowl.core.identity.Permission;
import com.b2international.snowowl.core.identity.User;
import com.b2international.snowowl.core.plugin.Component;
import com.b2international.snowowl.core.repository.RevisionDocument;
import com.b2international.snowowl.core.request.io.ImportResponse;
import com.b2international.snowowl.snomed.cis.ISnomedIdentifierService;
import com.b2international.snowowl.snomed.cis.InternalSnomedIdentifierService;
import com.b2international.snowowl.snomed.cis.domain.IdentifierStatus;
import com.b2international.snowowl.snomed.cis.domain.SctId;
import com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants;
import com.b2international.snowowl.snomed.core.domain.Rf2ReleaseType;
import com.b2international.snowowl.snomed.datastore.SnomedDatastoreActivator;
import com.b2international.snowowl.snomed.datastore.index.entry.SnomedComponentDocument;
import com.b2international.snowowl.snomed.datastore.index.entry.SnomedConceptDocument;
import com.b2international.snowowl.snomed.datastore.index.entry.SnomedDescriptionIndexEntry;
import com.b2international.snowowl.snomed.datastore.index.entry.SnomedRelationshipIndexEntry;
import com.b2international.snowowl.snomed.datastore.request.SnomedRequests;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Stopwatch;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import picocli.CommandLine;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * @since 7.0
 */
@Component
@picocli.CommandLine.Command(
	name = "snomed",
	header = "Provides subcommands to manage SNOMED CT content",
	description = "Provides subcommands to manage SNOMED CT content",
	subcommands = {
		HelpCommand.class,
		SnomedCommand.ImportCommand.class,
		SnomedCommand.IdentifiersCommand.class,
		SnomedCommand.SyncIdentifiersCommand.class,
		SnomedCommand.RevisionCheckCommand.class
	}
)
public final class SnomedCommand extends Command {

	@Override
	public void run(CommandLineStream out) {
		CommandLine.usage(this, (PrintStream) out);
	}
	
	@picocli.CommandLine.Command(
		name = "import",
		header = "Imports SNOMED CT content",
		description = "Imports SNOMED CT content"
	)
	private static final class ImportCommand extends Command {
		
		private static final String SUPPORTED_FORMAT = "rf2";
		
		@Option(names = { "-b", "--branch" }, description = "The target branch. After a successful import all importable content will be accessible from this branch.", defaultValue = "SNOMEDCT/HEAD", required = true)
		String branch = SnomedTerminologyComponentConstants.SNOMED_SHORT_NAME;
		
		@Option(names = { "-f", "--format" }, description = "The import file format. Currently 'rf2' is supported only.", defaultValue = SUPPORTED_FORMAT)
		String format = SUPPORTED_FORMAT;
		
		@Option(names = { "-t" }, description = "The importable release type from an RF2 compatible file.", defaultValue = "FULL")
		Rf2ReleaseType rf2ReleaseType = Rf2ReleaseType.FULL;
		
		@Option(names = { "-v" }, description = "Whether to create versions for the underlying code system or just import the content.", defaultValue = "true")
		boolean createVersions;
		
		@Parameters(paramLabel = "PATH", description = "The absolute path to the importable file")
		String path;
		
		@Override
		public void run(CommandLineStream out) {
			if (!SUPPORTED_FORMAT.equalsIgnoreCase(format)) {
				out.println("Unrecognized import format: '%s'. Supported formats are: %s", format, SUPPORTED_FORMAT);
			}
			
			final User user = out.authenticate(getBus());
			
			if (user == null || !user.hasPermission(Permission.toImport(SnomedDatastoreActivator.REPOSITORY_UUID, branch))) {
				out.println("User is unauthorized to import SNOMED CT content.");
				return;
			}
			
			UUID rf2ArchiveId = UUID.randomUUID();
			try (FileInputStream in = new FileInputStream(new File(path))) {
				ApplicationContext.getServiceForClass(AttachmentRegistry.class).upload(rf2ArchiveId, in);
			} catch (IOException e) {
				if (e instanceof FileNotFoundException) {
					out.println("Cannot find the path specified. '%s'", path);
				} else {
					out.println("Error reading the path specified. '%s'. Message: '%s'", path, e.getMessage());
				}
				return;
			}
			
			final ImportResponse response = SnomedRequests.rf2().prepareImport()
					.setCreateVersions(createVersions)
					.setRf2ArchiveId(rf2ArchiveId)
					.setReleaseType(rf2ReleaseType)
					.build(SnomedDatastoreActivator.REPOSITORY_UUID, branch)
					.execute(getBus())
					.getSync();
			
			if (response.isSuccess()) {
				out.println("Successfully imported SNOMED CT content from file '%s'.", path);
			} else {
				out.println("Failed to import SNOMED CT content from file '%s'. %s", path, response.getError());
			}
		}
	}
	
	@picocli.CommandLine.Command(
			name = "identifiers",
			header = "Collects SNOMED CT core component identifiers",
			description = "Collect SNOMED CT identifiers"
			)
	private static final class IdentifiersCommand extends Command {
				
		private static final Set<String> ALL_SCTID_STATUSES = FluentIterable.from(IdentifierStatus.values()).transform(IdentifierStatus::getSerializedName).toSet();
				
		@Option(names = { "-s", "--status" }, description = "The SctId status to filter for. Can be Available, Reserved, Assigned, or Published.", defaultValue = "All", required = false)
		String status="All";
		
		@Parameters(paramLabel = "PATH", description = "The absolute path of the output folder")
		String path;
		
		
		@Override
		public void run(CommandLineStream out) {
			ISnomedIdentifierService identifierService = getContext().service(ISnomedIdentifierService.class);
			
			if (!(identifierService instanceof InternalSnomedIdentifierService)) {
				out.println("The current Component Identifier Service does not support listing SNOMED CT identifiers via this command");
				return;
			}
						
			Set<String> statuses = Sets.newHashSet();
			if (ALL_SCTID_STATUSES.contains(status)) {
				statuses.add(status);
			} else {
				statuses.addAll(ALL_SCTID_STATUSES);
			}
			
			((InternalSnomedIdentifierService) identifierService).cisStore().read( searcher -> {
				statuses.forEach( status -> {
					Query<String> idQuery = Query.select(String.class)
							.from(SctId.class)
							.fields("sctid")
							.where(SctId.Expressions.status(status))
							.limit(100_000)
							.build();
					
					File idReport = new File(String.format("%s/%sIds_%s.txt", path, status, Dates.now("yyyyMMdd_kkmmss")));
					
					try (FileOutputStream outputStream = new FileOutputStream(idReport)) {
						try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, "UTF-8")) {
							searcher.scroll(idQuery).forEach( hits -> {
								hits.forEach( id -> {
									try {
										outputStreamWriter.append(String.format("%s\n", id));
									} catch (Exception e) {
										handleException(e, out);
									}
								});
							});
						}
					} catch (IOException e) {
						handleException(e, out);
					}
				});
		        			    
			    return null;
			});			
		}
		
		private void handleException(Exception e, CommandLineStream out) {
			out.println("An error occurred while exporting SctIds.");
			throw SnowowlRuntimeException.wrap(e);
		}
		
	}
	
	@picocli.CommandLine.Command(
			name = "sync-ids",
			header = "Synchronizes SNOMED CT core component identifiers with the built-in CIS store",
			description = "Synchronize SNOMED CT identifiers"
			)
	private static final class SyncIdentifiersCommand extends Command {
				
		@Override
		public void run(CommandLineStream out) {
			
			Stopwatch watch = Stopwatch.createStarted();
			
			ISnomedIdentifierService identifierService = getContext().service(ISnomedIdentifierService.class);
			
			if (!(identifierService instanceof InternalSnomedIdentifierService)) {
				out.println("The current Component Identifier Service is not a local store, synchronization is not available");
				return;
			}
			
			out.println("Collecting published SNOMED component identifiers ...");
			
			Set<String> publishedIds = Sets.newHashSet();
			
			((InternalSnomedIdentifierService) identifierService).cisStore().read( searcher -> {
					
				Query<String> idQuery = Query.select(String.class)
						.from(SctId.class)
						.fields("sctid")
						.where(SctId.Expressions.status(IdentifierStatus.PUBLISHED.getSerializedName()))
						.limit(100_000)
						.build();
				
				searcher.scroll(idQuery).forEach( hits -> {
					hits.forEach(publishedIds::add);
					if (publishedIds.size() % 1_000_000 == 0) {
						out.println("\tcollected " + publishedIds.size() + " identifiers ...");
					}
				});
				
			    return null;
			    
			});
			
			out.println("");
			out.println("Collecting assigned SNOMED component identifiers ...");
			
			Set<String> assignedIds = Sets.newHashSet();
			
			((InternalSnomedIdentifierService) identifierService).cisStore().read( searcher -> {
					
				Query<String> idQuery = Query.select(String.class)
						.from(SctId.class)
						.fields("sctid")
						.where(SctId.Expressions.status(IdentifierStatus.ASSIGNED.getSerializedName()))
						.limit(100_000)
						.build();
				
				searcher.scroll(idQuery).forEach( hits -> {
					hits.forEach(assignedIds::add);
					if (assignedIds.size() % 1_000_000 == 0) {
						out.println("\tcollected " + assignedIds.size() + " identifiers ...");
					}
				});
				
			    return null;
			    
			});
			
			out.println("");
			out.println("Total number of published identifiers in the local store: " + publishedIds.size());
			out.println("Total number of assigned identifiers in the local store: " + assignedIds.size());
			
			out.println("");
			out.println("Collecting SNOMED component identifiers not yet registered ...");
			
			List<Class<? extends SnomedComponentDocument>> types = ImmutableList.of(
					SnomedConceptDocument.class,
					SnomedDescriptionIndexEntry.class, 
					SnomedRelationshipIndexEntry.class
				);
			
			List<CodeSystem> codeSystems = CodeSystemRequests.prepareSearchCodeSystem()
					.all()
					.build(SnomedDatastoreActivator.REPOSITORY_UUID)
					.execute(getBus())
					.getSync()
					.stream()
					.sorted( (cs1,cs2) -> Ints.compare(cs1.getShortName().length(), cs2.getShortName().length()))
					.collect(Collectors.toList());
				
			out.println("");
			out.println("Found the following SNOMED code systems:");
			codeSystems.forEach(cs -> out.println("\t" + cs.getShortName() + " -> " + cs.getBranchPath()));
			out.println("");
			out.println("");
			
			Set<String> allReleasedSnomedComponentIdentifiers = Sets.newHashSet();
			Set<String> allUnreleasedSnomedComponentIdentifiers = Sets.newHashSet();
			
			RevisionIndex index = getContext().service(RepositoryManager.class)
					.get(SnomedDatastoreActivator.REPOSITORY_UUID)
					.service(RevisionIndex.class);
			
			for (CodeSystem codeSystem : codeSystems) {
				
				Set<String> releasedCodeSystemIdentifiers = Sets.newHashSet();
				Set<String> unreleasedCodeSystemIdentifiers = Sets.newHashSet();
				
				index.read(codeSystem.getBranchPath(), searcher -> {
					
					for (Class<? extends SnomedComponentDocument> type : types) {
						
						out.println("Collecting identifiers of '" + type.getSimpleName() + "(s)' using branch '" + codeSystem.getBranchPath() + "' ...");
						
						Query<String[]> idQuery = Query.select(String[].class)
								.from(type)
								.fields(SnomedComponentDocument.Fields.ID, SnomedComponentDocument.Fields.RELEASED)
								.where(Expressions.matchAll())
								.limit(100_000)
								.build();
						
						int count = 0;
						
						for (Hits<String[]> hits : searcher.scroll(idQuery)) {
							
							for (String[] hit : hits) {
								
								String id = hit[0];
								String released = hit[1];
								
								if (Boolean.valueOf(released)) {
									releasedCodeSystemIdentifiers.add(id);
								} else {
									unreleasedCodeSystemIdentifiers.add(id);
								}
								
							}
							
							count+=hits.getHits().size();
							
							if (hits.getTotal() < 1_000_000 || count == hits.getTotal()) {
								out.println("\tprocessed " + count + " / " + hits.getTotal() + " identifiers");
							} else if (count % 1_000_000 == 0) {
								out.println("\tprocessed " + count + " / " + hits.getTotal() + " identifiers");
							}
							
						}
						
					}
					
					return null;
					
				});
				
				out.println("");
				out.println("Total number of released identifiers in '" + codeSystem.getShortName() + "': " + releasedCodeSystemIdentifiers.size());
				out.println("Total number of unreleased identifiers in '" + codeSystem.getShortName() + "': " + unreleasedCodeSystemIdentifiers.size());
				out.println("");
				
				allReleasedSnomedComponentIdentifiers.addAll(releasedCodeSystemIdentifiers);
				allUnreleasedSnomedComponentIdentifiers.addAll(unreleasedCodeSystemIdentifiers);
			
			}
			
			out.println("");
			out.println("Total number of released identifiers in all SNOMED code systems: " + allReleasedSnomedComponentIdentifiers.size());
			out.println("Total number of unreleased identifiers in all SNOMED code systems: " + allUnreleasedSnomedComponentIdentifiers.size());
			out.println("");
			
			Set<String> notRegisteredPublishedIds = Sets.difference(allReleasedSnomedComponentIdentifiers, publishedIds);
			
			if (!notRegisteredPublishedIds.isEmpty()) {
				
				int publishCount = 0;
				
				for (List<String> ids : Iterables.partition(notRegisteredPublishedIds, 100_000)) {
					
					SnomedRequests.identifiers()
						.preparePublish()
						.setComponentIds(ids)
						.buildAsync()
						.execute(getBus())
						.getSync();
					
					publishCount+=ids.size();
					
					out.println("Publishing ids (" + publishCount + " / " + notRegisteredPublishedIds.size() + ") ...");
					
				}
				
			} else {
				out.println("All existing released SNOMED identifiers are present in the local identifier service");
			}
			
			Set<String> notRegisteredUnpublishedIds = Sets.difference(allUnreleasedSnomedComponentIdentifiers, assignedIds);
			
			if (!notRegisteredUnpublishedIds.isEmpty()) {
				
				int unpublishCount = 0;
				
				for (List<String> ids : Iterables.partition(notRegisteredUnpublishedIds, 100_000)) {
					
					SnomedRequests.identifiers()
						.prepareRegister()
						.setComponentIds(ids)
						.buildAsync()
						.execute(getBus())
						.getSync();
					
					unpublishCount+=ids.size();
					
					out.println("Registering ids (" + unpublishCount + " / " + notRegisteredUnpublishedIds.size() + ") ...");
					
				}
				
			} else {
				out.println("All existing unreleased SNOMED identifiers are present in the local identifier service");
			}

			out.println("");
			out.println("Execution took: " + TimeUtil.toString(watch));
			out.println("");
			
		}
		
	}
	
	@picocli.CommandLine.Command(
		name = "revision",
		header = "Prints revision information about a SNOMED CT concept",
		description = "Prints revision information about a SNOMED CT concept"
	)
	private static final class RevisionCheckCommand extends Command {
		
		@Parameters(index = "0", paramLabel = "BRANCH", description = "The branch to filter the revisions")
		String branch;
		
		@Parameters(index = "1", paramLabel = "CONCEPT_ID", description = "The concept id to show revisions for")
		String conceptId;
		
		@Option(names = {"-p", "--pretty"}, description = "To make the output pretty print all JSON objects")
		boolean pretty = false;
		
		@Override
		public void run(CommandLineStream out) {
			RevisionIndex index = getContext().service(RepositoryManager.class).get("snomedStore").service(RevisionIndex.class);
			index.read(branch, searcher -> {
				out.println("Fetching revisions visible from branch...");

				Hits<JsonNode> hits = searcher.search(Query.select(JsonNode.class)
						.from(SnomedConceptDocument.class)
						.where(RevisionDocument.Expressions.id(conceptId))
						.limit(Integer.MAX_VALUE)
						.build());
				
				hits.forEach(rev -> {
					out.println(pretty ? rev.toPrettyString() : rev.toString());
				});
				
				if (hits.getTotal() > 1) {
					out.println("Found more than revision for the diagnosed document, fetching all revisions in chronological order:");
					
					if (hits.getTotal() == 2) {
						out.println("Performing a diff between the two revisions:");
						Iterator<JsonNode> it = hits.iterator();
						JsonDiff.diff(it.next(), it.next()).forEach(change -> {
							out.println("\t%s", change);
						});
					} else {
						out.println("More than two revisions found for the same ID, skipping diff generation.");
					}
					
					searcher.searcher().search(Query.select(JsonNode.class)
								.from(SnomedConceptDocument.class)
								.where(RevisionDocument.Expressions.id(conceptId))
								.limit(Integer.MAX_VALUE)
								.build())
						.stream()
						.sorted((j1, j2) -> {
							var j1Created = RevisionBranchPoint.valueOf(j1.get("created").asText());
							var j2Created = RevisionBranchPoint.valueOf(j2.get("created").asText());
							return Longs.compare(j1Created.getTimestamp(), j2Created.getTimestamp());
						})
						.forEach(c -> {
							var branchPoint = RevisionBranchPoint.valueOf(c.get("created").asText());
							try {
								RevisionBranch branch = index.branching().getBranch(branchPoint.getBranchId());
								out.println("%s - %s - %s", Instant.ofEpochMilli(branchPoint.getTimestamp()).atOffset(ZoneOffset.UTC).toLocalDateTime(), branch.getPath(), c);
								// fetch commit detail for each revision to see what's registered in the commit object for compare and merge processes
								searcher.search(Query.select(Commit.class)
										.where(Commit.Expressions.timestamp(branchPoint.getTimestamp()))
										.limit(1)
										.build())
										.stream()
										.findFirst()
										.ifPresent(commit -> {
											out.println("\t-> %s pushed commit '%s' with details '%s'", commit.getAuthor(), commit.getComment(), commit.getDetailsByObject(conceptId));
										});
							} catch (Exception e) {
								if (e instanceof NotFoundException) {
									// unknown branch, probably something that was a test or had to be removed, ignore revision
								} else {
									// print error otherwise
									out.print(e);
								}
							}
						});
				}
				
				return null;
			});			
		}
	}
}
