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
import com.facebook.presto.geospatial.type.GeometryType;
import com.facebook.presto.testing.MaterializedResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static java.lang.String.format;
import static org.testng.Assert.assertEquals;

public class TestGeometryTypeDataRead
        extends IcebergImportedTableTestBase
{
    private final String testName = "geometry_data_type_read";
    private String tablePath;

    @BeforeMethod
    public void setup()
    {
        tablePath = setupAndRegisterTable(ICEBERG_V3, testName);
    }

    @AfterMethod(alwaysRun = true)
    public void teardown()
    {
        dropAndCleanupTable(ICEBERG_V3, testName, tablePath);
    }

    @Test
    public void readGeomDataType()
    {
        // Create session
        Session session = Session.builder(getSession()).build();

        // Assert schema creation
        String querySchema = format("SELECT 1 FROM iceberg.information_schema.schemata WHERE schema_name = '%s'", ICEBERG_V3);
        MaterializedResult resultSchema = computeActual(session, querySchema);
        assertEquals(resultSchema.getMaterializedRows().get(0).getField(0), 1);

        // Assert table creation
        String queryTable = format("SELECT 1 FROM iceberg.information_schema.tables WHERE table_schema = '%s' AND table_name = '%s'", ICEBERG_V3, testName);
        MaterializedResult resultTable = computeActual(session, queryTable);
        assertEquals(resultTable.getMaterializedRows().get(0).getField(0), 1);

        // Read geometry type
        String querySelect = format("select * from iceberg.%s.%s", ICEBERG_V3, testName);
        MaterializedResult resultSelect = computeActual(session, querySelect);

        // Confirm geometry read
        assertEquals(resultSelect.getTypes().get(1), GeometryType.GEOMETRY);
        assertEquals(resultSelect.getMaterializedRows().get(0).getField(1), "MULTIPOINT ((1 2))");
        assertEquals(resultSelect.getMaterializedRows().get(1).getField(1), "MULTIPOINT ((3 4))");
    }
}
