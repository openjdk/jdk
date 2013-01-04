/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime.linker;

import static jdk.internal.org.objectweb.asm.Opcodes.ACC_FINAL;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_STATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_SUPER;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_VARARGS;
import static jdk.internal.org.objectweb.asm.Opcodes.ACONST_NULL;
import static jdk.internal.org.objectweb.asm.Opcodes.ALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.ARETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.ASTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.DUP;
import static jdk.internal.org.objectweb.asm.Opcodes.IFNONNULL;
import static jdk.internal.org.objectweb.asm.Opcodes.ILOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.ISTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.RETURN;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.linker.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.AllPermission;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.SecureClassLoader;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.commons.InstructionAdapter;
import jdk.nashorn.internal.objects.NativeJava;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ECMAException;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.Undefined;
import org.dynalang.dynalink.beans.StaticClass;
import org.dynalang.dynalink.support.LinkRequestImpl;

/**
 * A factory class that generates adapter classes. Adapter classes allow implementation of Java interfaces and
 * extending of Java classes from JavaScript. For every original Class object, exactly one adapter Class is generated
 * that either extends the original class or - if the original Class represents an interface - extends Object and
 * implements the interface represented by the original Class.
 * </p><p>
 * The adapter class is generated in a new secure class loader that inherits Nashorn's protection domain, and has either
 * the original Class' class loader or the Nashorn's class loader as its parent - the parent class loader is chosen so
 * that both the original Class and the Nashorn core classes are visible from it (as the adapter will have constant pool
 * references to ScriptObject and ScriptFunction classes). In case neither candidate class loader has visibility into
 * the other set of classes, an error is thrown.
 * </p><p>
 * For every protected or public constructor in the extended class (which is either the original class, or Object when
 * an interface is implemented), the adapter class will have one or two public constructors (visibility of protected
 * constructors in the extended class is promoted to public). In every case, for every original constructor, a new
 * constructor taking an initial ScriptObject argument followed by original constructor arguments is present on the
 * adapter class. When such a constructor is invoked, the passed ScriptObject's member functions are used to implement
 * and/or override methods on the original class, dispatched by name. A single JavaScript function will act as the
 * implementation for all overloaded methods of the same name. When methods on an adapter instance are invoked, the
 * functions are invoked having the ScriptObject passed in the instance constructor as their "this". Subsequent changes
 * to the ScriptObject (reassignment or removal of its functions) are not reflected in the adapter instance; the method
 * implementations are bound to functions at constructor invocation time. {@code java.lang.Object} methods
 * {@code equals}, {@code hashCode}, and {@code toString} can also be overridden (from interface implementations too).
 * The only restriction is that since every JavaScript object already has a {@code toString} function through the
 * {@code Object.prototype}, the {@code toString} in the adapter is only overridden if the passed ScriptObject has a
 * {@code toString} function as its own property, and not inherited from a prototype. All other adapter methods can be
 * implemented or overridden through a prototype-inherited function of the ScriptObject passed to the constructor too.
 * </p><p>
 * For abstract classes or interfaces that only have one abstract method, or have several of them, but all share the
 * same name, an additional constructor is provided for every original constructor; this one takes a ScriptFunction as
 * its first argument followed by original constructor arguments. This constructor will use the passed function as the
 * implementation for all abstract methods. For consistency, any concrete methods sharing the single abstract method
 * name will also be overridden by the function. When methods on the adapter instance are invoked, the ScriptFunction is
 * invoked with {@code null} as its "this".
 * </p><p>If the superclass has a protected or public default constructor, then a generated constructor that only takes
 * a ScriptFunction is also implicitly used as an automatic conversion whenever a ScriptFunction is passed in an
 * invocation of any Java method that expects such SAM type.
 * </p><p>
 * For adapter methods that return values, all the JavaScript-to-Java conversions supported by Nashorn will be in effect
 * to coerce the JavaScript function return value to the expected Java return type.
 * </p><p>
 * You normally don't use this class directly, but rather either create adapters from script using
 * {@link NativeJava#extend(Object, Object)}, using the {@code new} operator on abstract classes and interfaces (see
 * {@link NativeJava#type(Object, Object)}), or implicitly when passing script functions to Java methods expecting SAM
 * types.
 * </p>
 */

public class JavaAdapterFactory {
    private static final Type SCRIPT_FUNCTION_TYPE = Type.getType(ScriptFunction.class);
    private static final Type SCRIPT_OBJECT_TYPE = Type.getType(ScriptObject.class);
    private static final Type OBJECT_TYPE = Type.getType(Object.class);
    private static final Type STRING_TYPE = Type.getType(String.class);
    private static final Type CONTEXT_TYPE = Type.getType(Context.class);
    private static final Type METHOD_TYPE_TYPE = Type.getType(MethodType.class);
    private static final Type METHOD_HANDLE_TYPE = Type.getType(MethodHandle.class);
    private static final String GET_HANDLE_OBJECT_DESCRIPTOR = Type.getMethodDescriptor(METHOD_HANDLE_TYPE,
            SCRIPT_OBJECT_TYPE, STRING_TYPE, METHOD_TYPE_TYPE, Type.BOOLEAN_TYPE);
    private static final String GET_HANDLE_FUNCTION_DESCRIPTOR = Type.getMethodDescriptor(METHOD_HANDLE_TYPE,
            SCRIPT_FUNCTION_TYPE, METHOD_TYPE_TYPE, Type.BOOLEAN_TYPE);
    private static final Type RUNTIME_EXCEPTION_TYPE = Type.getType(RuntimeException.class);
    private static final Type THROWABLE_TYPE = Type.getType(Throwable.class);
    private static final Type PRIVILEGED_ACTION_TYPE = Type.getType(PrivilegedAction.class);
    private static final Type UNSUPPORTED_OPERATION_TYPE = Type.getType(UnsupportedOperationException.class);

    private static final String THIS_CLASS_TYPE_NAME = Type.getInternalName(JavaAdapterFactory.class);
    private static final String RUNTIME_EXCEPTION_TYPE_NAME = RUNTIME_EXCEPTION_TYPE.getInternalName();
    private static final String ERROR_TYPE_NAME = Type.getInternalName(Error.class);
    private static final String THROWABLE_TYPE_NAME = THROWABLE_TYPE.getInternalName();
    private static final String CONTEXT_TYPE_NAME = CONTEXT_TYPE.getInternalName();
    private static final String OBJECT_TYPE_NAME = OBJECT_TYPE.getInternalName();
    private static final String PRIVILEGED_ACTION_TYPE_NAME = PRIVILEGED_ACTION_TYPE.getInternalName();
    private static final String UNSUPPORTED_OPERATION_TYPE_NAME = UNSUPPORTED_OPERATION_TYPE.getInternalName();

    private static final String METHOD_HANDLE_TYPE_DESCRIPTOR = METHOD_HANDLE_TYPE.getDescriptor();
    private static final String SCRIPT_OBJECT_TYPE_DESCRIPTOR = SCRIPT_OBJECT_TYPE.getDescriptor();
    private static final String GET_GLOBAL_METHOD_DESCRIPTOR = Type.getMethodDescriptor(SCRIPT_OBJECT_TYPE);
    private static final String SET_GLOBAL_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.VOID_TYPE, SCRIPT_OBJECT_TYPE);
    private static final String GET_CLASS_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(Class.class));
    private static final String PRIVILEGED_RUN_METHOD_DESCRIPTOR = Type.getMethodDescriptor(OBJECT_TYPE);

    // Package used when the adapter can't be defined in the adaptee's package (either because it's sealed, or because
    // it's a java.* package.
    private static final String ADAPTER_PACKAGE_PREFIX = "jdk/nashorn/internal/javaadapters/";
    // Class name suffix used to append to the adaptee class name, when it can be defined in the adaptee's package.
    private static final String ADAPTER_CLASS_NAME_SUFFIX = "$$NashornJavaAdapter";

    private static final String JAVA_PACKAGE_PREFIX = "java/";
    private static final String INIT = "<init>";
    private static final String VOID_NOARG = Type.getMethodDescriptor(Type.VOID_TYPE);
    private static final String GLOBAL_FIELD_NAME = "global";

    /**
     * Contains various outcomes for attempting to generate an adapter class. These are stored in AdapterInfo instances.
     * We have a successful outcome (adapter class was generated) and three possible error outcomes: a class is final,
     * a class is not public, and the class has no public or protected constructor. We don't throw exceptions when we
     * try to generate the adapter, but rather just record these error conditions as they are still useful as partial
     * outcomes, as Nashorn's linker can still successfully check whether the class can be autoconverted from a script
     * function even when it is not possible to generate an adapter for it.
     */
    private enum AdaptationOutcome {
        SUCCESS,
        ERROR_FINAL_CLASS,
        ERROR_NON_PUBLIC_CLASS,
        ERROR_NO_ACCESSIBLE_CONSTRUCTOR
    }

    /**
     * Collection of methods we never override: Object.clone(), Object.finalize().
     */
    private static final Collection<MethodInfo> EXCLUDED = getExcludedMethods();

    /**
     * A mapping from an original Class object to AdapterInfo representing the adapter for the class it represents.
     */
    private static final ClassValue<AdapterInfo> ADAPTER_INFOS = new ClassValue<AdapterInfo>() {
        @Override
        protected AdapterInfo computeValue(final Class<?> type) {
            return createAdapterInfo(type);
        }
    };

    private static final Random random = new SecureRandom();
    private static final ProtectionDomain GENERATED_PROTECTION_DOMAIN = createGeneratedProtectionDomain();

    // This is the supertype for our generated adapter. It's either Object if we're implementing an interface, or same
    // as originalType if we're extending a class.
    private final Class<?> superType;
    // Class loader used as the parent for the class loader we'll create to load the generated class. It will be a class
    // loader that has the visibility of both the original type and of the Nashorn classes.
    private final ClassLoader commonLoader;

    // Binary name of the superType
    private final String superTypeName;
    // Binary name of the generated class.
    private final String generatedTypeName;
    private final String globalSetterClassName;
    private final Set<String> usedFieldNames = new HashSet<>();
    private final Set<String> abstractMethodNames = new HashSet<>();
    private final String samName;
    private final Set<MethodInfo> finalMethods = new HashSet<>(EXCLUDED);
    private final Set<MethodInfo> methodInfos = new HashSet<>();
    private boolean autoConvertibleFromFunction = false;

    private final ClassWriter cw;

    /**
     * Creates a factory that will produce the adapter type for the specified original type.
     * @param originalType the type for which this factory will generate the adapter type.
     * @throws AdaptationException if the adapter can not be generated for some reason.
     */
    private JavaAdapterFactory(final Class<?> originalType) throws AdaptationException {
        this.commonLoader = findCommonLoader(originalType);
        cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(final String type1, final String type2) {
                // We need to override ClassWriter.getCommonSuperClass to use this factory's commonLoader as a class
                // loader to find the common superclass of two types when needed.
                return JavaAdapterFactory.this.getCommonSuperClass(type1, type2);
            }
        };
        final String originalTypeName = Type.getInternalName(originalType);
        final String[] interfaces;
        final boolean isInterface = originalType.isInterface();
        if (isInterface) {
            superType = Object.class;
            interfaces = new String[] { originalTypeName };
        } else {
            superType = originalType;
            interfaces = null;
        }
        superTypeName = Type.getInternalName(superType);
        final Package pkg = originalType.getPackage();
        if (originalTypeName.startsWith(JAVA_PACKAGE_PREFIX) || pkg == null || pkg.isSealed()) {
            // Can't define new classes in java.* packages
            generatedTypeName = ADAPTER_PACKAGE_PREFIX + originalTypeName;
        } else {
            generatedTypeName = originalTypeName + ADAPTER_CLASS_NAME_SUFFIX;
        }
        // Randomize the name of the privileged global setter, to make it non-feasible to find.
        final long l;
        synchronized(random) {
            l = random.nextLong();
        }
        globalSetterClassName = generatedTypeName.concat("$" + Long.toHexString(l & Long.MAX_VALUE));
        cw.visit(Opcodes.V1_7, ACC_PUBLIC | ACC_SUPER | ACC_FINAL, generatedTypeName, null, superTypeName, interfaces);
        cw.visitField(ACC_PRIVATE | ACC_FINAL, GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR, null, null).visitEnd();
        usedFieldNames.add(GLOBAL_FIELD_NAME);

        gatherMethods(originalType);
        if (isInterface) {
            // Add ability to override Object methods if implementing an interface
            gatherMethods(Object.class);
        }
        samName = abstractMethodNames.size() == 1 ? abstractMethodNames.iterator().next() : null;
        generateFields();
        generateConstructors();
        generateMethods();
        // }
        cw.visitEnd();
    }

    /**
     * Utility method used by few other places in the code. Tests if the class has the abstract modifier and is not an
     * array class. For some reason, array classes have the abstract modifier set in HotSpot JVM, and we don't want to
     * treat array classes as abstract.
     * @param clazz the inspected class
     * @return true if the class is abstract and is not an array type.
     */
    static boolean isAbstractClass(final Class<?> clazz) {
        return Modifier.isAbstract(clazz.getModifiers()) && !clazz.isArray();
    }

    /**
     * Returns an adapter class for the specified original class. The adapter class extends/implements the original
     * class/interface.
     * @param originalClass the original class/interface to extend/implement.
     * @return an adapter class. See this class' documentation for details on the generated adapter class.
     * @throws ECMAException with a TypeError if the adapter class can not be generated because the original class is
     * final, non-public, or has no public or protected constructors.
     */
    public static StaticClass getAdapterClassFor(final StaticClass originalClass) {
        return getAdapterClassFor(originalClass.getRepresentedClass());
    }

    static StaticClass getAdapterClassFor(final Class<?> originalClass) {
        final AdapterInfo adapterInfo = ADAPTER_INFOS.get(originalClass);
        final StaticClass clazz = adapterInfo.adapterClass;
        if (clazz != null) {
            return clazz;
        }
        assert adapterInfo.adaptationOutcome != AdaptationOutcome.SUCCESS;
        typeError(Context.getGlobal(), "extend." + adapterInfo.adaptationOutcome, originalClass.getName());

        throw new AssertionError();
    }

    /**
     * Returns whether an instance of the specified class/interface can be generated from a ScriptFunction. Returns true
     * iff: the adapter for the class/interface can be created, it is abstract (this includes interfaces), it has at
     * least one abstract method, all the abstract methods share the same name, and it has a public or protected default
     * constructor. Note that invoking this class will most likely result in the adapter class being defined in the JVM
     * if it hasn't been already.
     * @param clazz the inspected class
     * @return true iff an instance of the specified class/interface can be generated from a ScriptFunction.
     */
    static boolean isAutoConvertibleFromFunction(final Class<?> clazz) {
        return ADAPTER_INFOS.get(clazz).autoConvertibleFromFunction;
    }

    /**
     * Returns a method handle representing a constructor that takes a single argument of the source type (which,
     * really, should be one of {@link ScriptObject}, {@link ScriptFunction}, or {@link Object}, and returns an instance
     * of the adapter for the target type. Used to implement the function autoconverters as well as the Nashorn's
     * JSR-223 script engine's {@code getInterface()} method.
     * @param sourceType the source type; should be either {@link ScriptObject}, {@link ScriptFunction}, or
     * {@link Object}. In case of {@code Object}, it will return a method handle that dispatches to either the script
     * object or function constructor at invocation based on the actual argument.
     * @param targetType the target type, for which adapter instances will be created
     * @return the constructor method handle.
     * @throws Exception if anything goes wrong
     */
    public static MethodHandle getConstructor(final Class<?> sourceType, final Class<?> targetType) throws Exception {
        final StaticClass adapterClass = getAdapterClassFor(targetType);
        return MH.bindTo(Bootstrap.getLinkerServices().getGuardedInvocation(new LinkRequestImpl(NashornCallSiteDescriptor.get(
                "dyn:new", MethodType.methodType(targetType, StaticClass.class, sourceType), 0), false,
                adapterClass, null)).getInvocation(), adapterClass);
    }

    /**
     * Finishes the bytecode generation for the adapter class that was started in the constructor, and loads the
     * bytecode as a new class into the JVM.
     * @return the generated adapter class
     */
    private Class<?> generateClass() {
        final String binaryName = generatedTypeName.replace('/', '.');
        try {
            return Class.forName(binaryName, true, createClassLoader(commonLoader, binaryName, cw.toByteArray(),
                    globalSetterClassName.replace('/', '.')));
        } catch (final ClassNotFoundException e) {
            throw new AssertionError(e); // cannot happen
        }
    }

    // Creation of class loader is in a separate static method so that it doesn't retain a reference to the factory
    // instance. Note that the adapter class is created in the protection domain of the class/interface being
    // extended/implemented, and only the privileged global setter action class is generated in the protection domain
    // of Nashorn itself. Also note that the creation and loading of the global setter is deferred until it is
    // required by JVM linker, which will only happen on first invocation of any of the adapted method. We could defer
    // it even more by separating its invocation into a separate static method on the adapter class, but then someone
    // with ability to introspect on the class and use setAccessible(true) on it could invoke the method. It's a
    // security tradeoff...
    private static ClassLoader createClassLoader(final ClassLoader parentLoader, final String className,
            final byte[] classBytes, final String privilegedActionClassName) {
        return new SecureClassLoader(parentLoader) {
            @Override
            protected Class<?> findClass(final String name) throws ClassNotFoundException {
                if(name.equals(className)) {
                    final byte[] bytes = classBytes;
                    return defineClass(name, bytes, 0, bytes.length, GENERATED_PROTECTION_DOMAIN);
                } else if(name.equals(privilegedActionClassName)) {
                    final byte[] bytes = generatePrivilegedActionClassBytes(privilegedActionClassName.replace('.', '/'));
                    return defineClass(name, bytes, 0, bytes.length, getClass().getProtectionDomain());
                } else {
                    throw new ClassNotFoundException(name);
                }
            }
        };
    }

    /**
     * Generates a PrivilegedAction implementation class for invoking {@link Context#setGlobal(ScriptObject)} from the
     * adapter class.
     */
    private static byte[] generatePrivilegedActionClassBytes(final String className) {
        final ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        // class GlobalSetter implements PrivilegedAction {
        w.visit(Opcodes.V1_7, ACC_SUPER | ACC_FINAL, className, null, OBJECT_TYPE_NAME, new String[] {
                PRIVILEGED_ACTION_TYPE_NAME
        });

        // private final ScriptObject global;
        w.visitField(ACC_PRIVATE | ACC_FINAL, GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR, null, null).visitEnd();

        // private ContextSetter(ScriptObject global) {
        InstructionAdapter mv = new InstructionAdapter(w.visitMethod(ACC_PRIVATE, INIT,
                SET_GLOBAL_METHOD_DESCRIPTOR, null, new String[0]));
        mv.visitCode();
        // super();
        mv.visitVarInsn(ALOAD, 0);
        mv.invokespecial(OBJECT_TYPE_NAME, INIT, VOID_NOARG);
        // this.global = global;
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.putfield(className, GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);

        mv.visitInsn(RETURN);
        mv.visitEnd();
        mv.visitMaxs(0, 0);

        // public Object run() {
        mv = new InstructionAdapter(w.visitMethod(ACC_PUBLIC, "run", PRIVILEGED_RUN_METHOD_DESCRIPTOR, null,
                new String[0]));
        mv.visitCode();
        // Context.setGlobal(this.global);
        mv.visitVarInsn(ALOAD, 0);
        mv.getfield(className, GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);
        mv.invokestatic(CONTEXT_TYPE_NAME, "setGlobal", SET_GLOBAL_METHOD_DESCRIPTOR);
        // return null;
        mv.visitInsn(ACONST_NULL);
        mv.visitInsn(ARETURN);

        mv.visitEnd();
        mv.visitMaxs(0, 0);

        // static void setGlobal(ScriptObject global) {
        mv = new InstructionAdapter(w.visitMethod(ACC_STATIC, "setGlobal", SET_GLOBAL_METHOD_DESCRIPTOR, null,
                new String[0]));
        mv.visitCode();
        // new GlobalSetter(ScriptObject global)
        mv.anew(Type.getType("L" + className + ";"));
        mv.dup();
        mv.visitVarInsn(ALOAD, 0);
        mv.invokespecial(className, INIT, SET_GLOBAL_METHOD_DESCRIPTOR);
        // AccessController.doPrivileged(...)
        mv.invokestatic(Type.getInternalName(AccessController.class), "doPrivileged", Type.getMethodDescriptor(
                OBJECT_TYPE, PRIVILEGED_ACTION_TYPE));
        mv.pop();
        mv.visitInsn(RETURN);

        mv.visitEnd();
        mv.visitMaxs(0, 0);

        return w.toByteArray();
    }

    private void generateFields() {
        for (final MethodInfo mi: methodInfos) {
            cw.visitField(ACC_PRIVATE | ACC_FINAL, mi.methodHandleFieldName, METHOD_HANDLE_TYPE_DESCRIPTOR, null, null).visitEnd();
        }
    }

    private void generateConstructors() throws AdaptationException {
        boolean gotCtor = false;
        for (final Constructor<?> ctor: superType.getDeclaredConstructors()) {
            final int modifier = ctor.getModifiers();
            if((modifier & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0) {
                generateConstructor(ctor);
                gotCtor = true;
            }
        }
        if(!gotCtor) {
            throw new AdaptationException(AdaptationOutcome.ERROR_NO_ACCESSIBLE_CONSTRUCTOR);
        }
    }

    boolean isAutoConvertibleFromFunction() {
        return autoConvertibleFromFunction;
    }

    private void generateConstructor(final Constructor<?> ctor) {
        // Generate a constructor that delegates to ctor, but takes an additional ScriptObject parameter at the
        // beginning of its parameter list.
        generateConstructor(ctor, false);

        if (samName != null) {
            if (!autoConvertibleFromFunction && ctor.getParameterTypes().length == 0) {
                // If the original type only has a single abstract method name, as well as a default ctor, then it can
                // be automatically converted from JS function.
                autoConvertibleFromFunction = true;
            }
            // If all our abstract methods have a single name, generate an additional constructor, one that takes a
            // ScriptFunction as its first parameter and assigns it as the implementation for all abstract methods.
            generateConstructor(ctor, true);
        }
    }

    /**
     * Generates a constructor for the adapter class. This constructor will take the same arguments as the supertype
     * constructor passed as the argument here, and delegate to it. However, it will take an additional argument of
     * either ScriptObject or ScriptFunction type (based on the value of the "fromFunction" parameter), and initialize
     * all the method handle fields of the adapter instance with functions from the script object (or the script
     * function itself, if that's what's passed). There is one method handle field in the adapter class for every method
     * that can be implemented or overridden; the name of every field is same as the name of the method, with a number
     * suffix that makes it unique in case of overloaded methods. The generated constructor will invoke
     * {@link #getHandle(ScriptFunction, MethodType, boolean)} or {@link #getHandle(ScriptObject, String, MethodType,
     * boolean)} to obtain the method handles; these methods make sure to add the necessary conversions and arity
     * adjustments so that the resulting method handles can be invoked from generated methods using {@code invokeExact}.
     * The constructor that takes a script function will only initialize the abstract methods
     * The constructor will also store the Nashorn global that was current at the constructor invocation time in a
     * field named "global". The generated constructor will be public, regardless of whether the supertype constructor
     * was public or protected.
     * @param ctor the supertype constructor that is serving as the base for the generated constructor.
     * @param fromFunction true if a
     */
    private void generateConstructor(final Constructor<?> ctor, final boolean fromFunction) {
        final Type originalCtorType = Type.getType(ctor);
        final Type[] originalArgTypes = originalCtorType.getArgumentTypes();
        final int argLen = originalArgTypes.length;
        final Type[] newArgTypes = new Type[argLen + 1];

        // Insert ScriptFunction|ScriptObject as the frontmost argument to the constructor
        final Type extraArgumentType = fromFunction ? SCRIPT_FUNCTION_TYPE : SCRIPT_OBJECT_TYPE;
        newArgTypes[0] = extraArgumentType;
        System.arraycopy(originalArgTypes, 0, newArgTypes, 1, argLen);

        // All constructors must be public, even if in the superclass they were protected.
        // Existing super constructor <init>(this, args...) triggers generating <init>(this, scriptObj, args...).
        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(ACC_PUBLIC | (ctor.isVarArgs() ?
                ACC_VARARGS : 0), INIT, Type.getMethodDescriptor(originalCtorType.getReturnType(), newArgTypes),
                null, null));

        mv.visitCode();
        // First, invoke super constructor with shifted arguments. If the form of the constructor we're generating is
        // <init>(this, scriptFn, args...), then we're invoking super.<init>(this, args...).
        mv.visitVarInsn(ALOAD, 0);
        final Class<?>[] argTypes = ctor.getParameterTypes();
        int offset = 2; // First arg is at position 2, after this and scriptFn.
        for (int i = 0; i < argLen; ++i) {
            final Type argType = Type.getType(argTypes[i]);
            mv.load(offset, argType);
            offset += argType.getSize();
        }
        mv.invokespecial(superTypeName, INIT, originalCtorType.getDescriptor());

        // Get a descriptor to the appropriate "JavaAdapterFactory.getHandle" method.
        final String getHandleDescriptor = fromFunction ? GET_HANDLE_FUNCTION_DESCRIPTOR : GET_HANDLE_OBJECT_DESCRIPTOR;

        // Assign MethodHandle fields through invoking getHandle()
        for (final MethodInfo mi : methodInfos) {
            mv.visitVarInsn(ALOAD, 0);
            if (fromFunction && !mi.getName().equals(samName)) {
                // Constructors initializing from a ScriptFunction only initialize methods with the SAM name.
                // NOTE: if there's a concrete overloaded method sharing the SAM name, it'll be overriden too. This
                // is a deliberate design choice. All other method handles are initialized to null.
                mv.visitInsn(ACONST_NULL);
            } else {
                mv.visitVarInsn(ALOAD, 1);
                if(!fromFunction) {
                    mv.aconst(mi.getName());
                }
                mv.aconst(Type.getMethodType(mi.type.toMethodDescriptorString()));
                mv.iconst(mi.method.isVarArgs() ? 1 : 0);
                mv.invokestatic(THIS_CLASS_TYPE_NAME, "getHandle", getHandleDescriptor);
            }
            mv.putfield(generatedTypeName, mi.methodHandleFieldName, METHOD_HANDLE_TYPE_DESCRIPTOR);
        }

        // Assign "this.global = Context.getGlobal()"
        mv.visitVarInsn(ALOAD, 0);
        invokeGetGlobal(mv);
        mv.dup();
        mv.invokevirtual(OBJECT_TYPE_NAME, "getClass", GET_CLASS_METHOD_DESCRIPTOR); // check against null Context
        mv.pop();
        mv.putfield(generatedTypeName, GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);

        // Wrap up
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void invokeGetGlobal(final InstructionAdapter mv) {
        mv.invokestatic(CONTEXT_TYPE_NAME, "getGlobal", GET_GLOBAL_METHOD_DESCRIPTOR);
    }

    private void invokeSetGlobal(final InstructionAdapter mv) {
        mv.invokestatic(globalSetterClassName, "setGlobal", SET_GLOBAL_METHOD_DESCRIPTOR);
    }

    /**
     * Given a JS script function, binds it to null JS "this", and adapts its parameter types, return types, and arity
     * to the specified type and arity. This method is public mainly for implementation reasons, so the adapter classes
     * can invoke it from their constructors that take a ScriptFunction in its first argument to obtain the method
     * handles for their abstract method implementations.
     * @param fn the script function
     * @param type the method type it has to conform to
     * @param varArg if the Java method for which the function is being adapted is a variable arity method
     * @return the appropriately adapted method handle for invoking the script function.
     */
    public static MethodHandle getHandle(final ScriptFunction fn, final MethodType type, final boolean varArg) {
        // JS "this" will be null for SAMs
        return adaptHandle(fn.getBoundInvokeHandle(null), type, varArg);
    }

    /**
     * Given a JS script object, retrieves a function from it by name, binds it to the script object as its "this", and
     * adapts its parameter types, return types, and arity to the specified type and arity. This method is public mainly
     * for implementation reasons, so the adapter classes can invoke it from their constructors that take a ScriptObject
     * in its first argument to obtain the method handles for their method implementations.
     * @param obj the script obj
     * @param name the name of the property that contains the function
     * @param type the method type it has to conform to
     * @param varArg if the Java method for which the function is being adapted is a variable arity method
     * @return the appropriately adapted method handle for invoking the script function, or null if the value of the
     * property is either null or undefined, or "toString" was requested as the name, but the object doesn't directly
     * define it but just inherits it through prototype.
     */
    public static MethodHandle getHandle(final ScriptObject obj, final String name, final MethodType type, final boolean varArg) {
        // Since every JS Object has a toString, we only override "String toString()" it if it's explicitly specified
        if ("toString".equals(name) && !obj.hasOwnProperty("toString")) {
            return null;
        }

        final Object fnObj = obj.get(name);
        if (fnObj instanceof ScriptFunction) {
            return adaptHandle(((ScriptFunction)fnObj).getBoundInvokeHandle(obj), type, varArg);
        } else if(fnObj == null || fnObj instanceof Undefined) {
            return null;
        } else {
            typeError(Context.getGlobal(), "not.a.function", name);
            throw new AssertionError();
        }
    }

    private static MethodHandle adaptHandle(final MethodHandle handle, final MethodType type, final boolean varArg) {
        return Bootstrap.getLinkerServices().asType(ScriptObject.pairArguments(handle, type, varArg), type);
    }

    /**
     * Encapsulation of the information used to generate methods in the adapter classes. Basically, a wrapper around the
     * reflective Method object, a cached MethodType, and the name of the field in the adapter class that will hold the
     * method handle serving as the implementation of this method in adapter instances.
     *
     */
    private static class MethodInfo {
        private final Method method;
        private final MethodType type;
        private String methodHandleFieldName;

        private MethodInfo(final Class<?> clazz, final String name, final Class<?>... argTypes) throws NoSuchMethodException {
            this(clazz.getDeclaredMethod(name, argTypes));
        }

        private MethodInfo(final Method method) {
            this.method = method;
            this.type   = MH.type(method.getReturnType(), method.getParameterTypes());
        }

        @Override
        public boolean equals(final Object obj) {
            return obj instanceof MethodInfo && equals((MethodInfo)obj);
        }

        private boolean equals(final MethodInfo other) {
            // Only method name and type are used for comparison; method handle field name is not.
            return getName().equals(other.getName()) && type.equals(other.type);
        }

        String getName() {
            return method.getName();
        }

        @Override
        public int hashCode() {
            return getName().hashCode() ^ type.hashCode();
        }

        void setIsCanonical(final Set<String> usedFieldNames) {
            int i = 0;
            String fieldName = getName();
            while(!usedFieldNames.add(fieldName)) {
                fieldName = getName() + (i++);
            }
            methodHandleFieldName = fieldName;
        }
    }

    private void generateMethods() {
        for(final MethodInfo mi: methodInfos) {
            generateMethod(mi);
        }
    }

    /**
     * Generates a method in the adapter class that adapts a method from the original class. The generated methods will
     * inspect the method handle field assigned to them. If it is null (the JS object doesn't provide an implementation
     * for the method) then it will either invoke its version in the supertype, or if it is abstract, throw an
     * {@link UnsupportedOperationException}. Otherwise, if the method handle field's value is not null, the handle is
     * invoked using invokeExact (signature polymorphic invocation as per JLS 15.12.3). Before the invocation, the
     * current Nashorn {@link Context} is checked, and if it is different than the global used to create the adapter
     * instance, the creating global is set to be the current global. In this case, the previously current global is
     * restored after the invocation. If invokeExact results in a Throwable that is not one of the method's declared
     * exceptions, and is not an unchecked throwable, then it is wrapped into a {@link RuntimeException} and the runtime
     * exception is thrown. The method handle retrieved from the field is guaranteed to exactly match the signature of
     * the method; this is guaranteed by the way constructors of the adapter class obtain them using
     * {@link #getHandle(ScriptObject, String, MethodType, boolean)}.
     * @param mi the method info describing the method to be generated.
     */
    private void generateMethod(final MethodInfo mi) {
        final Method method = mi.method;
        final int mod = method.getModifiers();
        final int access = ACC_PUBLIC | (method.isVarArgs() ? ACC_VARARGS : 0);
        final Class<?>[] exceptions = method.getExceptionTypes();
        final String[] exceptionNames = new String[exceptions.length];
        for (int i = 0; i < exceptions.length; ++i) {
            exceptionNames[i] = Type.getInternalName(exceptions[i]);
        }
        final MethodType type = mi.type;
        final String methodDesc = type.toMethodDescriptorString();
        final String name = mi.getName();

        final Type asmType = Type.getMethodType(methodDesc);
        final Type[] asmArgTypes = asmType.getArgumentTypes();

        // Determine the first index for a local variable
        int nextLocalVar = 1; // this
        for(final Type t: asmArgTypes) {
            nextLocalVar += t.getSize();
        }

        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(access, name, methodDesc, null,
                exceptionNames));
        mv.visitCode();

        final Label methodHandleNotNull = new Label();
        final Label methodEnd = new Label();

        final Type returnType = Type.getType(type.returnType());

        // Get the method handle
        mv.visitVarInsn(ALOAD, 0);
        mv.getfield(generatedTypeName, mi.methodHandleFieldName, METHOD_HANDLE_TYPE_DESCRIPTOR);
        mv.visitInsn(DUP); // It'll remain on the stack all the way until the invocation
        // Check if the method handle is null
        mv.visitJumpInsn(IFNONNULL, methodHandleNotNull);
        if(Modifier.isAbstract(mod)) {
            // If it's null, and the method is abstract, throw an exception
            mv.anew(UNSUPPORTED_OPERATION_TYPE);
            mv.dup();
            mv.invokespecial(UNSUPPORTED_OPERATION_TYPE_NAME, INIT, VOID_NOARG);
            mv.athrow();
        } else {
            // If it's null, and the method is not abstract, delegate to super method.
            mv.visitVarInsn(ALOAD, 0);
            int nextParam = 1;
            for(final Type t: asmArgTypes) {
                mv.load(nextParam, t);
                nextParam += t.getSize();
            }
            mv.invokespecial(superTypeName, name, methodDesc);
            mv.areturn(returnType);
        }

        mv.visitLabel(methodHandleNotNull);
        final int currentGlobalVar = nextLocalVar++;
        final int globalsDifferVar = nextLocalVar++;

        // Emit code for switching to the creating global
        // ScriptObject currentGlobal = Context.getGlobal();
        invokeGetGlobal(mv);
        mv.dup();
        mv.visitVarInsn(ASTORE, currentGlobalVar);
        // if(this.global == currentGlobal) {
        loadGlobalOnStack(mv);
        final Label globalsDiffer = new Label();
        mv.ifacmpne(globalsDiffer);
        //     globalsDiffer = false
        mv.iconst(0); // false
        final Label proceed = new Label();
        mv.goTo(proceed);
        mv.visitLabel(globalsDiffer);
        // } else {
        //     Context.setGlobal(this.global);
        loadGlobalOnStack(mv);
        invokeSetGlobal(mv);
        //     globalsDiffer = true
        mv.iconst(1);

        mv.visitLabel(proceed);
        mv.visitVarInsn(ISTORE, globalsDifferVar);

        // Load all parameters back on stack for dynamic invocation.
        int varOffset = 1;
        for (final Type t : asmArgTypes) {
            mv.load(varOffset, t);
            varOffset += t.getSize();
        }

        // Invoke the target method handle
        final Label tryBlockStart = new Label();
        mv.visitLabel(tryBlockStart);
        mv.invokevirtual(METHOD_HANDLE_TYPE.getInternalName(), "invokeExact", type.toMethodDescriptorString());
        final Label tryBlockEnd = new Label();
        mv.visitLabel(tryBlockEnd);
        emitFinally(mv, currentGlobalVar, globalsDifferVar);
        mv.areturn(returnType);

        // If Throwable is not declared, we need an adapter from Throwable to RuntimeException
        final boolean throwableDeclared = isThrowableDeclared(exceptions);
        final Label throwableHandler;
        if (!throwableDeclared) {
            // Add "throw new RuntimeException(Throwable)" handler for Throwable
            throwableHandler = new Label();
            mv.visitLabel(throwableHandler);
            mv.anew(RUNTIME_EXCEPTION_TYPE);
            mv.dupX1();
            mv.swap();
            mv.invokespecial(RUNTIME_EXCEPTION_TYPE_NAME, INIT, Type.getMethodDescriptor(Type.VOID_TYPE, THROWABLE_TYPE));
            // Fall through to rethrow handler
        } else {
            throwableHandler = null;
        }
        final Label rethrowHandler = new Label();
        mv.visitLabel(rethrowHandler);
        // Rethrow handler for RuntimeException, Error, and all declared exception types
        emitFinally(mv, currentGlobalVar, globalsDifferVar);
        mv.athrow();
        mv.visitLabel(methodEnd);

        mv.visitLocalVariable("currentGlobal", SCRIPT_OBJECT_TYPE_DESCRIPTOR, null, methodHandleNotNull, methodEnd, currentGlobalVar);
        mv.visitLocalVariable("globalsDiffer", Type.INT_TYPE.getDescriptor(), null, methodHandleNotNull, methodEnd, globalsDifferVar);

        if(throwableDeclared) {
            mv.visitTryCatchBlock(tryBlockStart, tryBlockEnd, rethrowHandler, THROWABLE_TYPE_NAME);
            assert throwableHandler == null;
        } else {
            mv.visitTryCatchBlock(tryBlockStart, tryBlockEnd, rethrowHandler, RUNTIME_EXCEPTION_TYPE_NAME);
            mv.visitTryCatchBlock(tryBlockStart, tryBlockEnd, rethrowHandler, ERROR_TYPE_NAME);
            for(final String excName: exceptionNames) {
                mv.visitTryCatchBlock(tryBlockStart, tryBlockEnd, rethrowHandler, excName);
            }
            mv.visitTryCatchBlock(tryBlockStart, tryBlockEnd, throwableHandler, THROWABLE_TYPE_NAME);
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /**
     * Emit code to restore the previous Nashorn Context when needed.
     * @param mv the instruction adapter
     * @param currentGlobalVar index of the local variable holding the reference to the current global at method
     * entry.
     * @param globalsDifferVar index of the boolean local variable that is true if the global needs to be restored.
     */
    private void emitFinally(final InstructionAdapter mv, final int currentGlobalVar, final int globalsDifferVar) {
        // Emit code to restore the previous Nashorn global if needed
        mv.visitVarInsn(ILOAD, globalsDifferVar);
        final Label skip = new Label();
        mv.ifeq(skip);
        mv.visitVarInsn(ALOAD, currentGlobalVar);
        invokeSetGlobal(mv);
        mv.visitLabel(skip);
    }

    private void loadGlobalOnStack(final InstructionAdapter mv) {
        mv.visitVarInsn(ALOAD, 0);
        mv.getfield(generatedTypeName, GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);
    }

    private static boolean isThrowableDeclared(final Class<?>[] exceptions) {
        for (final Class<?> exception : exceptions) {
            if (exception == Throwable.class) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gathers methods that can be implemented or overridden from the specified type into this factory's
     * {@link #methodInfos} set. It will add all non-final, non-static methods that are either public or protected from
     * the type if the type itself is public. If the type is a class, the method will recursively invoke itself for its
     * superclass and the interfaces it implements, and add further methods that were not directly declared on the
     * class.
     * @param type the type defining the methods.
     */
    private void gatherMethods(final Class<?> type) {
        if (Modifier.isPublic(type.getModifiers())) {
            final Method[] typeMethods = type.isInterface() ? type.getMethods() : type.getDeclaredMethods();

            for (final Method typeMethod: typeMethods) {
                final int m = typeMethod.getModifiers();
                if (Modifier.isStatic(m)) {
                    continue;
                }
                if (Modifier.isPublic(m) || Modifier.isProtected(m)) {
                    final MethodInfo mi = new MethodInfo(typeMethod);
                    if (Modifier.isFinal(m)) {
                        finalMethods.add(mi);
                    } else if (!finalMethods.contains(mi) && methodInfos.add(mi)) {
                        if (Modifier.isAbstract(m)) {
                            abstractMethodNames.add(mi.getName());
                        }
                        mi.setIsCanonical (usedFieldNames);
                    }
                }
            }
        }
        // If the type is a class, visit its superclasses and declared interfaces. If it's an interface, we're done.
        // Needing to invoke the method recursively for a non-interface Class object is the consequence of needing to
        // see all declared protected methods, and Class.getDeclaredMethods() doesn't provide those declared in a
        // superclass. For interfaces, we used Class.getMethods(), as we're only interested in public ones there, and
        // getMethods() does provide those declared in a superinterface.
        if (!type.isInterface()) {
            final Class<?> superClass = type.getSuperclass();
            if (superClass != null) {
                gatherMethods(superClass);
            }
            for (final Class<?> itf: type.getInterfaces()) {
                gatherMethods(itf);
            }
        }
    }

    /**
     * Creates a collection of methods that are not final, but we still never allow them to be overridden in adapters,
     * as explicitly declaring them automatically is a bad idea. Currently, this means {@code Object.finalize()} and
     * {@code Object.clone()}.
     * @return a collection of method infos representing those methods that we never override in adapter classes.
     */
    private static Collection<MethodInfo> getExcludedMethods() {
        return AccessController.doPrivileged(new PrivilegedAction<Collection<MethodInfo>>() {
            @Override
            public Collection<MethodInfo> run() {
                try {
                    return Arrays.asList(
                            new MethodInfo(Object.class, "finalize"),
                            new MethodInfo(Object.class, "clone"));
                } catch (final NoSuchMethodException e) {
                    throw new AssertionError(e);
                }
            }
        });
    }

    private static ProtectionDomain createGeneratedProtectionDomain() {
        // Generated classes need to have AllPermission. Since we require the "createClassLoader" RuntimePermission, we
        // can create a class loader that'll load new classes with any permissions. Our generated classes are just
        // delegating adapters, so having AllPermission can't cause anything wrong; the effective set of permissions for
        // the executing script functions will still be limited by the permissions of the caller and the permissions of
        // the script.
        final Permissions permissions = new Permissions();
        permissions.add(new AllPermission());
        return new ProtectionDomain(new CodeSource(null, (CodeSigner[])null), permissions);
    }

    private static class AdapterInfo {
        final StaticClass adapterClass;
        final boolean autoConvertibleFromFunction;
        final AdaptationOutcome adaptationOutcome;

        AdapterInfo(final StaticClass adapterClass, final boolean autoConvertibleFromFunction) {
            this.adapterClass = adapterClass;
            this.autoConvertibleFromFunction = autoConvertibleFromFunction;
            this.adaptationOutcome = AdaptationOutcome.SUCCESS;
        }

        AdapterInfo(final AdaptationOutcome outcome) {
            this.adapterClass = null;
            this.autoConvertibleFromFunction = false;
            this.adaptationOutcome = outcome;
        }
    }

    /**
     * For a given class, create its adapter class and associated info.
     * @param type the class for which the adapter is created
     * @return the adapter info for the class.
     */
    private static AdapterInfo createAdapterInfo(final Class<?> type) {
        final int mod = type.getModifiers();
        if (Modifier.isFinal(mod)) {
            return new AdapterInfo(AdaptationOutcome.ERROR_FINAL_CLASS);
        }
        if (!Modifier.isPublic(mod)) {
            return new AdapterInfo(AdaptationOutcome.ERROR_NON_PUBLIC_CLASS);
        }
        return AccessController.doPrivileged(new PrivilegedAction<AdapterInfo>() {
            @Override
            public AdapterInfo run() {
                try {
                    final JavaAdapterFactory factory = new JavaAdapterFactory(type);
                    return new AdapterInfo(StaticClass.forClass(factory.generateClass()),
                            factory.isAutoConvertibleFromFunction());
                } catch (final AdaptationException e) {
                    return new AdapterInfo(e.outcome);
                }
            }
        });
    }

    @SuppressWarnings("serial")
    private static class AdaptationException extends Exception {
        private final AdaptationOutcome outcome;
        AdaptationException(final AdaptationOutcome outcome) {
            this.outcome = outcome;
        }
    }

    private String getCommonSuperClass(final String type1, final String type2) {
        try {
            final Class<?> c1 = Class.forName(type1.replace('/', '.'), false, commonLoader);
            final Class<?> c2 = Class.forName(type2.replace('/', '.'), false, commonLoader);
            if (c1.isAssignableFrom(c2)) {
                return type1;
            }
            if (c2.isAssignableFrom(c1)) {
                return type2;
            }
            if (c1.isInterface() || c2.isInterface()) {
                return "java/lang/Object";
            }
            return assignableSuperClass(c1, c2).getName().replace('.', '/');
        } catch(final ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static Class<?> assignableSuperClass(final Class<?> c1, final Class<?> c2) {
        final Class<?> superClass = c1.getSuperclass();
        return superClass.isAssignableFrom(c2) ? superClass : assignableSuperClass(superClass, c2);
    }

    /**
     * Finds a class loader that sees both the specified class and Nashorn classes.
     * @param clazz the class that needs to be visible from the found class loader.
     * @return the class loader that sees both the specified class and Nashorn classes.
     * @throws IllegalStateException if no such class loader is found.
     */
    private static ClassLoader findCommonLoader(final Class<?> clazz) {
        final ClassLoader clazzLoader = clazz.getClassLoader();
        if (canSeeClass(clazzLoader, ScriptObject.class)) {
            return clazzLoader;
        }

        final ClassLoader nashornLoader = ScriptObject.class.getClassLoader();
        if(canSeeClass(nashornLoader, clazz)) {
            return nashornLoader;
        }

        throw new IllegalStateException("Can't find a common class loader for ScriptObject and " +
                    clazz.getName());
    }

    private static boolean canSeeClass(final ClassLoader cl, final Class<?> clazz) {
        try {
            return Class.forName(clazz.getName(), false, cl) == clazz;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }
}
