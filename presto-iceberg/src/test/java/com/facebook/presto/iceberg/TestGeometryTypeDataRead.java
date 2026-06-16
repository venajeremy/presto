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
    private final String testName = "geometryDataTypeRead";
    private String tablePath;

    @BeforeMethod
    public void setup()
    {
        tablePath = setupIcebergTable(ICEBERG_V3, testName);
    }

    @AfterMethod(alwaysRun = true)
    public void teardown()
    {
        closeIcebergTable(ICEBERG_V3, testName, tablePath);
    }

    @Test
    public void readGeometryDataTypeFromImportedTable()
    {
        // Create session
        Session session = Session.builder(getSession()).build();

        // Read geometry type
        String querySelect = format("select * from iceberg.%s.%s", ICEBERG_V3, testName);
        MaterializedResult resultSelect = computeActual(session, querySelect);

        // Confirm geometry read
        assertEquals(resultSelect.getTypes().get(1), GeometryType.GEOMETRY);
        assertEquals(resultSelect.getMaterializedRows().get(0).getField(1), "MULTIPOINT ((1 2))");
    }
}
