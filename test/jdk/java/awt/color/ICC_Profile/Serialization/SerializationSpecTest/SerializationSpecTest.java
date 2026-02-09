/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.OptionalDataException;

/**
 * @test
 * @bug 8367384
 * @summary Verify ICC_Profile serialization per spec, all name/data cases
 */
public final class SerializationSpecTest {

    public static void main(String[] args) throws Exception {
        // Serialization form for ICC_Profile includes version, profile name,
        // and profile data. If the name is invalid or does not match a standard
        // profile, the data is used. An exception is thrown only if both the
        // name and the data are invalid, or if one of them is missing or is of
        // the wrong type.

        // Naming conventions used in test file names:
        // null     : null reference
        // valid    : valid standard profile name or valid profile data (byte[])
        // invalid  : unrecognized name or data with incorrect ICC header
        // wrongType: incorrect type, e.g., int[] instead of String or byte[]

        // No name or data
        test("empty", OptionalDataException.class);

        // Cases where only the profile name is present (no profile data)
        test("null", OptionalDataException.class);
        test("valid", OptionalDataException.class);
        test("invalid", OptionalDataException.class);
        test("wrongType", InvalidObjectException.class);

        // The test files are named as <name>_<data>.ser
        test("null_null", InvalidObjectException.class);
        test("null_valid", null); // valid data is enough if name is null
        test("null_invalid", InvalidObjectException.class);
        test("null_wrongType", InvalidObjectException.class);

        test("invalid_null", InvalidObjectException.class);
        test("invalid_valid", null); // valid data is enough if name is invalid
        test("invalid_invalid", InvalidObjectException.class);
        test("invalid_wrongType", InvalidObjectException.class);

        test("wrongType_null", InvalidObjectException.class);
        test("wrongType_valid", InvalidObjectException.class);
        test("wrongType_invalid", InvalidObjectException.class);
        test("wrongType_wrongType", InvalidObjectException.class);

        test("valid_null", null); // the valid name is enough
        test("valid_valid", null); // the valid name is enough
        test("valid_invalid", null); // the valid name is enough
        test("valid_wrongType", InvalidObjectException.class);
    }

    private static void test(String test, Class<?> expected) {
        String fileName = test + ".ser";
        File file = new File(System.getProperty("test.src", "."), fileName);
        Class<?> actual = null;
        try (var fis = new FileInputStream(file);
             var ois = new ObjectInputStream(fis))
        {
            ois.readObject();
        } catch (Exception e) {
            actual = e.getClass();
        }
        if (actual != expected) {
            System.err.println("Test: " + test);
            System.err.println("Expected: " + expected);
            System.err.println("Actual: " + actual);
            throw new RuntimeException("Test failed");
        }
    }
}
