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
package sql;

import org.junit.jupiter.params.provider.ValueSource;
import util.BaseTest;
import util.StubConnection;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class PreparedStatementTests extends BaseTest {

    private PreparedStatement pstmt;

    @BeforeEach
    public void setUpMethod() throws Exception {
        pstmt = new StubConnection().prepareStatement("Select * from foo were bar = ?");
    }

    @AfterEach
    public void tearDownMethod() throws Exception {
        pstmt.close();
    }

    /*
     * Verify that enquoteLiteral creates a  valid literal and converts every
     * single quote to two single quotes
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("validEnquotedLiteralValues")
    public void test00(String s, String expected) throws SQLException {
        assertEquals(expected, pstmt.enquoteLiteral(s));
    }

    /*
     * Validate a NullPointerException is thrown if the string passed to
     * enquoteLiteral is null
     */
    @Test
    public void test01() throws SQLException {
        assertThrows(NullPointerException.class, () -> pstmt.enquoteLiteral(null));
    }

    /*
     * Validate that enquoteIdentifier returns the expected value
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("validEnquotedIdentifierValues")
    public void test02(String s, boolean alwaysQuote, String expected) throws SQLException {
        assertEquals(expected, pstmt.enquoteIdentifier(s, alwaysQuote));
    }

    /*
     * Validate that a SQLException is thrown for values that are not valid
     * for a SQL identifier
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("invalidEnquotedIdentifierValues")
    public void test03(String s, boolean alwaysQuote) throws SQLException {
        assertThrows(SQLException.class, () -> pstmt.enquoteIdentifier(s, alwaysQuote));
    }

    /*
     * Validate a NullPointerException is thrown is the string passed to
     * enquoteIdentiifer is null
     */
    @ParameterizedTest(autoCloseArguments = false)
    @ValueSource(booleans = {true, false})
    public void test04(boolean alwaysQuote) throws SQLException {
        assertThrows(NullPointerException.class, () -> pstmt.enquoteIdentifier(null, alwaysQuote));
    }

    /*
     * Validate that isSimpleIdentifier returns the expected value
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("simpleIdentifierValues")
    public void test05(String s, boolean expected) throws SQLException {
        assertEquals(expected, pstmt.isSimpleIdentifier(s));
    }

    /*
     * Validate a NullPointerException is thrown if the string passed to
     * isSimpleIdentifier is null
     */
    @Test
    public void test06() throws SQLException {
        assertThrows(NullPointerException.class, () -> pstmt.isSimpleIdentifier(null));
    }

    /*
     * Verify that enquoteLiteral creates a  valid literal and converts every
     * single quote to two single quotes
     */
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("validEnquotedNCharLiteralValues")
    public void test07(String s, String expected) throws SQLException {
        assertEquals(expected, pstmt.enquoteNCharLiteral(s));
    }

    /*
     * Validate a NullPointerException is thrown if the string passed to
     * enquoteNCharLiteral is null
     */
    @Test
    public void test08() throws SQLException {
        assertThrows(NullPointerException.class, () -> pstmt.enquoteNCharLiteral(null));
    }
}
