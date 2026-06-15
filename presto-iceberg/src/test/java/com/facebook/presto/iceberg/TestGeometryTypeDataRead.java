package com.facebook.presto.iceberg;

import com.facebook.presto.Session;
import com.facebook.presto.geospatial.type.GeometryType;
import com.facebook.presto.testing.MaterializedResult;
import org.testng.annotations.*;

import static java.lang.String.format;
import static org.testng.AssertJUnit.assertEquals;

public class TestGeometryTypeDataRead extends IcebergImportedTableTestBase {

    private final String testName = "geometryDataTypeRead";
    private String tablePath;

    @BeforeMethod
    public void setup() {
        tablePath = setupIcebergTable(ICEBERG_V3, testName);
    }

    @AfterMethod(alwaysRun = true)
    public void teardown() {
        closeIcebergTable(ICEBERG_V3, testName, tablePath);
    }

    @Test
    public void readGeometryDataTypeFromImportedTable(){

        // Create session
        Session session = Session.builder(getSession()).build();

        // Read geometry type
        String querySelect = format("select * from iceberg.%s.%s",
                ICEBERG_V3, testName);
        MaterializedResult resultSelect = computeActual(session, querySelect);

        // Confirm geometry read
        assertEquals(resultSelect.getTypes().get(1), GeometryType.GEOMETRY);
        assertEquals(resultSelect.getMaterializedRows().get(0).getField(1),"MULTIPOINT ((1 2))");

    }
}
