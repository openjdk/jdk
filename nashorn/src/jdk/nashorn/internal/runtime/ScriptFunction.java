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

package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.codegen.CompilerConstants.virtualCallNoLookup;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static jdk.nashorn.internal.runtime.linker.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.objects.annotations.SpecializedConstructor;
import jdk.nashorn.internal.objects.annotations.SpecializedFunction;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.runtime.linker.MethodHandleFactory;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;
import jdk.nashorn.internal.runtime.linker.NashornGuardedInvocation;
import jdk.nashorn.internal.runtime.linker.NashornGuards;
import jdk.nashorn.internal.runtime.options.Options;
import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.LinkRequest;

/**
 * Runtime representation of a JavaScript function.
 */
public abstract class ScriptFunction extends ScriptObject {

    /** Method handle for prototype getter for this ScriptFunction */
    public static final MethodHandle G$PROTOTYPE  = findOwnMH("G$prototype",  Object.class, Object.class);

    /** Method handle for prototype setter for this ScriptFunction */
    public static final MethodHandle S$PROTOTYPE  = findOwnMH("S$prototype",  void.class, Object.class, Object.class);

    /** Method handle for length getter for this ScriptFunction */
    public static final MethodHandle G$LENGTH     = findOwnMH("G$length",     int.class, Object.class);

    /** Method handle for name getter for this ScriptFunction */
    public static final MethodHandle G$NAME       = findOwnMH("G$name",       Object.class, Object.class);

    /** Method handle for allocate function for this ScriptFunction */
    public static final MethodHandle ALLOCATE     = findOwnMH("allocate", Object.class);

    private static final MethodHandle NEWFILTER = findOwnMH("newFilter", Object.class, Object.class, Object.class);

    /** method handle to arity setter for this ScriptFunction */
    public static final Call SET_ARITY = virtualCallNoLookup(ScriptFunction.class, "setArity", void.class, int.class);
    /** method handle to scope getter for this ScriptFunction */
    public static final Call GET_SCOPE = virtualCallNoLookup(ScriptFunction.class, "getScope", ScriptObject.class);

    /** Should specialized function and specialized constructors for the builtin be used if available? */
    private static final boolean DISABLE_SPECIALIZATION = Options.getBooleanProperty("nashorn.scriptfunction.specialization.disable");

    /** Name of function or null. */
    private final String name;

    /** Source of function. */
    private final Source source;

    /** Start position and length in source. */
    private final long token;

    /** Reference to code for this method. */
    private final MethodHandle invokeHandle;

    /** Reference to code for this method when called to create "new" object */
    protected MethodHandle constructHandle;

    /** Reference to constructor prototype. */
    protected Object prototype;

    /** Constructor to create a new instance. */
    private MethodHandle allocator;

    /** Map for new instance constructor. */
    private PropertyMap allocatorMap;

    /** The parent scope. */
    private final ScriptObject scope;

    /** Specializations - see @SpecializedFunction */
    private MethodHandle[] invokeSpecializations;

    /** Specializations - see @SpecializedFunction */
    private MethodHandle[] constructSpecializations;

    /** This field is either computed in constructor or set explicitly by calling setArity method. */
    private int arity;

    /**
     * Constructor
     *
     * @param name          function name
     * @param methodHandle  method handle to function (if specializations are present, assumed to be most generic)
     * @param map           property map
     * @param scope         scope
     * @param specs         specialized version of this function - other method handles
     */
    protected ScriptFunction(
            final String name,
            final MethodHandle methodHandle,
            final PropertyMap map,
            final ScriptObject scope,
            final MethodHandle[] specs) {
        this(name, methodHandle, map, scope, null, 0, false, specs);
    }

    /**
     * Constructor
     *
     * @param name          function name
     * @param methodHandle  method handle to function (if specializations are present, assumed to be most generic)
     * @param map           property map
     * @param scope         scope
     * @param source        the source
     * @param token         token
     * @param allocator     method handle to this function's allocator - see JO$ classes
     * @param allocatorMap  property map to be used for all constructors
     * @param needsCallee   does this method use the {@code callee} variable
     * @param specs         specialized version of this function - other method handles
     */
    protected ScriptFunction(
            final String name,
            final MethodHandle methodHandle,
            final PropertyMap map,
            final ScriptObject scope,
            final Source source,
            final long token,
            final MethodHandle allocator,
            final PropertyMap allocatorMap,
            final boolean needsCallee,
            final MethodHandle[] specs) {

        this(name, methodHandle, map, scope, source, token, needsCallee, specs);

        //this is the internal constructor

        this.allocator    = allocator;
        this.allocatorMap = allocatorMap;
    }

    /**
     * Constructor
     *
     * @param name              function name
     * @param methodHandle      method handle to function (if specializations are present, assumed to be most generic)
     * @param map               property map
     * @param scope             scope
     * @param source            the source
     * @param token             token
     * @param needsCallee       does this method use the {@code callee} variable
     * @param specs             specialized version of this function - other method handles
     */
    protected ScriptFunction(
            final String name,
            final MethodHandle methodHandle,
            final PropertyMap map,
            final ScriptObject scope,
            final Source source,
            final long token,
            final boolean needsCallee,
            final MethodHandle[] specs) {

        super(map);

        if (Context.DEBUG) {
            constructorCount++;
        }

        // needCallee => scope != null
        assert !needsCallee || scope != null;
        this.name   = name;
        this.source = source;
        this.token  = token;
        this.scope  = scope;

        final MethodType type       = methodHandle.type();
        final int        paramCount = type.parameterCount();
        final boolean    isVarArg   = type.parameterType(paramCount - 1).isArray();

        final MethodHandle mh = MH.asType(methodHandle, adaptType(type, scope != null, isVarArg));

        this.arity = isVarArg ? -1 : paramCount - 1; //drop the self param for arity

        if (scope != null) {
            if (needsCallee) {
                if (!isVarArg) {
                    this.arity--;
                }
            }
            this.invokeHandle    = mh;
            this.constructHandle = mh;
        } else if (isConstructor(mh)) {
            if (!isVarArg) {
                this.arity--;    // drop the boolean flag for arity
            }
            /*
             * We insert a boolean argument to tell if the method was invoked as
             * constructor or not if the method handle's first argument is boolean.
             */
            this.invokeHandle    = MH.insertArguments(mh, 0, false);
            this.constructHandle = MH.insertArguments(mh, 0, true);

            if (specs != null) {
                this.invokeSpecializations    = new MethodHandle[specs.length];
                this.constructSpecializations = new MethodHandle[specs.length];
                for (int i = 0; i < specs.length; i++) {
                    this.invokeSpecializations[i]    = MH.insertArguments(specs[i], 0, false);
                    this.constructSpecializations[i] = MH.insertArguments(specs[i], 0, true);
                }
            }
        } else {
            this.invokeHandle             = mh;
            this.constructHandle          = mh;
            this.invokeSpecializations    = specs;
            this.constructSpecializations = specs;
        }
    }

    /**
     * Takes a method type, and returns a (potentially different method type) that the method handles used by
     * ScriptFunction must conform to in order to be usable in {@link #invoke(Object, Object...)} and
     * {@link #construct(Object, Object...)}. The returned method type will be sure to return {@code Object}, and will
     * have all its parameters turned into {@code Object} as well, except for the following ones:
     * <ul>
     * <li>an optional first {@code boolean} parameter, used for some functions to distinguish method and constructor
     * invocation,</li>
     * <li>a last parameter of type {@code Object[]} which is used for vararg functions,</li>
     * <li>the second (or, in presence of boolean parameter, third) argument, which is forced to be
     * {@link ScriptFunction}, in case the function receives itself (callee) as an argument</li>
     * @param type the original type
     * @param hasCallee true if the function uses the callee argument
     * @param isVarArg if the function is a vararg
     * @return the new type, conforming to the rules above.
     */
    private static MethodType adaptType(final MethodType type, final boolean hasCallee, final boolean isVarArg) {
        // Generify broadly
        MethodType newType = type.generic().changeReturnType(Object.class);
        if(isVarArg) {
            // Change back to vararg if we over-generified it
            newType = newType.changeParameterType(type.parameterCount() - 1, Object[].class);
        }
        final boolean hasBoolean = type.parameterType(0) == boolean.class;
        if(hasBoolean) {
            // Restore the initial boolean argument
            newType = newType.changeParameterType(0, boolean.class);
        }
        if(hasCallee) {
            // Restore the ScriptFunction argument
            newType = newType.changeParameterType(hasBoolean ? 2 : 1, ScriptFunction.class);
        }
        return newType;
    }

    @Override
    public String getClassName() {
        return "Function";
    }

    /**
     * ECMA 15.3.5.3 [[HasInstance]] (V)
     * Step 3 if "prototype" value is not an Object, throw TypeError
     */
    @Override
    public boolean isInstance(final ScriptObject instance) {
        if (!(prototype instanceof ScriptObject)) {
            typeError("prototype.not.an.object", ScriptRuntime.safeToString(this), ScriptRuntime.safeToString(prototype));
        }

        for (ScriptObject proto = instance.getProto(); proto != null; proto = proto.getProto()) {
            if (proto == prototype) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the arity of this ScriptFunction
     * @return arity
     */
    public final int getArity() {
        return arity;
    }

    /**
     * Set the arity of this ScriptFunction
     * @param arity arity
     */
    public final void setArity(final int arity) {
        this.arity = arity;
    }

    /**
     * Is this a ECMAScript 'use strict' function?
     * @return true if function is in strict mode
     */
    public abstract boolean isStrict();

    /**
     * Is this a ECMAScript built-in function (like parseInt, Array.isArray) ?
     * @return true if built-in
     */
    public abstract boolean isBuiltin();

    /**
     * Is this a non-strict (not built-in) script function?
     * @return true if neither strict nor built-in
     */
    public boolean isNonStrictFunction() {
        return !isStrict() && !isBuiltin();
    }

    /**
     * Execute this script function.
     * @param self  Target object.
     * @param arguments  Call arguments.
     * @return ScriptFunction result.
     * @throws Throwable if there is an exception/error with the invocation or thrown from it
     */
    public Object invoke(final Object self, final Object... arguments) throws Throwable {
        if (Context.DEBUG) {
            invokes++;
        }

        final Object[] args = arguments == null ? ScriptRuntime.EMPTY_ARRAY : arguments;

        if (isVarArg(invokeHandle)) {
            if (hasCalleeParameter()) {
                return invokeHandle.invokeExact(self, this, args);
            }
            return invokeHandle.invokeExact(self, args);
        }

        final int paramCount = invokeHandle.type().parameterCount();
        if (hasCalleeParameter()) {
            switch (paramCount) {
            case 2:
                return invokeHandle.invokeExact(self, this);
            case 3:
                return invokeHandle.invokeExact(self, this, getArg(args, 0));
            case 4:
                return invokeHandle.invokeExact(self, this, getArg(args, 0), getArg(args, 1));
            case 5:
                return invokeHandle.invokeExact(self, this, getArg(args, 0), getArg(args, 1), getArg(args, 2));
            default:
                return invokeHandle.invokeWithArguments(withArguments(self, this, paramCount, args));
            }
        }

        switch (paramCount) {
        case 1:
            return invokeHandle.invokeExact(self);
        case 2:
            return invokeHandle.invokeExact(self, getArg(args, 0));
        case 3:
            return invokeHandle.invokeExact(self, getArg(args, 0), getArg(args, 1));
        case 4:
            return invokeHandle.invokeExact(self, getArg(args, 0), getArg(args, 1), getArg(args, 2));
        default:
            return invokeHandle.invokeWithArguments(withArguments(self, null, paramCount, args));
        }
    }

    private static Object getArg(final Object[] args, final int i) {
        return i < args.length ? args[i] : UNDEFINED;
    }

    /**
     * Construct new object using this constructor.
     * @param self  Target object.
     * @param args  Call arguments.
     * @return ScriptFunction result.
     * @throws Throwable if there is an exception/error with the constructor invocation or thrown from it
     */
    public Object construct(final Object self, final Object... args) throws Throwable {
        if (constructHandle == null) {
            typeError("not.a.constructor", ScriptRuntime.safeToString(this));
        }

        if (isVarArg(constructHandle)) {
            if (hasCalleeParameter()) {
                return constructHandle.invokeExact(self, this, args);
            }
            return constructHandle.invokeExact(self, args);
        }

        final int paramCount = constructHandle.type().parameterCount();
        if (hasCalleeParameter()) {
            switch (paramCount) {
            case 2:
                return constructHandle.invokeExact(self, this);
            case 3:
                return constructHandle.invokeExact(self, this, getArg(args, 0));
            case 4:
                return constructHandle.invokeExact(self, this, getArg(args, 0), getArg(args, 1));
            case 5:
                return constructHandle.invokeExact(self, this, getArg(args, 0), getArg(args, 1), getArg(args, 2));
            default:
                return constructHandle.invokeWithArguments(withArguments(self, this, args));
            }
        }

        switch(paramCount) {
        case 1:
            return constructHandle.invokeExact(self);
        case 2:
            return constructHandle.invokeExact(self, getArg(args, 0));
        case 3:
            return constructHandle.invokeExact(self, getArg(args, 0), getArg(args, 1));
        case 4:
            return constructHandle.invokeExact(self, getArg(args, 0), getArg(args, 1), getArg(args, 2));
        default:
            return constructHandle.invokeWithArguments(withArguments(self, null, args));
        }
    }

    private static Object[] withArguments(final Object self, final ScriptFunction function, final Object... args) {
        return withArguments(self, function, args.length + (function == null ? 1 : 2), args); // + 2 to include self and function
    }

    private static Object[] withArguments(final Object self, final ScriptFunction function, final int paramCount, final Object... args) {
        final Object[] finalArgs = new Object[paramCount];

        finalArgs[0] = self;
        int nextArg = 1;
        if (function != null) {
            finalArgs[nextArg++] = function;
        }

        //don't add more args that there is paramcount in the handle (including self)
        final int maxArgs = Math.min(args.length, paramCount - (function == null ? 1 : 2));
        for (int i = 0; i < maxArgs;) {
            finalArgs[nextArg++] = args[i++];
        }

        //if we have fewer params than paramcount, pad undefined
        while (nextArg < paramCount) {
            finalArgs[nextArg++] = UNDEFINED;
        }

        return finalArgs;
    }

    /**
     * Allocate function. Called from generated {@link ScriptObject} code
     * for allocation as a factory method
     *
     * @return a new instance of the {@link ScriptObject} whose allocator this is
     */
    public Object allocate() {
        if (Context.DEBUG) {
            allocations++;
        }

        if (getConstructHandle() == null) {
            typeError("not.a.constructor", ScriptRuntime.safeToString(this));
        }

        ScriptObject object = null;

        if (allocator != null) {
            try {
                object = (ScriptObject)allocator.invokeExact(allocatorMap);
            } catch (final RuntimeException | Error e) {
                throw e;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }
        }

        if (object != null) {
            if (prototype instanceof ScriptObject) {
                object.setProto((ScriptObject)prototype);
            }

            if (object.getProto() == null) {
                object.setProto(getObjectPrototype());
            }
        }

        return object;
    }

    /**
     * Return Object.prototype - used by "allocate"
     * @return Object.prototype
     */
    protected abstract ScriptObject getObjectPrototype();

    /**
     * Creates a version of this function bound to a specific "self" and other argumentss
     * @param self the self to bind the function to
     * @param args other arguments (beside self) to bind the function to
     * @return the bound function
     */
    public abstract ScriptFunction makeBoundFunction(Object self, Object[] args);

    /**
     * Test if a methodHandle refers to a constructor.
     * @param methodHandle MethodHandle to test.
     * @return True if method is a constructor.
     */
    private static boolean isConstructor(final MethodHandle methodHandle) {
        return methodHandle.type().parameterCount() >= 1 && methodHandle.type().parameterType(0) == boolean.class;
    }

    /**
     * Test if a methodHandle refers to a variable argument method.
     * @param methodHandle MethodHandle to test.
     * @return True if variable arguments.
     */
    public boolean isVarArg(final MethodHandle methodHandle) {
        return hasCalleeParameter()
                ? methodHandle.type().parameterCount() == 3 && methodHandle.type().parameterType(2).isArray()
                : methodHandle.type().parameterCount() == 2 && methodHandle.type().parameterType(1).isArray();
    }

    @Override
    public final String safeToString() {
        return toSource();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append(super.toString())
            .append(" [ ")
            .append(invokeHandle)
            .append(", ")
            .append((name == null || name.isEmpty()) ? "<anonymous>" : name);

        if (source != null) {
            sb.append(" @ ")
               .append(source.getName())
               .append(':')
               .append(source.getLine(Token.descPosition(token)));
        }
        sb.append(" ]");

        return sb.toString();
    }

    /**
     * Get this function as a String containing its source code. If no source code
     * exists in this ScriptFunction, its contents will be displayed as {@code [native code]}
     * @return string representation of this function's source
     */
    public final String toSource() {
        if (source != null && token != 0) {
            return source.getString(Token.descPosition(token), Token.descLength(token));
        }

        return "function " + (name == null ? "" : name) + "() { [native code] }";
    }

    /**
     * Get the prototype object for this function
     * @return prototype
     */
    public final Object getPrototype() {
        return prototype;
    }

    /**
     * Set the prototype object for this function
     * @param prototype new prototype object
     * @return the prototype parameter
     */
    public final Object setPrototype(final Object prototype) {
        this.prototype = prototype;
        return prototype;
    }

    private static int weigh(final MethodType t) {
        int weight = Type.typeFor(t.returnType()).getWeight();
        for (final Class<?> paramType : t.parameterArray()) {
            final int pweight = Type.typeFor(paramType).getWeight();
            weight += pweight;
        }
        return weight;
    }

    private static boolean typeCompatible(final MethodType desc, final MethodType spec) {
        //spec must fit in desc
        final Class<?>[] dparray = desc.parameterArray();
        final Class<?>[] sparray = spec.parameterArray();

        if (dparray.length != sparray.length) {
            return false;
        }

        for (int i = 0; i < dparray.length; i++) {
            final Type dp = Type.typeFor(dparray[i]);
            final Type sp = Type.typeFor(sparray[i]);

            if (dp.isBoolean()) {
                return false; //don't specialize on booleans, we have the "true" vs int 1 ambiguity in resolution
            }

            //specialization arguments must be at least as wide as dp, if not wider
            if (Type.widest(dp, sp) != sp) {
                //e.g. specialization takes double and callsite says "object". reject.
                //but if specialization says double and callsite says "int" or "long" or "double", that's fine
                return false;
            }
        }

        return true; // anything goes for return type, take the convenient one and it will be upcasted thru dynalink magic.
    }

    private MethodHandle candidateWithLowestWeight(final MethodType descType, final MethodHandle initialCandidate, final MethodHandle[] specs) {
        if (DISABLE_SPECIALIZATION || specs == null) {
            return initialCandidate;
        }

        int          minimumWeight = Integer.MAX_VALUE;
        MethodHandle candidate     = initialCandidate;

        for (final MethodHandle spec : specs) {
            final MethodType specType = spec.type();

            if (!typeCompatible(descType, specType)) {
                continue;
            }

            //return type is ok. we want a wider or equal one for our callsite.
            final int specWeight = weigh(specType);
            if (specWeight < minimumWeight) {
                candidate = spec;
                minimumWeight = specWeight;
            }
        }

        if (DISABLE_SPECIALIZATION && candidate != initialCandidate) {
            Context.err("### Specializing builtin " + getName() + " -> " + candidate + "?");
        }

        return candidate;
    }

    /**
     * Return the most appropriate invoke handle if there are specializations
     * @param type most specific method type to look for invocation with
     * @return invoke method handle
     */
    public final MethodHandle getBestSpecializedInvokeHandle(final MethodType type) {
        return candidateWithLowestWeight(type, getInvokeHandle(), invokeSpecializations);
    }

    /**
     * Get the invoke handle - the most generic (and if no specializations are in place, only) invocation
     * method handle for this ScriptFunction
     * @see SpecializedFunction
     * @return invokeHandle
     */
    public final MethodHandle getInvokeHandle() {
        return invokeHandle;
    }

    /**
     * Return the invoke handle bound to a given ScriptObject self reference.
     * If callee parameter is required result is rebound to this.
     *
     * @param self self reference
     * @return bound invoke handle
     */
    public final MethodHandle getBoundInvokeHandle(final ScriptObject self) {
        final MethodHandle bound = MH.bindTo(getInvokeHandle(), self);
        return hasCalleeParameter() ? MH.bindTo(bound, this) : bound;
    }

    private boolean hasCalleeParameter() {
        return scope != null;
    }

    /**
     * Get the construct handle - the most generic (and if no specializations are in place, only) constructor
     * method handle for this ScriptFunction
     * @see SpecializedConstructor
     * @param type type for wanted constructor
     * @return construct handle
     */
    public final MethodHandle getConstructHandle(final MethodType type) {
        return candidateWithLowestWeight(type, getConstructHandle(), constructSpecializations);
    }

    /**
     * Get a method handle to the constructor for this function
     * @return constructor handle
     */
    public final MethodHandle getConstructHandle() {
        return constructHandle;
    }

    /**
     * Set a method handle to the constructor for this function
     * @param constructHandle constructor handle
     */
    public final void setConstructHandle(final MethodHandle constructHandle) {
        this.constructHandle = constructHandle;
        this.constructSpecializations = null;
    }

    /**
     * Get the name for this function
     * @return the name
     */
    public final String getName() {
        return name;
    }

    /**
     * Does this script function need to be compiled. This determined by
     * null checking invokeHandle
     *
     * @return true if this needs compilation
     */
    public final boolean needsCompilation() {
        return invokeHandle == null;
    }

    /**
     * Get token for this function
     * @return token
     */
    public final long getToken() {
        return token;
    }

    /**
     * Get the scope for this function
     * @return the scope
     */
    public final ScriptObject getScope() {
        return scope;
    }

    /**
     * Prototype getter for this ScriptFunction - follows the naming convention
     * used by Nasgen and the code generator
     *
     * @param self  self reference
     * @return self's prototype
     */
    public static Object G$prototype(final Object self) {
        return (self instanceof ScriptFunction) ?
            ((ScriptFunction)self).getPrototype() :
            UNDEFINED;
    }

    /**
     * Prototype setter for this ScriptFunction - follows the naming convention
     * used by Nasgen and the code generator
     *
     * @param self  self reference
     * @param prototype prototype to set
     */
    public static void S$prototype(final Object self, final Object prototype) {
        if (self instanceof ScriptFunction) {
            ((ScriptFunction)self).setPrototype(prototype);
        }
    }

    /**
     * Length getter - ECMA 15.3.3.2: Function.length
     * @param self self reference
     * @return length
     */
    public static int G$length(final Object self) {
        if (self instanceof ScriptFunction) {
            return ((ScriptFunction)self).getArity();
        }

        return 0;
    }

    /**
     * Name getter - ECMA Function.name
     * @param self self refence
     * @return the name, or undefined if none
     */
    public static Object G$name(final Object self) {
        if (self instanceof ScriptFunction) {
            return ((ScriptFunction)self).getName();
        }

        return UNDEFINED;
    }

    /**
     * Get the prototype for this ScriptFunction
     * @param constructor constructor
     * @return prototype, or null if given constructor is not a ScriptFunction
     */
    public static ScriptObject getPrototype(final Object constructor) {
        if (constructor instanceof ScriptFunction) {
            final Object proto = ((ScriptFunction)constructor).getPrototype();
            if (proto instanceof ScriptObject) {
                return (ScriptObject)proto;
            }
        }

        return null;
    }

    // These counters are updated only in debug mode.
    private static int constructorCount;
    private static int invokes;
    private static int allocations;

    /**
     * @return the constructorCount
     */
    public static int getConstructorCount() {
        return constructorCount;
    }

    /**
     * @return the invokes
     */
    public static int getInvokes() {
        return invokes;
    }

    /**
     * @return the allocations
     */
    public static int getAllocations() {
        return allocations;
    }

    @Override
    protected GuardedInvocation findNewMethod(final CallSiteDescriptor desc) {
        final MethodType type = desc.getMethodType();
        MethodHandle constructor = getConstructHandle(type);

        if (constructor == null) {
            typeError("not.a.constructor", ScriptRuntime.safeToString(this));
            return null;
        }

        final MethodType ctorType = constructor.type();

        // guard against primitive constructor return types
        constructor = MH.asType(constructor, constructor.type().changeReturnType(Object.class));

        // apply new filter
        final Class<?>[] ctorArgs = ctorType.dropParameterTypes(0, 1).parameterArray(); // drop self
        MethodHandle handle = MH.foldArguments(MH.dropArguments(NEWFILTER, 2, ctorArgs), constructor);

        if (hasCalleeParameter()) {
            final MethodHandle allocate = MH.bindTo(MethodHandles.exactInvoker(ALLOCATE.type()), ScriptFunction.ALLOCATE);
            handle = MH.foldArguments(handle, allocate);
            handle = MH.asType(handle, handle.type().changeParameterType(0, Object.class)); // ScriptFunction => Object
        } else {
            final MethodHandle allocate = MH.dropArguments(MH.bindTo(ScriptFunction.ALLOCATE, this), 0, Object.class);
            handle = MH.filterArguments(handle, 0, allocate);
        }

        final MethodHandle filterIn = MH.asType(pairArguments(handle, type), type);
        return new GuardedInvocation(filterIn, null, NashornGuards.getFunctionGuard(this));
    }

    @SuppressWarnings("unused")
    private static Object newFilter(final Object result, final Object allocation) {
        return (result instanceof ScriptObject || !JSType.isPrimitive(result))? result : allocation;
    }

    /**
     * dyn:call call site signature: (callee, thiz, [args...])
     * generated method signature:   (thiz, callee, [args...])
     *
     * cases:
     * (a) method has callee parameter
     *   (1) for local/scope calls, we just bind thiz and drop the second argument.
     *   (2) for normal this-calls, we have to swap thiz and callee to get matching signatures.
     * (b) method doesn't have callee parameter (builtin functions)
     *   (3) for local/scope calls, bind thiz and drop both callee and thiz.
     *   (4) for normal this-calls, drop callee.
     */
    @Override
    protected GuardedInvocation findCallMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final MethodType type = desc.getMethodType();

        if (request.isCallSiteUnstable()) {
            // (this, callee, args...) => (this, callee, args[])
            final MethodHandle collector = MH.asCollector(ScriptRuntime.APPLY.methodHandle(), Object[].class,
                    type.parameterCount() - 2);

            return new GuardedInvocation(addPrimitiveWrap(collector, desc, request),
                    desc.getMethodType().parameterType(0) == ScriptFunction.class ? null : NashornGuards.getScriptFunctionGuard());
        }

        MethodHandle boundHandle;
        if (hasCalleeParameter()) {
            final MethodHandle callHandle = getBestSpecializedInvokeHandle(type);

            if(NashornCallSiteDescriptor.isScope(desc)) {
                // (this, callee, args...) => (callee, args...) => (callee, [this], args...)
                boundHandle = MH.bindTo(callHandle, isNonStrictFunction() ? Context.getGlobalTrusted() : ScriptRuntime.UNDEFINED);
                boundHandle = MH.dropArguments(boundHandle, 1, Object.class);
            } else {
                // (this, callee, args...) permute => (callee, this, args...) which is what we get in
                final MethodType oldType = callHandle.type();
                final int[] reorder = new int[oldType.parameterCount()];
                for (int i = 2; i < reorder.length; i++) {
                    reorder[i] = i;
                }
                reorder[0] = 1;
                assert reorder[1] == 0;
                final MethodType newType = oldType.changeParameterType(0, oldType.parameterType(1)).changeParameterType(1, oldType.parameterType(0));
                boundHandle = MethodHandles.permuteArguments(callHandle, newType, reorder);
                // thiz argument may be a JS primitive needing a wrapper
                boundHandle = addPrimitiveWrap(boundHandle, desc, request);
            }
        } else {
            final MethodHandle callHandle = getBestSpecializedInvokeHandle(type.dropParameterTypes(0, 1));

            if(NashornCallSiteDescriptor.isScope(desc)) {
                boundHandle = MH.bindTo(callHandle, isNonStrictFunction()? Context.getGlobalTrusted() : ScriptRuntime.UNDEFINED);
                boundHandle = MH.dropArguments(boundHandle, 0, Object.class, Object.class);
            } else {
                boundHandle = MH.dropArguments(callHandle, 0, Object.class);
            }
        }

        boundHandle = pairArguments(boundHandle, type);
        return new NashornGuardedInvocation(boundHandle, null, NashornGuards.getFunctionGuard(this), isNonStrictFunction());
   }

    /**
     * Used for noSuchMethod/noSuchProperty and JSAdapter hooks.
     *
     * These don't want a callee parameter, so bind that. Name binding is optional.
     */
    MethodHandle getCallMethodHandle(final MethodType type, final String bindName) {
        MethodHandle methodHandle = getBestSpecializedInvokeHandle(type);

        if (bindName != null) {
            if (hasCalleeParameter()) {
                methodHandle = MH.insertArguments(methodHandle, 1, this, bindName);
            } else {
                methodHandle = MH.insertArguments(methodHandle, 1, bindName);
            }
        } else {
            if (hasCalleeParameter()) {
                methodHandle = MH.insertArguments(methodHandle, 1, this);
            }
        }

        return pairArguments(methodHandle, type);
    }

    private MethodHandle addPrimitiveWrap(final MethodHandle mh, final CallSiteDescriptor desc, final LinkRequest request) {
        // Check whether thiz is a JS primitive type and needs an object wrapper for non-strict function
        if (!NashornCallSiteDescriptor.isScope(desc) && isNonStrictFunction()) {
            Object self = request.getArguments()[1];
            if (isPrimitiveThis(self)) {
                MethodHandle wrapFilter = ((GlobalObject) Context.getGlobalTrusted()).getWrapFilter(self);
                return MH.filterArguments(mh, 1, MH.asType(wrapFilter, wrapFilter.type().changeReturnType(Object.class)));
            }
        }
        return mh;
    }

    private static boolean isPrimitiveThis(Object obj) {
        return obj instanceof String || obj instanceof ConsString ||
               obj instanceof Number || obj instanceof Boolean;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        final Class<?>   own = ScriptFunction.class;
        final MethodType mt  = MH.type(rtype, types);
        try {
            return MH.findStatic(MethodHandles.lookup(), own, name, mt);
        } catch (final MethodHandleFactory.LookupException e) {
            return MH.findVirtual(MethodHandles.lookup(), own, name, mt);
        }
    }
}

