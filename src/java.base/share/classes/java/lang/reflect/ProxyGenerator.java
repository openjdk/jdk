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

import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.attribute.ExceptionsAttribute;
import java.lang.classfile.constantpool.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import jdk.internal.constant.ConstantUtils;
import jdk.internal.constant.MethodTypeDescImpl;
import jdk.internal.constant.ReferenceClassDescImpl;
import sun.security.action.GetBooleanAction;

import static java.lang.classfile.ClassFile.*;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.classfile.attribute.StackMapTableAttribute;
import java.lang.constant.ConstantDescs;
import static java.lang.constant.ConstantDescs.*;
import static jdk.internal.constant.ConstantUtils.*;

import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;

/**
 * ProxyGenerator contains the code to generate a dynamic proxy class
 * for the java.lang.reflect.Proxy API.
 * <p>
 * The external interface to ProxyGenerator is the static
 * "generateProxyClass" method.
 */
final class ProxyGenerator {

    private static final ClassFile CF_CONTEXT =
            ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS);

    private static final ClassDesc
            CD_Class_array = ReferenceClassDescImpl.ofValidated("[Ljava/lang/Class;"),
            CD_IllegalAccessException = ReferenceClassDescImpl.ofValidated("Ljava/lang/IllegalAccessException;"),
            CD_InvocationHandler = ReferenceClassDescImpl.ofValidated("Ljava/lang/reflect/InvocationHandler;"),
            CD_Method = ReferenceClassDescImpl.ofValidated("Ljava/lang/reflect/Method;"),
            CD_NoSuchMethodError = ReferenceClassDescImpl.ofValidated("Ljava/lang/NoSuchMethodError;"),
            CD_NoSuchMethodException = ReferenceClassDescImpl.ofValidated("Ljava/lang/NoSuchMethodException;"),
            CD_Object_array = ReferenceClassDescImpl.ofValidated("[Ljava/lang/Object;"),
            CD_Proxy = ReferenceClassDescImpl.ofValidated("Ljava/lang/reflect/Proxy;"),
            CD_UndeclaredThrowableException = ReferenceClassDescImpl.ofValidated("Ljava/lang/reflect/UndeclaredThrowableException;");

    private static final MethodTypeDesc
            MTD_boolean = MethodTypeDescImpl.ofValidated(CD_boolean),
            MTD_void_InvocationHandler = MethodTypeDescImpl.ofValidated(CD_void, CD_InvocationHandler),
            MTD_void_String = MethodTypeDescImpl.ofValidated(CD_void, CD_String),
            MTD_void_Throwable = MethodTypeDescImpl.ofValidated(CD_void, CD_Throwable),
            MTD_Class = MethodTypeDescImpl.ofValidated(CD_Class),
            MTD_Class_array = MethodTypeDescImpl.ofValidated(CD_Class_array),
            MTD_Method_String_Class_array = MethodTypeDescImpl.ofValidated(CD_Method, ConstantDescs.CD_String, CD_Class_array),
            MTD_MethodHandles$Lookup = MethodTypeDescImpl.ofValidated(CD_MethodHandles_Lookup),
            MTD_MethodHandles$Lookup_MethodHandles$Lookup = MethodTypeDescImpl.ofValidated(CD_MethodHandles_Lookup, CD_MethodHandles_Lookup),
            MTD_Object_Object_Method_ObjectArray = MethodTypeDescImpl.ofValidated(CD_Object, CD_Object, CD_Method, CD_Object_array),
            MTD_String = MethodTypeDescImpl.ofValidated(CD_String);

    private static final String NAME_LOOKUP_ACCESSOR = "proxyClassLookup";

    private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];

    /**
     * name of field for storing a proxy instance's invocation handler
     */
    private static final String NAME_HANDLER_FIELD = "h";

    /**
     * debugging flag for saving generated class files
     */
    @SuppressWarnings("removal")
    private static final boolean SAVE_GENERATED_FILES =
            java.security.AccessController.doPrivileged(
                    new GetBooleanAction(
                            "jdk.proxy.ProxyGenerator.saveGeneratedFiles"));

    /* Preloaded ProxyMethod objects for methods in java.lang.Object */
    private static final ProxyMethod HASH_CODE_METHOD;
    private static final ProxyMethod EQUALS_METHOD;
    private static final ProxyMethod TO_STRING_METHOD;

    static {
        try {
            HASH_CODE_METHOD = new ProxyMethod(Object.class.getMethod("hashCode"));
            EQUALS_METHOD = new ProxyMethod(Object.class.getMethod("equals", Object.class));
            TO_STRING_METHOD = new ProxyMethod(Object.class.getMethod("toString"));
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(e.getMessage());
        }
    }

    private final ConstantPoolBuilder cp;
    private final List<StackMapFrameInfo.VerificationTypeInfo> throwableStack;
    private final NameAndTypeEntry exInit;
    private final ClassEntry object, proxy, ute;
    private final FieldRefEntry handlerField;
    private final InterfaceMethodRefEntry invoke;
    private final MethodRefEntry uteInit;
    private final DirectMethodHandleDesc bsm;


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
    private ProxyGenerator(String className, List<Class<?>> interfaces,
                           int accessFlags) {
        this.cp = ConstantPoolBuilder.of();
        this.classEntry = cp.classEntry(ConstantUtils.binaryNameToDesc(className));
        this.interfaces = interfaces;
        this.accessFlags = accessFlags;
        this.throwableStack = List.of(StackMapFrameInfo.ObjectVerificationTypeInfo.of(cp.classEntry(CD_Throwable)));
        this.exInit = cp.nameAndTypeEntry(INIT_NAME, MTD_void_String);
        this.object = cp.classEntry(CD_Object);
        this.proxy = cp.classEntry(CD_Proxy);
        this.handlerField = cp.fieldRefEntry(proxy, cp.nameAndTypeEntry(NAME_HANDLER_FIELD, CD_InvocationHandler));
        this.invoke = cp.interfaceMethodRefEntry(CD_InvocationHandler, "invoke", MTD_Object_Object_Method_ObjectArray);
        this.ute = cp.classEntry(CD_UndeclaredThrowableException);
        this.uteInit = cp.methodRefEntry(ute, cp.nameAndTypeEntry(INIT_NAME, MTD_void_Throwable));
        this.bsm = ConstantDescs.ofConstantBootstrap(classEntry.asSymbol(), "$getMethod", CD_Method, CD_Class, CD_String, CD_MethodType);
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
        ProxyGenerator gen = new ProxyGenerator(name, interfaces, accessFlags);
        final byte[] classFile = gen.generateClassFile();

        if (SAVE_GENERATED_FILES) {
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
    private static List<ClassEntry> toClassEntries(ConstantPoolBuilder cp, List<Class<?>> types) {
        var ces = new ArrayList<ClassEntry>(types.size());
        for (var t : types)
            ces.add(cp.classEntry(ConstantUtils.binaryNameToDesc(t.getName())));
        return ces;
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
        addProxyMethod(HASH_CODE_METHOD);
        addProxyMethod(EQUALS_METHOD);
        addProxyMethod(TO_STRING_METHOD);

        /*
         * Accumulate all of the methods from the proxy interfaces.
         */
        for (Class<?> intf : interfaces) {
            for (Method m : intf.getMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) {
                    addProxyMethod(m, intf);
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

        return CF_CONTEXT.build(classEntry, cp, clb -> {
            clb.withSuperclass(proxy);
            clb.withFlags(accessFlags);
            clb.withInterfaces(toClassEntries(cp, interfaces));
            generateConstructor(clb);

            for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
                for (ProxyMethod pm : sigmethods) {
                    // Generate code for proxy method
                    pm.generateMethod(this, clb);
                }
            }

            generateBootstrapMethod(clb);
            generateLookupAccessor(clb);
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
    private void addProxyMethod(Method m, Class<?> fromClass) {
        Class<?> returnType = m.getReturnType();
        Class<?>[] exceptionTypes = m.getSharedExceptionTypes();

        String sig = m.toShortSignature();
        List<ProxyMethod> sigmethods = proxyMethods.computeIfAbsent(sig,
                _ -> new ArrayList<>(3));
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
                exceptionTypes, fromClass));
    }

    /**
     * Add an existing ProxyMethod (hashcode, equals, toString).
     *
     * @param pm an existing ProxyMethod
     */
    private void addProxyMethod(ProxyMethod pm) {
        String sig = pm.shortSignature;
        List<ProxyMethod> sigmethods = proxyMethods.computeIfAbsent(sig,
                _ -> new ArrayList<>(3));
        sigmethods.add(pm);
    }

    /**
     * Generate the constructor method for the proxy class.
     */
    private void generateConstructor(ClassBuilder clb) {
        clb.withMethodBody(INIT_NAME, MTD_void_InvocationHandler, ACC_PUBLIC, cob -> cob
               .aload(0)
               .aload(1)
               .invokespecial(cp.methodRefEntry(proxy, cp.nameAndTypeEntry(INIT_NAME, MTD_void_InvocationHandler)))
               .return_());
    }

    /**
     * Generate CONDY bootstrap method for the proxy class to retrieve {@link Method} instances.
     */
    private void generateBootstrapMethod(ClassBuilder clb) {
        clb.withMethodBody(bsm.methodName(), bsm.invocationType(), ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC, cob -> {
            cob.aload(3) //interface Class
               .aload(4) //interface method name String
               .aload(5) //interface MethodType
               .invokevirtual(CD_MethodType, "parameterArray", MTD_Class_array)
               .invokevirtual(ConstantDescs.CD_Class, "getMethod", MTD_Method_String_Class_array)
               .areturn();
            Label failLabel = cob.newBoundLabel();
            ClassEntry nsme = cp.classEntry(CD_NoSuchMethodError);
            cob.exceptionCatch(cob.startLabel(), failLabel, failLabel, CD_NoSuchMethodException)
               .new_(nsme)
               .dup_x1()
               .swap()
               .invokevirtual(cp.methodRefEntry(CD_Throwable, "getMessage", MTD_String))
               .invokespecial(cp.methodRefEntry(nsme, exInit))
               .athrow()
               .with(StackMapTableAttribute.of(List.of(
                       StackMapFrameInfo.of(failLabel, List.of(), throwableStack))));
        });
    }

    /**
     * Generate the static lookup accessor method that returns the Lookup
     * on this proxy class if the caller's lookup class is java.lang.reflect.Proxy;
     * otherwise, IllegalAccessException is thrown
     */
    private void generateLookupAccessor(ClassBuilder clb) {
        clb.withMethod(NAME_LOOKUP_ACCESSOR,
                MTD_MethodHandles$Lookup_MethodHandles$Lookup,
                ACC_PRIVATE | ACC_STATIC,
                mb -> mb.with(ExceptionsAttribute.of(List.of(mb.constantPool().classEntry(CD_IllegalAccessException))))
                        .withCode(cob -> {
                            Label failLabel = cob.newLabel();
                            ClassEntry mhl = cp.classEntry(CD_MethodHandles_Lookup);
                            ClassEntry iae = cp.classEntry(CD_IllegalAccessException);
                            cob.aload(cob.parameterSlot(0))
                               .invokevirtual(cp.methodRefEntry(mhl, cp.nameAndTypeEntry("lookupClass", MTD_Class)))
                               .ldc(proxy)
                               .if_acmpne(failLabel)
                               .aload(cob.parameterSlot(0))
                               .invokevirtual(cp.methodRefEntry(mhl, cp.nameAndTypeEntry("hasFullPrivilegeAccess", MTD_boolean)))
                               .ifeq(failLabel)
                               .invokestatic(CD_MethodHandles, "lookup", MTD_MethodHandles$Lookup)
                               .areturn()
                               .labelBinding(failLabel)
                               .new_(iae)
                               .dup()
                               .aload(cob.parameterSlot(0))
                               .invokevirtual(cp.methodRefEntry(mhl, cp.nameAndTypeEntry("toString", MTD_String)))
                               .invokespecial(cp.methodRefEntry(iae, exInit))
                               .athrow()
                               .with(StackMapTableAttribute.of(List.of(
                                       StackMapFrameInfo.of(failLabel,
                                               List.of(StackMapFrameInfo.ObjectVerificationTypeInfo.of(mhl)),
                                               List.of()))));
                        }));
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
        private Class<?>[] exceptionTypes;

        private ProxyMethod(Method method, String sig, Class<?>[] parameterTypes,
                            Class<?> returnType, Class<?>[] exceptionTypes,
                            Class<?> fromClass) {
            this.method = method;
            this.shortSignature = sig;
            this.parameterTypes = parameterTypes;
            this.returnType = returnType;
            this.exceptionTypes = exceptionTypes;
            this.fromClass = fromClass;
        }

        /**
         * Create a new specific ProxyMethod with a specific field name
         *
         * @param method          The method for which to create a proxy
         */
        private ProxyMethod(Method method) {
            this(method, method.toShortSignature(),
                 method.getSharedParameterTypes(), method.getReturnType(),
                 method.getSharedExceptionTypes(), method.getDeclaringClass());
        }

        /**
         * Generate this method, including the code and exception table entry.
         */
        private void generateMethod(ProxyGenerator pg, ClassBuilder clb) {
            var cp = pg.cp;
            var desc = methodTypeDesc(returnType, parameterTypes);
            int accessFlags = (method.isVarArgs()) ? ACC_VARARGS | ACC_PUBLIC | ACC_FINAL
                                                   : ACC_PUBLIC | ACC_FINAL;
            var catchList = computeUniqueCatchList(exceptionTypes);
            clb.withMethod(method.getName(), desc, accessFlags, mb ->
                  mb.with(ExceptionsAttribute.of(toClassEntries(cp, List.of(exceptionTypes))))
                    .withCode(cob -> {
                        cob.aload(0)
                           .getfield(pg.handlerField)
                           .aload(0)
                           .ldc(DynamicConstantDesc.of(pg.bsm,
                                referenceClassDesc(fromClass),
                                method.getName(),
                                desc));
                        if (parameterTypes.length > 0) {
                            // Create an array and fill with the parameters converting primitives to wrappers
                            cob.loadConstant(parameterTypes.length)
                               .anewarray(pg.object);
                            for (int i = 0; i < parameterTypes.length; i++) {
                                cob.dup()
                                   .loadConstant(i);
                                codeWrapArgument(cob, parameterTypes[i], cob.parameterSlot(i));
                                cob.aastore();
                            }
                        } else {
                            cob.aconst_null();
                        }

                        cob.invokeinterface(pg.invoke);

                        if (returnType == void.class) {
                            cob.pop()
                               .return_();
                        } else {
                            codeUnwrapReturnValue(cob, returnType);
                        }
                        if (!catchList.isEmpty()) {
                            var c1 = cob.newBoundLabel();
                            for (var exc : catchList) {
                                cob.exceptionCatch(cob.startLabel(), c1, c1, referenceClassDesc(exc));
                            }
                            cob.athrow();   // just rethrow the exception
                            var c2 = cob.newBoundLabel();
                            cob.exceptionCatchAll(cob.startLabel(), c1, c2)
                               .new_(pg.ute)
                               .dup_x1()
                               .swap()
                               .invokespecial(pg.uteInit)
                               .athrow()
                               .with(StackMapTableAttribute.of(List.of(
                                       StackMapFrameInfo.of(c1, List.of(), pg.throwableStack),
                                       StackMapFrameInfo.of(c2, List.of(), pg.throwableStack))));
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
                cob.invokestatic(prim.wrapperMethodRef(cob.constantPool()));
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
                   .invokevirtual(prim.unwrapMethodRef(cob.constantPool()))
                   .return_(TypeKind.from(type).asLoadable());
            } else {
                cob.checkcast(referenceClassDesc(type))
                   .areturn();
            }
        }

        @Override
        public String toString() {
            return method.toShortString();
        }
    }

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
         * wrapper class
         */
        private final ClassDesc wrapperClass;
        /**
         * wrapper factory method type
         */
        private final MethodTypeDesc wrapperMethodType;
        /**
         * wrapper class method name for retrieving primitive value
         */
        private final String unwrapMethodName;
        /**
         * wrapper class method type for retrieving primitive value
         */
        private final MethodTypeDesc unwrapMethodType;

        PrimitiveTypeInfo(Class<?> primitiveClass, ClassDesc baseType, ClassDesc wrapperClass) {
            assert baseType.isPrimitive();
            this.wrapperClass = wrapperClass;
            this.wrapperMethodType = MethodTypeDescImpl.ofValidated(wrapperClass, baseType);
            this.unwrapMethodName = primitiveClass.getName() + "Value";
            this.unwrapMethodType = MethodTypeDescImpl.ofValidated(baseType);
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

        public MethodRefEntry wrapperMethodRef(ConstantPoolBuilder cp) {
            return cp.methodRefEntry(wrapperClass, "valueOf", wrapperMethodType);
        }

        public MethodRefEntry unwrapMethodRef(ConstantPoolBuilder cp) {
            return cp.methodRefEntry(wrapperClass, unwrapMethodName, unwrapMethodType);
        }
    }
}
