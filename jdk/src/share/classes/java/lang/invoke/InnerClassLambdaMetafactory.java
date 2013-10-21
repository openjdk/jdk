/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
import sun.misc.Unsafe;
import sun.security.action.GetPropertyAction;

import java.io.FilePermission;
import java.lang.reflect.Constructor;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.PropertyPermission;

import static jdk.internal.org.objectweb.asm.Opcodes.*;

/**
 * Lambda metafactory implementation which dynamically creates an
 * inner-class-like class per lambda callsite.
 *
 * @see LambdaMetafactory
 */
/* package */ final class InnerClassLambdaMetafactory extends AbstractValidatingLambdaMetafactory {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private static final int CLASSFILE_VERSION = 51;
    private static final String METHOD_DESCRIPTOR_VOID = Type.getMethodDescriptor(Type.VOID_TYPE);
    private static final String JAVA_LANG_OBJECT = "java/lang/Object";
    private static final String NAME_CTOR = "<init>";

    //Serialization support
    private static final String NAME_SERIALIZED_LAMBDA = "java/lang/invoke/SerializedLambda";
    private static final String DESCR_METHOD_WRITE_REPLACE = "()Ljava/lang/Object;";
    private static final String NAME_METHOD_WRITE_REPLACE = "writeReplace";
    private static final String DESCR_CTOR_SERIALIZED_LAMBDA
            = MethodType.methodType(void.class,
                                    Class.class,
                                    String.class, String.class, String.class,
                                    int.class, String.class, String.class, String.class,
                                    String.class,
                                    Object[].class).toMethodDescriptorString();

    // Used to ensure that each spun class name is unique
    private static final AtomicInteger counter = new AtomicInteger(0);

    // For dumping generated classes to disk, for debugging purposes
    private static final ProxyClassesDumper dumper;

    static {
        final String key = "jdk.internal.lambda.dumpProxyClasses";
        String path = AccessController.doPrivileged(
                new GetPropertyAction(key), null,
                new PropertyPermission(key , "read"));
        dumper = (null == path) ? null : ProxyClassesDumper.getInstance(path);
    }

    // See context values in AbstractValidatingLambdaMetafactory
    private final String implMethodClassName;        // Name of type containing implementation "CC"
    private final String implMethodName;             // Name of implementation method "impl"
    private final String implMethodDesc;             // Type descriptor for implementation methods "(I)Ljava/lang/String;"
    private final Type[] implMethodArgumentTypes;    // ASM types for implementation method parameters
    private final Type implMethodReturnType;         // ASM type for implementation method return type "Ljava/lang/String;"
    private final MethodType constructorType;        // Generated class constructor type "(CC)void"
    private final String constructorDesc;            // Type descriptor for constructor "(LCC;)V"
    private final ClassWriter cw;                    // ASM class writer
    private final Type[] argTypes;                   // ASM types for the constructor arguments
    private final String[] argNames;                 // Generated names for the constructor arguments
    private final String lambdaClassName;            // Generated name for the generated class "X$$Lambda$1"
    private final Type[] instantiatedArgumentTypes;  // ASM types for the functional interface arguments

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
     * @throws ReflectiveOperationException
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
            throws ReflectiveOperationException, LambdaConversionException {
        super(caller, invokedType, samMethodName, samMethodType,
              implMethod, instantiatedMethodType,
              isSerializable, markerInterfaces, additionalBridges);
        implMethodClassName = implDefiningClass.getName().replace('.', '/');
        implMethodName = implInfo.getName();
        implMethodDesc = implMethodType.toMethodDescriptorString();
        Type implMethodAsmType = Type.getMethodType(implMethodDesc);
        implMethodArgumentTypes = implMethodAsmType.getArgumentTypes();
        implMethodReturnType = (implKind == MethodHandleInfo.REF_newInvokeSpecial)
                ? Type.getObjectType(implMethodClassName)
                : implMethodAsmType.getReturnType();
        constructorType = invokedType.changeReturnType(Void.TYPE);
        constructorDesc = constructorType.toMethodDescriptorString();
        lambdaClassName = targetClass.getName().replace('.', '/') + "$$Lambda$" + counter.incrementAndGet();
        cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        argTypes = Type.getArgumentTypes(constructorDesc);
        argNames = new String[argTypes.length];
        for (int i = 0; i < argTypes.length; i++) {
            argNames[i] = "arg$" + (i + 1);
        }
        instantiatedArgumentTypes = Type.getArgumentTypes(
                instantiatedMethodType.toMethodDescriptorString());
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
    CallSite buildCallSite() throws ReflectiveOperationException, LambdaConversionException {
        final Class<?> innerClass = spinInnerClass();
        if (invokedType.parameterCount() == 0) {
            final Constructor[] ctrs = AccessController.doPrivileged(
                    new PrivilegedAction<Constructor[]>() {
                @Override
                public Constructor[] run() {
                    return innerClass.getDeclaredConstructors();
                }
            });
            if (ctrs.length != 1) {
                throw new ReflectiveOperationException("Expected one lambda constructor for "
                        + innerClass.getCanonicalName() + ", got " + ctrs.length);
            }
            // The lambda implementing inner class constructor is private, set
            // it accessible (by us) before creating the constant sole instance
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    ctrs[0].setAccessible(true);
                    return null;
                }
            });
            Object inst = ctrs[0].newInstance();
            return new ConstantCallSite(MethodHandles.constant(samBase, inst));
        } else {
            return new ConstantCallSite(
                    MethodHandles.Lookup.IMPL_LOOKUP
                         .findConstructor(innerClass, constructorType)
                         .asType(constructorType.changeReturnType(samBase)));
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
        String[] interfaces = new String[markerInterfaces.length + 1];
        interfaces[0] = samBase.getName().replace('.', '/');
        for (int i=0; i<markerInterfaces.length; i++) {
            interfaces[i+1] = markerInterfaces[i].getName().replace('.', '/');
        }
        cw.visit(CLASSFILE_VERSION, ACC_SUPER + ACC_FINAL + ACC_SYNTHETIC,
                 lambdaClassName, null,
                 JAVA_LANG_OBJECT, interfaces);

        // Generate final fields to be filled in by constructor
        for (int i = 0; i < argTypes.length; i++) {
            FieldVisitor fv = cw.visitField(ACC_PRIVATE + ACC_FINAL,
                                            argNames[i],
                                            argTypes[i].getDescriptor(),
                                            null, null);
            fv.visitEnd();
        }

        generateConstructor();

        // Forward the SAM method
        String methodDescriptor = samMethodType.toMethodDescriptorString();
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, samMethodName,
                                          methodDescriptor, null, null);
        new ForwardingMethodGenerator(mv).generate(methodDescriptor);

        // Forward the bridges
        if (additionalBridges != null) {
            for (MethodType mt : additionalBridges) {
                methodDescriptor = mt.toMethodDescriptorString();
                mv = cw.visitMethod(ACC_PUBLIC|ACC_BRIDGE, samMethodName,
                                    methodDescriptor, null, null);
                new ForwardingMethodGenerator(mv).generate(methodDescriptor);
            }
        }

        if (isSerializable)
            generateWriteReplace();

        cw.visitEnd();

        // Define the generated class in this VM.

        final byte[] classBytes = cw.toByteArray();

        // If requested, dump out to a file for debugging purposes
        if (dumper != null) {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
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
     * Generate the constructor for the class
     */
    private void generateConstructor() {
        // Generate constructor
        MethodVisitor ctor = cw.visitMethod(ACC_PRIVATE, NAME_CTOR,
                                            constructorDesc, null, null);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, JAVA_LANG_OBJECT, NAME_CTOR,
                             METHOD_DESCRIPTOR_VOID);
        int lvIndex = 0;
        for (int i = 0; i < argTypes.length; i++) {
            ctor.visitVarInsn(ALOAD, 0);
            ctor.visitVarInsn(argTypes[i].getOpcode(ILOAD), lvIndex + 1);
            lvIndex += argTypes[i].getSize();
            ctor.visitFieldInsn(PUTFIELD, lambdaClassName, argNames[i],
                                argTypes[i].getDescriptor());
        }
        ctor.visitInsn(RETURN);
        // Maxs computed by ClassWriter.COMPUTE_MAXS, these arguments ignored
        ctor.visitMaxs(-1, -1);
        ctor.visitEnd();
    }

    /**
     * Generate the writeReplace method (if needed for serialization)
     */
    private void generateWriteReplace() {
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

        mv.iconst(argTypes.length);
        mv.visitTypeInsn(ANEWARRAY, JAVA_LANG_OBJECT);
        for (int i = 0; i < argTypes.length; i++) {
            mv.visitInsn(DUP);
            mv.iconst(i);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, lambdaClassName, argNames[i],
                              argTypes[i].getDescriptor());
            mv.boxIfTypePrimitive(argTypes[i]);
            mv.visitInsn(AASTORE);
        }
        mv.visitMethodInsn(INVOKESPECIAL, NAME_SERIALIZED_LAMBDA, NAME_CTOR,
                DESCR_CTOR_SERIALIZED_LAMBDA);
        mv.visitInsn(ARETURN);
        // Maxs computed by ClassWriter.COMPUTE_MAXS, these arguments ignored
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

        void generate(String methodDescriptor) {
            visitCode();

            if (implKind == MethodHandleInfo.REF_newInvokeSpecial) {
                visitTypeInsn(NEW, implMethodClassName);
                visitInsn(DUP);
            }
            for (int i = 0; i < argTypes.length; i++) {
                visitVarInsn(ALOAD, 0);
                visitFieldInsn(GETFIELD, lambdaClassName, argNames[i],
                               argTypes[i].getDescriptor());
            }

            convertArgumentTypes(Type.getArgumentTypes(methodDescriptor));

            // Invoke the method we want to forward to
            visitMethodInsn(invocationOpcode(), implMethodClassName, implMethodName, implMethodDesc);

            // Convert the return value (if any) and return it
            // Note: if adapting from non-void to void, the 'return'
            // instruction will pop the unneeded result
            Type samReturnType = Type.getReturnType(methodDescriptor);
            convertType(implMethodReturnType, samReturnType, samReturnType);
            visitInsn(samReturnType.getOpcode(Opcodes.IRETURN));
            // Maxs computed by ClassWriter.COMPUTE_MAXS,these arguments ignored
            visitMaxs(-1, -1);
            visitEnd();
        }

        private void convertArgumentTypes(Type[] samArgumentTypes) {
            int lvIndex = 0;
            boolean samIncludesReceiver = implIsInstanceMethod &&
                                                   argTypes.length == 0;
            int samReceiverLength = samIncludesReceiver ? 1 : 0;
            if (samIncludesReceiver) {
                // push receiver
                Type rcvrType = samArgumentTypes[0];
                Type instantiatedRcvrType = instantiatedArgumentTypes[0];

                visitVarInsn(rcvrType.getOpcode(ILOAD), lvIndex + 1);
                lvIndex += rcvrType.getSize();
                convertType(rcvrType, Type.getType(implDefiningClass), instantiatedRcvrType);
            }
            int argOffset = implMethodArgumentTypes.length - samArgumentTypes.length;
            for (int i = samReceiverLength; i < samArgumentTypes.length; i++) {
                Type argType = samArgumentTypes[i];
                Type targetType = implMethodArgumentTypes[argOffset + i];
                Type instantiatedArgType = instantiatedArgumentTypes[i];

                visitVarInsn(argType.getOpcode(ILOAD), lvIndex + 1);
                lvIndex += argType.getSize();
                convertType(argType, targetType, instantiatedArgType);
            }
        }

        private void convertType(Type argType, Type targetType, Type functionalType) {
            convertType(argType.getDescriptor(),
                        targetType.getDescriptor(),
                        functionalType.getDescriptor());
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
}
