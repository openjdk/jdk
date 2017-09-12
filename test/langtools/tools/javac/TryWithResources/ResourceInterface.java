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
 * @bug 6970833
 * @author Maurizio Cimadamore
 * @summary Try-with-resource implementation throws an NPE during Flow analysis
 * @compile/fail/ref=ResourceInterface.out -XDrawDiagnostics ResourceInterface.java
 */

class ResourceInterface {
    public void test1() {
        try(Resource1 r1 = null) { }
    }

    public void test2() {
        try(Resource2 r2 = null) { }
    }

    static class E1 extends Exception {}

    static class E2 extends Exception {}


    interface C1 extends AutoCloseable {
       void close() throws E1;
    }

    interface C2 extends AutoCloseable {
       void close() throws E2;
    }

    interface C3 extends AutoCloseable {
       void close() throws E2, E1;
    }

    static interface Resource1 extends C1, C2 {}

    static interface Resource2 extends C1, C3 {}
}
