/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8200792
 * @summary PKCS12Attribute#hashCode is always constant -1
 */

import java.io.*;
import java.security.PKCS12Attribute;
import java.util.HashSet;
import java.util.Set;

public class HashCode {
    public static void main(String[] args) throws Exception {
        int h1 = new PKCS12Attribute("1.2.3.4", "AA").hashCode();
        int h2 = new PKCS12Attribute("2.3.4.5", "BB,CC").hashCode();
        if (h1 == -1 || h2 == -1 || h1 == h2) {
            throw new Exception("I see " + h1 + " and " + h2);
        }

        // Positive Equality + Hash Code Consistency
        PKCS12Attribute attr1 = new PKCS12Attribute("1.2.3.4", "AA");
        PKCS12Attribute attr2 = new PKCS12Attribute("2.3.4.5", "BB,CC");

        testHashCode(attr1);
        testHashCode(attr2);

        // Inequality and Hash Code Difference
        testInequality(
                new PKCS12Attribute("1.2.3.4", "AA"),
                new PKCS12Attribute("1.2.3.4", "BB")
        );
        testInequality(
                new PKCS12Attribute("1.2.3.4", "AA"),
                new PKCS12Attribute("2.3.4.5", "AA")
        );

        // Consistency Across Multiple Calls
        testRepeatHashCode(attr1);

        // Large Set Uniqueness Check
        testHashCodeUniqueness();

        //Multiple deserialization test
        testMultipleLogicalReconstruction(attr1);
    }
    private static void testHashCode(PKCS12Attribute attr) throws Exception {
        int originalHash = attr.hashCode();

        // Reconstruct a logically equivalent attribute
        PKCS12Attribute reconstructed = new PKCS12Attribute(attr.getName(), attr.getValue());

        if (!attr.equals(reconstructed)) {
            throw new Exception("Equality failed: " + attr + " vs " + reconstructed);
        }

        int newHash = reconstructed.hashCode();

        if (originalHash != newHash) {
            throw new Exception("Hash code mismatch: " + originalHash + " vs " + newHash);
        }

        System.out.println("Pass: " + attr.getName() + " = " + attr.getValue() +
                ", hashCode = " + originalHash);
    }

    // Expect inequality and different hash codes
    private static void testInequality(PKCS12Attribute a1, PKCS12Attribute a2) throws Exception {
        if (a1.equals(a2)) {
            throw new Exception("Unexpected equality: " + a1 + " == " + a2);
        }

        if (a1.hashCode() == a2.hashCode()) {
            System.out.println("Warning: Different attributes have same hashCode: " + a1.hashCode());
        } else {
            System.out.println("Pass: " + a1 + " != " + a2 + " and hashCodes differ");
        }
    }

    // Repeat hashCode call to ensure consistency
    private static void testRepeatHashCode(PKCS12Attribute attr) throws Exception {
        int h1 = attr.hashCode();
        int h2 = attr.hashCode();

        if (h1 != h2) {
            throw new Exception("Inconsistent hashCode for: " + attr);
        } else {
            System.out.println("Pass: hashCode repeat consistency for " + attr.getName());
        }
    }

    // Check hash uniqueness over a wide range of values
    private static void testHashCodeUniqueness() {
        Set<Integer> seen = new HashSet<>();
        int collisions = 0;
        for (int i = 0; i < 1000; i++) {
            PKCS12Attribute attr = new PKCS12Attribute("1.2.3." + i, "V" + i);
            if (!seen.add(attr.hashCode())) {
                System.out.println("Hash collision for: " + attr);
                collisions++;
            }
        }
        System.out.println("Hash uniqueness test complete. Collisions: " + collisions);
    }

    private static void testMultipleLogicalReconstruction(PKCS12Attribute original) throws Exception {
        System.out.println("Testing multiple logical reconstructions of: " + original);

        String name = original.getName();
        String value = original.getValue();
        int expectedHash = original.hashCode();

        for (int i = 0; i < 5; i++) {
            PKCS12Attribute copy = new PKCS12Attribute(name, value);

            if (copy == original) {
                throw new Exception("Reconstructed object is same reference as original (should be distinct)");
            }

            if (!original.equals(copy)) {
                throw new Exception("Reconstructed object not equal to original on iteration " + i);
            }

            if (copy.hashCode() != expectedHash) {
                throw new Exception("HashCode mismatch on iteration " + i + ": " + copy.hashCode() + " vs " + expectedHash);
            }
        }

        System.out.println("Pass: Multiple reconstructions produce equal and consistent objects");
    }
}
