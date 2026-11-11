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

import com.sun.rowset.CachedRowSetImpl;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetFactory;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import javax.sql.rowset.spi.SyncFactory;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Hashtable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/*
 * This test addresses a number of warning related issues in CachedRowSetImpl.
 * See JDK-8388810 for further details.
 */
public class CachedRowSetWarningsTest {

    private static final RowSetFactory FACTORY;
    private static final String WARNING_MSG =
            "Populating rows setting has exceeded max row setting";

    static {
        try {
            FACTORY = RowSetProvider.newFactory();
        } catch (SQLException e) {
            throw new RuntimeException("Cannot create row set factory used for test", e);
        }
    }

    /*
     * The max rows warning should only be added a single time, not for each subsequent
     * row above the max row that is encountered. In the old code, only the 1-arg `populate`
     * suffered from this issue, but this test covers both the 1-arg and 2-arg for coverage.
     */
    @Test
    public void maxRowsShouldWarnOnceTest() throws SQLException {
        // RowSetWarning cannot be cleared, so use fresh instances to test the
        // 1-arg and 2-arg populate
        try (var oneArgCrs = FACTORY.createCachedRowSet();
             var twoArgCrs = FACTORY.createCachedRowSet();
             var pagedCrs = FACTORY.createCachedRowSet()) {
            oneArgCrs.setMaxRows(1);
            oneArgCrs.populate(resultSetWithRows(5));
            // There should be no subsequent chained warnings
            assertNull(oneArgCrs.getRowSetWarnings().getNextWarning());

            // The 2-arg path here also implicitly covers a bug in the old code where
            // an Error would always be thrown when `getNextWarning` was invoked on the
            // returned `RowSetWarning`.
            twoArgCrs.setMaxRows(1);
            twoArgCrs.populate(resultSetWithRows(5), 1);
            // There should be no subsequent chained warnings
            assertNull(twoArgCrs.getRowSetWarnings().getNextWarning());

            // Also check the paged path for 2-arg path
            pagedCrs.setPageSize(1);
            pagedCrs.populate(resultSetWithRows(5), 1);
            assertNull(pagedCrs.getRowSetWarnings().getNextWarning());
        }
    }

    /*
     * The old code in the hashtable ctor never initialized the warnings.
     * An NPE would be thrown when max rows exceeded during a populate operation.
     */
    @Test
    public void hashtableMaxRowsTest() throws SQLException {
        var env = new Hashtable<String, String>();
        // Supply the standard provider
        env.put(SyncFactory.ROWSET_SYNC_PROVIDER,
                "com.sun.rowset.providers.RIOptimisticProvider");
        try (var crs = new CachedRowSetImpl(env);
            var crs2 = new CachedRowSetImpl(env)) {
            crs.setMaxRows(1);
            crs2.setMaxRows(2);
            // Exercise both 1-arg and 2-arg
            assertDoesNotThrow(() -> crs.populate(resultSetWithRows(5)));
            assertDoesNotThrow(() -> crs2.populate(resultSetWithRows(5), 1));
        }
    }

    /*
     * When created via the default ctor, warnings used to be eagerly created.
     * Thus, the old code would return a non-null warning when there were no warnings.
     * To adhere to the specification, null needs to be returned.
     */
    @Test
    public void noWarningsShouldBeNullTest() throws SQLException {
        try (var crs = FACTORY.createCachedRowSet()) {
            // Check both the SQLWarning and the RowSetWarning
            assertNull(crs.getRowSetWarnings());
            assertNull(crs.getWarnings());
        }
    }

    /*
     * The old code always created a root warning with no message.
     * If a root warning exists, it should always contain a valid warning message.
     */
    @Test
    public void rootWarningShouldNotBeEmptyTest() throws SQLException {
        try (var crs = FACTORY.createCachedRowSet()) {
            crs.setMaxRows(1);
            crs.populate(resultSetWithRows(5));
            assertEquals(WARNING_MSG, crs.getRowSetWarnings().getMessage());
        }
    }

    // Utility to create a crs with dummy rows (which is used to violate a crs with max rows defined)
    private static ResultSet resultSetWithRows(int rows) throws SQLException {
        CachedRowSet rs = FACTORY.createCachedRowSet();
        RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(1);
        metadata.setColumnType(1, Types.INTEGER);
        metadata.setColumnName(1, "foo");
        rs.setMetaData(metadata);
        // Data does not matter, just create rows
        for (int i = 1; i <= rows; i++) {
            rs.moveToInsertRow();
            rs.updateInt(1, i);
            rs.insertRow();
        }
        rs.moveToCurrentRow();
        rs.beforeFirst();
        return rs;
    }
}
