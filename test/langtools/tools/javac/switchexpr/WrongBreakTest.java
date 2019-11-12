/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8223305
 * @summary Ensure javac is not crashing for wrong breaks.
 * @compile/fail/ref=WrongBreakTest.out -XDrawDiagnostics -XDshould-stop.at=FLOW WrongBreakTest.java
 */

public class WrongBreakTest {

    void test(int i) {
        int s = 0;
        int j = switch (s) { default: break; };
        test(switch (s) { default: yield; });
        Runnable r = () -> {
            yield 15;
        };
        while (true) {
            yield 15;
        }
    }

    void test(Object o) {}
}
