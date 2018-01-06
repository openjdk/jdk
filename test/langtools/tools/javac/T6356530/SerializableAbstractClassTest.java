/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6356530 8191637
 * @summary -Xlint:serial does not flag abstract classes with persisent fields
 * @compile/fail/ref=SerializableAbstractClassTest.out -XDrawDiagnostics -Werror -Xlint:serial SerializableAbstractClassTest.java
 */

abstract class SerializableAbstractClassTest implements java.io.Serializable {
    // no serialVersionUID; error
    abstract void m2();

    static abstract class AWithUID implements java.io.Serializable {
        private static final long serialVersionUID = 0;
        void m(){}
    }

    interface I extends java.io.Serializable {
        // no need for serialVersionUID
    }

    interface IDefault extends java.io.Serializable {
        // no need for serialVersionUID
        default int m() { return 1; }
    }

    interface IUID extends java.io.Serializable {
        // no need for serialVersionUID, but not wrong
        static final long serialVersionUID = 0;
    }
}
