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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

import org.junit.jupiter.api.Test;
import util.StubConnection;
import util.StubPreparedStatement;

import static org.junit.jupiter.api.Assertions.assertTrue;

// This test uses stub classes to simulate the bug without an actual
// connection to a database.
public class CachedRowSetUpdateTest {

    private final List<String> commands = new ArrayList<>();
    private boolean firstQuery = true;

    /*
     * Address a bug where the occurrence of "where" in catalog, schema or a table name
     * could cause an incorrect SQL statement to be executed.
     */
    @Test
    void tableNameWithWhereTest() throws SQLException {
        try (var conn = new TestStubConnection();
             var rows = createRowSet()) {

            // Define the table name that includes "where"
            rows.setTableName("BARWHEREBAZ");

            // Before the fix in 8394143, these SQL statements would have trimmed the
            // portions after "WHERE" in the table name, so "BAZ" would be missing.
            rows.acceptChanges(conn);
            // First update
            assertTrue(commands.contains("SELECT N, M FROM BARWHEREBAZ WHERE N = ? AND M IS NULL "), commands.toString());
            assertTrue(commands.contains("UPDATE BARWHEREBAZ SET N = ? , M = ? WHERE N = ? AND M IS NULL "), commands.toString());
            // Second update
            assertTrue(commands.contains("SELECT N, M FROM BARWHEREBAZ WHERE N IS NULL AND M = ? "), commands.toString());
            assertTrue(commands.contains("UPDATE BARWHEREBAZ SET N = ? , M = ? WHERE N IS NULL AND M = ? "), commands.toString());
        }
    }

    // 2 rows and 2 cols: M and N. N is updated in the first row and M in the second row.
    private static CachedRowSet createRowSet() throws SQLException {
        var rows = RowSetProvider.newFactory().createCachedRowSet();
        rows.setMetaData(createMetaData());
        // Apply UPDATE to subsequent cols
        for (var col : List.of("N", "M")) {
            // Add the data
            rows.moveToInsertRow();
            rows.updateInt(col, -1);
            rows.updateNull(col.equals("N") ? "M" : "N");
            rows.insertRow();
            rows.moveToCurrentRow();

            // Update the data
            rows.next();
            rows.setOriginalRow();
            rows.updateInt(col, 0);
            rows.updateRow();
        }
        return rows;
    }

    // Matches `createRowSet`
    private static RowSetMetaDataImpl createMetaData() throws SQLException {
        var metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(2);
        metadata.setColumnType(1, Types.INTEGER);
        metadata.setColumnName(1, "N");
        metadata.setColumnType(2, Types.INTEGER);
        metadata.setColumnName(2, "M");
        return metadata;
    }

    // All the stub classes implement the minimal methods required to exercise
    // the bug path for `writeData` and `initSQLStatements`.
    private class TestStubConnection extends StubConnection {

        @Override
        public Map<String, Class<?>> getTypeMap() throws SQLException {
            return Map.of();
        }

        @Override
        public PreparedStatement prepareStatement(String sql) {
            commands.add(sql);
            return new TestStubPreparedStatement(this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) {
            commands.add(sql);
            return new TestStubPreparedStatement(this);
        }

        @Override
        public void commit() {}
    }

    private class TestStubPreparedStatement extends StubPreparedStatement {

        public TestStubPreparedStatement(StubConnection con) {
            super(con);
        }

        // Needs to return an actual result that matches the update queries in the test.
        // Fixed ordering to match the query order that the test naturally encounters.
        @Override
        public ResultSet executeQuery() throws SQLException {
            String first;
            String second;
            if (firstQuery) {
                firstQuery = false;
                first = "N";
                second = "M";
            } else {
                second = "N";
                first = "M";
            }
            var rows = RowSetProvider.newFactory().createCachedRowSet();
            rows.setMetaData(createMetaData());
            rows.moveToInsertRow();
            rows.updateInt(first, -1);
            rows.updateNull(second);
            rows.insertRow();
            rows.moveToCurrentRow();
            rows.beforeFirst();
            return rows;
        }

        @Override
        public void setNull(int parameterIndex, int sqlType) {}

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
