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

package jdk.nashorn.internal.codegen;

import static jdk.nashorn.internal.codegen.Compiler.SCRIPTS_PACKAGE;
import static jdk.nashorn.internal.codegen.CompilerConstants.ALLOCATE;
import static jdk.nashorn.internal.codegen.CompilerConstants.INIT_ARGUMENTS;
import static jdk.nashorn.internal.codegen.CompilerConstants.INIT_MAP;
import static jdk.nashorn.internal.codegen.CompilerConstants.INIT_SCOPE;
import static jdk.nashorn.internal.codegen.CompilerConstants.JAVA_THIS;
import static jdk.nashorn.internal.codegen.CompilerConstants.JS_OBJECT_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.className;
import static jdk.nashorn.internal.codegen.CompilerConstants.constructorNoLookup;
import static jdk.nashorn.internal.lookup.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import jdk.nashorn.internal.codegen.ClassEmitter.Flag;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.runtime.AccessorProperty;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.DebugLogger;
import jdk.nashorn.internal.runtime.FunctionScope;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptEnvironment;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * Generates the ScriptObject subclass structure with fields for a user objects.
 */
public final class ObjectClassGenerator {

    /**
     * Marker for scope parameters.
     */
    static final String SCOPE_MARKER = "P";

    /**
     * Minimum number of extra fields in an object.
     */
    static final int FIELD_PADDING  = 4;

    /**
     * Rounding when calculating the number of fields.
     */
    static final int FIELD_ROUNDING = 4;

    /**
     * Debug field logger
     * Should we print debugging information for fields when they are generated and getters/setters are called?
     */
    public static final DebugLogger LOG = new DebugLogger("fields", "nashorn.fields.debug");

    /**
     * is field debugging enabled. Several modules in codegen and properties use this, hence
     * public access.
     */
    public static final boolean DEBUG_FIELDS = LOG.isEnabled();

    /**
     * Should the runtime only use java.lang.Object slots for fields? If this is false, the representation
     * will be a primitive 64-bit long value used for all primitives and a java.lang.Object for references.
     * This introduces a larger number of method handles in the system, as we need to have different getters
     * and setters for the different fields. Currently this introduces significant overhead in Hotspot.
     *
     * This is engineered to plug into the TaggedArray implementation, when it's done.
     */
    public static final boolean OBJECT_FIELDS_ONLY = !Options.getBooleanProperty("nashorn.fields.dual");

    /** The field types in the system */
    private static final List<Type> FIELD_TYPES = new LinkedList<>();

    /** What type is the primitive type in dual representation */
    public static final Type PRIMITIVE_TYPE = Type.LONG;

    /**
     * The list of field types that we support - one type creates one field. This is currently either
     * LONG + OBJECT or just OBJECT for classic mode.
     */
    static {
        if (!OBJECT_FIELDS_ONLY) {
            System.err.println("WARNING!!! Running with primitive fields - there is untested functionality!");
            FIELD_TYPES.add(PRIMITIVE_TYPE);
        }
        FIELD_TYPES.add(Type.OBJECT);
    }

    /** The context */
    private final Context context;

    /**
     * The list of available accessor types in width order. This order is used for type guesses narrow{@literal ->} wide
     *  in the dual--fields world
     */
    public static final List<Type> ACCESSOR_TYPES = Collections.unmodifiableList(
            Arrays.asList(
                Type.INT,
                Type.LONG,
                Type.NUMBER,
                Type.OBJECT));

    //these are hard coded for speed and so that we can switch on them
    private static final int TYPE_INT_INDEX    = 0; //getAccessorTypeIndex(int.class);
    private static final int TYPE_LONG_INDEX   = 1; //getAccessorTypeIndex(long.class);
    private static final int TYPE_DOUBLE_INDEX = 2; //getAccessorTypeIndex(double.class);
    private static final int TYPE_OBJECT_INDEX = 3; //getAccessorTypeIndex(Object.class);

    /**
     * Constructor
     *
     * @param context a context
     */
    public ObjectClassGenerator(final Context context) {
        this.context = context;
        assert context != null;
    }

    /**
     * Given a type of an accessor, return its index in [0..getNumberOfAccessorTypes())
     *
     * @param type the type
     *
     * @return the accessor index, or -1 if no accessor of this type exists
     */
    public static int getAccessorTypeIndex(final Type type) {
        return getAccessorTypeIndex(type.getTypeClass());
    }

    /**
     * Given a class of an accessor, return its index in [0..getNumberOfAccessorTypes())
     *
     * Note that this is hardcoded with respect to the dynamic contents of the accessor
     * types array for speed. Hotspot got stuck with this as 5% of the runtime in
     * a benchmark when it looped over values and increased an index counter. :-(
     *
     * @param type the type
     *
     * @return the accessor index, or -1 if no accessor of this type exists
     */
    public static int getAccessorTypeIndex(final Class<?> type) {
        if (type == int.class) {
            return 0;
        } else if (type == long.class) {
            return 1;
        } else if (type == double.class) {
            return 2;
        } else if (!type.isPrimitive()) {
            return 3;
        }
        return -1;
    }

    /**
     * Return the number of accessor types available.
     *
     * @return number of accessor types in system
     */
    public static int getNumberOfAccessorTypes() {
        return ACCESSOR_TYPES.size();
    }

    /**
     * Return the accessor type based on its index in [0..getNumberOfAccessorTypes())
     * Indexes are ordered narrower{@literal ->}wider / optimistic{@literal ->}pessimistic. Invalidations always
     * go to a type of higher index
     *
     * @param index accessor type index
     *
     * @return a type corresponding to the index.
     */

    public static Type getAccessorType(final int index) {
        return ACCESSOR_TYPES.get(index);
    }

    /**
     * Returns the class name for JavaScript objects with fieldCount fields.
     *
     * @param fieldCount Number of fields to allocate.
     *
     * @return The class name.
     */
    public static String getClassName(final int fieldCount) {
        return fieldCount != 0 ? SCRIPTS_PACKAGE + '/' + JS_OBJECT_PREFIX.symbolName() + fieldCount :
                                 SCRIPTS_PACKAGE + '/' + JS_OBJECT_PREFIX.symbolName();
    }

    /**
     * Returns the class name for JavaScript scope with fieldCount fields and
     * paramCount parameters.
     *
     * @param fieldCount Number of fields to allocate.
     * @param paramCount Number of parameters to allocate
     *
     * @return The class name.
     */
    public static String getClassName(final int fieldCount, final int paramCount) {
        return SCRIPTS_PACKAGE + '/' + JS_OBJECT_PREFIX.symbolName() + fieldCount + SCOPE_MARKER + paramCount;
    }

    /**
     * Returns the number of fields in the JavaScript scope class. Its name had to be generated using either
     * {@link #getClassName(int)} or {@link #getClassName(int, int)}.
     * @param clazz the JavaScript scope class.
     * @return the number of fields in the scope class.
     */
    public static int getFieldCount(Class<?> clazz) {
        final String name = clazz.getSimpleName();
        final String prefix = JS_OBJECT_PREFIX.symbolName();
        if(prefix.equals(name)) {
            return 0;
        }
        final int scopeMarker = name.indexOf(SCOPE_MARKER);
        return Integer.parseInt(scopeMarker == -1 ? name.substring(prefix.length()) : name.substring(prefix.length(), scopeMarker));
    }

    /**
     * Returns the name of a field based on number and type.
     *
     * @param fieldIndex Ordinal of field.
     * @param type       Type of field.
     *
     * @return The field name.
     */
    public static String getFieldName(final int fieldIndex, final Type type) {
        return type.getDescriptor().substring(0, 1) + fieldIndex;
    }

    /**
     * In the world of Object fields, we also have no undefined SwitchPoint, to reduce as much potential
     * MethodHandle overhead as possible. In that case, we explicitly need to assign undefined to fields
     * when we initialize them.
     *
     * @param init       constructor to generate code in
     * @param className  name of class
     * @param fieldNames fields to initialize to undefined, where applicable
     */
    private static void initializeToUndefined(final MethodEmitter init, final String className, final List<String> fieldNames) {
        if (fieldNames.isEmpty()) {
            return;
        }

        // always initialize fields to undefined, even with --dual-fields. Then it's ok to
        // remember things like "widest set type" in properties, and if it's object, don't
        // add any special "return undefined" getters, saving an invalidation
        init.load(Type.OBJECT, JAVA_THIS.slot());
        init.loadUndefined(Type.OBJECT);

        final Iterator<String> iter = fieldNames.iterator();
        while (iter.hasNext()) {
            final String fieldName = iter.next();
            if (iter.hasNext()) {
                init.dup2();
            }
            init.putField(className, fieldName, Type.OBJECT.getDescriptor());
        }
    }

    /**
     * Generate the byte codes for a JavaScript object class or scope.
     * Class name is a function of number of fields and number of param
     * fields
     *
     * @param descriptor Descriptor pulled from class name.
     *
     * @return Byte codes for generated class.
     */
    public byte[] generate(final String descriptor) {
        final String[] counts     = descriptor.split(SCOPE_MARKER);
        final int      fieldCount = Integer.valueOf(counts[0]);

        if (counts.length == 1) {
            return generate(fieldCount);
        }

        final int paramCount = Integer.valueOf(counts[1]);

        return generate(fieldCount, paramCount);
    }

    /**
     * Generate the byte codes for a JavaScript object class with fieldCount fields.
     *
     * @param fieldCount Number of fields in the JavaScript object.
     *
     * @return Byte codes for generated class.
     */
    public byte[] generate(final int fieldCount) {
        final String       className    = getClassName(fieldCount);
        final String       superName    = className(ScriptObject.class);
        final ClassEmitter classEmitter = newClassEmitter(className, superName);
        final List<String> initFields   = addFields(classEmitter, fieldCount);

        final MethodEmitter init = newInitMethod(classEmitter);
        initializeToUndefined(init, className, initFields);
        init.returnVoid();
        init.end();

        newEmptyInit(classEmitter, className);
        newAllocate(classEmitter, className);

        return toByteArray(classEmitter);
    }

    /**
     * Generate the byte codes for a JavaScript scope class with fieldCount fields
     * and paramCount parameters.
     *
     * @param fieldCount Number of fields in the JavaScript scope.
     * @param paramCount Number of parameters in the JavaScript scope
     * .
     * @return Byte codes for generated class.
     */
    public byte[] generate(final int fieldCount, final int paramCount) {
        final String className          = getClassName(fieldCount, paramCount);
        final String superName          = className(FunctionScope.class);
        final ClassEmitter classEmitter = newClassEmitter(className, superName);
        final List<String> initFields   = addFields(classEmitter, fieldCount);

        final MethodEmitter init = newInitScopeMethod(classEmitter);
        initializeToUndefined(init, className, initFields);
        init.returnVoid();
        init.end();

        final MethodEmitter initWithArguments = newInitScopeWithArgumentsMethod(classEmitter);
        initializeToUndefined(initWithArguments, className, initFields);
        initWithArguments.returnVoid();
        initWithArguments.end();

        return toByteArray(classEmitter);
    }

    /**
     * Generates the needed fields.
     *
     * @param classEmitter Open class emitter.
     * @param fieldCount   Number of fields.
     *
     * @return List fields that need to be initialized.
     */
    private static List<String> addFields(final ClassEmitter classEmitter, final int fieldCount) {
        final List<String> initFields = new LinkedList<>();

        for (int i = 0; i < fieldCount; i++) {
            for (final Type type : FIELD_TYPES) {
                final String fieldName = getFieldName(i, type);
                classEmitter.field(fieldName, type.getTypeClass());

                if (type == Type.OBJECT) {
                    initFields.add(fieldName);
                }
            }
        }

        return initFields;
    }

    /**
     * Allocate and initialize a new class emitter.
     *
     * @param className Name of JavaScript class.
     *
     * @return Open class emitter.
     */
    private ClassEmitter newClassEmitter(final String className, final String superName) {
        final ClassEmitter classEmitter = new ClassEmitter(context.getEnv(), className, superName);
        classEmitter.begin();

        return classEmitter;
    }

    /**
     * Allocate and initialize a new <init> method.
     *
     * @param classEmitter  Open class emitter.
     *
     * @return Open method emitter.
     */
    private static MethodEmitter newInitMethod(final ClassEmitter classEmitter) {
        final MethodEmitter init = classEmitter.init(PropertyMap.class);
        init.begin();
        init.load(Type.OBJECT, JAVA_THIS.slot());
        init.load(Type.OBJECT, INIT_MAP.slot());
        init.invoke(constructorNoLookup(ScriptObject.class, PropertyMap.class));

        return init;
    }

    /**
     * Allocate and initialize a new <init> method for scopes.
     * @param classEmitter  Open class emitter.
     * @return Open method emitter.
     */
    private static MethodEmitter newInitScopeMethod(final ClassEmitter classEmitter) {
        final MethodEmitter init = classEmitter.init(PropertyMap.class, ScriptObject.class);
        init.begin();
        init.load(Type.OBJECT, JAVA_THIS.slot());
        init.load(Type.OBJECT, INIT_MAP.slot());
        init.load(Type.OBJECT, INIT_SCOPE.slot());
        init.invoke(constructorNoLookup(FunctionScope.class, PropertyMap.class, ScriptObject.class));

        return init;
    }

    /**
     * Allocate and initialize a new <init> method for scopes with arguments.
     * @param classEmitter  Open class emitter.
     * @return Open method emitter.
     */
    private static MethodEmitter newInitScopeWithArgumentsMethod(final ClassEmitter classEmitter) {
        final MethodEmitter init = classEmitter.init(PropertyMap.class, ScriptObject.class, Object.class);
        init.begin();
        init.load(Type.OBJECT, JAVA_THIS.slot());
        init.load(Type.OBJECT, INIT_MAP.slot());
        init.load(Type.OBJECT, INIT_SCOPE.slot());
        init.load(Type.OBJECT, INIT_ARGUMENTS.slot());
        init.invoke(constructorNoLookup(FunctionScope.class, PropertyMap.class, ScriptObject.class, Object.class));

        return init;
    }

    /**
     * Add an empty <init> method to the JavaScript class.
     *
     * @param classEmitter Open class emitter.
     * @param className    Name of JavaScript class.
     */
    private static void newEmptyInit(final ClassEmitter classEmitter, final String className) {
        final MethodEmitter emptyInit = classEmitter.init();
        emptyInit.begin();
        emptyInit.load(Type.OBJECT, JAVA_THIS.slot());
        emptyInit.loadNull();
        emptyInit.invoke(constructorNoLookup(className, PropertyMap.class));
        emptyInit.returnVoid();
        emptyInit.end();
    }

    /**
     * Add an empty <init> method to the JavaScript class.
     *
     * @param classEmitter Open class emitter.
     * @param className    Name of JavaScript class.
     */
    private static void newAllocate(final ClassEmitter classEmitter, final String className) {
        final MethodEmitter allocate = classEmitter.method(EnumSet.of(Flag.PUBLIC, Flag.STATIC), ALLOCATE.symbolName(), ScriptObject.class, PropertyMap.class);
        allocate.begin();
        allocate._new(className);
        allocate.dup();
        allocate.load(Type.typeFor(PropertyMap.class), 0);
        allocate.invoke(constructorNoLookup(className, PropertyMap.class));
        allocate._return();
        allocate.end();
    }

    /**
     * Collects the byte codes for a generated JavaScript class.
     *
     * @param classEmitter Open class emitter.
     * @return Byte codes for the class.
     */
    private byte[] toByteArray(final ClassEmitter classEmitter) {
        classEmitter.end();

        final byte[] code = classEmitter.toByteArray();
        final ScriptEnvironment env = context.getEnv();

        if (env._print_code) {
            env.getErr().println(ClassEmitter.disassemble(code));
        }

        if (env._verify_code) {
            context.verify(code);
        }

        return code;
    }

    /** Double to long bits, used with --dual-fields for primitive double values */
    private static final MethodHandle PACK_DOUBLE =
        MH.explicitCastArguments(MH.findStatic(MethodHandles.publicLookup(), Double.class, "doubleToRawLongBits", MH.type(long.class, double.class)), MH.type(long.class, double.class));

    /** double bits to long, used with --dual-fields for primitive double values */
    private static MethodHandle UNPACK_DOUBLE =
        MH.findStatic(MethodHandles.publicLookup(), Double.class, "longBitsToDouble", MH.type(double.class, long.class));

    /** object conversion quickies with JS semantics - used for return value and parameter filter */
    private static MethodHandle[] CONVERT_OBJECT = {
        JSType.TO_INT32.methodHandle(),
        JSType.TO_UINT32.methodHandle(),
        JSType.TO_NUMBER.methodHandle(),
        null
    };

    /**
     * Given a primitiveGetter (optional for non dual fields) and an objectSetter that retrieve
     * the primitive and object version of a field respectively, return one with the correct
     * method type and the correct filters. For example, if the value is stored as a double
     * and we want an Object getter, in the dual fields world we'd pick the primitiveGetter,
     * which reads a long, use longBitsToDouble on the result to unpack it, and then change the
     * return type to Object, boxing it. In the objects only world there are only object fields,
     * primtives are boxed when asked for them and we don't need to bother with primitive encoding
     * (or even undefined, which if forType==null) representation, so we just return whatever is
     * in the object field. The object field is always initiated to Undefined, so here, where we have
     * the representation for Undefined in all our bits, this is not a problem.
     * <p>
     * Representing undefined in a primitive is hard, for an int there aren't enough bits, for a long
     * we could limit the width of a representation, and for a double (as long as it is stored as long,
     * as all NaNs will turn into QNaN on ia32, which is one bit pattern, we should use a special NaN).
     * Naturally we could have special undefined values for all types which mean "go look in a wider field",
     * but the guards needed on every getter took too much time.
     * <p>
     * To see how this is used, look for example in {@link AccessorProperty#getGetter}
     * <p>
     * @param forType         representation of the underlying type in the field, null if undefined
     * @param type            type to retrieve it as
     * @param primitiveGetter getter to read the primitive version of this field (null if Objects Only)
     * @param objectGetter    getter to read the object version of this field
     *
     * @return getter for the given representation that returns the given type
     */
    public static MethodHandle createGetter(final Class<?> forType, final Class<?> type, final MethodHandle primitiveGetter, final MethodHandle objectGetter) {
        final int fti = forType == null ? -1 : getAccessorTypeIndex(forType);
        final int ti  = getAccessorTypeIndex(type);

        if (fti == TYPE_OBJECT_INDEX || OBJECT_FIELDS_ONLY) {
            if (ti == TYPE_OBJECT_INDEX) {
                return objectGetter;
            }

            return MH.filterReturnValue(objectGetter, CONVERT_OBJECT[ti]);
        }

        assert !OBJECT_FIELDS_ONLY;
        if (forType == null) {
            return GET_UNDEFINED[ti];
        }

        final MethodType pmt = primitiveGetter.type();

        switch (fti) {
        case TYPE_INT_INDEX:
        case TYPE_LONG_INDEX:
            switch (ti) {
            case TYPE_INT_INDEX:
                //get int while an int, truncating cast of long value
                return MH.explicitCastArguments(primitiveGetter, pmt.changeReturnType(int.class));
            case TYPE_LONG_INDEX:
                return primitiveGetter;
            default:
                return MH.asType(primitiveGetter, pmt.changeReturnType(type));
            }
        case TYPE_DOUBLE_INDEX:
            final MethodHandle getPrimitiveAsDouble = MH.filterReturnValue(primitiveGetter, UNPACK_DOUBLE);
            switch (ti) {
            case TYPE_INT_INDEX:
            case TYPE_LONG_INDEX:
                return MH.explicitCastArguments(getPrimitiveAsDouble, pmt.changeReturnType(type));
            case TYPE_DOUBLE_INDEX:
                return getPrimitiveAsDouble;
            default:
                return MH.asType(getPrimitiveAsDouble, pmt.changeReturnType(Object.class));
            }
        default:
            assert false;
            return null;
        }
    }

    private static final MethodHandle IS_TYPE_GUARD = findOwnMH("isType", boolean.class, Class.class, Object.class);

    @SuppressWarnings("unused")
    private static boolean isType(final Class<?> boxedForType, final Object x) {
        return x.getClass() == boxedForType;
    }

    private static Class<? extends Number> getBoxedType(final Class<?> forType) {
        if (forType == int.class) {
            return Integer.class;
        }

        if (forType == long.class) {
            return Long.class;
        }

        if (forType == double.class) {
            return Double.class;
        }

        assert false;
        return null;
    }

    /**
     * If we are setting boxed types (because the compiler couldn't determine which they were) to
     * a primitive field, we can reuse the primitive field getter, as long as we are setting an element
     * of the same boxed type as the primitive type representation
     *
     * @param forType           the current type
     * @param primitiveSetter   primitive setter for the current type with an element of the current type
     * @param objectSetter      the object setter
     *
     * @return method handle that checks if the element to be set is of the currenttype, even though it's boxed
     *  and instead of using the generic object setter, that would blow up the type and invalidate the map,
     *  unbox it and call the primitive setter instead
     */
    public static MethodHandle createGuardBoxedPrimitiveSetter(final Class<?> forType, final MethodHandle primitiveSetter, final MethodHandle objectSetter) {
        final Class<? extends Number> boxedForType = getBoxedType(forType);
        //object setter that checks for primitive if current type is primitive

        return MH.guardWithTest(
            MH.insertArguments(
                MH.dropArguments(
                    IS_TYPE_GUARD,
                    1,
                    Object.class),
                0,
                boxedForType),
                MH.asType(
                    primitiveSetter,
                    objectSetter.type()),
                objectSetter);
    }

    /**
     * This is similar to the {@link ObjectClassGenerator#createGetter} function. Performs
     * the necessary operations to massage a setter operand of type {@code type} to
     * fit into the primitive field (if primitive and dual fields is enabled) or into
     * the object field (box if primitive and dual fields is disabled)
     *
     * @param forType         representation of the underlying object
     * @param type            representation of field to write, and setter signature
     * @param primitiveSetter setter that writes to the primitive field (null if Objects Only)
     * @param objectSetter    setter that writes to the object field
     *
     * @return the setter for the given representation that takes a {@code type}
     */
    public static MethodHandle createSetter(final Class<?> forType, final Class<?> type, final MethodHandle primitiveSetter, final MethodHandle objectSetter) {
        assert forType != null;

        final int fti = getAccessorTypeIndex(forType);
        final int ti  = getAccessorTypeIndex(type);

        if (fti == TYPE_OBJECT_INDEX || OBJECT_FIELDS_ONLY) {
            if (ti == TYPE_OBJECT_INDEX) {
                return objectSetter;
            }

            return MH.asType(objectSetter, objectSetter.type().changeParameterType(1, type));
        }

        assert !OBJECT_FIELDS_ONLY;

        final MethodType pmt = primitiveSetter.type();

        switch (fti) {
        case TYPE_INT_INDEX:
        case TYPE_LONG_INDEX:
            switch (ti) {
            case TYPE_INT_INDEX:
                return MH.asType(primitiveSetter, pmt.changeParameterType(1, int.class));
            case TYPE_LONG_INDEX:
                return primitiveSetter;
            case TYPE_DOUBLE_INDEX:
                return MH.filterArguments(primitiveSetter, 1, PACK_DOUBLE);
            default:
                return objectSetter;
            }
        case TYPE_DOUBLE_INDEX:
            if (ti == TYPE_OBJECT_INDEX) {
                return objectSetter;
            }
            return MH.asType(MH.filterArguments(primitiveSetter, 1, PACK_DOUBLE), pmt.changeParameterType(1, type));
        default:
            assert false;
            return null;
        }
    }

    //
    // Provide generic getters and setters for undefined types. If a type is undefined, all
    // and marshals the set to the correct setter depending on the type of the value being set.
    // Note that there are no actual undefined versions of int, long and double in JavaScript,
    // but executing toInt32, toLong and toNumber always returns a working result, 0, 0L or NaN
    //

    /** The value of Undefined cast to an int32 */
    public static final int    UNDEFINED_INT    = 0;
    /** The value of Undefined cast to a long */
    public static final long   UNDEFINED_LONG   = 0L;
    /** The value of Undefined cast to a double */
    public static final double UNDEFINED_DOUBLE = Double.NaN;

    /**
     * Compute type name for correct undefined getter
     * @param type the type
     * @return name of getter
     */
    private static String typeName(final Type type) {
        String name = type.getTypeClass().getName();
        final int dot = name.lastIndexOf('.');
        if (dot != -1) {
            name = name.substring(dot + 1);
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * Handles for undefined getters of the different types
     */
    private static final MethodHandle[] GET_UNDEFINED = new MethodHandle[ObjectClassGenerator.getNumberOfAccessorTypes()];

    /**
     * Used to wrap getters for undefined values, where this matters. Currently only in dual fields.
     * If an object starts out as undefined it needs special getters until it has been assigned
     * something the first time
     *
     * @param returnType type to cast the undefined to
     *
     * @return undefined as returnType
     */
    public static MethodHandle getUndefined(final Class<?> returnType) {
        return GET_UNDEFINED[ObjectClassGenerator.getAccessorTypeIndex(returnType)];
    }

    static {
        int pos = 0;
        for (final Type type : ACCESSOR_TYPES) {
            GET_UNDEFINED[pos++] = findOwnMH("getUndefined" + typeName(type), type.getTypeClass(), Object.class);
        }
    }

    @SuppressWarnings("unused")
    private static int getUndefinedInt(final Object obj) {
        return UNDEFINED_INT;
    }

    @SuppressWarnings("unused")
    private static long getUndefinedLong(final Object obj) {
        return UNDEFINED_LONG;
    }

    @SuppressWarnings("unused")
    private static double getUndefinedDouble(final Object obj) {
        return UNDEFINED_DOUBLE;
    }

    @SuppressWarnings("unused")
    private static Object getUndefinedObject(final Object obj) {
        return ScriptRuntime.UNDEFINED;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), ObjectClassGenerator.class, name, MH.type(rtype, types));
    }
}
