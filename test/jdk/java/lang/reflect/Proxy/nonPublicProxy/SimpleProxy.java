/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.*;
import java.security.*;
import java.util.Arrays;

/*
 * @test
 * @bug 8004260
 * @summary Test making a proxy instance that implements a non-public
 *          interface with and without security manager installed
 * @build p.Foo p.Bar
 * @run main SimpleProxy
 */
public class SimpleProxy {
    public interface I {
        default String m() { return "I"; }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = SimpleProxy.class.getClassLoader();
        Class<?> fooClass = Class.forName("p.Foo");
        Class<?> barClass = Class.forName("p.Bar");

        // create a proxy instance of a non-public proxy interface
        // also test invocation of a default method
        makeProxy(loader, fooClass).testDefaultMethod("foo");
        makeProxy(loader, barClass, fooClass).testDefaultMethod("bar");

        // verify security permission check
        Policy.setPolicy(new SimplePolicy());
        System.setSecurityManager(new SecurityManager());
        // create a proxy instance of a public proxy interface should succeed
        makeProxy(loader, I.class).testDefaultMethod("I");
        try {
            // fail to create a proxy instance of a non-public proxy interface
            makeProxy(loader, barClass);
            throw new RuntimeException("should fail to new proxy instance of a non-public interface");
        } catch (AccessControlException e) {
            if (e.getPermission().getClass() != ReflectPermission.class ||
                    !e.getPermission().getName().equals("newProxyInPackage.p")) {
                throw e;
            }
        }
    }

    private static SimpleProxy makeProxy(ClassLoader loader, Class<?>... intfs) {
        Object proxy = Proxy.newProxyInstance(loader, intfs, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args)
                    throws Throwable {
                Class<?>[] intfs = proxy.getClass().getInterfaces();
                System.out.println("Proxy for " + Arrays.toString(intfs)
                        + " " + method.getName() + " is being invoked");
                if (method.getName().equals("m")) {
                    return Proxy.invokeDefaultMethod(proxy, method, args);
                }
                return null;
            }
        });
        return new SimpleProxy(proxy);
    }

    final Object proxy;
    SimpleProxy(Object proxy) {
        this.proxy = proxy;
    }

    /*
     * Verify if a default method "m" can be invoked successfully
     */
    void testDefaultMethod(String expected) throws ReflectiveOperationException {
        Method m = proxy.getClass().getDeclaredMethod("m");
        m.setAccessible(true);
        String name = (String)m.invoke(proxy);
        if (!expected.equals(name)) {
            throw new RuntimeException("return value: " + name + " expected: " + expected);
        }
    }

    static class SimplePolicy extends Policy {
        static final Policy DEFAULT_POLICY = Policy.getPolicy();
        final PermissionCollection permissions = new Permissions();
        SimplePolicy() {
            permissions.add(new SecurityPermission("getPolicy"));
            permissions.add(new ReflectPermission("suppressAccessChecks"));
        }
        public PermissionCollection getPermissions(ProtectionDomain domain) {
            return permissions;
        }

        public PermissionCollection getPermissions(CodeSource codesource) {
            return permissions;
        }

        public boolean implies(ProtectionDomain domain, Permission perm) {
            return permissions.implies(perm) ||
                    DEFAULT_POLICY.implies(domain, perm);
        }
    }
}
