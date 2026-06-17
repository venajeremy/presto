/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.iceberg;

import com.facebook.presto.Session;
import com.facebook.presto.testing.QueryRunner;
import com.facebook.presto.tests.AbstractTestQueryFramework;
import com.google.common.collect.ImmutableMap;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonDecoder;
import org.apache.avro.io.JsonEncoder;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterClass;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;

import static java.lang.String.format;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.Files.readAllBytes;
import static java.nio.file.Files.write;

public abstract class IcebergImportedTableTestBase
        extends AbstractTestQueryFramework
{
    protected static final String ICEBERG_V3 = "iceberg_v3";

    private static final String METADATA = "metadata";
    private static final String PATHPLACEHOLDER = "FILEPATH";
    private QueryRunner queryRunner;

    @Override
    protected QueryRunner createQueryRunner()
            throws Exception
    {
        queryRunner = IcebergQueryRunner.builder().setExtraProperties(ImmutableMap.of(
                "experimental.pushdown-subfields-enabled", "true",
                "experimental.pushdown-dereference-enabled", "true")).build().getQueryRunner();

        createSchema(ICEBERG_V3, queryRunner);

        return queryRunner;
    }

    @AfterClass
    public void deleteSchema()
    {
        QueryRunner queryRunner = getQueryRunner();
        if(queryRunner != null) {
            dropSchema(ICEBERG_V3, queryRunner);
        }
    }

    private static void createSchema(String schema, QueryRunner queryRunner)
    {
        if (queryRunner != null) {
            queryRunner.execute(format(
                    "CREATE SCHEMA IF NOT EXISTS iceberg.%s",
                    schema));
        }
    }

    private static void dropSchema(String schema, QueryRunner queryRunner)
    {
        if (queryRunner != null) {
            queryRunner.execute(format(
                    "DROP SCHEMA IF EXISTS iceberg.%s",
                    schema));
        }
    }

    protected String setupAndRegisterTable(String catalogName, String testName)
    {
        String tablePath = goldenTablePathWithPrefix(catalogName, testName);

        // Create temp directory
        File tempDirectory = null;
        String activeTableParent = null;

        try {
            tempDirectory = createTempDirectory("IcebergTemporaryTable").toFile();
            File tempTable = new File(tempDirectory, testName);
            FileUtils.copyDirectory(new File(tablePath), tempTable);

            // Save temp directory and table
            activeTableParent = tempDirectory.getAbsolutePath();
            String activeTable = tempTable.getAbsolutePath();

            File tempMetadata = new File(tempTable, METADATA);

            if(!tempMetadata.isDirectory()){
                throw new RuntimeException("Metadata folder does not exist in iceberg table at: "+tempDirectory);
            }

            // Update all .avro files
            File[] avroFiles = tempMetadata.listFiles((dir, name) -> name.endsWith(".avro"));
            for (File avroFile : avroFiles) {
                // Load avro files
                try (DataFileReader<GenericRecord> reader = new DataFileReader<>(avroFile, new GenericDatumReader<>())) {
                    // Convert avro to json
                    String json = avroToJson(reader);

                    // String replace
                    json = json.replace(PATHPLACEHOLDER, activeTable);

                    // Convert json to avro and update existing avroFile
                    jsonToAvro(json, reader, avroFile.getAbsolutePath());
                }
            }

            // Update all .metadata.json files
            File[] jsonFiles = tempMetadata.listFiles((dir, name) -> name.endsWith(".json"));
            for (File jsonFile : jsonFiles) {
                // Use java Files to read file
                String fileContent = new String(readAllBytes(jsonFile.toPath()));
                // Replace the placeholder with absolute path
                fileContent = fileContent.replace(PATHPLACEHOLDER, activeTable);
                // Write json back to file
                write(jsonFile.toPath(), fileContent.getBytes());
            }

            // Add table to schema
            Session session = Session.builder(getSession()).build();
            String queryCreate = format("call iceberg.system.register_table( " +
                    "schema => '%s', " +
                    "table_name => '%s', " +
                    "metadata_location => 'file://%s'" +
                    ")", catalogName, testName, activeTable);
            computeActual(session, queryCreate);

            return activeTableParent;
        }

        catch (Exception e) {
            // Delete temp directory
            dropAndCleanupTable(catalogName, testName, activeTableParent);
            throw new RuntimeException(e);
        }
    }

    protected void dropAndCleanupTable(String catalogName, String testName, String activeTableDirectory)
    {
        // Remove table from schema
        Session session = Session.builder(getSession()).build();
        String queryDrop = format("drop table if exists %s.%s", catalogName, testName);
        computeActual(session, queryDrop);

        // Delete table from temp directory
        if (activeTableDirectory != null) {
            try {
                FileUtils.deleteDirectory(new File(activeTableDirectory));
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void jsonToAvro(String json, DataFileReader<GenericRecord> reader, String avroAbsolutePath)
    {
        try {
            // Get avro file schema
            Schema schema = reader.getSchema();

            JsonDecoder decoder = DecoderFactory.get().jsonDecoder(schema, json);
            DatumReader<GenericRecord> datumReader = new GenericDatumReader<>(schema);
            GenericRecord updated = datumReader.read(null, decoder);

            try (DataFileWriter<GenericRecord> fileWriter = new DataFileWriter<>(new GenericDatumWriter<>(schema))) {
                fileWriter.create(schema, new File(avroAbsolutePath));
                fileWriter.append(updated);
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String avroToJson(DataFileReader<GenericRecord> reader)
    {
        try {
            // Get avro file schema
            Schema schema = reader.getSchema();

            // Convert to JSON
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DatumWriter<GenericRecord> writer = new GenericDatumWriter<>(schema);

            // Create JSON encoder
            JsonEncoder encoder = EncoderFactory.get().jsonEncoder(schema, baos, true);

            while(reader.hasNext()){
                GenericRecord record = reader.next();
                writer.write(record, encoder);
                encoder.flush();
            }

            return baos.toString();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static String goldenTablePath(String tableName)
    {
        String path = IcebergImportedTableTestBase.class.getClassLoader().getResource(tableName).getPath();
        if(path == null){
            throw new RuntimeException("Failed to located path for resource: "+tableName);
        }
        return path;
    }

    protected static String goldenTablePathWithPrefix(String prefix, String tableName)
    {
        return goldenTablePath(prefix + FileSystems.getDefault().getSeparator() + tableName);
    }
}
