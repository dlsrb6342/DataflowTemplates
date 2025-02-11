/*
 * Copyright (C) 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.teleport.v2.cdc.mappers;

import com.google.api.services.bigquery.model.TableRow;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.teleport.v2.cdc.merge.MergeInfo;
import com.google.cloud.teleport.v2.values.DatastreamRow;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.apache.beam.sdk.metrics.Counter;
import org.apache.beam.sdk.metrics.Metrics;
import org.apache.beam.sdk.transforms.FlatMapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.apache.beam.vendor.guava.v32_1_2_jre.com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Class {@link MergeInfoMapper}. */
public class MergeInfoMapper
    extends PTransform<PCollection<KV<TableId, TableRow>>, PCollection<MergeInfo>> {

  public static final String METADATA_DELETED = "_metadata_deleted";
  public static final String METADATA_REPLICA_TABLE = "_metadata_table";
  public static final String JOB_ID_PREFIX = "datastream";

  private static final Logger LOG = LoggerFactory.getLogger(MergeInfoMapper.class);
  private String projectId;
  private String stagingDataset;
  private String stagingTable;
  private String replicaDataset;
  private String replicaTable;

  private final Counter foregoneMerges = Metrics.counter(MergeInfoMapper.class, "mergesForegone");

  public MergeInfoMapper(
      String projectId,
      String stagingDataset,
      String stagingTable,
      String replicaDataset,
      String replicaTable) {
    this.projectId = projectId;
    this.stagingDataset = stagingDataset;
    this.stagingTable = stagingTable;

    this.replicaDataset = replicaDataset;
    this.replicaTable = replicaTable;
  }

  @Override
  public PCollection<MergeInfo> expand(PCollection<KV<TableId, TableRow>> input) {
    return input.apply(
        FlatMapElements.into(TypeDescriptor.of(MergeInfo.class))
            .via(
                element -> {
                  try {
                    TableId tableId = element.getKey();
                    DatastreamRow row = DatastreamRow.of(element.getValue());

                    String streamName = row.getStreamName();
                    String schemaName = row.getSchemaName();
                    String tableName = row.getTableName();

                    List<String> allPkFields = row.getPrimaryKeys();
                    List<String> allSortFields = row.getSortFields();

                    if (allPkFields.size() == 0) {
                      LOG.warn(
                          "Unable to retrieve primary keys for table {}.{} in stream {}. "
                              + "Not performing merge-based consolidation.",
                          schemaName,
                          tableName,
                          streamName);
                      foregoneMerges.inc();
                      return Lists.newArrayList();
                    } else if (allSortFields.size() == 0) {
                      LOG.warn(
                          "Unable to retrieve sort keys for table {}.{} in stream {}. "
                              + "Not performing merge-based consolidation.",
                          schemaName,
                          tableName,
                          streamName);
                      foregoneMerges.inc();
                    }

                    String formattedReplicaDataset =
                        row.formatStringTemplateForBigQueryDataset(replicaDataset);
                    String formattedReplicaTable =
                        row.formatStringTemplateForBigQuery(replicaTable);

                    MergeInfo mergeInfo =
                        MergeInfo.create(
                            projectId,
                            allPkFields,
                            allSortFields,
                            METADATA_DELETED,
                            TableId.of(
                                projectId,
                                row.formatStringTemplateForBigQueryDataset(stagingDataset),
                                row.formatStringTemplateForBigQuery(stagingTable)),
                            TableId.of(projectId, formattedReplicaDataset, formattedReplicaTable),
                            getJobId(projectId, formattedReplicaDataset, formattedReplicaTable));

                    return Lists.newArrayList(mergeInfo);
                  } catch (Exception e) {
                    LOG.error(
                        "Merge Info Failure, skipping merge for: {} -> {}",
                        element.getValue().toString(),
                        e.toString());
                    return Lists.newArrayList();
                  }
                }));
  }

  String getJobId(String projectId, String dataset, String table) {
    DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ssz").withZone(ZoneId.of("UTC"));
    return String.format(
        "%s_%s_%s_%s_%s_%s",
        JOB_ID_PREFIX,
        projectId,
        dataset,
        table,
        formatter.format(Instant.now()),
        UUID.randomUUID().toString());
  }
}
