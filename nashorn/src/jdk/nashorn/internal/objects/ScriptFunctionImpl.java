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

import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import jdk.nashorn.internal.runtime.AccessorProperty;
import jdk.nashorn.internal.runtime.GlobalFunctions;
import jdk.nashorn.internal.runtime.Property;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.RecompilableScriptFunctionData;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptFunctionData;
import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * Concrete implementation of ScriptFunction. This sets correct map for the
 * function objects -- to expose properties like "prototype", "length" etc.
 */
public class ScriptFunctionImpl extends ScriptFunction {

    /** Reference to constructor prototype. */
    private Object prototype;

    // property map for strict mode functions
    private static final PropertyMap strictmodemap$;
    // property map for bound functions
    private static final PropertyMap boundfunctionmap$;
    // property map for non-strict, non-bound functions.
    private static final PropertyMap map$;

    // Marker object for lazily initialized prototype object
    private static final Object LAZY_PROTOTYPE = new Object();

    private ScriptFunctionImpl(final String name, final MethodHandle invokeHandle, final MethodHandle[] specs, final Global global) {
        super(name, invokeHandle, map$, null, specs, ScriptFunctionData.IS_BUILTIN_CONSTRUCTOR);
        init(global);
    }

    /**
     * Constructor called by Nasgen generated code, no membercount, use the default map.
     * Creates builtin functions only.
     *
     * @param name name of function
     * @param invokeHandle handle for invocation
     * @param specs specialized versions of this method, if available, null otherwise
     */
    ScriptFunctionImpl(final String name, final MethodHandle invokeHandle, final MethodHandle[] specs) {
        this(name, invokeHandle, specs, Global.instance());
    }

    private ScriptFunctionImpl(final String name, final MethodHandle invokeHandle, final PropertyMap map, final MethodHandle[] specs, final Global global) {
        super(name, invokeHandle, map.addAll(map$), null, specs, ScriptFunctionData.IS_BUILTIN_CONSTRUCTOR);
        init(global);
    }

    /**
     * Constructor called by Nasgen generated code, no membercount, use the map passed as argument.
     * Creates builtin functions only.
     *
     * @param name name of function
     * @param invokeHandle handle for invocation
     * @param map initial property map
     * @param specs specialized versions of this method, if available, null otherwise
     */
    ScriptFunctionImpl(final String name, final MethodHandle invokeHandle, final PropertyMap map, final MethodHandle[] specs) {
        this(name, invokeHandle, map, specs, Global.instance());
    }

    private ScriptFunctionImpl(final String name, final MethodHandle methodHandle, final ScriptObject scope, final MethodHandle[] specs, final int flags, final Global global) {
        super(name, methodHandle, getMap(isStrict(flags)), scope, specs, flags);
        init(global);
    }

    /**
     * Constructor called by Global.newScriptFunction (runtime).
     *
     * @param name name of function
     * @param methodHandle handle for invocation
     * @param scope scope object
     * @param specs specialized versions of this method, if available, null otherwise
     * @param flags {@link ScriptFunctionData} flags
     */
    ScriptFunctionImpl(final String name, final MethodHandle methodHandle, final ScriptObject scope, final MethodHandle[] specs, final int flags) {
        this(name, methodHandle, scope, specs, flags, Global.instance());
    }

    private ScriptFunctionImpl(final RecompilableScriptFunctionData data, final ScriptObject scope, final Global global) {
        super(data, getMap(data.isStrict()), scope);
        init(global);
    }

    /**
     * Constructor called by (compiler) generated code for {@link ScriptObject}s.
     *
     * @param data static function data
     * @param scope scope object
     */
    public ScriptFunctionImpl(final RecompilableScriptFunctionData data, final ScriptObject scope) {
        this(data, scope, Global.instance());
    }

    /**
     * Only invoked internally from {@link BoundScriptFunctionImpl} constructor.
     * @param data the script function data for the bound function.
     * @param global the global object
     */
    ScriptFunctionImpl(final ScriptFunctionData data, final Global global) {
        super(data, boundfunctionmap$, null);
        init(global);
    }

    static {
        final ArrayList<Property> properties = new ArrayList<>(3);
        properties.add(AccessorProperty.create("prototype", Property.NOT_ENUMERABLE | Property.NOT_CONFIGURABLE, G$PROTOTYPE, S$PROTOTYPE));
        properties.add(AccessorProperty.create("length",  Property.NOT_ENUMERABLE | Property.NOT_CONFIGURABLE | Property.NOT_WRITABLE, G$LENGTH, null));
        properties.add(AccessorProperty.create("name", Property.NOT_ENUMERABLE | Property.NOT_CONFIGURABLE | Property.NOT_WRITABLE, G$NAME, null));
        map$ = PropertyMap.newMap(properties);
        strictmodemap$ = createStrictModeMap(map$);
        boundfunctionmap$ = createBoundFunctionMap(strictmodemap$);
    }

    private static PropertyMap createStrictModeMap(final PropertyMap map) {
        final int flags = Property.NOT_ENUMERABLE | Property.NOT_CONFIGURABLE;
        PropertyMap newMap = map;
        // Need to add properties directly to map since slots are assigned speculatively by newUserAccessors.
        newMap = newMap.addPropertyNoHistory(map.newUserAccessors("arguments", flags));
        newMap = newMap.addPropertyNoHistory(map.newUserAccessors("caller", flags));
        return newMap;
    }

    private static boolean isStrict(final int flags) {
        return (flags & ScriptFunctionData.IS_STRICT) != 0;
    }

    // Choose the map based on strict mode!
    private static PropertyMap getMap(final boolean strict) {
        return strict ? strictmodemap$ : map$;
    }

    private static PropertyMap createBoundFunctionMap(final PropertyMap strictModeMap) {
        // Bound function map is same as strict function map, but additionally lacks the "prototype" property, see
        // ECMAScript 5.1 section 15.3.4.5
        return strictModeMap.deleteProperty(strictModeMap.findProperty("prototype"));
    }

    // Instance of this class is used as global anonymous function which
    // serves as Function.prototype object.
    private static class AnonymousFunction extends ScriptFunctionImpl {
        private static final PropertyMap anonmap$ = PropertyMap.newMap();

        AnonymousFunction() {
            super("", GlobalFunctions.ANONYMOUS, anonmap$, null);
        }
    }

    static ScriptFunctionImpl newAnonymousFunction() {
        return new AnonymousFunction();
    }

    private static ScriptFunction makeFunction(final String name, final MethodHandle methodHandle, final MethodHandle[] specs, final int flags) {
        final ScriptFunctionImpl func = new ScriptFunctionImpl(name, methodHandle, null, specs, flags);
        func.setPrototype(UNDEFINED);
        // Non-constructor built-in functions do not have "prototype" property
        func.deleteOwnProperty(func.getMap().findProperty("prototype"));

        return func;
    }

    /**
     * Factory method for non-constructor built-in functions
     *
     * @param name   function name
     * @param methodHandle handle for invocation
     * @param specs  specialized versions of function if available, null otherwise
     * @return new ScriptFunction
     */
    static ScriptFunction makeFunction(final String name, final MethodHandle methodHandle, final MethodHandle[] specs) {
        return makeFunction(name, methodHandle, specs, ScriptFunctionData.IS_BUILTIN);
    }

    /**
     * Factory method for non-constructor built-in, strict functions
     *
     * @param name   function name
     * @param methodHandle handle for invocation
     * @return new ScriptFunction
     */
    static ScriptFunction makeStrictFunction(final String name, final MethodHandle methodHandle) {
        return makeFunction(name, methodHandle, null, ScriptFunctionData.IS_BUILTIN | ScriptFunctionData.IS_STRICT );
    }

    /**
     * Factory method for non-constructor built-in functions
     *
     * @param name   function name
     * @param methodHandle handle for invocation
     * @return new ScriptFunction
     */
    static ScriptFunction makeFunction(final String name, final MethodHandle methodHandle) {
        return makeFunction(name, methodHandle, null);
    }

    @Override
    public ScriptFunction makeSynchronizedFunction(final Object sync) {
        final MethodHandle mh = MH.insertArguments(ScriptFunction.INVOKE_SYNC, 0, this, sync);
        return makeFunction(getName(), mh);
    }

    /**
     * Same as {@link ScriptFunction#makeBoundFunction(Object, Object[])}. The only reason we override it is so that we
     * can expose it to methods in this package.
     * @param self the self to bind to this function. Can be null (in which case, null is bound as this).
     * @param args additional arguments to bind to this function. Can be null or empty to not bind additional arguments.
     * @return a function with the specified self and parameters bound.
     */
    @Override
    protected ScriptFunction makeBoundFunction(final Object self, final Object[] args) {
        return super.makeBoundFunction(self, args);
    }

    /**
     * This method is used to create a bound function based on this function.
     *
     * @param data the {@code ScriptFunctionData} specifying the functions immutable portion.
     * @return a function initialized from the specified data. Its parent scope will be set to null, therefore the
     * passed in data should not expect a callee.
     */
    @Override
    protected ScriptFunction makeBoundFunction(final ScriptFunctionData data) {
        return new BoundScriptFunctionImpl(data, getTargetFunction());
    }

    // return Object.prototype - used by "allocate"
    @Override
    protected final ScriptObject getObjectPrototype() {
        return Global.objectPrototype();
    }

    @Override
    public final Object getPrototype() {
        if (prototype == LAZY_PROTOTYPE) {
            prototype = new PrototypeObject(this);
        }
        return prototype;
    }

    @Override
    public final void setPrototype(final Object newProto) {
        if (newProto instanceof ScriptObject && newProto != this.prototype && allocatorMap != null) {
            // Replace our current allocator map with one that is associated with the new prototype.
            allocatorMap = allocatorMap.changeProto((ScriptObject)newProto);
        }
        this.prototype = newProto;
    }

    // Internals below..
    private void init(final Global global) {
        this.setInitialProto(global.getFunctionPrototype());
        this.prototype = LAZY_PROTOTYPE;

        // We have to fill user accessor functions late as these are stored
        // in this object rather than in the PropertyMap of this object.
        assert objectSpill == null;
        final ScriptFunction typeErrorThrower = global.getTypeErrorThrower();
        if (findProperty("arguments", true) != null) {
            initUserAccessors("arguments", Property.NOT_CONFIGURABLE | Property.NOT_ENUMERABLE, typeErrorThrower, typeErrorThrower);
       }
        if (findProperty("caller", true) != null) {
            initUserAccessors("caller", Property.NOT_CONFIGURABLE | Property.NOT_ENUMERABLE, typeErrorThrower, typeErrorThrower);
       }
    }
}
