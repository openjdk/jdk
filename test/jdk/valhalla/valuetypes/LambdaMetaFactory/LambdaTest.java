/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test lambdas with parameter types or return type of value class
 * @enablePreview
 * @run junit/othervm LambdaTest
 */

import java.util.function.Function;
import java.util.function.IntFunction;

import jdk.internal.vm.annotation.NullRestricted;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LambdaTest {
    static value class V {
        int x;
        V(int x) {
            this.x = x;
        }

        static V get(int x) {
            return new V(x);
        }
    }

    static value class Value {
        @NullRestricted
        V v;
        Value(V v) {
            this.v = v;
        }
        static Value get(int x) {
            return new Value(new V(x));
        }
    }

    static int getV(V v) {
        return v.x;
    }

    static int getValue(Value v) {
        return v.v.x;
    }

    @Test
    public void testValueParameterType() {
        Function<Value, Integer> func1 = LambdaTest::getValue;
        assertTrue(func1.apply(new Value(new V(100))) == 100);

        Function<V, Integer> func2 = LambdaTest::getV;
        assertTrue(func2.apply(new V(200)) == 200);
    }

    @Test
    public void testValueReturnType() {
        IntFunction<Value> func1 = Value::get;
        assertEquals(func1.apply(10), new Value(new V(10)));

        IntFunction<V> func2 = V::get;
        assertEquals(func2.apply(20), new V(20));
    }
}
