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
import static jdk.nashorn.internal.runtime.JSType.CONVERT_OBJECT;
import static jdk.nashorn.internal.runtime.JSType.CONVERT_OBJECT_OPTIMISTIC;
import static jdk.nashorn.internal.runtime.JSType.GET_UNDEFINED;
import static jdk.nashorn.internal.runtime.JSType.TYPE_DOUBLE_INDEX;
import static jdk.nashorn.internal.runtime.JSType.TYPE_INT_INDEX;
import static jdk.nashorn.internal.runtime.JSType.TYPE_LONG_INDEX;
import static jdk.nashorn.internal.runtime.JSType.TYPE_OBJECT_INDEX;
import static jdk.nashorn.internal.runtime.JSType.TYPE_UNDEFINED_INDEX;
import static jdk.nashorn.internal.runtime.JSType.getAccessorTypeIndex;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.isValid;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import jdk.nashorn.internal.codegen.ClassEmitter.Flag;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.runtime.AccessorProperty;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.FunctionScope;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptEnvironment;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.Undefined;
import jdk.nashorn.internal.runtime.UnwarrantedOptimismException;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import jdk.nashorn.internal.runtime.logging.Loggable;
import jdk.nashorn.internal.runtime.logging.Logger;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * Generates the ScriptObject subclass structure with fields for a user objects.
 */
@Logger(name="fields")
public final class ObjectClassGenerator implements Loggable {

    /**
     * Type guard to make sure we don't unnecessarily explode field storages. Rather unbox e.g.
     * a java.lang.Number than blow up the field. Gradually, optimistic types should create almost
     * no boxed types
     */
    private static final MethodHandle IS_TYPE_GUARD = findOwnMH("isType", boolean.class, Class.class, Object.class);

    /**
     * Marker for scope parameters
     */
    private static final String SCOPE_MARKER = "P";

    /**
     * Minimum number of extra fields in an object.
     */
    static final int FIELD_PADDING  = 4;

    /**
     * Debug field logger
     * Should we print debugging information for fields when they are generated and getters/setters are called?
     */
    private final DebugLogger log;

    /**
     * Should the runtime only use java.lang.Object slots for fields? If this is false, the representation
     * will be a primitive 64-bit long value used for all primitives and a java.lang.Object for references.
     * This introduces a larger number of method handles in the system, as we need to have different getters
     * and setters for the different fields.
     *
     * This is engineered to plug into the TaggedArray implementation, when it's done.
     */
    public static final boolean OBJECT_FIELDS_ONLY = Options.getBooleanProperty("nashorn.fields.objects");

    /** The field types in the system */
    private static final List<Type> FIELD_TYPES = new LinkedList<>();

    /** What type is the primitive type in dual representation */
    public static final Type PRIMITIVE_FIELD_TYPE = Type.LONG;

    private static final MethodHandle GET_DIFFERENT           = findOwnMH("getDifferent", Object.class, Object.class, Class.class, MethodHandle.class, MethodHandle.class, int.class);
    private static final MethodHandle GET_DIFFERENT_UNDEFINED = findOwnMH("getDifferentUndefined", Object.class, int.class);

    /**
     * The list of field types that we support - one type creates one field. This is currently either
     * LONG + OBJECT or just OBJECT for classic mode.
     */
    static {
        if (!OBJECT_FIELDS_ONLY) {
            FIELD_TYPES.add(PRIMITIVE_FIELD_TYPE);
        }
        FIELD_TYPES.add(Type.OBJECT);
    }
    private static boolean initialized = false;

    /** The context */
    private final Context context;

    /**
     * Constructor
     *
     * @param context a context
     */
    public ObjectClassGenerator(final Context context) {
        this.context = context;
        assert context != null;
        this.log = initLogger(context);
        if (!initialized) {
            initialized = true;
            if (OBJECT_FIELDS_ONLY) {
                log.warning("Running with object fields only - this is a deprecated configuration.");
            }
        }
    }

    @Override
    public DebugLogger getLogger() {
        return log;
    }

    @Override
    public DebugLogger initLogger(final Context ctxt) {
        return ctxt.getLogger(this.getClass());
    }

    /**
     * Pack a number into a primitive long field
     * @param n number object
     * @return primitive long value with all the bits in the number
     */
    public static long pack(final Number n) {
        if (n instanceof Integer) {
            return n.intValue();
        } else if (n instanceof Long) {
            return n.longValue();
        } else if (n instanceof Double) {
            return Double.doubleToRawLongBits(n.doubleValue());
        }
        throw new AssertionError("cannot pack" + n);
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
    public static int getFieldCount(final Class<?> clazz) {
        final String name = clazz.getSimpleName();
        final String prefix = JS_OBJECT_PREFIX.symbolName();
        if (prefix.equals(name)) {
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
        if (!OBJECT_FIELDS_ONLY) {
            // no need to initialize anything to undefined in the dual field world
            // - then we have a constant getter for undefined for any unknown type
            return;
        }

        if (fieldNames.isEmpty()) {
            return;
        }

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

        addFields(classEmitter, fieldCount);

        final MethodEmitter init = newInitMethod(classEmitter);
        init.returnVoid();
        init.end();

        final MethodEmitter initWithSpillArrays = newInitWithSpillArraysMethod(classEmitter, ScriptObject.class);
        initWithSpillArrays.returnVoid();
        initWithSpillArrays.end();

        newEmptyInit(className, classEmitter);
        newAllocate(className, classEmitter);

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
        final String       className    = getClassName(fieldCount, paramCount);
        final String       superName    = className(FunctionScope.class);
        final ClassEmitter classEmitter = newClassEmitter(className, superName);
        final List<String> initFields   = addFields(classEmitter, fieldCount);

        final MethodEmitter init = newInitScopeMethod(classEmitter);
        initializeToUndefined(init, className, initFields);
        init.returnVoid();
        init.end();

        final MethodEmitter initWithSpillArrays = newInitWithSpillArraysMethod(classEmitter, FunctionScope.class);
        initializeToUndefined(initWithSpillArrays, className, initFields);
        initWithSpillArrays.returnVoid();
        initWithSpillArrays.end();

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
        final ClassEmitter classEmitter = new ClassEmitter(context, className, superName);
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

     private static MethodEmitter newInitWithSpillArraysMethod(final ClassEmitter classEmitter, final Class<?> superClass) {
        final MethodEmitter init = classEmitter.init(PropertyMap.class, long[].class, Object[].class);
        init.begin();
        init.load(Type.OBJECT, JAVA_THIS.slot());
        init.load(Type.OBJECT, INIT_MAP.slot());
        init.load(Type.LONG_ARRAY, 2);
        init.load(Type.OBJECT_ARRAY, 3);
        init.invoke(constructorNoLookup(superClass, PropertyMap.class, long[].class, Object[].class));

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
        final MethodEmitter init = classEmitter.init(PropertyMap.class, ScriptObject.class, ScriptObject.class);
        init.begin();
        init.load(Type.OBJECT, JAVA_THIS.slot());
        init.load(Type.OBJECT, INIT_MAP.slot());
        init.load(Type.OBJECT, INIT_SCOPE.slot());
        init.load(Type.OBJECT, INIT_ARGUMENTS.slot());
        init.invoke(constructorNoLookup(FunctionScope.class, PropertyMap.class, ScriptObject.class, ScriptObject.class));

        return init;
    }

    /**
     * Add an empty <init> method to the JavaScript class.
     *
     * @param classEmitter Open class emitter.
     * @param className    Name of JavaScript class.
     */
    private static void newEmptyInit(final String className, final ClassEmitter classEmitter) {
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
    private static void newAllocate(final String className, final ClassEmitter classEmitter) {
        final MethodEmitter allocate = classEmitter.method(EnumSet.of(Flag.PUBLIC, Flag.STATIC), ALLOCATE.symbolName(), ScriptObject.class, PropertyMap.class);
        allocate.begin();
        allocate._new(className, Type.typeFor(ScriptObject.class));
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

        if (env._print_code && env._print_code_dir == null) {
            env.getErr().println(ClassEmitter.disassemble(code));
        }

        if (env._verify_code) {
            context.verify(code);
        }

        return code;
    }

    /** Double to long bits, used with --dual-fields for primitive double values */
    public static final MethodHandle PACK_DOUBLE =
        MH.explicitCastArguments(MH.findStatic(MethodHandles.publicLookup(), Double.class, "doubleToRawLongBits", MH.type(long.class, double.class)), MH.type(long.class, double.class));

    /** double bits to long, used with --dual-fields for primitive double values */
    public static final MethodHandle UNPACK_DOUBLE =
        MH.findStatic(MethodHandles.publicLookup(), Double.class, "longBitsToDouble", MH.type(double.class, long.class));

    //type != forType, so use the correct getter for forType, box it and throw
    @SuppressWarnings("unused")
    private static Object getDifferent(final Object receiver, final Class<?> forType, final MethodHandle primitiveGetter, final MethodHandle objectGetter, final int programPoint) {
        //create the sametype getter, and upcast to value. no matter what the store format is,
        //
        final MethodHandle sameTypeGetter = getterForType(forType, primitiveGetter, objectGetter);
        final MethodHandle mh = MH.asType(sameTypeGetter, sameTypeGetter.type().changeReturnType(Object.class));
        try {
            final Object value = mh.invokeExact(receiver);
            throw new UnwarrantedOptimismException(value, programPoint);
        } catch (final Error | RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    private static Object getDifferentUndefined(final int programPoint) {
        throw new UnwarrantedOptimismException(Undefined.getUndefined(), programPoint);
    }

    private static MethodHandle getterForType(final Class<?> forType, final MethodHandle primitiveGetter, final MethodHandle objectGetter) {
        switch (getAccessorTypeIndex(forType)) {
        case TYPE_INT_INDEX:
            assert !OBJECT_FIELDS_ONLY : "this can only happen with dual fields";
            return MH.explicitCastArguments(primitiveGetter, primitiveGetter.type().changeReturnType(int.class));
        case TYPE_LONG_INDEX:
            assert !OBJECT_FIELDS_ONLY : "this can only happen with dual fields";
            return primitiveGetter;
        case TYPE_DOUBLE_INDEX:
            assert !OBJECT_FIELDS_ONLY : "this can only happen with dual fields";
            return MH.filterReturnValue(primitiveGetter, UNPACK_DOUBLE);
        case TYPE_OBJECT_INDEX:
            return objectGetter;
        default:
            throw new AssertionError(forType);
        }
    }

    //no optimism here. we do unconditional conversion to types
    private static MethodHandle createGetterInner(final Class<?> forType, final Class<?> type, final MethodHandle primitiveGetter, final MethodHandle objectGetter, final List<MethodHandle> converters, final int programPoint) {
        final int fti = forType == null ? TYPE_UNDEFINED_INDEX : getAccessorTypeIndex(forType);
        final int ti  = getAccessorTypeIndex(type);
        //this means fail if forType != type
        final boolean isOptimistic = converters == CONVERT_OBJECT_OPTIMISTIC;
        final boolean isPrimitiveStorage = forType != null && forType.isPrimitive();

        //which is the primordial getter
        final MethodHandle getter = OBJECT_FIELDS_ONLY ? objectGetter : isPrimitiveStorage ? primitiveGetter : objectGetter;

        if (forType == null) {
            if (isOptimistic) {
                //return undefined if asking for object. otherwise throw UnwarrantedOptimismException
                if (ti == TYPE_OBJECT_INDEX) {
                    return MH.dropArguments(GET_UNDEFINED.get(TYPE_OBJECT_INDEX), 0, Object.class);
                }
                //throw exception
                return MH.asType(
                    MH.dropArguments(
                            MH.insertArguments(
                                    GET_DIFFERENT_UNDEFINED,
                                    0,
                                    programPoint),
                            0,
                            Object.class),
                    getter.type().changeReturnType(type));
            }
            //return an undefined and coerce it to the appropriate type
            return MH.dropArguments(GET_UNDEFINED.get(ti), 0, Object.class);
        }

        assert forType != null;
        assert !OBJECT_FIELDS_ONLY || forType == Object.class : forType;

        if (isOptimistic) {
            if (fti < ti) {
                //asking for a wider type than currently stored. then it's OK to coerce.
                //e.g. stored as int,  ask for long or double
                //e.g. stored as long, ask for double
                assert fti != TYPE_UNDEFINED_INDEX;
                final MethodHandle tgetter = getterForType(forType, primitiveGetter, objectGetter);
                return MH.asType(tgetter, tgetter.type().changeReturnType(type));
            } else if (fti == ti) {
                //Fast path, never throw exception - exact getter, just unpack if needed
                return getterForType(forType, primitiveGetter, objectGetter);
            } else {
                assert fti > ti;
                //if asking for a narrower type than the storage - throw exception
                //unless FTI is object, in that case we have to go through the converters
                //there is no
                if (fti == TYPE_OBJECT_INDEX) {
                    return MH.filterReturnValue(
                            objectGetter,
                            MH.insertArguments(
                                    converters.get(ti),
                                    1,
                                    programPoint));
                }

                //asking for narrower primitive than we have stored, that is an
                //UnwarrantedOptimismException
                return MH.asType(
                        MH.filterArguments(
                            objectGetter,
                            0,
                            MH.insertArguments(
                                    GET_DIFFERENT,
                                    1,
                                    forType,
                                    primitiveGetter,
                                    objectGetter,
                                    programPoint)),
                        objectGetter.type().changeReturnType(type));
            }
        }

        assert !isOptimistic;
            //freely coerce the result to whatever you asked for, this is e.g. Object->int for a & b
        final MethodHandle tgetter = getterForType(forType, primitiveGetter, objectGetter);
        if (fti == TYPE_OBJECT_INDEX) {
            if (fti != ti) {
                return MH.filterReturnValue(tgetter, CONVERT_OBJECT.get(ti));
            }
            return tgetter;
        }

        assert !OBJECT_FIELDS_ONLY;
        //final MethodType pmt = primitiveGetter.type();
        assert primitiveGetter != null;
        final MethodType tgetterType = tgetter.type();
        switch (fti) {
        case TYPE_INT_INDEX: {
            return MH.asType(tgetter, tgetterType.changeReturnType(type));
        }
        case TYPE_LONG_INDEX:
            switch (ti) {
            case TYPE_INT_INDEX:
                //get int while an int, truncating cast of long value
                return MH.filterReturnValue(tgetter, JSType.TO_INT32_L.methodHandle);
            case TYPE_LONG_INDEX:
                return primitiveGetter;
            default:
                return MH.asType(tgetter, tgetterType.changeReturnType(type));
            }
        case TYPE_DOUBLE_INDEX:
            switch (ti) {
            case TYPE_INT_INDEX:
                return MH.filterReturnValue(tgetter, JSType.TO_INT32_D.methodHandle);
            case TYPE_LONG_INDEX:
                return MH.explicitCastArguments(tgetter, tgetterType.changeReturnType(type));
            case TYPE_DOUBLE_INDEX:
                assert tgetterType.returnType() == double.class;
                return tgetter;
            default:
                return MH.asType(tgetter, tgetterType.changeReturnType(Object.class));
            }
        default:
            throw new UnsupportedOperationException(forType + "=>" + type);
        }
    }

    /**
     * Given a primitiveGetter (optional for non dual fields) and an objectSetter that retrieve
     * the primitive and object version of a field respectively, return one with the correct
     * method type and the correct filters. For example, if the value is stored as a double
     * and we want an Object getter, in the dual fields world we'd pick the primitiveGetter,
     * which reads a long, use longBitsToDouble on the result to unpack it, and then change the
     * return type to Object, boxing it. In the objects only world there are only object fields,
     * primitives are boxed when asked for them and we don't need to bother with primitive encoding
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
     * @param programPoint    program point for getter, if program point is INVALID_PROGRAM_POINT, then this is not an optimistic getter
     *
     * @return getter for the given representation that returns the given type
     */
    public static MethodHandle createGetter(final Class<?> forType, final Class<?> type, final MethodHandle primitiveGetter, final MethodHandle objectGetter, final int programPoint) {
        return createGetterInner(
                forType,
                type,
                primitiveGetter,
                objectGetter,
                isValid(programPoint) ? CONVERT_OBJECT_OPTIMISTIC : CONVERT_OBJECT,
                programPoint);
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
            throw new UnsupportedOperationException(forType + "=>" + type);
        }
    }

    @SuppressWarnings("unused")
    private static boolean isType(final Class<?> boxedForType, final Object x) {
        return x != null && x.getClass() == boxedForType;
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
     * Add padding to field count to avoid creating too many classes and have some spare fields
     * @param count the field count
     * @return the padded field count
     */
    static int getPaddedFieldCount(final int count) {
        return count / FIELD_PADDING * FIELD_PADDING + FIELD_PADDING;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), ObjectClassGenerator.class, name, MH.type(rtype, types));
    }


}
