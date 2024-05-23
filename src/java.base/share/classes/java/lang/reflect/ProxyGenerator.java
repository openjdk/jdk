/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

import sun.security.action.GetBooleanAction;

import java.io.IOException;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.ExceptionsAttribute;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.classfile.attribute.StackMapTableAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantDynamicEntry;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.constant.ClassDesc;
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

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

/**
 * ProxyGenerator contains the code to generate a dynamic proxy class
 * for the java.lang.reflect.Proxy API.
 * <p>
 * The external interface to ProxyGenerator is the static
 * "generateProxyClass" method.
 */
final class ProxyGenerator {

    private static final ClassDesc
            CD_IllegalAccessException = ClassDesc.ofInternalName("java/lang/IllegalAccessException"),
            CD_InvocationHandler = ClassDesc.ofInternalName("java/lang/reflect/InvocationHandler"),
            CD_Method = ClassDesc.ofInternalName("java/lang/reflect/Method"),
            CD_Proxy = ClassDesc.ofInternalName("java/lang/reflect/Proxy"),
            CD_UndeclaredThrowableException = ClassDesc.ofInternalName("java/lang/reflect/UndeclaredThrowableException");

    private static final MethodTypeDesc
            MTD_boolean = MethodTypeDesc.of(CD_boolean),
            MTD_void_InvocationHandler = MethodTypeDesc.of(CD_void, CD_InvocationHandler),
            MTD_void_String = MethodTypeDesc.of(CD_void, CD_String),
            MTD_void_Throwable = MethodTypeDesc.of(CD_void, CD_Throwable),
            MTD_Class = MethodTypeDesc.of(CD_Class),
            MTD_MethodHandles$Lookup = MethodTypeDesc.of(CD_MethodHandles_Lookup),
            MTD_MethodHandles$Lookup_MethodHandles$Lookup = MethodTypeDesc.of(CD_MethodHandles_Lookup, CD_MethodHandles_Lookup),
            MTD_Object_Object_Method_ObjectArray = MethodTypeDesc.of(CD_Object, CD_Object, CD_Method, CD_Object.arrayType()),
            MTD_Object_int = MethodTypeDesc.of(CD_Object, CD_int),
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

    private static final ClassEntry CE_Method;
    private static final ClassEntry CE_Object;
    private static final ClassEntry CE_Throwable;
    private static final ClassEntry CE_UndeclaredThrowableException;

    private static final FieldRefEntry FRE_Proxy_h;

    private static final InterfaceMethodRefEntry IMRE_InvocationHandler_invoke;
    private static final InterfaceMethodRefEntry IMRE_List_get;

    private static final MethodRefEntry MRE_UndeclaredThrowableException_init;

    private static final ConstantDynamicEntry CDE_METHOD_LIST;

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
        var ei = new int[9];
        TEMPLATE = cc.parse(cc.build(CD_Proxy, clb -> {
            clb.withSuperclass(CD_Proxy);
            generateConstructor(clb);
            generateLookupAccessor(clb);
            var cp = clb.constantPool();

            int i = 0;
            ei[i++] = cp.classEntry(CD_Method).index();
            ei[i++] = cp.classEntry(CD_Object).index();
            ei[i++] = cp.classEntry(CD_Throwable).index();
            ei[i++] = cp.classEntry(CD_UndeclaredThrowableException).index();

            ei[i++] = cp.fieldRefEntry(CD_Proxy, handlerFieldName, CD_InvocationHandler).index();

            ei[i++] = cp.interfaceMethodRefEntry(CD_InvocationHandler, "invoke", MTD_Object_Object_Method_ObjectArray).index();
            ei[i++] = cp.interfaceMethodRefEntry(CD_List, "get", MTD_Object_int).index();

            ei[i++] = cp.methodRefEntry(CD_UndeclaredThrowableException, INIT_NAME, MTD_void_Throwable).index();

            ei[i++] = cp.constantDynamicEntry(
                    cp.bsmEntry(BSM_CLASS_DATA, List.of()),
                    cp.nameAndTypeEntry(DEFAULT_NAME, CD_List)
            ).index();
        }));

        int i = 0;
        CE_Method = entryByIndex(ei[i++]);
        CE_Object = entryByIndex(ei[i++]);
        CE_Throwable = entryByIndex(ei[i++]);
        CE_UndeclaredThrowableException = entryByIndex(ei[i++]);

        FRE_Proxy_h = entryByIndex(ei[i++]);

        IMRE_InvocationHandler_invoke = entryByIndex(ei[i++]);
        IMRE_List_get = entryByIndex(ei[i++]);

        MRE_UndeclaredThrowableException_init = entryByIndex(ei[i++]);

        CDE_METHOD_LIST = entryByIndex(ei[i++]);

        try {
            hashCodeMethod = new ProxyMethod(Object.class.getMethod("hashCode"));
            equalsMethod = new ProxyMethod(Object.class.getMethod("equals", Object.class));
            toStringMethod = new ProxyMethod(Object.class.getMethod("toString"));
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
    private final ClassEntry classEntry;

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
     * Construct a ProxyGenerator to generate a proxy class with the
     * specified name and for the given interfaces.
     * <p>
     * A ProxyGenerator object contains the state for the ongoing
     * generation of a particular proxy class.
     */
    private ProxyGenerator(ClassLoader loader, String className, List<Class<?>> interfaces,
                           int accessFlags) {
        this.classfileContext = ClassFile.of(
                StackMapsOption.DROP_STACK_MAPS,
                ClassHierarchyResolverOption.of(
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
    static GeneratedClass generateProxyClass(ClassLoader loader,
                                     final String name,
                                     List<Class<?>> interfaces,
                                     int accessFlags) {
        Objects.requireNonNull(interfaces);
        ProxyGenerator gen = new ProxyGenerator(loader, name, interfaces, accessFlags);
        var classFile = gen.generateClassFile();

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
                                Files.write(path, classFile.bytecode);
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
            ProxyMethod pm = methods.getFirst();
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

    record GeneratedClass(byte[] bytecode, List<Method> classData) {}

    /**
     * Generate a class file for the proxy class.  This method drives the
     * class file generation process.
     */
    private GeneratedClass generateClassFile() {
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
                    addProxyMethod(m, cp);
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

        List<Method> methods = new ArrayList<>();
        var bytes = classfileContext.build(classEntry, cp, clb -> {
            TEMPLATE.forEach(clb);
            clb.withFlags(accessFlags);
            clb.withInterfaces(toClassEntries(cp, interfaces));

            methods.clear();
            for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
                for (ProxyMethod pm : sigmethods) {
                    // Generate code for proxy method
                    pm.generateMethod(clb, methods.size());
                    methods.add(pm.method);
                }
            }
        });
        return new GeneratedClass(bytes, List.copyOf(methods));
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
    private void addProxyMethod(Method m, ConstantPoolBuilder cp) {
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
                exceptionTypes));
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
        private final Class<?>[] parameterTypes;
        private final Class<?> returnType;
        private Class<?>[] exceptionTypes;

        private ProxyMethod(Method method, String sig, Class<?>[] parameterTypes,
                            Class<?> returnType, Class<?>[] exceptionTypes) {
            this.method = method;
            this.shortSignature = sig;
            this.parameterTypes = parameterTypes;
            this.returnType = returnType;
            this.exceptionTypes = exceptionTypes;
        }

        /**
         * Create a new specific ProxyMethod with a specific field name
         *
         * @param method          The method for which to create a proxy
         */
        private ProxyMethod(Method method) {
            this(method, method.toShortSignature(),
                 method.getSharedParameterTypes(), method.getReturnType(),
                 method.getSharedExceptionTypes());
        }

        /**
         * Generate this method, including the code and exception table entry.
         */
        private void generateMethod(ClassBuilder clb, int index) {
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
                           .ldc(CDE_METHOD_LIST)
                           .loadConstant(index)
                           .invokeinterface(IMRE_List_get)
                           .checkcast(CE_Method);

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

        PrimitiveTypeInfo(Class<?> primitiveClass, ClassDesc baseType, ClassDesc wrapperClass) {
            assert baseType.isPrimitive();
            this.wrapperClass = CP.classEntry(wrapperClass);
            this.wrapperMethodRef = CP.methodRefEntry(wrapperClass, "valueOf", MethodTypeDesc.of(wrapperClass, baseType));
            this.unwrapMethodRef = CP.methodRefEntry(wrapperClass, primitiveClass.getName() + "Value", MethodTypeDesc.of(baseType));
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
