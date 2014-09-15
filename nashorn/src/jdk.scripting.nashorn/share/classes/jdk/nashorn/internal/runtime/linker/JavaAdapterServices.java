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
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_STATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_SUPER;
import static jdk.internal.org.objectweb.asm.Opcodes.ALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.RETURN;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.AccessController;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.SecureClassLoader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.commons.InstructionAdapter;
import jdk.nashorn.api.scripting.ScriptUtils;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Undefined;

/**
 * Provides static utility services to generated Java adapter classes.
 */
public final class JavaAdapterServices {
    private static final ThreadLocal<ScriptObject> classOverrides = new ThreadLocal<>();
    private static final MethodHandle NO_PERMISSIONS_INVOKER = createNoPermissionsInvoker();

    private JavaAdapterServices() {
    }

    /**
     * Given a JS script function, binds it to null JS "this", and adapts its parameter types, return types, and arity
     * to the specified type and arity. This method is public mainly for implementation reasons, so the adapter classes
     * can invoke it from their constructors that take a ScriptFunction in its first argument to obtain the method
     * handles for their abstract method implementations.
     * @param fn the script function
     * @param type the method type it has to conform to
     * @return the appropriately adapted method handle for invoking the script function.
     */
    public static MethodHandle getHandle(final ScriptFunction fn, final MethodType type) {
        // JS "this" will be global object or undefined depending on if 'fn' is strict or not
        return bindAndAdaptHandle(fn, fn.isStrict()? ScriptRuntime.UNDEFINED : Context.getGlobal(), type);
    }

    /**
     * Given a JS script object, retrieves a function from it by name, binds it to the script object as its "this", and
     * adapts its parameter types, return types, and arity to the specified type and arity. This method is public mainly
     * for implementation reasons, so the adapter classes can invoke it from their constructors that take a Object
     * in its first argument to obtain the method handles for their method implementations.
     * @param obj the script obj
     * @param name the name of the property that contains the function
     * @param type the method type it has to conform to
     * @return the appropriately adapted method handle for invoking the script function, or null if the value of the
     * property is either null or undefined, or "toString" was requested as the name, but the object doesn't directly
     * define it but just inherits it through prototype.
     */
    public static MethodHandle getHandle(final Object obj, final String name, final MethodType type) {
        if (! (obj instanceof ScriptObject)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(obj));
        }

        final ScriptObject sobj = (ScriptObject)obj;
        // Since every JS Object has a toString, we only override "String toString()" it if it's explicitly specified
        if ("toString".equals(name) && !sobj.hasOwnProperty("toString")) {
            return null;
        }

        final Object fnObj = sobj.get(name);
        if (fnObj instanceof ScriptFunction) {
            return bindAndAdaptHandle((ScriptFunction)fnObj, sobj, type);
        } else if(fnObj == null || fnObj instanceof Undefined) {
            return null;
        } else {
            throw typeError("not.a.function", name);
        }
    }

    /**
     * Returns a thread-local JS object used to define methods for the adapter class being initialized on the current
     * thread. This method is public solely for implementation reasons, so the adapter classes can invoke it from their
     * static initializers.
     * @return the thread-local JS object used to define methods for the class being initialized.
     */
    public static Object getClassOverrides() {
        final Object overrides = classOverrides.get();
        assert overrides != null;
        return overrides;
    }

    /**
     * Takes a method handle and an argument to it, and invokes the method handle passing it the argument. Basically
     * equivalent to {@code method.invokeExact(arg)}, except that the method handle will be invoked in a protection
     * domain with absolutely no permissions.
     * @param method the method handle to invoke. The handle must have the exact type of {@code void(Object)}.
     * @param arg the argument to pass to the handle.
     * @throws Throwable if anything goes wrong.
     */
    public static void invokeNoPermissions(final MethodHandle method, final Object arg) throws Throwable {
        NO_PERMISSIONS_INVOKER.invokeExact(method, arg);
    }

    /**
     * Set the current global scope
     * @param global the global scope
     */
    public static void setGlobal(final Object global) {
        Context.setGlobal((ScriptObject)global);
    }

    /**
     * Get the current global scope
     * @return the current global scope
     */
    public static Object getGlobal() {
        return Context.getGlobal();
    }

    static void setClassOverrides(final ScriptObject overrides) {
        classOverrides.set(overrides);
    }

    private static MethodHandle bindAndAdaptHandle(final ScriptFunction fn, final Object self, final MethodType type) {
        return Bootstrap.getLinkerServices().asType(ScriptObject.pairArguments(fn.getBoundInvokeHandle(self), type, false), type);
    }

    private static MethodHandle createNoPermissionsInvoker() {
        final String className = "NoPermissionsInvoker";

        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_7, ACC_PUBLIC | ACC_SUPER | ACC_FINAL, className, null, "java/lang/Object", null);
        final Type objectType = Type.getType(Object.class);
        final Type methodHandleType = Type.getType(MethodHandle.class);
        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "invoke",
                Type.getMethodDescriptor(Type.VOID_TYPE, methodHandleType, objectType), null, null));
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.invokevirtual(methodHandleType.getInternalName(), "invokeExact", Type.getMethodDescriptor(
                Type.VOID_TYPE, objectType), false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        final byte[] bytes = cw.toByteArray();

        final ClassLoader loader = AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
            @Override
            public ClassLoader run() {
                return new SecureClassLoader(null) {
                    @Override
                    protected Class<?> findClass(final String name) throws ClassNotFoundException {
                        if(name.equals(className)) {
                            return defineClass(name, bytes, 0, bytes.length, new ProtectionDomain(
                                    new CodeSource(null, (CodeSigner[])null), new Permissions()));
                        }
                        throw new ClassNotFoundException(name);
                    }
                };
            }
        });

        try {
            return MethodHandles.lookup().findStatic(Class.forName(className, true, loader), "invoke",
                    MethodType.methodType(void.class, MethodHandle.class, Object.class));
        } catch(final ReflectiveOperationException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    /**
     * Returns a method handle used to convert a return value from a delegate method (always Object) to the expected
     * Java return type.
     * @param returnType the return type
     * @return the converter for the expected return type
     */
    public static MethodHandle getObjectConverter(final Class<?> returnType) {
        return Bootstrap.getLinkerServices().getTypeConverter(Object.class, returnType);
    }

    /**
     * Invoked when returning Object from an adapted method to filter out internal Nashorn objects that must not be seen
     * by the callers. Currently only transforms {@code ConsString} into {@code String} and transforms {@code ScriptObject} into {@code ScriptObjectMirror}.
     * @param obj the return value
     * @return the filtered return value.
     */
    public static Object exportReturnValue(final Object obj) {
        return ScriptUtils.wrap(NashornBeansLinker.exportArgument(obj));
    }

    /**
     * Invoked to convert a return value of a delegate function to primitive char. There's no suitable conversion in
     * {@code JSType}, so we provide our own to adapters.
     * @param obj the return value.
     * @return the character value of the return value
     */
    public static char toCharPrimitive(final Object obj) {
        return JavaArgumentConverters.toCharPrimitive(obj);
    }

    /**
     * Invoked to convert a return value of a delegate function to String. It is similar to
     * {@code JSType.toString(Object)}, except it doesn't handle StaticClass specially, and it returns null for null
     * input instead of the string "null".
     * @param obj the return value.
     * @return the String value of the return value
     */
    public static String toString(final Object obj) {
        return JavaArgumentConverters.toString(obj);
    }
}
