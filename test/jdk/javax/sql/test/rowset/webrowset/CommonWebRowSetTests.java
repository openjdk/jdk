/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package test.rowset.webrowset;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import test.rowset.cachedrowset.CommonCachedRowSetTests;

import javax.sql.rowset.WebRowSet;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class CommonWebRowSetTests extends CommonCachedRowSetTests {

    protected final String XMLFILEPATH = System.getProperty("test.src", ".")
            + File.separatorChar + "xml" + File.separatorChar;
    protected final String COFFEE_ROWS_XML = XMLFILEPATH + "COFFEE_ROWS.xml";
    protected final String DELETED_COFFEE_ROWS_XML
            = XMLFILEPATH + "DELETED_COFFEE_ROWS.xml";
    protected final String MODFIED_DELETED_COFFEE_ROWS_XML
            = XMLFILEPATH + "MODFIED_DELETED_COFFEE_ROWS.xml";
    protected final String UPDATED_COFFEE_ROWS_XML
            = XMLFILEPATH + "UPDATED_COFFEE_ROWS.xml";
    protected final String INSERTED_COFFEE_ROWS_XML
            = XMLFILEPATH + "INSERTED_COFFEE_ROWS.xml";
    protected final String UPDATED_INSERTED_COFFEE_ROWS_XML
            = XMLFILEPATH + "UPDATED_INSERTED_COFFEE_ROWS.xml";
    private static final String ROWSET_VALIDATION_PROPERTY
            = "jdk.sql.rowset.webrowsetValidation";

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Runs the given action with the WebRowSet validation system property set
     * to the requested value, then restores the previous value.
     *
     * @param value the temporary property value, or {@code null} to clear it
     * @param action the action to run while the property is set
     */
    private static synchronized void withRowSetValidation(String value,
            ThrowingRunnable action) throws Exception {
        String previous = System.getProperty(ROWSET_VALIDATION_PROPERTY);
        try {
            if (value == null) {
                System.clearProperty(ROWSET_VALIDATION_PROPERTY);
            } else {
                System.setProperty(ROWSET_VALIDATION_PROPERTY, value);
            }
            action.run();
        } finally {
            if (previous == null) {
                System.clearProperty(ROWSET_VALIDATION_PROPERTY);
            } else {
                System.setProperty(ROWSET_VALIDATION_PROPERTY, previous);
            }
        }
    }

    /*
     * Utility method to write a WebRowSet XML file via an OutputStream
     */
    protected ByteArrayOutputStream writeWebRowSetWithOutputStream(WebRowSet rs) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            rs.writeXml(oos);
        }
        return baos;
    }

    /*
     * Utility method to write a WebRowSet XML file via an OutputStream
     * and populating the WebRowSet via a ResultSet
     */
    protected ByteArrayOutputStream writeWebRowSetWithOutputStream(ResultSet rs) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            WebRowSet wrs = rsf.createWebRowSet();
            wrs.writeXml(rs, oos);
        }
        return baos;
    }


    /*
     * Utility method to popoulate a WebRowSet via a InputStream
     */
    protected WebRowSet readWebRowSetWithOInputStream(ByteArrayOutputStream baos) throws Exception {
        WebRowSet wrs1 = rsf.createWebRowSet();
        try (ObjectInputStream ois
                = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            wrs1.readXml(ois);
        }
        return wrs1;
    }

    /*
     * Utility method to write a WebRowSet XML file via a Writer
     */
    protected ByteArrayOutputStream writeWebRowSetWithOutputStreamWithWriter(WebRowSet rs) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter osw = new OutputStreamWriter(baos);
        rs.writeXml(osw);
        return baos;
    }

    /*
     * Utility method to write a WebRowSet XML file via a Writer and populating
     * the WebRowSet via a ResultSet
     */
    protected ByteArrayOutputStream writeWebRowSetWithOutputStreamWithWriter(ResultSet rs) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter osw = new OutputStreamWriter(baos);
        WebRowSet wrs = rsf.createWebRowSet();
        wrs.writeXml(rs, osw);
        return baos;
    }

    /*
     * Utility method to popoulate a WebRowSet via a Readar
     */
    protected WebRowSet readWebRowSetWithOInputStreamWithReader(ByteArrayOutputStream baos) throws Exception {
        WebRowSet wrs1 = rsf.createWebRowSet();
        InputStreamReader isr = new InputStreamReader(new ByteArrayInputStream(baos.toByteArray()));
        wrs1.readXml(isr);
        return wrs1;
    }

    /*
     * Validate the expected Rows are contained within the RowSet
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("rowsetUsingCoffees")
    public void WebRowSetTest0000(WebRowSet wrs) throws Exception {
        assertArrayEquals(COFFEES_PRIMARY_KEYS, getPrimaryKeys(wrs));
        assertEquals(COFFEES_ROWS, wrs.size());
        wrs.close();
    }

    /*
     * Validate the expected Rows are contained within the RowSet
     * populated by readXML(Reader)
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("rowSetType")
    public void WebRowSetTest0001(WebRowSet wrs1) throws Exception {

        try (FileReader fr = new FileReader(COFFEE_ROWS_XML)) {
            wrs1.readXml(fr);
        }
        assertArrayEquals(COFFEES_PRIMARY_KEYS, getPrimaryKeys(wrs1));
        assertEquals(COFFEES_ROWS, wrs1.size());
        wrs1.close();

    }

    /*
     * Validate the expected Rows are contained within the RowSet
     * populated by readXML(InputStream)
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("rowSetType")
    public void WebRowSetTest0002(WebRowSet wrs1) throws Exception {
        try (FileInputStream fis = new FileInputStream(COFFEE_ROWS_XML)) {
            wrs1.readXml(fis);
        }
        assertArrayEquals(COFFEES_PRIMARY_KEYS, getPrimaryKeys(wrs1));
        assertEquals(COFFEES_ROWS, wrs1.size());
        wrs1.close();
    }

    /*
     * Write a WebRowSet via writeXML(OutputStream), read it
     * back via readXML(InputStream) and validate the primary  keys
     * are the same
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("rowsetUsingCoffees")
    public void WebRowSetTest0003(WebRowSet wrs) throws Exception {
        ByteArrayOutputStream baos = writeWebRowSetWithOutputStream(wrs);
        try (WebRowSet wrs1 = readWebRowSetWithOInputStream(baos)) {
            assertArrayEquals(COFFEES_PRIMARY_KEYS, getPrimaryKeys(wrs1));
            assertEquals(COFFEES_ROWS, wrs1.size());
        }
    }

    /*
     * Write a ResultSet via writeXML(OutputStream), read it
     * back via readXML(InputStream) and validate the primary  keys
     * are the same
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("rowsetUsingCoffees")
    public void WebRowSetTest0004(WebRowSet wrs) throws Exception {
        ResultSet rs = wrs;
        rs.beforeFirst();
        ByteArrayOutputStream baos = writeWebRowSetWithOutputStream(rs);
        try (WebRowSet wrs1 = readWebRowSetWithOInputStream(baos)) {
            assertArrayEquals(COFFEES_PRIMARY_KEYS, getPrimaryKeys(wrs1));
            assertEquals(COFFEES_ROWS, wrs1.size());
        }
    }

    /*
     * Write a WebRowSet via writeXML(Writer), read it
     * back via readXML(Reader) and validate the primary  keys
     * are the same
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("rowsetUsingCoffees")
    public void WebRowSetTest0005(WebRowSet wrs) throws Exception {
        ByteArrayOutputStream baos = writeWebRowSetWithOutputStreamWithWriter(wrs);
        try (WebRowSet wrs1 = readWebRowSetWithOInputStreamWithReader(baos)) {
            assertArrayEquals(COFFEES_PRIMARY_KEYS, getPrimaryKeys(wrs1));
            assertEquals(COFFEES_ROWS, wrs1.size());
        }
    }

    /*
     * Write a WebRowSet via writeXML(Writer), read it
     * back via readXML(Reader) and validate the primary  keys
     * are the same
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("rowsetUsingCoffees")
    public void WebRowSetTest0006(WebRowSet wrs) throws Exception {
        ResultSet rs = wrs;
        rs.beforeFirst();
        ByteArrayOutputStream baos = writeWebRowSetWithOutputStreamWithWriter(rs);
        try (WebRowSet wrs1 = readWebRowSetWithOInputStreamWithReader(baos)) {
            assertArrayEquals(COFFEES_PRIMARY_KEYS, getPrimaryKeys(wrs1));
            assertEquals(COFFEES_ROWS, wrs1.size());
        }
    }

    /*
     * Validate the expected Rows are contained within the RowSet
     * after deleting the specified rows
     */
    @Disabled
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("rowsetUsingCoffees")
    public void WebRowSetTest0007(WebRowSet wrs) throws Exception {
        assertArrayEquals(COFFEES_PRIMARY_KEYS, getPrimaryKeys(wrs));
        int[] rowsToDelete = {2, 4};
        assertArrayEquals(COFFEES_PRIMARY_KEYS, getPrimaryKeys(wrs));
        for (int row : rowsToDelete) {
            assertTrue(deleteRowByPrimaryKey(wrs, row, 1));
        }

        FileInputStream fis = new FileInputStream(MODFIED_DELETED_COFFEE_ROWS_XML);
        try (WebRowSet wrs1 = rsf.createWebRowSet()) {
            wrs1.readXml(fis);
            // With setShowDeleted(false) which is the default,
            // the deleted row should not be visible
            for (int row : rowsToDelete) {
                assertTrue(findRowByPrimaryKey(wrs1, row, 1));
            }
            assertTrue(wrs.size() == COFFEES_ROWS);
            // With setShowDeleted(true), the deleted row should be visible
            for (int row : rowsToDelete) {
                assertTrue(findRowByPrimaryKey(wrs, row, 1));
            }
        }
    }

    /*
     * Validate the expected Rows are contained within the RowSet
     * that was populated by reading an xml file with all rows
     * marked as a currentRow
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("rowSetType")
    public void WebRowSetTest0008(WebRowSet wrs1) throws Exception {
        FileInputStream fis = new FileInputStream(COFFEE_ROWS_XML);
        wrs1.readXml(fis);
        assertTrue(wrs1.size() == COFFEES_ROWS);
        assertArrayEquals(COFFEES_PRIMARY_KEYS, getPrimaryKeys(wrs1));
        // Validate that the rows are not marked as deleted, inserted or updated
        wrs1.beforeFirst();
        while (wrs1.next()) {
            assertFalse(wrs1.rowDeleted());
            assertFalse(wrs1.rowInserted());
            assertFalse(wrs1.rowUpdated());
        }
        wrs1.close();
    }

    /*
     * Read an XML file to populate a WebRowSet and validate that the rows
     * that are marked as deleted are marked as such in the WebRowSet
     * Also validate that they are or are not visible based on the
     * setShowDeleted value
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("rowSetType")
    public void WebRowSetTest0009(WebRowSet wrs1) throws Exception {
        int[] rowsToDelete = {2, 4};
        Object[] expectedRows = {1, 3, 5};
        FileInputStream fis = new FileInputStream(DELETED_COFFEE_ROWS_XML);
        wrs1.readXml(fis);
        assertTrue(wrs1.size() == COFFEES_ROWS);
        assertArrayEquals(expectedRows, getPrimaryKeys(wrs1));
        // With setShowDeleted(false) which is the default,
        // the deleted row should not be visible
        for (int row : rowsToDelete) {
            assertFalse(findRowByPrimaryKey(wrs1, row, 1));
        }
        // With setShowDeleted(true), the deleted row should be visible
        wrs1.setShowDeleted(true);
        for (int row : rowsToDelete) {
            assertTrue(findRowByPrimaryKey(wrs1, row, 1));
        }
        assertArrayEquals(COFFEES_PRIMARY_KEYS, getPrimaryKeys(wrs1));
        wrs1.close();

    }

    /*
     * Validate that the correct row in the WebRowSet that had been created
     * from an xml file is marked as updated and contains the correct values
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("rowSetType")
    public void WebRowSetTest0010(WebRowSet wrs1) throws Exception {
        FileInputStream fis = new FileInputStream(UPDATED_COFFEE_ROWS_XML);
        wrs1.readXml(fis);
        assertTrue(wrs1.size() == COFFEES_ROWS);
        assertArrayEquals(COFFEES_PRIMARY_KEYS, getPrimaryKeys(wrs1));
        wrs1.beforeFirst();
        while (wrs1.next()) {
            if (wrs1.getInt(1) == 3) {
                assertTrue(wrs1.rowUpdated());
                assertTrue(wrs1.getInt(5) == 21 && wrs1.getInt(6) == 69);
                assertFalse(wrs1.rowDeleted());
                assertFalse(wrs1.rowInserted());
            } else {
                assertFalse(wrs1.rowUpdated());
                assertFalse(wrs1.rowDeleted());
                assertFalse(wrs1.rowInserted());
            }
        }
        wrs1.close();
    }

    /*
     * Validate the correct row is marked as inserted in a WebRowSet
     * that is read from an xml file
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("rowSetType")
    public void WebRowSetTest0011(WebRowSet wrs1) throws Exception {
        int expectedSize = COFFEES_ROWS + 2;
        int addedRowPK = 15;
        int addedRowPK2 = 20;
        Object[] expected = Arrays.copyOf(COFFEES_PRIMARY_KEYS, expectedSize);
        expected[expectedSize - 2] = addedRowPK;
        expected[expectedSize - 1] = addedRowPK2;
        FileInputStream fis = new FileInputStream(INSERTED_COFFEE_ROWS_XML);
        wrs1.readXml(fis);
        assertTrue(wrs1.size() == expectedSize);
        var actual = getPrimaryKeys(wrs1);
        Arrays.sort(actual);
        Arrays.sort(expected);
        assertArrayEquals(expected, actual);
        wrs1.beforeFirst();
        while (wrs1.next()) {
            if (wrs1.getInt(1) == 15 || wrs1.getInt(1) == 20) {
                assertTrue(wrs1.rowInserted());
                assertFalse(wrs1.rowDeleted());
                assertFalse(wrs1.rowUpdated());
            } else {
                assertFalse(wrs1.rowInserted());
                assertFalse(wrs1.rowDeleted());
                assertFalse(wrs1.rowUpdated());
            }
        }
        wrs1.close();
    }

    /*
     * Read an xml file which contains a row that was inserted and updated
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("rowSetType")
    public void WebRowSetTest0012(WebRowSet wrs1) throws Exception {
        int expectedSize = COFFEES_ROWS + 1;
        int addedRowPK = 100;
        Object[] expected = Arrays.copyOf(COFFEES_PRIMARY_KEYS, expectedSize);
        expected[expectedSize - 1] = addedRowPK;
        FileInputStream fis = new FileInputStream(UPDATED_INSERTED_COFFEE_ROWS_XML);
        wrs1.readXml(fis);
        assertTrue(wrs1.size() == expectedSize);
        assertArrayEquals(expected, getPrimaryKeys(wrs1));
        wrs1.beforeFirst();
        while (wrs1.next()) {
            if (wrs1.getInt(1) == addedRowPK) {
                // Row that was inserted and updated
                assertTrue(wrs1.rowUpdated());
                assertTrue(
                        wrs1.getBigDecimal(4).equals(BigDecimal.valueOf(12.99))
                        && wrs1.getInt(6) == 125);
                assertFalse(wrs1.rowDeleted());
                assertTrue(wrs1.rowInserted());
            } else {
                // Remaining rows should only be inserted
                assertFalse(wrs1.rowUpdated());
                assertFalse(wrs1.rowDeleted());
                assertTrue(wrs1.rowInserted());
            }
        }
        wrs1.close();
    }

    /**
     * Verifies that an updated row written as XML can be read back with schema
     * validation enabled.
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("rowSetType")
    public void testUpdatedRows(WebRowSet wrs) throws Exception {
        try {
            readCoffeeRows(wrs);
            wrs.absolute(3);
            wrs.updateInt(5, 21);
            wrs.updateInt(6, 69);
            wrs.updateRow();

            ByteArrayOutputStream baos =
                    writeWebRowSetWithOutputStreamWithWriter(wrs);
            withRowSetValidation(null, () -> {
                // Clearing the property ensures validation is enabled;
                // verifies the writer emits schema-compliant
                // modifyRow/updateValue XML.
                try (WebRowSet wrs1 =
                        readWebRowSetWithOInputStreamWithReader(baos)) {
                    assertEquals(COFFEES_ROWS, wrs1.size());
                    assertArrayEquals(COFFEES_PRIMARY_KEYS, getPrimaryKeys(wrs1));

                    assertTrue(findRowByPrimaryKey(wrs1, 3, 1));
                    assertTrue(wrs1.rowUpdated());
                    assertEquals(21, wrs1.getInt(5));
                    assertEquals(69, wrs1.getInt(6));
                }
            });
        } finally {
            wrs.close();
        }
    }

    /**
     * Verifies that a deleted row written as XML can be read back with schema
     * validation enabled.
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("rowSetType")
    public void testDeletedRows(WebRowSet wrs)
            throws Exception {
        try {
            readCoffeeRows(wrs);
            assertTrue(deleteRowByPrimaryKey(wrs, 2, 1));

            ByteArrayOutputStream baos =
                    writeWebRowSetWithOutputStreamWithWriter(wrs);
            withRowSetValidation(null, () -> {
                // Clearing the property ensures validation is enabled; parsing
                // fails if the writer omits schema-required empty updateValue
                // elements.
                try (WebRowSet wrs1 =
                        readWebRowSetWithOInputStreamWithReader(baos)) {
                    Object[] expectedRows = {1, 3, 4, 5};
                    assertEquals(COFFEES_ROWS, wrs1.size());
                    assertArrayEquals(expectedRows, getPrimaryKeys(wrs1));

                    wrs1.setShowDeleted(true);
                    assertTrue(findRowByPrimaryKey(wrs1, 2, 1));
                    assertTrue(wrs1.rowDeleted());
                    assertArrayEquals(COFFEES_PRIMARY_KEYS, getPrimaryKeys(wrs1));
                }
            });
        } finally {
            wrs.close();
        }
    }

    /**
     * Verifies that null properties are written as schema-compliant empty
     * elements and read back as null.
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("rowSetType")
    public void testNullProperties(WebRowSet wrs) throws Exception {
        try {
            readCoffeeRows(wrs);
            assertNull(wrs.getCommand());
            assertNull(wrs.getDataSourceName());
            assertNull(wrs.getUrl());

            ByteArrayOutputStream baos =
                    writeWebRowSetWithOutputStreamWithWriter(wrs);
            withRowSetValidation(null, () -> {
                // Reading with validation enabled fails if the writer emits the
                // legacy invalid <null/> form for nullable properties.
                try (WebRowSet wrs1 =
                        readWebRowSetWithOInputStreamWithReader(baos)) {
                    assertEquals(COFFEES_ROWS, wrs1.size());
                    assertArrayEquals(COFFEES_PRIMARY_KEYS, getPrimaryKeys(wrs1));
                    assertNull(wrs1.getCommand());
                    assertNull(wrs1.getDataSourceName());
                    assertNull(wrs1.getUrl());
                }
            });
        } finally {
            wrs.close();
        }
    }

    /**
     * Verifies that legacy XML is rejected since schema validation is enabled
     * by default.
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("rowSetType")
    public void testLegacyXmlWithValidation(WebRowSet wrs)
            throws Exception {
        String legacyXml = getLegacyXml();
        withRowSetValidation(null, () -> {
            try {
                assertThrows(SQLException.class,
                        () -> wrs.readXml(new StringReader(legacyXml)));
            } finally {
                wrs.close();
            }
        });
    }

    /**
     * Verifies that legacy XML is accepted when schema validation is explicitly
     * disabled.
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("rowSetType")
    public void testLegacyXmlWithoutValidation(WebRowSet wrs)
            throws Exception {
        String legacyXml = getLegacyXml()
                // Exercise the path where an empty element follows a value bearing
                // element. This is for JDK-8386783
                .replace("<url>jdbc:derby://localhost:1527/testDB;create=true</url>", "<url/>");
        withRowSetValidation("false", () -> {
            try {
                wrs.readXml(new StringReader(legacyXml));
                assertNull(wrs.getUrl());
                assertEquals(COFFEES_ROWS, wrs.size());
                assertArrayEquals(COFFEES_PRIMARY_KEYS, getPrimaryKeys(wrs));

                wrs.beforeFirst();
                boolean foundUpdatedRow = false;
                while (wrs.next()) {
                    if (wrs.getInt(1) == 3) {
                        foundUpdatedRow = true;
                        assertTrue(wrs.rowUpdated());
                        assertEquals(21, wrs.getInt(5));
                        assertEquals(69, wrs.getInt(6));
                    }
                }
                assertTrue(foundUpdatedRow);
            } finally {
                wrs.close();
            }
        });
    }

    /**
     * Verifies that legacy null elements are rejected since schema validation
     * is enabled by default.
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("rowSetType")
    public void testLegacyNullXmlWithValidation(WebRowSet wrs)
            throws Exception {
        String legacyXml = getLegacyNullXml();
        withRowSetValidation(null, () -> {
            try {
                // Legacy <null/> property values are not valid against the
                // standard schema.
                assertThrows(SQLException.class,
                        () -> wrs.readXml(new StringReader(legacyXml)));
            } finally {
                wrs.close();
            }
        });
    }

    /**
     * Verifies that legacy null elements are accepted when schema validation is
     * explicitly disabled.
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("rowSetType")
    public void testLegacyNullXmlWithoutValidation(WebRowSet wrs)
            throws Exception {
        String legacyXml = getLegacyNullXml();
        withRowSetValidation("false", () -> {
            try {
                wrs.readXml(new StringReader(legacyXml));
                assertEquals(COFFEES_ROWS, wrs.size());
                assertArrayEquals(COFFEES_PRIMARY_KEYS, getPrimaryKeys(wrs));

                // With validation disabled, preserve the old reader behavior
                // for nullable properties and metadata represented by <null/>.
                assertNull(wrs.getCommand());
                assertNull(wrs.getDataSourceName());
                assertNull(wrs.getUrl());

                ResultSetMetaData md = wrs.getMetaData();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    assertEquals("", md.getColumnLabel(i));
                    assertEquals("", md.getColumnTypeName(i));
                }
            } finally {
                wrs.close();
            }
        });
    }

    /**
     * Builds a legacy document from the schema-compliant UPDATED_COFFEE_ROWS_XML
     * file. Older WebRowSet XML used currentRow/updateRow for modified rows,
     * while the webrowset schema requires modifyRow/updateValue.
     */
    private String getLegacyXml() throws Exception {
        return Files.readString(Path.of(UPDATED_COFFEE_ROWS_XML))
                .replace("<modifyRow>", "<currentRow>")
                .replace("</modifyRow>", "</currentRow>")
                .replace("updateValue", "updateRow");
    }

    /**
     * Builds a legacy document from the schema-compliant COFFEE_ROWS_XML file.
     * Older WebRowSet XML used nested {@code <null/>} elements for nullable
     * string properties and metadata, while the webrowset schema requires
     * simple string content.
     */
    private String getLegacyNullXml() throws Exception {
        return Files.readString(Path.of(COFFEE_ROWS_XML))
                .replace("<command/>", "<command><null/></command>")
                .replace("<datasource/>", "<datasource><null/></datasource>")
                .replace("<url/>", "<url><null/></url>")
                .replace("<column-label/>",
                        "<column-label><null/></column-label>")
                .replace("<column-type-name/>",
                        "<column-type-name><null/></column-type-name>");
    }

    /**
     * Reads COFFEE_ROWS_XML and populates the given WebRowSet.
     */
    private void readCoffeeRows(WebRowSet wrs) throws Exception {
        try (FileInputStream fis = new FileInputStream(COFFEE_ROWS_XML)) {
            wrs.readXml(fis);
        }
    }

}
