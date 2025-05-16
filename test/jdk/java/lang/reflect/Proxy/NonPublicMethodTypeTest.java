/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8333854 8349716
 * @summary Test invoking a method in a proxy interface with package-private
 *          classes or interfaces in its method type
 * @run junit NonPublicMethodTypeTest
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

public final class NonPublicMethodTypeTest {
    interface NonPublicWorker {
        void work();
    }

    public interface PublicWorkable {
        void accept(NonPublicWorker worker);
    }

    public interface PublicWorkerFactory {
        NonPublicWorker provide();
    }

    @Test
    public void passParameter() {
        PublicWorkable proxy = (PublicWorkable) Proxy.newProxyInstance(
               NonPublicMethodTypeTest.class.getClassLoader(),
               new Class[] {PublicWorkable.class},
               (_, _, _) -> null);
        proxy.accept(() -> {}); // Call should not fail
    }

    @Test
    public void obtainReturn() {
        PublicWorkerFactory proxy = (PublicWorkerFactory) Proxy.newProxyInstance(
               NonPublicMethodTypeTest.class.getClassLoader(),
               new Class[] {PublicWorkerFactory.class},
               (_, _, _) -> (NonPublicWorker) () -> {});
        assertNotNull(proxy.provide()); // Call should not fail
    }

    @ParameterizedTest
    @ValueSource(classes = {PublicWorkable.class, PublicWorkerFactory.class})
    public void inspectReflectively(Class<?> intf) {
        var proxy = Proxy.newProxyInstance(
                NonPublicMethodTypeTest.class.getClassLoader(),
                new Class[] {intf},
                (_, _, _) -> null);

        assertSame(NonPublicWorker.class.getPackage(),
                   proxy.getClass().getPackage(),
                   "Proxy class must be able to access method parameter " +
                           "NonPublic type's package");
    }
}
