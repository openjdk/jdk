/*
 * Copyright (c) 2008, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;

import jdk.internal.classfile.Annotation;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.Opcode;
import jdk.internal.classfile.TypeKind;
import jdk.internal.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import sun.invoke.WrapperInstance;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;
import sun.reflect.misc.ReflectUtil;

import static java.lang.constant.ConstantDescs.*;
import static java.lang.invoke.MethodHandleStatics.*;
import static jdk.internal.classfile.Classfile.*;

/**
 * This class consists exclusively of static methods that help adapt
 * method handles to other JVM types, such as interfaces.
 *
 * @since 1.7
 */
public class MethodHandleProxies {

    private MethodHandleProxies() { }  // do not instantiate

    /**
     * Produces an instance of the given single-method interface which redirects
     * its calls to the given method handle.
     * <p>
     * A single-method interface is an interface which declares a uniquely named method.
     * When determining the uniquely named method of a single-method interface,
     * the public {@code Object} methods ({@code toString}, {@code equals}, {@code hashCode})
     * are disregarded as are any default (non-abstract) methods.
     * For example, {@link java.util.Comparator} is a single-method interface,
     * even though it re-declares the {@code Object.equals} method and also
     * declares default methods, such as {@code Comparator.reverse}.
     * <p>
     * The interface must be public and not {@linkplain Class#isSealed() sealed}.
     * No additional access checks are performed.
     * <p>
     * The resulting instance of the required type will respond to
     * invocation of the type's uniquely named method by calling
     * the given target on the incoming arguments,
     * and returning or throwing whatever the target
     * returns or throws.  The invocation will be as if by
     * {@code target.invoke}.
     * The target's type will be checked before the
     * instance is created, as if by a call to {@code asType},
     * which may result in a {@code WrongMethodTypeException}.
     * <p>
     * The uniquely named method is allowed to be multiply declared,
     * with distinct type descriptors.  (E.g., it can be overloaded,
     * or can possess bridge methods.)  All such declarations are
     * connected directly to the target method handle.
     * Argument and return types are adjusted by {@code asType}
     * for each individual declaration.
     * <p>
     * The wrapper instance will implement the requested interface
     * and its super-types, but no other single-method interfaces.
     * This means that the instance will not unexpectedly
     * pass an {@code instanceof} test for any unrequested type.
     * <p style="font-size:smaller;">
     * <em>Implementation Note:</em>
     * Therefore, each instance must implement a unique single-method interface.
     * Implementations may not bundle together
     * multiple single-method interfaces onto single implementation classes
     * in the style of {@link java.desktop/java.awt.AWTEventMulticaster}.
     * <p>
     * The method handle may throw an <em>undeclared exception</em>,
     * which means any checked exception (or other checked throwable)
     * not declared by the requested type's single abstract method.
     * If this happens, the throwable will be wrapped in an instance of
     * {@link java.lang.reflect.UndeclaredThrowableException UndeclaredThrowableException}
     * and thrown in that wrapped form.
     * <p>
     * Like {@link java.lang.Integer#valueOf Integer.valueOf},
     * {@code asInterfaceInstance} is a factory method whose results are defined
     * by their behavior.
     * It is not guaranteed to return a new instance for every call.
     * <p>
     * Because of the possibility of {@linkplain java.lang.reflect.Method#isBridge bridge methods}
     * and other corner cases, the interface may also have several abstract methods
     * with the same name but having distinct descriptors (types of returns and parameters).
     * In this case, all the methods are bound in common to the one given target.
     * The type check and effective {@code asType} conversion is applied to each
     * method type descriptor, and all abstract methods are bound to the target in common.
     * Beyond this type check, no further checks are made to determine that the
     * abstract methods are related in any way.
     * <p>
     * Future versions of this API may accept additional types,
     * such as abstract classes with single abstract methods.
     * Future versions of this API may also equip wrapper instances
     * with one or more additional public "marker" interfaces.
     * <p>
     * If a security manager is installed, this method is caller sensitive.
     * During any invocation of the target method handle via the returned wrapper,
     * the original creator of the wrapper (the caller) will be visible
     * to context checks requested by the security manager.
     *
     * @param <T> the desired type of the wrapper, a single-method interface
     * @param intfc a class object representing {@code T}
     * @param target the method handle to invoke from the wrapper
     * @return a correctly-typed wrapper for the given target
     * @throws NullPointerException if either argument is null
     * @throws IllegalArgumentException if the {@code intfc} is not a
     *         valid argument to this method
     * @throws WrongMethodTypeException if the target cannot
     *         be converted to the type required by the requested interface
     */
    // Other notes to implementors:
    // <p>
    // No stable mapping is promised between the single-method interface and
    // the implementation class C.  Over time, several implementation
    // classes might be used for the same type.
    // <p>
    // If the implementation is able
    // to prove that a wrapper of the required type
    // has already been created for a given
    // method handle, or for another method handle with the
    // same behavior, the implementation may return that wrapper in place of
    // a new wrapper.
    // <p>
    // This method is designed to apply to common use cases
    // where a single method handle must interoperate with
    // an interface that implements a function-like
    // API.  Additional variations, such as single-abstract-method classes with
    // private constructors, or interfaces with multiple but related
    // entry points, must be covered by hand-written or automatically
    // generated adapter classes.
    //
    @SuppressWarnings({"removal",
                       "doclint:reference"}) // cross-module links
    @CallerSensitive
    public static <T> T asInterfaceInstance(final Class<T> intfc, final MethodHandle target) {
        if (!intfc.isInterface() || !Modifier.isPublic(intfc.getModifiers()))
            throw newIllegalArgumentException("not a public interface", intfc.getName());
        if (intfc.isSealed())
            throw newIllegalArgumentException("a sealed interface", intfc.getName());
        if (intfc.isHidden())
            throw newIllegalArgumentException("a hidden interface", intfc.getName());
        Objects.requireNonNull(target);
        final MethodHandle mh;
        if (System.getSecurityManager() != null) {
            final Class<?> caller = Reflection.getCallerClass();
            final ClassLoader ccl = caller != null ? caller.getClassLoader() : null;
            ReflectUtil.checkProxyPackageAccess(ccl, intfc);
            mh = ccl != null ? bindCaller(target, caller) : target;
        } else {
            mh = target;
        }

        // We will just define the class as hidden under the interface
        var ifaceInfo = setupInterface(intfc);
        MethodHandle[] mhs = new MethodHandle[ifaceInfo.methods.size() + 1];
        mhs[0] = target;
        for (var info : ifaceInfo.methods) {
            mhs[info.classDataIndex] = mh.asType(info.mt);
        }

        var bytes = spinTargetDedicatedProxyClass(ifaceInfo);
        Object proxy;
        try {
            var lookup = new MethodHandles.Lookup(intfc)
                    .defineHiddenClassWithClassData(bytes, List.of(mhs), true);
            proxy = lookup.findConstructor(lookup.lookupClass(), MT_void)
                    .asType(MT_Object)
                    .invokeExact();
        } catch (Throwable e) {
            throw new InternalError("Cannot create interface instance", e);
        }

        return intfc.cast(proxy);
    }

    record MethodInfo(int classDataIndex, MethodType mt, MethodTypeDesc desc, List<ClassDesc> rethrowns) {}
    // information about an interface. maybe we will cache in a classvalue one day
    record InterfaceInfo(ClassDesc cd, String name, List<MethodInfo> methods) {}

    private static InterfaceInfo setupInterface(Class<?> intfc) {
        final List<Method> methods = getSingleNameMethods(intfc);
        if (methods == null)
            throw newIllegalArgumentException("not a single-method interface", intfc.getName());
        List<MethodInfo> infos = new ArrayList<>(methods.size());
        for (int i = 0; i < methods.size(); i++) {
            Method sm = methods.get(i);
            MethodType mt = MethodType.methodType(sm.getReturnType(), sm.getParameterTypes());
            var thrown = sm.getExceptionTypes();
            if (thrown.length == 0) {
                infos.add(new MethodInfo(i + 1, mt, desc(mt), DEFAULT_RETHROWNS));
            } else {
                infos.add(new MethodInfo(i + 1, mt, desc(mt), Stream.concat(Stream.of(RuntimeException.class, Error.class),
                        Arrays.stream(thrown)).map(MethodHandleProxies::desc).distinct().toList()));
            }
        }
        return new InterfaceInfo(desc(intfc), methods.get(0).getName(), infos);
    }

    private static final MethodType MT_void = MethodType.methodType(void.class);
    private static final MethodType MT_Object = MethodType.genericMethodType(0);
    private static final ClassDesc CD_RuntimeException = desc(RuntimeException.class);
    private static final ClassDesc CD_Error = desc(Error.class);
    private static final List<ClassDesc> DEFAULT_RETHROWNS = List.of(CD_RuntimeException, CD_Error);
    private static final ClassDesc CD_WrapperInstance = desc(WrapperInstance.class);
    private static final String WRAPPER_INSTANCE_TARGET_NAME = "getWrapperInstanceTarget";
    private static final String WRAPPER_INSTANCE_TYPE_NAME = "getWrapperInstanceType";
    private static final MethodType MT_MethodHandle = MethodType.methodType(MethodHandle.class);
    private static final MethodType MT_MethodHandle_Object = MethodType.methodType(MethodHandle.class, Object.class);
    private static final MethodType MT_Class = MethodType.methodType(Class.class);
    private static final MethodType MT_Class_Object = MethodType.methodType(Class.class, Object.class);
    private static final MethodTypeDesc MTD_MethodHandle = desc(MT_MethodHandle);
    private static final MethodTypeDesc MTD_Class = desc(MT_Class);
    private static final MethodTypeDesc MTD_int = MethodTypeDesc.of(CD_int);
    private static final MethodTypeDesc MTD_int_Object = MethodTypeDesc.of(CD_int, CD_Object);
    private static final MethodTypeDesc MTD_String = MethodTypeDesc.of(CD_String);
    private static final MethodTypeDesc MTD_String_Object = MethodTypeDesc.of(CD_String, CD_Object);
    private static final MethodTypeDesc MTD_boolean_Object = MethodTypeDesc.of(CD_boolean, CD_Object);
    private static final ClassDesc CD_System = desc(System.class);
    private static final ClassDesc CD_Objects = desc(Objects.class);
    private static final DirectMethodHandleDesc MHD_MethodHandle_asType = MethodHandleDesc.ofMethod(
            DirectMethodHandleDesc.Kind.VIRTUAL, CD_MethodHandle, "asType", MethodTypeDesc.of(CD_MethodHandle, CD_MethodType));
    private static final ClassDesc CD_UndeclaredThrowableException = desc(UndeclaredThrowableException.class);
    private static final MethodTypeDesc MTD_void_Throwable = MethodTypeDesc.of(CD_void, CD_Throwable);

    // Spin an implementation class for an interface. A new class should be defined for each handle.
    // class data: [target, mh1, mh2, ...]
    private static byte[] spinTargetDedicatedProxyClass(InterfaceInfo ifaceInfo) {
        ClassDesc targetName = ifaceInfo.cd.nested("$MethodHandleProxy");
        return Classfile.build(targetName, clb -> {
            clb.withSuperclass(CD_Object);
            clb.withFlags(ACC_FINAL | ACC_SYNTHETIC);
            clb.withInterfaceSymbols(ifaceInfo.cd);
            clb.with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(CD_WrapperInstance)));
            // <init>
            clb.withMethodBody(INIT_NAME, MTD_void, 0, cob -> cob
                    .aload(0)
                    .invokespecial(CD_Object, INIT_NAME, MTD_void)
                    .return_());
            DynamicConstantDesc<?> targetCondy = DynamicConstantDesc.ofNamed(BSM_CLASS_DATA_AT, DEFAULT_NAME, CD_MethodHandle, 0);
            // wrapper instance methods
            clb.withMethodBody(WRAPPER_INSTANCE_TARGET_NAME, MTD_MethodHandle, ACC_PRIVATE, cob -> cob
                    .constantInstruction(targetCondy)
                    .areturn());
            clb.withMethodBody(WRAPPER_INSTANCE_TYPE_NAME, MTD_Class, ACC_PRIVATE, cob -> cob
                    .constantInstruction(ifaceInfo.cd)
                    .areturn());
            // object methods
            clb.withMethodBody("toString", MTD_String, ACC_PUBLIC, cob -> cob
                    .aload(0)
                    .invokestatic(CD_Objects, "toIdentityString", MTD_String_Object)
                    .areturn());
            clb.withMethodBody("hashCode", MTD_int, ACC_PUBLIC, cob -> cob
                    .aload(0)
                    .invokestatic(CD_System, "identityHashCode", MTD_int_Object)
                    .ireturn());
            clb.withMethodBody("equals", MTD_boolean_Object, ACC_PUBLIC, cob -> cob
                    .aload(0)
                    .aload(1)
                    .ifThenElse(Opcode.IF_ACMPEQ, CodeBuilder::iconst_1, CodeBuilder::iconst_0)
                    .ireturn());
            // actual implementations
            for (MethodInfo mi : ifaceInfo.methods) {
                DynamicConstantDesc<?> asTypeMhCondy = DynamicConstantDesc.ofNamed(BSM_CLASS_DATA_AT, DEFAULT_NAME,
                        CD_MethodHandle, mi.classDataIndex);
                // we don't need to generate thrown exception attribute
                clb.withMethodBody(ifaceInfo.name, mi.desc, ACC_PUBLIC, cob -> cob
                        .trying(bcb -> {
                                    bcb.constantInstruction(asTypeMhCondy);
                                    int slot = 1;
                                    for (var t : mi.desc.parameterList()) {
                                        var kind = TypeKind.from(t);
                                        bcb.loadInstruction(kind, slot);
                                        slot += kind.slotSize();
                                    }
                                    bcb.invokevirtual(CD_MethodHandle, "invokeExact", mi.desc);
                                    bcb.returnInstruction(TypeKind.from(mi.desc.returnType()));
                                },
                                ctb -> ctb
                                        .catchingMulti(mi.rethrowns, CodeBuilder::athrow)
                                        .catchingAll(cb -> cb
                                                .new_(CD_UndeclaredThrowableException)
                                                .dup_x1()
                                                .swap()
                                                .invokespecial(CD_UndeclaredThrowableException, INIT_NAME, MTD_void_Throwable)
                                                .athrow()
                                        )
                        ));
            }
        });
    }
    private static MethodHandle bindCaller(MethodHandle target, Class<?> hostClass) {
        return MethodHandleImpl.bindCaller(target, hostClass).withVarargs(target.isVarargsCollector());
    }

    /**
     * Determines if the given object was produced by a call to {@link #asInterfaceInstance asInterfaceInstance}.
     * @param x any reference
     * @return true if the reference is not null and points to an object produced by {@code asInterfaceInstance}
     */
    public static boolean isWrapperInstance(Object x) {
        return x.getClass().getDeclaredAnnotation(WrapperInstance.class) != null;
    }

    private static Class<?> ensureWrapperInstance(Object x) {
        if (!isWrapperInstance(x))
            throw newIllegalArgumentException("not a wrapper instance: " + x);
        return x.getClass();
    }

    /**
     * Produces or recovers a target method handle which is behaviorally
     * equivalent to the unique method of this wrapper instance.
     * The object {@code x} must have been produced by a call to {@link #asInterfaceInstance asInterfaceInstance}.
     * This requirement may be tested via {@link #isWrapperInstance isWrapperInstance}.
     * @param x any reference
     * @return a method handle implementing the unique method
     * @throws IllegalArgumentException if the reference x is not to a wrapper instance
     */
    public static MethodHandle wrapperInstanceTarget(Object x) {
        var t = ensureWrapperInstance(x);
        try {
            return (MethodHandle) new MethodHandles.Lookup(t)
                    .findVirtual(t, WRAPPER_INSTANCE_TARGET_NAME, MT_MethodHandle)
                    .asType(MT_MethodHandle_Object)
                    .invokeExact(x);
        } catch (Throwable ex) {
            throw new InternalError("Cannot obtain wrapper instance target", ex);
        }
    }

    /**
     * Recovers the unique single-method interface type for which this wrapper instance was created.
     * The object {@code x} must have been produced by a call to {@link #asInterfaceInstance asInterfaceInstance}.
     * This requirement may be tested via {@link #isWrapperInstance isWrapperInstance}.
     * @param x any reference
     * @return the single-method interface type for which the wrapper was created
     * @throws IllegalArgumentException if the reference x is not to a wrapper instance
     */
    public static Class<?> wrapperInstanceType(Object x) {
        var t = ensureWrapperInstance(x);
        try {
            return (Class<?>) new MethodHandles.Lookup(t)
                    .findVirtual(t, WRAPPER_INSTANCE_TYPE_NAME, MT_Class)
                    .asType(MT_Class_Object)
                    .invokeExact(x);
        } catch (Throwable ex) {
            throw new InternalError("Cannot obtain wrapper instance type", ex);
        }
    }

    private static ClassDesc desc(Class<?> cl) {
        return cl.describeConstable().orElseThrow(() -> new InternalError("Cannot convert class "
                + cl.getName() + " to a constant"));
    }

    private static MethodTypeDesc desc(MethodType mt) {
        return mt.describeConstable().orElseThrow(() -> new InternalError("Cannot convert method type "
                + mt + " to a constant"));
    }

    private static boolean isObjectMethod(Method m) {
        return switch (m.getName()) {
            case "toString" -> m.getReturnType() == String.class
                               && m.getParameterCount() == 0;
            case "hashCode" -> m.getReturnType() == int.class
                               && m.getParameterCount() == 0;
            case "equals"   -> m.getReturnType() == boolean.class
                               && m.getParameterCount() == 1
                               && m.getParameterTypes()[0] == Object.class;
            default -> false;
        };
    }

    private static List<Method> getSingleNameMethods(Class<?> intfc) {
        ArrayList<Method> methods = new ArrayList<>();
        String uniqueName = null;
        for (Method m : intfc.getMethods()) {
            if (isObjectMethod(m))  continue;
            if (!Modifier.isAbstract(m.getModifiers()))  continue;
            String mname = m.getName();
            if (uniqueName == null)
                uniqueName = mname;
            else if (!uniqueName.equals(mname))
                return null;  // too many abstract methods
            methods.add(m);
        }
        if (uniqueName == null)  return null;
        return methods;
    }
}
