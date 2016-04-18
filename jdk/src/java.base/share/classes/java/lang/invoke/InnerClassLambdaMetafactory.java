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

import jdk.internal.org.objectweb.asm.*;
import sun.invoke.util.BytecodeDescriptor;
import jdk.internal.misc.Unsafe;
import sun.security.action.GetPropertyAction;

import java.io.FilePermission;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.PropertyPermission;
import java.util.Set;

import static jdk.internal.org.objectweb.asm.Opcodes.*;

/**
 * Lambda metafactory implementation which dynamically creates an
 * inner-class-like class per lambda callsite.
 *
 * @see LambdaMetafactory
 */
/* package */ final class InnerClassLambdaMetafactory extends AbstractValidatingLambdaMetafactory {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private static final int CLASSFILE_VERSION = 52;
    private static final String METHOD_DESCRIPTOR_VOID = Type.getMethodDescriptor(Type.VOID_TYPE);
    private static final String JAVA_LANG_OBJECT = "java/lang/Object";
    private static final String NAME_CTOR = "<init>";
    private static final String NAME_FACTORY = "get$Lambda";

    //Serialization support
    private static final String NAME_SERIALIZED_LAMBDA = "java/lang/invoke/SerializedLambda";
    private static final String NAME_NOT_SERIALIZABLE_EXCEPTION = "java/io/NotSerializableException";
    private static final String DESCR_METHOD_WRITE_REPLACE = "()Ljava/lang/Object;";
    private static final String DESCR_METHOD_WRITE_OBJECT = "(Ljava/io/ObjectOutputStream;)V";
    private static final String DESCR_METHOD_READ_OBJECT = "(Ljava/io/ObjectInputStream;)V";
    private static final String NAME_METHOD_WRITE_REPLACE = "writeReplace";
    private static final String NAME_METHOD_READ_OBJECT = "readObject";
    private static final String NAME_METHOD_WRITE_OBJECT = "writeObject";

    private static final String DESCR_CLASS = "Ljava/lang/Class;";
    private static final String DESCR_STRING = "Ljava/lang/String;";
    private static final String DESCR_OBJECT = "Ljava/lang/Object;";
    private static final String DESCR_CTOR_SERIALIZED_LAMBDA
            = "(" + DESCR_CLASS + DESCR_STRING + DESCR_STRING + DESCR_STRING + "I"
            + DESCR_STRING + DESCR_STRING + DESCR_STRING + DESCR_STRING + "[" + DESCR_OBJECT + ")V";

    private static final String DESCR_CTOR_NOT_SERIALIZABLE_EXCEPTION = "(Ljava/lang/String;)V";
    private static final String[] SER_HOSTILE_EXCEPTIONS = new String[] {NAME_NOT_SERIALIZABLE_EXCEPTION};


    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    // Used to ensure that each spun class name is unique
    private static final AtomicInteger counter = new AtomicInteger(0);

    // For dumping generated classes to disk, for debugging purposes
    private static final ProxyClassesDumper dumper;

    static {
        final String key = "jdk.internal.lambda.dumpProxyClasses";
        String path = AccessController.doPrivileged(
                new GetPropertyAction(key));
        dumper = (null == path) ? null : ProxyClassesDumper.getInstance(path);
    }

    // See context values in AbstractValidatingLambdaMetafactory
    private final String implMethodClassName;        // Name of type containing implementation "CC"
    private final String implMethodName;             // Name of implementation method "impl"
    private final String implMethodDesc;             // Type descriptor for implementation methods "(I)Ljava/lang/String;"
    private final Class<?> implMethodReturnClass;    // class for implementation method return type "Ljava/lang/String;"
    private final MethodType constructorType;        // Generated class constructor type "(CC)void"
    private final ClassWriter cw;                    // ASM class writer
    private final String[] argNames;                 // Generated names for the constructor arguments
    private final String[] argDescs;                 // Type descriptors for the constructor arguments
    private final String lambdaClassName;            // Generated name for the generated class "X$$Lambda$1"

    /**
     * General meta-factory constructor, supporting both standard cases and
     * allowing for uncommon options such as serialization or bridging.
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
     * @param implMethod The implementation method which should be called (with
     *                   suitable adaptation of argument types, return types,
     *                   and adjustment for captured arguments) when methods of
     *                   the resulting functional interface instance are invoked.
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
    public InnerClassLambdaMetafactory(MethodHandles.Lookup caller,
                                       MethodType invokedType,
                                       String samMethodName,
                                       MethodType samMethodType,
                                       MethodHandle implMethod,
                                       MethodType instantiatedMethodType,
                                       boolean isSerializable,
                                       Class<?>[] markerInterfaces,
                                       MethodType[] additionalBridges)
            throws LambdaConversionException {
        super(caller, invokedType, samMethodName, samMethodType,
              implMethod, instantiatedMethodType,
              isSerializable, markerInterfaces, additionalBridges);
        implMethodClassName = implDefiningClass.getName().replace('.', '/');
        implMethodName = implInfo.getName();
        implMethodDesc = implMethodType.toMethodDescriptorString();
        implMethodReturnClass = (implKind == MethodHandleInfo.REF_newInvokeSpecial)
                ? implDefiningClass
                : implMethodType.returnType();
        constructorType = invokedType.changeReturnType(Void.TYPE);
        lambdaClassName = targetClass.getName().replace('.', '/') + "$$Lambda$" + counter.incrementAndGet();
        cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        int parameterCount = invokedType.parameterCount();
        if (parameterCount > 0) {
            argNames = new String[parameterCount];
            argDescs = new String[parameterCount];
            for (int i = 0; i < parameterCount; i++) {
                argNames[i] = "arg$" + (i + 1);
                argDescs[i] = BytecodeDescriptor.unparse(invokedType.parameterType(i));
            }
        } else {
            argNames = argDescs = EMPTY_STRING_ARRAY;
        }
    }

    /**
     * Build the CallSite. Generate a class file which implements the functional
     * interface, define the class, if there are no parameters create an instance
     * of the class which the CallSite will return, otherwise, generate handles
     * which will call the class' constructor.
     *
     * @return a CallSite, which, when invoked, will return an instance of the
     * functional interface
     * @throws ReflectiveOperationException
     * @throws LambdaConversionException If properly formed functional interface
     * is not found
     */
    @Override
    CallSite buildCallSite() throws LambdaConversionException {
        final Class<?> innerClass = spinInnerClass();
        if (invokedType.parameterCount() == 0) {
            final Constructor<?>[] ctrs = AccessController.doPrivileged(
                    new PrivilegedAction<>() {
                @Override
                public Constructor<?>[] run() {
                    Constructor<?>[] ctrs = innerClass.getDeclaredConstructors();
                    if (ctrs.length == 1) {
                        // The lambda implementing inner class constructor is private, set
                        // it accessible (by us) before creating the constant sole instance
                        ctrs[0].setAccessible(true);
                    }
                    return ctrs;
                }
                    });
            if (ctrs.length != 1) {
                throw new LambdaConversionException("Expected one lambda constructor for "
                        + innerClass.getCanonicalName() + ", got " + ctrs.length);
            }

            try {
                Object inst = ctrs[0].newInstance();
                return new ConstantCallSite(MethodHandles.constant(samBase, inst));
            }
            catch (ReflectiveOperationException e) {
                throw new LambdaConversionException("Exception instantiating lambda object", e);
            }
        } else {
            try {
                UNSAFE.ensureClassInitialized(innerClass);
                return new ConstantCallSite(
                        MethodHandles.Lookup.IMPL_LOOKUP
                             .findStatic(innerClass, NAME_FACTORY, invokedType));
            }
            catch (ReflectiveOperationException e) {
                throw new LambdaConversionException("Exception finding constructor", e);
            }
        }
    }

    /**
     * Generate a class file which implements the functional
     * interface, define and return the class.
     *
     * @implNote The class that is generated does not include signature
     * information for exceptions that may be present on the SAM method.
     * This is to reduce classfile size, and is harmless as checked exceptions
     * are erased anyway, no one will ever compile against this classfile,
     * and we make no guarantees about the reflective properties of lambda
     * objects.
     *
     * @return a Class which implements the functional interface
     * @throws LambdaConversionException If properly formed functional interface
     * is not found
     */
    private Class<?> spinInnerClass() throws LambdaConversionException {
        String[] interfaces;
        String samIntf = samBase.getName().replace('.', '/');
        boolean accidentallySerializable = !isSerializable && Serializable.class.isAssignableFrom(samBase);
        if (markerInterfaces.length == 0) {
            interfaces = new String[]{samIntf};
        } else {
            // Assure no duplicate interfaces (ClassFormatError)
            Set<String> itfs = new LinkedHashSet<>(markerInterfaces.length + 1);
            itfs.add(samIntf);
            for (Class<?> markerInterface : markerInterfaces) {
                itfs.add(markerInterface.getName().replace('.', '/'));
                accidentallySerializable |= !isSerializable && Serializable.class.isAssignableFrom(markerInterface);
            }
            interfaces = itfs.toArray(new String[itfs.size()]);
        }

        cw.visit(CLASSFILE_VERSION, ACC_SUPER + ACC_FINAL + ACC_SYNTHETIC,
                 lambdaClassName, null,
                 JAVA_LANG_OBJECT, interfaces);

        // Generate final fields to be filled in by constructor
        for (int i = 0; i < argDescs.length; i++) {
            FieldVisitor fv = cw.visitField(ACC_PRIVATE + ACC_FINAL,
                                            argNames[i],
                                            argDescs[i],
                                            null, null);
            fv.visitEnd();
        }

        generateConstructor();

        if (invokedType.parameterCount() != 0) {
            generateFactory();
        }

        // Forward the SAM method
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, samMethodName,
                                          samMethodType.toMethodDescriptorString(), null, null);
        mv.visitAnnotation("Ljava/lang/invoke/LambdaForm$Hidden;", true);
        new ForwardingMethodGenerator(mv).generate(samMethodType);

        // Forward the bridges
        if (additionalBridges != null) {
            for (MethodType mt : additionalBridges) {
                mv = cw.visitMethod(ACC_PUBLIC|ACC_BRIDGE, samMethodName,
                                    mt.toMethodDescriptorString(), null, null);
                mv.visitAnnotation("Ljava/lang/invoke/LambdaForm$Hidden;", true);
                new ForwardingMethodGenerator(mv).generate(mt);
            }
        }

        if (isSerializable)
            generateSerializationFriendlyMethods();
        else if (accidentallySerializable)
            generateSerializationHostileMethods();

        cw.visitEnd();

        // Define the generated class in this VM.

        final byte[] classBytes = cw.toByteArray();

        // If requested, dump out to a file for debugging purposes
        if (dumper != null) {
            AccessController.doPrivileged(new PrivilegedAction<>() {
                @Override
                public Void run() {
                    dumper.dumpClass(lambdaClassName, classBytes);
                    return null;
                }
            }, null,
            new FilePermission("<<ALL FILES>>", "read, write"),
            // createDirectories may need it
            new PropertyPermission("user.dir", "read"));
        }

        return UNSAFE.defineAnonymousClass(targetClass, classBytes, null);
    }

    /**
     * Generate the factory method for the class
     */
    private void generateFactory() {
        MethodVisitor m = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, NAME_FACTORY, invokedType.toMethodDescriptorString(), null, null);
        m.visitCode();
        m.visitTypeInsn(NEW, lambdaClassName);
        m.visitInsn(Opcodes.DUP);
        int parameterCount = invokedType.parameterCount();
        for (int typeIndex = 0, varIndex = 0; typeIndex < parameterCount; typeIndex++) {
            Class<?> argType = invokedType.parameterType(typeIndex);
            m.visitVarInsn(getLoadOpcode(argType), varIndex);
            varIndex += getParameterSize(argType);
        }
        m.visitMethodInsn(INVOKESPECIAL, lambdaClassName, NAME_CTOR, constructorType.toMethodDescriptorString(), false);
        m.visitInsn(ARETURN);
        m.visitMaxs(-1, -1);
        m.visitEnd();
    }

    /**
     * Generate the constructor for the class
     */
    private void generateConstructor() {
        // Generate constructor
        MethodVisitor ctor = cw.visitMethod(ACC_PRIVATE, NAME_CTOR,
                                            constructorType.toMethodDescriptorString(), null, null);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, JAVA_LANG_OBJECT, NAME_CTOR,
                             METHOD_DESCRIPTOR_VOID, false);
        int parameterCount = invokedType.parameterCount();
        for (int i = 0, lvIndex = 0; i < parameterCount; i++) {
            ctor.visitVarInsn(ALOAD, 0);
            Class<?> argType = invokedType.parameterType(i);
            ctor.visitVarInsn(getLoadOpcode(argType), lvIndex + 1);
            lvIndex += getParameterSize(argType);
            ctor.visitFieldInsn(PUTFIELD, lambdaClassName, argNames[i], argDescs[i]);
        }
        ctor.visitInsn(RETURN);
        // Maxs computed by ClassWriter.COMPUTE_MAXS, these arguments ignored
        ctor.visitMaxs(-1, -1);
        ctor.visitEnd();
    }

    /**
     * Generate a writeReplace method that supports serialization
     */
    private void generateSerializationFriendlyMethods() {
        TypeConvertingMethodAdapter mv
                = new TypeConvertingMethodAdapter(
                    cw.visitMethod(ACC_PRIVATE + ACC_FINAL,
                    NAME_METHOD_WRITE_REPLACE, DESCR_METHOD_WRITE_REPLACE,
                    null, null));

        mv.visitCode();
        mv.visitTypeInsn(NEW, NAME_SERIALIZED_LAMBDA);
        mv.visitInsn(DUP);
        mv.visitLdcInsn(Type.getType(targetClass));
        mv.visitLdcInsn(invokedType.returnType().getName().replace('.', '/'));
        mv.visitLdcInsn(samMethodName);
        mv.visitLdcInsn(samMethodType.toMethodDescriptorString());
        mv.visitLdcInsn(implInfo.getReferenceKind());
        mv.visitLdcInsn(implInfo.getDeclaringClass().getName().replace('.', '/'));
        mv.visitLdcInsn(implInfo.getName());
        mv.visitLdcInsn(implInfo.getMethodType().toMethodDescriptorString());
        mv.visitLdcInsn(instantiatedMethodType.toMethodDescriptorString());
        mv.iconst(argDescs.length);
        mv.visitTypeInsn(ANEWARRAY, JAVA_LANG_OBJECT);
        for (int i = 0; i < argDescs.length; i++) {
            mv.visitInsn(DUP);
            mv.iconst(i);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, lambdaClassName, argNames[i], argDescs[i]);
            mv.boxIfTypePrimitive(Type.getType(argDescs[i]));
            mv.visitInsn(AASTORE);
        }
        mv.visitMethodInsn(INVOKESPECIAL, NAME_SERIALIZED_LAMBDA, NAME_CTOR,
                DESCR_CTOR_SERIALIZED_LAMBDA, false);
        mv.visitInsn(ARETURN);
        // Maxs computed by ClassWriter.COMPUTE_MAXS, these arguments ignored
        mv.visitMaxs(-1, -1);
        mv.visitEnd();
    }

    /**
     * Generate a readObject/writeObject method that is hostile to serialization
     */
    private void generateSerializationHostileMethods() {
        MethodVisitor mv = cw.visitMethod(ACC_PRIVATE + ACC_FINAL,
                                          NAME_METHOD_WRITE_OBJECT, DESCR_METHOD_WRITE_OBJECT,
                                          null, SER_HOSTILE_EXCEPTIONS);
        mv.visitCode();
        mv.visitTypeInsn(NEW, NAME_NOT_SERIALIZABLE_EXCEPTION);
        mv.visitInsn(DUP);
        mv.visitLdcInsn("Non-serializable lambda");
        mv.visitMethodInsn(INVOKESPECIAL, NAME_NOT_SERIALIZABLE_EXCEPTION, NAME_CTOR,
                           DESCR_CTOR_NOT_SERIALIZABLE_EXCEPTION, false);
        mv.visitInsn(ATHROW);
        mv.visitMaxs(-1, -1);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PRIVATE + ACC_FINAL,
                            NAME_METHOD_READ_OBJECT, DESCR_METHOD_READ_OBJECT,
                            null, SER_HOSTILE_EXCEPTIONS);
        mv.visitCode();
        mv.visitTypeInsn(NEW, NAME_NOT_SERIALIZABLE_EXCEPTION);
        mv.visitInsn(DUP);
        mv.visitLdcInsn("Non-serializable lambda");
        mv.visitMethodInsn(INVOKESPECIAL, NAME_NOT_SERIALIZABLE_EXCEPTION, NAME_CTOR,
                           DESCR_CTOR_NOT_SERIALIZABLE_EXCEPTION, false);
        mv.visitInsn(ATHROW);
        mv.visitMaxs(-1, -1);
        mv.visitEnd();
    }

    /**
     * This class generates a method body which calls the lambda implementation
     * method, converting arguments, as needed.
     */
    private class ForwardingMethodGenerator extends TypeConvertingMethodAdapter {

        ForwardingMethodGenerator(MethodVisitor mv) {
            super(mv);
        }

        void generate(MethodType methodType) {
            visitCode();

            if (implKind == MethodHandleInfo.REF_newInvokeSpecial) {
                visitTypeInsn(NEW, implMethodClassName);
                visitInsn(DUP);
            }
            for (int i = 0; i < argNames.length; i++) {
                visitVarInsn(ALOAD, 0);
                visitFieldInsn(GETFIELD, lambdaClassName, argNames[i], argDescs[i]);
            }

            convertArgumentTypes(methodType);

            // Invoke the method we want to forward to
            visitMethodInsn(invocationOpcode(), implMethodClassName,
                            implMethodName, implMethodDesc,
                            implDefiningClass.isInterface());

            // Convert the return value (if any) and return it
            // Note: if adapting from non-void to void, the 'return'
            // instruction will pop the unneeded result
            Class<?> samReturnClass = methodType.returnType();
            convertType(implMethodReturnClass, samReturnClass, samReturnClass);
            visitInsn(getReturnOpcode(samReturnClass));
            // Maxs computed by ClassWriter.COMPUTE_MAXS,these arguments ignored
            visitMaxs(-1, -1);
            visitEnd();
        }

        private void convertArgumentTypes(MethodType samType) {
            int lvIndex = 0;
            boolean samIncludesReceiver = implIsInstanceMethod &&
                                                   invokedType.parameterCount() == 0;
            int samReceiverLength = samIncludesReceiver ? 1 : 0;
            if (samIncludesReceiver) {
                // push receiver
                Class<?> rcvrType = samType.parameterType(0);
                visitVarInsn(getLoadOpcode(rcvrType), lvIndex + 1);
                lvIndex += getParameterSize(rcvrType);
                convertType(rcvrType, implDefiningClass, instantiatedMethodType.parameterType(0));
            }
            int samParametersLength = samType.parameterCount();
            int argOffset = implMethodType.parameterCount() - samParametersLength;
            for (int i = samReceiverLength; i < samParametersLength; i++) {
                Class<?> argType = samType.parameterType(i);
                visitVarInsn(getLoadOpcode(argType), lvIndex + 1);
                lvIndex += getParameterSize(argType);
                convertType(argType, implMethodType.parameterType(argOffset + i), instantiatedMethodType.parameterType(i));
            }
        }

        private int invocationOpcode() throws InternalError {
            switch (implKind) {
                case MethodHandleInfo.REF_invokeStatic:
                    return INVOKESTATIC;
                case MethodHandleInfo.REF_newInvokeSpecial:
                    return INVOKESPECIAL;
                 case MethodHandleInfo.REF_invokeVirtual:
                    return INVOKEVIRTUAL;
                case MethodHandleInfo.REF_invokeInterface:
                    return INVOKEINTERFACE;
                case MethodHandleInfo.REF_invokeSpecial:
                    return INVOKESPECIAL;
                default:
                    throw new InternalError("Unexpected invocation kind: " + implKind);
            }
        }
    }

    static int getParameterSize(Class<?> c) {
        if (c == Void.TYPE) {
            return 0;
        } else if (c == Long.TYPE || c == Double.TYPE) {
            return 2;
        }
        return 1;
    }

    static int getLoadOpcode(Class<?> c) {
        if(c == Void.TYPE) {
            throw new InternalError("Unexpected void type of load opcode");
        }
        return ILOAD + getOpcodeOffset(c);
    }

    static int getReturnOpcode(Class<?> c) {
        if(c == Void.TYPE) {
            return RETURN;
        }
        return IRETURN + getOpcodeOffset(c);
    }

    private static int getOpcodeOffset(Class<?> c) {
        if (c.isPrimitive()) {
            if (c == Long.TYPE) {
                return 1;
            } else if (c == Float.TYPE) {
                return 2;
            } else if (c == Double.TYPE) {
                return 3;
            }
            return 0;
        } else {
            return 4;
        }
    }

}
