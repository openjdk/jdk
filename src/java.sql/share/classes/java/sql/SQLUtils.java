/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package java.sql;

import java.util.regex.Pattern;

/**
 * Utility class used by the Connection & Statement interfaces for their
 * shared default methods.
 */
class SQLUtils {
    // Pattern used to verify if an identifier is a Simple SQL identifier
    private static final Pattern SIMPLE_IDENTIFIER_PATTERN
            = Pattern.compile("[\\p{Alpha}][\\p{Alnum}_]*");
    // Pattern to check if an identifier contains a null character or a double quote
     private static final Pattern INVALID_IDENTIFIER_CHARACTERS_PATTERN
            = Pattern.compile("[^\u0000\"]+");

    /**
     * Returns a {@code String} enclosed in single quotes. Any occurrence of a
     * single quote within the string will be replaced by two single quotes.
     *
     * <blockquote>
     * <table class="striped">
     * <caption>Examples of the conversion:</caption>
     * <thead>
     * <tr><th scope="col">Value</th><th scope="col">Result</th></tr>
     * </thead>
     * <tbody style="text-align:center">
     * <tr> <th scope="row">Hello</th> <td>'Hello'</td> </tr>
     * <tr> <th scope="row">G'Day</th> <td>'G''Day'</td> </tr>
     * <tr> <th scope="row">'G''Day'</th>
     * <td>'''G''''Day'''</td> </tr>
     * <tr> <th scope="row">I'''M</th> <td>'I''''''M'</td>
     * </tr>
     *
     * </tbody>
     * </table>
     * </blockquote>
     *
     * @param val a character string
     * @return A string enclosed by single quotes with every single quote
     * converted to two single quotes
     * @throws NullPointerException if val is {@code null}
     * @throws SQLException         if a database access error occurs
     * @implNote JDBC driver implementations may need to provide their own implementation
     * of this method in order to meet the requirements of the underlying
     * datasource.
     */
    static String enquoteLiteral(String val) throws SQLException {
        return "'" + val.replace("'", "''") + "'";
    }

    /**
     * Returns a SQL identifier. If {@code identifier} is a simple SQL identifier:
     * <ul>
     * <li>Return the original value if {@code alwaysDelimit} is
     * {@code false}</li>
     * <li>Return a delimited identifier if {@code alwaysDelimit} is
     * {@code true}</li>
     * </ul>
     * <p>
     * If {@code identifier} is not a simple SQL identifier, {@code identifier} will be
     * enclosed in double quotes if not already present. If the datasource does
     * not support double quotes for delimited identifiers, the
     * identifier should be enclosed by the string returned from
     * {@link DatabaseMetaData#getIdentifierQuoteString}.  If the datasource
     * does not support delimited identifiers, a
     * {@code SQLFeatureNotSupportedException} should be thrown.
     * <p>
     * A {@code SQLException} will be thrown if {@code identifier} contains any
     * characters invalid in a delimited identifier or the identifier length is
     * invalid for the datasource.
     *
     * @param identifier  a SQL identifier
     * @param alwaysDelimit indicates if a simple SQL identifier should be
     *                    returned as a quoted identifier
     * @return A simple SQL identifier or a delimited identifier
     * @throws SQLException                    if identifier is not a valid identifier
     * @throws SQLFeatureNotSupportedException if the datasource does not support
     *                                         delimited identifiers
     * @throws NullPointerException            if identifier is {@code null}
     * @implSpec The default implementation uses the following criteria to
     * determine a valid simple SQL identifier:
     * <ul>
     * <li>The string is not enclosed in double quotes</li>
     * <li>The first character is an alphabetic character from a through z, or
     * from A through Z</li>
     * <li>The name only contains alphanumeric characters or the character "_"</li>
     * </ul>
     * <p>
     * The default implementation will throw a {@code SQLException} if:
     * <ul>
     * <li>{@code identifier} contains a {@code null} character or double quote and is not
     * a simple SQL identifier.</li>
     * <li>The length of {@code identifier} is less than 1 or greater than 128 characters
     * </ul>
     * <blockquote>
     * <table class="striped" >
     * <caption>Examples of the conversion:</caption>
     * <thead>
     * <tr>
     * <th scope="col">identifier</th>
     * <th scope="col">alwaysDelimit</th>
     * <th scope="col">Result</th></tr>
     * </thead>
     * <tbody>
     * <tr>
     * <th scope="row">Hello</th>
     * <td>false</td>
     * <td>Hello</td>
     * </tr>
     * <tr>
     * <th scope="row">Hello</th>
     * <td>true</td>
     * <td>"Hello"</td>
     * </tr>
     * <tr>
     * <th scope="row">G'Day</th>
     * <td>false</td>
     * <td>"G'Day"</td>
     * </tr>
     * <tr>
     * <th scope="row">"Bruce Wayne"</th>
     * <td>false</td>
     * <td>"Bruce Wayne"</td>
     * </tr>
     * <tr>
     * <th scope="row">"Bruce Wayne"</th>
     * <td>true</td>
     * <td>"Bruce Wayne"</td>
     * </tr>
     * <tr>
     * <th scope="row">GoodDay$</th>
     * <td>false</td>
     * <td>"GoodDay$"</td>
     * </tr>
     * <tr>
     * <th scope="row">Hello"World</th>
     * <td>false</td>
     * <td>SQLException</td>
     * </tr>
     * <tr>
     * <th scope="row">"Hello"World"</th>
     * <td>false</td>
     * <td>SQLException</td>
     * </tr>
     * </tbody>
     * </table>
     * </blockquote>
     * @implNote JDBC driver implementations may need to provide their own implementation
     * of this method in order to meet the requirements of the underlying
     * datasource.
     */
    static String enquoteIdentifier(String identifier, boolean alwaysDelimit) throws SQLException {
        int len = identifier.length();
        if (len < 1 || len > 128) {
            throw new SQLException("Invalid name");
        }
        if (SIMPLE_IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            return alwaysDelimit ? "\"" + identifier + "\"" : identifier;
        }
        if (identifier.matches("^\".+\"$")) {
            identifier = identifier.substring(1, len - 1);
        }
        // Enclose the identifier in double quotes.  If the identifier
        // contains a null character or a double quote, throw a SQLException
        if (INVALID_IDENTIFIER_CHARACTERS_PATTERN.matcher(identifier).matches()) {
            return "\"" + identifier + "\"";
        } else {
            throw new SQLException("Invalid name");
        }
    }

    /**
     * Retrieves whether {@code identifier} is a simple SQL identifier.
     *
     * @param identifier a SQL identifier
     * @return true if a simple SQL identifier, false otherwise
     * @throws NullPointerException if identifier is {@code null}
     * @throws SQLException         if a database access error occurs
     * @implSpec The default implementation uses the following criteria to
     * determine a valid simple SQL identifier:
     * <ul>
     * <li>The string is not enclosed in double quotes</li>
     * <li>The first character is an alphabetic character from a through z, or
     * from A through Z</li>
     * <li>The string only contains alphanumeric characters or the character
     * "_"</li>
     * <li>The string is between 1 and 128 characters in length inclusive</li>
     * </ul>
     *
     * <blockquote>
     * <table class="striped" >
     * <caption>Examples of the conversion:</caption>
     * <thead>
     * <tr>
     * <th scope="col">identifier</th>
     * <th scope="col">Simple Identifier</th>
     * </thead>
     *
     * <tbody>
     * <tr>
     * <th scope="row">Hello</th>
     * <td>true</td>
     * </tr>
     * <tr>
     * <th scope="row">G'Day</th>
     * <td>false</td>
     * </tr>
     * <tr>
     * <th scope="row">"Bruce Wayne"</th>
     * <td>false</td>
     * </tr>
     * <tr>
     * <th scope="row">GoodDay$</th>
     * <td>false</td>
     * </tr>
     * <tr>
     * <th scope="row">Hello"World</th>
     * <td>false</td>
     * </tr>
     * <tr>
     * <th scope="row">"Hello"World"</th>
     * <td>false</td>
     * </tr>
     * </tbody>
     * </table>
     * </blockquote>
     * @implNote JDBC driver implementations may need to provide their own
     * implementation of this method in order to meet the requirements of the
     * underlying datasource.
     */
    static boolean isSimpleIdentifier(String identifier) throws SQLException {
        int len = identifier.length();
        return len >= 1 && len <= 128
                && SIMPLE_IDENTIFIER_PATTERN.matcher(identifier).matches();
    }

    /**
     * Returns a {@code String} representing a National Character Set Literal
     * enclosed in single quotes and prefixed with an upper case letter N.
     * Any occurrence of a single quote within the string will be replaced
     * by two single quotes.
     *
     * <blockquote>
     * <table class="striped">
     * <caption>Examples of the conversion:</caption>
     * <thead>
     * <tr>
     * <th scope="col">Value</th>
     * <th scope="col">Result</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr> <th scope="row">Hello</th> <td>N'Hello'</td> </tr>
     * <tr> <th scope="row">G'Day</th> <td>N'G''Day'</td> </tr>
     * <tr> <th scope="row">'G''Day'</th>
     * <td>N'''G''''Day'''</td> </tr>
     * <tr> <th scope="row">I'''M</th> <td>N'I''''''M'</td>
     * <tr> <th scope="row">N'Hello'</th> <td>N'N''Hello'''</td> </tr>
     *
     * </tbody>
     * </table>
     * </blockquote>
     *
     * @param val a character string
     * @return the result of replacing every single quote character in the
     * argument by two single quote characters where this entire result is
     * then prefixed with 'N'.
     * @throws NullPointerException if val is {@code null}
     * @throws SQLException         if a database access error occurs
     * @implNote JDBC driver implementations may need to provide their own implementation
     * of this method in order to meet the requirements of the underlying
     * datasource. An implementation of enquoteNCharLiteral may accept a different
     * set of characters than that accepted by the same drivers implementation of
     * enquoteLiteral.
     */
    static String enquoteNCharLiteral(String val) throws SQLException {
        return "N'" + val.replace("'", "''") + "'";
    }
}
