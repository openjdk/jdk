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

import static jdk.nashorn.internal.lookup.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.Source;

/**
 * This class represents constant names of variables, methods and fields in
 * the compiler
 */

public enum CompilerConstants {
    /** the __FILE__ variable */
    __FILE__,

    /** the __DIR__ variable */
    __DIR__,

    /** the __LINE__ variable */
    __LINE__,

    /** constructor name */
    INIT("<init>"),

    /** static initializer name */
    CLINIT("<clinit>"),

    /** eval name */
    EVAL("eval"),

    /** source name and class */
    SOURCE("source", Source.class),

    /** constants name and class */
    CONSTANTS("constants", Object[].class),

    /** strict mode field name and type */
    STRICT_MODE("strictMode", boolean.class),

    /** default script name */
    DEFAULT_SCRIPT_NAME("Script"),

    /** function prefix for anonymous functions */
    ANON_FUNCTION_PREFIX("L:"),

    /** separator for method names of nested functions */
    NESTED_FUNCTION_SEPARATOR("#"),

    /** separator for making method names unique by appending numeric ids */
    ID_FUNCTION_SEPARATOR("-"),

    /** method name for Java method that is the program entry point */
    PROGRAM(":program"),

    /** method name for Java method that creates the script function for the program */
    CREATE_PROGRAM_FUNCTION(":createProgramFunction"),

    /**
     * "this" name symbol for a parameter representing ECMAScript "this" in static methods that are compiled
     * representations of ECMAScript functions. It is not assigned a slot, as its position in the method signature is
     * dependent on other factors (most notably, callee can precede it).
     */
    THIS("this", Object.class),

    /** this debugger symbol */
    THIS_DEBUGGER(":this"),

    /** scope name, type and slot */
    SCOPE(":scope", ScriptObject.class, 2),

    /** the return value variable name were intermediate results are stored for scripts */
    RETURN(":return"),

    /** the callee value variable when necessary */
    CALLEE(":callee", ScriptFunction.class),

    /** the varargs variable when necessary */
    VARARGS(":varargs", Object[].class),

    /** the arguments variable (visible to function body). Initially set to ARGUMENTS, but can be reassigned by code in
     * the function body.*/
    ARGUMENTS_VAR("arguments", Object.class),

    /** the internal arguments object, when necessary (not visible to scripts, can't be reassigned). */
    ARGUMENTS(":arguments", ScriptObject.class),

    /** prefix for apply-to-call exploded arguments */
    EXPLODED_ARGUMENT_PREFIX(":xarg"),

    /** prefix for iterators for for (x in ...) */
    ITERATOR_PREFIX(":i", Iterator.class),

    /** prefix for tag variable used for switch evaluation */
    SWITCH_TAG_PREFIX(":s"),

    /** prefix for JVM exceptions */
    EXCEPTION_PREFIX(":e", Throwable.class),

    /** prefix for quick slots generated in Store */
    QUICK_PREFIX(":q"),

    /** prefix for temporary variables */
    TEMP_PREFIX(":t"),

    /** prefix for literals */
    LITERAL_PREFIX(":l"),

    /** prefix for regexps */
    REGEX_PREFIX(":r"),

    /** "this" used in non-static Java methods; always in slot 0 */
    JAVA_THIS(null, 0),

    /** Map parameter in scope object constructors; always in slot 1 */
    INIT_MAP(null, 1),

    /** Parent scope parameter in scope object constructors; always in slot 2 */
    INIT_SCOPE(null, 2),

    /** Arguments parameter in scope object constructors; in slot 3 when present */
    INIT_ARGUMENTS(null, 3),

    /** prefix for all ScriptObject subclasses with dual object/primitive fields, see {@link ObjectClassGenerator} */
    JS_OBJECT_DUAL_FIELD_PREFIX("JD"),

    /** prefix for all ScriptObject subclasses with object fields only, see {@link ObjectClassGenerator} */
    JS_OBJECT_SINGLE_FIELD_PREFIX("JO"),

    /** name for allocate method in JO objects */
    ALLOCATE("allocate"),

    /** prefix for split methods, @see Splitter */
    SPLIT_PREFIX(":split"),

    /** prefix for split array method and slot */
    SPLIT_ARRAY_ARG(":split_array", 3),

    /** get string from constant pool */
    GET_STRING(":getString"),

    /** get map */
    GET_MAP(":getMap"),

    /** set map */
    SET_MAP(":setMap"),

    /** get array prefix */
    GET_ARRAY_PREFIX(":get"),

    /** get array suffix */
    GET_ARRAY_SUFFIX("$array");

    /** To save memory - intern the compiler constant symbol names, as they are frequently reused */
    static {
        for (final CompilerConstants c : values()) {
            final String symbolName = c.symbolName();
            if (symbolName != null) {
                symbolName.intern();
            }
        }
    }

    private static Set<String> symbolNames;

    /**
     * Prefix used for internal methods generated in script classes.
     */
    private static final String INTERNAL_METHOD_PREFIX = ":";

    private final String symbolName;
    private final Class<?> type;
    private final int slot;

    private CompilerConstants() {
        this.symbolName = name();
        this.type = null;
        this.slot = -1;
    }

    private CompilerConstants(final String symbolName) {
        this(symbolName, -1);
    }

    private CompilerConstants(final String symbolName, final int slot) {
        this(symbolName, null, slot);
    }

    private CompilerConstants(final String symbolName, final Class<?> type) {
        this(symbolName, type, -1);
    }

    private CompilerConstants(final String symbolName, final Class<?> type, final int slot) {
        this.symbolName = symbolName;
        this.type       = type;
        this.slot       = slot;
    }

    /**
     * Check whether a name is that of a reserved compiler constant
     * @param name name
     * @return true if compiler constant name
     */
    public static boolean isCompilerConstant(final String name) {
        ensureSymbolNames();
        return symbolNames.contains(name);
    }

    private static void ensureSymbolNames() {
        if(symbolNames == null) {
            symbolNames = new HashSet<>();
            for(final CompilerConstants cc: CompilerConstants.values()) {
                symbolNames.add(cc.symbolName);
            }
        }
    }

    /**
     * Return the tag for this compile constant. Deliberately avoiding "name" here
     * not to conflate with enum implementation. This is the master string for the
     * constant - every constant has one.
     *
     * @return the tag
     */
    public final String symbolName() {
        return symbolName;
    }

    /**
     * Return the type for this compile constant
     *
     * @return type for this constant's instances, or null if N/A
     */
    public final Class<?> type() {
        return type;
    }

    /**
     * Return the slot for this compile constant
     *
     * @return byte code slot where constant is stored or -1 if N/A
     */
    public final int slot() {
        return slot;
    }

    /**
     * Return a descriptor for this compile constant. Only relevant if it has
     * a type
     *
     * @return descriptor the descriptor
     */
    public final String descriptor() {
        assert type != null : " asking for descriptor of typeless constant";
        return typeDescriptor(type);
    }

    /**
     * Get the internal class name for a type
     *
     * @param type a type
     * @return  the internal name for this type
     */
    public static String className(final Class<?> type) {
        return Type.getInternalName(type);
    }

    /**
     * Get the method descriptor for a given method type collection
     *
     * @param rtype  return type
     * @param ptypes parameter types
     *
     * @return internal descriptor for this method
     */
    public static String methodDescriptor(final Class<?> rtype, final Class<?>... ptypes) {
        return Type.getMethodDescriptor(rtype, ptypes);
    }

    /**
     * Get the type descriptor for a type
     *
     * @param clazz a type
     *
     * @return the internal descriptor for this type
     */
    public static String typeDescriptor(final Class<?> clazz) {
        return Type.typeFor(clazz).getDescriptor();
    }

    /**
     * Create a call representing a void constructor for a given type. Don't
     * attempt to look this up at compile time
     *
     * @param clazz the class
     *
     * @return Call representing void constructor for type
     */
    public static Call constructorNoLookup(final Class<?> clazz) {
        return specialCallNoLookup(clazz, INIT.symbolName(), void.class);
    }

    /**
     * Create a call representing a constructor for a given type. Don't
     * attempt to look this up at compile time
     *
     * @param className the type class name
     * @param ptypes    the parameter types for the constructor
     *
     * @return Call representing constructor for type
     */
    public static Call constructorNoLookup(final String className, final Class<?>... ptypes) {
        return specialCallNoLookup(className, INIT.symbolName(), methodDescriptor(void.class, ptypes));
    }

    /**
     * Create a call representing a constructor for a given type. Don't
     * attempt to look this up at compile time
     *
     * @param clazz  the class name
     * @param ptypes the parameter types for the constructor
     *
     * @return Call representing constructor for type
     */
    public static Call constructorNoLookup(final Class<?> clazz, final Class<?>... ptypes) {
        return specialCallNoLookup(clazz, INIT.symbolName(), void.class, ptypes);
    }

    /**
     * Create a call representing an invokespecial to a given method. Don't
     * attempt to look this up at compile time
     *
     * @param className the class name
     * @param name      the method name
     * @param desc      the descriptor
     *
     * @return Call representing specified invokespecial call
     */
    public static Call specialCallNoLookup(final String className, final String name, final String desc) {
        return new Call(null, className, name, desc) {
            @Override
            MethodEmitter invoke(final MethodEmitter method) {
                return method.invokespecial(className, name, descriptor);
            }

            @Override
            public void invoke(final MethodVisitor mv) {
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, className, name, desc, false);
            }
        };
    }

    /**
     * Create a call representing an invokespecial to a given method. Don't
     * attempt to look this up at compile time
     *
     * @param clazz  the class
     * @param name   the method name
     * @param rtype  the return type
     * @param ptypes the parameter types
     *
     * @return Call representing specified invokespecial call
     */
    public static Call specialCallNoLookup(final Class<?> clazz, final String name, final Class<?> rtype, final Class<?>... ptypes) {
        return specialCallNoLookup(className(clazz), name, methodDescriptor(rtype, ptypes));
    }

    /**
     * Create a call representing an invokestatic to a given method. Don't
     * attempt to look this up at compile time
     *
     * @param className the class name
     * @param name      the method name
     * @param desc      the descriptor
     *
     * @return Call representing specified invokestatic call
     */
    public static Call staticCallNoLookup(final String className, final String name, final String desc) {
        return new Call(null, className, name, desc) {
            @Override
            MethodEmitter invoke(final MethodEmitter method) {
                return method.invokestatic(className, name, descriptor);
            }

            @Override
            public void invoke(final MethodVisitor mv) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, className, name, desc, false);
            }
        };
    }

    /**
     * Create a call representing an invokestatic to a given method. Don't
     * attempt to look this up at compile time
     *
     * @param clazz  the class
     * @param name   the method name
     * @param rtype  the return type
     * @param ptypes the parameter types
     *
     * @return Call representing specified invokestatic call
     */
    public static Call staticCallNoLookup(final Class<?> clazz, final String name, final Class<?> rtype, final Class<?>... ptypes) {
        return staticCallNoLookup(className(clazz), name, methodDescriptor(rtype, ptypes));
    }

    /**
     * Create a call representing an invokevirtual to a given method. Don't
     * attempt to look this up at compile time
     *
     * @param clazz  the class
     * @param name   the method name
     * @param rtype  the return type
     * @param ptypes the parameter types
     *
     * @return Call representing specified invokevirtual call
     */
    public static Call virtualCallNoLookup(final Class<?> clazz, final String name, final Class<?> rtype, final Class<?>... ptypes) {
        return new Call(null, className(clazz), name, methodDescriptor(rtype, ptypes)) {
            @Override
            MethodEmitter invoke(final MethodEmitter method) {
                return method.invokevirtual(className, name, descriptor);
            }

            @Override
            public void invoke(final MethodVisitor mv) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, name, descriptor, false);
            }
        };
    }

    /**
     * Create a call representing an invokeinterface to a given method. Don't
     * attempt to look this up at compile time
     *
     * @param clazz  the class
     * @param name   the method name
     * @param rtype  the return type
     * @param ptypes the parameter types
     *
     * @return Call representing specified invokeinterface call
     */
    public static Call interfaceCallNoLookup(final Class<?> clazz, final String name, final Class<?> rtype, final Class<?>... ptypes) {
        return new Call(null, className(clazz), name, methodDescriptor(rtype, ptypes)) {
            @Override
            MethodEmitter invoke(final MethodEmitter method) {
                return method.invokeinterface(className, name, descriptor);
            }

            @Override
            public void invoke(final MethodVisitor mv) {
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, className, name, descriptor, true);
            }
        };
    }

    /**
     * Create a FieldAccess representing a virtual field, that can be subject to put
     * or get operations
     *
     * @param className name of the class where the field is a member
     * @param name      name of the field
     * @param desc      type descriptor of the field
     *
     * @return a field access object giving access code generation method for the virtual field
     */
    public static FieldAccess virtualField(final String className, final String name, final String desc) {
        return new FieldAccess(className, name, desc) {
            @Override
            public MethodEmitter get(final MethodEmitter method) {
                return method.getField(className, name, descriptor);
            }

            @Override
            public void put(final MethodEmitter method) {
                method.putField(className, name, descriptor);
            }
        };
    }

    /**
     * Create a FieldAccess representing a virtual field, that can be subject to put
     * or get operations
     *
     * @param clazz class where the field is a member
     * @param name  name of the field
     * @param type  type of the field
     *
     * @return a field access object giving access code generation method for the virtual field
     */
    public static FieldAccess virtualField(final Class<?> clazz, final String name, final Class<?> type) {
        return virtualField(className(clazz), name, typeDescriptor(type));
    }

    /**
     * Create a FieldAccess representing a static field, that can be subject to put
     * or get operations
     *
     * @param className name of the class where the field is a member
     * @param name      name of the field
     * @param desc      type descriptor of the field
     *
     * @return a field access object giving access code generation method for the static field
     */
    public static FieldAccess staticField(final String className, final String name, final String desc) {
        return new FieldAccess(className, name, desc) {
            @Override
            public MethodEmitter get(final MethodEmitter method) {
                return method.getStatic(className, name, descriptor);
            }

            @Override
            public void put(final MethodEmitter method) {
                method.putStatic(className, name, descriptor);
            }
        };
    }

    /**
     * Create a FieldAccess representing a static field, that can be subject to put
     * or get operations
     *
     * @param clazz class where the field is a member
     * @param name  name of the field
     * @param type  type of the field
     *
     * @return a field access object giving access code generation method for the virtual field
     */
    public static FieldAccess staticField(final Class<?> clazz, final String name, final Class<?> type) {
        return staticField(className(clazz), name, typeDescriptor(type));
    }

    /**
     * Create a static call, given an explicit lookup, looking up the method handle for it at the same time
     *
     * @param lookup the lookup
     * @param clazz  the class
     * @param name   the name of the method
     * @param rtype  the return type
     * @param ptypes the parameter types
     *
     * @return the call object representing the static call
     */
    public static Call staticCall(final MethodHandles.Lookup lookup, final Class<?> clazz, final String name, final Class<?> rtype, final Class<?>... ptypes) {
        return new Call(MH.findStatic(lookup, clazz, name, MH.type(rtype, ptypes)), className(clazz), name, methodDescriptor(rtype, ptypes)) {
            @Override
            MethodEmitter invoke(final MethodEmitter method) {
                return method.invokestatic(className, name, descriptor);
            }

            @Override
            public void invoke(final MethodVisitor mv) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, className, name, descriptor, false);
            }
        };
    }

    /**
     * Create a virtual call, given an explicit lookup, looking up the method handle for it at the same time
     *
     * @param lookup the lookup
     * @param clazz  the class
     * @param name   the name of the method
     * @param rtype  the return type
     * @param ptypes the parameter types
     *
     * @return the call object representing the virtual call
     */
    public static Call virtualCall(final MethodHandles.Lookup lookup, final Class<?> clazz, final String name, final Class<?> rtype, final Class<?>... ptypes) {
        return new Call(MH.findVirtual(lookup, clazz, name, MH.type(rtype, ptypes)), className(clazz), name, methodDescriptor(rtype, ptypes)) {
            @Override
            MethodEmitter invoke(final MethodEmitter method) {
                return method.invokevirtual(className, name, descriptor);
            }

            @Override
            public void invoke(final MethodVisitor mv) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, name, descriptor, false);
            }
        };
    }

    /**
     * Create a special call, given an explicit lookup, looking up the method handle for it at the same time.
     * clazz is used as this class
     *
     * @param lookup    the lookup
     * @param clazz     the class
     * @param name      the name of the method
     * @param rtype     the return type
     * @param ptypes    the parameter types
     *
     * @return the call object representing the virtual call
     */
    public static Call specialCall(final MethodHandles.Lookup lookup, final Class<?> clazz, final String name, final Class<?> rtype, final Class<?>... ptypes) {
        return new Call(MH.findSpecial(lookup, clazz, name, MH.type(rtype, ptypes), clazz), className(clazz), name, methodDescriptor(rtype, ptypes)) {
            @Override
            MethodEmitter invoke(final MethodEmitter method) {
                return method.invokespecial(className, name, descriptor);
            }

            @Override
            public void invoke(final MethodVisitor mv) {
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, className, name, descriptor, false);
            }
        };
    }

    /**
     * Returns true if the passed string looks like a method name of an internally generated Nashorn method. Basically,
     * if it starts with a colon character {@code :} but is not the name of the program method {@code :program}.
     * Program function is not considered internal as we want it to show up in exception stack traces.
     * @param methodName the name of a method
     * @return true if it looks like an internal Nashorn method name.
     * @throws NullPointerException if passed null
     */
    public static boolean isInternalMethodName(final String methodName) {
        return methodName.startsWith(INTERNAL_METHOD_PREFIX) && !methodName.equals(PROGRAM.symbolName);
     }

    /**
     * Private class representing an access. This can generate code into a method code or
     * a field access.
     */
    private abstract static class Access {
        protected final MethodHandle methodHandle;
        protected final String       className;
        protected final String       name;
        protected final String       descriptor;

        /**
         * Constructor
         *
         * @param methodHandle methodHandle or null if none
         * @param className    class name for access
         * @param name         field or method name for access
         * @param descriptor   descriptor for access field or method
         */
        protected Access(final MethodHandle methodHandle, final String className, final String name, final String descriptor) {
            this.methodHandle = methodHandle;
            this.className    = className;
            this.name         = name;
            this.descriptor   = descriptor;
        }

        /**
         * Get the method handle, or null if access hasn't been looked up
         *
         * @return method handle
         */
        public MethodHandle methodHandle() {
            return methodHandle;
        }

        /**
         * Get the class name of the access
         *
         * @return the class name
         */
        public String className() {
            return className;
        }

        /**
         * Get the field name or method name of the access
         *
         * @return the name
         */
        public String name() {
            return name;
        }

        /**
         * Get the descriptor of the method or field of the access
         *
         * @return the descriptor
         */
        public String descriptor() {
            return descriptor;
        }
    }

    /**
     * Field access - this can be used for generating code for static or
     * virtual field accesses
     */
    public abstract static class FieldAccess extends Access {
        /**
         * Constructor
         *
         * @param className  name of the class where the field is
         * @param name       name of the field
         * @param descriptor descriptor of the field
         */
        protected FieldAccess(final String className, final String name, final String descriptor) {
            super(null, className, name, descriptor);
        }

        /**
         * Generate get code for the field
         *
         * @param emitter a method emitter
         *
         * @return the method emitter
         */
        protected abstract MethodEmitter get(final MethodEmitter emitter);

        /**
         * Generate put code for the field
         *
         * @param emitter a method emitter
         */
        protected abstract void put(final MethodEmitter emitter);
    }

    /**
     * Call - this can be used for generating code for different types of calls
     */
    public abstract static class Call extends Access {

        /**
         * Constructor
         *
         * @param className  class name for the method of the call
         * @param name       method name
         * @param descriptor method descriptor
         */
        protected Call(final String className, final String name, final String descriptor) {
            super(null, className, name, descriptor);
        }

        /**
         * Constructor
         *
         * @param methodHandle method handle for the call if resolved
         * @param className    class name for the method of the call
         * @param name         method name
         * @param descriptor   method descriptor
         */
        protected Call(final MethodHandle methodHandle, final String className, final String name, final String descriptor) {
            super(methodHandle, className, name, descriptor);
        }

        /**
         * Generate invocation code for the method
         *
         * @param emitter a method emitter
         *
         * @return the method emitter
         */
        abstract MethodEmitter invoke(final MethodEmitter emitter);

        /**
         * Generate invocation code for the method
         *
         * @param mv a method visitor
         */
        public abstract void invoke(final MethodVisitor mv);
    }

}
