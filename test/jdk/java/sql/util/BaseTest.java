/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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
package util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.JDBCType;
import java.sql.SQLException;
import java.util.stream.Stream;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.provider.Arguments;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BaseTest {

    protected final String reason = "reason";
    protected final String state = "SQLState";
    protected final String cause = "java.lang.Throwable: cause";
    protected final Throwable t = new Throwable("cause");
    protected final Throwable t1 = new Throwable("cause 1");
    protected final Throwable t2 = new Throwable("cause 2");
    protected final int errorCode = 21;
    protected final String[] msgs = {"Exception 1", "cause 1", "Exception 2",
        "Exception 3", "cause 2"};
    private static final String MAX_LENGTH_IDENTIFIER = "a".repeat(128);

    /*
     * Take some form of SQLException, serialize and deserialize it
     */
    @SuppressWarnings("unchecked")
    protected <T extends SQLException> T
            createSerializedException(T ex)
            throws IOException, ClassNotFoundException {
        return (T) serializeDeserializeObject(ex);
    }

    /*
     * Utility method to serialize and deserialize an object
     */
    @SuppressWarnings("unchecked")
    protected <T> T serializeDeserializeObject(T o)
            throws IOException, ClassNotFoundException {
        T o1;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(o);
        }
        try (ObjectInputStream ois
                = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            o1 = (T) ois.readObject();
        }
        return o1;
    }

    /*
     * DataProvider used to specify the standard JDBC Types
     */
    protected Stream<Integer> jdbcTypes() {
        return Stream.of(JDBCType.values()).map(JDBCType::getVendorTypeNumber);
    }

    /*
     * DataProvider used to provide strings that will be used to validate
     * that enquoteLiteral converts a string to a literal and every instance of
     * a single quote will be converted into two single quotes in the literal.
     */
    protected Stream<Arguments> validEnquotedLiteralValues() {
        return Stream.of(
                Arguments.of("Hello", "'Hello'"),
                Arguments.of("G'Day", "'G''Day'"),
                Arguments.of("'G''Day'", "'''G''''Day'''"),
                Arguments.of("I'''M", "'I''''''M'"),
                Arguments.of("The Dark Knight", "'The Dark Knight'")
        );
    }

    /*
     * DataProvider used to provide strings that will be used to validate
     * that enqouteIdentifier returns a simple SQL Identifier or a
     * quoted identifier
     */
    protected Stream<Arguments> validEnquotedIdentifierValues() {
        return Stream.of(
                Arguments.of("b", false, "b"),
                Arguments.of("b", true, "\"b\""),
                Arguments.of(MAX_LENGTH_IDENTIFIER, false, MAX_LENGTH_IDENTIFIER),
                Arguments.of(MAX_LENGTH_IDENTIFIER, true, "\"" + MAX_LENGTH_IDENTIFIER + "\""),
                Arguments.of("Hello", false, "Hello"),
                Arguments.of("Hello", true, "\"Hello\""),
                Arguments.of("G'Day", false, "\"G'Day\""),
                Arguments.of("G'Day", true, "\"G'Day\""),
                Arguments.of("Bruce Wayne", false, "\"Bruce Wayne\""),
                Arguments.of("Bruce Wayne", true, "\"Bruce Wayne\""),
                Arguments.of("select", false, "\"select\""),
                Arguments.of("table", true, "\"table\""),
                Arguments.of("GoodDay$", false, "\"GoodDay$\""),
                Arguments.of("GoodDay$", true, "\"GoodDay$\"")
        );
    }

    /*
     * DataProvider used to provide strings are invalid for enquoteIdentifier
     * resulting in a SQLException being thrown
     */
    protected Stream<Arguments> invalidEnquotedIdentifierValues() {
        return Stream.of(
                Arguments.of("Hel\"lo", false),
                Arguments.of("\"Hel\"lo\"", true),
                Arguments.of("Hello" + '\0', false),
                Arguments.of("", false),
                Arguments.of(MAX_LENGTH_IDENTIFIER + 'a', false)
        );
    }

    /*
     * DataProvider used to provide strings that will be used to validate
     * that isSimpleIdentifier returns the correct value based on the
     * identifier specified.
     */
    protected Stream<Arguments> simpleIdentifierValues() {
        return Stream.of(
                Arguments.of("b", true),
                Arguments.of("Hello", true),
                Arguments.of("\"Gotham\"", false),
                Arguments.of("G'Day", false),
                Arguments.of("Bruce Wayne", false),
                Arguments.of("GoodDay$", false),
                Arguments.of("Dick_Grayson", true),
                Arguments.of("Batmobile1966", true),
                Arguments.of(MAX_LENGTH_IDENTIFIER, true),
                Arguments.of(MAX_LENGTH_IDENTIFIER + 'a', false),
                Arguments.of("", false),
                Arguments.of("select", false)
            );
    }

    /*
     * DataProvider used to provide strings that will be used to validate
     * that enquoteNCharLiteral converts a string to a National Character
     * literal and every instance of
     * a single quote will be converted into two single quotes in the literal.
     */
    protected Stream<Arguments> validEnquotedNCharLiteralValues() {
        return Stream.of(
                Arguments.of("Hello", "N'Hello'"),
                Arguments.of("G'Day", "N'G''Day'"),
                Arguments.of("'G''Day'", "N'''G''''Day'''"),
                Arguments.of("I'''M", "N'I''''''M'"),
                Arguments.of("N'Hello'", "N'N''Hello'''"),
                Arguments.of("The Dark Knight", "N'The Dark Knight'")
        );
    }
}
