/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.reflect;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.internal.access.JavaLangInvokeAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.Unsafe;
import jdk.internal.misc.VM;
import sun.security.action.GetPropertyAction;

import static java.lang.invoke.MethodType.genericMethodType;
import static java.lang.invoke.MethodType.methodType;
import static jdk.internal.reflect.MethodHandleAccessorFactory.LazyStaticHolder.*;

final class MethodHandleAccessorFactory {
    /**
     * Creates a MethodAccessor for the given reflected method.
     *
     * If the given method is called before the java.lang.invoke initialization
     * or the given method is a native method, it will use the native VM reflection
     * support.
     *
     * If the given method is a caller-sensitive method and the corresponding
     * caller-sensitive adapter with the caller class parameter is present,
     * it will use the method handle of the caller-sensitive adapter.
     *
     * Otherwise, it will use the direct method handle of the given method.
     *
     * @see CallerSensitive
     * @see CallerSensitiveAdapter
     */
    static MethodAccessorImpl newMethodAccessor(Method method, boolean callerSensitive) {
        if (useNativeAccessor(method)) {
            return DirectMethodHandleAccessor.nativeAccessor(method, callerSensitive);
        }

        // ExceptionInInitializerError may be thrown during class initialization
        // Ensure class initialized outside the invocation of method handle
        // so that EIIE is propagated (not wrapped with ITE)
        ensureClassInitialized(method.getDeclaringClass());

        try {
            if (callerSensitive) {
                var dmh = findCallerSensitiveAdapter(method);
                if (dmh != null) {
                    return DirectMethodHandleAccessor.callerSensitiveAdapter(method, dmh);
                }
            }
            var dmh = getDirectMethod(method, callerSensitive);
            if (callerSensitive) {
                return DirectMethodHandleAccessor.callerSensitiveMethodAccessor(method, dmh);
            } else {
                return DirectMethodHandleAccessor.methodAccessor(method, dmh);
            }
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Creates a ConstructorAccessor for the given reflected constructor.
     *
     * If a given constructor is called before the java.lang.invoke initialization,
     * it will use the native VM reflection support.
     *
     * Otherwise, it will use the direct method handle of the given constructor.
     */
    static ConstructorAccessorImpl newConstructorAccessor(Constructor<?> ctor) {
        if (useNativeAccessor(ctor)) {
            return DirectConstructorHandleAccessor.nativeAccessor(ctor);
        }

        // ExceptionInInitializerError may be thrown during class initialization
        // Ensure class initialized outside the invocation of method handle
        // so that EIIE is propagated (not wrapped with ITE)
        ensureClassInitialized(ctor.getDeclaringClass());

        try {
            MethodHandle mh = JLIA.unreflectConstructor(ctor);
            int paramCount = mh.type().parameterCount();
            MethodHandle target = mh.asFixedArity();
            MethodType mtype = specializedMethodTypeForConstructor(paramCount);
            if (paramCount > SPECIALIZED_PARAM_COUNT) {
                // spread the parameters only for the non-specialized case
                target = target.asSpreader(Object[].class, paramCount);
            }
            target = target.asType(mtype);
            return DirectConstructorHandleAccessor.constructorAccessor(ctor, target);
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Creates a FieldAccessor for the given reflected field.
     *
     * Limitation: Field access via core reflection is only supported after
     * java.lang.invoke completes initialization.
     * java.lang.invoke initialization starts soon after System::initPhase1
     * and method handles are ready for use when initPhase2 begins.
     * During early VM startup (initPhase1), fields can be accessed directly
     * from the VM or through JNI.
     */
    static FieldAccessorImpl newFieldAccessor(Field field, boolean isReadOnly) {
        if (!VM.isJavaLangInvokeInited()) {
            throw new InternalError(field.getDeclaringClass().getName() + "::" + field.getName() +
                    " cannot be accessed reflectively before java.lang.invoke is initialized");
        }

        try {
            // the declaring class of the field has been initialized
            var varHandle = JLIA.unreflectVarHandle(field);
            Class<?> type = field.getType();
            if (type == Boolean.TYPE) {
                return VarHandleBooleanFieldAccessorImpl.fieldAccessor(field, varHandle, isReadOnly);
            } else if (type == Byte.TYPE) {
                return VarHandleByteFieldAccessorImpl.fieldAccessor(field, varHandle, isReadOnly);
            } else if (type == Short.TYPE) {
                return VarHandleShortFieldAccessorImpl.fieldAccessor(field, varHandle, isReadOnly);
            } else if (type == Character.TYPE) {
                return VarHandleCharacterFieldAccessorImpl.fieldAccessor(field, varHandle, isReadOnly);
            } else if (type == Integer.TYPE) {
                return VarHandleIntegerFieldAccessorImpl.fieldAccessor(field, varHandle, isReadOnly);
            } else if (type == Long.TYPE) {
                return VarHandleLongFieldAccessorImpl.fieldAccessor(field, varHandle, isReadOnly);
            } else if (type == Float.TYPE) {
                return VarHandleFloatFieldAccessorImpl.fieldAccessor(field, varHandle, isReadOnly);
            } else if (type == Double.TYPE) {
                return VarHandleDoubleFieldAccessorImpl.fieldAccessor(field, varHandle, isReadOnly);
            } else {
                return VarHandleObjectFieldAccessorImpl.fieldAccessor(field, varHandle, isReadOnly);
            }
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    private static MethodHandle getDirectMethod(Method method, boolean callerSensitive) throws IllegalAccessException {
        var mtype = methodType(method.getReturnType(), method.getParameterTypes());
        var isStatic = Modifier.isStatic(method.getModifiers());
        var dmh = isStatic ? JLIA.findStatic(method.getDeclaringClass(), method.getName(), mtype)
                                        : JLIA.findVirtual(method.getDeclaringClass(), method.getName(), mtype);
        if (callerSensitive) {
            // the reflectiveInvoker for caller-sensitive method expects the same signature
            // as Method::invoke i.e. (Object, Object[])Object
            return makeTarget(dmh, isStatic, false);
        }
        return makeSpecializedTarget(dmh, isStatic, false);
    }

    /**
     * Finds the method handle of a caller-sensitive adapter for the given
     * caller-sensitive method.  It has the same name as the given method
     * with a trailing caller class parameter.
     *
     * @see CallerSensitiveAdapter
     */
    private static MethodHandle findCallerSensitiveAdapter(Method method) throws IllegalAccessException {
        String name = method.getName();
        // append a Class parameter
        MethodType mtype = methodType(method.getReturnType(), method.getParameterTypes())
                                .appendParameterTypes(Class.class);
        boolean isStatic = Modifier.isStatic(method.getModifiers());

        MethodHandle dmh = isStatic ? JLIA.findStatic(method.getDeclaringClass(), name, mtype)
                                    : JLIA.findVirtual(method.getDeclaringClass(), name, mtype);
        return dmh != null ? makeSpecializedTarget(dmh, isStatic, true) : null;
    }

    /**
     * Transform the given dmh to a specialized target method handle.
     *
     * If {@code hasCallerParameter} parameter is true, transform the method handle
     * of this method type: {@code (Object, Object[], Class)Object} for the default
     * case.
     *
     * If {@code hasCallerParameter} parameter is false, transform the method handle
     * of this method type: {@code (Object, Object[])Object} for the default case.
     *
     * If the number of formal arguments is small, use a method type specialized
     * the number of formal arguments is 0, 1, and 2, for example, the method type
     * of a static method with one argument can be: {@code (Object)Object}
     *
     * If it's a static method, there is no leading Object parameter.
     *
     * @apiNote
     * This implementation avoids using MethodHandles::catchException to help
     * cold startup performance since this combination is very costly to setup.
     *
     * @param dmh DirectMethodHandle
     * @param isStatic whether given dmh represents static method or not
     * @param hasCallerParameter whether given dmh represents a method with an
     *                         additional caller Class parameter
     * @return transformed dmh to be used as a target in direct method accessors
     */
    static MethodHandle makeSpecializedTarget(MethodHandle dmh, boolean isStatic, boolean hasCallerParameter) {
        MethodHandle target = dmh.asFixedArity();

        // number of formal arguments to the original method (not the adapter)
        // If it is a non-static method, it has a leading `this` argument.
        // Also do not count the caller class argument
        int paramCount = dmh.type().parameterCount() - (isStatic ? 0 : 1) - (hasCallerParameter ? 1 : 0);
        MethodType mtype = specializedMethodType(isStatic, hasCallerParameter, paramCount);
        if (paramCount > SPECIALIZED_PARAM_COUNT) {
            int spreadArgPos = isStatic ? 0 : 1;
            target = target.asSpreader(spreadArgPos, Object[].class, paramCount);
        }
        if (isStatic) {
            // add leading 'this' parameter to static method which is then ignored
            target = MethodHandles.dropArguments(target, 0, Object.class);
        }
        return target.asType(mtype);
    }

    // specialize for number of formal arguments <= 3 to avoid spreader
    static final int SPECIALIZED_PARAM_COUNT = 3;
    static MethodType specializedMethodType(boolean isStatic, boolean hasCallerParameter, int paramCount) {
        return switch (paramCount) {
            case 0 -> hasCallerParameter ? methodType(Object.class, Object.class, Class.class)
                                         : genericMethodType(1);
            case 1 -> hasCallerParameter ? methodType(Object.class, Object.class, Object.class, Class.class)
                                         : genericMethodType(2);
            case 2 -> hasCallerParameter ? methodType(Object.class, Object.class, Object.class, Object.class, Class.class)
                                         : genericMethodType(3);
            case 3 -> hasCallerParameter ? methodType(Object.class, Object.class, Object.class, Object.class, Object.class, Class.class)
                                         : genericMethodType(4);
            default -> hasCallerParameter ? methodType(Object.class, Object.class, Object[].class, Class.class)
                                          : genericMethodType(1, true);
        };
    }

    static MethodType specializedMethodTypeForConstructor(int paramCount) {
        return switch (paramCount) {
            case 0 ->  genericMethodType(0);
            case 1 ->  genericMethodType(1);
            case 2 ->  genericMethodType(2);
            case 3 ->  genericMethodType(3);
            default -> genericMethodType(0, true);
        };
    }

    /**
     * Transforms the given dmh into a target method handle with the method type
     * {@code (Object, Object[])Object} or {@code (Object, Class, Object[])Object}
     */
    static MethodHandle makeTarget(MethodHandle dmh, boolean isStatic, boolean hasCallerParameter) {
        MethodType mtype = hasCallerParameter
                                ? methodType(Object.class, Object.class, Object[].class, Class.class)
                                : genericMethodType(1, true);
        // number of formal arguments
        int paramCount = dmh.type().parameterCount() - (isStatic ? 0 : 1) - (hasCallerParameter ? 1 : 0);
        int spreadArgPos = isStatic ? 0 : 1;
        MethodHandle target = dmh.asFixedArity().asSpreader(spreadArgPos, Object[].class, paramCount);
        if (isStatic) {
            // add leading 'this' parameter to static method which is then ignored
            target = MethodHandles.dropArguments(target, 0, Object.class);
        }
        return target.asType(mtype);
    }

    /**
     * Spins a hidden class that invokes a constant VarHandle of the target field,
     * loaded from the class data via condy, for reliable performance.
     *
     * For a non-volatile field, one class file is generated for each primitive
     * type and a reference type, its class name is FieldAccessorImpl_L_<T>
     * for an instance field or FieldAccessorImpl_<T> for a static field
     * where T is the base type, B C D F I J S Z, for primitive type or L
     * for a reference type.
     *
     * For a volatile field, its class name is FieldAccessorImpl_<T>_V
     * for an instance field or FieldAccessorImpl_<T>_V for a static field.
     */
    static VHInvoker newVarHandleAccessor(Field field, VarHandle varHandle) {
        var name = classNamePrefix(field);
        var cn = name + "$$" + counter.getAndIncrement();
        byte[] bytes = ACCESSOR_CLASSFILES.computeIfAbsent(name, n -> spinByteCode(name, field));
        try {
            var lookup = JLIA.defineHiddenClassWithClassData(LOOKUP, cn, bytes, varHandle, true);
            var ctor = lookup.findConstructor(lookup.lookupClass(), methodType(void.class));
            ctor = ctor.asType(methodType(VHInvoker.class));
            return (VHInvoker) ctor.invokeExact();
        } catch (Throwable e) {
            throw new InternalError(e);
        }
    }

    /**
     * Spins a hidden class that invokes a constant MethodHandle of the target method handle,
     * loaded from the class data via condy, for reliable performance.
     *
     * One class file is generated for each method type which is either
     * (Object,Object[])Object or the specialized version
     * (Object,Object, ...)Object or (Object,Object, ..., Class)Object
     * where the number of parameter types = receiver if it's an instance method
     * + the number of formal parameters + "Class" if it's a caller-sensitive method
     * which has an adapter defined.
     */
    static MHInvoker newMethodHandleInvoker(Method method, MethodHandle target, boolean hasCallerParameter) {
        var name = classNamePrefix(method, target.type(), hasCallerParameter);
        var cn = name + "$$" + counter.getAndIncrement();
        byte[] bytes = ACCESSOR_CLASSFILES.computeIfAbsent(name,
                n -> spinByteCode(name, method, target.type(), hasCallerParameter));
        try {
            var lookup = JLIA.defineHiddenClassWithClassData(LOOKUP, cn, bytes, target, true);
            var ctor = lookup.findConstructor(lookup.lookupClass(), methodType(void.class));
            ctor = ctor.asType(methodType(MHInvoker.class));
            return (MHInvoker) ctor.invokeExact();
        } catch (Throwable e) {
            throw new InternalError(e);
        }
    }

    /**
     * Spins a hidden class that invokes a constant MethodHandle of the target method handle,
     * loaded from the class data via condy, for reliable performance.
     *
     * Due to the overhead of class loading, this is not the default.
     */
    static MHInvoker newMethodHandleInvoker(Constructor<?> c, MethodHandle target) {
        var name = classNamePrefix(c, target.type());
        var cn = name + "$$" + counter.getAndIncrement();
        byte[] bytes = ACCESSOR_CLASSFILES.computeIfAbsent(name,
                n -> spinByteCode(name, c, target.type()));
        try {
            var lookup = JLIA.defineHiddenClassWithClassData(LOOKUP, cn, bytes, target, true);
            var ctor = lookup.findConstructor(lookup.lookupClass(), methodType(void.class));
            ctor = ctor.asType(methodType(MHInvoker.class));
            return (MHInvoker) ctor.invokeExact();
        } catch (Throwable e) {
            throw new InternalError(e);
        }
    }

    private static final ConcurrentHashMap<String, byte[]> ACCESSOR_CLASSFILES = new ConcurrentHashMap<>();
    private static final String FIELD_CLASS_NAME_PREFIX = "jdk/internal/reflect/FieldAccessorImpl_";
    private static final String METHOD_CLASS_NAME_PREFIX = "jdk/internal/reflect/MethodAccessorImpl_";
    private static final String CONSTRUCTOR_CLASS_NAME_PREFIX = "jdk/internal/reflect/ConstructorAccessorImpl_";

    // Used to ensure that each spun class name is unique
    private static final AtomicInteger counter = new AtomicInteger();

    private static String classNamePrefix(Field field) {
        var isStatic = Modifier.isStatic(field.getModifiers());
        var isVolatile = Modifier.isVolatile(field.getModifiers());
        var type = field.getType();
        var desc = type.isPrimitive() ? type.descriptorString() : "L";
        return FIELD_CLASS_NAME_PREFIX + (isStatic ? desc : "L" + desc) + (isVolatile ? "_V" : "");
    }
    private static String classNamePrefix(Method method, MethodType mtype, boolean hasCallerParameter) {
        var isStatic = Modifier.isStatic(method.getModifiers());
        var methodTypeName = methodTypeName(isStatic, hasCallerParameter, mtype);
        return METHOD_CLASS_NAME_PREFIX + methodTypeName;
    }
    private static String classNamePrefix(Constructor<?> c, MethodType mtype) {
        var methodTypeName = methodTypeName(true, false, mtype);
        return CONSTRUCTOR_CLASS_NAME_PREFIX + methodTypeName;
    }

    /**
     * Returns a string to represent the specialized method type.
     */
    private static String methodTypeName(boolean isStatic, boolean hasCallerParameter, MethodType mtype) {
        StringBuilder sb = new StringBuilder();
        int pIndex = 0;
        if (!isStatic) {
            sb.append("L");
            pIndex++;
        }
        int paramCount = mtype.parameterCount() - (hasCallerParameter ? 1 : 0);
        for (;pIndex < paramCount; pIndex++) {
            Class<?> ptype = mtype.parameterType(pIndex);
            if (ptype == Object[].class) {
                sb.append("A");
            } else {
                assert ptype == Object.class;
                sb.append("L");
            }
        }
        if (hasCallerParameter) {
            sb.append("Class");
            pIndex++;
        }
        return sb.toString();
    }

    private static byte[] spinByteCode(String cn, Field field) {
        var builder = new InvokerBuilder(cn, VarHandle.class);
        var bytes = builder.buildVarHandleInvoker(field);
        maybeDumpClassFile(cn, bytes);
        return bytes;
    }
    private static byte[] spinByteCode(String cn, Method method, MethodType mtype, boolean hasCallerParameter) {
        var builder = new InvokerBuilder(cn, MethodHandle.class);
        var bytes = builder.buildMethodHandleInvoker(method, mtype, hasCallerParameter);
        maybeDumpClassFile(cn, bytes);
        return bytes;
    }
    private static byte[] spinByteCode(String cn, Constructor<?> ctor, MethodType mtype) {
        var builder = new InvokerBuilder(cn, MethodHandle.class);
        var bytes = builder.buildMethodHandleInvoker(ctor, mtype);
        maybeDumpClassFile(cn, bytes);
        return bytes;
    }
    private static void maybeDumpClassFile(String classname, byte[] bytes) {
        if (DUMP_CLASS_FILES != null) {
            try {
                Path p = DUMP_CLASS_FILES.resolve(classname + ".class");
                Files.createDirectories(p.getParent());
                try (OutputStream os = Files.newOutputStream(p)) {
                    os.write(bytes);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * Ensures the given class is initialized.  If this is called from <clinit>,
     * this method returns but defc's class initialization is not completed.
     */
    static void ensureClassInitialized(Class<?> defc) {
        if (UNSAFE.shouldBeInitialized(defc)) {
            UNSAFE.ensureClassInitialized(defc);
        }
    }

    /*
     * Returns true if NativeAccessor should be used.
     */
    private static boolean useNativeAccessor(Executable member) {
        if (!VM.isJavaLangInvokeInited())
            return true;

        if (Modifier.isNative(member.getModifiers()))
            return true;

        if (ReflectionFactory.isUseNativeAccessorOnly())  // for testing only
            return true;

        // MethodHandle::withVarargs on a member with varargs modifier bit set
        // verifies that the last parameter of the member must be an array type.
        // The JVMS does not require the last parameter descriptor of the method descriptor
        // is an array type if the ACC_VARARGS flag is set in the access_flags item.
        // Hence the reflection implementation does not check the last parameter type
        // if ACC_VARARGS flag is set.  Workaround this by invoking through
        // the native accessor.
        int paramCount = member.getParameterCount();
        if (member.isVarArgs() &&
                (paramCount == 0 || !(member.getParameterTypes()[paramCount-1].isArray()))) {
            return true;
        }
        return false;
    }

    /*
     * Delay initializing these static fields until java.lang.invoke is fully initialized.
     */
    static class LazyStaticHolder {
        static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
        static final JavaLangInvokeAccess JLIA = SharedSecrets.getJavaLangInvokeAccess();
    }

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final Path DUMP_CLASS_FILES;
    static {
        String dumpPath = GetPropertyAction.privilegedGetProperty("jdk.reflect.dumpClassPath");
        if (dumpPath != null) {
            DUMP_CLASS_FILES = Path.of(dumpPath);
        } else {
            DUMP_CLASS_FILES = null;
        }
    }
}

