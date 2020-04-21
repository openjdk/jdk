/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

import sun.invoke.util.Wrapper;

import static java.lang.invoke.MethodHandleInfo.*;
import static sun.invoke.util.Wrapper.forPrimitiveType;
import static sun.invoke.util.Wrapper.forWrapperType;
import static sun.invoke.util.Wrapper.isWrapperType;

/**
 * Abstract implementation of a lambda metafactory which provides parameter
 * unrolling and input validation.
 *
 * @see LambdaMetafactory
 */
/* package */ abstract class AbstractValidatingLambdaMetafactory {

    /*
     * For context, the comments for the following fields are marked in quotes
     * with their values, given this program:
     * interface II<T> {  Object foo(T x); }
     * interface JJ<R extends Number> extends II<R> { }
     * class CC {  String impl(int i) { return "impl:"+i; }}
     * class X {
     *     public static void main(String[] args) {
     *         JJ<Integer> iii = (new CC())::impl;
     *         System.out.printf(">>> %s\n", iii.foo(44));
     * }}
     */
    final MethodHandles.Lookup caller;        // The caller's lookup context
    final Class<?> targetClass;               // The class calling the meta-factory via invokedynamic "class X"
    final MethodType invokedType;             // The type of the invoked method "(CC)II"
    final Class<?> samBase;                   // The type of the returned instance "interface JJ"
    final String samMethodName;               // Name of the SAM method "foo"
    final MethodType samMethodType;           // Type of the SAM method "(Object)Object"
    final MethodHandle implMethod;            // Raw method handle for the implementation method
    final MethodType implMethodType;          // Type of the implMethod MethodHandle "(CC,int)String"
    final MethodHandleInfo implInfo;          // Info about the implementation method handle "MethodHandleInfo[5 CC.impl(int)String]"
    final int implKind;                       // Invocation kind for implementation "5"=invokevirtual
    final boolean implIsInstanceMethod;       // Is the implementation an instance method "true"
    final Class<?> implClass;                 // Class for referencing the implementation method "class CC"
    final MethodType instantiatedMethodType;  // Instantiated erased functional interface method type "(Integer)Object"
    final boolean isSerializable;             // Should the returned instance be serializable
    final Class<?>[] markerInterfaces;        // Additional marker interfaces to be implemented
    final MethodType[] additionalBridges;     // Signatures of additional methods to bridge


    /**
     * Meta-factory constructor.
     *
     * @param caller Stacked automatically by VM; represents a lookup context
     *               with the accessibility privileges of the caller.
     * @param invokedType Stacked automatically by VM; the signature of the
     *                    invoked method, which includes the expected static
     *                    type of the returned lambda object, and the static
     *                    types of the captured arguments for the lambda.  In
     *                    the event that the implementation method is an
     *                    instance method, the first argument in the invocation
     *                    signature will correspond to the receiver.
     * @param samMethodName Name of the method in the functional interface to
     *                      which the lambda or method reference is being
     *                      converted, represented as a String.
     * @param samMethodType Type of the method in the functional interface to
     *                      which the lambda or method reference is being
     *                      converted, represented as a MethodType.
     * @param implMethod The implementation method which should be called
     *                   (with suitable adaptation of argument types, return
     *                   types, and adjustment for captured arguments) when
     *                   methods of the resulting functional interface instance
     *                   are invoked.
     * @param instantiatedMethodType The signature of the primary functional
     *                               interface method after type variables are
     *                               substituted with their instantiation from
     *                               the capture site
     * @param isSerializable Should the lambda be made serializable?  If set,
     *                       either the target type or one of the additional SAM
     *                       types must extend {@code Serializable}.
     * @param markerInterfaces Additional interfaces which the lambda object
     *                       should implement.
     * @param additionalBridges Method types for additional signatures to be
     *                          bridged to the implementation method
     * @throws LambdaConversionException If any of the meta-factory protocol
     * invariants are violated
     */
    AbstractValidatingLambdaMetafactory(MethodHandles.Lookup caller,
                                       MethodType invokedType,
                                       String samMethodName,
                                       MethodType samMethodType,
                                       MethodHandle implMethod,
                                       MethodType instantiatedMethodType,
                                       boolean isSerializable,
                                       Class<?>[] markerInterfaces,
                                       MethodType[] additionalBridges)
            throws LambdaConversionException {
        if ((caller.lookupModes() & MethodHandles.Lookup.PRIVATE) == 0) {
            throw new LambdaConversionException(String.format(
                    "Invalid caller: %s",
                    caller.lookupClass().getName()));
        }
        this.caller = caller;
        this.targetClass = caller.lookupClass();
        this.invokedType = invokedType;

        this.samBase = invokedType.returnType();

        this.samMethodName = samMethodName;
        this.samMethodType  = samMethodType;

        this.implMethod = implMethod;
        this.implMethodType = implMethod.type();
        this.implInfo = caller.revealDirect(implMethod);
        switch (implInfo.getReferenceKind()) {
            case REF_invokeVirtual:
            case REF_invokeInterface:
                this.implClass = implMethodType.parameterType(0);
                // reference kind reported by implInfo may not match implMethodType's first param
                // Example: implMethodType is (Cloneable)String, implInfo is for Object.toString
                this.implKind = implClass.isInterface() ? REF_invokeInterface : REF_invokeVirtual;
                this.implIsInstanceMethod = true;
                break;
            case REF_invokeSpecial:
                // JDK-8172817: should use referenced class here, but we don't know what it was
                this.implClass = implInfo.getDeclaringClass();
                this.implIsInstanceMethod = true;

                // Classes compiled prior to dynamic nestmate support invokes a private instance
                // method with REF_invokeSpecial.
                //
                // invokespecial should only be used to invoke private nestmate constructors.
                // The lambda proxy class will be defined as a nestmate of targetClass.
                // If the method to be invoked is an instance method of targetClass, then
                // convert to use invokevirtual or invokeinterface.
                if (targetClass == implClass && !implInfo.getName().equals("<init>")) {
                    this.implKind = implClass.isInterface() ? REF_invokeInterface : REF_invokeVirtual;
                } else {
                    this.implKind = REF_invokeSpecial;
                }
                break;
            case REF_invokeStatic:
            case REF_newInvokeSpecial:
                // JDK-8172817: should use referenced class here for invokestatic, but we don't know what it was
                this.implClass = implInfo.getDeclaringClass();
                this.implKind = implInfo.getReferenceKind();
                this.implIsInstanceMethod = false;
                break;
            default:
                throw new LambdaConversionException(String.format("Unsupported MethodHandle kind: %s", implInfo));
        }

        this.instantiatedMethodType = instantiatedMethodType;
        this.isSerializable = isSerializable;
        this.markerInterfaces = markerInterfaces;
        this.additionalBridges = additionalBridges;

        if (samMethodName.isEmpty() ||
                samMethodName.indexOf('.') >= 0 ||
                samMethodName.indexOf(';') >= 0 ||
                samMethodName.indexOf('[') >= 0 ||
                samMethodName.indexOf('/') >= 0 ||
                samMethodName.indexOf('<') >= 0 ||
                samMethodName.indexOf('>') >= 0) {
            throw new LambdaConversionException(String.format(
                    "Method name '%s' is not legal",
                    samMethodName));
        }

        if (!samBase.isInterface()) {
            throw new LambdaConversionException(String.format(
                    "Functional interface %s is not an interface",
                    samBase.getName()));
        }

        for (Class<?> c : markerInterfaces) {
            if (!c.isInterface()) {
                throw new LambdaConversionException(String.format(
                        "Marker interface %s is not an interface",
                        c.getName()));
            }
        }
    }

    /**
     * Build the CallSite.
     *
     * @return a CallSite, which, when invoked, will return an instance of the
     * functional interface
     * @throws ReflectiveOperationException
     */
    abstract CallSite buildCallSite()
            throws LambdaConversionException;

    /**
     * Check the meta-factory arguments for errors
     * @throws LambdaConversionException if there are improper conversions
     */
    void validateMetafactoryArgs() throws LambdaConversionException {
        // Check arity: captured + SAM == impl
        final int implArity = implMethodType.parameterCount();
        final int capturedArity = invokedType.parameterCount();
        final int samArity = samMethodType.parameterCount();
        final int instantiatedArity = instantiatedMethodType.parameterCount();
        if (implArity != capturedArity + samArity) {
            throw new LambdaConversionException(
                    String.format("Incorrect number of parameters for %s method %s; %d captured parameters, %d functional interface method parameters, %d implementation parameters",
                                  implIsInstanceMethod ? "instance" : "static", implInfo,
                                  capturedArity, samArity, implArity));
        }
        if (instantiatedArity != samArity) {
            throw new LambdaConversionException(
                    String.format("Incorrect number of parameters for %s method %s; %d instantiated parameters, %d functional interface method parameters",
                                  implIsInstanceMethod ? "instance" : "static", implInfo,
                                  instantiatedArity, samArity));
        }
        for (MethodType bridgeMT : additionalBridges) {
            if (bridgeMT.parameterCount() != samArity) {
                throw new LambdaConversionException(
                        String.format("Incorrect number of parameters for bridge signature %s; incompatible with %s",
                                      bridgeMT, samMethodType));
            }
        }

        // If instance: first captured arg (receiver) must be subtype of class where impl method is defined
        final int capturedStart; // index of first non-receiver capture parameter in implMethodType
        final int samStart; // index of first non-receiver sam parameter in implMethodType
        if (implIsInstanceMethod) {
            final Class<?> receiverClass;

            // implementation is an instance method, adjust for receiver in captured variables / SAM arguments
            if (capturedArity == 0) {
                // receiver is function parameter
                capturedStart = 0;
                samStart = 1;
                receiverClass = instantiatedMethodType.parameterType(0);
            } else {
                // receiver is a captured variable
                capturedStart = 1;
                samStart = capturedArity;
                receiverClass = invokedType.parameterType(0);
            }

            // check receiver type
            if (!implClass.isAssignableFrom(receiverClass)) {
                throw new LambdaConversionException(
                        String.format("Invalid receiver type %s; not a subtype of implementation type %s",
                                      receiverClass, implClass));
            }
        } else {
            // no receiver
            capturedStart = 0;
            samStart = capturedArity;
        }

        // Check for exact match on non-receiver captured arguments
        for (int i=capturedStart; i<capturedArity; i++) {
            Class<?> implParamType = implMethodType.parameterType(i);
            Class<?> capturedParamType = invokedType.parameterType(i);
            if (!capturedParamType.equals(implParamType)) {
                throw new LambdaConversionException(
                        String.format("Type mismatch in captured lambda parameter %d: expecting %s, found %s",
                                      i, capturedParamType, implParamType));
            }
        }
        // Check for adaptation match on non-receiver SAM arguments
        for (int i=samStart; i<implArity; i++) {
            Class<?> implParamType = implMethodType.parameterType(i);
            Class<?> instantiatedParamType = instantiatedMethodType.parameterType(i - capturedArity);
            if (!isAdaptableTo(instantiatedParamType, implParamType, true)) {
                throw new LambdaConversionException(
                        String.format("Type mismatch for lambda argument %d: %s is not convertible to %s",
                                      i, instantiatedParamType, implParamType));
            }
        }

        // Adaptation match: return type
        Class<?> expectedType = instantiatedMethodType.returnType();
        Class<?> actualReturnType = implMethodType.returnType();
        if (!isAdaptableToAsReturn(actualReturnType, expectedType)) {
            throw new LambdaConversionException(
                    String.format("Type mismatch for lambda return: %s is not convertible to %s",
                                  actualReturnType, expectedType));
        }

        // Check descriptors of generated methods
        checkDescriptor(samMethodType);
        for (MethodType bridgeMT : additionalBridges) {
            checkDescriptor(bridgeMT);
        }
    }

    /** Validate that the given descriptor's types are compatible with {@code instantiatedMethodType} **/
    private void checkDescriptor(MethodType descriptor) throws LambdaConversionException {
        for (int i = 0; i < instantiatedMethodType.parameterCount(); i++) {
            Class<?> instantiatedParamType = instantiatedMethodType.parameterType(i);
            Class<?> descriptorParamType = descriptor.parameterType(i);
            if (!descriptorParamType.isAssignableFrom(instantiatedParamType)) {
                String msg = String.format("Type mismatch for instantiated parameter %d: %s is not a subtype of %s",
                                           i, instantiatedParamType, descriptorParamType);
                throw new LambdaConversionException(msg);
            }
        }

        Class<?> instantiatedReturnType = instantiatedMethodType.returnType();
        Class<?> descriptorReturnType = descriptor.returnType();
        if (!isAdaptableToAsReturnStrict(instantiatedReturnType, descriptorReturnType)) {
            String msg = String.format("Type mismatch for lambda expected return: %s is not convertible to %s",
                                       instantiatedReturnType, descriptorReturnType);
            throw new LambdaConversionException(msg);
        }
    }

    /**
     * Check type adaptability for parameter types.
     * @param fromType Type to convert from
     * @param toType Type to convert to
     * @param strict If true, do strict checks, else allow that fromType may be parameterized
     * @return True if 'fromType' can be passed to an argument of 'toType'
     */
    private boolean isAdaptableTo(Class<?> fromType, Class<?> toType, boolean strict) {
        if (fromType.equals(toType)) {
            return true;
        }
        if (fromType.isPrimitive()) {
            Wrapper wfrom = forPrimitiveType(fromType);
            if (toType.isPrimitive()) {
                // both are primitive: widening
                Wrapper wto = forPrimitiveType(toType);
                return wto.isConvertibleFrom(wfrom);
            } else {
                // from primitive to reference: boxing
                return toType.isAssignableFrom(wfrom.wrapperType());
            }
        } else {
            if (toType.isPrimitive()) {
                // from reference to primitive: unboxing
                Wrapper wfrom;
                if (isWrapperType(fromType) && (wfrom = forWrapperType(fromType)).primitiveType().isPrimitive()) {
                    // fromType is a primitive wrapper; unbox+widen
                    Wrapper wto = forPrimitiveType(toType);
                    return wto.isConvertibleFrom(wfrom);
                } else {
                    // must be convertible to primitive
                    return !strict;
                }
            } else {
                // both are reference types: fromType should be a superclass of toType.
                return !strict || toType.isAssignableFrom(fromType);
            }
        }
    }

    /**
     * Check type adaptability for return types --
     * special handling of void type) and parameterized fromType
     * @return True if 'fromType' can be converted to 'toType'
     */
    private boolean isAdaptableToAsReturn(Class<?> fromType, Class<?> toType) {
        return toType.equals(void.class)
               || !fromType.equals(void.class) && isAdaptableTo(fromType, toType, false);
    }
    private boolean isAdaptableToAsReturnStrict(Class<?> fromType, Class<?> toType) {
        if (fromType.equals(void.class) || toType.equals(void.class)) return fromType.equals(toType);
        else return isAdaptableTo(fromType, toType, true);
    }


    /*********** Logging support -- for debugging only, uncomment as needed
    static final Executor logPool = Executors.newSingleThreadExecutor();
    protected static void log(final String s) {
        MethodHandleProxyLambdaMetafactory.logPool.execute(new Runnable() {
            @Override
            public void run() {
                System.out.println(s);
            }
        });
    }

    protected static void log(final String s, final Throwable e) {
        MethodHandleProxyLambdaMetafactory.logPool.execute(new Runnable() {
            @Override
            public void run() {
                System.out.println(s);
                e.printStackTrace(System.out);
            }
        });
    }
    ***********************/

}
