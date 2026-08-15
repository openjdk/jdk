/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;

import javax.naming.spi.InitialContextFactory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/*
 * @test
 * @bug 8154193
 * @summary Verify that the com.sun.jndi.rmi.registry.RegistryContextFactory Service
 *          provided by the jdk.naming.rmi module isn't loaded by the boot loader
 * @run junit ${test.main.class}
 */
class InitialContextFactoryClassLoaderTest {

    private static final String RMI_INITIAL_CTX_FACTORY_SERVICE =
            "com.sun.jndi.rmi.registry.RegistryContextFactory";

    /*
     * Verifies that the javax.naming.spi.InitialContextFactory service provided by the
     * jdk.naming.rmi module isn't loaded through the boot loader
     */
    @Test
    void testClassLoader() throws Exception {
        final ServiceLoader<InitialContextFactory> serviceLoader =
                ServiceLoader.load(InitialContextFactory.class, null);
        final List<? extends Class<? extends InitialContextFactory>> serviceTypes = serviceLoader
                .stream()
                .map(Provider::get)
                .map(InitialContextFactory::getClass)
                .toList();
        System.err.println("Found InitialContextFactory services: " + serviceTypes);
        Class<?> rmiInitialCtxService = null;
        for (Class<?> klass : serviceTypes) {
            if (klass.getName().equals(RMI_INITIAL_CTX_FACTORY_SERVICE)) {
                rmiInitialCtxService = klass;
                break; // found the relevant service
            }
        }
        // verify that the RMI InitialContextFactory service was found by the ServiceLoader
        assertNotNull(rmiInitialCtxService, RMI_INITIAL_CTX_FACTORY_SERVICE
                + " was not found by ServiceLoader");
        // now verify its module and the classloader
        assertEquals("jdk.naming.rmi", rmiInitialCtxService.getModule().getName(),
                "unexpected module for " + RMI_INITIAL_CTX_FACTORY_SERVICE + " class");
        // we don't expect the service to be loaded by boot loader
        assertNotNull(rmiInitialCtxService.getClassLoader(), RMI_INITIAL_CTX_FACTORY_SERVICE
                + " was unexpectedly loaded by boot loader");
    }
}
