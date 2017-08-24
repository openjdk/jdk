/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package p;

import java.security.AccessControlContext;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import static java.security.AccessController.doPrivileged;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeTest;
import static org.testng.Assert.*;

/**
 * Basic tests with a security manager to ensure that the provider code
 * is run with permissions restricted by whatever created the ServiceLoader
 * object.
 */

public class Tests {

    static final Permission PERM = new RuntimePermission("eatMuffin");

    static <T> PrivilegedAction<ServiceLoader<T>> loadAction(Class<T> service) {
        return () -> ServiceLoader.load(service);
    }

    static AccessControlContext withPermissions(Permission... perms) {
        Permissions p = new Permissions();
        for (Permission perm : perms) {
            p.add(perm);
        }
        ProtectionDomain pd = new ProtectionDomain(null, p);
        return new AccessControlContext(new ProtectionDomain[]{ pd });
    }

    static AccessControlContext noPermissions() {
        return withPermissions(/*empty*/);
    }

    @BeforeTest
    public void setSecurityManager() {
        class Policy extends java.security.Policy {
            private final Permissions perms;
            public Policy(Permission... permissions) {
                perms = new Permissions();
                for (Permission permission : permissions) {
                    perms.add(permission);
                }
            }
            public PermissionCollection getPermissions(CodeSource cs) {
                return perms;
            }
            public PermissionCollection getPermissions(ProtectionDomain pd) {
                return perms;
            }
            public boolean implies(ProtectionDomain pd, Permission p) {
                return perms.implies(p);
            }
            public void refresh() { }
        }
        Policy policy = new Policy(new AllPermission());
        Policy.setPolicy(policy);
        System.setSecurityManager(new SecurityManager());
    }

    @Test
    public void testConstructorUsingIteratorWithPermission() {
        ServiceLoader<S1> sl = doPrivileged(loadAction(S1.class), withPermissions(PERM));
        S1 obj = sl.iterator().next();
    }

    @Test
    public void testConstructorUsingStreamWithPermission() {
        ServiceLoader<S1> sl = doPrivileged(loadAction(S1.class), withPermissions(PERM));
        assertTrue(sl.stream().map(Provider::get).count() == 1);
    }

    @Test
    public void testConstructorUsingIteratorNoPermission() {
        ServiceLoader<S1> sl = doPrivileged(loadAction(S1.class), noPermissions());
        try {
            sl.iterator().next();
            assertTrue(false);
        } catch (ServiceConfigurationError e) {
            assertTrue(e.getCause() instanceof AccessControlException);
        }
    }

    @Test
    public void testConstructorUsingStreamNoPermission() {
        ServiceLoader<S1> sl = doPrivileged(loadAction(S1.class), noPermissions());
        try {
            sl.stream().map(Provider::get).count();
            assertTrue(false);
        } catch (ServiceConfigurationError e) {
            assertTrue(e.getCause() instanceof AccessControlException);
        }
    }

    @Test
    public void testFactoryMethodUsingIteratorWithPermission() {
        ServiceLoader<S2> sl = doPrivileged(loadAction(S2.class), withPermissions(PERM));
        S2 obj = sl.iterator().next();
    }

    @Test
    public void testFactoryMethodUsingStreamWithPermission() {
        ServiceLoader<S2> sl = doPrivileged(loadAction(S2.class), withPermissions(PERM));
        assertTrue(sl.stream().map(Provider::get).count() == 1);
    }

    @Test
    public void testFactoryMethodUsingIteratorNoPermission() {
        ServiceLoader<S2> sl = doPrivileged(loadAction(S2.class), noPermissions());
        try {
            sl.iterator().next();
            assertTrue(false);
        } catch (ServiceConfigurationError e) {
            assertTrue(e.getCause() instanceof AccessControlException);
        }
    }

    @Test
    public void testFactoryMethodUsingStreamNoPermission() {
        ServiceLoader<S2> sl = doPrivileged(loadAction(S2.class), noPermissions());
        try {
            sl.stream().map(Provider::get).count();
            assertTrue(false);
        } catch (ServiceConfigurationError e) {
            assertTrue(e.getCause() instanceof AccessControlException);
        }
    }


    // service types and implementations

    public static interface S1 { }
    public static interface S2 { }

    public static class P1 implements S1 {
        public P1() {
            AccessController.getContext().checkPermission(PERM);
        }
    }
    public static class P2 implements S2 {
        private P2() {
            AccessController.getContext().checkPermission(PERM);
        }
        public static S2 provider() {
            return new P2();
        }
    }
}
