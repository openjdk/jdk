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
 * @summary symbol not found error, implicit lambdas and diamond constructor invocations
 * @compile CantFindSymbolImplicitLambdaAndDiamondTest.java
 */

import java.util.function.Consumer;

class CantFindSymbolImplicitLambdaAndDiamondTest {
    static class B<T>{}

    static class A1 {
        <T> A1(Consumer<T> cons) {}
    }

    static class A2<T> {
        A2(Consumer<T> cons) {}
    }

    public void mount() {
        new A1(inHours ->
                new B<>() {{
                    System.out.println(inHours);
                }});

        new A2<>(inHours ->
            new B<>() {{
                System.out.println(inHours);
            }});
    }
}
