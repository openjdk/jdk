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
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.module.ModuleDescriptor;
import java.lang.ref.SoftReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.security.AccessController;
import java.security.PrivilegedAction;
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
import jdk.internal.classfile.ClassHierarchyResolver;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.TypeKind;
import jdk.internal.module.Modules;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;
import jdk.internal.util.ClassFileDumper;
import sun.reflect.misc.ReflectUtil;

import static java.lang.constant.ConstantDescs.*;
import static java.lang.invoke.MethodHandleStatics.*;
import static java.lang.invoke.MethodType.methodType;
import static java.lang.module.ModuleDescriptor.Modifier.SYNTHETIC;
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
    /*
     * Discussion:
     * Since project leyden aims to improve startup speed, asInterfaceInstance
     * will share one implementation class for each interface than one implementation
     * class for each method handle. This is good for use cases where multiple distinct
     * method handles are converted into one interface stored in a collection, where each
     * instance is invoked rarely (or only once), such as property getters on a bean.
     *
     * Using super-customized implementation classes for each method handle would
     * allow constant-folding of calls through the returned instance, but it comes with
     * a huge class definition overhead for each instance and is not feasible for rare
     * invocation use cases like above.
     *
     * The shared-class implementation is also closer in behavior to the original
     * proxy-backed implementation. We might add another API for super-customized instances.
     */
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

        /*
         * For each interface I define a new hidden class and pass the original and
         * caller-sensitive method handles to its constructor.
         *
         * The hidden classes defined for I is defined in a dynamic module M
         * which has access to the types referenced by the members of I including
         * the parameter types, return type and exception types.
         */
        ProxyClassInfo info = getProxyClassInfo(intfc); // throws IllegalArgumentException

        Object proxy;
        try {
            proxy = info.constructor.invokeExact(info.originalLookup, target, mh);
        } catch (Throwable e) {
            throw uncaughtException(e); // propagates WrongMethodTypeException
        }

        assert proxy.getClass().getModule().isNamed() : proxy.getClass() + " " + proxy.getClass().getModule();
        return intfc.cast(proxy);
    }

    private record LocalMethodInfo(MethodTypeDesc desc, List<ClassDesc> thrown, String fieldName) {}

    private record ProxyClassInfo(MethodHandle constructor, Lookup originalLookup,
                                  MethodHandle targetGetter) {}

    private static final ClassFileDumper DUMPER = ClassFileDumper.getInstance(
            "jdk.invoke.MethodHandleProxies.dumpClassFiles", "DUMP_MH_PROXY_CLASSFILES");

    /*
     * A map from a given interface to the ProxyClassDefiner.
     * This creates a dynamic module for each interface.
     */
    private static final ClassValue<SoftReference<ProxyClassInfo>> PROXY_CLASS_INFOS = new ClassValue<>() {
        @Override
        protected SoftReference<ProxyClassInfo> computeValue(Class<?> intfc) {

            final SamInfo stats = getStats(intfc);
            if (stats == null)
                throw newIllegalArgumentException("not a single-method interface", intfc.getName());

            List<LocalMethodInfo> infos = new ArrayList<>(stats.methods.size());
            for (int i = 0; i < stats.methods.size(); i++) {
                String fieldName = "m" + i;
                Method m = stats.methods.get(i);
                MethodType mt = methodType(m.getReturnType(), JLRA.getExecutableSharedParameterTypes(m));
                MethodTypeDesc mtDesc = desc(mt);
                var thrown = JLRA.getExecutableSharedExceptionTypes(m);
                if (thrown.length == 0) {
                    infos.add(new LocalMethodInfo(mtDesc, DEFAULT_RETHROWS, fieldName));
                } else {
                    infos.add(new LocalMethodInfo(mtDesc, Stream.concat(DEFAULT_RETHROWS.stream(),
                            Arrays.stream(thrown).map(MethodHandleProxies::desc)).distinct().toList(), fieldName));
                }
            }

            Set<Class<?>> referencedTypes = stats.referencedTypes;
            var loader = intfc.getClassLoader();
            Module targetModule = newDynamicModule(loader, referencedTypes);

            // generate a class file in the package of the dynamic module
            String pn = targetModule.getName();
            String n = intfc.getName();
            int i = n.lastIndexOf('.');
            String cn = i > 0 ? pn + "." + n.substring(i+1) : pn + "." + n;
            ClassDesc proxyDesc = ClassDesc.of(cn);
            byte[] template = createTemplate(loader, proxyDesc, desc(intfc), stats.singleName, infos);
            var definer = new Lookup(intfc).makeHiddenClassDefiner(cn, template, Set.of(), DUMPER);
            Lookup lookup;

            @SuppressWarnings("removal")
            var sm = System.getSecurityManager();
            if (sm != null) {
                @SuppressWarnings("removal")
                var l = AccessController.doPrivileged((PrivilegedAction<Lookup>) () -> definer.defineClassAsLookup(true));
                lookup = l;
            } else {
                lookup = definer.defineClassAsLookup(true);
            }

            MethodHandle constructor;
            MethodHandle targetGetter;
            try {
                constructor = lookup.findConstructor(lookup.lookupClass(), MT_void_Lookup_MethodHandle_MethodHandle)
                        .asType(MT_Object_Lookup_MethodHandle_MethodHandle);
                targetGetter = lookup.findGetter(lookup.lookupClass(), ORIGINAL_TARGET_NAME, MethodHandle.class)
                        .asType(MT_MethodHandle_Object);
            } catch (Throwable ex) {
                throw uncaughtException(ex);
            }
            return new SoftReference<>(new ProxyClassInfo(constructor, lookup, targetGetter));
        }
    };

    private static ProxyClassInfo getProxyClassInfo(Class<?> intfc) throws IllegalArgumentException {
        while (true) {
            var maybeInfo = PROXY_CLASS_INFOS.get(intfc); // throws IllegalArgumentException
            var info = maybeInfo.get();
            if (info != null) {
                return info;
            }
            PROXY_CLASS_INFOS.remove(intfc); // force recomputation
        }
    }

    private static final List<ClassDesc> DEFAULT_RETHROWS = List.of(desc(RuntimeException.class), desc(Error.class));
    private static final ClassDesc CD_UndeclaredThrowableException = desc(UndeclaredThrowableException.class);
    private static final ClassDesc CD_IllegalAccessException = desc(IllegalAccessException.class);
    private static final MethodTypeDesc MTD_void_Throwable = MethodTypeDesc.of(CD_void, CD_Throwable);
    private static final MethodType MT_void_Lookup_MethodHandle_MethodHandle = methodType(void.class, Lookup.class, MethodHandle.class, MethodHandle.class);
    private static final MethodType MT_Object_Lookup_MethodHandle_MethodHandle = MT_void_Lookup_MethodHandle_MethodHandle.changeReturnType(Object.class);
    private static final MethodType MT_MethodHandle_Object = methodType(MethodHandle.class, Object.class);
    private static final MethodTypeDesc MTD_void_Lookup_MethodHandle_MethodHandle = desc(MT_void_Lookup_MethodHandle_MethodHandle);
    private static final MethodTypeDesc MTD_void_Lookup = MethodTypeDesc.of(CD_void, CD_MethodHandles_Lookup);
    private static final MethodTypeDesc MTD_MethodHandle_MethodType = MethodTypeDesc.of(CD_MethodHandle, CD_MethodType);
    private static final MethodTypeDesc MTD_Class = MethodTypeDesc.of(CD_Class);
    private static final MethodTypeDesc MTD_int = MethodTypeDesc.of(CD_int);
    private static final MethodTypeDesc MTD_String = MethodTypeDesc.of(CD_String);
    private static final MethodTypeDesc MTD_void_String = MethodTypeDesc.of(CD_void, CD_String);
    private static final String ORIGINAL_TARGET_NAME = "originalTarget";
    private static final String ORIGINAL_TYPE_NAME = "originalType";
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
                                         String methodName, List<LocalMethodInfo> methods) {
        return Classfile.of(ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofClassLoading(loader)))
                .build(proxyDesc, clb -> {
            clb.withSuperclass(CD_Object);
            clb.withFlags(ACC_FINAL | ACC_SYNTHETIC);
            clb.withInterfaceSymbols(ifaceDesc);

            // individual handle fields
            clb.withField(ORIGINAL_TYPE_NAME, CD_Class, ACC_PRIVATE | ACC_STATIC | ACC_FINAL);
            clb.withField(ORIGINAL_TARGET_NAME, CD_MethodHandle, ACC_PRIVATE | ACC_FINAL);
            for (var mi : methods) {
                clb.withField(mi.fieldName, CD_MethodHandle, ACC_PRIVATE | ACC_FINAL);
            }

            // <clinit>
            clb.withMethodBody(CLASS_INIT_NAME, MTD_void, ACC_STATIC, cob -> {
                cob.constantInstruction(ifaceDesc);
                cob.putstatic(proxyDesc, ORIGINAL_TYPE_NAME, CD_Class);
                cob.return_();
            });

            // <init>(Lookup, MethodHandle originalHandle, MethodHandle implHandle)
            clb.withMethodBody(INIT_NAME, MTD_void_Lookup_MethodHandle_MethodHandle, 0, cob -> {
                cob.aload(0);
                cob.invokespecial(CD_Object, INIT_NAME, MTD_void);

                // WrapperInstance.ensureOriginalLookup
                cob.aload(1);
                cob.invokestatic(proxyDesc, "ensureOriginalLookup", MTD_void_Lookup);

                // keep original target
                // this.originalTarget = originalHandle;
                cob.aload(0);
                cob.aload(2);
                cob.putfield(proxyDesc, ORIGINAL_TARGET_NAME, CD_MethodHandle);

                // convert individual handles
                for (var mi : methods) {
                    // this.handleField = implHandle.asType(xxType);
                    cob.aload(0);
                    cob.aload(3);
                    cob.constantInstruction(mi.desc);
                    cob.invokevirtual(CD_MethodHandle, "asType", MTD_MethodHandle_MethodType);
                    cob.putfield(proxyDesc, mi.fieldName, CD_MethodHandle);
                }

                // complete
                cob.return_();
            });

            clb.withMethodBody(ENSURE_ORIGINAL_LOOKUP, MTD_void_Lookup, ACC_PRIVATE | ACC_STATIC, cob -> {
                var failLabel = cob.newLabel();
                // check lookupClass
                cob.aload(0);
                cob.invokevirtual(CD_MethodHandles_Lookup, "lookupClass", MTD_Class);
                cob.constantInstruction(proxyDesc);
                cob.if_acmpne(failLabel);
                // check original access
                cob.aload(0);
                cob.invokevirtual(CD_MethodHandles_Lookup, "lookupModes", MTD_int);
                cob.constantInstruction(Lookup.ORIGINAL);
                cob.iand();
                cob.ifeq(failLabel);
                // success
                cob.return_();
                // throw exception
                cob.labelBinding(failLabel);
                cob.new_(CD_IllegalAccessException);
                cob.dup();
                cob.aload(0); // lookup
                cob.invokevirtual(CD_Object, "toString", MTD_String);
                cob.invokespecial(CD_IllegalAccessException, INIT_NAME, MTD_void_String);
                cob.athrow();
            });

            // implementation methods
            for (LocalMethodInfo mi : methods) {
                // we don't need to generate thrown exception attribute
                clb.withMethodBody(methodName, mi.desc, ACC_PUBLIC, cob -> cob
                        .trying(bcb -> {
                                    // return this.handleField.invokeExact(arguments...);
                                    bcb.aload(0);
                                    bcb.getfield(proxyDesc, mi.fieldName, CD_MethodHandle);
                                    for (int j = 0; j < mi.desc.parameterCount(); j++) {
                                        bcb.loadInstruction(TypeKind.from(mi.desc.parameterType(j)), bcb.parameterSlot(j));
                                    }
                                    bcb.invokevirtual(CD_MethodHandle, "invokeExact", mi.desc);
                                    bcb.returnInstruction(TypeKind.from(mi.desc.returnType()));
                                }, ctb -> ctb
                                        // catch (Error | RuntimeException | Declared ex) { throw ex; }
                                        .catchingMulti(mi.thrown, CodeBuilder::athrow)
                                        // catch (Throwable ex) { throw new UndeclaredThrowableException(ex); }
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
        return isWrapperClass(x.getClass());
    }

    static boolean isWrapperClass(Class<?> cls) {
        try {
            return WRAPPER_TYPES.get(cls) != null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static final ClassValue<Class<?>> WRAPPER_TYPES = new ClassValue<>() {
        @Override
        protected Class<?> computeValue(Class<?> type) {
            MethodHandle originalTypeField;
            try {
                originalTypeField = new Lookup(type).findStaticGetter(type, ORIGINAL_TYPE_NAME, Class.class);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                return null;
            }

            Class<?> originalType;
            try {
                originalType = (Class<?>) originalTypeField.invokeExact();
            } catch (Throwable e) {
                throw uncaughtException(e);
            }

            if (originalType == null)
                return null;

            try {
                getProxyClassInfo(originalType); // throws IAE
            } catch (IllegalArgumentException ex) {
                return null;
            }

            return originalType;
        }
    };

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

        var getter = getProxyClassInfo(WRAPPER_TYPES.get(x.getClass())).targetGetter;

        try {
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

        return WRAPPER_TYPES.get(x.getClass());
    }

    private static ClassDesc desc(Class<?> cl) {
        return cl.describeConstable().orElseThrow(() -> newInternalError("Cannot convert class "
                + cl.getName() + " to a constant"));
    }

    private static MethodTypeDesc desc(MethodType mt) {
        return mt.describeConstable().orElseThrow(() -> newInternalError("Cannot convert method type "
                + mt + " to a constant"));
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

    /**
     * Stores the result of iteration over methods in a given single-abstract-method interface.
     *
     * @param singleName the single abstract method's name in the given interface
     * @param methods the abstract methods to implement in the given interface
     * @param referencedTypes a set of types that are referenced by the instance methods of the given interface
     */
    private record SamInfo(String singleName, List<Method> methods, Set<Class<?>> referencedTypes) {
    }

    /*
     * Returns null if given interface is not SAM
     */
    private static SamInfo getStats(Class<?> intfc) {
        if (!intfc.isInterface()) {
            throw new IllegalArgumentException(intfc + " not an inteface");
        }

        ArrayList<Method> methods = new ArrayList<>();
        var types = new HashSet<Class<?>>();
        types.add(intfc);
        String uniqueName = null;
        for (Method m : intfc.getMethods()) {
            if (Modifier.isStatic(m.getModifiers()))
                continue;

            if (isObjectMethod(m))
                continue; // covered by java.base reads

            addElementType(types, m.getReturnType());
            addElementTypes(types, JLRA.getExecutableSharedParameterTypes(m));
            addElementTypes(types, JLRA.getExecutableSharedExceptionTypes(m));

            if (!Modifier.isAbstract(m.getModifiers()))
                continue;
            String mname = m.getName();
            if (uniqueName == null)
                uniqueName = mname;
            else if (!uniqueName.equals(mname))
                return null;  // too many abstract methods
            methods.add(m);
        }

        if (uniqueName == null)
            return null;

        return new SamInfo(uniqueName, methods, types);
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
