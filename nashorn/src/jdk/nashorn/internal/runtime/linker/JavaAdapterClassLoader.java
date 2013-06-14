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

package jdk.nashorn.internal.runtime.linker;

import static jdk.internal.org.objectweb.asm.Opcodes.ACC_FINAL;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_STATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_SUPER;
import static jdk.internal.org.objectweb.asm.Opcodes.ACONST_NULL;
import static jdk.internal.org.objectweb.asm.Opcodes.ALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.ARETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.RETURN;

import java.security.AccessController;
import java.security.AllPermission;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.SecureClassLoader;

import jdk.internal.dynalink.beans.StaticClass;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.commons.InstructionAdapter;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * This class encapsulates the bytecode of the adapter class and can be used to load it into the JVM as an actual Class.
 * It can be invoked repeatedly to create multiple adapter classes from the same bytecode; adapter classes that have
 * class-level overrides must be re-created for every set of such overrides. Note that while this class is named
 * "class loader", it does not, in fact, extend {@code ClassLoader}, but rather uses them internally. Instances of this
 * class are normally created by {@link JavaAdapterBytecodeGenerator}.
 */
@SuppressWarnings("javadoc")
class JavaAdapterClassLoader extends JavaAdapterGeneratorBase {
    private static final Type PRIVILEGED_ACTION_TYPE = Type.getType(PrivilegedAction.class);

    private static final String PRIVILEGED_ACTION_TYPE_NAME = PRIVILEGED_ACTION_TYPE.getInternalName();
    private static final String PRIVILEGED_RUN_METHOD_DESCRIPTOR = Type.getMethodDescriptor(OBJECT_TYPE);

    private static final ProtectionDomain GENERATED_PROTECTION_DOMAIN = createGeneratedProtectionDomain();

    private final String className;
    private final byte[] classBytes;
    private final String globalSetterClassName;

    JavaAdapterClassLoader(String className, byte[] classBytes, String globalSetterClassName) {
        this.className = className.replace('/', '.');
        this.classBytes = classBytes;
        this.globalSetterClassName = globalSetterClassName.replace('/', '.');
    }

    /**
     * Loads the generated adapter class into the JVM.
     * @param parentLoader the parent class loader for the generated class loader
     * @return the generated adapter class
     */
    StaticClass generateClass(final ClassLoader parentLoader) {
        return AccessController.doPrivileged(new PrivilegedAction<StaticClass>() {
            @Override
            public StaticClass run() {
                try {
                    return StaticClass.forClass(Class.forName(className, true, createClassLoader(parentLoader)));
                } catch (final ClassNotFoundException e) {
                    throw new AssertionError(e); // cannot happen
                }
            }
        });
    }

    private static class AdapterLoader extends SecureClassLoader {
        AdapterLoader(ClassLoader parent) {
            super(parent);
        }
    }

    static boolean isAdapterClass(Class<?> clazz) {
        return clazz.getClassLoader() instanceof AdapterLoader;
    }

    // Note that the adapter class is created in the protection domain of the class/interface being
    // extended/implemented, and only the privileged global setter action class is generated in the protection domain
    // of Nashorn itself. Also note that the creation and loading of the global setter is deferred until it is
    // required by JVM linker, which will only happen on first invocation of any of the adapted method. We could defer
    // it even more by separating its invocation into a separate static method on the adapter class, but then someone
    // with ability to introspect on the class and use setAccessible(true) on it could invoke the method. It's a
    // security tradeoff...
    private ClassLoader createClassLoader(final ClassLoader parentLoader) {
        return new AdapterLoader(parentLoader) {
            private final ClassLoader myLoader = getClass().getClassLoader();
            private final ProtectionDomain myProtectionDomain = getClass().getProtectionDomain();

            @Override
            public Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
                try {
                    return super.loadClass(name, resolve);
                } catch (final SecurityException se) {
                    // we may be implementing an interface or extending a class that was
                    // loaded by a loader that prevents package.access. If so, it'd throw
                    // SecurityException for nashorn's classes!. For adapter's to work, we
                    // should be able to refer to nashorn classes.
                    if (name.startsWith("jdk.nashorn.internal.")) {
                        return myLoader.loadClass(name);
                    }
                    throw se;
                }
            }

            @Override
            protected Class<?> findClass(final String name) throws ClassNotFoundException {
                if(name.equals(className)) {
                    return defineClass(name, classBytes, 0, classBytes.length, GENERATED_PROTECTION_DOMAIN);
                } else if(name.equals(globalSetterClassName)) {
                    final byte[] bytes = generatePrivilegedActionClassBytes(globalSetterClassName.replace('.', '/'));
                    return defineClass(name, bytes, 0, bytes.length, myProtectionDomain);
                } else {
                    throw new ClassNotFoundException(name);
                }
            }
        };
    }

    private static ProtectionDomain createGeneratedProtectionDomain() {
        // Generated classes need to have AllPermission. Since we require the "createClassLoader" RuntimePermission, we
        // can create a class loader that'll load new classes with any permissions. Our generated classes are just
        // delegating adapters, so having AllPermission can't cause anything wrong; the effective set of permissions for
        // the executing script functions will still be limited by the permissions of the caller and the permissions of
        // the script.
        final Permissions permissions = new Permissions();
        permissions.add(new AllPermission());
        return new ProtectionDomain(new CodeSource(null, (CodeSigner[])null), permissions);
    }

    /**
     * Generates a PrivilegedAction implementation class for invoking {@link Context#setGlobal(ScriptObject)} from the
     * adapter class.
     */
    private static byte[] generatePrivilegedActionClassBytes(final String className) {
        final ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        // class GlobalSetter implements PrivilegedAction {
        w.visit(Opcodes.V1_7, ACC_SUPER | ACC_FINAL, className, null, OBJECT_TYPE_NAME, new String[] {
                PRIVILEGED_ACTION_TYPE_NAME
        });

        // private final ScriptObject global;
        w.visitField(ACC_PRIVATE | ACC_FINAL, GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR, null, null).visitEnd();

        // private GlobalSetter(ScriptObject global) {
        InstructionAdapter mv = new InstructionAdapter(w.visitMethod(ACC_PRIVATE, INIT,
                SET_GLOBAL_METHOD_DESCRIPTOR, null, new String[0]));
        mv.visitCode();
        // super();
        mv.visitVarInsn(ALOAD, 0);
        mv.invokespecial(OBJECT_TYPE_NAME, INIT, VOID_NOARG_METHOD_DESCRIPTOR);
        // this.global = global;
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.putfield(className, GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);

        mv.visitInsn(RETURN);
        mv.visitEnd();
        mv.visitMaxs(0, 0);

        // public Object run() {
        mv = new InstructionAdapter(w.visitMethod(ACC_PUBLIC, "run", PRIVILEGED_RUN_METHOD_DESCRIPTOR, null,
                new String[0]));
        mv.visitCode();
        // Context.setGlobal(this.global);
        mv.visitVarInsn(ALOAD, 0);
        mv.getfield(className, GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);
        mv.invokestatic(CONTEXT_TYPE_NAME, "setGlobal", SET_GLOBAL_METHOD_DESCRIPTOR);
        // return null;
        mv.visitInsn(ACONST_NULL);
        mv.visitInsn(ARETURN);

        mv.visitEnd();
        mv.visitMaxs(0, 0);

        // static void setGlobal(ScriptObject global) {
        mv = new InstructionAdapter(w.visitMethod(ACC_STATIC, "setGlobal", SET_GLOBAL_METHOD_DESCRIPTOR, null,
                new String[0]));
        mv.visitCode();
        // new GlobalSetter(ScriptObject global)
        mv.anew(Type.getType("L" + className + ";"));
        mv.dup();
        mv.visitVarInsn(ALOAD, 0);
        mv.invokespecial(className, INIT, SET_GLOBAL_METHOD_DESCRIPTOR);
        // AccessController.doPrivileged(...)
        mv.invokestatic(Type.getInternalName(AccessController.class), "doPrivileged", Type.getMethodDescriptor(
                OBJECT_TYPE, PRIVILEGED_ACTION_TYPE));
        mv.pop();
        mv.visitInsn(RETURN);

        mv.visitEnd();
        mv.visitMaxs(0, 0);

        return w.toByteArray();
    }
}
