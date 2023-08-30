/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8314632
 * @summary Intra-case dominance check fails in the presence of a guard
 * @enablePreview
 * @compile/fail/ref=T8314632.out -XDrawDiagnostics T8314632.java
 */
public class T8314632 {
    void test1(Object obj) {
        switch (obj) {
            case Float _ -> {}
            case Integer _, CharSequence _, String _ when obj.hashCode() > 0 -> { } // error
            default -> throw new IllegalStateException("Unexpected value: " + obj);
        }
    }

    void test2(Object obj) {
        switch (obj) {
            case Float _ -> {}
            case Integer _, CharSequence _, String _ -> { }
            default -> throw new IllegalStateException("Unexpected value: " + obj); // error
        }
    }

    void test3(Object obj) {
        switch (obj) {
            case Float _, CharSequence _ when obj.hashCode() > 0 -> { } // OK
            case Integer _, String _     when obj.hashCode() > 0 -> { } // OK, since the previous case is guarded
            default -> throw new IllegalStateException("Unexpected value: " + obj);
        }
    }

    void test4(Object obj) {
        switch (obj) {
            case Float _, CharSequence _ -> { } // OK
            case Integer _, String _     when obj.hashCode() > 0 -> { } // error, since the previous case is unguarded
            default -> throw new IllegalStateException("Unexpected value: " + obj);
        }
    }
}
