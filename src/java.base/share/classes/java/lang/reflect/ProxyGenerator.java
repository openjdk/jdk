/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.reflect;

import java.lang.classfile.*;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.attribute.ExceptionsAttribute;
import sun.security.action.GetBooleanAction;

import java.io.IOException;
import static java.lang.classfile.ClassFile.*;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.classfile.attribute.StackMapTableAttribute;
import java.lang.constant.ClassDesc;
import static java.lang.constant.ConstantDescs.*;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * ProxyGenerator contains the code to generate a dynamic proxy class
 * for the java.lang.reflect.Proxy API.
 * <p>
 * The external interface to ProxyGenerator is the static
 * "generateProxyClass" method.
 */
final class ProxyGenerator {

    private static final ClassDesc
            CD_ClassLoader = ClassDesc.ofInternalName("java/lang/ClassLoader"),
            CD_ClassNotFoundException = ClassDesc.ofInternalName("java/lang/ClassNotFoundException"),
            CD_IllegalAccessException = ClassDesc.ofInternalName("java/lang/IllegalAccessException"),
            CD_InvocationHandler = ClassDesc.ofInternalName("java/lang/reflect/InvocationHandler"),
            CD_Method = ClassDesc.ofInternalName("java/lang/reflect/Method"),
            CD_NoClassDefFoundError = ClassDesc.ofInternalName("java/lang/NoClassDefFoundError"),
            CD_NoSuchMethodError = ClassDesc.ofInternalName("java/lang/NoSuchMethodError"),
            CD_NoSuchMethodException = ClassDesc.ofInternalName("java/lang/NoSuchMethodException"),
            CD_Proxy = ClassDesc.ofInternalName("java/lang/reflect/Proxy"),
            CD_UndeclaredThrowableException = ClassDesc.ofInternalName("java/lang/reflect/UndeclaredThrowableException");

    private static final MethodTypeDesc
            MTD_boolean = MethodTypeDesc.of(CD_boolean),
            MTD_void_InvocationHandler = MethodTypeDesc.of(CD_void, CD_InvocationHandler),
            MTD_void_String = MethodTypeDesc.of(CD_void, CD_String),
            MTD_void_Throwable = MethodTypeDesc.of(CD_void, CD_Throwable),
            MTD_Class = MethodTypeDesc.of(CD_Class),
            MTD_Class_String_boolean_ClassLoader = MethodTypeDesc.of(CD_Class, CD_String, CD_boolean, CD_ClassLoader),
            MTD_ClassLoader = MethodTypeDesc.of(CD_ClassLoader),
            MTD_MethodHandles$Lookup = MethodTypeDesc.of(CD_MethodHandles_Lookup),
            MTD_MethodHandles$Lookup_MethodHandles$Lookup = MethodTypeDesc.of(CD_MethodHandles_Lookup, CD_MethodHandles_Lookup),
            MTD_Method_String_ClassArray = MethodTypeDesc.of(CD_Method, CD_String, CD_Class.arrayType()),
            MTD_Object_Object_Method_ObjectArray = MethodTypeDesc.of(CD_Object, CD_Object, CD_Method, CD_Object.arrayType()),
            MTD_String = MethodTypeDesc.of(CD_String);

    private static final String NAME_LOOKUP_ACCESSOR = "proxyClassLookup";

    private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];

    /**
     * name of field for storing a proxy instance's invocation handler
     */
    private static final String handlerFieldName = "h";

    /**
     * debugging flag for saving generated class files
     */
    @SuppressWarnings("removal")
    private static final boolean saveGeneratedFiles =
            java.security.AccessController.doPrivileged(
                    new GetBooleanAction(
                            "jdk.proxy.ProxyGenerator.saveGeneratedFiles"));

    /* Preloaded ProxyMethod objects for methods in java.lang.Object */
    private static final ProxyMethod hashCodeMethod;
    private static final ProxyMethod equalsMethod;
    private static final ProxyMethod toStringMethod;

    private static final ClassModel TEMPLATE;

    private static final ClassEntry CE_Class;
    private static final ClassEntry CE_ClassNotFoundException;
    private static final ClassEntry CE_NoClassDefFoundError;
    private static final ClassEntry CE_NoSuchMethodError;
    private static final ClassEntry CE_NoSuchMethodException;
    private static final ClassEntry CE_Object;
    private static final ClassEntry CE_Throwable;
    private static final ClassEntry CE_UndeclaredThrowableException;

    private static final FieldRefEntry FRE_Proxy_h;

    private static final InterfaceMethodRefEntry IMRE_InvocationHandler_invoke;

    private static final MethodRefEntry MRE_Class_forName;
    private static final MethodRefEntry MRE_Class_getClassLoader;
    private static final MethodRefEntry MRE_Class_getMethod;
    private static final MethodRefEntry MRE_NoClassDefFoundError_init;
    private static final MethodRefEntry MRE_NoSuchMethodError_init;
    private static final MethodRefEntry MRE_Throwable_getMessage;
    private static final MethodRefEntry MRE_UndeclaredThrowableException_init;

    private static final Utf8Entry UE_Method;

    private static final List<StackMapFrameInfo.VerificationTypeInfo> THROWABLE_STACK;

    @SuppressWarnings("unchecked")
    private static <T extends PoolEntry> T entryByIndex(int index) {
        return (T) TEMPLATE.constantPool().entryByIndex(index);
    }

    static {
        // static template ClassModel holds pre-defined constant pool entries
        // proxy transformed from the template shares the template constant pool
        // each direct use of the template pool entry is significantly faster
        var cc = ClassFile.of();
        var ei = new int[21];
        TEMPLATE = cc.parse(cc.build(CD_Proxy, clb -> {
            clb.withSuperclass(CD_Proxy);
            generateConstructor(clb);
            generateLookupAccessor(clb);
            var cp = clb.constantPool();

            ei[0] = cp.classEntry(CD_Class).index();
            ei[1] = cp.classEntry(CD_ClassNotFoundException).index();
            ei[2] = cp.classEntry(CD_NoClassDefFoundError).index();
            ei[3] = cp.classEntry(CD_NoSuchMethodError).index();
            ei[4] = cp.classEntry(CD_NoSuchMethodException).index();
            ei[5] = cp.classEntry(CD_Object).index();
            ei[6] = cp.classEntry(CD_Throwable).index();
            ei[7] = cp.classEntry(CD_UndeclaredThrowableException).index();

            ei[8] = cp.fieldRefEntry(CD_Proxy, handlerFieldName, CD_InvocationHandler).index();

            ei[9] = cp.interfaceMethodRefEntry(CD_InvocationHandler, "invoke", MTD_Object_Object_Method_ObjectArray).index();

            ei[10] = cp.methodRefEntry(CD_Class, "forName", MTD_Class_String_boolean_ClassLoader).index();
            ei[11] = cp.methodRefEntry(CD_Class, "getClassLoader", MTD_ClassLoader).index();
            ei[12] = cp.methodRefEntry(CD_Class, "getMethod", MTD_Method_String_ClassArray).index();
            ei[13] = cp.methodRefEntry(CD_NoClassDefFoundError, INIT_NAME, MTD_void_String).index();
            ei[14] = cp.methodRefEntry(CD_NoSuchMethodError, INIT_NAME, MTD_void_String).index();
            ei[15] = cp.methodRefEntry(CD_Throwable, "getMessage", MTD_String).index();
            ei[16] = cp.methodRefEntry(CD_UndeclaredThrowableException, INIT_NAME, MTD_void_Throwable).index();

            ei[17] = cp.utf8Entry(CD_Method).index();

            ei[18] = cp.utf8Entry("m0").index();
            ei[19] = cp.utf8Entry("m1").index();
            ei[20] = cp.utf8Entry("m2").index();
        }));

        CE_Class = entryByIndex(ei[0]);
        CE_ClassNotFoundException = entryByIndex(ei[1]);
        CE_NoClassDefFoundError = entryByIndex(ei[2]);
        CE_NoSuchMethodError = entryByIndex(ei[3]);
        CE_NoSuchMethodException = entryByIndex(ei[4]);
        CE_Object = entryByIndex(ei[5]);
        CE_Throwable = entryByIndex(ei[6]);
        CE_UndeclaredThrowableException = entryByIndex(ei[7]);

        FRE_Proxy_h = entryByIndex(ei[8]);

        IMRE_InvocationHandler_invoke = entryByIndex(ei[9]);

        MRE_Class_forName = entryByIndex(ei[10]);
        MRE_Class_getClassLoader = entryByIndex(ei[11]);
        MRE_Class_getMethod = entryByIndex(ei[12]);
        MRE_NoClassDefFoundError_init = entryByIndex(ei[13]);
        MRE_NoSuchMethodError_init = entryByIndex(ei[14]);
        MRE_Throwable_getMessage = entryByIndex(ei[15]);
        MRE_UndeclaredThrowableException_init = entryByIndex(ei[16]);

        UE_Method = entryByIndex(ei[17]);

        try {
            hashCodeMethod = new ProxyMethod(Object.class.getMethod("hashCode"), entryByIndex(ei[18]));
            equalsMethod = new ProxyMethod(Object.class.getMethod("equals", Object.class), entryByIndex(ei[19]));
            toStringMethod = new ProxyMethod(Object.class.getMethod("toString"), entryByIndex(ei[20]));
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(e.getMessage());
        }

        THROWABLE_STACK = List.of(StackMapFrameInfo.ObjectVerificationTypeInfo.of(CE_Throwable));
    }

    /**
     * Classfile context
     */
    private final ClassFile classfileContext;
    private final ConstantPoolBuilder cp;

    /**
     * Name of proxy class
     */
    private ClassEntry classEntry;

    /**
     * Proxy interfaces
     */
    private final List<Class<?>> interfaces;

    /**
     * Proxy class access flags
     */
    private final int accessFlags;

    /**
     * Maps method signature string to list of ProxyMethod objects for
     * proxy methods with that signature.
     * Kept in insertion order to make it easier to compare old and new.
     */
    private final Map<String, List<ProxyMethod>> proxyMethods = new LinkedHashMap<>();

    /**
     * Ordinal of next ProxyMethod object added to proxyMethods.
     * Indexes are reserved for hashcode(0), equals(1), toString(2).
     */
    private int proxyMethodCount = 3;

    /**
     * Construct a ProxyGenerator to generate a proxy class with the
     * specified name and for the given interfaces.
     * <p>
     * A ProxyGenerator object contains the state for the ongoing
     * generation of a particular proxy class.
     */
    private ProxyGenerator(ClassLoader loader, String className, List<Class<?>> interfaces,
                           int accessFlags) {
        this.classfileContext = ClassFile.of(
                ClassFile.StackMapsOption.DROP_STACK_MAPS,
                ClassFile.ClassHierarchyResolverOption.of(
                        ClassHierarchyResolver.ofClassLoading(loader).cached()));
        this.cp = ConstantPoolBuilder.of(TEMPLATE);
        this.classEntry = cp.classEntry(ClassDesc.of(className));
        this.interfaces = interfaces;
        this.accessFlags = accessFlags;
    }

    /**
     * Generate a proxy class given a name and a list of proxy interfaces.
     *
     * @param name        the class name of the proxy class
     * @param interfaces  proxy interfaces
     * @param accessFlags access flags of the proxy class
     */
    @SuppressWarnings("removal")
    static byte[] generateProxyClass(ClassLoader loader,
                                     final String name,
                                     List<Class<?>> interfaces,
                                     int accessFlags) {
        Objects.requireNonNull(interfaces);
        ProxyGenerator gen = new ProxyGenerator(loader, name, interfaces, accessFlags);
        final byte[] classFile = gen.generateClassFile();

        if (saveGeneratedFiles) {
            java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedAction<Void>() {
                        public Void run() {
                            try {
                                int i = name.lastIndexOf('.');
                                Path path;
                                if (i > 0) {
                                    Path dir = Path.of(name.substring(0, i).replace('.', '/'));
                                    Files.createDirectories(dir);
                                    path = dir.resolve(name.substring(i + 1) + ".class");
                                } else {
                                    path = Path.of(name + ".class");
                                }
                                Files.write(path, classFile);
                                return null;
                            } catch (IOException e) {
                                throw new InternalError(
                                        "I/O exception saving generated file: " + e);
                            }
                        }
                    });
        }

        return classFile;
    }

    /**
     * {@return the entries of the given type}
     * @param types the {@code Class} objects, not primitive types nor array types
     */
    private static ClassEntry[] toClassEntries(ConstantPoolBuilder cp, List<Class<?>> types) {
        var ces = new ClassEntry[types.size()];
        for (int i = 0; i < ces.length; i++)
            ces[i] = cp.classEntry(cp.utf8Entry(types.get(i).getName().replace('.', '/')));
        return ces;
    }

    /**
     * {@return the {@code ClassDesc} of the given type}
     * @param type the {@code Class} object
     */
    private static ClassDesc toClassDesc(Class<?> type) {
        return ClassDesc.ofDescriptor(type.descriptorString());
    }

    /**
     * For a given set of proxy methods with the same signature, check
     * that their return types are compatible according to the Proxy
     * specification.
     *
     * Specifically, if there is more than one such method, then all
     * of the return types must be reference types, and there must be
     * one return type that is assignable to each of the rest of them.
     */
    private static void checkReturnTypes(List<ProxyMethod> methods) {
        /*
         * If there is only one method with a given signature, there
         * cannot be a conflict.  This is the only case in which a
         * primitive (or void) return type is allowed.
         */
        if (methods.size() < 2) {
            return;
        }

        /*
         * List of return types that are not yet known to be
         * assignable from ("covered" by) any of the others.
         */
        List<Class<?>> uncoveredReturnTypes = new ArrayList<>(1);

        nextNewReturnType:
        for (ProxyMethod pm : methods) {
            Class<?> newReturnType = pm.returnType;
            if (newReturnType.isPrimitive()) {
                throw new IllegalArgumentException(
                        "methods with same signature " +
                                pm.shortSignature +
                                " but incompatible return types: " +
                                newReturnType.getName() + " and others");
            }
            boolean added = false;

            /*
             * Compare the new return type to the existing uncovered
             * return types.
             */
            ListIterator<Class<?>> liter = uncoveredReturnTypes.listIterator();
            while (liter.hasNext()) {
                Class<?> uncoveredReturnType = liter.next();

                /*
                 * If an existing uncovered return type is assignable
                 * to this new one, then we can forget the new one.
                 */
                if (newReturnType.isAssignableFrom(uncoveredReturnType)) {
                    assert !added;
                    continue nextNewReturnType;
                }

                /*
                 * If the new return type is assignable to an existing
                 * uncovered one, then should replace the existing one
                 * with the new one (or just forget the existing one,
                 * if the new one has already be put in the list).
                 */
                if (uncoveredReturnType.isAssignableFrom(newReturnType)) {
                    // (we can assume that each return type is unique)
                    if (!added) {
                        liter.set(newReturnType);
                        added = true;
                    } else {
                        liter.remove();
                    }
                }
            }

            /*
             * If we got through the list of existing uncovered return
             * types without an assignability relationship, then add
             * the new return type to the list of uncovered ones.
             */
            if (!added) {
                uncoveredReturnTypes.add(newReturnType);
            }
        }

        /*
         * We shouldn't end up with more than one return type that is
         * not assignable from any of the others.
         */
        if (uncoveredReturnTypes.size() > 1) {
            ProxyMethod pm = methods.get(0);
            throw new IllegalArgumentException(
                    "methods with same signature " +
                            pm.shortSignature +
                            " but incompatible return types: " + uncoveredReturnTypes);
        }
    }

    /**
     * Given the exceptions declared in the throws clause of a proxy method,
     * compute the exceptions that need to be caught from the invocation
     * handler's invoke method and rethrown intact in the method's
     * implementation before catching other Throwables and wrapping them
     * in UndeclaredThrowableExceptions.
     *
     * The exceptions to be caught are returned in a List object.  Each
     * exception in the returned list is guaranteed to not be a subclass of
     * any of the other exceptions in the list, so the catch blocks for
     * these exceptions may be generated in any order relative to each other.
     *
     * Error and RuntimeException are each always contained by the returned
     * list (if none of their superclasses are contained), since those
     * unchecked exceptions should always be rethrown intact, and thus their
     * subclasses will never appear in the returned list.
     *
     * The returned List will be empty if java.lang.Throwable is in the
     * given list of declared exceptions, indicating that no exceptions
     * need to be caught.
     */
    private static List<Class<?>> computeUniqueCatchList(Class<?>[] exceptions) {
        List<Class<?>> uniqueList = new ArrayList<>();
        // unique exceptions to catch

        uniqueList.add(Error.class);            // always catch/rethrow these
        uniqueList.add(RuntimeException.class);

        nextException:
        for (Class<?> ex : exceptions) {
            if (ex.isAssignableFrom(Throwable.class)) {
                /*
                 * If Throwable is declared to be thrown by the proxy method,
                 * then no catch blocks are necessary, because the invoke
                 * can, at most, throw Throwable anyway.
                 */
                uniqueList.clear();
                break;
            } else if (!Throwable.class.isAssignableFrom(ex)) {
                /*
                 * Ignore types that cannot be thrown by the invoke method.
                 */
                continue;
            }
            /*
             * Compare this exception against the current list of
             * exceptions that need to be caught:
             */
            for (int j = 0; j < uniqueList.size(); ) {
                Class<?> ex2 = uniqueList.get(j);
                if (ex2.isAssignableFrom(ex)) {
                    /*
                     * if a superclass of this exception is already on
                     * the list to catch, then ignore this one and continue;
                     */
                    continue nextException;
                } else if (ex.isAssignableFrom(ex2)) {
                    /*
                     * if a subclass of this exception is on the list
                     * to catch, then remove it;
                     */
                    uniqueList.remove(j);
                } else {
                    j++;        // else continue comparing.
                }
            }
            // This exception is unique (so far): add it to the list to catch.
            uniqueList.add(ex);
        }
        return uniqueList;
    }

    /**
     * Add to the given list all of the types in the "from" array that
     * are not already contained in the list and are assignable to at
     * least one of the types in the "with" array.
     * <p>
     * This method is useful for computing the greatest common set of
     * declared exceptions from duplicate methods inherited from
     * different interfaces.
     */
    private static void collectCompatibleTypes(Class<?>[] from,
                                               Class<?>[] with,
                                               List<Class<?>> list) {
        for (Class<?> fc : from) {
            if (!list.contains(fc)) {
                for (Class<?> wc : with) {
                    if (wc.isAssignableFrom(fc)) {
                        list.add(fc);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Generate a class file for the proxy class.  This method drives the
     * class file generation process.
     */
    private byte[] generateClassFile() {
        /*
         * Add proxy methods for the hashCode, equals,
         * and toString methods of java.lang.Object.  This is done before
         * the methods from the proxy interfaces so that the methods from
         * java.lang.Object take precedence over duplicate methods in the
         * proxy interfaces.
         */
        addProxyMethod(hashCodeMethod);
        addProxyMethod(equalsMethod);
        addProxyMethod(toStringMethod);

        /*
         * Accumulate all of the methods from the proxy interfaces.
         */
        for (Class<?> intf : interfaces) {
            for (Method m : intf.getMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) {
                    addProxyMethod(m, intf, cp);
                }
            }
        }

        /*
         * For each set of proxy methods with the same signature,
         * verify that the methods' return types are compatible.
         */
        for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
            checkReturnTypes(sigmethods);
        }

        return classfileContext.build(classEntry, cp, clb -> {
            TEMPLATE.forEach(clb);
            clb.withFlags(accessFlags);
            clb.withInterfaces(toClassEntries(cp, interfaces));

            for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
                for (ProxyMethod pm : sigmethods) {
                    // add static field for the Method object
                    clb.withField(pm.methodFieldName, UE_Method, ACC_PRIVATE | ACC_STATIC | ACC_FINAL);

                    // Generate code for proxy method
                    pm.generateMethod(clb, classEntry);
                }
            }

            generateStaticInitializer(clb);
        });
    }

    /**
     * Add another method to be proxied, either by creating a new
     * ProxyMethod object or augmenting an old one for a duplicate
     * method.
     *
     * "fromClass" indicates the proxy interface that the method was
     * found through, which may be different from (a subinterface of)
     * the method's "declaring class".  Note that the first Method
     * object passed for a given name and descriptor identifies the
     * Method object (and thus the declaring class) that will be
     * passed to the invocation handler's "invoke" method for a given
     * set of duplicate methods.
     */
    private void addProxyMethod(Method m, Class<?> fromClass, ConstantPoolBuilder cp) {
        Class<?> returnType = m.getReturnType();
        Class<?>[] exceptionTypes = m.getSharedExceptionTypes();

        String sig = m.toShortSignature();
        List<ProxyMethod> sigmethods = proxyMethods.computeIfAbsent(sig,
                (f) -> new ArrayList<>(3));
        for (ProxyMethod pm : sigmethods) {
            if (returnType == pm.returnType) {
                /*
                 * Found a match: reduce exception types to the
                 * greatest set of exceptions that can be thrown
                 * compatibly with the throws clauses of both
                 * overridden methods.
                 */
                List<Class<?>> legalExceptions = new ArrayList<>();
                collectCompatibleTypes(
                        exceptionTypes, pm.exceptionTypes, legalExceptions);
                collectCompatibleTypes(
                        pm.exceptionTypes, exceptionTypes, legalExceptions);
                pm.exceptionTypes = legalExceptions.toArray(EMPTY_CLASS_ARRAY);
                return;
            }
        }
        sigmethods.add(new ProxyMethod(m, sig, m.getSharedParameterTypes(), returnType,
                exceptionTypes, fromClass,
                cp.utf8Entry("m" + proxyMethodCount++)));
    }

    /**
     * Add an existing ProxyMethod (hashcode, equals, toString).
     *
     * @param pm an existing ProxyMethod
     */
    private void addProxyMethod(ProxyMethod pm) {
        String sig = pm.shortSignature;
        List<ProxyMethod> sigmethods = proxyMethods.computeIfAbsent(sig,
                (f) -> new ArrayList<>(3));
        sigmethods.add(pm);
    }

    /**
     * Generate the constructor method for the proxy class.
     */
    private static void generateConstructor(ClassBuilder clb) {
        clb.withMethodBody(INIT_NAME, MTD_void_InvocationHandler, ACC_PUBLIC, cob -> cob
               .aload(cob.receiverSlot())
               .aload(cob.parameterSlot(0))
               .invokespecial(CD_Proxy, INIT_NAME, MTD_void_InvocationHandler)
               .return_());
    }

    /**
     * Generate the static initializer method for the proxy class.
     */
    private void generateStaticInitializer(ClassBuilder clb) {
        clb.withMethodBody(CLASS_INIT_NAME, MTD_void, ACC_STATIC, cob -> {
            // Put ClassLoader at local variable index 0, used by
            // Class.forName(String, boolean, ClassLoader) calls
            cob.ldc(classEntry)
               .invokevirtual(MRE_Class_getClassLoader)
               .astore(0);
            var ts = cob.newBoundLabel();
            for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
                for (ProxyMethod pm : sigmethods) {
                    pm.codeFieldInitialization(cob, classEntry);
                }
            }
            cob.return_();
            var c1 = cob.newBoundLabel();
            cob.exceptionCatch(ts, c1, c1, CE_NoSuchMethodException)
               .new_(CE_NoSuchMethodError)
               .dup_x1()
               .swap()
               .invokevirtual(MRE_Throwable_getMessage)
               .invokespecial(MRE_NoSuchMethodError_init)
               .athrow();
            var c2 = cob.newBoundLabel();
            cob.exceptionCatch(ts, c1, c2, CE_ClassNotFoundException)
               .new_(CE_NoClassDefFoundError)
               .dup_x1()
               .swap()
               .invokevirtual(MRE_Throwable_getMessage)
               .invokespecial(MRE_NoClassDefFoundError_init)
               .athrow()
               .with(StackMapTableAttribute.of(List.of(
                     StackMapFrameInfo.of(c1, List.of(), THROWABLE_STACK),
                     StackMapFrameInfo.of(c2, List.of(), THROWABLE_STACK))));
        });
    }

    /**
     * Generate the static lookup accessor method that returns the Lookup
     * on this proxy class if the caller's lookup class is java.lang.reflect.Proxy;
     * otherwise, IllegalAccessException is thrown
     */
    private static void generateLookupAccessor(ClassBuilder clb) {
        clb.withMethod(NAME_LOOKUP_ACCESSOR,
                MTD_MethodHandles$Lookup_MethodHandles$Lookup,
                ACC_PRIVATE | ACC_STATIC,
                mb -> mb.with(ExceptionsAttribute.of(List.of(mb.constantPool().classEntry(CD_IllegalAccessException))))
                        .withCode(cob -> cob
                            .block(blockBuilder -> blockBuilder
                                    .aload(cob.parameterSlot(0))
                                    .invokevirtual(CD_MethodHandles_Lookup, "lookupClass", MTD_Class)
                                    .ldc(CD_Proxy)
                                    .if_acmpne(blockBuilder.breakLabel())
                                    .aload(cob.parameterSlot(0))
                                    .invokevirtual(CD_MethodHandles_Lookup, "hasFullPrivilegeAccess", MTD_boolean)
                                    .ifeq(blockBuilder.breakLabel())
                                    .invokestatic(CD_MethodHandles, "lookup", MTD_MethodHandles$Lookup)
                                    .areturn())
                            .new_(CD_IllegalAccessException)
                            .dup()
                            .aload(cob.parameterSlot(0))
                            .invokevirtual(CD_MethodHandles_Lookup, "toString", MTD_String)
                            .invokespecial(CD_IllegalAccessException, INIT_NAME, MTD_void_String)
                            .athrow()));
    }

    /**
     * A ProxyMethod object represents a proxy method in the proxy class
     * being generated: a method whose implementation will encode and
     * dispatch invocations to the proxy instance's invocation handler.
     */
    private static class ProxyMethod {

        private final Method method;
        private final String shortSignature;
        private final Class<?> fromClass;
        private final Class<?>[] parameterTypes;
        private final Class<?> returnType;
        private final Utf8Entry methodFieldName;
        private Class<?>[] exceptionTypes;

        private ProxyMethod(Method method, String sig, Class<?>[] parameterTypes,
                            Class<?> returnType, Class<?>[] exceptionTypes,
                            Class<?> fromClass, Utf8Entry methodFieldName) {
            this.method = method;
            this.shortSignature = sig;
            this.parameterTypes = parameterTypes;
            this.returnType = returnType;
            this.exceptionTypes = exceptionTypes;
            this.fromClass = fromClass;
            this.methodFieldName = methodFieldName;
        }

        /**
         * Create a new specific ProxyMethod with a specific field name
         *
         * @param method          The method for which to create a proxy
         * @param methodFieldName the fieldName to generate
         */
        private ProxyMethod(Method method, Utf8Entry methodFieldName) {
            this(method, method.toShortSignature(),
                 method.getSharedParameterTypes(), method.getReturnType(),
                 method.getSharedExceptionTypes(), method.getDeclaringClass(), methodFieldName);
        }

        /**
         * Generate this method, including the code and exception table entry.
         */
        private void generateMethod(ClassBuilder clb, ClassEntry className) {
            var cp = clb.constantPool();
            MethodTypeDesc desc = MethodTypeDesc.of(toClassDesc(returnType),
                    Arrays.stream(parameterTypes).map(ProxyGenerator::toClassDesc).toArray(ClassDesc[]::new));
            int accessFlags = (method.isVarArgs()) ? ACC_VARARGS | ACC_PUBLIC | ACC_FINAL
                                                   : ACC_PUBLIC | ACC_FINAL;
            var catchList = computeUniqueCatchList(exceptionTypes);
            clb.withMethod(method.getName(), desc, accessFlags, mb ->
                  mb.with(ExceptionsAttribute.of(toClassEntries(cp, List.of(exceptionTypes))))
                    .withCode(cob -> {
                        cob.aload(cob.receiverSlot())
                           .getfield(FRE_Proxy_h)
                           .aload(cob.receiverSlot())
                           .getstatic(cp.fieldRefEntry(className, cp.nameAndTypeEntry(methodFieldName, UE_Method)));

                        if (parameterTypes.length > 0) {
                            // Create an array and fill with the parameters converting primitives to wrappers
                            cob.loadConstant(parameterTypes.length)
                               .anewarray(CE_Object);
                            for (int i = 0; i < parameterTypes.length; i++) {
                                cob.dup()
                                   .loadConstant(i);
                                codeWrapArgument(cob, parameterTypes[i], cob.parameterSlot(i));
                                cob.aastore();
                            }
                        } else {
                            cob.aconst_null();
                        }

                        cob.invokeinterface(IMRE_InvocationHandler_invoke);

                        if (returnType == void.class) {
                            cob.pop()
                               .return_();
                        } else {
                            codeUnwrapReturnValue(cob, returnType);
                        }
                        if (!catchList.isEmpty()) {
                            var c1 = cob.newBoundLabel();
                            for (var exc : catchList) {
                                cob.exceptionCatch(cob.startLabel(), c1, c1, toClassDesc(exc));
                            }
                            cob.athrow();   // just rethrow the exception
                            var c2 = cob.newBoundLabel();
                            cob.exceptionCatchAll(cob.startLabel(), c1, c2)
                               .new_(CE_UndeclaredThrowableException)
                               .dup_x1()
                               .swap()
                               .invokespecial(MRE_UndeclaredThrowableException_init)
                               .athrow()
                               .with(StackMapTableAttribute.of(List.of(
                                    StackMapFrameInfo.of(c1, List.of(), THROWABLE_STACK),
                                    StackMapFrameInfo.of(c2, List.of(), THROWABLE_STACK))));
                        }
                    }));
        }

        /**
         * Generate code for wrapping an argument of the given type
         * whose value can be found at the specified local variable
         * index, in order for it to be passed (as an Object) to the
         * invocation handler's "invoke" method.
         */
        private void codeWrapArgument(CodeBuilder cob, Class<?> type, int slot) {
            if (type.isPrimitive()) {
                cob.loadLocal(TypeKind.from(type).asLoadable(), slot);
                PrimitiveTypeInfo prim = PrimitiveTypeInfo.get(type);
                cob.invokestatic(prim.wrapperMethodRef);
            } else {
                cob.aload(slot);
            }
        }

        /**
         * Generate code for unwrapping a return value of the given
         * type from the invocation handler's "invoke" method (as type
         * Object) to its correct type.
         */
        private void codeUnwrapReturnValue(CodeBuilder cob, Class<?> type) {
            if (type.isPrimitive()) {
                PrimitiveTypeInfo prim = PrimitiveTypeInfo.get(type);

                cob.checkcast(prim.wrapperClass)
                   .invokevirtual(prim.unwrapMethodRef)
                   .return_(TypeKind.from(type).asLoadable());
            } else {
                cob.checkcast(toClassDesc(type))
                   .areturn();
            }
        }

        /**
         * Generate code for initializing the static field that stores
         * the Method object for this proxy method. A class loader is
         * anticipated at local variable index 0.
         */
        private void codeFieldInitialization(CodeBuilder cob, ClassEntry className) {
            var cp = cob.constantPool();
            codeClassForName(cob, fromClass);

            cob.ldc(method.getName())
               .loadConstant(parameterTypes.length)
               .anewarray(CE_Class);

            // Construct an array with the parameter types mapping primitives to Wrapper types
            for (int i = 0; i < parameterTypes.length; i++) {
                cob.dup()
                   .loadConstant(i);
                if (parameterTypes[i].isPrimitive()) {
                    PrimitiveTypeInfo prim = PrimitiveTypeInfo.get(parameterTypes[i]);
                    cob.getstatic(prim.typeFieldRef);
                } else {
                    codeClassForName(cob, parameterTypes[i]);
                }
                cob.aastore();
            }
            // lookup the method
            cob.invokevirtual(MRE_Class_getMethod)
               .putstatic(cp.fieldRefEntry(className, cp.nameAndTypeEntry(methodFieldName, UE_Method)));
        }

        /*
         * =============== Code Generation Utility Methods ===============
         */

        /**
         * Generate code to invoke the Class.forName with the name of the given
         * class to get its Class object at runtime.  The code is written to
         * the supplied stream.  Note that the code generated by this method
         * may cause the checked ClassNotFoundException to be thrown. A class
         * loader is anticipated at local variable index 0.
         */
        private void codeClassForName(CodeBuilder cob, Class<?> cl) {
            cob.ldc(cl.getName())
               .iconst_0() // false
               .aload(0)// classLoader
               .invokestatic(MRE_Class_forName);
        }

        @Override
        public String toString() {
            return method.toShortString();
        }
    }

    private static final ConstantPoolBuilder CP = ConstantPoolBuilder.of();
    /**
     * A PrimitiveTypeInfo object contains bytecode-related information about
     * a primitive type in its instance fields. The struct for a particular
     * primitive type can be obtained using the static "get" method.
     */
    private enum PrimitiveTypeInfo {
        BYTE(byte.class, CD_byte, CD_Byte),
        CHAR(char.class, CD_char, CD_Character),
        DOUBLE(double.class, CD_double, CD_Double),
        FLOAT(float.class, CD_float, CD_Float),
        INT(int.class, CD_int, CD_Integer),
        LONG(long.class, CD_long, CD_Long),
        SHORT(short.class, CD_short, CD_Short),
        BOOLEAN(boolean.class, CD_boolean, CD_Boolean);

        /**
         * CP entry of corresponding wrapper class
         */
        private final ClassEntry wrapperClass;
        /**
         * CP entry for wrapper class "valueOf" factory method
         */
        private final MethodRefEntry wrapperMethodRef;
        /**
         * CP entry of wrapper class method for retrieving primitive value
         */
        private final MethodRefEntry unwrapMethodRef;
        /**
         * CP entry of wrapper class TYPE field
         */
        private final FieldRefEntry typeFieldRef;

        PrimitiveTypeInfo(Class<?> primitiveClass, ClassDesc baseType, ClassDesc wrapperClass) {
            assert baseType.isPrimitive();
            this.wrapperClass = CP.classEntry(wrapperClass);
            this.wrapperMethodRef = CP.methodRefEntry(wrapperClass, "valueOf", MethodTypeDesc.of(wrapperClass, baseType));
            this.unwrapMethodRef = CP.methodRefEntry(wrapperClass, primitiveClass.getName() + "Value", MethodTypeDesc.of(baseType));
            this.typeFieldRef = CP.fieldRefEntry(wrapperClass, "TYPE", CD_Class);
        }

        public static PrimitiveTypeInfo get(Class<?> cl) {
            // Uses if chain for speed: 8284880
            if (cl == int.class)     return INT;
            if (cl == long.class)    return LONG;
            if (cl == boolean.class) return BOOLEAN;
            if (cl == short.class)   return SHORT;
            if (cl == byte.class)    return BYTE;
            if (cl == char.class)    return CHAR;
            if (cl == float.class)   return FLOAT;
            if (cl == double.class)  return DOUBLE;
            throw new AssertionError(cl);
        }
    }
}
