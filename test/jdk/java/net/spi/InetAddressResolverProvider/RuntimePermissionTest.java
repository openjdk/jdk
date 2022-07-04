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

import org.testng.Assert;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.security.Permission;
import java.util.ServiceConfigurationError;
import java.util.logging.Logger;

/*
 * @test
 * @summary Test that instantiation of InetAddressResolverProvider requires "inetAddressResolverProvider"
 *          RuntimePermission when running with security manager.
 * @library lib providers/simple
 * @build test.library/testlib.ResolutionRegistry simple.provider/impl.SimpleResolverProviderImpl
 *        RuntimePermissionTest
 * @run testng/othervm -Djava.security.manager=allow RuntimePermissionTest
 */

public class RuntimePermissionTest {

    @Test
    public void withRuntimePermission() throws Exception {
        testRuntimePermission(true);
    }

    @Test
    public void noRuntimePermission() throws Exception {
        testRuntimePermission(false);
    }

    @SuppressWarnings("removal")
    private void testRuntimePermission(boolean permitInetAddressResolver) throws Exception {
        // Set security manager which grants all permissions + RuntimePermission("inetAddressResolverProvider")
        var securityManager = new TestSecurityManager(permitInetAddressResolver);
        try {
            System.setSecurityManager(securityManager);
            if (permitInetAddressResolver) {
                InetAddress.getByName("javaTest.org");
            } else {
                ServiceConfigurationError sce =
                        Assert.expectThrows(ServiceConfigurationError.class,
                                            () -> InetAddress.getByName("javaTest.org"));
                LOGGER.info("Got ServiceConfigurationError: " + sce);
                Throwable cause = sce.getCause();
                Assert.assertTrue(cause instanceof SecurityException);
                Assert.assertTrue(cause.getMessage().contains(RUNTIME_PERMISSION_NAME));
            }
        } finally {
            System.setSecurityManager(null);
        }
    }

    static class TestSecurityManager extends SecurityManager {
        final boolean permitInetAddressResolver;

        public TestSecurityManager(boolean permitInetAddressResolver) {
            this.permitInetAddressResolver = permitInetAddressResolver;
            LOGGER.info("inetAddressResolverProvider permission is " +
                        (permitInetAddressResolver ? "granted" : "not granted"));
        }

        @Override
        public void checkPermission(Permission permission) {
            if (permission instanceof RuntimePermission) {
                LOGGER.info("Checking RuntimePermission: " + permission);
                if (RUNTIME_PERMISSION_NAME.equals(permission.getName()) && !permitInetAddressResolver) {
                    LOGGER.info("Denying '" + RUNTIME_PERMISSION_NAME + "' permission");
                    throw new SecurityException("Access Denied: " + RUNTIME_PERMISSION_NAME);
                }
            }
        }
    }

    private static final String RUNTIME_PERMISSION_NAME = "inetAddressResolverProvider";
    private static final Logger LOGGER = Logger.getLogger(RuntimePermissionTest.class.getName());

}
