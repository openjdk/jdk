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
import static jdk.internal.org.objectweb.asm.Opcodes.ALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.ASTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.D2F;
import static jdk.internal.org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static jdk.internal.org.objectweb.asm.Opcodes.I2B;
import static jdk.internal.org.objectweb.asm.Opcodes.I2S;
import static jdk.internal.org.objectweb.asm.Opcodes.RETURN;
import static jdk.nashorn.internal.codegen.CompilerConstants.interfaceCallNoLookup;
import static jdk.nashorn.internal.codegen.CompilerConstants.staticCallNoLookup;
import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.linker.AdaptationResult.Outcome.ERROR_NO_ACCESSIBLE_CONSTRUCTOR;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.commons.InstructionAdapter;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.api.scripting.ScriptUtils;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.linker.AdaptationResult.Outcome;
import jdk.internal.reflect.CallerSensitive;

/**
 * Generates bytecode for a Java adapter class. Used by the {@link JavaAdapterFactory}.
 * </p><p>
 * For every protected or public constructor in the extended class, the adapter class will have either one or two
 * public constructors (visibility of protected constructors in the extended class is promoted to public).
 * <li>
 * <li>For adapter classes with instance-level overrides, a constructor taking a trailing ScriptObject argument preceded
 * by original constructor arguments is always created on the adapter class. When such a constructor is invoked, the
 * passed ScriptObject's member functions are used to implement and/or override methods on the original class,
 * dispatched by name. A single JavaScript function will act as the implementation for all overloaded methods of the
 * same name. When methods on an adapter instance are invoked, the functions are invoked having the ScriptObject passed
 * in the instance constructor as their "this". Subsequent changes to the ScriptObject (reassignment or removal of its
 * functions) will be reflected in the adapter instance as it is live dispatching to its members on every method invocation.
 * {@code java.lang.Object} methods {@code equals}, {@code hashCode}, and {@code toString} can also be overridden. The
 * only restriction is that since every JavaScript object already has a {@code toString} function through the
 * {@code Object.prototype}, the {@code toString} in the adapter is only overridden if the passed ScriptObject has a
 * {@code toString} function as its own property, and not inherited from a prototype. All other adapter methods can be
 * implemented or overridden through a prototype-inherited function of the ScriptObject passed to the constructor too.
 * </li>
 * <li>
 * If the original types collectively have only one abstract method, or have several of them, but all share the
 * same name, an additional constructor for instance-level override adapter is provided for every original constructor;
 * this one takes a ScriptFunction as its last argument preceded by original constructor arguments. This constructor
 * will use the passed function as the implementation for all abstract methods. For consistency, any concrete methods
 * sharing the single abstract method name will also be overridden by the function. When methods on the adapter instance
 * are invoked, the ScriptFunction is invoked with UNDEFINED or Global as its "this" depending whether the function is
 * strict or not.
 * </li>
 * <li>
 * If the adapter being generated has class-level overrides, constructors taking same arguments as the superclass
 * constructors are created. These constructors simply delegate to the superclass constructor. They are simply used to
 * create instances of the adapter class, with no instance-level overrides, as they don't have them. If the original
 * class' constructor was variable arity, the adapter constructor will also be variable arity. Protected constructors
 * are exposed as public.
 * </li>
 * </ul>
 * </p><p>
 * For adapter methods that return values, all the JavaScript-to-Java conversions supported by Nashorn will be in effect
 * to coerce the JavaScript function return value to the expected Java return type.
 * </p><p>
 * Since we are adding a trailing argument to the generated constructors in the adapter class with instance-level overrides, they will never be
 * declared as variable arity, even if the original constructor in the superclass was declared as variable arity. The
 * reason we are passing the additional argument at the end of the argument list instead at the front is that the
 * source-level script expression <code>new X(a, b) { ... }</code> (which is a proprietary syntax extension Nashorn uses
 * to resemble Java anonymous classes) is actually equivalent to <code>new X(a, b, { ... })</code>.
 * </p><p>
 * It is possible to create two different adapter classes: those that can have class-level overrides, and those that can
 * have instance-level overrides. When {@link JavaAdapterFactory#getAdapterClassFor(Class[], ScriptObject, ProtectionDomain)}
 * or {@link JavaAdapterFactory#getAdapterClassFor(Class[], ScriptObject, Lookup)} is invoked
 * with non-null {@code classOverrides} parameter, an adapter class is created that can have class-level overrides, and
 * the passed script object will be used as the implementations for its methods, just as in the above case of the
 * constructor taking a script object. Note that in the case of class-level overrides, a new adapter class is created on
 * every invocation, and the implementation object is bound to the class, not to any instance. All created instances
 * will share these functions. If it is required to have both class-level overrides and instance-level overrides, the
 * class-level override adapter class should be subclassed with an instance-override adapter. Since adapters delegate to
 * super class when an overriding method handle is not specified, this will behave as expected. It is not possible to
 * have both class-level and instance-level overrides in the same class for security reasons: adapter classes are
 * defined with a protection domain of their creator code, and an adapter class that has both class and instance level
 * overrides would need to have two potentially different protection domains: one for class-based behavior and one for
 * instance-based behavior; since Java classes can only belong to a single protection domain, this could not be
 * implemented securely.
 */
final class JavaAdapterBytecodeGenerator {
    // Field names in adapters
    private static final String GLOBAL_FIELD_NAME = "global";
    private static final String DELEGATE_FIELD_NAME = "delegate";
    private static final String IS_FUNCTION_FIELD_NAME = "isFunction";
    private static final String CALL_THIS_FIELD_NAME = "callThis";

    // Initializer names
    private static final String INIT = "<init>";
    private static final String CLASS_INIT = "<clinit>";

    // Types often used in generated bytecode
    private static final Type OBJECT_TYPE = Type.getType(Object.class);
    private static final Type SCRIPT_OBJECT_TYPE = Type.getType(ScriptObject.class);
    private static final Type SCRIPT_FUNCTION_TYPE = Type.getType(ScriptFunction.class);
    private static final Type SCRIPT_OBJECT_MIRROR_TYPE = Type.getType(ScriptObjectMirror.class);

    // JavaAdapterServices methods used in generated bytecode
    private static final Call CHECK_FUNCTION = lookupServiceMethod("checkFunction", ScriptFunction.class, Object.class, String.class);
    private static final Call EXPORT_RETURN_VALUE = lookupServiceMethod("exportReturnValue", Object.class, Object.class);
    private static final Call GET_CALL_THIS = lookupServiceMethod("getCallThis", Object.class, ScriptFunction.class, Object.class);
    private static final Call GET_CLASS_OVERRIDES = lookupServiceMethod("getClassOverrides", ScriptObject.class);
    private static final Call GET_NON_NULL_GLOBAL = lookupServiceMethod("getNonNullGlobal", ScriptObject.class);
    private static final Call HAS_OWN_TO_STRING = lookupServiceMethod("hasOwnToString", boolean.class, ScriptObject.class);
    private static final Call INVOKE_NO_PERMISSIONS = lookupServiceMethod("invokeNoPermissions", void.class, MethodHandle.class, Object.class);
    private static final Call NOT_AN_OBJECT = lookupServiceMethod("notAnObject", void.class, Object.class);
    private static final Call SET_GLOBAL = lookupServiceMethod("setGlobal", Runnable.class, ScriptObject.class);
    private static final Call TO_CHAR_PRIMITIVE = lookupServiceMethod("toCharPrimitive", char.class, Object.class);
    private static final Call UNSUPPORTED = lookupServiceMethod("unsupported", UnsupportedOperationException.class);
    private static final Call WRAP_THROWABLE = lookupServiceMethod("wrapThrowable", RuntimeException.class, Throwable.class);
    private static final Call UNWRAP_MIRROR = lookupServiceMethod("unwrapMirror", ScriptObject.class, Object.class, boolean.class);

    // Other methods invoked by the generated bytecode
    private static final Call UNWRAP = staticCallNoLookup(ScriptUtils.class, "unwrap", Object.class, Object.class);
    private static final Call CHAR_VALUE_OF = staticCallNoLookup(Character.class, "valueOf", Character.class, char.class);
    private static final Call DOUBLE_VALUE_OF = staticCallNoLookup(Double.class, "valueOf", Double.class, double.class);
    private static final Call LONG_VALUE_OF = staticCallNoLookup(Long.class, "valueOf", Long.class, long.class);
    private static final Call RUN = interfaceCallNoLookup(Runnable.class, "run", void.class);

    // ASM handle to the bootstrap method
    private static final Handle BOOTSTRAP_HANDLE = new Handle(H_INVOKESTATIC,
            Type.getInternalName(JavaAdapterServices.class), "bootstrap",
            MethodType.methodType(CallSite.class, Lookup.class, String.class,
                    MethodType.class, int.class).toMethodDescriptorString(), false);

    // ASM handle to the bootstrap method for array populator
    private static final Handle CREATE_ARRAY_BOOTSTRAP_HANDLE = new Handle(H_INVOKESTATIC,
            Type.getInternalName(JavaAdapterServices.class), "createArrayBootstrap",
            MethodType.methodType(CallSite.class, Lookup.class, String.class,
                    MethodType.class).toMethodDescriptorString(), false);

    // Field type names used in the generated bytecode
    private static final String SCRIPT_OBJECT_TYPE_DESCRIPTOR = SCRIPT_OBJECT_TYPE.getDescriptor();
    private static final String OBJECT_TYPE_DESCRIPTOR = OBJECT_TYPE.getDescriptor();
    private static final String BOOLEAN_TYPE_DESCRIPTOR = Type.BOOLEAN_TYPE.getDescriptor();

    // Throwable names used in the generated bytecode
    private static final String RUNTIME_EXCEPTION_TYPE_NAME = Type.getInternalName(RuntimeException.class);
    private static final String ERROR_TYPE_NAME = Type.getInternalName(Error.class);
    private static final String THROWABLE_TYPE_NAME = Type.getInternalName(Throwable.class);

    // Some more frequently used method descriptors
    private static final String GET_METHOD_PROPERTY_METHOD_DESCRIPTOR = Type.getMethodDescriptor(OBJECT_TYPE, SCRIPT_OBJECT_TYPE);
    private static final String VOID_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.VOID_TYPE);

    private static final String ADAPTER_PACKAGE_INTERNAL = "jdk/nashorn/javaadapters/";
    private static final int MAX_GENERATED_TYPE_NAME_LENGTH = 255;

    // Method name prefix for invoking super-methods
    static final String SUPER_PREFIX = "super$";

    // Method name and type for the no-privilege finalizer delegate
    private static final String FINALIZER_DELEGATE_NAME = "$$nashornFinalizerDelegate";
    private static final String FINALIZER_DELEGATE_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.VOID_TYPE, OBJECT_TYPE);

    /**
     * Collection of methods we never override: Object.clone(), Object.finalize().
     */
    private static final Collection<MethodInfo> EXCLUDED = getExcludedMethods();

    // This is the superclass for our generated adapter.
    private final Class<?> superClass;
    // Interfaces implemented by our generated adapter.
    private final List<Class<?>> interfaces;
    // Class loader used as the parent for the class loader we'll create to load the generated class. It will be a class
    // loader that has the visibility of all original types (class to extend and interfaces to implement) and of the
    // Nashorn classes.
    private final ClassLoader commonLoader;
    // Is this a generator for the version of the class that can have overrides on the class level?
    private final boolean classOverride;
    // Binary name of the superClass
    private final String superClassName;
    // Binary name of the generated class.
    private final String generatedClassName;
    private final Set<String> abstractMethodNames = new HashSet<>();
    private final String samName;
    private final Set<MethodInfo> finalMethods = new HashSet<>(EXCLUDED);
    private final Set<MethodInfo> methodInfos = new HashSet<>();
    private final boolean autoConvertibleFromFunction;
    private boolean hasExplicitFinalizer = false;

    private final ClassWriter cw;

    /**
     * Creates a generator for the bytecode for the adapter for the specified superclass and interfaces.
     * @param superClass the superclass the adapter will extend.
     * @param interfaces the interfaces the adapter will implement.
     * @param commonLoader the class loader that can see all of superClass, interfaces, and Nashorn classes.
     * @param classOverride true to generate the bytecode for the adapter that has class-level overrides, false to
     * generate the bytecode for the adapter that has instance-level overrides.
     * @throws AdaptationException if the adapter can not be generated for some reason.
     */
    JavaAdapterBytecodeGenerator(final Class<?> superClass, final List<Class<?>> interfaces,
                                 final ClassLoader commonLoader, final boolean classOverride) throws AdaptationException {
        assert superClass != null && !superClass.isInterface();
        assert interfaces != null;

        this.superClass = superClass;
        this.interfaces = interfaces;
        this.classOverride = classOverride;
        this.commonLoader = commonLoader;
        cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(final String type1, final String type2) {
                // We need to override ClassWriter.getCommonSuperClass to use this factory's commonLoader as a class
                // loader to find the common superclass of two types when needed.
                return JavaAdapterBytecodeGenerator.this.getCommonSuperClass(type1, type2);
            }
        };
        superClassName = Type.getInternalName(superClass);
        generatedClassName = getGeneratedClassName(superClass, interfaces);

        cw.visit(Opcodes.V1_8, ACC_PUBLIC | ACC_SUPER, generatedClassName, null, superClassName, getInternalTypeNames(interfaces));
        generateField(GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);
        generateField(DELEGATE_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);

        gatherMethods(superClass);
        gatherMethods(interfaces);
        if (abstractMethodNames.size() == 1) {
            samName = abstractMethodNames.iterator().next();
            generateField(CALL_THIS_FIELD_NAME, OBJECT_TYPE_DESCRIPTOR);
            generateField(IS_FUNCTION_FIELD_NAME, BOOLEAN_TYPE_DESCRIPTOR);
        } else {
            samName = null;
        }
        if(classOverride) {
            generateClassInit();
        }
        autoConvertibleFromFunction = generateConstructors();
        generateMethods();
        generateSuperMethods();
        if (hasExplicitFinalizer) {
            generateFinalizerMethods();
        }
        // }
        cw.visitEnd();
    }

    private void generateField(final String name, final String fieldDesc) {
        cw.visitField(ACC_PRIVATE | ACC_FINAL | (classOverride ? ACC_STATIC : 0), name, fieldDesc, null, null).visitEnd();
    }

    JavaAdapterClassLoader createAdapterClassLoader() {
        return new JavaAdapterClassLoader(generatedClassName, cw.toByteArray());
    }

    boolean isAutoConvertibleFromFunction() {
        return autoConvertibleFromFunction;
    }

    private static String getGeneratedClassName(final Class<?> superType, final List<Class<?>> interfaces) {
        // The class we use to primarily name our adapter is either the superclass, or if it is Object (meaning we're
        // just implementing interfaces or extending Object), then the first implemented interface or Object.
        final Class<?> namingType = superType == Object.class ? (interfaces.isEmpty()? Object.class : interfaces.get(0)) : superType;
        final Package pkg = namingType.getPackage();
        final String namingTypeName = Type.getInternalName(namingType);
        final StringBuilder buf = new StringBuilder();
        buf.append(ADAPTER_PACKAGE_INTERNAL).append(namingTypeName.replace('/', '_'));
        final Iterator<Class<?>> it = interfaces.iterator();
        if(superType == Object.class && it.hasNext()) {
            it.next(); // Skip first interface, it was used to primarily name the adapter
        }
        // Append interface names to the adapter name
        while(it.hasNext()) {
            buf.append("$$").append(it.next().getSimpleName());
        }
        return buf.toString().substring(0, Math.min(MAX_GENERATED_TYPE_NAME_LENGTH, buf.length()));
    }

    /**
     * Given a list of class objects, return an array with their binary names. Used to generate the array of interface
     * names to implement.
     * @param classes the classes
     * @return an array of names
     */
    private static String[] getInternalTypeNames(final List<Class<?>> classes) {
        final int interfaceCount = classes.size();
        final String[] interfaceNames = new String[interfaceCount];
        for(int i = 0; i < interfaceCount; ++i) {
            interfaceNames[i] = Type.getInternalName(classes.get(i));
        }
        return interfaceNames;
    }

    private void generateClassInit() {
        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(ACC_STATIC, CLASS_INIT,
                VOID_METHOD_DESCRIPTOR, null, null));

        // Assign "global = Context.getGlobal()"
        GET_NON_NULL_GLOBAL.invoke(mv);
        mv.putstatic(generatedClassName, GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);

        GET_CLASS_OVERRIDES.invoke(mv);
        if(samName != null) {
            // If the class is a SAM, allow having ScriptFunction passed as class overrides
            mv.dup();
            mv.instanceOf(SCRIPT_FUNCTION_TYPE);
            mv.dup();
            mv.putstatic(generatedClassName, IS_FUNCTION_FIELD_NAME, BOOLEAN_TYPE_DESCRIPTOR);
            final Label notFunction = new Label();
            mv.ifeq(notFunction);
            mv.dup();
            mv.checkcast(SCRIPT_FUNCTION_TYPE);
            emitInitCallThis(mv);
            mv.visitLabel(notFunction);
        }
        mv.putstatic(generatedClassName, DELEGATE_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);

        endInitMethod(mv);
    }

    /**
     * Emit bytecode for initializing the "callThis" field.
     */
    private void emitInitCallThis(final InstructionAdapter mv) {
        loadField(mv, GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);
        GET_CALL_THIS.invoke(mv);
        if(classOverride) {
            mv.putstatic(generatedClassName, CALL_THIS_FIELD_NAME, OBJECT_TYPE_DESCRIPTOR);
        } else {
            // It is presumed ALOAD 0 was already executed
            mv.putfield(generatedClassName, CALL_THIS_FIELD_NAME, OBJECT_TYPE_DESCRIPTOR);
        }
    }

    private boolean generateConstructors() throws AdaptationException {
        boolean gotCtor = false;
        boolean canBeAutoConverted = false;
        for (final Constructor<?> ctor: superClass.getDeclaredConstructors()) {
            final int modifier = ctor.getModifiers();
            if((modifier & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0 && !isCallerSensitive(ctor)) {
                canBeAutoConverted = generateConstructors(ctor) | canBeAutoConverted;
                gotCtor = true;
            }
        }
        if(!gotCtor) {
            throw new AdaptationException(ERROR_NO_ACCESSIBLE_CONSTRUCTOR, superClass.getCanonicalName());
        }
        return canBeAutoConverted;
    }

    private boolean generateConstructors(final Constructor<?> ctor) {
        if(classOverride) {
            // Generate a constructor that just delegates to ctor. This is used with class-level overrides, when we want
            // to create instances without further per-instance overrides.
            generateDelegatingConstructor(ctor);
            return false;
        }

            // Generate a constructor that delegates to ctor, but takes an additional ScriptObject parameter at the
            // beginning of its parameter list.
            generateOverridingConstructor(ctor, false);

        if (samName == null) {
            return false;
        }
        // If all our abstract methods have a single name, generate an additional constructor, one that takes a
        // ScriptFunction as its first parameter and assigns it as the implementation for all abstract methods.
        generateOverridingConstructor(ctor, true);
        // If the original type only has a single abstract method name, as well as a default ctor, then it can
        // be automatically converted from JS function.
        return ctor.getParameterTypes().length == 0;
    }

    private void generateDelegatingConstructor(final Constructor<?> ctor) {
        final Type originalCtorType = Type.getType(ctor);
        final Type[] argTypes = originalCtorType.getArgumentTypes();

        // All constructors must be public, even if in the superclass they were protected.
        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(ACC_PUBLIC |
                (ctor.isVarArgs() ? ACC_VARARGS : 0), INIT,
                Type.getMethodDescriptor(originalCtorType.getReturnType(), argTypes), null, null));

        mv.visitCode();
        emitSuperConstructorCall(mv, originalCtorType.getDescriptor());

        endInitMethod(mv);
    }

    /**
     * Generates a constructor for the instance adapter class. This constructor will take the same arguments as the supertype
     * constructor passed as the argument here, and delegate to it. However, it will take an additional argument of
     * either ScriptObject or ScriptFunction type (based on the value of the "fromFunction" parameter), and initialize
     * all the method handle fields of the adapter instance with functions from the script object (or the script
     * function itself, if that's what's passed). Additionally, it will create another constructor with an additional
     * Object type parameter that can be used for ScriptObjectMirror objects.
     * The constructor will also store the Nashorn global that was current at the constructor
     * invocation time in a field named "global". The generated constructor will be public, regardless of whether the
     * supertype constructor was public or protected. The generated constructor will not be variable arity, even if the
     * supertype constructor was.
     * @param ctor the supertype constructor that is serving as the base for the generated constructor.
     * @param fromFunction true if we're generating a constructor that initializes SAM types from a single
     * ScriptFunction passed to it, false if we're generating a constructor that initializes an arbitrary type from a
     * ScriptObject passed to it.
     */
    private void generateOverridingConstructor(final Constructor<?> ctor, final boolean fromFunction) {
        final Type originalCtorType = Type.getType(ctor);
        final Type[] originalArgTypes = originalCtorType.getArgumentTypes();
        final int argLen = originalArgTypes.length;
        final Type[] newArgTypes = new Type[argLen + 1];

        // Insert ScriptFunction|ScriptObject as the last argument to the constructor
        final Type extraArgumentType = fromFunction ? SCRIPT_FUNCTION_TYPE : SCRIPT_OBJECT_TYPE;
        newArgTypes[argLen] = extraArgumentType;
        System.arraycopy(originalArgTypes, 0, newArgTypes, 0, argLen);

        // All constructors must be public, even if in the superclass they were protected.
        // Existing super constructor <init>(this, args...) triggers generating <init>(this, args..., delegate).
        // Any variable arity constructors become fixed-arity with explicit array arguments.
        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(ACC_PUBLIC, INIT,
                Type.getMethodDescriptor(originalCtorType.getReturnType(), newArgTypes), null, null));

        mv.visitCode();
        // First, invoke super constructor with original arguments.
        final int extraArgOffset = emitSuperConstructorCall(mv, originalCtorType.getDescriptor());

        // Assign "this.global = Context.getGlobal()"
        mv.visitVarInsn(ALOAD, 0);
        GET_NON_NULL_GLOBAL.invoke(mv);
        mv.putfield(generatedClassName, GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);

        // Assign "this.delegate = delegate"
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, extraArgOffset);
        mv.putfield(generatedClassName, DELEGATE_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);

        if (fromFunction) {
            // Assign "isFunction = true"
            mv.visitVarInsn(ALOAD, 0);
            mv.iconst(1);
            mv.putfield(generatedClassName, IS_FUNCTION_FIELD_NAME, BOOLEAN_TYPE_DESCRIPTOR);

        mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, extraArgOffset);
            emitInitCallThis(mv);
        }

        endInitMethod(mv);

        if (! fromFunction) {
            newArgTypes[argLen] = OBJECT_TYPE;
            final InstructionAdapter mv2 = new InstructionAdapter(cw.visitMethod(ACC_PUBLIC, INIT,
                    Type.getMethodDescriptor(originalCtorType.getReturnType(), newArgTypes), null, null));
            generateOverridingConstructorWithObjectParam(mv2, originalCtorType.getDescriptor());
        }
    }

    // Object additional param accepting constructor for handling ScriptObjectMirror objects, which are
    // unwrapped to work as ScriptObjects or ScriptFunctions. This also handles null and undefined values for
    // script adapters by throwing TypeError on such script adapters.
    private void generateOverridingConstructorWithObjectParam(final InstructionAdapter mv, final String ctorDescriptor) {
        mv.visitCode();
        final int extraArgOffset = emitSuperConstructorCall(mv, ctorDescriptor);

        // Check for ScriptObjectMirror
        mv.visitVarInsn(ALOAD, extraArgOffset);
        mv.instanceOf(SCRIPT_OBJECT_MIRROR_TYPE);
        final Label notMirror = new Label();
        mv.ifeq(notMirror);

        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, extraArgOffset);
        mv.iconst(0);
        UNWRAP_MIRROR.invoke(mv);
        mv.putfield(generatedClassName, DELEGATE_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);

        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, extraArgOffset);
        mv.iconst(1);
        UNWRAP_MIRROR.invoke(mv);
        mv.putfield(generatedClassName, GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);

        final Label done = new Label();

        if (samName != null) {
            mv.visitVarInsn(ALOAD, 0);
            mv.getfield(generatedClassName, DELEGATE_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);
            mv.instanceOf(SCRIPT_FUNCTION_TYPE);
            mv.ifeq(done);

            // Assign "isFunction = true"
            mv.visitVarInsn(ALOAD, 0);
            mv.iconst(1);
            mv.putfield(generatedClassName, IS_FUNCTION_FIELD_NAME, BOOLEAN_TYPE_DESCRIPTOR);

            mv.visitVarInsn(ALOAD, 0);
            mv.dup();
            mv.getfield(generatedClassName, DELEGATE_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);
            mv.checkcast(SCRIPT_FUNCTION_TYPE);
            emitInitCallThis(mv);
            mv.goTo(done);
        }

        mv.visitLabel(notMirror);

        // Throw error if not a ScriptObject
        mv.visitVarInsn(ALOAD, extraArgOffset);
        NOT_AN_OBJECT.invoke(mv);

        mv.visitLabel(done);
        endInitMethod(mv);
    }

    private static void endInitMethod(final InstructionAdapter mv) {
        mv.visitInsn(RETURN);
        endMethod(mv);
    }

    private static void endMethod(final InstructionAdapter mv) {
        mv.visitMaxs(0, 0);
        mv.visitEnd();
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
    }

    private void generateMethods() {
        for(final MethodInfo mi: methodInfos) {
            generateMethod(mi);
        }
    }

    /**
     * Generates a method in the adapter class that adapts a method from the
     * original class. The generated method will either invoke the delegate
     * using a CALL dynamic operation call site (if it is a SAM method and the
     * delegate is a ScriptFunction), or invoke GET_METHOD_PROPERTY dynamic
     * operation with the method name as the argument and then invoke the
     * returned ScriptFunction using the CALL dynamic operation. If
     * GET_METHOD_PROPERTY returns null or undefined (that is, the JS object
     * doesn't provide an implementation for the method) then the method will
     * either do a super invocation to base class, or if the method is abstract,
     * throw an {@link UnsupportedOperationException}. Finally, if
     * GET_METHOD_PROPERTY returns something other than a ScriptFunction, null,
     * or undefined, a TypeError is thrown. The current Global is checked before
     * the dynamic operations, and if it is different  than the Global used to
     * create the adapter, the creating Global is set to be the current Global.
     * In this case, the previously current Global is restored after the
     * invocation. If CALL results in a Throwable that is not one of the
     * method's declared exceptions, and is not an unchecked throwable, then it
     * is wrapped into a {@link RuntimeException} and the runtime exception is
     * thrown.
     * @param mi the method info describing the method to be generated.
     */
    private void generateMethod(final MethodInfo mi) {
        final Method method = mi.method;
        final Class<?>[] exceptions = method.getExceptionTypes();
        final String[] exceptionNames = getExceptionNames(exceptions);
        final MethodType type = mi.type;
        final String methodDesc = type.toMethodDescriptorString();
        final String name = mi.getName();

        final Type asmType = Type.getMethodType(methodDesc);
        final Type[] asmArgTypes = asmType.getArgumentTypes();

        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(getAccessModifiers(method), name,
                methodDesc, null, exceptionNames));
        mv.visitCode();

        final Class<?> returnType = type.returnType();
        final Type asmReturnType = Type.getType(returnType);

        // Determine the first index for a local variable
        int nextLocalVar = 1; // "this" is at 0
        for(final Type t: asmArgTypes) {
            nextLocalVar += t.getSize();
        }
        // Set our local variable index
        final int globalRestoringRunnableVar = nextLocalVar++;

        // Load the creatingGlobal object
        loadField(mv, GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);
        // stack: [creatingGlobal]
        SET_GLOBAL.invoke(mv);
        // stack: [runnable]
        mv.visitVarInsn(ASTORE, globalRestoringRunnableVar);
        // stack: []

        final Label tryBlockStart = new Label();
        mv.visitLabel(tryBlockStart);

        final Label callCallee = new Label();
        final Label defaultBehavior = new Label();
        // If this is a SAM type...
        if (samName != null) {
            // ...every method will be checking whether we're initialized with a
            // function.
            loadField(mv, IS_FUNCTION_FIELD_NAME, BOOLEAN_TYPE_DESCRIPTOR);
            // stack: [isFunction]
            if (name.equals(samName)) {
                final Label notFunction = new Label();
                mv.ifeq(notFunction);
                // stack: []
                // If it's a SAM method, it'll load delegate as the "callee" and
                // "callThis" as "this" for the call if delegate is a function.
                loadField(mv, DELEGATE_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);
                // NOTE: if we added "mv.checkcast(SCRIPT_FUNCTION_TYPE);" here
                // we could emit the invokedynamic CALL instruction with signature
                // (ScriptFunction, Object, ...) instead of (Object, Object, ...).
                // We could combine this with an optimization in
                // ScriptFunction.findCallMethod where it could link a call with a
                // thinner guard when the call site statically guarantees that the
                // callee argument is a ScriptFunction. Additionally, we could use
                // a "ScriptFunction function" field in generated classes instead
                // of a "boolean isFunction" field to avoid the checkcast.
                loadField(mv, CALL_THIS_FIELD_NAME, OBJECT_TYPE_DESCRIPTOR);
                // stack: [callThis, delegate]
                mv.goTo(callCallee);
                mv.visitLabel(notFunction);
            } else {
                // If it's not a SAM method, and the delegate is a function,
                // it'll fall back to default behavior
                mv.ifne(defaultBehavior);
                // stack: []
            }
        }

        // At this point, this is either not a SAM method or the delegate is
        // not a ScriptFunction. We need to emit a GET_METHOD_PROPERTY Nashorn
        // invokedynamic.

        if(name.equals("toString")) {
            // Since every JS Object has a toString, we only override
            // "String toString()" it if it's explicitly specified on the object.
            loadField(mv, DELEGATE_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);
            // stack: [delegate]
            HAS_OWN_TO_STRING.invoke(mv);
            // stack: [hasOwnToString]
            mv.ifeq(defaultBehavior);
        }

        loadField(mv, DELEGATE_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);
        //For the cases like scripted overridden methods invoked from super constructors get adapter global/delegate fields as null, since we
        //cannot set these fields before invoking super constructor better solution is opt out of scripted overridden method if global/delegate fields
        //are null and invoke super method instead
        mv.ifnull(defaultBehavior);
        loadField(mv, DELEGATE_FIELD_NAME, SCRIPT_OBJECT_TYPE_DESCRIPTOR);
        mv.dup();
        // stack: [delegate, delegate]
        final String encodedName = NameCodec.encode(name);
        mv.visitInvokeDynamicInsn(encodedName,
                GET_METHOD_PROPERTY_METHOD_DESCRIPTOR, BOOTSTRAP_HANDLE,
                NashornCallSiteDescriptor.GET_METHOD_PROPERTY);
        // stack: [callee, delegate]
        mv.visitLdcInsn(name);
        // stack: [name, callee, delegate]
        CHECK_FUNCTION.invoke(mv);
        // stack: [fnCalleeOrNull, delegate]
        final Label hasFunction = new Label();
        mv.dup();
        // stack: [fnCalleeOrNull, fnCalleeOrNull, delegate]
        mv.ifnonnull(hasFunction);
        // stack: [null, delegate]
        // If it's null or undefined, clear stack and fall back to default
        // behavior.
        mv.pop2();
        // stack: []

        // We can also arrive here from check for "delegate instanceof ScriptFunction"
        // in a non-SAM method as well as from a check for "hasOwnToString(delegate)"
        // for a toString delegate.
        mv.visitLabel(defaultBehavior);
        final Runnable emitFinally = ()->emitFinally(mv, globalRestoringRunnableVar);
        final Label normalFinally = new Label();
        if(Modifier.isAbstract(method.getModifiers())) {
            // If the super method is abstract, throw UnsupportedOperationException
            UNSUPPORTED.invoke(mv);
            // NOTE: no need to invoke emitFinally.run() as we're inside the
            // tryBlockStart/tryBlockEnd range, so throwing this exception will
            // transfer control to the rethrow handler and the finally block in it
            // will execute.
            mv.athrow();
        } else {
            // If the super method is not abstract, delegate to it.
            emitSuperCall(mv, method.getDeclaringClass(), name, methodDesc);
            mv.goTo(normalFinally);
        }

        mv.visitLabel(hasFunction);
        // stack: [callee, delegate]
        mv.swap();
        // stack [delegate, callee]
        mv.visitLabel(callCallee);


        // Load all parameters back on stack for dynamic invocation.

        int varOffset = 1;
        // If the param list length is more than 253 slots, we can't invoke it
        // directly as with (callee, this) it'll exceed 255.
        final boolean isVarArgCall = getParamListLengthInSlots(asmArgTypes) > 253;
        for (final Type t : asmArgTypes) {
            mv.load(varOffset, t);
            convertParam(mv, t, isVarArgCall);
            varOffset += t.getSize();
        }
        // stack: [args..., callee, delegate]

        // If the resulting parameter list length is too long...
        if (isVarArgCall) {
            // ... we pack the parameters (except callee and this) into an array
            // and use Nashorn vararg invocation.
            mv.visitInvokeDynamicInsn(NameCodec.EMPTY_NAME,
                    getArrayCreatorMethodType(type).toMethodDescriptorString(),
                    CREATE_ARRAY_BOOTSTRAP_HANDLE);
        }

        // Invoke the target method handle
        mv.visitInvokeDynamicInsn(encodedName,
                getCallMethodType(isVarArgCall, type).toMethodDescriptorString(),
                BOOTSTRAP_HANDLE, NashornCallSiteDescriptor.CALL);
        // stack: [returnValue]
        convertReturnValue(mv, returnType);
        mv.visitLabel(normalFinally);
        emitFinally.run();
        mv.areturn(asmReturnType);

        // If Throwable is not declared, we need an adapter from Throwable to RuntimeException
        final boolean throwableDeclared = isThrowableDeclared(exceptions);
        final Label throwableHandler;
        if (!throwableDeclared) {
            // Add "throw new RuntimeException(Throwable)" handler for Throwable
            throwableHandler = new Label();
            mv.visitLabel(throwableHandler);
            WRAP_THROWABLE.invoke(mv);
            // Fall through to rethrow handler
        } else {
            throwableHandler = null;
        }
        final Label rethrowHandler = new Label();
        mv.visitLabel(rethrowHandler);
        // Rethrow handler for RuntimeException, Error, and all declared exception types
        emitFinally.run();
        mv.athrow();

        if(throwableDeclared) {
            mv.visitTryCatchBlock(tryBlockStart, normalFinally, rethrowHandler, THROWABLE_TYPE_NAME);
            assert throwableHandler == null;
        } else {
            mv.visitTryCatchBlock(tryBlockStart, normalFinally, rethrowHandler, RUNTIME_EXCEPTION_TYPE_NAME);
            mv.visitTryCatchBlock(tryBlockStart, normalFinally, rethrowHandler, ERROR_TYPE_NAME);
            for(final String excName: exceptionNames) {
                mv.visitTryCatchBlock(tryBlockStart, normalFinally, rethrowHandler, excName);
            }
            mv.visitTryCatchBlock(tryBlockStart, normalFinally, throwableHandler, THROWABLE_TYPE_NAME);
        }
        endMethod(mv);
    }

    private static MethodType getCallMethodType(final boolean isVarArgCall, final MethodType type) {
        final Class<?>[] callParamTypes;
        if (isVarArgCall) {
            // Variable arity calls are always (Object callee, Object this, Object[] params)
            callParamTypes = new Class<?>[] { Object.class, Object.class, Object[].class };
        } else {
            // Adjust invocation type signature for conversions we instituted in
            // convertParam; also, byte and short get passed as ints.
            final Class<?>[] origParamTypes = type.parameterArray();
            callParamTypes = new Class<?>[origParamTypes.length + 2];
            callParamTypes[0] = Object.class; // callee; could be ScriptFunction.class ostensibly
            callParamTypes[1] = Object.class; // this
            for(int i = 0; i < origParamTypes.length; ++i) {
                callParamTypes[i + 2] = getNashornParamType(origParamTypes[i], false);
            }
        }
        return MethodType.methodType(getNashornReturnType(type.returnType()), callParamTypes);
    }

    private static MethodType getArrayCreatorMethodType(final MethodType type) {
        final Class<?>[] callParamTypes = type.parameterArray();
        for(int i = 0; i < callParamTypes.length; ++i) {
            callParamTypes[i] = getNashornParamType(callParamTypes[i], true);
        }
        return MethodType.methodType(Object[].class, callParamTypes);
    }

    private static Class<?> getNashornParamType(final Class<?> clazz, final boolean varArg) {
        if (clazz == byte.class || clazz == short.class) {
            return int.class;
        } else if (clazz == float.class) {
            // If using variable arity, we'll pass a Double instead of double
            // so that floats don't extend the length of the parameter list.
            // We return Object.class instead of Double.class though as the
            // array collector will anyway operate on Object.
            return varArg ? Object.class : double.class;
        } else if (!clazz.isPrimitive() || clazz == long.class || clazz == char.class) {
            return Object.class;
        }
        return clazz;
    }

    private static Class<?> getNashornReturnType(final Class<?> clazz) {
        if (clazz == byte.class || clazz == short.class) {
            return int.class;
        } else if (clazz == float.class) {
            return double.class;
        } else if (clazz == void.class || clazz == char.class) {
            return Object.class;
        }
        return clazz;
    }


    private void loadField(final InstructionAdapter mv, final String name, final String desc) {
        if(classOverride) {
            mv.getstatic(generatedClassName, name, desc);
        } else {
            mv.visitVarInsn(ALOAD, 0);
            mv.getfield(generatedClassName, name, desc);
        }
    }

    private static void convertReturnValue(final InstructionAdapter mv, final Class<?> origReturnType) {
        if (origReturnType == void.class) {
            mv.pop();
        } else if (origReturnType == Object.class) {
            // Must hide ConsString (and potentially other internal Nashorn types) from callers
            EXPORT_RETURN_VALUE.invoke(mv);
        } else if (origReturnType == byte.class) {
            mv.visitInsn(I2B);
        } else if (origReturnType == short.class) {
            mv.visitInsn(I2S);
        } else if (origReturnType == float.class) {
            mv.visitInsn(D2F);
        } else if (origReturnType == char.class) {
            TO_CHAR_PRIMITIVE.invoke(mv);
        }
    }

    /**
     * Emits instruction for converting a parameter on the top of the stack to
     * a type that is understood by Nashorn.
     * @param mv the current method visitor
     * @param t the type on the top of the stack
     * @param varArg if the invocation will be variable arity
     */
    private static void convertParam(final InstructionAdapter mv, final Type t, final boolean varArg) {
        // We perform conversions of some primitives to accommodate types that
        // Nashorn can handle.
        switch(t.getSort()) {
        case Type.CHAR:
            // Chars are boxed, as we don't know if the JS code wants to treat
            // them as an effective "unsigned short" or as a single-char string.
            CHAR_VALUE_OF.invoke(mv);
            break;
        case Type.FLOAT:
            // Floats are widened to double.
            mv.visitInsn(Opcodes.F2D);
            if (varArg) {
                // We'll be boxing everything anyway for the vararg invocation,
                // so we might as well do it proactively here and thus not cause
                // a widening in the number of slots, as that could even make
                // the array creation invocation go over 255 param slots.
                DOUBLE_VALUE_OF.invoke(mv);
            }
            break;
        case Type.LONG:
            // Longs are boxed as Nashorn can't represent them precisely as a
            // primitive number.
            LONG_VALUE_OF.invoke(mv);
            break;
        case Type.OBJECT:
            if(t.equals(OBJECT_TYPE)) {
                // Object can carry a ScriptObjectMirror and needs to be unwrapped
                // before passing into a Nashorn function.
                UNWRAP.invoke(mv);
            }
            break;
        }
    }

    private static int getParamListLengthInSlots(final Type[] paramTypes) {
        int len = paramTypes.length;
        for(final Type t: paramTypes) {
            final int sort = t.getSort();
            if (sort == Type.FLOAT || sort == Type.DOUBLE) {
                // Floats are widened to double, so they'll take up two slots.
                // Longs on the other hand are always boxed, so their width
                // becomes 1 and thus they don't contribute an extra slot here.
                ++len;
            }
        }
        return len;
    }

    /**
     * Emit code to restore the previous Nashorn Context when needed.
     * @param mv the instruction adapter
     * @param globalRestoringRunnableVar index of the local variable holding the reference to the global restoring Runnable
     */
    private static void emitFinally(final InstructionAdapter mv, final int globalRestoringRunnableVar) {
        mv.visitVarInsn(ALOAD, globalRestoringRunnableVar);
        RUN.invoke(mv);
    }

    private static boolean isThrowableDeclared(final Class<?>[] exceptions) {
        for (final Class<?> exception : exceptions) {
            if (exception == Throwable.class) {
                return true;
            }
        }
        return false;
    }

    private void generateSuperMethods() {
        for(final MethodInfo mi: methodInfos) {
            if(!Modifier.isAbstract(mi.method.getModifiers())) {
                generateSuperMethod(mi);
            }
        }
    }

    private void generateSuperMethod(final MethodInfo mi) {
        final Method method = mi.method;

        final String methodDesc = mi.type.toMethodDescriptorString();
        final String name = mi.getName();

        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(getAccessModifiers(method),
                SUPER_PREFIX + name, methodDesc, null, getExceptionNames(method.getExceptionTypes())));
        mv.visitCode();

        emitSuperCall(mv, method.getDeclaringClass(), name, methodDesc);
        mv.areturn(Type.getType(mi.type.returnType()));
        endMethod(mv);
    }

    // find the appropriate super type to use for invokespecial on the given interface
    private Class<?> findInvokespecialOwnerFor(final Class<?> cl) {
        assert Modifier.isInterface(cl.getModifiers()) : cl + " is not an interface";

        if (cl.isAssignableFrom(superClass)) {
            return superClass;
        }

        for (final Class<?> iface : interfaces) {
            if (cl.isAssignableFrom(iface)) {
                return iface;
            }
        }

        // we better that interface that extends the given interface!
        throw new AssertionError("can't find the class/interface that extends " + cl);
    }

    private int emitSuperConstructorCall(final InstructionAdapter mv, final String methodDesc) {
        return emitSuperCall(mv, null, INIT, methodDesc, true);
    }

    private int emitSuperCall(final InstructionAdapter mv, final Class<?> owner, final String name, final String methodDesc) {
        return emitSuperCall(mv, owner, name, methodDesc, false);
    }

    private int emitSuperCall(final InstructionAdapter mv, final Class<?> owner, final String name, final String methodDesc, final boolean constructor) {
        mv.visitVarInsn(ALOAD, 0);
        int nextParam = 1;
        final Type methodType = Type.getMethodType(methodDesc);
        for(final Type t: methodType.getArgumentTypes()) {
            mv.load(nextParam, t);
            nextParam += t.getSize();
        }

        // default method - non-abstract, interface method
        if (!constructor && Modifier.isInterface(owner.getModifiers())) {
            // we should call default method on the immediate "super" type - not on (possibly)
            // the indirectly inherited interface class!
            final Class<?> superType = findInvokespecialOwnerFor(owner);
            mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(superType), name, methodDesc,
                Modifier.isInterface(superType.getModifiers()));
        } else {
            mv.invokespecial(superClassName, name, methodDesc, false);
        }
        return nextParam;
    }

    private void generateFinalizerMethods() {
        generateFinalizerDelegate();
        generateFinalizerOverride();
    }

    private void generateFinalizerDelegate() {
        // Generate a delegate that will be invoked from the no-permission trampoline. Note it can be private, as we'll
        // refer to it with a MethodHandle constant pool entry in the overridden finalize() method (see
        // generateFinalizerOverride()).
        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(ACC_PRIVATE | ACC_STATIC,
                FINALIZER_DELEGATE_NAME, FINALIZER_DELEGATE_METHOD_DESCRIPTOR, null, null));

        // Simply invoke super.finalize()
        mv.visitVarInsn(ALOAD, 0);
        mv.checkcast(Type.getType('L' + generatedClassName + ';'));
        mv.invokespecial(superClassName, "finalize", VOID_METHOD_DESCRIPTOR, false);

        mv.visitInsn(RETURN);
        endMethod(mv);
    }

    private void generateFinalizerOverride() {
        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(ACC_PUBLIC, "finalize",
                VOID_METHOD_DESCRIPTOR, null, null));
        // Overridden finalizer will take a MethodHandle to the finalizer delegating method, ...
        mv.aconst(new Handle(Opcodes.H_INVOKESTATIC, generatedClassName, FINALIZER_DELEGATE_NAME,
                FINALIZER_DELEGATE_METHOD_DESCRIPTOR, false));
        mv.visitVarInsn(ALOAD, 0);
        // ...and invoke it through JavaAdapterServices.invokeNoPermissions
        INVOKE_NO_PERMISSIONS.invoke(mv);
        mv.visitInsn(RETURN);
        endMethod(mv);
    }

    private static String[] getExceptionNames(final Class<?>[] exceptions) {
        final String[] exceptionNames = new String[exceptions.length];
        for (int i = 0; i < exceptions.length; ++i) {
            exceptionNames[i] = Type.getInternalName(exceptions[i]);
        }
        return exceptionNames;
    }

    private static int getAccessModifiers(final Method method) {
        return ACC_PUBLIC | (method.isVarArgs() ? ACC_VARARGS : 0);
    }

    /**
     * Gathers methods that can be implemented or overridden from the specified type into this factory's
     * {@link #methodInfos} set. It will add all non-final, non-static methods that are either public or protected from
     * the type if the type itself is public. If the type is a class, the method will recursively invoke itself for its
     * superclass and the interfaces it implements, and add further methods that were not directly declared on the
     * class.
     * @param type the type defining the methods.
     */
    private void gatherMethods(final Class<?> type) throws AdaptationException {
        if (Modifier.isPublic(type.getModifiers())) {
            final Method[] typeMethods = type.isInterface() ? type.getMethods() : type.getDeclaredMethods();

            for (final Method typeMethod: typeMethods) {
                final String name = typeMethod.getName();
                if(name.startsWith(SUPER_PREFIX)) {
                    continue;
                }
                final int m = typeMethod.getModifiers();
                if (Modifier.isStatic(m)) {
                    continue;
                }
                if (Modifier.isPublic(m) || Modifier.isProtected(m)) {
                    // Is it a "finalize()"?
                    if(name.equals("finalize") && typeMethod.getParameterCount() == 0) {
                        if(type != Object.class) {
                            hasExplicitFinalizer = true;
                            if(Modifier.isFinal(m)) {
                                // Must be able to override an explicit finalizer
                                throw new AdaptationException(Outcome.ERROR_FINAL_FINALIZER, type.getCanonicalName());
                            }
                        }
                        continue;
                    }

                    final MethodInfo mi = new MethodInfo(typeMethod);
                    if (Modifier.isFinal(m) || isCallerSensitive(typeMethod)) {
                        finalMethods.add(mi);
                    } else if (!finalMethods.contains(mi) && methodInfos.add(mi) && Modifier.isAbstract(m)) {
                        abstractMethodNames.add(mi.getName());
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
            final Class<?> superType = type.getSuperclass();
            if (superType != null) {
                gatherMethods(superType);
            }
            for (final Class<?> itf: type.getInterfaces()) {
                gatherMethods(itf);
            }
        }
    }

    private void gatherMethods(final List<Class<?>> classes) throws AdaptationException {
        for(final Class<?> c: classes) {
            gatherMethods(c);
        }
    }

    private static final AccessControlContext GET_DECLARED_MEMBERS_ACC_CTXT = ClassAndLoader.createPermAccCtxt("accessDeclaredMembers");

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
        }, GET_DECLARED_MEMBERS_ACC_CTXT);
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
                return OBJECT_TYPE.getInternalName();
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

    private static boolean isCallerSensitive(final AccessibleObject e) {
        return e.isAnnotationPresent(CallerSensitive.class);
    }

    private static Call lookupServiceMethod(final String name, final Class<?> rtype, final Class<?>... ptypes) {
        return staticCallNoLookup(JavaAdapterServices.class, name, rtype, ptypes);
    }
}
