/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8200792 8355379
 * @summary Tests PKCS12Attribute#hashCode correctness, stability, and caching after Stable annotation fix
 */
import java.security.PKCS12Attribute;
import java.util.HashSet;
import java.util.Set;

public class HashCode {
    public static void main(String[] args) throws Exception {
        int h1 = new PKCS12Attribute("1.2.3.4", "AA").hashCode();
        int h2 = new PKCS12Attribute("2.3.4.5", "BB,CC").hashCode();
        if (h1 == -1 || h2 == -1 || h1 == h2) {
            throw new Exception("Unexpected hashCodes: " + h1 + " and " + h2);
        }

        PKCS12Attribute attr1 = new PKCS12Attribute("1.2.3.4", "AA");

        // Equality and hash code consistency
        testHashCode(attr1);

        // Inequality and hash code difference
        testInequality(new PKCS12Attribute("1.2.3.4", "AA"),
                new PKCS12Attribute("1.2.3.4", "BB"));
        testInequality(new PKCS12Attribute("1.2.3.4", "AA"),
                new PKCS12Attribute("2.3.4.5", "AA"));

        // Repeated hashCode consistency
        testRepeatHashCode(attr1);

        // Hash code uniqueness stress test
        testHashCodeUniqueness();
    }

    private static void testHashCode(PKCS12Attribute attr) throws Exception {
        int originalHash = attr.hashCode();

        PKCS12Attribute reconstructed = new PKCS12Attribute(attr.getName(), attr.getValue());

        if (!attr.equals(reconstructed)) {
            throw new Exception("Equality failed for: name=" + attr.getName() +
                    ", value=" + attr.getValue());
        }

        int newHash = reconstructed.hashCode();
        if (originalHash != newHash) {
            throw new Exception("Hash code mismatch for: name=" + attr.getName() +
                    ", original=" + originalHash + ", new=" + newHash);
        }

        System.out.println("Pass: name=" + attr.getName() + ", value=" + attr.getValue() +
                ", hashCode=" + originalHash);
    }

    private static void testInequality(PKCS12Attribute a1, PKCS12Attribute a2) throws Exception {
        if (a1.equals(a2)) {
            throw new Exception("Unexpected equality: name=" + a1.getName() +
                    ", values=" + a1.getValue() + " vs " + a2.getValue());
        }

        if (a1.hashCode() == a2.hashCode()) {
            System.out.println("Warning: Different attributes share hashCode: " + a1.hashCode());
        } else {
            System.out.println("Pass: name=" + a1.getName() +
                    ", values differ and hashCodes differ");
        }
    }

    private static void testRepeatHashCode(PKCS12Attribute attr) throws Exception {
        int h1 = attr.hashCode();
        int h2 = attr.hashCode();

        if (h1 != h2) {
            throw new Exception("Inconsistent hashCode for: name=" + attr.getName() +
                    ", value=" + attr.getValue());
        }

        System.out.println("Pass: repeat hashCode consistency for name=" + attr.getName());
    }

    private static void testHashCodeUniqueness() {
        Set<Integer> seen = new HashSet<>();
        int collisions = 0;

        for (int i = 0; i < 1000; i++) {
            PKCS12Attribute attr = new PKCS12Attribute("1.2.3." + i, "V" + i);
            if (!seen.add(attr.hashCode())) {
                System.out.println("Hash collision: name=" + attr.getName() +
                        ", value=" + attr.getValue());
                collisions++;
            }
        }

        System.out.println("Hash uniqueness test complete. Collisions: " + collisions);
    }
}
