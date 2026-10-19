/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package sql;

import org.junit.jupiter.params.provider.ValueSource;
import util.BaseTest;
import util.StubConnection;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ConnectionTests extends BaseTest {

    protected StubConnection conn;

    @BeforeEach
    public void setUpMethod() throws Exception {
        conn = new StubConnection();
    }

    /*
     * Verify that enquoteLiteral creates a  valid literal and converts every
     * single quote to two single quotes
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("validEnquotedLiteralValues")
    public void test00(String s, String expected) throws SQLException {
        assertEquals(expected, conn.enquoteLiteral(s));
    }

    /*
     * Validate a NullPointerException is thrown if the string passed to
     * enquoteLiteral is null
     */
    @Test
    public void test01() throws SQLException {
        assertThrows(NullPointerException.class, () -> conn.enquoteLiteral(null));
    }

    /*
     * Validate that enquoteIdentifier returns the expected value
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("validEnquotedIdentifierValues")
    public void test02(String s, boolean alwaysQuote, String expected) throws SQLException {
        assertEquals(expected, conn.enquoteIdentifier(s, alwaysQuote));
    }

    /*
     * Validate that a SQLException is thrown for values that are not valid
     * for a SQL identifier
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("invalidEnquotedIdentifierValues")
    public void test03(String s, boolean alwaysQuote) throws SQLException {
        assertThrows(SQLException.class, () -> conn.enquoteIdentifier(s, alwaysQuote));
    }

    /*
     * Validate a NullPointerException is thrown is the string passed to
     * enquoteIdentiifer is null
     */
    @ParameterizedTest(autoCloseArguments = false)
    @ValueSource(booleans = {true, false})
    public void test04(boolean alwaysQuote) throws SQLException {
        assertThrows(NullPointerException.class, () -> conn.enquoteIdentifier(null, alwaysQuote));
    }

    /*
     * Validate that isSimpleIdentifier returns the expected value
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("simpleIdentifierValues")
    public void test05(String s, boolean expected) throws SQLException {
        assertEquals(expected, conn.isSimpleIdentifier(s));
    }

    /*
     * Validate a NullPointerException is thrown if the string passed to
     * isSimpleIdentifier is null
     */
    @Test
    public void test06() throws SQLException {
        assertThrows(NullPointerException.class, () -> conn.isSimpleIdentifier(null));
    }

    /*
     * Verify that enquoteLiteral creates a  valid literal and converts every
     * single quote to two single quotes
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("validEnquotedNCharLiteralValues")
    public void test07(String s, String expected) throws SQLException {
        assertEquals(expected, conn.enquoteNCharLiteral(s));
    }

    /*
     * Validate a NullPointerException is thrown if the string passed to
     * enquoteNCharLiteral is null
     */
    @Test
    public void test08() throws SQLException {
        assertThrows(NullPointerException.class, () -> conn.enquoteNCharLiteral(null));
    }
}
