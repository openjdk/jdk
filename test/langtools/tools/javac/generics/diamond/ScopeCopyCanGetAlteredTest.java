/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8260892
 * @summary Compilation fails: lambda parameter not visible in body when generics involved
 * @compile ScopeCopyCanGetAlteredTest.java
 */

import java.util.function.Function;
import java.util.function.IntFunction;

class ScopeCopyCanGetAlteredTest {
    interface GenericOp<A> {
        <B> A apply(IntFunction<B> func1, Function<B, A> func2);
    }

    static <A> GenericOp<A> foo(IntFunction<GenericOp<A>> f) {
        return null;
    }

    static <A> GenericOp<A> bar() {
        return foo((int arg) -> new GenericOp<>() {
            @Override
            public <B> A apply(IntFunction<B> func1, Function<B, A> func2) {
                return func2.apply(func1.apply(arg));
            }
        });
    }
}
