/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package test.sql;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import util.BaseTest;
import util.StubConnection;

import java.sql.SQLException;

import static org.testng.Assert.*;

public class ConnectionTests extends BaseTest {

    protected StubConnection conn;

    @BeforeMethod
    public void setUpMethod() throws Exception {
        conn = new StubConnection();
    }

    /*
     * Verify that enquoteLiteral creates a  valid literal and converts every
     * single quote to two single quotes
     */
    @Test(dataProvider = "validEnquotedLiteralValues")
    public void test00(String s, String expected) throws SQLException {
        assertEquals(conn.enquoteLiteral(s), expected);
    }

    /*
     * Validate a NullPointerException is thrown if the string passed to
     * enquoteLiteral is null
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void test01() throws SQLException {
        conn.enquoteLiteral(null);
    }

    /*
     * Validate that enquoteIdentifier returns the expected value
     */
    @Test(dataProvider = "validIdentifierValues")
    public void test02(String s, boolean alwaysQuote, String expected) throws SQLException {
        assertEquals(conn.enquoteIdentifier(s, alwaysQuote), expected);
    }

    /*
     * Validate that a SQLException is thrown for values that are not valid
     * for a SQL identifier
     */
    @Test(dataProvider = "invalidIdentifierValues",
            expectedExceptions = SQLException.class)
    public void test03(String s, boolean alwaysQuote) throws SQLException {
        conn.enquoteIdentifier(s, alwaysQuote);
    }

    /*
     * Validate a NullPointerException is thrown is the string passed to
     * enquoteIdentiifer is null
     */
    @Test(dataProvider = "trueFalse",
            expectedExceptions = NullPointerException.class)
    public void test04(boolean alwaysQuote) throws SQLException {
        conn.enquoteIdentifier(null, alwaysQuote);
    }

    /*
     * Validate that isSimpleIdentifier returns the expected value
     */
    @Test(dataProvider = "simpleIdentifierValues")
    public void test05(String s, boolean expected) throws SQLException {
        assertEquals(conn.isSimpleIdentifier(s), expected);
    }

    /*
     * Validate a NullPointerException is thrown if the string passed to
     * isSimpleIdentifier is null
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void test06() throws SQLException {
        conn.isSimpleIdentifier(null);
    }

    /*
     * Verify that enquoteLiteral creates a  valid literal and converts every
     * single quote to two single quotes
     */
    @Test(dataProvider = "validEnquotedNCharLiteralValues")
    public void test07(String s, String expected) throws SQLException {
        assertEquals(conn.enquoteNCharLiteral(s), expected);
    }

    /*
     * Validate a NullPointerException is thrown if the string passed to
     * enquoteNCharLiteral is null
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void test08() throws SQLException {
        conn.enquoteNCharLiteral(null);
    }
}
