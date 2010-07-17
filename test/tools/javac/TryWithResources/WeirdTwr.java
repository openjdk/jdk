/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6911256 6964740
 * @author Joseph D. Darcy
 * @summary Strange TWRs
 * @compile/fail -source 6 WeirdTwr.java
 * @compile WeirdTwr.java
 * @run main WeirdTwr
 */

public class WeirdTwr implements AutoCloseable {
    private static int closeCount = 0;
    public static void main(String... args) {
        try(WeirdTwr r1 = new WeirdTwr(); WeirdTwr r2 = r1) {
            if (r1 != r2)
                throw new RuntimeException("Unexpected inequality.");
        }
        if (closeCount != 2)
            throw new RuntimeException("bad closeCount" + closeCount);
    }

    public void close() {
        closeCount++;
    }
}
