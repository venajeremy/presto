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
package com.facebook.presto.nativetests.iceberg;

import com.facebook.presto.Session;
import com.facebook.presto.geospatial.type.GeometryType;
import com.facebook.presto.iceberg.IcebergImportedTableTestBase;
import com.facebook.presto.testing.MaterializedResult;
import com.facebook.presto.testing.QueryRunner;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.facebook.presto.nativeworker.PrestoNativeQueryRunnerUtils.ICEBERG_DEFAULT_STORAGE_FORMAT;
import static com.facebook.presto.nativeworker.PrestoNativeQueryRunnerUtils.nativeIcebergQueryRunnerBuilder;
import static java.lang.String.format;
import static org.testng.Assert.assertEquals;

/**
 * Tests for Iceberg Format Version 3 Geometry Type Read Using IcebergImportedTableTestBase to Create Table With Existing Values
 */
public class TestIcebergV3GeometryRead
        extends IcebergImportedTableTestBase
{
    private final String testName = "geometry_data_type_read";
    private String tablePath;

    @Override
    protected QueryRunner chooseQueryRunner() throws Exception
    {
        return nativeIcebergQueryRunnerBuilder()
                .setStorageFormat(ICEBERG_DEFAULT_STORAGE_FORMAT)
                .setAddStorageFormatToPath(true)
                .build();
    }

    @BeforeMethod
    public void setup()
    {
        tablePath = setupAndRegisterTable(testName);
    }

    @AfterMethod(alwaysRun = true)
    public void teardown()
    {
        dropAndCleanupTable(testName, tablePath);
    }

    @Test
    public void nativeReadGeometryType()
    {
        // Create session
        Session session = Session.builder(getSession()).build();

        // Assert schema creation
        String querySchema = format("SELECT 1 FROM iceberg.information_schema.schemata WHERE schema_name = '%s'", SCHEMANAME);
        MaterializedResult resultSchema = computeActual(session, querySchema);
        assertEquals(resultSchema.getMaterializedRows().get(0).getField(0), 1);

        // Assert table creation
        String queryTable = format("SELECT 1 FROM iceberg.information_schema.tables WHERE table_schema = '%s' AND table_name = '%s'", SCHEMANAME, testName);
        MaterializedResult resultTable = computeActual(session, queryTable);
        assertEquals(resultTable.getMaterializedRows().get(0).getField(0), 1);

        // Read geometry type
        String querySelect = format("select * from iceberg.%s.%s", SCHEMANAME, testName);
        MaterializedResult resultSelect = computeActual(session, querySelect);

        // Confirm geometry read
        assertEquals(resultSelect.getTypes().get(1), GeometryType.GEOMETRY);
        assertEquals(resultSelect.getMaterializedRows().get(0).getField(1), "MULTIPOINT ((1 2))");
        assertEquals(resultSelect.getMaterializedRows().get(1).getField(1), "MULTIPOINT ((3 4))");
    }
}
