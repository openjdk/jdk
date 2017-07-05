/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8022343
 * @summary make sure Class.getAnnotatedSuperclass() returns null when specified to do so
 */

public class GetAnnotatedSuperclass {
    private static final Class<?>[] testData = {
        Object.class,
        If.class,
        Object[].class,
        void.class,
        int.class,
    };

    public static void main(String[] args) throws Exception {
        int failed = 0;
        for (Class<?> toTest : testData) {
            Object res = toTest.getAnnotatedSuperclass();

            if (res != null) {
                failed++;
                System.out.println(toTest + ".getAnnotatedSuperclass() returns: "
                        + res + ", should be null");
            }
        }

        if (failed != 0)
            throw new RuntimeException("Test failed, check log for details");
    }

    interface If {}
}
