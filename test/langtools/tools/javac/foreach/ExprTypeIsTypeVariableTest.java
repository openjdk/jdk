/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8273234
 * @summary extended 'for' with expression of type tvar causes the compiler to crash
 * @compile ExprTypeIsTypeVariableTest.java
 */

import java.util.*;

class ExprTypeIsTypeVariableTest {
    abstract class A {}

    abstract class ACD<E> implements Iterable<E> {
        public Iterator<E> iterator() {
            return null;
        }
    }

    abstract class ALD<E> extends ACD<E> implements List<E> {}

    abstract class ASP<NT extends A> extends ALD<A> {
        <P extends ASP<NT>> void foo(P prod) {
            for (A sym : prod) {}
        }
    }
}
