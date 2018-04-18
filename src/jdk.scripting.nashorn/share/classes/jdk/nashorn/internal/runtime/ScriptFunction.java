/*
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.codegen.CompilerConstants.virtualCallNoLookup;
import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.INVALID_PROGRAM_POINT;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.SecureLookupSupplier;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.support.Guards;
import jdk.nashorn.internal.codegen.ApplySpecialization;
import jdk.nashorn.internal.codegen.Compiler;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.objects.NativeFunction;
import jdk.nashorn.internal.objects.annotations.SpecializedFunction.LinkLogic;
import jdk.nashorn.internal.runtime.linker.Bootstrap;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;
import jdk.nashorn.internal.runtime.logging.DebugLogger;

/**
 * Runtime representation of a JavaScript function. This class has only private
 * and protected constructors. There are no *public* constructors - but only
 * factory methods that follow the naming pattern "createXYZ".
 */
public class ScriptFunction extends ScriptObject {

    /**
     * Method handle for prototype getter for this ScriptFunction
     */
    public static final MethodHandle G$PROTOTYPE = findOwnMH_S("G$prototype", Object.class, Object.class);

    /**
     * Method handle for prototype setter for this ScriptFunction
     */
    public static final MethodHandle S$PROTOTYPE = findOwnMH_S("S$prototype", void.class, Object.class, Object.class);

    /**
     * Method handle for length getter for this ScriptFunction
     */
    public static final MethodHandle G$LENGTH = findOwnMH_S("G$length", int.class, Object.class);

    /**
     * Method handle for name getter for this ScriptFunction
     */
    public static final MethodHandle G$NAME = findOwnMH_S("G$name", Object.class, Object.class);

    /**
     * Method handle used for implementing sync() in mozilla_compat
     */
    public static final MethodHandle INVOKE_SYNC = findOwnMH_S("invokeSync", Object.class, ScriptFunction.class, Object.class, Object.class, Object[].class);

    /**
     * Method handle for allocate function for this ScriptFunction
     */
    static final MethodHandle ALLOCATE = findOwnMH_V("allocate", Object.class);

    private static final MethodHandle WRAPFILTER = findOwnMH_S("wrapFilter", Object.class, Object.class);

    private static final MethodHandle SCRIPTFUNCTION_GLOBALFILTER = findOwnMH_S("globalFilter", Object.class, Object.class);

    /**
     * method handle to scope getter for this ScriptFunction
     */
    public static final Call GET_SCOPE = virtualCallNoLookup(ScriptFunction.class, "getScope", ScriptObject.class);

    private static final MethodHandle IS_FUNCTION_MH = findOwnMH_S("isFunctionMH", boolean.class, Object.class, ScriptFunctionData.class);

    private static final MethodHandle IS_APPLY_FUNCTION = findOwnMH_S("isApplyFunction", boolean.class, boolean.class, Object.class, Object.class);

    private static final MethodHandle IS_NONSTRICT_FUNCTION = findOwnMH_S("isNonStrictFunction", boolean.class, Object.class, Object.class, ScriptFunctionData.class);

    private static final MethodHandle ADD_ZEROTH_ELEMENT = findOwnMH_S("addZerothElement", Object[].class, Object[].class, Object.class);

    private static final MethodHandle WRAP_THIS = MH.findStatic(MethodHandles.lookup(), ScriptFunctionData.class, "wrapThis", MH.type(Object.class, Object.class));

    // various property maps used for different kinds of functions
    // property map for anonymous function that serves as Function.prototype
    private static final PropertyMap anonmap$;
    // property map for strict mode functions
    private static final PropertyMap strictmodemap$;
    // property map for bound functions
    private static final PropertyMap boundfunctionmap$;
    // property map for non-strict, non-bound functions.
    private static final PropertyMap map$;

    // Marker object for lazily initialized prototype object
    private static final Object LAZY_PROTOTYPE = new Object();

    private static final AccessControlContext GET_LOOKUP_PERMISSION_CONTEXT =
            AccessControlContextFactory.createAccessControlContext(SecureLookupSupplier.GET_LOOKUP_PERMISSION_NAME);

    private static PropertyMap createStrictModeMap(final PropertyMap map) {
        final int flags = Property.NOT_ENUMERABLE | Property.NOT_CONFIGURABLE;
        PropertyMap newMap = map;
        // Need to add properties directly to map since slots are assigned speculatively by newUserAccessors.
        newMap = newMap.addPropertyNoHistory(newMap.newUserAccessors("arguments", flags));
        newMap = newMap.addPropertyNoHistory(newMap.newUserAccessors("caller", flags));
        return newMap;
    }

    private static PropertyMap createBoundFunctionMap(final PropertyMap strictModeMap) {
        // Bound function map is same as strict function map, but additionally lacks the "prototype" property, see
        // ECMAScript 5.1 section 15.3.4.5
        return strictModeMap.deleteProperty(strictModeMap.findProperty("prototype"));
    }

    static {
        anonmap$ = PropertyMap.newMap();
        final ArrayList<Property> properties = new ArrayList<>(3);
        properties.add(AccessorProperty.create("prototype", Property.NOT_ENUMERABLE | Property.NOT_CONFIGURABLE, G$PROTOTYPE, S$PROTOTYPE));
        properties.add(AccessorProperty.create("length", Property.NOT_ENUMERABLE | Property.NOT_CONFIGURABLE | Property.NOT_WRITABLE, G$LENGTH, null));
        properties.add(AccessorProperty.create("name", Property.NOT_ENUMERABLE | Property.NOT_CONFIGURABLE | Property.NOT_WRITABLE, G$NAME, null));
        map$ = PropertyMap.newMap(properties);
        strictmodemap$ = createStrictModeMap(map$);
        boundfunctionmap$ = createBoundFunctionMap(strictmodemap$);
    }

    private static boolean isStrict(final int flags) {
        return (flags & ScriptFunctionData.IS_STRICT) != 0;
    }

    // Choose the map based on strict mode!
    private static PropertyMap getMap(final boolean strict) {
        return strict ? strictmodemap$ : map$;
    }

    /**
     * The parent scope.
     */
    private final ScriptObject scope;

    private final ScriptFunctionData data;

    /**
     * The property map used for newly allocated object when function is used as
     * constructor.
     */
    protected PropertyMap allocatorMap;

    /**
     * Reference to constructor prototype.
     */
    protected Object prototype;

    /**
     * Constructor
     *
     * @param data static function data
     * @param map property map
     * @param scope scope
     */
    private ScriptFunction(
            final ScriptFunctionData data,
            final PropertyMap map,
            final ScriptObject scope,
            final Global global) {

        super(map);

        if (Context.DEBUG) {
            constructorCount.increment();
        }

        this.data = data;
        this.scope = scope;
        this.setInitialProto(global.getFunctionPrototype());
        this.prototype = LAZY_PROTOTYPE;

        // We have to fill user accessor functions late as these are stored
        // in this object rather than in the PropertyMap of this object.
        assert objectSpill == null;
        if (isStrict() || isBoundFunction()) {
            final ScriptFunction typeErrorThrower = global.getTypeErrorThrower();
            initUserAccessors("arguments", typeErrorThrower, typeErrorThrower);
            initUserAccessors("caller", typeErrorThrower, typeErrorThrower);
        }
    }

    /**
     * Constructor
     *
     * @param name function name
     * @param methodHandle method handle to function (if specializations are
     * present, assumed to be most generic)
     * @param map property map
     * @param scope scope
     * @param specs specialized version of this function - other method handles
     * @param flags {@link ScriptFunctionData} flags
     */
    private ScriptFunction(
            final String name,
            final MethodHandle methodHandle,
            final PropertyMap map,
            final ScriptObject scope,
            final Specialization[] specs,
            final int flags,
            final Global global) {
        this(new FinalScriptFunctionData(name, methodHandle, specs, flags), map, scope, global);
    }

    /**
     * Constructor
     *
     * @param name name of function
     * @param methodHandle handle for invocation
     * @param scope scope object
     * @param specs specialized versions of this method, if available, null
     * otherwise
     * @param flags {@link ScriptFunctionData} flags
     */
    private ScriptFunction(
            final String name,
            final MethodHandle methodHandle,
            final ScriptObject scope,
            final Specialization[] specs,
            final int flags) {
        this(name, methodHandle, getMap(isStrict(flags)), scope, specs, flags, Global.instance());
    }

    /**
     * Constructor called by Nasgen generated code, zero added members, use the
     * default map. Creates builtin functions only.
     *
     * @param name name of function
     * @param invokeHandle handle for invocation
     * @param specs specialized versions of this method, if available, null
     * otherwise
     */
    protected ScriptFunction(final String name, final MethodHandle invokeHandle, final Specialization[] specs) {
        this(name, invokeHandle, map$, null, specs, ScriptFunctionData.IS_BUILTIN_CONSTRUCTOR, Global.instance());
    }

    /**
     * Constructor called by Nasgen generated code, non zero member count, use
     * the map passed as argument. Creates builtin functions only.
     *
     * @param name name of function
     * @param invokeHandle handle for invocation
     * @param map initial property map
     * @param specs specialized versions of this method, if available, null
     * otherwise
     */
    protected ScriptFunction(final String name, final MethodHandle invokeHandle, final PropertyMap map, final Specialization[] specs) {
        this(name, invokeHandle, map.addAll(map$), null, specs, ScriptFunctionData.IS_BUILTIN_CONSTRUCTOR, Global.instance());
    }

    // Factory methods to create various functions
    /**
     * Factory method called by compiler generated code for functions that need
     * parent scope.
     *
     * @param constants the generated class' constant array
     * @param index the index of the {@code RecompilableScriptFunctionData}
     * object in the constants array.
     * @param scope the parent scope object
     * @return a newly created function object
     */
    public static ScriptFunction create(final Object[] constants, final int index, final ScriptObject scope) {
        final RecompilableScriptFunctionData data = (RecompilableScriptFunctionData) constants[index];
        return new ScriptFunction(data, getMap(data.isStrict()), scope, Global.instance());
    }

    /**
     * Factory method called by compiler generated code for functions that don't
     * need parent scope.
     *
     * @param constants the generated class' constant array
     * @param index the index of the {@code RecompilableScriptFunctionData}
     * object in the constants array.
     * @return a newly created function object
     */
    public static ScriptFunction create(final Object[] constants, final int index) {
        return create(constants, index, null);
    }

    /**
     * Create anonymous function that serves as Function.prototype
     *
     * @return anonymous function object
     */
    public static ScriptFunction createAnonymous() {
        return new ScriptFunction("", GlobalFunctions.ANONYMOUS, anonmap$, null);
    }

    // builtin function create helper factory
    private static ScriptFunction createBuiltin(final String name, final MethodHandle methodHandle, final Specialization[] specs, final int flags) {
        final ScriptFunction func = new ScriptFunction(name, methodHandle, null, specs, flags);
        func.setPrototype(UNDEFINED);
        // Non-constructor built-in functions do not have "prototype" property
        func.deleteOwnProperty(func.getMap().findProperty("prototype"));

        return func;
    }

    /**
     * Factory method for non-constructor built-in functions
     *
     * @param name function name
     * @param methodHandle handle for invocation
     * @param specs specialized versions of function if available, null
     * otherwise
     * @return new ScriptFunction
     */
    public static ScriptFunction createBuiltin(final String name, final MethodHandle methodHandle, final Specialization[] specs) {
        return ScriptFunction.createBuiltin(name, methodHandle, specs, ScriptFunctionData.IS_BUILTIN);
    }

    /**
     * Factory method for non-constructor built-in functions
     *
     * @param name function name
     * @param methodHandle handle for invocation
     * @return new ScriptFunction
     */
    public static ScriptFunction createBuiltin(final String name, final MethodHandle methodHandle) {
        return ScriptFunction.createBuiltin(name, methodHandle, null);
    }

    /**
     * Factory method for non-constructor built-in, strict functions
     *
     * @param name function name
     * @param methodHandle handle for invocation
     * @return new ScriptFunction
     */
    public static ScriptFunction createStrictBuiltin(final String name, final MethodHandle methodHandle) {
        return ScriptFunction.createBuiltin(name, methodHandle, null, ScriptFunctionData.IS_BUILTIN | ScriptFunctionData.IS_STRICT);
    }

    // Subclass to represent bound functions
    private static class Bound extends ScriptFunction {
        private final ScriptFunction target;

        Bound(final ScriptFunctionData boundData, final ScriptFunction target) {
            super(boundData, boundfunctionmap$, null, Global.instance());
            setPrototype(ScriptRuntime.UNDEFINED);
            this.target = target;
        }

        @Override
        protected ScriptFunction getTargetFunction() {
            return target;
        }
    }

    /**
     * Creates a version of this function bound to a specific "self" and other
     * arguments, as per {@code Function.prototype.bind} functionality in
     * ECMAScript 5.1 section 15.3.4.5.
     *
     * @param self the self to bind to this function. Can be null (in which
     * case, null is bound as this).
     * @param args additional arguments to bind to this function. Can be null or
     * empty to not bind additional arguments.
     * @return a function with the specified self and parameters bound.
     */
    public final ScriptFunction createBound(final Object self, final Object[] args) {
        return new Bound(data.makeBoundFunctionData(this, self, args), getTargetFunction());
    }

    /**
     * Create a function that invokes this function synchronized on {@code sync}
     * or the self object of the invocation.
     *
     * @param sync the Object to synchronize on, or undefined
     * @return synchronized function
     */
    public final ScriptFunction createSynchronized(final Object sync) {
        final MethodHandle mh = MH.insertArguments(ScriptFunction.INVOKE_SYNC, 0, this, sync);
        return createBuiltin(getName(), mh);
    }

    @Override
    public String getClassName() {
        return "Function";
    }

    /**
     * ECMA 15.3.5.3 [[HasInstance]] (V) Step 3 if "prototype" value is not an
     * Object, throw TypeError
     */
    @Override
    public boolean isInstance(final ScriptObject instance) {
        final Object basePrototype = getTargetFunction().getPrototype();
        if (!(basePrototype instanceof ScriptObject)) {
            throw typeError("prototype.not.an.object", ScriptRuntime.safeToString(getTargetFunction()), ScriptRuntime.safeToString(basePrototype));
        }

        for (ScriptObject proto = instance.getProto(); proto != null; proto = proto.getProto()) {
            if (proto == basePrototype) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the target function for this function. If the function was not
     * created using {@link #createBound(Object, Object[])}, its target
     * function is itself. If it is bound, its target function is the target
     * function of the function it was made from (therefore, the target function
     * is always the final, unbound recipient of the calls).
     *
     * @return the target function for this function.
     */
    protected ScriptFunction getTargetFunction() {
        return this;
    }

    final boolean isBoundFunction() {
        return getTargetFunction() != this;
    }

    /**
     * Set the arity of this ScriptFunction
     *
     * @param arity arity
     */
    public final void setArity(final int arity) {
        data.setArity(arity);
    }

    /**
     * Is this a ECMAScript 'use strict' function?
     *
     * @return true if function is in strict mode
     */
    public final boolean isStrict() {
        return data.isStrict();
    }

    /**
     * Is this is a function with all variables in scope?
     * @return true if function has all
     */
    public boolean hasAllVarsInScope() {
        return data instanceof RecompilableScriptFunctionData &&
                (((RecompilableScriptFunctionData) data).getFunctionFlags() & FunctionNode.HAS_ALL_VARS_IN_SCOPE) != 0;
    }

    /**
     * Returns true if this is a non-strict, non-built-in function that requires
     * non-primitive this argument according to ECMA 10.4.3.
     *
     * @return true if this argument must be an object
     */
    public final boolean needsWrappedThis() {
        return data.needsWrappedThis();
    }

    private static boolean needsWrappedThis(final Object fn) {
        return fn instanceof ScriptFunction ? ((ScriptFunction) fn).needsWrappedThis() : false;
    }

    /**
     * Execute this script function.
     *
     * @param self Target object.
     * @param arguments Call arguments.
     * @return ScriptFunction result.
     * @throws Throwable if there is an exception/error with the invocation or
     * thrown from it
     */
    final Object invoke(final Object self, final Object... arguments) throws Throwable {
        if (Context.DEBUG) {
            invokes.increment();
        }
        return data.invoke(this, self, arguments);
    }

    /**
     * Execute this script function as a constructor.
     *
     * @param arguments Call arguments.
     * @return Newly constructed result.
     * @throws Throwable if there is an exception/error with the invocation or
     * thrown from it
     */
    final Object construct(final Object... arguments) throws Throwable {
        return data.construct(this, arguments);
    }

    /**
     * Allocate function. Called from generated {@link ScriptObject} code for
     * allocation as a factory method
     *
     * @return a new instance of the {@link ScriptObject} whose allocator this
     * is
     */
    @SuppressWarnings("unused")
    private Object allocate() {
        if (Context.DEBUG) {
            allocations.increment();
        }

        assert !isBoundFunction(); // allocate never invoked on bound functions

        final ScriptObject prototype = getAllocatorPrototype();
        final ScriptObject object = data.allocate(getAllocatorMap(prototype));

        if (object != null) {
            object.setInitialProto(prototype);
        }

        return object;
    }

    /**
     * Get the property map used by "allocate"
     * @param prototype actual prototype object
     * @return property map
     */
    private PropertyMap getAllocatorMap(final ScriptObject prototype) {
        if (allocatorMap == null || allocatorMap.isInvalidSharedMapFor(prototype)) {
            // The prototype map has changed since this function was last used as constructor.
            // Get a new allocator map.
            allocatorMap = data.getAllocatorMap(prototype);
        }
        return allocatorMap;
    }

    /**
     * Return the actual prototype used by "allocate"
     * @return allocator prototype
     */
    private ScriptObject getAllocatorPrototype() {
        final Object prototype = getPrototype();
        if (prototype instanceof ScriptObject) {
            return (ScriptObject) prototype;
        }
        return Global.objectPrototype();
    }

    @Override
    public final String safeToString() {
        return toSource();
    }

    @Override
    public final String toString() {
        return data.toString();
    }

    /**
     * Get this function as a String containing its source code. If no source
     * code exists in this ScriptFunction, its contents will be displayed as
     * {@code [native code]}
     *
     * @return string representation of this function's source
     */
    public final String toSource() {
        return data.toSource();
    }

    /**
     * Get the prototype object for this function
     *
     * @return prototype
     */
    public final Object getPrototype() {
        if (prototype == LAZY_PROTOTYPE) {
            prototype = new PrototypeObject(this);
        }
        return prototype;
    }

    /**
     * Set the prototype object for this function
     *
     * @param newPrototype new prototype object
     */
    public final void setPrototype(final Object newPrototype) {
        if (newPrototype instanceof ScriptObject && newPrototype != this.prototype && allocatorMap != null) {
            // Unset allocator map to be replaced with one matching the new prototype.
            allocatorMap = null;
        }
        this.prototype = newPrototype;
    }

    /**
     * Return the invoke handle bound to a given ScriptObject self reference. If
     * callee parameter is required result is rebound to this.
     *
     * @param self self reference
     * @return bound invoke handle
     */
    public final MethodHandle getBoundInvokeHandle(final Object self) {
        return MH.bindTo(bindToCalleeIfNeeded(data.getGenericInvoker(scope)), self);
    }

    /**
     * Bind the method handle to this {@code ScriptFunction} instance if it
     * needs a callee parameter. If this function's method handles don't have a
     * callee parameter, the handle is returned unchanged.
     *
     * @param methodHandle the method handle to potentially bind to this
     * function instance.
     * @return the potentially bound method handle
     */
    private MethodHandle bindToCalleeIfNeeded(final MethodHandle methodHandle) {
        return ScriptFunctionData.needsCallee(methodHandle) ? MH.bindTo(methodHandle, this) : methodHandle;

    }

    /**
     * Get the documentation for this function
     *
     * @return the documentation
     */
    public final String getDocumentation() {
        return data.getDocumentation();
    }

    /**
     * Get the documentation key for this function
     *
     * @return the documentation key
     */
    public final String getDocumentationKey() {
        return data.getDocumentationKey();
    }

    /**
     * Set the documentation key for this function
     *
     * @param docKey documentation key String for this function
     */
    public final void setDocumentationKey(final String docKey) {
        data.setDocumentationKey(docKey);
    }

    /**
     * Get the name for this function
     *
     * @return the name
     */
    public final String getName() {
        return data.getName();
    }

    /**
     * Get the scope for this function
     *
     * @return the scope
     */
    public final ScriptObject getScope() {
        return scope;
    }

    /**
     * Prototype getter for this ScriptFunction - follows the naming convention
     * used by Nasgen and the code generator
     *
     * @param self self reference
     * @return self's prototype
     */
    public static Object G$prototype(final Object self) {
        return self instanceof ScriptFunction
                ? ((ScriptFunction) self).getPrototype()
                : UNDEFINED;
    }

    /**
     * Prototype setter for this ScriptFunction - follows the naming convention
     * used by Nasgen and the code generator
     *
     * @param self self reference
     * @param prototype prototype to set
     */
    public static void S$prototype(final Object self, final Object prototype) {
        if (self instanceof ScriptFunction) {
            ((ScriptFunction) self).setPrototype(prototype);
        }
    }

    /**
     * Length getter - ECMA 15.3.3.2: Function.length
     *
     * @param self self reference
     * @return length
     */
    public static int G$length(final Object self) {
        if (self instanceof ScriptFunction) {
            return ((ScriptFunction) self).data.getArity();
        }

        return 0;
    }

    /**
     * Name getter - ECMA Function.name
     *
     * @param self self reference
     * @return the name, or undefined if none
     */
    public static Object G$name(final Object self) {
        if (self instanceof ScriptFunction) {
            return ((ScriptFunction) self).getName();
        }

        return UNDEFINED;
    }

    /**
     * Get the prototype for this ScriptFunction
     *
     * @param constructor constructor
     * @return prototype, or null if given constructor is not a ScriptFunction
     */
    public static ScriptObject getPrototype(final ScriptFunction constructor) {
        if (constructor != null) {
            final Object proto = constructor.getPrototype();
            if (proto instanceof ScriptObject) {
                return (ScriptObject) proto;
            }
        }

        return null;
    }

    // These counters are updated only in debug mode.
    private static LongAdder constructorCount;
    private static LongAdder invokes;
    private static LongAdder allocations;

    static {
        if (Context.DEBUG) {
            constructorCount = new LongAdder();
            invokes = new LongAdder();
            allocations = new LongAdder();
        }
    }

    /**
     * @return the constructorCount
     */
    public static long getConstructorCount() {
        return constructorCount.longValue();
    }

    /**
     * @return the invokes
     */
    public static long getInvokes() {
        return invokes.longValue();
    }

    /**
     * @return the allocations
     */
    public static long getAllocations() {
        return allocations.longValue();
    }

    @Override
    protected GuardedInvocation findNewMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final MethodType type = desc.getMethodType();
        assert desc.getMethodType().returnType() == Object.class && !NashornCallSiteDescriptor.isOptimistic(desc);
        final CompiledFunction cf = data.getBestConstructor(type, scope, CompiledFunction.NO_FUNCTIONS);
        final GuardedInvocation bestCtorInv = cf.createConstructorInvocation();
        //TODO - ClassCastException
        return new GuardedInvocation(pairArguments(bestCtorInv.getInvocation(), type), getFunctionGuard(this, cf.getFlags()), bestCtorInv.getSwitchPoints(), null);
    }

    private static Object wrapFilter(final Object obj) {
        if (obj instanceof ScriptObject || !ScriptFunctionData.isPrimitiveThis(obj)) {
            return obj;
        }
        return Context.getGlobal().wrapAsObject(obj);
    }

    @SuppressWarnings("unused")
    private static Object globalFilter(final Object object) {
        // replace whatever we get with the current global object
        return Context.getGlobal();
    }

    /**
     * Some receivers are primitive, in that case, according to the Spec we
     * create a new native object per callsite with the wrap filter. We can only
     * apply optimistic builtins if there is no per instance state saved for
     * these wrapped objects (e.g. currently NativeStrings), otherwise we can't
     * create optimistic versions
     *
     * @param self receiver
     * @param linkLogicClass linkLogicClass, or null if no link logic exists
     * @return link logic instance, or null if one could not be constructed for
     * this receiver
     */
    private static LinkLogic getLinkLogic(final Object self, final Class<? extends LinkLogic> linkLogicClass) {
        if (linkLogicClass == null) {
            return LinkLogic.EMPTY_INSTANCE; //always OK to link this, specialization but without special linking logic
        }

        if (!Context.getContextTrusted().getEnv()._optimistic_types) {
            return null; //if optimistic types are off, optimistic builtins are too
        }

        final Object wrappedSelf = wrapFilter(self);
        if (wrappedSelf instanceof OptimisticBuiltins) {
            if (wrappedSelf != self && ((OptimisticBuiltins) wrappedSelf).hasPerInstanceAssumptions()) {
                return null; //pessimistic - we created a wrapped object different from the primitive, but the assumptions have instance state
            }
            return ((OptimisticBuiltins) wrappedSelf).getLinkLogic(linkLogicClass);
        }
        return null;
    }

    /**
     * StandardOperation.CALL call site signature: (callee, thiz, [args...]) generated method
     * signature: (callee, thiz, [args...])
     *
     * cases:
     * (a) method has callee parameter
     *     (1) for local/scope calls, we just bind thiz and drop the second argument.
     *     (2) for normal this-calls, we have to swap thiz and callee to get matching signatures.
     * (b) method doesn't have callee parameter (builtin functions)
     *     (3) for local/scope calls, bind thiz and drop both callee and thiz.
     *     (4) for normal this-calls, drop callee.
     *
     * @return guarded invocation for call
     */
    @Override
    protected GuardedInvocation findCallMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final MethodType type = desc.getMethodType();

        final String name = getName();
        final boolean isUnstable = request.isCallSiteUnstable();
        final boolean scopeCall = NashornCallSiteDescriptor.isScope(desc);
        final boolean isCall = !scopeCall && data.isBuiltin() && "call".equals(name);
        final boolean isApply = !scopeCall && data.isBuiltin() && "apply".equals(name);

        final boolean isApplyOrCall = isCall | isApply;

        if (isUnstable && !isApplyOrCall) {
            //megamorphic - replace call with apply
            final MethodHandle handle;
            //ensure that the callsite is vararg so apply can consume it
            if (type.parameterCount() == 3 && type.parameterType(2) == Object[].class) {
                // Vararg call site
                handle = ScriptRuntime.APPLY.methodHandle();
            } else {
                // (callee, this, args...) => (callee, this, args[])
                handle = MH.asCollector(ScriptRuntime.APPLY.methodHandle(), Object[].class, type.parameterCount() - 2);
            }

            // If call site is statically typed to take a ScriptFunction, we don't need a guard, otherwise we need a
            // generic "is this a ScriptFunction?" guard.
            return new GuardedInvocation(
                    handle,
                    null,
                    (SwitchPoint) null,
                    ClassCastException.class);
        }

        MethodHandle boundHandle;
        MethodHandle guard = null;

        // Special handling of Function.apply and Function.call. Note we must be invoking
        if (isApplyOrCall && !isUnstable) {
            final Object[] args = request.getArguments();
            if (Bootstrap.isCallable(args[1])) {
                return createApplyOrCallCall(isApply, desc, request, args);
            }
        } //else just fall through and link as ordinary function or unstable apply

        int programPoint = INVALID_PROGRAM_POINT;
        if (NashornCallSiteDescriptor.isOptimistic(desc)) {
            programPoint = NashornCallSiteDescriptor.getProgramPoint(desc);
        }

        CompiledFunction cf = data.getBestInvoker(type, scope, CompiledFunction.NO_FUNCTIONS);
        final Object self = request.getArguments()[1];
        final Collection<CompiledFunction> forbidden = new HashSet<>();

        //check for special fast versions of the compiled function
        final List<SwitchPoint> sps = new ArrayList<>();
        Class<? extends Throwable> exceptionGuard = null;

        while (cf.isSpecialization()) {
            final Class<? extends LinkLogic> linkLogicClass = cf.getLinkLogicClass();
            //if linklogic is null, we can always link with the standard mechanism, it's still a specialization
            final LinkLogic linkLogic = getLinkLogic(self, linkLogicClass);

            if (linkLogic != null && linkLogic.checkLinkable(self, desc, request)) {
                final DebugLogger log = Context.getContextTrusted().getLogger(Compiler.class);

                if (log.isEnabled()) {
                    log.info("Linking optimistic builtin function: '", name, "' args=", Arrays.toString(request.getArguments()), " desc=", desc);
                }

                exceptionGuard = linkLogic.getRelinkException();

                break;
            }

            //could not link this specialization because link check failed
            forbidden.add(cf);
            final CompiledFunction oldCf = cf;
            cf = data.getBestInvoker(type, scope, forbidden);
            assert oldCf != cf;
        }

        final GuardedInvocation bestInvoker = cf.createFunctionInvocation(type.returnType(), programPoint);
        final MethodHandle callHandle = bestInvoker.getInvocation();

        if (data.needsCallee()) {
            if (scopeCall && needsWrappedThis()) {
                // (callee, this, args...) => (callee, [this], args...)
                boundHandle = MH.filterArguments(callHandle, 1, SCRIPTFUNCTION_GLOBALFILTER);
            } else {
                // It's already (callee, this, args...), just what we need
                boundHandle = callHandle;
            }
        } else if (data.isBuiltin() && Global.isBuiltInJavaExtend(this)) {
            // We're binding the current lookup as "self" so the function can do
            // security-sensitive creation of adapter classes.
            boundHandle = MH.dropArguments(MH.bindTo(callHandle, getLookupPrivileged(desc)), 0, type.parameterType(0), type.parameterType(1));
        } else if (data.isBuiltin() && Global.isBuiltInJavaTo(this)) {
            // We're binding the current call site descriptor as "self" so the function can do
            // security-sensitive creation of adapter classes.
            boundHandle = MH.dropArguments(MH.bindTo(callHandle, desc), 0, type.parameterType(0), type.parameterType(1));
        } else if (scopeCall && needsWrappedThis()) {
            // Make a handle that drops the passed "this" argument and substitutes either Global or Undefined
            // (this, args...) => ([this], args...)
            boundHandle = MH.filterArguments(callHandle, 0, SCRIPTFUNCTION_GLOBALFILTER);
            // ([this], args...) => ([callee], [this], args...)
            boundHandle = MH.dropArguments(boundHandle, 0, type.parameterType(0));
        } else {
            // (this, args...) => ([callee], this, args...)
            boundHandle = MH.dropArguments(callHandle, 0, type.parameterType(0));
        }

        // For non-strict functions, check whether this-object is primitive type.
        // If so add a to-object-wrapper argument filter.
        // Else install a guard that will trigger a relink when the argument becomes primitive.
        if (!scopeCall && needsWrappedThis()) {
            if (ScriptFunctionData.isPrimitiveThis(request.getArguments()[1])) {
                boundHandle = MH.filterArguments(boundHandle, 1, WRAPFILTER);
            } else {
                guard = getNonStrictFunctionGuard(this);
            }
        }

        // Is this an unstable callsite which was earlier apply-to-call optimized?
        // If so, earlier apply2call would have exploded arguments. We have to convert
        // that as an array again!
        if (isUnstable && NashornCallSiteDescriptor.isApplyToCall(desc)) {
            boundHandle = MH.asCollector(boundHandle, Object[].class, type.parameterCount() - 2);
        }

        boundHandle = pairArguments(boundHandle, type);

        if (bestInvoker.getSwitchPoints() != null) {
            sps.addAll(Arrays.asList(bestInvoker.getSwitchPoints()));
        }
        final SwitchPoint[] spsArray = sps.isEmpty() ? null : sps.toArray(new SwitchPoint[0]);

        return new GuardedInvocation(
                boundHandle,
                guard == null ?
                        getFunctionGuard(
                                this,
                                cf.getFlags()) :
                        guard,
                spsArray,
                exceptionGuard);
    }

    private static Lookup getLookupPrivileged(final CallSiteDescriptor desc) {
        // NOTE: we'd rather not make NashornCallSiteDescriptor.getLookupPrivileged public.
        return AccessController.doPrivileged((PrivilegedAction<Lookup>)()->desc.getLookup(),
                GET_LOOKUP_PERMISSION_CONTEXT);
    }

    private GuardedInvocation createApplyOrCallCall(final boolean isApply, final CallSiteDescriptor desc, final LinkRequest request, final Object[] args) {
        final MethodType descType = desc.getMethodType();
        final int paramCount = descType.parameterCount();
        if (descType.parameterType(paramCount - 1).isArray()) {
            // This is vararg invocation of apply or call. This can normally only happen when we do a recursive
            // invocation of createApplyOrCallCall (because we're doing apply-of-apply). In this case, create delegate
            // linkage by unpacking the vararg invocation and use pairArguments to introduce the necessary spreader.
            return createVarArgApplyOrCallCall(isApply, desc, request, args);
        }

        final boolean passesThis = paramCount > 2;
        final boolean passesArgs = paramCount > 3;
        final int realArgCount = passesArgs ? paramCount - 3 : 0;

        final Object appliedFn = args[1];
        final boolean appliedFnNeedsWrappedThis = needsWrappedThis(appliedFn);

        //box call back to apply
        CallSiteDescriptor appliedDesc = desc;
        final SwitchPoint applyToCallSwitchPoint = Global.getBuiltinFunctionApplySwitchPoint();
        //enough to change the proto switchPoint here

        final boolean isApplyToCall = NashornCallSiteDescriptor.isApplyToCall(desc);
        final boolean isFailedApplyToCall = isApplyToCall && applyToCallSwitchPoint.hasBeenInvalidated();

        // R(apply|call, ...) => R(...)
        MethodType appliedType = descType.dropParameterTypes(0, 1);
        if (!passesThis) {
            // R() => R(this)
            appliedType = appliedType.insertParameterTypes(1, Object.class);
        } else if (appliedFnNeedsWrappedThis) {
            appliedType = appliedType.changeParameterType(1, Object.class);
        }

        /*
         * dropArgs is a synthetic method handle that contains any args that we need to
         * get rid of that come after the arguments array in the apply case. We adapt
         * the callsite to ask for 3 args only and then dropArguments on the method handle
         * to make it fit the extraneous args.
         */
        MethodType dropArgs = MH.type(void.class);
        if (isApply && !isFailedApplyToCall) {
            final int pc = appliedType.parameterCount();
            for (int i = 3; i < pc; i++) {
                dropArgs = dropArgs.appendParameterTypes(appliedType.parameterType(i));
            }
            if (pc > 3) {
                appliedType = appliedType.dropParameterTypes(3, pc);
            }
        }

        if (isApply || isFailedApplyToCall) {
            if (passesArgs) {
                // R(this, args) => R(this, Object[])
                appliedType = appliedType.changeParameterType(2, Object[].class);
                // drop any extraneous arguments for the apply fail case
                if (isFailedApplyToCall) {
                    appliedType = appliedType.dropParameterTypes(3, paramCount - 1);
                }
            } else {
                // R(this) => R(this, Object[])
                appliedType = appliedType.insertParameterTypes(2, Object[].class);
            }
        }

        appliedDesc = appliedDesc.changeMethodType(appliedType); //no extra args

        // Create the same arguments for the delegate linking request that would be passed in an actual apply'd invocation
        final Object[] appliedArgs = new Object[isApply ? 3 : appliedType.parameterCount()];
        appliedArgs[0] = appliedFn;
        appliedArgs[1] = passesThis ? appliedFnNeedsWrappedThis ? ScriptFunctionData.wrapThis(args[2]) : args[2] : ScriptRuntime.UNDEFINED;
        if (isApply && !isFailedApplyToCall) {
            appliedArgs[2] = passesArgs ? NativeFunction.toApplyArgs(args[3]) : ScriptRuntime.EMPTY_ARRAY;
        } else {
            if (passesArgs) {
                if (isFailedApplyToCall) {
                    final Object[] tmp = new Object[args.length - 3];
                    System.arraycopy(args, 3, tmp, 0, tmp.length);
                    appliedArgs[2] = NativeFunction.toApplyArgs(tmp);
                } else {
                    assert !isApply;
                    System.arraycopy(args, 3, appliedArgs, 2, args.length - 3);
                }
            } else if (isFailedApplyToCall) {
                appliedArgs[2] = ScriptRuntime.EMPTY_ARRAY;
            }
        }

        // Ask the linker machinery for an invocation of the target function
        final LinkRequest appliedRequest = request.replaceArguments(appliedDesc, appliedArgs);

        GuardedInvocation appliedInvocation;
        try {
            appliedInvocation = Bootstrap.getLinkerServices().getGuardedInvocation(appliedRequest);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        assert appliedRequest != null; // Bootstrap.isCallable() returned true for args[1], so it must produce a linkage.

        final Class<?> applyFnType = descType.parameterType(0);
        // Invocation and guard handles from apply invocation.
        MethodHandle inv = appliedInvocation.getInvocation();
        MethodHandle guard = appliedInvocation.getGuard();

        if (isApply && !isFailedApplyToCall) {
            if (passesArgs) {
                // Make sure that the passed argArray is converted to Object[] the same way NativeFunction.apply() would do it.
                inv = MH.filterArguments(inv, 2, NativeFunction.TO_APPLY_ARGS);
                // Some guards (non-strict functions with non-primitive this) have a this-object parameter, so we
                // need to apply this transformations to them as well.
                if (guard.type().parameterCount() > 2) {
                    guard = MH.filterArguments(guard, 2, NativeFunction.TO_APPLY_ARGS);
                }
            } else {
                // If the original call site doesn't pass argArray, pass in an empty array
                inv = MH.insertArguments(inv, 2, (Object) ScriptRuntime.EMPTY_ARRAY);
            }
        }

        if (isApplyToCall) {
            if (isFailedApplyToCall) {
                //take the real arguments that were passed to a call and force them into the apply instead
                Context.getContextTrusted().getLogger(ApplySpecialization.class).info("Collection arguments to revert call to apply in " + appliedFn);
                inv = MH.asCollector(inv, Object[].class, realArgCount);
            } else {
                appliedInvocation = appliedInvocation.addSwitchPoint(applyToCallSwitchPoint);
            }
        }

        if (!passesThis) {
            // If the original call site doesn't pass in a thisArg, pass in Global/undefined as needed
            inv = bindImplicitThis(appliedFnNeedsWrappedThis, inv);
            // guard may have this-parameter that needs to be inserted
            if (guard.type().parameterCount() > 1) {
                guard = bindImplicitThis(appliedFnNeedsWrappedThis, guard);
            }
        } else if (appliedFnNeedsWrappedThis) {
            // target function needs a wrapped this, so make sure we filter for that
            inv = MH.filterArguments(inv, 1, WRAP_THIS);
            // guard may have this-parameter that needs to be wrapped
            if (guard.type().parameterCount() > 1) {
                guard = MH.filterArguments(guard, 1, WRAP_THIS);
            }
        }

        final MethodType guardType = guard.type(); // Needed for combining guards below

        // We need to account for the dropped (apply|call) function argument.
        inv = MH.dropArguments(inv, 0, applyFnType);
        guard = MH.dropArguments(guard, 0, applyFnType);

        /*
         * Dropargs can only be non-()V in the case of isApply && !isFailedApplyToCall, which
         * is when we need to add arguments to the callsite to catch and ignore the synthetic
         * extra args that someone has added to the command line.
         */
        for (int i = 0; i < dropArgs.parameterCount(); i++) {
            inv = MH.dropArguments(inv, 4 + i, dropArgs.parameterType(i));
        }

        // Take the "isApplyFunction" guard, and bind it to this function.
        MethodHandle applyFnGuard = MH.insertArguments(IS_APPLY_FUNCTION, 2, this); //TODO replace this with switchpoint
        // Adapt the guard to receive all the arguments that the original guard does.
        applyFnGuard = MH.dropArguments(applyFnGuard, 2, guardType.parameterArray());
        // Fold the original function guard into our apply guard.
        guard = MH.foldArguments(applyFnGuard, guard);

        return appliedInvocation.replaceMethods(inv, guard);
    }

    /*
     * This method is used for linking nested apply. Specialized apply and call linking will create a variable arity
     * call site for an apply call; when createApplyOrCallCall sees a linking request for apply or call with
     * Nashorn-style variable arity call site (last argument type is Object[]) it'll delegate to this method.
     * This method converts the link request from a vararg to a non-vararg one (unpacks the array), then delegates back
     * to createApplyOrCallCall (with which it is thus mutually recursive), and adds appropriate argument spreaders to
     * invocation and the guard of whatever createApplyOrCallCall returned to adapt it back into a variable arity
     * invocation. It basically reduces the problem of vararg call site linking of apply and call back to the (already
     * solved by createApplyOrCallCall) non-vararg call site linking.
     */
    private GuardedInvocation createVarArgApplyOrCallCall(final boolean isApply, final CallSiteDescriptor desc,
            final LinkRequest request, final Object[] args) {
        final MethodType descType = desc.getMethodType();
        final int paramCount = descType.parameterCount();
        final Object[] varArgs = (Object[]) args[paramCount - 1];
        // -1 'cause we're not passing the vararg array itself
        final int copiedArgCount = args.length - 1;
        final int varArgCount = varArgs.length;

        // Spread arguments for the delegate createApplyOrCallCall invocation.
        final Object[] spreadArgs = new Object[copiedArgCount + varArgCount];
        System.arraycopy(args, 0, spreadArgs, 0, copiedArgCount);
        System.arraycopy(varArgs, 0, spreadArgs, copiedArgCount, varArgCount);

        // Spread call site descriptor for the delegate createApplyOrCallCall invocation. We drop vararg array and
        // replace it with a list of Object.class.
        final MethodType spreadType = descType.dropParameterTypes(paramCount - 1, paramCount).appendParameterTypes(
                Collections.<Class<?>>nCopies(varArgCount, Object.class));
        final CallSiteDescriptor spreadDesc = desc.changeMethodType(spreadType);

        // Delegate back to createApplyOrCallCall with the spread (that is, reverted to non-vararg) request/
        final LinkRequest spreadRequest = request.replaceArguments(spreadDesc, spreadArgs);
        final GuardedInvocation spreadInvocation = createApplyOrCallCall(isApply, spreadDesc, spreadRequest, spreadArgs);

        // Add spreader combinators to returned invocation and guard.
        return spreadInvocation.replaceMethods(
                // Use standard ScriptObject.pairArguments on the invocation
                pairArguments(spreadInvocation.getInvocation(), descType),
                // Use our specialized spreadGuardArguments on the guard (see below).
                spreadGuardArguments(spreadInvocation.getGuard(), descType));
    }

    private static MethodHandle spreadGuardArguments(final MethodHandle guard, final MethodType descType) {
        final MethodType guardType = guard.type();
        final int guardParamCount = guardType.parameterCount();
        final int descParamCount = descType.parameterCount();
        final int spreadCount = guardParamCount - descParamCount + 1;
        if (spreadCount <= 0) {
            // Guard doesn't dip into the varargs
            return guard;
        }

        final MethodHandle arrayConvertingGuard;
        // If the last parameter type of the guard is an array, then it is already itself a guard for a vararg apply
        // invocation. We must filter the last argument with toApplyArgs otherwise deeper levels of nesting will fail
        // with ClassCastException of NativeArray to Object[].
        if (guardType.parameterType(guardParamCount - 1).isArray()) {
            arrayConvertingGuard = MH.filterArguments(guard, guardParamCount - 1, NativeFunction.TO_APPLY_ARGS);
        } else {
            arrayConvertingGuard = guard;
        }

        return ScriptObject.adaptHandleToVarArgCallSite(arrayConvertingGuard, descParamCount);
    }

    private static MethodHandle bindImplicitThis(final boolean needsWrappedThis, final MethodHandle mh) {
        final MethodHandle bound;
        if (needsWrappedThis) {
            bound = MH.filterArguments(mh, 1, SCRIPTFUNCTION_GLOBALFILTER);
        } else {
            bound = mh;
        }
        return MH.insertArguments(bound, 1, ScriptRuntime.UNDEFINED);
    }

    /**
     * Used for noSuchMethod/noSuchProperty and JSAdapter hooks.
     *
     * These don't want a callee parameter, so bind that. Name binding is
     * optional.
     */
    MethodHandle getCallMethodHandle(final MethodType type, final String bindName) {
        return pairArguments(bindToNameIfNeeded(bindToCalleeIfNeeded(data.getGenericInvoker(scope)), bindName), type);
    }

    private static MethodHandle bindToNameIfNeeded(final MethodHandle methodHandle, final String bindName) {
        if (bindName == null) {
            return methodHandle;
        }

        // if it is vararg method, we need to extend argument array with
        // a new zeroth element that is set to bindName value.
        final MethodType methodType = methodHandle.type();
        final int parameterCount = methodType.parameterCount();

        if (parameterCount < 2) {
            return methodHandle; // method does not have enough parameters
        }
        final boolean isVarArg = methodType.parameterType(parameterCount - 1).isArray();

        if (isVarArg) {
            return MH.filterArguments(methodHandle, 1, MH.insertArguments(ADD_ZEROTH_ELEMENT, 1, bindName));
        }
        return MH.insertArguments(methodHandle, 1, bindName);
    }

    /**
     * Get the guard that checks if a {@link ScriptFunction} is equal to a known
     * ScriptFunction, using reference comparison
     *
     * @param function The ScriptFunction to check against. This will be bound
     * to the guard method handle
     *
     * @return method handle for guard
     */
    private static MethodHandle getFunctionGuard(final ScriptFunction function, final int flags) {
        assert function.data != null;
        // Built-in functions have a 1-1 correspondence to their ScriptFunctionData, so we can use a cheaper identity
        // comparison for them.
        if (function.data.isBuiltin()) {
            return Guards.getIdentityGuard(function);
        }
        return MH.insertArguments(IS_FUNCTION_MH, 1, function.data);
    }

    /**
     * Get a guard that checks if a {@link ScriptFunction} is equal to a known
     * ScriptFunction using reference comparison, and whether the type of the
     * second argument (this-object) is not a JavaScript primitive type.
     *
     * @param function The ScriptFunction to check against. This will be bound
     * to the guard method handle
     *
     * @return method handle for guard
     */
    private static MethodHandle getNonStrictFunctionGuard(final ScriptFunction function) {
        assert function.data != null;
        return MH.insertArguments(IS_NONSTRICT_FUNCTION, 2, function.data);
    }

    @SuppressWarnings("unused")
    private static boolean isFunctionMH(final Object self, final ScriptFunctionData data) {
        return self instanceof ScriptFunction && ((ScriptFunction) self).data == data;
    }

    @SuppressWarnings("unused")
    private static boolean isNonStrictFunction(final Object self, final Object arg, final ScriptFunctionData data) {
        return self instanceof ScriptFunction && ((ScriptFunction) self).data == data && arg instanceof ScriptObject;
    }

    //TODO this can probably be removed given that we have builtin switchpoints in the context
    @SuppressWarnings("unused")
    private static boolean isApplyFunction(final boolean appliedFnCondition, final Object self, final Object expectedSelf) {
        // NOTE: we're using self == expectedSelf as we're only using this with built-in functions apply() and call()
        return appliedFnCondition && self == expectedSelf;
    }

    @SuppressWarnings("unused")
    private static Object[] addZerothElement(final Object[] args, final Object value) {
        // extends input array with by adding new zeroth element
        final Object[] src = args == null ? ScriptRuntime.EMPTY_ARRAY : args;
        final Object[] result = new Object[src.length + 1];
        System.arraycopy(src, 0, result, 1, src.length);
        result[0] = value;
        return result;
    }

    @SuppressWarnings("unused")
    private static Object invokeSync(final ScriptFunction func, final Object sync, final Object self, final Object... args)
            throws Throwable {
        final Object syncObj = sync == UNDEFINED ? self : sync;
        synchronized (syncObj) {
            return func.invoke(self, args);
        }
    }

    private static MethodHandle findOwnMH_S(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), ScriptFunction.class, name, MH.type(rtype, types));
    }

    private static MethodHandle findOwnMH_V(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findVirtual(MethodHandles.lookup(), ScriptFunction.class, name, MH.type(rtype, types));
    }
}
