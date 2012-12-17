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

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicInteger;
import sun.util.logging.PlatformLogger;
import jdk.internal.org.objectweb.asm.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;
import sun.misc.Unsafe;

/**
 * InnerClassLambdaMetafactory
 */
/*non-public*/ final class InnerClassLambdaMetafactory extends AbstractValidatingLambdaMetafactory {
    private static final int CLASSFILE_VERSION = 51;
    private static final Type TYPE_VOID = Type.getType(void.class);
    private static final String METHOD_DESCRIPTOR_VOID = Type.getMethodDescriptor(Type.VOID_TYPE);
    private static final String NAME_MAGIC_ACCESSOR_IMPL = "java/lang/invoke/MagicLambdaImpl";
    private static final String NAME_SERIALIZABLE = "java/io/Serializable";
    private static final String NAME_CTOR = "<init>";

    //Serialization support
    private static final String NAME_SERIALIZED_LAMBDA = "com/oracle/java/lang/invoke/SerializedLambdaImpl";
    private static final String DESCR_METHOD_WRITE_REPLACE = "()Ljava/lang/Object;";
    private static final String NAME_METHOD_WRITE_REPLACE = "writeReplace";
    private static final String NAME_OBJECT = "java/lang/Object";

    // Used to ensure that each spun class name is unique
    private static final AtomicInteger counter = new AtomicInteger(0);

    // See context values in AbstractValidatingLambdaMetafactory
    private final String implMethodClassName;        // Name of type containing implementation "CC"
    private final String implMethodName;             // Name of implementation method "impl"
    private final String implMethodDesc;             // Type descriptor for implementation methods "(I)Ljava/lang/String;"
    private final Type[] implMethodArgumentTypes;    // ASM types for implementaion method parameters
    private final Type implMethodReturnType;         // ASM type for implementaion method return type "Ljava/lang/String;"
    private final MethodType constructorType;        // Generated class constructor type "(CC)void"
    private final String constructorDesc;            // Type descriptor for constructor "(LCC;)V"
    private final ClassWriter cw;                    // ASM class writer
    private final Type[] argTypes;                   // ASM types for the constructor arguments
    private final String[] argNames;                 // Generated names for the constructor arguments
    private final String lambdaClassName;            // Generated name for the generated class "X$$Lambda$1"
    private final Type[] instantiatedArgumentTypes;  // ASM types for the functional interface arguments

    /**
     * Meta-factory constructor.
     *
     * @param caller Stacked automatically by VM; represents a lookup context with the accessibility privileges
     *               of the caller.
     * @param invokedType Stacked automatically by VM; the signature of the invoked method, which includes the
     *                    expected static type of the returned lambda object, and the static types of the captured
     *                    arguments for the lambda.  In the event that the implementation method is an instance method,
     *                    the first argument in the invocation signature will correspond to the receiver.
     * @param samMethod The primary method in the functional interface to which the lambda or method reference is
     *                  being converted, represented as a method handle.
     * @param implMethod The implementation method which should be called (with suitable adaptation of argument
     *                   types, return types, and adjustment for captured arguments) when methods of the resulting
     *                   functional interface instance are invoked.
     * @param instantiatedMethodType The signature of the SAM method from the functional interface's perspective
     * @throws ReflectiveOperationException
     */
    public InnerClassLambdaMetafactory(MethodHandles.Lookup caller,
                                       MethodType invokedType,
                                       MethodHandle samMethod,
                                       MethodHandle implMethod,
                                       MethodType instantiatedMethodType)
            throws ReflectiveOperationException {
        super(caller, invokedType, samMethod, implMethod, instantiatedMethodType);
        implMethodClassName = implDefiningClass.getName().replace('.', '/');
        implMethodName = implInfo.getName();
        implMethodDesc = implMethodType.toMethodDescriptorString();
        Type implMethodAsmType = Type.getMethodType(implMethodDesc);
        implMethodArgumentTypes = implMethodAsmType.getArgumentTypes();
        implMethodReturnType = implMethodAsmType.getReturnType();
        constructorType = invokedType.changeReturnType(Void.TYPE);
        constructorDesc = constructorType.toMethodDescriptorString();
        lambdaClassName = targetClass.getName().replace('.', '/') + "$$Lambda$" + counter.incrementAndGet();
        cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        argTypes = Type.getArgumentTypes(constructorDesc);
        argNames = new String[argTypes.length];
        for (int i = 0; i < argTypes.length; i++) {
            argNames[i] = "arg$" + (i + 1);
        }
        instantiatedArgumentTypes = Type.getArgumentTypes(instantiatedMethodType.toMethodDescriptorString());

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
     */
    @Override
    CallSite buildCallSite() throws ReflectiveOperationException, LambdaConversionException {
        final Class<?> innerClass = spinInnerClass();
        if (invokedType.parameterCount() == 0) {
            return new ConstantCallSite(MethodHandles.constant(samBase, innerClass.newInstance()));
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
     * @return a Class which implements the functional interface
     */
    private <T> Class<? extends T> spinInnerClass() throws LambdaConversionException {
        String samName = samBase.getName().replace('.', '/');

        cw.visit(CLASSFILE_VERSION, ACC_PUBLIC + ACC_SUPER, lambdaClassName, null, NAME_MAGIC_ACCESSOR_IMPL,
                 isSerializable ? new String[]{samName, NAME_SERIALIZABLE} : new String[]{samName});

        // Generate final fields to be filled in by constructor
        for (int i = 0; i < argTypes.length; i++) {
            FieldVisitor fv = cw.visitField(ACC_PRIVATE + ACC_FINAL, argNames[i], argTypes[i].getDescriptor(), null, null);
            fv.visitEnd();
        }

        generateConstructor();

        MethodAnalyzer ma = new MethodAnalyzer();

        // Forward the SAM method
        if (ma.getSamMethod() == null) {
            throw new LambdaConversionException(String.format("SAM method not found: %s", samMethodType));
        } else {
            generateForwardingMethod(ma.getSamMethod(), false);
        }

        // Forward the bridges
        // @@@ Once the VM can do fail-over, uncomment the default method test
        if (!ma.getMethodsToBridge().isEmpty() /* && !ma.wasDefaultMethodFound() */) {
            for (Method m : ma.getMethodsToBridge()) {
                generateForwardingMethod(m, true);
            }
        }

        /***** Serialization not yet supported
        if (isSerializable) {
            String samMethodName = samInfo.getName();
            Type samType = Type.getType(samBase);
            generateSerializationMethod(samType, samMethodName);
        }
        ******/

        cw.visitEnd();

        // Define the generated class in this VM.

        final byte[] classBytes = cw.toByteArray();

        if (System.getProperty("debug.dump.generated") != null) {
            System.out.printf("Loaded: %s (%d bytes) %n", lambdaClassName, classBytes.length);
            try (FileOutputStream fos = new FileOutputStream(lambdaClassName.replace('/', '.') + ".class")) {
                fos.write(classBytes);
            } catch (IOException ex) {
                PlatformLogger.getLogger(InnerClassLambdaMetafactory.class.getName()).severe(ex.getMessage(), ex);
            }
        }

        ClassLoader loader = targetClass.getClassLoader();
        ProtectionDomain pd = (loader == null) ? null : targetClass.getProtectionDomain();
        return (Class<? extends T>) Unsafe.getUnsafe().defineClass(lambdaClassName, classBytes, 0, classBytes.length, loader, pd);
    }

    /**
     * Generate the constructor for the class
     */
    private void generateConstructor() {
        // Generate constructor
        MethodVisitor ctor = cw.visitMethod(ACC_PUBLIC, NAME_CTOR, constructorDesc, null, null);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, NAME_MAGIC_ACCESSOR_IMPL, NAME_CTOR, METHOD_DESCRIPTOR_VOID);
        int lvIndex = 0;
        for (int i = 0; i < argTypes.length; i++) {
            ctor.visitVarInsn(ALOAD, 0);
            ctor.visitVarInsn(argTypes[i].getOpcode(ILOAD), lvIndex + 1);
            lvIndex += argTypes[i].getSize();
            ctor.visitFieldInsn(PUTFIELD, lambdaClassName, argNames[i], argTypes[i].getDescriptor());
        }
        ctor.visitInsn(RETURN);
        ctor.visitMaxs(-1, -1); // Maxs computed by ClassWriter.COMPUTE_MAXS, these arguments ignored
        ctor.visitEnd();
    }

    /**
     * Generate the serialization method (if needed)
     */
    /****** This code is out of date -- known to be wrong -- and not currently used ******
    private void generateSerializationMethod(Type samType, String samMethodName) {
        String samMethodDesc = samMethodType.toMethodDescriptorString();
        TypeConvertingMethodAdapter mv = new TypeConvertingMethodAdapter(cw.visitMethod(ACC_PRIVATE + ACC_FINAL, NAME_METHOD_WRITE_REPLACE, DESCR_METHOD_WRITE_REPLACE, null, null));

        mv.visitCode();
        mv.visitTypeInsn(NEW, NAME_SERIALIZED_LAMBDA);
        mv.dup();
        mv.visitLdcInsn(samType);
        mv.visitLdcInsn(samMethodName);
        mv.visitLdcInsn(samMethodDesc);
        mv.visitLdcInsn(Type.getType(implDefiningClass));
        mv.visitLdcInsn(implMethodName);
        mv.visitLdcInsn(implMethodDesc);

        mv.iconst(argTypes.length);
        mv.visitTypeInsn(ANEWARRAY, NAME_OBJECT);
        for (int i = 0; i < argTypes.length; i++) {
            mv.dup();
            mv.iconst(i);
            mv.visitVarInsn(ALOAD, 0);
            mv.getfield(lambdaClassName, argNames[i], argTypes[i].getDescriptor());
            mv.boxIfPrimitive(argTypes[i]);
            mv.visitInsn(AASTORE);
        }
        mv.invokespecial(NAME_SERIALIZED_LAMBDA, NAME_CTOR,
                           "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)V");
        mv.visitInsn(ARETURN);
        mv.visitMaxs(-1, -1); // Maxs computed by ClassWriter.COMPUTE_MAXS, these arguments ignored
        mv.visitEnd();
    }
    ********/

    /**
     * Generate a method which calls the lambda implementation method,
     * converting arguments, as needed.
     * @param m The method whose signature should be generated
     * @param isBridge True if this methods should be flagged as a bridge
     */
    private void generateForwardingMethod(Method m, boolean isBridge) {
        Class<?>[] exceptionTypes = m.getExceptionTypes();
        String[] exceptionNames = new String[exceptionTypes.length];
        for (int i = 0; i < exceptionTypes.length; i++) {
            exceptionNames[i] = exceptionTypes[i].getName().replace('.', '/');
        }
        String methodDescriptor = Type.getMethodDescriptor(m);
        int access = isBridge? ACC_PUBLIC | ACC_BRIDGE : ACC_PUBLIC;
        MethodVisitor mv = cw.visitMethod(access, m.getName(), methodDescriptor, null, exceptionNames);
        new ForwardingMethodGenerator(mv).generate(m);
    }

    /**
     * This class generates a method body which calls the lambda implementation
     * method, converting arguments, as needed.
     */
    private class ForwardingMethodGenerator extends TypeConvertingMethodAdapter {

        ForwardingMethodGenerator(MethodVisitor mv) {
            super(mv);
        }

        void generate(Method m) throws InternalError {
            visitCode();

            if (implKind == MethodHandleInfo.REF_newInvokeSpecial) {
                visitTypeInsn(NEW, implMethodClassName);
                dup();
            }
            for (int i = 0; i < argTypes.length; i++) {
                visitVarInsn(ALOAD, 0);
                getfield(lambdaClassName, argNames[i], argTypes[i].getDescriptor());
            }

            convertArgumentTypes(Type.getArgumentTypes(m));

            // Invoke the method we want to forward to
            visitMethodInsn(invocationOpcode(), implMethodClassName, implMethodName, implMethodDesc);

            // Convert the return value (if any) and return it
            // Note: if adapting from non-void to void, the 'return' instruction will pop the unneeded result
            Type samReturnType = Type.getReturnType(m);
            convertType(implMethodReturnType, samReturnType, samReturnType);
            areturn(samReturnType);

            visitMaxs(-1, -1); // Maxs computed by ClassWriter.COMPUTE_MAXS, these arguments ignored
            visitEnd();
        }

        private void convertArgumentTypes(Type[] samArgumentTypes) {
            int lvIndex = 0;
            boolean samIncludesReceiver = implIsInstanceMethod && argTypes.length == 0;
            int samReceiverLength = samIncludesReceiver ? 1 : 0;
            if (samIncludesReceiver) {
                // push receiver
                Type rcvrType = samArgumentTypes[0];
                Type instantiatedRcvrType = instantiatedArgumentTypes[0];

                load(lvIndex + 1, rcvrType);
                lvIndex += rcvrType.getSize();
                convertType(rcvrType, Type.getType(implDefiningClass), instantiatedRcvrType);
            }
            int argOffset = implMethodArgumentTypes.length - samArgumentTypes.length;
            for (int i = samReceiverLength; i < samArgumentTypes.length; i++) {
                Type argType = samArgumentTypes[i];
                Type targetType = implMethodArgumentTypes[argOffset + i];
                Type instantiatedArgType = instantiatedArgumentTypes[i];

                load(lvIndex + 1, argType);
                lvIndex += argType.getSize();
                convertType(argType, targetType, instantiatedArgType);
            }
        }

        private void convertType(Type argType, Type targetType, Type functionalType) {
            convertType(argType.getDescriptor(), targetType.getDescriptor(), functionalType.getDescriptor());
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

        /**
         * The following methods are copied from
         * org.objectweb.asm.commons.InstructionAdapter. Part of ASM: a very
         * small and fast Java bytecode manipulation framework. Copyright (c)
         * 2000-2005 INRIA, France Telecom All rights reserved.
         *
         * Subclass with that (removing these methods) if that package/class is
         * ever added to the JDK.
         */
        private void iconst(final int cst) {
            if (cst >= -1 && cst <= 5) {
                mv.visitInsn(Opcodes.ICONST_0 + cst);
            } else if (cst >= Byte.MIN_VALUE && cst <= Byte.MAX_VALUE) {
                mv.visitIntInsn(Opcodes.BIPUSH, cst);
            } else if (cst >= Short.MIN_VALUE && cst <= Short.MAX_VALUE) {
                mv.visitIntInsn(Opcodes.SIPUSH, cst);
            } else {
                mv.visitLdcInsn(cst);
            }
        }

        private void load(final int var, final Type type) {
            mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), var);
        }

        private void dup() {
            mv.visitInsn(Opcodes.DUP);
        }

        private void areturn(final Type t) {
            mv.visitInsn(t.getOpcode(Opcodes.IRETURN));
        }

        private void getfield(
                final String owner,
                final String name,
                final String desc) {
            mv.visitFieldInsn(Opcodes.GETFIELD, owner, name, desc);
        }
    }
}
