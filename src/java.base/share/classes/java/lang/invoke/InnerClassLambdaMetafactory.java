/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.misc.CDS;
import jdk.internal.util.ClassFileDumper;
import sun.invoke.util.VerifyAccess;
import sun.security.action.GetBooleanAction;

import java.io.Serializable;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.FieldBuilder;
import java.lang.classfile.MethodBuilder;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static java.lang.classfile.ClassFile.*;
import java.lang.classfile.attribute.ExceptionsAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.MethodRefEntry;
import static java.lang.constant.ConstantDescs.*;
import static java.lang.invoke.MethodHandles.Lookup.ClassOption.NESTMATE;
import static java.lang.invoke.MethodHandles.Lookup.ClassOption.STRONG;
import static java.lang.invoke.MethodType.methodType;
import jdk.internal.constant.ConstantUtils;
import jdk.internal.constant.MethodTypeDescImpl;
import jdk.internal.constant.ReferenceClassDescImpl;
import sun.invoke.util.Wrapper;

/**
 * Lambda metafactory implementation which dynamically creates an
 * inner-class-like class per lambda callsite.
 *
 * @see LambdaMetafactory
 */
/* package */ final class InnerClassLambdaMetafactory extends AbstractValidatingLambdaMetafactory {
    private static final String LAMBDA_INSTANCE_FIELD = "LAMBDA_INSTANCE$";
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final ClassDesc[] EMPTY_CLASSDESC_ARRAY = ConstantUtils.EMPTY_CLASSDESC;

    // Static builders to avoid lambdas
    record FieldFlags(int flags) implements Consumer<FieldBuilder> {
        @Override
        public void accept(FieldBuilder fb) {
            fb.withFlags(flags);
        }
    };
    record MethodBody(Consumer<CodeBuilder> code) implements Consumer<MethodBuilder> {
        @Override
        public void accept(MethodBuilder mb) {
            mb.withCode(code);
        }
    };

    // For dumping generated classes to disk, for debugging purposes
    private static final ClassFileDumper lambdaProxyClassFileDumper;

    private static final boolean disableEagerInitialization;

    static {
        // To dump the lambda proxy classes, set this system property:
        //    -Djdk.invoke.LambdaMetafactory.dumpProxyClassFiles
        // or -Djdk.invoke.LambdaMetafactory.dumpProxyClassFiles=true
        final String dumpProxyClassesKey = "jdk.invoke.LambdaMetafactory.dumpProxyClassFiles";
        lambdaProxyClassFileDumper = ClassFileDumper.getInstance(dumpProxyClassesKey, "DUMP_LAMBDA_PROXY_CLASS_FILES");

        final String disableEagerInitializationKey = "jdk.internal.lambda.disableEagerInitialization";
        disableEagerInitialization = GetBooleanAction.privilegedGetProperty(disableEagerInitializationKey);
    }

    // See context values in AbstractValidatingLambdaMetafactory
    private final ClassDesc implMethodClassDesc;     // Name of type containing implementation "CC"
    private final String implMethodName;             // Name of implementation method "impl"
    private final MethodTypeDesc implMethodDesc;     // Type descriptor for implementation methods "(I)Ljava/lang/String;"
    private final MethodType constructorType;        // Generated class constructor type "(CC)void"
    private final MethodTypeDesc constructorTypeDesc;// Type descriptor for the generated class constructor type "(CC)void"
    private final String[] argNames;                 // Generated names for the constructor arguments
    private final ClassDesc[] argDescs;              // Type descriptors for the constructor arguments
    private final String lambdaClassName;            // Generated name for the generated class "X$$Lambda$1"
    private final ClassDesc lambdaClassDesc;         // Type descriptor for the generated class "X$$Lambda$1"
    private final boolean useImplMethodHandle;       // use MethodHandle invocation instead of symbolic bytecode invocation

    /**
     * General meta-factory constructor, supporting both standard cases and
     * allowing for uncommon options such as serialization or bridging.
     *
     * @param caller Stacked automatically by VM; represents a lookup context
     *               with the accessibility privileges of the caller.
     * @param factoryType Stacked automatically by VM; the signature of the
     *                    invoked method, which includes the expected static
     *                    type of the returned lambda object, and the static
     *                    types of the captured arguments for the lambda.  In
     *                    the event that the implementation method is an
     *                    instance method, the first argument in the invocation
     *                    signature will correspond to the receiver.
     * @param interfaceMethodName Name of the method in the functional interface to
     *                   which the lambda or method reference is being
     *                   converted, represented as a String.
     * @param interfaceMethodType Type of the method in the functional interface to
     *                            which the lambda or method reference is being
     *                            converted, represented as a MethodType.
     * @param implementation The implementation method which should be called (with
     *                       suitable adaptation of argument types, return types,
     *                       and adjustment for captured arguments) when methods of
     *                       the resulting functional interface instance are invoked.
     * @param dynamicMethodType The signature of the primary functional
     *                          interface method after type variables are
     *                          substituted with their instantiation from
     *                          the capture site
     * @param isSerializable Should the lambda be made serializable?  If set,
     *                       either the target type or one of the additional SAM
     *                       types must extend {@code Serializable}.
     * @param altInterfaces Additional interfaces which the lambda object
     *                      should implement.
     * @param altMethods Method types for additional signatures to be
     *                   implemented by invoking the implementation method
     * @throws LambdaConversionException If any of the meta-factory protocol
     *         invariants are violated
     * @throws SecurityException If a security manager is present, and it
     *         <a href="MethodHandles.Lookup.html#secmgr">denies access</a>
     *         from {@code caller} to the package of {@code implementation}.
     */
    public InnerClassLambdaMetafactory(MethodHandles.Lookup caller,
                                       MethodType factoryType,
                                       String interfaceMethodName,
                                       MethodType interfaceMethodType,
                                       MethodHandle implementation,
                                       MethodType dynamicMethodType,
                                       boolean isSerializable,
                                       Class<?>[] altInterfaces,
                                       MethodType[] altMethods)
            throws LambdaConversionException {
        super(caller, factoryType, interfaceMethodName, interfaceMethodType,
              implementation, dynamicMethodType,
              isSerializable, altInterfaces, altMethods);
        implMethodClassDesc = implClassDesc(implClass);
        implMethodName = implInfo.getName();
        implMethodDesc = methodDesc(implInfo.getMethodType());
        constructorType = factoryType.changeReturnType(Void.TYPE);
        constructorTypeDesc = methodDesc(constructorType);
        lambdaClassName = lambdaClassName(targetClass);
        lambdaClassDesc = ClassDesc.ofInternalName(lambdaClassName);
        // If the target class invokes a protected method inherited from a
        // superclass in a different package, or does 'invokespecial', the
        // lambda class has no access to the resolved method, or does
        // 'invokestatic' on a hidden class which cannot be resolved by name.
        // Instead, we need to pass the live implementation method handle to
        // the proxy class to invoke directly. (javac prefers to avoid this
        // situation by generating bridges in the target class)
        useImplMethodHandle = (Modifier.isProtected(implInfo.getModifiers()) &&
                               !VerifyAccess.isSamePackage(targetClass, implInfo.getDeclaringClass())) ||
                               implKind == MethodHandleInfo.REF_invokeSpecial ||
                               implKind == MethodHandleInfo.REF_invokeStatic && implClass.isHidden();
        int parameterCount = factoryType.parameterCount();
        if (parameterCount > 0) {
            argNames = new String[parameterCount];
            argDescs = new ClassDesc[parameterCount];
            for (int i = 0; i < parameterCount; i++) {
                argNames[i] = "arg$" + (i + 1);
                argDescs[i] = classDesc(factoryType.parameterType(i));
            }
        } else {
            argNames = EMPTY_STRING_ARRAY;
            argDescs = EMPTY_CLASSDESC_ARRAY;
        }
    }

    private static String lambdaClassName(Class<?> targetClass) {
        String name = targetClass.getName();
        if (targetClass.isHidden()) {
            // use the original class name
            name = name.replace('/', '_');
        }
        return name.replace('.', '/') + "$$Lambda";
    }

    /**
     * Build the CallSite. Generate a class file which implements the functional
     * interface, define the class, if there are no parameters create an instance
     * of the class which the CallSite will return, otherwise, generate handles
     * which will call the class' constructor.
     *
     * @return a CallSite, which, when invoked, will return an instance of the
     * functional interface
     * @throws LambdaConversionException If properly formed functional interface
     * is not found
     */
    @Override
    CallSite buildCallSite() throws LambdaConversionException {
        final Class<?> innerClass = spinInnerClass();
        if (factoryType.parameterCount() == 0 && disableEagerInitialization) {
            try {
                return new ConstantCallSite(caller.findStaticGetter(innerClass, LAMBDA_INSTANCE_FIELD,
                                                                    factoryType.returnType()));
            } catch (ReflectiveOperationException e) {
                throw new LambdaConversionException(
                        "Exception finding " + LAMBDA_INSTANCE_FIELD + " static field", e);
            }
        } else {
            try {
                MethodHandle mh = caller.findConstructor(innerClass, constructorType);
                if (factoryType.parameterCount() == 0) {
                    // In the case of a non-capturing lambda, we optimize linkage by pre-computing a single instance
                    Object inst = mh.asType(methodType(Object.class)).invokeExact();
                    return new ConstantCallSite(MethodHandles.constant(interfaceClass, inst));
                } else {
                    return new ConstantCallSite(mh.asType(factoryType));
                }
            } catch (ReflectiveOperationException e) {
                throw new LambdaConversionException("Exception finding constructor", e);
            } catch (Throwable e) {
                throw new LambdaConversionException("Exception instantiating lambda object", e);
            }
        }
    }

    /**
     * Spins the lambda proxy class.
     *
     * This first checks if a lambda proxy class can be loaded from CDS archive.
     * Otherwise, generate the lambda proxy class. If CDS dumping is enabled, it
     * registers the lambda proxy class for including into the CDS archive.
     */
    private Class<?> spinInnerClass() throws LambdaConversionException {
        // CDS does not handle disableEagerInitialization or useImplMethodHandle
        if (!disableEagerInitialization && !useImplMethodHandle) {
            if (CDS.isUsingArchive()) {
                // load from CDS archive if present
                Class<?> innerClass = LambdaProxyClassArchive.find(targetClass,
                                                                   interfaceMethodName,
                                                                   factoryType,
                                                                   interfaceMethodType,
                                                                   implementation,
                                                                   dynamicMethodType,
                                                                   isSerializable,
                                                                   altInterfaces,
                                                                   altMethods);
                if (innerClass != null) return innerClass;
            }

            // include lambda proxy class in CDS archive at dump time
            if (CDS.isDumpingArchive()) {
                Class<?> innerClass = generateInnerClass();
                LambdaProxyClassArchive.register(targetClass,
                                                 interfaceMethodName,
                                                 factoryType,
                                                 interfaceMethodType,
                                                 implementation,
                                                 dynamicMethodType,
                                                 isSerializable,
                                                 altInterfaces,
                                                 altMethods,
                                                 innerClass);
                return innerClass;
            }

        }
        return generateInnerClass();
    }

    /**
     * Generate a class file which implements the functional
     * interface, define and return the class.
     *
     * @return a Class which implements the functional interface
     * @throws LambdaConversionException If properly formed functional interface
     * is not found
     */
    private Class<?> generateInnerClass() throws LambdaConversionException {
        List<ClassDesc> interfaces;
        ClassDesc interfaceDesc = classDesc(interfaceClass);
        boolean accidentallySerializable = !isSerializable && Serializable.class.isAssignableFrom(interfaceClass);
        if (altInterfaces.length == 0) {
            interfaces = List.of(interfaceDesc);
        } else {
            // Assure no duplicate interfaces (ClassFormatError)
            Set<ClassDesc> itfs = LinkedHashSet.newLinkedHashSet(altInterfaces.length + 1);
            itfs.add(interfaceDesc);
            for (Class<?> i : altInterfaces) {
                itfs.add(classDesc(i));
                accidentallySerializable |= !isSerializable && Serializable.class.isAssignableFrom(i);
            }
            interfaces = List.copyOf(itfs);
        }
        final boolean finalAccidentallySerializable = accidentallySerializable;
        final byte[] classBytes = ClassFile.of().build(lambdaClassDesc, new Consumer<ClassBuilder>() {
            @Override
            public void accept(ClassBuilder clb) {
                clb.withFlags(ACC_SUPER | ACC_FINAL | ACC_SYNTHETIC)
                   .withInterfaceSymbols(interfaces);
                // Generate final fields to be filled in by constructor
                for (int i = 0; i < argDescs.length; i++) {
                    clb.withField(argNames[i], argDescs[i], new FieldFlags(ACC_PRIVATE | ACC_FINAL));
                }

                generateConstructor(clb);

                if (factoryType.parameterCount() == 0 && disableEagerInitialization) {
                    generateClassInitializer(clb);
                }

                // Forward the SAM method
                clb.withMethod(interfaceMethodName,
                        methodDesc(interfaceMethodType),
                        ACC_PUBLIC,
                        forwardingMethod(interfaceMethodType));

                // Forward the bridges
                if (altMethods != null) {
                    for (MethodType mt : altMethods) {
                        clb.withMethod(interfaceMethodName,
                                methodDesc(mt),
                                ACC_PUBLIC | ACC_BRIDGE,
                                forwardingMethod(mt));
                    }
                }

                if (isSerializable)
                    generateSerializationFriendlyMethods(clb);
                else if (finalAccidentallySerializable)
                    generateSerializationHostileMethods(clb);
            }
        });

        // Define the generated class in this VM.

        try {
            // this class is linked at the indy callsite; so define a hidden nestmate
            var classdata = useImplMethodHandle? implementation : null;
            return caller.makeHiddenClassDefiner(lambdaClassName, classBytes, Set.of(NESTMATE, STRONG), lambdaProxyClassFileDumper)
                         .defineClass(!disableEagerInitialization, classdata);

        } catch (Throwable t) {
            throw new InternalError(t);
        }
    }

    /**
     * Generate a static field and a static initializer that sets this field to an instance of the lambda
     */
    private void generateClassInitializer(ClassBuilder clb) {
        ClassDesc lambdaTypeDescriptor = classDesc(factoryType.returnType());

        // Generate the static final field that holds the lambda singleton
        clb.withField(LAMBDA_INSTANCE_FIELD, lambdaTypeDescriptor, new FieldFlags(ACC_PRIVATE | ACC_STATIC | ACC_FINAL));

        // Instantiate the lambda and store it to the static final field
        clb.withMethod(CLASS_INIT_NAME, MTD_void, ACC_STATIC, new MethodBody(new Consumer<CodeBuilder>() {
            @Override
            public void accept(CodeBuilder cob) {
                assert factoryType.parameterCount() == 0;
                cob.new_(lambdaClassDesc)
                   .dup()
                   .invokespecial(lambdaClassDesc, INIT_NAME, constructorTypeDesc)
                   .putstatic(lambdaClassDesc, LAMBDA_INSTANCE_FIELD, lambdaTypeDescriptor)
                   .return_();
            }
        }));
    }

    /**
     * Generate the constructor for the class
     */
    private void generateConstructor(ClassBuilder clb) {
        // Generate constructor
        clb.withMethod(INIT_NAME, constructorTypeDesc, ACC_PRIVATE,
                new MethodBody(new Consumer<CodeBuilder>() {
                    @Override
                    public void accept(CodeBuilder cob) {
                        cob.aload(0)
                           .invokespecial(CD_Object, INIT_NAME, MTD_void);
                        int parameterCount = factoryType.parameterCount();
                        for (int i = 0; i < parameterCount; i++) {
                            cob.aload(0);
                            Class<?> argType = factoryType.parameterType(i);
                            cob.loadLocal(TypeKind.from(argType), cob.parameterSlot(i));
                            cob.putfield(lambdaClassDesc, argNames[i], argDescs[i]);
                        }
                        cob.return_();
                    }
                }));
    }

    private static class SerializationSupport {
        // Serialization support
        private static final ClassDesc CD_SerializedLambda = ReferenceClassDescImpl.ofValidated("Ljava/lang/invoke/SerializedLambda;");
        private static final ClassDesc CD_ObjectOutputStream = ReferenceClassDescImpl.ofValidated("Ljava/io/ObjectOutputStream;");
        private static final ClassDesc CD_ObjectInputStream = ReferenceClassDescImpl.ofValidated("Ljava/io/ObjectInputStream;");
        private static final MethodTypeDesc MTD_Object = MethodTypeDescImpl.ofValidated(CD_Object);
        private static final MethodTypeDesc MTD_void_ObjectOutputStream = MethodTypeDescImpl.ofValidated(CD_void, CD_ObjectOutputStream);
        private static final MethodTypeDesc MTD_void_ObjectInputStream = MethodTypeDescImpl.ofValidated(CD_void, CD_ObjectInputStream);

        private static final String NAME_METHOD_WRITE_REPLACE = "writeReplace";
        private static final String NAME_METHOD_READ_OBJECT = "readObject";
        private static final String NAME_METHOD_WRITE_OBJECT = "writeObject";

        static final ClassDesc CD_NotSerializableException = ReferenceClassDescImpl.ofValidated("Ljava/io/NotSerializableException;");
        static final MethodTypeDesc MTD_CTOR_NOT_SERIALIZABLE_EXCEPTION = MethodTypeDescImpl.ofValidated(CD_void, CD_String);
        static final MethodTypeDesc MTD_CTOR_SERIALIZED_LAMBDA = MethodTypeDescImpl.ofValidated(CD_void,
                CD_Class, CD_String, CD_String, CD_String, CD_int, CD_String, CD_String, CD_String, CD_String, ReferenceClassDescImpl.ofValidated("[Ljava/lang/Object;"));

    }

    /**
     * Generate a writeReplace method that supports serialization
     */
    private void generateSerializationFriendlyMethods(ClassBuilder clb) {
        clb.withMethod(SerializationSupport.NAME_METHOD_WRITE_REPLACE, SerializationSupport.MTD_Object, ACC_PRIVATE | ACC_FINAL,
                new MethodBody(new Consumer<CodeBuilder>() {
                    @Override
                    public void accept(CodeBuilder cob) {
                        cob.new_(SerializationSupport.CD_SerializedLambda)
                           .dup()
                           .ldc(classDesc(targetClass))
                           .ldc(factoryType.returnType().getName().replace('.', '/'))
                           .ldc(interfaceMethodName)
                           .ldc(interfaceMethodType.toMethodDescriptorString())
                           .ldc(implInfo.getReferenceKind())
                           .ldc(implInfo.getDeclaringClass().getName().replace('.', '/'))
                           .ldc(implInfo.getName())
                           .ldc(implInfo.getMethodType().toMethodDescriptorString())
                           .ldc(dynamicMethodType.toMethodDescriptorString())
                           .loadConstant(argDescs.length)
                           .anewarray(CD_Object);
                        for (int i = 0; i < argDescs.length; i++) {
                            cob.dup()
                               .loadConstant(i)
                               .aload(0)
                               .getfield(lambdaClassDesc, argNames[i], argDescs[i]);
                            TypeConvertingMethodAdapter.boxIfTypePrimitive(cob, TypeKind.from(argDescs[i]));
                            cob.aastore();
                        }
                        cob.invokespecial(SerializationSupport.CD_SerializedLambda, INIT_NAME,
                                          SerializationSupport.MTD_CTOR_SERIALIZED_LAMBDA)
                           .areturn();
                    }
                }));
    }

    /**
     * Generate a readObject/writeObject method that is hostile to serialization
     */
    private void generateSerializationHostileMethods(ClassBuilder clb) {
        var hostileMethod = new Consumer<MethodBuilder>() {
            @Override
            public void accept(MethodBuilder mb) {
                ConstantPoolBuilder cp = mb.constantPool();
                ClassEntry nseCE = cp.classEntry(SerializationSupport.CD_NotSerializableException);
                mb.with(ExceptionsAttribute.of(nseCE))
                        .withCode(new Consumer<CodeBuilder>() {
                            @Override
                            public void accept(CodeBuilder cob) {
                                cob.new_(nseCE)
                                        .dup()
                                        .ldc("Non-serializable lambda")
                                        .invokespecial(cp.methodRefEntry(nseCE, cp.nameAndTypeEntry(INIT_NAME,
                                                SerializationSupport.MTD_CTOR_NOT_SERIALIZABLE_EXCEPTION)))
                                        .athrow();
                            }
                        });
            }
        };
        clb.withMethod(SerializationSupport.NAME_METHOD_WRITE_OBJECT, SerializationSupport.MTD_void_ObjectOutputStream,
                ACC_PRIVATE + ACC_FINAL, hostileMethod);
        clb.withMethod(SerializationSupport.NAME_METHOD_READ_OBJECT, SerializationSupport.MTD_void_ObjectInputStream,
                ACC_PRIVATE + ACC_FINAL, hostileMethod);
    }

    /**
     * This method generates a method body which calls the lambda implementation
     * method, converting arguments, as needed.
     */
    Consumer<MethodBuilder> forwardingMethod(MethodType methodType) {
        return new MethodBody(new Consumer<CodeBuilder>() {
            @Override
            public void accept(CodeBuilder cob) {
                if (implKind == MethodHandleInfo.REF_newInvokeSpecial) {
                    cob.new_(implMethodClassDesc)
                       .dup();
                }
                if (useImplMethodHandle) {
                    ConstantPoolBuilder cp = cob.constantPool();
                    cob.ldc(cp.constantDynamicEntry(cp.bsmEntry(cp.methodHandleEntry(BSM_CLASS_DATA), List.of()),
                                                    cp.nameAndTypeEntry(DEFAULT_NAME, CD_MethodHandle)));
                }
                for (int i = 0; i < argNames.length; i++) {
                    cob.aload(0)
                       .getfield(lambdaClassDesc, argNames[i], argDescs[i]);
                }

                convertArgumentTypes(cob, methodType);

                if (useImplMethodHandle) {
                    MethodType mtype = implInfo.getMethodType();
                    if (implKind != MethodHandleInfo.REF_invokeStatic) {
                        mtype = mtype.insertParameterTypes(0, implClass);
                    }
                    cob.invokevirtual(CD_MethodHandle, "invokeExact", methodDesc(mtype));
                } else {
                    // Invoke the method we want to forward to
                    cob.invoke(invocationOpcode(), implMethodClassDesc, implMethodName, implMethodDesc, implClass.isInterface());
                }
                // Convert the return value (if any) and return it
                // Note: if adapting from non-void to void, the 'return'
                // instruction will pop the unneeded result
                Class<?> implReturnClass = implMethodType.returnType();
                Class<?> samReturnClass = methodType.returnType();
                TypeConvertingMethodAdapter.convertType(cob, implReturnClass, samReturnClass, samReturnClass);
                cob.return_(TypeKind.from(samReturnClass));
            }
        });
    }

    private void convertArgumentTypes(CodeBuilder cob, MethodType samType) {
        int samParametersLength = samType.parameterCount();
        int captureArity = factoryType.parameterCount();
        for (int i = 0; i < samParametersLength; i++) {
            Class<?> argType = samType.parameterType(i);
            cob.loadLocal(TypeKind.from(argType), cob.parameterSlot(i));
            TypeConvertingMethodAdapter.convertType(cob, argType, implMethodType.parameterType(captureArity + i), dynamicMethodType.parameterType(i));
        }
    }

    private Opcode invocationOpcode() throws InternalError {
        return switch (implKind) {
            case MethodHandleInfo.REF_invokeStatic     -> Opcode.INVOKESTATIC;
            case MethodHandleInfo.REF_newInvokeSpecial -> Opcode.INVOKESPECIAL;
            case MethodHandleInfo.REF_invokeVirtual    -> Opcode.INVOKEVIRTUAL;
            case MethodHandleInfo.REF_invokeInterface  -> Opcode.INVOKEINTERFACE;
            case MethodHandleInfo.REF_invokeSpecial    -> Opcode.INVOKESPECIAL;
            default -> throw new InternalError("Unexpected invocation kind: " + implKind);
        };
    }

    static ClassDesc implClassDesc(Class<?> cls) {
        return cls.isHidden() ? null : ReferenceClassDescImpl.ofValidated(cls.descriptorString());
    }

    static ClassDesc classDesc(Class<?> cls) {
        return cls.isPrimitive() ? Wrapper.forPrimitiveType(cls).basicClassDescriptor()
                                 : ReferenceClassDescImpl.ofValidated(cls.descriptorString());
    }

    static MethodTypeDesc methodDesc(MethodType mt) {
        var params = new ClassDesc[mt.parameterCount()];
        for (int i = 0; i < params.length; i++) {
            params[i] = classDesc(mt.parameterType(i));
        }
        return MethodTypeDescImpl.ofValidated(classDesc(mt.returnType()), params);
    }
}
