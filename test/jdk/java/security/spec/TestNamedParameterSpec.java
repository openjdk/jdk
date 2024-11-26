/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8345057
 * @summary Test the existence of constants inside the
 *         java.security.spec.NamedParameterSpec class.
 * @run main TestNamedParameterSpec
 */
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import java.util.TreeSet;

public class TestNamedParameterSpec {

    // names of the static fields in NamedParameterSpec class
    private static String[] EXPECTED = {
        "ED25519", "ED448", "X25519", "X448",
        "ML_DSA_44", "ML_DSA_65", "ML_DSA_87",
        "ML_KEM_512", "ML_KEM_768", "ML_KEM_1024",
    };

    public static void main(String[] args) throws Exception {
        Arrays.sort(EXPECTED);
        var actual = getSortedConstNames(NamedParameterSpec.class);
        // both arrays should be sorted before comparison
        if (Arrays.compare(EXPECTED, actual) != 0) {
            System.out.println("expected: " + Arrays.toString(EXPECTED));
            System.out.println("actual: " + Arrays.toString(actual));
            throw new RuntimeException("Name check failed");
        }
        System.out.println("Test Passed");
    }

    public static String[] getSortedConstNames(Class<?> clazz) {
        TreeSet<String> names = new TreeSet<String>();
        for (Field field : clazz.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isPublic(mods) && Modifier.isStatic(mods)
                    && Modifier.isFinal(mods)){
                names.add(field.getName());
            }
        }
        return names.toArray(new String[0]);
    }
}
