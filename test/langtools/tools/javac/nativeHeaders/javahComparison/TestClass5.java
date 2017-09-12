/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Native;

public class TestClass5 {
    @Native
    public static final int tc5 = 1;

    public class Inner1 {
        @Native
        public static final int tc5i1 = 2;

        public class Inner1A {
            @Native
            public static final int tc5i1i1a = 3;
        }

        public class Inner1B {
            @Native
            public static final int tc5i1i1b = 4;
        }
    }

    public class Inner2 {
        @Native
        public static final int tc521 = 5;

        public class Inner2A {
            @Native
            public static final int tc5i2i2a = 6;
        }

        public class Inner2B {
            @Native
            public static final int tc5i2i2b = 7;
        }
    }
}

