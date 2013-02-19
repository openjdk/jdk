/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file, and Oracle licenses the original version of this file under the BSD
 * license:
 */
/*
   Copyright 2009-2013 Attila Szegedi

   Licensed under both the Apache License, Version 2.0 (the "Apache License")
   and the BSD License (the "BSD License"), with licensee being free to
   choose either of the two at their discretion.

   You may not use this file except in compliance with either the Apache
   License or the BSD License.

   If you choose to use this file in compliance with the Apache License, the
   following notice applies to you:

       You may obtain a copy of the Apache License at

           http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing, software
       distributed under the License is distributed on an "AS IS" BASIS,
       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
       implied. See the License for the specific language governing
       permissions and limitations under the License.

   If you choose to use this file in compliance with the BSD License, the
   following notice applies to you:

       Redistribution and use in source and binary forms, with or without
       modification, are permitted provided that the following conditions are
       met:
       * Redistributions of source code must retain the above copyright
         notice, this list of conditions and the following disclaimer.
       * Redistributions in binary form must reproduce the above copyright
         notice, this list of conditions and the following disclaimer in the
         documentation and/or other materials provided with the distribution.
       * Neither the name of the copyright holder nor the names of
         contributors may be used to endorse or promote products derived from
         this software without specific prior written permission.

       THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
       IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
       TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
       PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL COPYRIGHT HOLDER
       BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
       CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
       SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
       BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
       WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
       OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
       ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jdk.internal.dynalink.beans;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.SecureClassLoader;

/**
 * A utility class to check whether a given class is in a package with restricted access e.g. "sun.*". These packages
 * are normally listed in the security property "package.access" for most JRE implementations, although we fortunately
 * don't rely on it but solely on {@link SecurityManager#checkPackageAccess(String)}).
 *
 * This class accomplishes the check in a fashion that works reliably even if Dynalink itself (and all the code on the
 * stack that led to the invocation) has the permission to access the restricted package.
 *
 * If Dynalink has a broad set of privileges (notably, it is loaded from boot or extension class path), then it loads
 * the {@link RestrictedPackageTester} class into a isolated secure class loader that gives it no permissions
 * whatsoever, and uses this completely unprivileged class to subsequently invoke
 * {@link SecurityManager#checkPackageAccess(String)}. This will reliably throw a {@link SecurityException} for every
 * restricted package even if Dynalink and other code on the stack have the requisite {@code "accessClassInPackage.*"}
 * {@link RuntimePermission} privilege.
 *
 * On the other hand, if Dynalink does not have a broad set of privileges normally granted by the boot or extension
 * class path, it will probably lack the privilege to create a new secure class loader into which to load the tester
 * class. In this case, it will invoke {@link SecurityManager#checkPackageAccess(String)} itself with the reasoning that
 * it will also be sufficient to discover whether a package is restricted or not.
 *
 * The rationale for this design is that if Dynalink is running as part of a privileged classpath - boot or extension
 * class path, it will have all privileges, so a security manager's package access check might succeed if all other code
 * on the stack when requesting linking with a particular restricted class is also privileged. A subsequent linking
 * request from less privileged code would then also succeed in requesting methods in privileged package. On the other
 * hand, if Dynalink is privileged, it will be able to delegate the package access check to the unprivileged class and
 * narrow the access based on its result. Finally, if Dynalink itself is unprivileged, it will not be able to load the
 * unprivileged class, but then it will also fail the security manager's package access.
 *
 * With this design, Dynalink effectively restrains itself from giving unauthorized access to restricted packages from
 * classes doing the linking in case it itself has access to those packages. The only way to defeat it would be to
 * selectively give Dynalink some {@code "accessClassInPackage.*"} permissions while denying it the privilege to
 * manipulate class loaders.
 */
class CheckRestrictedPackageInternal {
    private static final MethodHandle PACKAGE_ACCESS_CHECK = getPackageAccessCheckMethod();
    private static final String TESTER_CLASS_NAME = "jdk.internal.dynalink.beans.RestrictedPackageTester";

    /**
     * Returns true if the specified package has restricted access.
     * @param pkgName the name of the package to check.
     * @return true if the specified package has restricted access, false otherwise.
     * @throws NullPointerException if pkgName is null, or if there is {@link System#getSecurityManager()} returns null
     * as this method is only expected to be invoked in the presence of a security manager.
     */
    static boolean isRestrictedPackageName(String pkgName) {
        try {
            if(PACKAGE_ACCESS_CHECK != null) {
                // If we were able to load our unprivileged tester class, use it to check package access
                try {
                    PACKAGE_ACCESS_CHECK.invokeExact(pkgName);
                } catch(Error|RuntimeException e) {
                    throw e;
                } catch(Throwable t) {
                    throw new RuntimeException(t);
                }
            } else {
                // If we didn't have sufficient permissions to load our unprivileged tester class, we're definitely not
                // running in a privileged class path, so invoking SecurityManager.checkPackageAccess() directly should
                // have the same effect as going through an unprivileged tester.
                System.getSecurityManager().checkPackageAccess(pkgName);
            }
            return false;
        } catch(SecurityException e) {
            return true;
        }
    }

    private static MethodHandle getPackageAccessCheckMethod() {
        try {
            return AccessController.doPrivileged(new PrivilegedAction<MethodHandle>() {
                @Override
                public MethodHandle run() {
                    return getPackageAccessCheckMethodInternal();
                }
            });
        } catch(SecurityException e) {
            // We don't have sufficient privileges to load our tester class into a separate protection domain, so just
            // return null so isRestrictedPackageName() will default to itself invoking
            // SecurityManager.checkPackageAccess().
            return null;
        }
    }

    static MethodHandle getPackageAccessCheckMethodInternal() {
        try {
            // Can't use MethodHandles.lookup().findStatic() -- even though both this class and the loaded class are in
            // the same package, findStatic() will throw an IllegalAccessException since they have different class
            // loaders. That's why we have to use unreflect with a setAccessible(true)...
            final Method m = getTesterClass().getDeclaredMethod("checkPackageAccess", String.class);
            m.setAccessible(true);
            return MethodHandles.lookup().unreflect(m);
        } catch(IllegalAccessException|NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private static Class<?> getTesterClass() {
        final ClassLoader loader = getTesterClassLoader();
        try {
            final Class<?> checkerClass = Class.forName(TESTER_CLASS_NAME, true, loader);
            // Sanity check to ensure we didn't accidentally pick up the class from elsewhere
            if(checkerClass.getClassLoader() != loader) {
                throw new AssertionError(TESTER_CLASS_NAME + " was loaded from a different class loader");
            }
            return checkerClass;
        } catch(ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private static ClassLoader getTesterClassLoader() {
        // We deliberately override loadClass instead of findClass so that we don't give a chance to finding this
        // class already loaded anywhere else. Not that there's a big possibility for this, especially since the parent
        // class loader is the bootstrap class loader, but still...
        return new SecureClassLoader(null) {

            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if(name.equals(TESTER_CLASS_NAME)) {
                    final byte[] bytes = getTesterClassBytes();
                    // Define the class with a protection domain that grants no permissions.
                    Class<?> clazz = defineClass(name, bytes, 0, bytes.length, new ProtectionDomain(null,
                            new Permissions()));
                    if(resolve) {
                        resolveClass(clazz);
                    }
                    return clazz;
                }

                return super.loadClass(name, resolve);
            }
        };
    }

    static byte[] getTesterClassBytes() {
        try {
            final InputStream in = CheckRestrictedPackage.class.getResourceAsStream("RestrictedPackageTester.class");
            try {
                final ByteArrayOutputStream out = new ByteArrayOutputStream(2048);
                for(;;) {
                    final int b = in.read();
                    if(b == -1) {
                        break;
                    }
                    out.write(b);
                }
                return out.toByteArray();
            } finally {
                in.close();
            }
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
