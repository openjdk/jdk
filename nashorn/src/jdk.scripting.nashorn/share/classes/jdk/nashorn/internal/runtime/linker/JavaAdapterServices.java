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

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.security.AccessController;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.SecureClassLoader;
import java.util.Objects;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.commons.InstructionAdapter;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ECMAException;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * Provides static utility services to generated Java adapter classes.
 */
public final class JavaAdapterServices {
    private static final ThreadLocal<ScriptObject> classOverrides = new ThreadLocal<>();
    private static final MethodHandle NO_PERMISSIONS_INVOKER = createNoPermissionsInvoker();

    private JavaAdapterServices() {
    }

    /**
     * Given a script function used as a delegate for a SAM adapter, figure out
     * the right object to use as its "this" when called.
     * @param delegate the delegate function
     * @param global the current global of the adapter
     * @return either the passed global, or UNDEFINED if the function is strict.
     */
    public static Object getCallThis(final ScriptFunction delegate, final Object global) {
        return delegate.isStrict() ? ScriptRuntime.UNDEFINED : global;
    }

    /**
     * Throws a "not.an.object" type error. Used when the delegate passed to the
     * adapter constructor is not a script object.
     * @param obj the object that is not a script object.
     */
    public static void notAnObject(final Object obj) {
        throw typeError("not.an.object", ScriptRuntime.safeToString(obj));
    }

    /**
     * Checks if the passed object, which is supposed to be a callee retrieved
     * through applying the GET_METHOD_PROPERTY operation on the delegate, is
     * a ScriptFunction, or null or undefined. These are the only allowed values
     * for adapter method implementations, so in case it is neither, it throws
     * a type error. Note that this restriction is somewhat artificial; as the
     * CALL dynamic operation could invoke any Nashorn callable. We are
     * restricting adapters to actual ScriptFunction objects for now though.
     * @param callee the callee to check
     * @param name the name of the function
     * @return the callee cast to a ScriptFunction, or null if it was null or undefined.
     * @throws ECMAException representing a JS TypeError with "not.a.function"
     * message if the passed callee is neither a script function, nor null, nor
     * undefined.
     */
    public static ScriptFunction checkFunction(final Object callee, final String name) {
        if (callee instanceof ScriptFunction) {
            return (ScriptFunction)callee;
        } else if (JSType.nullOrUndefined(callee)) {
            return null;
        }
        throw typeError("not.a.function.value", name, ScriptRuntime.safeToString(callee));
    }

    /**
     * Returns a thread-local JS object used to define methods for the adapter class being initialized on the current
     * thread. This method is public solely for implementation reasons, so the adapter classes can invoke it from their
     * static initializers.
     * @return the thread-local JS object used to define methods for the class being initialized.
     */
    public static ScriptObject getClassOverrides() {
        final ScriptObject overrides = classOverrides.get();
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
     * Set the current global scope to that of the adapter global
     * @param adapterGlobal the adapter's global scope
     * @return a Runnable that when invoked restores the previous global
     */
    public static Runnable setGlobal(final ScriptObject adapterGlobal) {
        final Global currentGlobal = Context.getGlobal();
        if (adapterGlobal != currentGlobal) {
            Context.setGlobal(adapterGlobal);
            return ()->Context.setGlobal(currentGlobal);
        }
        return ()->{};
    }

    /**
     * Get the current non-null global scope
     * @return the current global scope
     * @throws NullPointerException if the current global scope is null.
     */
    public static ScriptObject getNonNullGlobal() {
        return Objects.requireNonNull(Context.getGlobal(), "Current global is null");
    }

    /**
     * Returns true if the object has its own toString function. Used
     * when implementing toString for adapters. Since every JS Object has a
     * toString function, we only override "String toString()" in adapters if
     * it is explicitly specified and not inherited from a prototype.
     * @param sobj the object
     * @return true if the object has its own toString function.
     */
    public static boolean hasOwnToString(final ScriptObject sobj) {
        // NOTE: we could just use ScriptObject.hasOwnProperty("toString"), but
        // its logic is more complex and this is what it boils down to with a
        // fixed "toString" argument.
        return sobj.getMap().findProperty("toString") != null;
    }

    /**
     * Delegate to {@link Bootstrap#bootstrap(Lookup, String, MethodType, int)}.
     * @param lookup MethodHandle lookup.
     * @param opDesc Dynalink dynamic operation descriptor.
     * @param type   Method type.
     * @param flags  flags for call type, trace/profile etc.
     * @return CallSite with MethodHandle to appropriate method or null if not found.
     */
    public static CallSite bootstrap(final Lookup lookup, final String opDesc, final MethodType type, final int flags) {
        return Bootstrap.bootstrap(lookup, opDesc, type, flags);
    }

    static void setClassOverrides(final ScriptObject overrides) {
        classOverrides.set(overrides);
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
            return MethodHandles.publicLookup().findStatic(Class.forName(className, true, loader), "invoke",
                    MethodType.methodType(void.class, MethodHandle.class, Object.class));
        } catch(final ReflectiveOperationException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    /**
     * Invoked when returning Object from an adapted method to filter out internal Nashorn objects that must not be seen
     * by the callers. Currently only transforms {@code ConsString} into {@code String} and transforms {@code ScriptObject} into {@code ScriptObjectMirror}.
     * @param obj the return value
     * @return the filtered return value.
     */
    public static Object exportReturnValue(final Object obj) {
        return NashornBeansLinker.exportArgument(obj, true);
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
     * Returns a new {@link RuntimeException} wrapping the passed throwable.
     * Makes generated bytecode smaller by doing an INVOKESTATIC to this method
     * rather than the NEW/DUP_X1/SWAP/INVOKESPECIAL &lt;init&gt; sequence.
     * @param t the original throwable to wrap
     * @return a newly created runtime exception wrapping the passed throwable.
     */
    public static RuntimeException wrapThrowable(final Throwable t) {
        return new RuntimeException(t);
    }

    /**
     * Creates and returns a new {@link UnsupportedOperationException}. Makes
     * generated bytecode smaller by doing INVOKESTATIC to this method rather
     * than the NEW/DUP/INVOKESPECIAL &lt;init&gt; sequence.
     * @return a newly created {@link UnsupportedOperationException}.
     */
    public static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException();
    }

    /**
     * A bootstrap method used to collect invocation arguments into an Object array.
     * for variable arity invocation.
     * @param lookup the adapter's lookup (not used).
     * @param name the call site name (not used).
     * @param type the method type
     * @return a method that takes the input parameters and packs them into a
     * newly allocated Object array.
     */
    public static CallSite createArrayBootstrap(final MethodHandles.Lookup lookup, final String name, final MethodType type) {
        return new ConstantCallSite(
                MethodHandles.identity(Object[].class)
                .asCollector(Object[].class, type.parameterCount())
                .asType(type));
    }
}
