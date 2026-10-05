/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package test.rowset.resourcebundle;

import java.util.Locale;
import java.sql.SQLException;
import javax.sql.rowset.RowSetProvider;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * @test
 * @bug 8294989
 * @summary Check that the resource bundle can be accessed
 * @throws SQLException if an unexpected error occurs
 */
public class ValidateResourceBundleAccess{
    // Expected JDBCResourceBundle message, jdbcrowsetimpl.invalstate
    private static final String INVALIDSTATE = "Invalid state";
    // Expected JDBCResourceBundle message, crsreader.connecterr
    private static final String RSREADERERROR = "Internal Error in RowSetReader: no connection or command";

    // Checking against English messages, set to US Locale
    @BeforeAll
    public static void setEnglishEnvironment() {
        Locale.setDefault(Locale.US);
    }

    @Test
    public void testResourceBundleAccess() throws SQLException {
        var rsr = RowSetProvider.newFactory();
        var crs =rsr.createCachedRowSet();
        var jrs = rsr.createJdbcRowSet();
        // Simple test to force an Exception to validate the expected message
        // is found from the resource bundle
        try {
            jrs.getMetaData();
            throw new RuntimeException("$$$ Expected SQLException was not thrown!");
        } catch (SQLException sqe) {
            assertTrue(sqe.getMessage().equals(INVALIDSTATE));
        }
        // Now tests via CachedRowSet
        try {
            crs.execute();
            throw new RuntimeException("$$$ Expected SQLException was not thrown!");
        } catch (SQLException e) {
            assertTrue(e.getMessage().equals(RSREADERERROR));
        }
    }
}
