/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test dynamic proxies with parameter types or return type of value class
 * @enablePreview
 * @run testng/othervm ProxyTest
 */

import java.lang.reflect.*;
import java.util.Arrays;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class ProxyTest {
    static value class V {
        int v;
        V(int v) {
            this.v = v;
        }
    }

    static value class P {
        int p;
        P(int p) {
            this.p = p;
        }
    }

    interface I {
        int getV(V v);
        int getP(P p);
    }

    interface J {
        int[] getV(V[] v);
    }

    @Test
    public void testProxy() throws Exception {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                switch (method.getName()) {
                    case "getV":
                        V v = (V)args[0];
                        return v.v;
                    case "getP":
                        P p = (P)args[0];
                        return p.p;
                    default:
                        throw new UnsupportedOperationException(method.toString());
                }
            }
        };

        Class<?>[] intfs = new Class<?>[] { I.class };
        I i = (I) Proxy.newProxyInstance(ProxyTest.class.getClassLoader(), intfs, handler);

        assertTrue(i.getV(new V(100)) == 100);
        assertTrue(i.getP(new P(200)) == 200);
    }

    @Test
    public void testValueArrayType() {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                switch (method.getName()) {
                    case "getV":
                        V[] vs = (V[])args[0];
                        return Arrays.stream(vs).mapToInt(v -> v.v).toArray();
                    default:
                        throw new UnsupportedOperationException(method.toString());
                }
            }
        };

        Class<?>[] intfs = new Class<?>[] { J.class };
        J j = (J) Proxy.newProxyInstance(ProxyTest.class.getClassLoader(), intfs, handler);

        V[] array = new V[] { new V(10), new V(20), new V(30)};
        assertEquals(j.getV(array), new int[] { 10, 20, 30});
    }
}
