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

import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ASM4;

import java.io.IOException;
import java.io.InputStream;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.security.SecureClassLoader;
import java.security.SecureRandom;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;

/**
 * A utility class that can load a class with specified name into an isolated zero-permissions protection domain. It can
 * be used to load classes that perform security-sensitive operations with no privileges at all, therefore ensuring such
 * operations will only succeed if they would require no permissions, as well as to make sure that if these operations
 * bind some part of the security execution context to their results, the bound security context is completely
 * unprivileged. Such measures serve as firebreaks against accidental privilege escalation.
 */
final class SandboxClassLoader {
    private final String className;
    private final String randomizedClassName;

    private SandboxClassLoader(String className) {
        this.className = className;
        final String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        this.randomizedClassName = "randomPackage" + Long.toHexString(new SecureRandom().nextLong()) + "." + simpleClassName;
    }

    /**
     * Load the named class into a zero-permissions protection domain. Even if the class is already loaded into the
     * Dynalink's class loader, an independent class is created from the same bytecode, thus the returned class will
     * never be identical with the one that might already be loaded. The class to be loaded is supposed to be package
     * private and have no public constructors. This is not a functional requirement, but it is enforced to ensure that
     * the original class was made adequately inaccessible. The returned class will be public and its constructors will
     * be changed to public. The only permission given to the returned class will be
     * {@code accessClassInPackage.jdk.internal.dynalink.beans.sandbox}. That package should be used solely to define
     * SPI interfaces implemented by the loaded class.
     * @param className the fully qualified name of the class to load
     * @return the loaded class, renamed to a random package, made public, its constructors made public, and lacking any
     * permissions except access to the sandbox package.
     * @throws SecurityException if the calling code lacks the {@code createClassLoader} runtime permission. This
     * normally means that Dynalink itself is running as untrusted code, and whatever functionality was meant to be
     * isolated into an unprivileged class is likely okay to be used directly too.
     */
    static Class<?> loadClass(String className) throws SecurityException {
        return new SandboxClassLoader(className).loadClass();
    }

    private Class<?> loadClass() throws SecurityException {
        final ClassLoader loader = createClassLoader();
        try {
            final Class<?> clazz = Class.forName(randomizedClassName, true, loader);
            // Sanity check to ensure we didn't accidentally pick up the class from elsewhere
            if(clazz.getClassLoader() != loader) {
                throw new AssertionError(randomizedClassName + " was loaded from a different class loader");
            }
            return clazz;
        } catch(ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private ClassLoader createClassLoader() throws SecurityException {
        final String lclassName = this.randomizedClassName;
        // We deliberately override loadClass instead of findClass so that we don't give a chance to finding this
        // class already loaded anywhere else. We use this class' loader as the parent class loader as the loaded class
        // needs to be able to access implemented interfaces from the sandbox package.
        return new SecureClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if(name.equals(lclassName)) {
                    final byte[] bytes = getClassBytes();
                    // Define the class with a protection domain that grants (almost) no permissions.
                    Class<?> clazz = defineClass(name, bytes, 0, bytes.length, createMinimalPermissionsDomain());
                    if(resolve) {
                        resolveClass(clazz);
                    }
                    return clazz;
                }

                final int i = name.lastIndexOf('.');
                if (i != -1) {
                    final SecurityManager sm = System.getSecurityManager();
                    if (sm != null) {
                        sm.checkPackageAccess(name.substring(0, i));
                    }
                }
                return super.loadClass(name, resolve);
            }
        };
    }

    /**
     * Create a no-permissions protection domain. Except, it's not really a no-permissions protection domain, since we
     * need to give the protection domain the permission to access the sandbox package where the interop interfaces are
     * defined.
     * @return a new (almost) no-permission protection domain.
     */
    private static ProtectionDomain createMinimalPermissionsDomain() {
        final Permissions p = new Permissions();
        p.add(new RuntimePermission("accessClassInPackage.jdk.internal.dynalink.beans.sandbox"));
        return new ProtectionDomain(null, p);
    }

    private byte[] getClassBytes() {
        try(final InputStream in = getClass().getResourceAsStream("/" + className.replace('.', '/') + ".class")) {
            final ClassReader cr = new ClassReader(in);
            final ClassWriter cw = new ClassWriter(cr, 0);
            cr.accept(new ClassVisitor(ASM4, cw) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName,
                        String[] interfaces) {
                    // Rename the class to its random name, and make it public (otherwise we won't be able to
                    // instantiate it). The privileged template class is package-private.
                    if((access & ACC_PUBLIC) != 0) {
                        throw new IllegalArgumentException("Class " + className + " must be package-private");
                    }
                    super.visit(version, access | ACC_PUBLIC, randomizedClassName.replace('.', '/'),
                            signature, superName, interfaces);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                        String[] exceptions) {
                    // Make the constructor(s) public (otherwise we won't be able to instantiate the class). The
                    // privileged template's constructor(s) should not be public.
                    final boolean isCtor = "<init>".equals(name);
                    if(isCtor && ((access & ACC_PUBLIC) != 0)) {
                        throw new IllegalArgumentException("Class " + className + " must have no public constructors");
                    }
                    return super.visitMethod(isCtor ? (access | ACC_PUBLIC) : access, name, desc, signature,
                            exceptions);
                }
            }, 0);
            return cw.toByteArray();
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
