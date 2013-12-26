/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;
import java.util.Comparator;
import sun.misc.JavaLangAccess;
import sun.misc.SharedSecrets;

/*
 * @test
 * @summary Test JavaLangAccess.newUnsafeString
 * @bug 8013528
 * @compile -XDignore.symbol.file NewUnsafeString.java
 */
public class NewUnsafeString {

    static final JavaLangAccess jla = SharedSecrets.getJavaLangAccess();

    public static void testNewUnsafeString() {
        String benchmark = "exemplar";
        String constructorCopy = new String(benchmark);
        char[] jlaChars = benchmark.toCharArray();
        String jlaCopy = jla.newStringUnsafe(jlaChars);

        if (benchmark == constructorCopy) {
            throw new Error("should be different instances");
        }
        if (!benchmark.equals(constructorCopy)) {
            throw new Error("Copy not equal");
        }
        if (0 != Objects.compare(benchmark, constructorCopy, Comparator.naturalOrder())) {
            throw new Error("Copy not equal");
        }

        if (benchmark == jlaCopy) {
            throw new Error("should be different instances");
        }
        if (!benchmark.equals(jlaCopy)) {
            throw new Error("Copy not equal");
        }
        if (0 != Objects.compare(benchmark, jlaCopy, Comparator.naturalOrder())) {
            throw new Error("Copy not equal");
        }

        if (constructorCopy == jlaCopy) {
            throw new Error("should be different instances");
        }
        if (!constructorCopy.equals(jlaCopy)) {
            throw new Error("Copy not equal");
        }
        if (0 != Objects.compare(constructorCopy, jlaCopy, Comparator.naturalOrder())) {
            throw new Error("Copy not equal");
        }

        // The following is extremely "evil". Never ever do this in non-test code.
        jlaChars[0] = 'X';
        if (!"Xxemplar".equals(jlaCopy)) {
            throw new Error("jla.newStringUnsafe did not use provided string");
        }

    }

    public static void main(String[] args) {
        testNewUnsafeString();
    }
}
