/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.sql.SQLException;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import util.BaseTest;
import util.StubStatement;

public class StatementTests extends BaseTest {

    protected StubStatement stmt;

    @BeforeMethod
    public void setUpMethod() throws Exception {
        stmt = new StubStatement();
    }

    /*
     * Verify that enquoteLiteral creates a  valid literal and converts every
     * single quote to two single quotes
     */
    @Test(dataProvider = "validEnquotedLiteralValues")
    public void test00(String s, String expected) {
        assertEquals(stmt.enquoteLiteral(s), expected);
    }

    /*
     * Validate a NullPointerException is thrown if the string passed to
     * enquoteLiteral is null
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void test01() {
        stmt.enquoteLiteral(null);

    }

    /*
     * Validate that enquoteIdentifier returns the expected value
     */
    @Test(dataProvider = "validIdentifierValues")
    public void test02(String s, boolean alwaysQuote, String expected) throws SQLException {
        assertEquals(stmt.enquoteIdentifier(s, alwaysQuote), expected);

    }

    /*
     * Validate that a SQLException is thrown for values that are not valid
     * for a SQL identifier
     */
    @Test(dataProvider = "invalidIdentifierValues",
            expectedExceptions = SQLException.class)
    public void test03(String s, boolean alwaysQuote) throws SQLException {
        stmt.enquoteIdentifier(s, alwaysQuote);

    }

    /*
     * Validate a NullPointerException is thrown is the string passed to
     * enquoteIdentiifer is null
     */
    @Test(dataProvider = "trueFalse",
            expectedExceptions = NullPointerException.class)
    public void test04(boolean alwaysQuote) throws SQLException {
        stmt.enquoteIdentifier(null, alwaysQuote);

    }

    /*
     * DataProvider used to provide strings that will be used to validate
     * that enquoteLiteral converts a string to a literal and every instance of
     * a single quote will be converted into two single quotes in the literal.
     */
    @DataProvider(name = "validEnquotedLiteralValues")
    protected Object[][] validEnquotedLiteralValues() {
        return new Object[][]{
            {"Hello", "'Hello'"},
            {"G'Day", "'G''Day'"},
            {"'G''Day'", "'''G''''Day'''"},
            {"I'''M", "'I''''''M'"},
            {"The Dark Knight", "'The Dark Knight'"}

        };
    }

    /*
     * DataProvider used to provide strings that will be used to validate
     * that enqouteIdentifier returns a simple SQL Identifier or a double
     * quoted identifier
     */
    @DataProvider(name = "validIdentifierValues")
    protected Object[][] validEnquotedIdentifierValues() {
        return new Object[][]{
            {"Hello", false, "Hello"},
            {"Hello", true, "\"Hello\""},
            {"G'Day", false, "\"G'Day\""},
            {"G'Day", true, "\"G'Day\""},
            {"Bruce Wayne", false, "\"Bruce Wayne\""},
            {"Bruce Wayne", true, "\"Bruce Wayne\""},
            {"GoodDay$", false, "\"GoodDay$\""},
            {"GoodDay$", true, "\"GoodDay$\""},};
    }

    /*
     * DataProvider used to provide strings are invalid for enquoteIdentifier
     * resulting in a SQLException being thrown
     */
    @DataProvider(name = "invalidIdentifierValues")
    protected Object[][] invalidEnquotedIdentifierValues() {
        int invalidLen = 129;
        StringBuilder s = new StringBuilder(invalidLen);
        for (int i = 0; i < invalidLen; i++) {
            s.append('a');
        }
        return new Object[][]{
            {"Hel\"lo", false},
            {"\"Hel\"lo\"", true},
            {"Hello" + '\0', false},
            {"", false},
            {s.toString(), false},};
    }
}
