
/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6843077
 * @summary random tests for new locations
 * @author Matt Papi
 * @compile -source 1.7 BasicTest.java
 */

import java.util.*;
import java.io.*;

@interface A {}
@interface B {}
@interface C {}
@interface D {}

/**
 * Tests basic JSR 308 parser functionality. We don't really care about what
 * the parse tree looks like, just that these annotations can be parsed.
 */
class BasicTest<T extends @A Object> extends @B LinkedList<T> implements @C List<T> {

    void test() {

        // Handle annotated class literals/cast types
        Class<?> c = @A String.class;
        Object o = (@A Object) "foo";

        // Handle annotated "new" expressions (except arrays; see ArrayTest)
        String s = new @A String("bar");

        boolean b = o instanceof @A Object;


        @A Map<@B List<@C String>, @D String> map =
            new @A HashMap<@B List<@C String>, @D String>();

        Class<? extends @A String> c2 = @A String.class;
    }

    // Handle receiver annotations
    // Handle annotations on a qualified identifier list
    void test2() @C @D throws @A IllegalArgumentException, @B IOException {

    }

    // Handle annotations on a varargs element type
    void test3(Object @A... objs) {

    }
}
