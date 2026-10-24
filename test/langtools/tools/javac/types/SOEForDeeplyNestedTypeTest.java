/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8337142
 * @summary StackOverflowError in Types.containsTypeRecursive with deeply nested type hierarchy
 * @compile SOEForDeeplyNestedTypeTest.java
 */

import java.util.List;

public class SOEForDeeplyNestedTypeTest {
    class T {
        List<M<?, ?>> xs = List.of(new M<>(One.class), new M<>(Two.class), new M<>(Two.class));
    }

    class M<R extends Eight, I extends Nine<? extends R>> {
        M(Class<? extends I> c) {}
    }

    class One implements Three<Five>, Six<One> {}
    class Two implements Four<Seven>, Six<Two> {}
    interface Three<R extends Eight> extends Nine<R> {}
    interface Four<R extends Eight> extends Nine<R> {}
    class Five extends Ten<Five> implements Eleven<Twelve>, Thirteen {}
    interface Six<FullKeyT extends TwentyTwo> extends TwentyTwo {}
    class Seven extends Ten<Seven> implements Eleven<Fourteen>, Thirteen {}
    interface Eight {}
    interface Nine<R extends Eight> extends TwentyTwo {}
    class Ten<K extends TwentyTwo> implements TwentyTwo {}
    interface Eleven<PkT extends Twenty> extends Twenty {}
    class Twelve extends Ten<Twelve> implements Eleven<Sixteen>, Thirteen {}
    interface Thirteen extends Seventeen {}
    class Fourteen extends Ten<Fourteen> implements Eleven<Fifteen>, Thirteen {}
    class Fifteen extends Ten<Fifteen> implements Eleven<Twelve>, Thirteen {}
    class Sixteen extends Nineteen<Sixteen> implements Eighteen, Thirteen {}
    interface Seventeen extends Twenty {}
    interface Eighteen extends Twenty {}
    class Nineteen<K extends Twenty> extends Ten<K> implements Twenty {}
    interface Twenty extends TwentyTwo {}
    class TwentyOne<R extends Eight> {}
    interface TwentyTwo extends Eight {}
}
