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

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8370839
 * @summary Behavior of methods whose signature has package-private
 *          class or interfaces but the proxy interface is public
 * @run junit NonPublicSignaturesTest
 */
public class NonPublicSignaturesTest {
    enum Internal { INSTANCE }

    public interface InternalParameter {
        void call(Internal parameter);
    }

    @Test
    void testNonPublicParameter() throws Throwable {
        // Creation should be successful
        InternalParameter instance = (InternalParameter) Proxy.newProxyInstance(
                InternalParameter.class.getClassLoader(),
                new Class[] { InternalParameter.class },
                (_, _, _) -> null);
        instance.call(null);
        instance.call(Internal.INSTANCE);
    }

    public interface InternalReturn {
        Internal call();
    }

    @Test
    void testNonPublicReturn() throws Throwable {
        AtomicReference<Internal> returnValue = new AtomicReference<>();
        // Creation should be successful
        InternalReturn instance = (InternalReturn) Proxy.newProxyInstance(
                InternalReturn.class.getClassLoader(),
                new Class[] { InternalReturn.class },
                (_, _, _) -> returnValue.get());
        // checkcast does not perform access check for null
        returnValue.set(null);
        instance.call();
        // checkcast fails - proxy class cannot access the return type
        returnValue.set(Internal.INSTANCE);
        assertThrows(IllegalAccessError.class, instance::call);
    }
}
