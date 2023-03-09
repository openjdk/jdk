/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.classfile.attribute.ExceptionsAttribute;
import jdk.internal.misc.CDS;
import jdk.internal.misc.VM;
import sun.invoke.util.BytecodeDescriptor;
import sun.invoke.util.VerifyAccess;
import sun.security.action.GetPropertyAction;
import sun.security.action.GetBooleanAction;

import java.io.FilePermission;
import java.io.Serializable;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.PropertyPermission;
import java.util.Set;

import static java.lang.invoke.MethodHandleStatics.CLASSFILE_VERSION;
import static java.lang.invoke.MethodHandles.Lookup.ClassOption.NESTMATE;
import static java.lang.invoke.MethodHandles.Lookup.ClassOption.STRONG;
import static java.lang.invoke.MethodType.methodType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import jdk.internal.classfile.AccessFlags;
import jdk.internal.classfile.ClassBuilder;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.Opcode;
import jdk.internal.classfile.TypeKind;
import static jdk.internal.classfile.Classfile.*;
import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.FieldBuilder;
import jdk.internal.classfile.MethodBuilder;

/**
 * Lambda metafactory implementation which dynamically creates an
 * inner-class-like class per lambda callsite.
 *
 * @see LambdaMetafactory
 */
/* package */ final class InnerClassLambdaMetafactory extends AbstractValidatingLambdaMetafactory {
    private static final int CLASSFILE_VERSION = VM.classFileVersion();
    private static final String NAME_CTOR = "<init>";
    private static final String LAMBDA_INSTANCE_FIELD = "LAMBDA_INSTANCE$";

    //Serialization support
    private static final ClassDesc NAME_SERIALIZED_LAMBDA = ClassDesc.ofInternalName("java/lang/invoke/SerializedLambda");
    private static final ClassDesc NAME_NOT_SERIALIZABLE_EXCEPTION = ClassDesc.ofInternalName("java/io/NotSerializableException");
    private static final ClassDesc DESC_OBJECTOUTPUTSTREAM = ClassDesc.ofInternalName("java/io/ObjectOutputStream");
    private static final ClassDesc DESC_OBJECTINPUTSTREAM = ClassDesc.ofInternalName("java/io/ObjectInputStream");
    private static final MethodTypeDesc DESCR_METHOD_WRITE_REPLACE = MethodTypeDesc.of(ConstantDescs.CD_Object);
    private static final MethodTypeDesc DESCR_METHOD_WRITE_OBJECT = MethodTypeDesc.of(ConstantDescs.CD_void, DESC_OBJECTOUTPUTSTREAM);
    private static final MethodTypeDesc DESCR_METHOD_READ_OBJECT = MethodTypeDesc.of(ConstantDescs.CD_void, DESC_OBJECTINPUTSTREAM);

    private static final String NAME_METHOD_WRITE_REPLACE = "writeReplace";
    private static final String NAME_METHOD_READ_OBJECT = "readObject";
    private static final String NAME_METHOD_WRITE_OBJECT = "writeObject";

    private static final MethodTypeDesc DESCR_CTOR_SERIALIZED_LAMBDA = MethodTypeDesc.of(ConstantDescs.CD_void,
            ConstantDescs.CD_Class,
            ConstantDescs.CD_String,
            ConstantDescs.CD_String,
            ConstantDescs.CD_String,
            ConstantDescs.CD_int,
            ConstantDescs.CD_String,
            ConstantDescs.CD_String,
            ConstantDescs.CD_String,
            ConstantDescs.CD_String,
            ConstantDescs.CD_Object.arrayType());

    private static final MethodTypeDesc DESCR_CTOR_NOT_SERIALIZABLE_EXCEPTION = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String);
    private static final ClassDesc SER_HOSTILE_EXCEPTIONS = NAME_NOT_SERIALIZABLE_EXCEPTION;

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    // Used to ensure that dumped class files for failed definitions have a unique class name
    private static final AtomicInteger counter = new AtomicInteger();

    // For dumping generated classes to disk, for debugging purposes
    private static final ProxyClassesDumper dumper;

    private static final boolean disableEagerInitialization;

    // condy to load implMethod from class data
    private static final DynamicConstantDesc<?> implMethodCondy;

    static {
        final String dumpProxyClassesKey = "jdk.internal.lambda.dumpProxyClasses";
        String dumpPath = GetPropertyAction.privilegedGetProperty(dumpProxyClassesKey);
        dumper = (null == dumpPath) ? null : ProxyClassesDumper.getInstance(dumpPath);

        final String disableEagerInitializationKey = "jdk.internal.lambda.disableEagerInitialization";
        disableEagerInitialization = GetBooleanAction.privilegedGetProperty(disableEagerInitializationKey);

        // condy to load implMethod from class data
//        MethodType classDataMType = MethodType.methodType(Object.class, MethodHandles.Lookup.class, String.class, Class.class);
//        Handle classDataBsm = new Handle(H_INVOKESTATIC, Type.getInternalName(MethodHandles.class), "classData",
//                                         classDataMType.descriptorString(), false);
//        ConstantDynamic implMethodCondy = new ConstantDynamic(ConstantDescs.DEFAULT_NAME, MethodHandle.class.descriptorString(), classDataBsm);
        DirectMethodHandleDesc classDataBsm = ConstantDescs.ofConstantBootstrap(ConstantDescs.CD_MethodHandles, "classData", ConstantDescs.CD_Object);
        implMethodCondy = DynamicConstantDesc.ofNamed(classDataBsm, ConstantDescs.DEFAULT_NAME, ConstantDescs.CD_MethodHandle);
    }

    // See context values in AbstractValidatingLambdaMetafactory
    private final ClassDesc implMethodClassDesc;     // Name of type containing implementation "CC"
    private final String implMethodName;             // Name of implementation method "impl"
    private final MethodTypeDesc implMethodDesc;             // Type descriptor for implementation methods "(I)Ljava/lang/String;"
    private final MethodType constructorType;        // Generated class constructor type "(CC)void"
    private final String[] argNames;                 // Generated names for the constructor arguments
    private final String[] argDescs;                 // Type descriptors for the constructor arguments
    private final String lambdaClassName;            // Generated name for the generated class "X$$Lambda$1"
    private final ClassDesc lambdaClassDesc;
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
        implMethodClassDesc = ClassDesc.ofDescriptor(implClass.descriptorString());
        implMethodName = implInfo.getName();
        implMethodDesc = MethodTypeDesc.ofDescriptor(implInfo.getMethodType().toMethodDescriptorString());
        constructorType = factoryType.changeReturnType(Void.TYPE);
        lambdaClassName = lambdaClassName(targetClass);
        lambdaClassDesc = ClassDesc.ofInternalName(lambdaClassName);
        // If the target class invokes a protected method inherited from a
        // superclass in a different package, or does 'invokespecial', the
        // lambda class has no access to the resolved method. Instead, we need
        // to pass the live implementation method handle to the proxy class
        // to invoke directly. (javac prefers to avoid this situation by
        // generating bridges in the target class)
        useImplMethodHandle = (Modifier.isProtected(implInfo.getModifiers()) &&
                               !VerifyAccess.isSamePackage(targetClass, implInfo.getDeclaringClass())) ||
                               implKind == MethodHandleInfo.REF_invokeSpecial;
        int parameterCount = factoryType.parameterCount();
        if (parameterCount > 0) {
            argNames = new String[parameterCount];
            argDescs = new String[parameterCount];
            for (int i = 0; i < parameterCount; i++) {
                argNames[i] = "arg$" + (i + 1);
                argDescs[i] = BytecodeDescriptor.unparse(factoryType.parameterType(i));
            }
        } else {
            argNames = argDescs = EMPTY_STRING_ARRAY;
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
        ClassDesc interfaceDesc = ClassDesc.ofDescriptor(interfaceClass.descriptorString());
        boolean accidentallySerializable = !isSerializable && Serializable.class.isAssignableFrom(interfaceClass);
        if (altInterfaces.length == 0) {
            interfaces = List.of(interfaceDesc);
        } else {
            // Assure no duplicate interfaces (ClassFormatError)
            Set<ClassDesc> itfs = LinkedHashSet.newLinkedHashSet(altInterfaces.length + 1);
            itfs.add(interfaceDesc);
            for (Class<?> i : altInterfaces) {
                itfs.add(ClassDesc.ofDescriptor(i.descriptorString()));
                accidentallySerializable |= !isSerializable && Serializable.class.isAssignableFrom(i);
            }
            interfaces = new ArrayList<>(itfs);
        }
        final boolean finalAccidentallySerializable = accidentallySerializable;
        final byte[] classBytes = Classfile.build(lambdaClassDesc, new Consumer<ClassBuilder>() {
            @Override
            public void accept(ClassBuilder clb) {
                clb.withFlags(ACC_SUPER + ACC_FINAL + ACC_SYNTHETIC);
                clb.withInterfaceSymbols(interfaces);
                // Generate final fields to be filled in by constructor
                for (int i = 0; i < argDescs.length; i++) {
                    clb.withVersion(CLASSFILE_VERSION, 0);
                    clb.withField(argNames[i], ClassDesc.ofDescriptor(argDescs[i]), new Consumer<FieldBuilder>() {
                        @Override
                        public void accept(FieldBuilder fb) {
                            fb.withFlags(ACC_PRIVATE + ACC_FINAL);
                        }
                    });
                }

                generateConstructor(clb);

                if (factoryType.parameterCount() == 0 && disableEagerInitialization) {
                    generateClassInitializer(clb);
                }

                // Forward the SAM method
                clb.withMethod(interfaceMethodName,
                        MethodTypeDesc.ofDescriptor(interfaceMethodType.toMethodDescriptorString()),
                        ACC_PUBLIC,
                        new Consumer<MethodBuilder>() {
                    @Override
                    public void accept(MethodBuilder mb) {
                        mb.withFlags(ACC_PUBLIC);
                        generateForwardingMethod(mb, interfaceMethodType);
                    }
                });

                // Forward the bridges
                if (altMethods != null) {
                    for (MethodType mt : altMethods) {
                        clb.withMethod(interfaceMethodName,
                                MethodTypeDesc.ofDescriptor(mt.toMethodDescriptorString()),
                                ACC_PUBLIC|ACC_BRIDGE,
                                new Consumer<MethodBuilder>() {
                            @Override
                            public void accept(MethodBuilder mb) {
                                mb.withFlags(ACC_PUBLIC|ACC_BRIDGE);
                                generateForwardingMethod(mb, mt);
                            }
                        });
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
            Lookup lookup = null;
            try {
                if (useImplMethodHandle) {
                    lookup = caller.defineHiddenClassWithClassData(classBytes, implementation, !disableEagerInitialization,
                                                                   NESTMATE, STRONG);
                } else {
                    lookup = caller.defineHiddenClass(classBytes, !disableEagerInitialization, NESTMATE, STRONG);
                }
                return lookup.lookupClass();
            } finally {
                // If requested, dump out to a file for debugging purposes
                if (dumper != null) {
                    String name;
                    if (lookup != null) {
                        String definedName = lookup.lookupClass().getName();
                        int suffixIdx = definedName.lastIndexOf('/');
                        assert suffixIdx != -1;
                        name = lambdaClassName + '.' + definedName.substring(suffixIdx + 1);
                    } else {
                        name = lambdaClassName + ".failed-" + counter.incrementAndGet();
                    }
                    doDump(name, classBytes);
                }
            }
        } catch (IllegalAccessException e) {
            throw new LambdaConversionException("Exception defining lambda proxy class", e);
        } catch (Throwable t) {
            throw new InternalError(t);
        }
    }

    @SuppressWarnings("removal")
    private void doDump(final String className, final byte[] classBytes) {
        AccessController.doPrivileged(new PrivilegedAction<>() {
            @Override
            public Void run() {
                dumper.dumpClass(className, classBytes);
                return null;
            }
        }, null,
        new FilePermission("<<ALL FILES>>", "read, write"),
        // createDirectories may need it
        new PropertyPermission("user.dir", "read"));
    }

    /**
     * Generate a static field and a static initializer that sets this field to an instance of the lambda
     */
    private void generateClassInitializer(ClassBuilder clb) {
        ClassDesc lambdaTypeDescriptor = ClassDesc.ofDescriptor(factoryType.returnType().descriptorString());

        // Generate the static final field that holds the lambda singleton
        clb.withField(LAMBDA_INSTANCE_FIELD, lambdaTypeDescriptor,
                new Consumer<FieldBuilder>() {
            @Override
            public void accept(FieldBuilder fb) {
                fb.withFlags(ACC_PRIVATE | ACC_STATIC | ACC_FINAL);
            }
        });

        // Instantiate the lambda and store it to the static final field
        clb.withMethod("<clinit>", MethodTypeDesc.of(ConstantDescs.CD_void), ACC_STATIC, new Consumer<MethodBuilder>() {
            @Override
            public void accept(MethodBuilder mb) {
                mb.withFlags(ACC_STATIC);
                mb.withCode(new Consumer<CodeBuilder>() {
                    @Override
                    public void accept(CodeBuilder cob) {
                        cob.new_(lambdaClassDesc);
                        cob.stackInstruction(Opcode.DUP);
                        assert factoryType.parameterCount() == 0;
                        cob.invokeInstruction(Opcode.INVOKESPECIAL, lambdaClassDesc, NAME_CTOR, MethodTypeDesc.ofDescriptor(constructorType.toMethodDescriptorString()), false);
                        cob.fieldInstruction(Opcode.PUTSTATIC, lambdaClassDesc, LAMBDA_INSTANCE_FIELD, lambdaTypeDescriptor);

                        cob.returnInstruction(TypeKind.VoidType);
                    }
                }); }
        });
    }

    /**
     * Generate the constructor for the class
     */
    private void generateConstructor(ClassBuilder clb) {
        // Generate constructor
        clb.withMethod(NAME_CTOR, MethodTypeDesc.ofDescriptor(constructorType.toMethodDescriptorString()), ACC_PRIVATE,
                new Consumer<MethodBuilder>() {
            @Override
            public void accept(MethodBuilder mb) {
                mb.withFlags(ACC_PRIVATE);
                mb.withCode(new Consumer<CodeBuilder>() {
                    @Override
                    public void accept(CodeBuilder cob) {
                        cob.loadInstruction(TypeKind.ReferenceType, 0);
                        cob.invokeInstruction(Opcode.INVOKESPECIAL, ConstantDescs.CD_Object, NAME_CTOR,
                                MethodTypeDesc.of(ConstantDescs.CD_void), false);
                        int parameterCount = factoryType.parameterCount();
                        for (int i = 0, lvIndex = 0; i < parameterCount; i++) {
                            cob.loadInstruction(TypeKind.ReferenceType, 0);
                            Class<?> argType = factoryType.parameterType(i);
                            cob.loadInstruction(getLoadType(argType), lvIndex + 1);
                            lvIndex += getParameterSize(argType);
                            cob.fieldInstruction(Opcode.PUTFIELD, lambdaClassDesc, argNames[i], ClassDesc.ofDescriptor(argDescs[i]));
                        }
                        cob.returnInstruction(TypeKind.VoidType);
                    }
                }); }
        });
    }

    /**
     * Generate a writeReplace method that supports serialization
     */
    private void generateSerializationFriendlyMethods(ClassBuilder clb) {
        clb.withMethod(NAME_METHOD_WRITE_REPLACE, DESCR_METHOD_WRITE_REPLACE, ACC_PRIVATE + ACC_FINAL,
                new Consumer<MethodBuilder>() {
            @Override
            public void accept(MethodBuilder mb) {
                mb.withFlags(ACC_PRIVATE + ACC_FINAL);
                mb.withCode(new Consumer<CodeBuilder>() {
                    @Override
                    public void accept(CodeBuilder cob) {
                        cob.new_(NAME_SERIALIZED_LAMBDA);
                        cob.stackInstruction(Opcode.DUP);
                        cob.constantInstruction(Opcode.LDC, targetClass.describeConstable().get());
                        cob.constantInstruction(Opcode.LDC, factoryType.returnType().getName().replace('.', '/'));
                        cob.constantInstruction(Opcode.LDC, interfaceMethodName);
                        cob.constantInstruction(Opcode.LDC, interfaceMethodType.toMethodDescriptorString());
                        cob.constantInstruction(Opcode.LDC, implInfo.getReferenceKind());
                        cob.constantInstruction(Opcode.LDC, implInfo.getDeclaringClass().getName().replace('.', '/'));
                        cob.constantInstruction(Opcode.LDC, implInfo.getName());
                        cob.constantInstruction(Opcode.LDC, implInfo.getMethodType().toMethodDescriptorString());
                        cob.constantInstruction(Opcode.LDC, dynamicMethodType.toMethodDescriptorString());
                        cob.constantInstruction(argDescs.length);

                        cob.anewarray(ConstantDescs.CD_Object);
                        for (int i = 0; i < argDescs.length; i++) {
                            cob.stackInstruction(Opcode.DUP);
                            cob.constantInstruction(i);
                            cob.loadInstruction(TypeKind.ReferenceType, 0);
                            cob.fieldInstruction(Opcode.GETFIELD, lambdaClassDesc, argNames[i], ClassDesc.ofDescriptor(argDescs[i]));
                            TypeConvertingMethodAdapter.boxIfTypePrimitive(cob, TypeKind.fromDescriptor(argDescs[i]));
                            cob.arrayStoreInstruction(TypeKind.ReferenceType);
                        }
                        cob.invokeInstruction(Opcode.INVOKESPECIAL, NAME_SERIALIZED_LAMBDA, NAME_CTOR,
                                DESCR_CTOR_SERIALIZED_LAMBDA, false);
                        cob.returnInstruction(TypeKind.ReferenceType);
                    }
                }); }
        });
    }

    /**
     * Generate a readObject/writeObject method that is hostile to serialization
     */
    private void generateSerializationHostileMethods(ClassBuilder clb) {
        clb.withMethod(NAME_METHOD_WRITE_OBJECT, DESCR_METHOD_WRITE_OBJECT, ACC_PRIVATE + ACC_FINAL,
            new Consumer<MethodBuilder>() {
            @Override
            public void accept(MethodBuilder mb) {
                mb.withFlags(ACC_PRIVATE + ACC_FINAL);
                mb.with(ExceptionsAttribute.of(mb.constantPool().classEntry(SER_HOSTILE_EXCEPTIONS)));
                mb.withCode(new Consumer<CodeBuilder>() {
                    @Override
                    public void accept(CodeBuilder cob) {
                        cob.new_(NAME_NOT_SERIALIZABLE_EXCEPTION);
                        cob.stackInstruction(Opcode.DUP);
                        cob.constantInstruction(Opcode.LDC, "Non-serializable lambda");
                        cob.invokeInstruction(Opcode.INVOKESPECIAL, NAME_NOT_SERIALIZABLE_EXCEPTION, NAME_CTOR,
                                DESCR_CTOR_NOT_SERIALIZABLE_EXCEPTION, false);
                        cob.throwInstruction();
                    }
                });
            }
        });
        clb.withMethod(NAME_METHOD_READ_OBJECT, DESCR_METHOD_READ_OBJECT, ACC_PRIVATE + ACC_FINAL,
            new Consumer<MethodBuilder>() {
            @Override
            public void accept(MethodBuilder mb) {
                mb.withFlags(ACC_PRIVATE + ACC_FINAL);
                mb.with(ExceptionsAttribute.of(mb.constantPool().classEntry(SER_HOSTILE_EXCEPTIONS)));
                mb.withCode(new Consumer<CodeBuilder>() {
                    @Override
                    public void accept(CodeBuilder cob) {
                        cob.new_(NAME_NOT_SERIALIZABLE_EXCEPTION);
                        cob.stackInstruction(Opcode.DUP);
                        cob.constantInstruction(Opcode.LDC, "Non-serializable lambda");
                        cob.invokeInstruction(Opcode.INVOKESPECIAL, NAME_NOT_SERIALIZABLE_EXCEPTION, NAME_CTOR,
                                DESCR_CTOR_NOT_SERIALIZABLE_EXCEPTION, false);
                        cob.throwInstruction();
                    }
                });
            }
        });
    }

    /**
     * This method generates a method body which calls the lambda implementation
     * method, converting arguments, as needed.
     */
    void generateForwardingMethod(MethodBuilder mb, MethodType methodType) {
        mb.withCode(new Consumer<CodeBuilder>() {
            @Override
            public void accept(CodeBuilder cob) {
                if (implKind == MethodHandleInfo.REF_newInvokeSpecial) {
                    cob.new_(implMethodClassDesc);
                    cob.stackInstruction(Opcode.DUP);
                }
                if (useImplMethodHandle) {
                    cob.constantInstruction(Opcode.LDC, implMethodCondy);
                }
                for (int i = 0; i < argNames.length; i++) {
                    cob.loadInstruction(TypeKind.ReferenceType, 0);
                    cob.fieldInstruction(Opcode.GETFIELD, lambdaClassDesc, argNames[i], ClassDesc.ofDescriptor(argDescs[i]));
                }

                convertArgumentTypes(cob, methodType);

                if (useImplMethodHandle) {
                    MethodType mtype = implInfo.getMethodType();
                    if (implKind != MethodHandleInfo.REF_invokeStatic) {
                        mtype = mtype.insertParameterTypes(0, implClass);
                    }
                    cob.invokeInstruction(Opcode.INVOKEVIRTUAL, ConstantDescs.CD_MethodHandle,
                            "invokeExact", MethodTypeDesc.ofDescriptor(mtype.descriptorString()), false);
                } else {
                    // Invoke the method we want to forward to
                    cob.invokeInstruction(invocationOpcode(), implMethodClassDesc,
                            implMethodName, implMethodDesc,
                            implClass.isInterface());
                }
                // Convert the return value (if any) and return it
                // Note: if adapting from non-void to void, the 'return'
                // instruction will pop the unneeded result
                Class<?> implReturnClass = implMethodType.returnType();
                Class<?> samReturnClass = methodType.returnType();
                TypeConvertingMethodAdapter.convertType(cob,implReturnClass, samReturnClass, samReturnClass);
                cob.returnInstruction(getReturnOpcode(samReturnClass));
            }
        });
    }

    private void convertArgumentTypes(CodeBuilder cob, MethodType samType) {
        int lvIndex = 0;
        int samParametersLength = samType.parameterCount();
        int captureArity = factoryType.parameterCount();
        for (int i = 0; i < samParametersLength; i++) {
            Class<?> argType = samType.parameterType(i);
            cob.loadInstruction(getLoadType(argType), lvIndex + 1);
            lvIndex += getParameterSize(argType);
            TypeConvertingMethodAdapter.convertType(cob, argType, implMethodType.parameterType(captureArity + i), dynamicMethodType.parameterType(i));
        }
    }

    private Opcode invocationOpcode() throws InternalError {
        return switch (implKind) {
            case MethodHandleInfo.REF_invokeStatic ->
                Opcode.INVOKESTATIC;
            case MethodHandleInfo.REF_newInvokeSpecial ->
                Opcode.INVOKESPECIAL;
             case MethodHandleInfo.REF_invokeVirtual ->
                Opcode.INVOKEVIRTUAL;
            case MethodHandleInfo.REF_invokeInterface ->
                Opcode.INVOKEINTERFACE;
            case MethodHandleInfo.REF_invokeSpecial ->
                Opcode.INVOKESPECIAL;
            default ->
                throw new InternalError("Unexpected invocation kind: " + implKind);
        };
    }

    static int getParameterSize(Class<?> c) {
        if (c == Void.TYPE) {
            return 0;
        } else if (c == Long.TYPE || c == Double.TYPE) {
            return 2;
        }
        return 1;
    }

    static TypeKind getLoadType(Class<?> c) {
        if(c == Void.TYPE) {
            throw new InternalError("Unexpected void type of load opcode");
        }
        return getClassTypeKind(c);
    }

    static TypeKind getReturnOpcode(Class<?> c) {
        if(c == Void.TYPE) {
            return TypeKind.VoidType;
        }
        return getClassTypeKind(c);
    }

    private static TypeKind getClassTypeKind(Class<?> c) {
        if (c.isPrimitive()) {
            if (c == Long.TYPE) {
                return TypeKind.LongType;
            } else if (c == Float.TYPE) {
                return TypeKind.FloatType;
            } else if (c == Double.TYPE) {
                return TypeKind.DoubleType;
            }
            return TypeKind.IntType;
        } else {
            return TypeKind.ReferenceType;
        }
    }

}
