/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package test.rowset.cachedrowset;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

import org.junit.jupiter.api.Test;
import util.StubConnection;
import util.StubDatabaseMetaData;
import util.StubPreparedStatement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

// This test uses stub classes to simulate the bug without an actual
// connection to a database.
public class CachedRowSetInsertionTest {

    /*
     * Address a bug where insertions would fail with AIOOBE if the amount of
     * primary key columns in the datasource were greater than the amount of
     * columns that were selected in the insertion.
     */
    @Test
    void primaryKeyInsertionFailureTest() throws SQLException {
        try (var conn = new TestStubConnection();
             var rows = RowSetProvider.newFactory().createCachedRowSet()) {
            RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
            metadata.setColumnCount(1);
            metadata.setColumnType(1, Types.INTEGER);
            metadata.setColumnName(1, "N");
            rows.setMetaData(metadata);
            rows.setTableName("T");
            // See `TestStubDatabaseMetaData` which creates the two primary keys cols: A and B.
            // We only select one column: N for our insertion, which allows the bug to be reproduced
            // because N (1) < A & B (2).
            rows.moveToInsertRow();
            rows.updateInt("N", -1);
            rows.insertRow();
            rows.moveToCurrentRow();
            // Before the fix in 8394152, this line would fail
            assertDoesNotThrow(() -> rows.acceptChanges(conn),
                    "insertion fails when there are more primary key columns than selected columns");
        }
    }

    // All the stub classes implement the minimal methods required to exercise
    // the proper writeData -> insertNewRow path. The one that really matters and sets
    // critical data is `TestStubDatabaseMetaData.getPrimaryKeys()`.
    private static class TestStubConnection extends StubConnection {

        @Override
        public DatabaseMetaData getMetaData() {
            return new TestStubDatabaseMetaData();
        }

        @Override
        public PreparedStatement prepareStatement(String sql) {
            return new TestStubPreparedStatement(this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) {
            return new TestStubPreparedStatement(this);
        }

        @Override
        public void commit() {}
    }

    /*
     * This stub class is what is important and relevant to the bug!
     * What we populate in here is what the `CachedRowSetWriter` will query to determine
     * the primary keys related to the underlying data source.
     */
    private static class TestStubDatabaseMetaData extends StubDatabaseMetaData {

        // Populate the metadata to have two primary keys: A and B.
        // The returned set consist of 1 col, with 2 rows whose values are A and B.
        @Override
        public ResultSet getPrimaryKeys(String catalog, String schema, String table)
                throws SQLException {
            var crs = RowSetProvider.newFactory().createCachedRowSet();
            RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
            metadata.setColumnCount(1);
            metadata.setColumnType(1, Types.VARCHAR);
            metadata.setColumnName(1, "COLUMN_NAME");
            crs.setMetaData(metadata);
            for (var pk : List.of("A", "B")) {
                crs.moveToInsertRow();
                crs.updateString("COLUMN_NAME", pk);
                crs.insertRow();
            }
            crs.moveToCurrentRow();
            crs.beforeFirst();
            return crs;
        }
    }

    private static class TestStubPreparedStatement extends StubPreparedStatement {

        public TestStubPreparedStatement(StubConnection con) {
            super(con);
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            return RowSetProvider.newFactory().createCachedRowSet();
        }

        @Override
        public void setObject(int parameterIndex, Object x) {}

        @Override
        public int executeUpdate() {
            return 1;
        }

        @Override
        public void close() {}
    }
}
