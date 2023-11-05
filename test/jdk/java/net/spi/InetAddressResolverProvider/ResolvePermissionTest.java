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

import java.net.InetAddress;
import java.net.SocketPermission;
import java.net.UnknownHostException;
import java.security.Permission;
import java.util.logging.Logger;

import org.testng.Assert;
import org.testng.annotations.Test;

/*
 * @test
 * @summary Test that resolution of host name requires SocketPermission("resolve", <host name>)
 * permission when running with security manager and custom resolver provider installed.
 * @library lib providers/simple
 * @build test.library/testlib.ResolutionRegistry simple.provider/impl.SimpleResolverProviderImpl
 *        ResolvePermissionTest
 * @run testng/othervm -Dtest.dataFileName=nonExistentFile -Djava.security.manager=allow
 *                      ResolvePermissionTest
 */

public class ResolvePermissionTest {

    @Test
    public void withResolvePermission() throws Exception {
        testResolvePermission(true);
    }

    @Test
    public void noResolvePermission() throws Exception {
        testResolvePermission(false);
    }

    @SuppressWarnings("removal")
    private void testResolvePermission(boolean grantResolvePermission) throws Exception {
        // Set security manager which grants or denies permission to resolve 'javaTest.org' host
        var securityManager = new ResolvePermissionTest.TestSecurityManager(grantResolvePermission);
        try {
            System.setSecurityManager(securityManager);
            Class expectedExceptionClass = grantResolvePermission ?
                    UnknownHostException.class : SecurityException.class;
            var exception = Assert.expectThrows(expectedExceptionClass, () -> InetAddress.getByName("javaTest.org"));
            LOGGER.info("Got expected exception: " + exception);
        } finally {
            System.setSecurityManager(null);
        }
    }

    static class TestSecurityManager extends SecurityManager {
        final boolean allowJavaTestOrgResolve;

        public TestSecurityManager(boolean allowJavaTestOrgResolve) {
            this.allowJavaTestOrgResolve = allowJavaTestOrgResolve;
        }

        @Override
        public void checkPermission(Permission permission) {
            if (permission instanceof java.net.SocketPermission) {
                SocketPermission sockPerm = (SocketPermission) permission;
                if ("resolve".equals(sockPerm.getActions())) {
                    String host = sockPerm.getName();
                    LOGGER.info("Checking 'resolve' SocketPermission: " + permission);
                    if ("javaTest.org".equals(host) && !allowJavaTestOrgResolve) {
                        LOGGER.info("Denying 'resolve' permission for 'javaTest.org'");
                        throw new SecurityException("Access Denied");
                    }
                }
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ResolvePermissionTest.class.getName());
}
