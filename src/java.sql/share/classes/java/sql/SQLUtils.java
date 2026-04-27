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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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
     // SQL 2023 reserved words
    private static final String[] SQL2023_RESERVED_WORDS = {
            "ABS", "ABSENT", "ACOS", "ALL", "ALLOCATE", "ALTER", "AND", "ANY",
            "ANY_VALUE", "ARE", "ARRAY", "ARRAY_AGG", "ARRAY_MAX_CARDINALITY",
            "AS", "ASENSITIVE", "ASIN", "ASYMMETRIC", "AT", "ATAN",
            "ATOMIC", "AUTHORIZATION", "AVG",
            "BEGIN", "BEGIN_FRAME", "BEGIN_PARTITION", "BETWEEN", "BIGINT",
            "BINARY", "BLOB", "BOOLEAN", "BOTH", "BTRIM", "BY",
            "CALL", "CALLED", "CARDINALITY", "CASCADED", "CASE", "CAST", "CEIL",
            "CEILING", "CHAR", "CHAR_LENGTH",
            "CHARACTER", "CHARACTER_LENGTH", "CHECK", "CLASSIFIER", "CLOB",
            "CLOSE", "COALESCE", "COLLATE", "COLLECT", "COLUMN", "COMMIT", "CONDITION",
            "CONNECT", "CONSTRAINT", "CONTAINS", "CONVERT", "COPY", "CORR", "CORRESPONDING",
            "COS", "COSH", "COUNT", "COVAR_POP", "COVAR_SAMP", "CREATE", "CROSS", "CUBE",
            "CUME_DIST", "CURRENT",
            "CURRENT_CATALOG", "CURRENT_DATE", "CURRENT_DEFAULT_TRANSFORM_GROUP", "CURRENT_PATH",
            "CURRENT_ROLE", "CURRENT_SCHEMA", "CURRENT_TIME", "CURRENT_TIMESTAMP",
            "CURRENT_TRANSFORM_GROUP_FOR_TYPE", "CURRENT_USER", "CURSOR", "CYCLE",
            "DATE", "DAY", "DEALLOCATE", "DEC", "DECFLOAT", "DECIMAL", "DECLARE", "DEFAULT",
            "DEFINE", "DELETE", "DENSE_RANK", "DEREF", "DESCRIBE", "DETERMINISTIC",
            "DISCONNECT", "DISTINCT", "DOUBLE", "DROP", "DYNAMIC",
            "EACH", "ELEMENT", "ELSE", "EMPTY", "END", "END_FRAME", "END_PARTITION",
            "END-EXEC", "EQUALS", "ESCAPE", "EVERY", "EXCEPT", "EXEC", "EXECUTE",
            "EXISTS", "EXP", "EXTERNAL", "EXTRACT",
            "FALSE", "FETCH", "FILTER", "FIRST_VALUE", "FLOAT", "FLOOR", "FOR", "FOREIGN", "FRAME_ROW",
            "FREE", "FROM", "FULL", "FUNCTION", "FUSION",
            "GET", "GLOBAL", "GRANT", "GREATEST", "GROUP", "GROUPING", "GROUPS",
            "HAVING", "HOLD", "HOUR",
            "IDENTITY", "IN", "INDICATOR", "INITIAL", "INNER", "INOUT", "INSENSITIVE",
            "INSERT", "INT", "INTEGER",
            "INTERSECT", "INTERSECTION", "INTERVAL", "INTO", "IS",
            "JOIN", "JSON", "JSON_ARRAY", "JSON_ARRAYAGG", "JSON_EXISTS",
            "JSON_OBJECT", "JSON_OBJECTAGG", "JSON_QUERY", "JSON_SCALAR",
            "JSON_SERIALIZE", "JSON_TABLE", "JSON_TABLE_PRIMITIVE", "JSON_VALUE",
            "LAG", "LANGUAGE", "LARGE", " LAST_VALUE", "LATERAL", "LEAD",
            "LEADING", "LEAST", "LEFT", "LIKE", "LIKE_REGEX", "LISTAGG",
            "LN", "LOCAL", "LOCALTIME", "LOCALTIMESTAMP", "LOG", "LOG10",
            "LOWER", "LPAD", "LTRIM",
            "MATCH", "MATCH_NUMBER", "MATCH_RECOGNIZE", "MATCHES", "MAX",
            "MEMBER", "MERGE", "METHOD", "MIN", "MINUTE", "MOD", "MODIFIES",
            "MODULE", "MONTH", "MULTISET",
            "NATIONAL", "NATURAL", "NCHAR", "NCLOB", "NEW", "NO", "NONE",
            "NORMALIZE", "NOT", "NTH_VALUE", "NTILE", "NULL", "NULLIF", "NUMERIC",
            "OCCURRENCES_REGEX", "OCTET_LENGTH", "OF", "OFFSET", "OLD", "OMIT",
            "ON", "ONE", "ONLY", "OPEN", "OR", "ORDER", "OUT", "OUTER", "OUTPUT",
            "OVER", "OVERLAPS", "OVERLAY",
            "PARAMETER", "PARTITION", "PATTERN", "PER", "PERCENT", "PERCENT_RANK",
            "PERCENTILE_CONT", "PERCENTILE_DISC", "PERIOD", "PORTION", "POSITION",
            "POSITION_REGEX", "POWER", "PRECEDES",
            "PRECISION", "PREPARE", "PRIMARY", "PROCEDURE", "PTF",
            "RANGE", "RANK", "READS", "REAL", "RECURSIVE", "REF", "REFERENCES",
            "REFERENCING", "REGR_AVGX", "REGR_AVGY", "REGR_COUNT", "REGR_INTERCEPT",
            "REGR_R2", "REGR_SLOPE", "REGR_SXX", "REGR_SXY", "REGR_SYY",
            "RELEASE", "RESULT", "RETURN", "RETURNS", "REVOKE", "RIGHT",
            "ROLLBACK", "ROLLUP", "ROW", "ROW_NUMBER", "ROWS", "RPAD", "RTRIM",
            "RUNNING",
            "SAVEPOINT", "SCOPE", "SCROLL", "SEARCH", "SECOND", "SEEK",
            "SELECT", "SENSITIVE", "SESSION_USER", "SET", "SHOW", "SIMILAR",
            "SIN", "SINH", "SKIP", "SMALLINT",
            "SOME", "SPECIFIC", "SPECIFICTYPE", "SQL", "SQLEXCEPTION", "SQLSTATE",
            "SQLWARNING", "SQRT", "START", "STATIC", "STDDEV_POP", "STDDEV_SAMP",
            "SUBMULTISET", "SUBSET", "SUBSTRING", "SUBSTRING_REGEX", "SUCCEEDS",
            "SUM", "SYMMETRIC", "SYSTEM", "SYSTEM_TIME", "SYSTEM_USER",
            "TABLE", "TABLESAMPLE", "TAN", "TANH", "THEN", "TIME", "TIMESTAMP",
            "TIMEZONE_HOUR", "TIMEZONE_MINUTE", "TO", "TRAILING", "TRANSLATE",
            "TRANSLATE_REGEX", "TRANSLATION", "TREAT", "TRIGGER", "TRIM",
            "TRIM_ARRAY", "TRUE", "TRUNCATE",
            "UESCAPE", "UNION", "UNIQUE", "UNKNOWN", "UNNEST", "UPDATE", "UPPER",
            "USER", "USING",
            "VALUE", "VALUES", "VALUE_OF", "VAR_POP", "VAR_SAMP", "VARBINARY",
            "VARCHAR", "VARYING", "VERSIONING",
            "WHEN", "WHENEVER", "WHERE", "WHILE", "WIDTH_BUCKET", "WINDOW",
            "WITH", "WITHIN", "WITHOUT",
            "YEAR"
    };
    private static final Set<String> SQL_RESERVED_WORDS =
            new HashSet<>(Arrays.asList(SQL2023_RESERVED_WORDS));

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
     * Returns a {@link #isSimpleIdentifier(String) simple SQL identifier} or a
     * delimited identifier.  A delimited identifier represents the name of a
     * database object such as a table, column, or view that is enclosed by a
     * delimiter, which is typically a double quote as defined by the SQL standard.
     * <p>
     * If {@code identifier} is a simple SQL identifier:
     * <ul>
     * <li>If {@code alwaysDelimit} is {@code false}, return the original value</li>
     * <li>if {@code alwaysDelimit} is {@code true}, enquote the original value
     * and return as a delimited identifier</li>
     * </ul>
     *
     * If {@code identifier} is not a simple SQL identifier, the delimited
     * {@code identifier} to be returned must be enclosed by the delimiter
     * returned from {@link DatabaseMetaData#getIdentifierQuoteString}. If
     * the datasource does not support delimited identifiers, a
     * {@code SQLFeatureNotSupportedException} is thrown.
     * <p>
     * A {@code SQLException} will be thrown if {@code identifier} contains any
     * invalid characters within a delimited identifier or the identifier length
     * is invalid for the datasource.
     *
     * @implSpec
     * The default implementation uses the following criteria to
     * determine a valid simple SQL identifier:
     * <ul>
     * <li>The string is not enclosed in double quotes</li>
     * <li>The first character is an alphabetic character from a ({@code '\u005C0061'})
     * through z ({@code '\u005Cu007A'}), or from A ({@code '\u005Cu0041'})
     * through Z ({@code '\u005Cu005A'})</li>
     * <li>The name only contains alphanumeric characters or the character "_"</li>
     * </ul>
     *
     * The default implementation will throw a {@code SQLException} if:
     * <ul>
     * <li> {@link DatabaseMetaData#getIdentifierQuoteString} does not return a
     * double quote</li>
     * <li>{@code identifier} contains a {@code null} character or double quote</li>
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
     * <th scope="row">"select"</th>
     * <td>false</td>
     * <td>"select"</td>
     * </tr>
     * <tr>
     * <th scope="row">"select"</th>
     * <td>true</td>
     * <td>"select"</td>
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
     * @implNote
     * JDBC driver implementations may need to provide their own implementation
     * of this method in order to meet the requirements of the underlying
     * datasource.
     * @param identifier a SQL identifier
     * @param alwaysDelimit indicates if a simple SQL identifier should be
     * returned as a delimited identifier
     * @return A simple SQL identifier or a delimited identifier
     * @throws SQLException if identifier is not a valid identifier
     * @throws SQLFeatureNotSupportedException if the datasource does not support
     * delimited identifiers
     * @throws NullPointerException if identifier is {@code null}
     */
    static String enquoteIdentifier(String delimiter, String identifier, boolean alwaysDelimit) throws SQLException {
        int len = identifier.length();
        if (len < 1 || len > 128) {
            throw new SQLException("Invalid identifier length");
        }
        if (!delimiter.equals("\"")) {
           throw new SQLException("Unsupported delimiter");
        }
        if (isSimpleIdentifier(identifier)) {
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
     * Returns whether {@code identifier} is a simple SQL identifier.
     * A simple SQL identifier is referred to as regular (or ordinary) identifier
     * within the SQL standard.  A regular identifier represents the name of a database
     * object such as a table, column, or view.
     * <p>
     * The rules for a regular Identifier are:
     * <ul>
     * <li>The first character is an alphabetic character from a ({@code '\u005Cu0061'})
     * through z ({@code '\u005Cu007A'}), or from A ({@code '\u005Cu0041'})
     * through Z ({@code '\u005Cu005A'})</li>
     * <li>The name only contains alphanumeric characters or the character "_"</li>
     * <li>It cannot be a SQL reserved word</li>
     * </ul>
     * <p>
     * A datasource may have additional rules for a regular identifier such as:
     * <ul>
     * <li>Supports additional characters within the name based on
     * the locale being used</li>
     * <li>Supports a different maximum length for the identifier</li>
     * </ul>
     *
     * @implSpec The default implementation uses the following criteria to
     * determine a valid simple SQL identifier:
     * <ul>
     * <li>The identifier is not enclosed in double quotes</li>
     * <li>The first character is an alphabetic character from a through z, or
     * from A through Z</li>
     * <li>The identifier only contains alphanumeric characters or the character
     * "_"</li>
     * <li>The identifier is not a SQL reserved word</li>
     * <li>The identifier is between 1 and 128 characters in length inclusive</li>
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
     * <tr>
     * <th scope="row">"select"</th>
     * <td>false</td>
     * <tr>
     * <th scope="row">"from"</th>
     * <td>false</td>
     * </tr>
     * </tbody>
     * </table>
     * </blockquote>
     * @implNote JDBC driver implementations may need to provide their own
     * implementation of this method in order to meet the requirements of the
     * underlying datasource.
     * @param identifier a SQL identifier
     * @return true if a simple SQL identifier, false otherwise
     * @throws NullPointerException if identifier is {@code null}
     * @throws SQLException if a database access error occurs
     */
    static boolean isSimpleIdentifier(String identifier) throws SQLException {
        int len = identifier.length();
        return !SQL_RESERVED_WORDS.contains(identifier.toUpperCase()) &&
                len >= 1 && len <= 128
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
