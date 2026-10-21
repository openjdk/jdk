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

    /*
     * Address a bug where the occurrence of "where" in catalog, schema or a table name
     * could cause an incorrect SQL statement to be executed.
     */
    @Test
    void tableNameWithWhereTest() throws SQLException {
        try (var conn = new TestStubConnection();
             var rows = RowSetProvider.newFactory().createCachedRowSet()) {
            RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
            metadata.setColumnCount(1);
            metadata.setColumnType(1, Types.INTEGER);
            metadata.setColumnName(1, "N");
            rows.setMetaData(metadata);
            // Define the table name that includes "where"
            rows.setTableName("BARWHEREBAZ");

            // Add the data
            rows.moveToInsertRow();
            rows.updateInt("N", -1);
            rows.insertRow();
            rows.moveToCurrentRow();

            // Update the data
            rows.next();
            rows.setOriginalRow();
            rows.updateInt("N", 0);
            rows.updateRow();

            // Before the fix in 8394143, these SQL statements would have trimmed the
            // portions after "WHERE", so "BAZ" would be missing.
            rows.acceptChanges(conn);
            assertTrue(commands.contains("SELECT N FROM BARWHEREBAZ WHERE N = ? "), commands.toString());
            assertTrue(commands.contains("UPDATE BARWHEREBAZ SET N = ? WHERE N = ? "), commands.toString());
        }
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

        // Needs to return an actual result that matches the update query in the test
        @Override
        public ResultSet executeQuery() throws SQLException {
            var metadata = new RowSetMetaDataImpl();
            var rows = RowSetProvider.newFactory().createCachedRowSet();
            metadata.setColumnCount(1);
            metadata.setColumnType(1, Types.INTEGER);
            metadata.setColumnName(1, "N");
            rows.setMetaData(metadata);
            rows.moveToInsertRow();
            rows.updateInt("N", -1);
            rows.insertRow();
            rows.moveToCurrentRow();
            rows.beforeFirst();
            return rows;
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
