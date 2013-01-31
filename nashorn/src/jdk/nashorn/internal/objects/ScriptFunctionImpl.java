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

package jdk.nashorn.internal.objects;

import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static jdk.nashorn.internal.runtime.linker.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import jdk.nashorn.internal.codegen.objects.FunctionObjectCreator;
import jdk.nashorn.internal.runtime.GlobalFunctions;
import jdk.nashorn.internal.runtime.Property;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.linker.Lookup;
import jdk.nashorn.internal.runtime.linker.MethodHandleFactory;

/**
 * Concrete implementation of ScriptFunction. This sets correct map for the
 * function objects -- to expose properties like "prototype", "length" etc.
 */
public class ScriptFunctionImpl extends ScriptFunction {
    // per-function object flags
    private static final int IS_STRICT  = 0b0000_0001;
    private static final int IS_BUILTIN = 0b0000_0010;
    private static final int HAS_CALLEE = 0b0000_0100;

    // set this function to be a builtin function
    private void setIsBuiltin() {
        flags |= IS_BUILTIN;
    }

    // set this function to be a ECMAScript strict function
    private void setIsStrict() {
        flags |= IS_STRICT;
    }

    private static final MethodHandle BOUND_FUNCTION    = findOwnMH("boundFunction",    Object.class, ScriptFunction.class, Object.class, Object[].class, Object.class, Object[].class);
    private static final MethodHandle BOUND_CONSTRUCTOR = findOwnMH("boundConstructor", Object.class, ScriptFunction.class, Object[].class, Object.class, Object[].class);

    private static final PropertyMap nasgenmap$;

    private int flags;

    /**
     * Constructor
     *
     * Called by Nasgen generated code, no membercount, use the default map
     * Creates builtin functions only
     *
     * @param name name of function
     * @param invokeHandle handle for invocation
     * @param specs specialized versions of this method, if available, null otherwise
     */
    ScriptFunctionImpl(final String name, final MethodHandle invokeHandle, final MethodHandle[] specs) {
        this(name, invokeHandle, nasgenmap$, specs);
    }

    /**
     * Constructor
     *
     * Called by Nasgen generated code, no membercount, use the default map
     * Creates builtin functions only
     *
     * @param name name of function
     * @param methodHandle handle for invocation
     * @param map initial property map
     * @param specs specialized versions of this method, if available, null otherwise
     */
    ScriptFunctionImpl(final String name, final MethodHandle methodHandle, final PropertyMap map, final MethodHandle[] specs) {
        super(name, methodHandle, (nasgenmap$ == map) ? nasgenmap$ : map.addAll(nasgenmap$), null, null, 0, false, specs);
        this.setIsBuiltin();
        init();
    }

    /**
     * Constructor
     *
     * Called by Global.newScriptFunction (runtime)
     *
     * @param name name of function
     * @param methodHandle handle for invocation
     * @param scope scope object
     * @param strict are we in strict mode
     * @param specs specialized versions of this method, if available, null otherwise
     */
    ScriptFunctionImpl(final String name, final MethodHandle methodHandle, final ScriptObject scope, final boolean strict, final MethodHandle[] specs) {
        super(name, methodHandle, getMap(strict), scope, specs);
        if (strict) {
            this.setIsStrict();
        }
        init();
    }

    /**
     * Constructor
     *
     * Called by (compiler) generated code for {@link ScriptObject}s. Code is
     * generated by {@link FunctionObjectCreator}
     *
     * TODO this is a horrible constructor - can we do it with fewer args?
     *
     * @param name name of function
     * @param methodHandle handle for invocation
     * @param scope scope object
     * @param source source
     * @param token token
     * @param allocator instance constructor for function
     * @param allocatorMap initial map that constructor will keep reference to for future instantiations
     * @param needCallee does the function use the {@code callee} variable
     * @param strict are we in strict mode
     */
    public ScriptFunctionImpl(
            final String name,
            final MethodHandle methodHandle,
            final ScriptObject scope,
            final Source source,
            final long token,
            final MethodHandle allocator,
            final PropertyMap allocatorMap,
            final boolean needCallee,
            final boolean strict) {
        super(name, methodHandle, getMap(strict), scope, source, token, allocator, allocatorMap, needCallee, null);
        if (strict) {
            this.setIsStrict();
        }
        init();
    }

    static {
        PropertyMap map = PropertyMap.newMap(ScriptFunctionImpl.class);
        map = Lookup.newProperty(map, "prototype", Property.NOT_ENUMERABLE | Property.NOT_CONFIGURABLE, G$PROTOTYPE, S$PROTOTYPE);
        map = Lookup.newProperty(map, "length",    Property.NOT_ENUMERABLE | Property.NOT_CONFIGURABLE | Property.NOT_WRITABLE, G$LENGTH, null);
        map = Lookup.newProperty(map, "name",      Property.NOT_ENUMERABLE | Property.NOT_CONFIGURABLE | Property.NOT_WRITABLE, G$NAME, null);
        nasgenmap$ = map;
    }

    // function object representing TypeErrorThrower
    private static ScriptFunction typeErrorThrower;

    static synchronized ScriptFunction getTypeErrorThrower() {
        if (typeErrorThrower == null) {
            //name handle
            final ScriptFunctionImpl func = new ScriptFunctionImpl("TypeErrorThrower", Lookup.TYPE_ERROR_THROWER_SETTER, null, false, null);
            // clear constructor handle...
            func.constructHandle = null;
            func.prototype       = UNDEFINED;
            typeErrorThrower     = func;
        }

        return typeErrorThrower;
    }

    // add a new property that throws TypeError on get as well as set
    static synchronized PropertyMap newThrowerProperty(final PropertyMap map, final String name, final int flags) {
        return map.newProperty(name, flags, Lookup.TYPE_ERROR_THROWER_GETTER, Lookup.TYPE_ERROR_THROWER_SETTER);
    }

    // property map for strict mode functions - lazily initialized
    private static PropertyMap strictmodemap$;

    // Choose the map based on strict mode!
    private static PropertyMap getMap(final boolean strict) {
        if (strict) {
            synchronized (ScriptFunctionImpl.class) {
                if (strictmodemap$ == null) {
                    // In strict mode, the following properties should throw TypeError
                    strictmodemap$ = nasgenmap$;
                    strictmodemap$ = newThrowerProperty(strictmodemap$, "arguments", Property.NOT_ENUMERABLE | Property.NOT_CONFIGURABLE);
                    strictmodemap$ = newThrowerProperty(strictmodemap$, "caller",    Property.NOT_ENUMERABLE | Property.NOT_CONFIGURABLE);
                }
            }
            return strictmodemap$;
        }

        return nasgenmap$;
    }

    // Instance of this class is used as global anonymous function which
    // serves as Function.prototype object.
    private static class AnonymousFunction extends ScriptFunctionImpl {
        private static final PropertyMap nasgenmap$$ = PropertyMap.newMap(AnonymousFunction.class);

        AnonymousFunction() {
            super("", GlobalFunctions.ANONYMOUS, nasgenmap$$, null);
        }
    }

    static ScriptFunctionImpl newAnonymousFunction() {
        return new AnonymousFunction();
    }

    @Override
    public final boolean isStrict() {
        return (flags & IS_STRICT) != 0;
    }

    @Override
    public final boolean hasCalleeParameter() {
        return (flags & HAS_CALLEE) != 0;
    }

    @Override
    protected void setHasCalleeParameter() {
        flags |= HAS_CALLEE;
    }

    @Override
    public final boolean isBuiltin() {
        return (flags & IS_BUILTIN) != 0;
    }

    /**
     * Factory method for non-constructor functions
     *
     * @param name   function name
     * @param methodHandle handle for invocation
     * @param specs  specialized versions of function if available, null otherwise
     * @param strict are we in strict mode
     * @return new ScriptFunction
     */
    public static ScriptFunction makeFunction(final String name, final MethodHandle methodHandle, final MethodHandle[] specs, final boolean strict) {
        final ScriptFunctionImpl func = new ScriptFunctionImpl(name, methodHandle, null, strict, specs);

        func.setIsBuiltin();
        func.setConstructHandle(null);
        func.setPrototype(UNDEFINED);

        return func;
    }

    /**
     * Factory method for non-constructor functions
     *
     * @param name   function name
     * @param methodHandle handle for invocation
     * @param specs  specialized versions of function if available, null otherwise
     * @return new ScriptFunction
     */
    public static ScriptFunction makeFunction(final String name, final MethodHandle methodHandle, final MethodHandle[] specs) {
        return makeFunction(name, methodHandle, specs, false);
    }

    /**
     * Factory method for non-constructor functions
     *
     * @param name   function name
     * @param methodHandle handle for invocation
     * @return new ScriptFunction
     */
    public static ScriptFunction makeFunction(final String name, final MethodHandle methodHandle) {
        return makeFunction(name, methodHandle, null);
    }

    /**
     * This method is used to create a bound function. See also
     * {@link NativeFunction#bind(Object, Object...)} method implementation.
     *
     * @param thiz this reference to bind
     * @param args arguments to bind
     */
    @Override
    public ScriptFunction makeBoundFunction(final Object thiz, final Object[] args) {
        Object[] allArgs = args;

        if (allArgs == null) {
            allArgs = ScriptRuntime.EMPTY_ARRAY;
        }

        final MethodHandle   boundMethod = MH.insertArguments(BOUND_FUNCTION, 0, this, thiz, allArgs);
        final ScriptFunction boundFunc   = makeFunction("", boundMethod, null, true);

        MethodHandle consHandle  = this.getConstructHandle();

        if (consHandle != null) {
            consHandle = MH.insertArguments(BOUND_CONSTRUCTOR, 0, this, allArgs);
        }

        boundFunc.setConstructHandle(consHandle);
        int newArity = this.getArity();
        if (newArity != -1) {
            newArity -= Math.min(newArity, allArgs.length);
        }
        boundFunc.setArity(newArity);

        return boundFunc;
    }

    @SuppressWarnings("unused")
    private static Object boundFunction(final ScriptFunction wrapped, final Object boundThiz, final Object[] boundArgs, final Object thiz, final Object[] args) {
        final Object[] allArgs = new Object[boundArgs.length + args.length];

        System.arraycopy(boundArgs, 0, allArgs, 0, boundArgs.length);
        System.arraycopy(args, 0, allArgs, boundArgs.length, args.length);

        return ScriptRuntime.apply(wrapped, boundThiz, allArgs);
    }

    @SuppressWarnings("unused")
    private static Object boundConstructor(final ScriptFunction wrapped, final Object[] boundArgs, final Object thiz, final Object[] args) {
        final Object[] allArgs = new Object[boundArgs.length + args.length];
        System.arraycopy(boundArgs, 0, allArgs, 0, boundArgs.length);
        System.arraycopy(args, 0, allArgs, boundArgs.length, args.length);

        return ScriptRuntime.construct(wrapped, allArgs);
    }

    // return Object.prototype - used by "allocate"
    @Override
    protected final ScriptObject getObjectPrototype() {
        return Global.objectPrototype();
    }

    // Internals below..
    private void init() {
        this.setProto(Global.instance().getFunctionPrototype());
        this.setPrototype(new PrototypeObject(this));

        if (isStrict()) {
            final ScriptFunction func = getTypeErrorThrower();
            // We have to fill user accessor functions late as these are stored
            // in this object rather than in the PropertyMap of this object.
            setUserAccessors("arguments", func, func);
            setUserAccessors("caller", func, func);
        }
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        try {
            return MethodHandles.lookup().findStatic(ScriptFunctionImpl.class, name, MH.type(rtype, types));
        } catch (final NoSuchMethodException | IllegalAccessException e) {
            throw new MethodHandleFactory.LookupException(e);
        }
    }
}
