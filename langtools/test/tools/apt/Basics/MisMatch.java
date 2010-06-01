/*
 * Copyright (c) 2004, 2007, Oracle and/or its affiliates. All rights reserved.
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



/**
 * Class that uses constructs whose language and vm interpretation
 * differ.
 */
public class MisMatch {
    static final int constant = 3;
    static int notConstant = 4;
    private static strictfp class NestedClass {
    }

    protected abstract class AbstractNestedClass {
        /**
         * Another doc comment.
         *
         * This one has multiple lines.
         */
        void myMethod() throws RuntimeException , Error {}

        abstract void myAbstractMethod();
    }

    void VarArgsMethod1(Number... num) {
        ;
    }

    void VarArgsMethod2(float f, double d, Number... num) {
        ;
    }
}

@interface Colors {
}

interface Inter {
    void interfaceMethod();
}

enum MyEnum {
    RED,
    GREEN,
    BLUE;
}
