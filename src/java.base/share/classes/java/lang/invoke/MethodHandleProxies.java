/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.module.ModuleDescriptor;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import jdk.internal.access.JavaLangReflectAccess;
import jdk.internal.access.SharedSecrets;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;

import jdk.internal.constant.ConstantUtils;
import jdk.internal.loader.ClassLoaders;
import jdk.internal.module.Modules;
import jdk.internal.util.ClassFileDumper;
import jdk.internal.util.ReferencedKeySet;

import static java.lang.constant.ConstantDescs.*;
import static java.lang.invoke.MethodHandleStatics.*;
import static java.lang.invoke.MethodType.methodType;
import static java.lang.module.ModuleDescriptor.Modifier.SYNTHETIC;
import static java.lang.classfile.ClassFile.*;
import static jdk.internal.constant.ConstantUtils.*;

/**
 * This class consists exclusively of static methods that help adapt
 * method handles to other JVM types, such as interfaces.
 *
 * @since 1.7
 */
public final class MethodHandleProxies {

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
     * The interface must be public, not {@linkplain Class#isHidden() hidden},
     * and not {@linkplain Class#isSealed() sealed}.
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
    @SuppressWarnings("doclint:reference") // cross-module links
    public static <T> T asInterfaceInstance(final Class<T> intfc, final MethodHandle target) {
        if (!intfc.isInterface() || !Modifier.isPublic(intfc.getModifiers()))
            throw newIllegalArgumentException("not a public interface", intfc.getName());
        if (intfc.isSealed())
            throw newIllegalArgumentException("a sealed interface", intfc.getName());
        if (intfc.isHidden())
            throw newIllegalArgumentException("a hidden interface", intfc.getName());
        Objects.requireNonNull(target);
        final MethodHandle mh = target;

        // Define one hidden class for each interface.  Create an instance of
        // the hidden class for a given target method handle which will be
        // accessed via getfield.  Multiple instances may be created for a
        // hidden class.  This approach allows the generated hidden classes
        // more shareable.
        //
        // The implementation class is weakly referenced; a new class is
        // defined if the last one has been garbage collected.
        //
        // An alternative approach is to define one hidden class with the
        // target method handle as class data and the target method handle
        // is loaded via ldc/condy.  If more than one target method handles
        // are used, the extra classes will pollute the same type profiles.
        // In addition, hidden classes without class data is more friendly
        // for pre-generation (shifting the dynamic class generation from
        // runtime to an earlier phrase).
        Class<?> proxyClass = getProxyClass(intfc);  // throws IllegalArgumentException
        Lookup lookup = new Lookup(proxyClass);
        Object proxy;
        try {
            MethodHandle constructor = lookup.findConstructor(proxyClass,
                                                              MT_void_Lookup_MethodHandle_MethodHandle)
                                             .asType(MT_Object_Lookup_MethodHandle_MethodHandle);
            proxy = constructor.invokeExact(lookup, target, mh);
        } catch (Throwable ex) {
            throw uncaughtException(ex);
        }
        assert proxy.getClass().getModule().isNamed() : proxy.getClass() + " " + proxy.getClass().getModule();
        return intfc.cast(proxy);
    }

    private record MethodInfo(MethodTypeDesc desc, List<ClassDesc> thrown, String fieldName) {}

    private static final ClassFileDumper DUMPER = ClassFileDumper.getInstance(
            "jdk.invoke.MethodHandleProxies.dumpClassFiles", "DUMP_MH_PROXY_CLASSFILES");

    private static final Set<Class<?>> WRAPPER_TYPES = ReferencedKeySet.create(false, ReferencedKeySet.concurrentHashMapSupplier());
    private static final ClassValue<WeakReferenceHolder<Class<?>>> PROXIES = new ClassValue<>() {
        @Override
        protected WeakReferenceHolder<Class<?>> computeValue(Class<?> intfc) {
            return new WeakReferenceHolder<>(newProxyClass(intfc));
        }
    };

    private static Class<?> newProxyClass(Class<?> intfc) {
        List<MethodInfo> methods = new ArrayList<>();
        Set<Class<?>> referencedTypes = new HashSet<>();
        referencedTypes.add(intfc);
        String uniqueName = null;
        int count = 0;
        for (Method m : intfc.getMethods()) {
            if (!Modifier.isAbstract(m.getModifiers()))
                continue;

            if (isObjectMethod(m))
                continue;

            // ensure it's SAM interface
            String methodName = m.getName();
            if (uniqueName == null) {
                uniqueName = methodName;
            } else if (!uniqueName.equals(methodName)) {
                // too many abstract methods
                throw newIllegalArgumentException("not a single-method interface", intfc.getName());
            }

            // the field name holding the method handle for this method
            String fieldName = "m" + count++;
            var md = methodTypeDesc(m.getReturnType(), JLRA.getExecutableSharedParameterTypes(m));
            var thrown = JLRA.getExecutableSharedExceptionTypes(m);
            var exceptionTypeDescs =
                    thrown.length == 0 ? DEFAULT_RETHROWS
                                       : Stream.concat(DEFAULT_RETHROWS.stream(),
                                                       Arrays.stream(thrown).map(ConstantUtils::referenceClassDesc))
                                               .distinct().toList();
            methods.add(new MethodInfo(md, exceptionTypeDescs, fieldName));

            // find the types referenced by this method
            addElementType(referencedTypes, m.getReturnType());
            addElementTypes(referencedTypes, JLRA.getExecutableSharedParameterTypes(m));
            addElementTypes(referencedTypes, JLRA.getExecutableSharedExceptionTypes(m));
        }

        if (uniqueName == null)
            throw newIllegalArgumentException("no method in ", intfc.getName());

        // create a dynamic module for each proxy class, which needs access
        // to the types referenced by the members of the interface including
        // the parameter types, return type and exception types
        var loader = intfc.getClassLoader();
        Module targetModule = newDynamicModule(loader, referencedTypes);

        // generate a class file in the package of the dynamic module
        String packageName = targetModule.getName();
        String intfcName = intfc.getName();
        int i = intfcName.lastIndexOf('.');
        // jdk.MHProxy#.Interface
        String className = packageName + "." + (i > 0 ? intfcName.substring(i + 1) : intfcName);
        byte[] template = createTemplate(loader, binaryNameToDesc(className),
                referenceClassDesc(intfc), uniqueName, methods);
        // define the dynamic module to the class loader of the interface
        var definer = new Lookup(intfc).makeHiddenClassDefiner(className, template, DUMPER);

        Lookup lookup = definer.defineClassAsLookup(true);
        // cache the wrapper type
        var ret = lookup.lookupClass();
        WRAPPER_TYPES.add(ret);
        return ret;
    }

    private static final class WeakReferenceHolder<T> {
        private volatile WeakReference<T> ref;

        WeakReferenceHolder(T value) {
            set(value);
        }

        void set(T value) {
            ref = new WeakReference<>(value);
        }

        T get() {
            return ref.get();
        }
    }

    private static Class<?> getProxyClass(Class<?> intfc) {
        WeakReferenceHolder<Class<?>> r = PROXIES.get(intfc);
        Class<?> cl = r.get();
        if (cl != null)
            return cl;

        // avoid spinning multiple classes in a race
        synchronized (r) {
            cl = r.get();
            if (cl != null)
                return cl;

            // If the referent is cleared, create a new value and update cached weak reference.
            cl = newProxyClass(intfc);
            r.set(cl);
            return cl;
        }
    }

    private static final List<ClassDesc> DEFAULT_RETHROWS = List.of(referenceClassDesc(RuntimeException.class), referenceClassDesc(Error.class));
    private static final ClassDesc CD_UndeclaredThrowableException = referenceClassDesc(UndeclaredThrowableException.class);
    private static final ClassDesc CD_IllegalAccessException = referenceClassDesc(IllegalAccessException.class);
    private static final MethodTypeDesc MTD_void_Throwable = MethodTypeDesc.of(CD_void, CD_Throwable);
    private static final MethodType MT_void_Lookup_MethodHandle_MethodHandle =
            methodType(void.class, Lookup.class, MethodHandle.class, MethodHandle.class);
    private static final MethodType MT_Object_Lookup_MethodHandle_MethodHandle =
            MT_void_Lookup_MethodHandle_MethodHandle.changeReturnType(Object.class);
    private static final MethodType MT_MethodHandle_Object = methodType(MethodHandle.class, Object.class);
    private static final MethodTypeDesc MTD_void_Lookup_MethodHandle_MethodHandle
            = methodTypeDesc(MT_void_Lookup_MethodHandle_MethodHandle);
    private static final MethodTypeDesc MTD_void_Lookup = MethodTypeDesc.of(CD_void, CD_MethodHandles_Lookup);
    private static final MethodTypeDesc MTD_MethodHandle_MethodType = MethodTypeDesc.of(CD_MethodHandle, CD_MethodType);
    private static final MethodTypeDesc MTD_Class = MethodTypeDesc.of(CD_Class);
    private static final MethodTypeDesc MTD_int = MethodTypeDesc.of(CD_int);
    private static final MethodTypeDesc MTD_String = MethodTypeDesc.of(CD_String);
    private static final MethodTypeDesc MTD_void_String = MethodTypeDesc.of(CD_void, CD_String);
    private static final String TARGET_NAME = "target";
    private static final String TYPE_NAME = "interfaceType";
    private static final String ENSURE_ORIGINAL_LOOKUP = "ensureOriginalLookup";

    /**
     * Creates an implementation class file for a given interface. One implementation class is
     * defined for each interface.
     *
     * @param ifaceDesc the given interface
     * @param methodName the name of the single abstract method
     * @param methods the information for implementation methods
     * @return the bytes of the implementation classes
     */
    private static byte[] createTemplate(ClassLoader loader, ClassDesc proxyDesc, ClassDesc ifaceDesc,
                                         String methodName, List<MethodInfo> methods) {
        return ClassFile.of(ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofClassLoading(loader == null ?
                        ClassLoaders.platformClassLoader() : loader)))
                        .build(proxyDesc, clb -> {
            clb.withSuperclass(CD_Object)
               .withFlags(ACC_FINAL | ACC_SYNTHETIC)
               .withInterfaceSymbols(ifaceDesc)
               // static and instance fields
               .withField(TYPE_NAME, CD_Class, ACC_PRIVATE | ACC_STATIC | ACC_FINAL)
               .withField(TARGET_NAME, CD_MethodHandle, ACC_PRIVATE | ACC_FINAL);
            for (var mi : methods) {
                clb.withField(mi.fieldName, CD_MethodHandle, ACC_PRIVATE | ACC_FINAL);
            }

            // <clinit>
            clb.withMethodBody(CLASS_INIT_NAME, MTD_void, ACC_STATIC, cob -> {
                cob.loadConstant(ifaceDesc)
                   .putstatic(proxyDesc, TYPE_NAME, CD_Class)
                   .return_();
            });

            // <init>(Lookup, MethodHandle target, MethodHandle callerBoundTarget)
            clb.withMethodBody(INIT_NAME, MTD_void_Lookup_MethodHandle_MethodHandle, 0, cob -> {
                cob.aload(0)
                   .invokespecial(CD_Object, INIT_NAME, MTD_void)
                   // call ensureOriginalLookup to verify the given Lookup has access
                   .aload(1)
                   .invokestatic(proxyDesc, ENSURE_ORIGINAL_LOOKUP, MTD_void_Lookup)
                   // this.target = target;
                   .aload(0)
                   .aload(2)
                   .putfield(proxyDesc, TARGET_NAME, CD_MethodHandle);

                // method handles adjusted to the method type of each method
                for (var mi : methods) {
                    // this.m<i> = callerBoundTarget.asType(xxType);
                    cob.aload(0)
                       .aload(3)
                       .loadConstant(mi.desc)
                       .invokevirtual(CD_MethodHandle, "asType", MTD_MethodHandle_MethodType)
                       .putfield(proxyDesc, mi.fieldName, CD_MethodHandle);
                }

                // complete
                cob.return_();
            });

            // private static void ensureOriginalLookup(Lookup) checks if the given Lookup
            // has ORIGINAL access to this class, i.e. the lookup class is this class;
            // otherwise, IllegalAccessException is thrown
            clb.withMethodBody(ENSURE_ORIGINAL_LOOKUP, MTD_void_Lookup, ACC_PRIVATE | ACC_STATIC, cob -> {
                var failLabel = cob.newLabel();
                // check lookupClass
                cob.aload(0)
                   .invokevirtual(CD_MethodHandles_Lookup, "lookupClass", MTD_Class)
                   .loadConstant(proxyDesc)
                   .if_acmpne(failLabel)
                   // check original access
                   .aload(0)
                   .invokevirtual(CD_MethodHandles_Lookup, "lookupModes", MTD_int)
                   .loadConstant(Lookup.ORIGINAL)
                   .iand()
                   .ifeq(failLabel)
                   // success
                   .return_()
                   // throw exception
                   .labelBinding(failLabel)
                   .new_(CD_IllegalAccessException)
                   .dup()
                   .aload(0) // lookup
                   .invokevirtual(CD_Object, "toString", MTD_String)
                   .invokespecial(CD_IllegalAccessException, INIT_NAME, MTD_void_String)
                   .athrow();
            });

            // implementation methods
            for (MethodInfo mi : methods) {
                // no need to generate thrown exception attribute
                clb.withMethodBody(methodName, mi.desc, ACC_PUBLIC, cob -> cob
                        .trying(bcb -> {
                                    // return this.handleField.invokeExact(arguments...);
                                    bcb.aload(0)
                                       .getfield(proxyDesc, mi.fieldName, CD_MethodHandle);
                                    for (int j = 0; j < mi.desc.parameterCount(); j++) {
                                        bcb.loadLocal(TypeKind.from(mi.desc.parameterType(j)),
                                                bcb.parameterSlot(j));
                                    }
                                    bcb.invokevirtual(CD_MethodHandle, "invokeExact", mi.desc)
                                       .return_(TypeKind.from(mi.desc.returnType()));
                                }, ctb -> ctb
                                        // catch (Error | RuntimeException | Declared ex) { throw ex; }
                                        .catchingMulti(mi.thrown, CodeBuilder::athrow)
                                        // catch (Throwable ex) { throw new UndeclaredThrowableException(ex); }
                                        .catchingAll(cb -> cb
                                                .new_(CD_UndeclaredThrowableException)
                                                .dup_x1()
                                                .swap()
                                                .invokespecial(CD_UndeclaredThrowableException,
                                                        INIT_NAME, MTD_void_Throwable)
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
        return x != null && WRAPPER_TYPES.contains(x.getClass());
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
        if (!isWrapperInstance(x))
            throw new IllegalArgumentException("not a wrapper instance: " + x);

        try {
            Class<?> type = x.getClass();
            MethodHandle getter = new Lookup(type).findGetter(type, TARGET_NAME, MethodHandle.class)
                                                  .asType(MT_MethodHandle_Object);
            return (MethodHandle) getter.invokeExact(x);
        } catch (Throwable ex) {
            throw uncaughtException(ex);
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
        if (!isWrapperInstance(x))
            throw new IllegalArgumentException("not a wrapper instance: " + x);

        try {
            Class<?> type = x.getClass();
            MethodHandle originalTypeField = new Lookup(type).findStaticGetter(type, TYPE_NAME, Class.class);
            return (Class<?>) originalTypeField.invokeExact();
        } catch (Throwable e) {
            throw uncaughtException(e);
        }
    }

    private static final JavaLangReflectAccess JLRA = SharedSecrets.getJavaLangReflectAccess();
    private static final AtomicInteger counter = new AtomicInteger();

    private static String nextModuleName() {
        return "jdk.MHProxy" + counter.incrementAndGet();
    }

    /**
     * Create a dynamic module defined to the given class loader and has
     * access to the given types.
     * <p>
     * The dynamic module contains only one single package named the same as
     * the name of the dynamic module.  It's not exported or open.
     */
    private static Module newDynamicModule(ClassLoader ld, Set<Class<?>> types) {
        Objects.requireNonNull(types);

        // create a dynamic module and setup module access
        String mn = nextModuleName();
        ModuleDescriptor descriptor = ModuleDescriptor.newModule(mn, Set.of(SYNTHETIC))
                .packages(Set.of(mn))
                .build();

        Module dynModule = Modules.defineModule(ld, descriptor, null);
        Module javaBase = Object.class.getModule();

        Modules.addReads(dynModule, javaBase);
        Modules.addOpens(dynModule, mn, javaBase);

        for (Class<?> c : types) {
            ensureAccess(dynModule, c);
        }
        return dynModule;
    }

    private static boolean isObjectMethod(Method m) {
        return switch (m.getName()) {
            case "toString" -> m.getReturnType() == String.class
                    && m.getParameterCount() == 0;
            case "hashCode" -> m.getReturnType() == int.class
                    && m.getParameterCount() == 0;
            case "equals"   -> m.getReturnType() == boolean.class
                    && m.getParameterCount() == 1
                    && JLRA.getExecutableSharedParameterTypes(m)[0] == Object.class;
            default -> false;
        };
    }

    /*
     * Ensure the given module can access the given class.
     */
    private static void ensureAccess(Module target, Class<?> c) {
        Module m = c.getModule();
        // add read edge and qualified export for the target module to access
        if (!target.canRead(m)) {
            Modules.addReads(target, m);
        }
        String pn = c.getPackageName();
        if (!m.isExported(pn, target)) {
            Modules.addExports(m, pn, target);
        }
    }

    private static void addElementTypes(Set<Class<?>> types, Class<?>... classes) {
        for (var cls : classes) {
            addElementType(types, cls);
        }
    }

    private static void addElementType(Set<Class<?>> types, Class<?> cls) {
        Class<?> e = cls;
        while (e.isArray()) {
            e = e.getComponentType();
        }

        if (!e.isPrimitive()) {
            types.add(e);
        }
    }
}
