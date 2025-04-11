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

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

public final class NonPublicMethodTypeTest {
    interface NonPublicWorker {
        void work();
    }

    public interface PublicWorkable {
        void accept(NonPublicWorker worker);
    }

    public interface PublicWorkFactory {
        NonPublicWorker createWorker();
    }

    public interface PublicManyWorkFactory {
        NonPublicWorker[] createWorkers();
    }

    @Test
    public void testArgument() {
        PublicWorkable proxy = (PublicWorkable) Proxy.newProxyInstance(
               NonPublicMethodTypeTest.class.getClassLoader(),
               new Class[] {PublicWorkable.class},
               (_, _, _) -> null);
        assertNotSame(NonPublicWorker.class.getPackage(),
                proxy.getClass().getPackage(),
                "Proxy class should not be able to access method parameter " +
                        "NonPublic type's package");
        proxy.accept(() -> {}); // Call should not fail
    }

    @Test
    public void testReturnSuccessful() {
        PublicWorkFactory proxy = (PublicWorkFactory) Proxy.newProxyInstance(
                NonPublicMethodTypeTest.class.getClassLoader(),
                new Class[] {PublicWorkFactory.class},
                (_, _, _) -> (NonPublicWorker) () -> {});
        assertNotSame(NonPublicWorker.class.getPackage(),
                proxy.getClass().getPackage(),
                "Proxy class should not be able to access method return " +
                        "NonPublic type's package");
        assertNotNull(proxy.createWorker(), "Missing object to access check"); // Call should not fail
    }

    @Test
    public void testReturnCast() {
        PublicWorkFactory proxy = (PublicWorkFactory) Proxy.newProxyInstance(
                NonPublicMethodTypeTest.class.getClassLoader(),
                new Class[] {PublicWorkFactory.class},
                (_, _, _) -> (Runnable) () -> {});
        assertNotSame(NonPublicWorker.class.getPackage(),
                proxy.getClass().getPackage(),
                "Proxy class should not be able to access method return " +
                        "NonPublic type's package");
        assertThrows(ClassCastException.class, proxy::createWorker, "Missing object cast");
    }

    @Test
    public void testReturnArray() {
        PublicManyWorkFactory proxy = (PublicManyWorkFactory) Proxy.newProxyInstance(
                NonPublicMethodTypeTest.class.getClassLoader(),
                new Class[] {PublicManyWorkFactory.class},
                (_, _, _) -> new NonPublicWorker[] {() -> {}});
        assertNotSame(NonPublicWorker.class.getPackage(),
                proxy.getClass().getPackage(),
                "Proxy class should not be able to access method return " +
                        "NonPublic component type's package");
        assertEquals(1, proxy.createWorkers().length, "Sanity check"); // Call should not fail
    }
}
