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

import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import jdk.nashorn.internal.runtime.linker.LinkerCallSite;


/**
 * A container for data needed to instantiate a specific {@link ScriptFunction} at runtime.
 * Instances of this class are created during codegen and stored in script classes'
 * constants array to reduce function instantiation overhead during runtime.
 */
public abstract class ScriptFunctionData implements Serializable {
    static final int MAX_ARITY = LinkerCallSite.ARGLIMIT;
    static {
        // Assert it fits in a byte, as that's what we store it in. It's just a size optimization though, so if needed
        // "byte arity" field can be widened.
        assert MAX_ARITY < 256;
    }

    /** Name of the function or "" for anonymous functions */
    protected final String name;

    /**
     * A list of code versions of a function sorted in ascending order of generic descriptors.
     */
    protected transient LinkedList<CompiledFunction> code = new LinkedList<>();

    /** Function flags */
    protected int flags;

    // Parameter arity of the function, corresponding to "f.length". E.g. "function f(a, b, c) { ... }" arity is 3, and
    // some built-in ECMAScript functions have their arity declared by the specification. Note that regardless of this
    // value, the function might still be capable of receiving variable number of arguments, see isVariableArity.
    private int arity;

    /**
     * A pair of method handles used for generic invoker and constructor. Field is volatile as it can be initialized by
     * multiple threads concurrently, but we still tolerate a race condition in it as all values stored into it are
     * idempotent.
     */
    private volatile transient GenericInvokers genericInvokers;

    private static final MethodHandle BIND_VAR_ARGS = findOwnMH("bindVarArgs", Object[].class, Object[].class, Object[].class);

    /** Is this a strict mode function? */
    public static final int IS_STRICT            = 1 << 0;
    /** Is this a built-in function? */
    public static final int IS_BUILTIN           = 1 << 1;
    /** Is this a constructor function? */
    public static final int IS_CONSTRUCTOR       = 1 << 2;
    /** Does this function expect a callee argument? */
    public static final int NEEDS_CALLEE         = 1 << 3;
    /** Does this function make use of the this-object argument? */
    public static final int USES_THIS            = 1 << 4;
    /** Is this a variable arity function? */
    public static final int IS_VARIABLE_ARITY    = 1 << 5;
    /** Is this a object literal property getter or setter? */
    public static final int IS_PROPERTY_ACCESSOR = 1 << 6;

    /** Flag for strict or built-in functions */
    public static final int IS_STRICT_OR_BUILTIN = IS_STRICT | IS_BUILTIN;
    /** Flag for built-in constructors */
    public static final int IS_BUILTIN_CONSTRUCTOR = IS_BUILTIN | IS_CONSTRUCTOR;

    private static final long serialVersionUID = 4252901245508769114L;

    /**
     * Constructor
     *
     * @param name  script function name
     * @param arity arity
     * @param flags the function flags
     */
    ScriptFunctionData(final String name, final int arity, final int flags) {
        this.name  = name;
        this.flags = flags;
        setArity(arity);
    }

    final int getArity() {
        return arity;
    }

    String getDocumentation() {
        return toSource();
    }

    String getDocumentationKey() {
        return null;
    }

    final boolean isVariableArity() {
        return (flags & IS_VARIABLE_ARITY) != 0;
    }

    final boolean isPropertyAccessor() {
        return (flags & IS_PROPERTY_ACCESSOR) != 0;
    }

    /**
     * Used from e.g. Native*$Constructors as an explicit call. TODO - make arity immutable and final
     * @param arity new arity
     */
    void setArity(final int arity) {
        if(arity < 0 || arity > MAX_ARITY) {
            throw new IllegalArgumentException(String.valueOf(arity));
        }
        this.arity = arity;
    }

    /**
     * Used from nasgen generated code.
     *
     * @param doc documentation for this function
     */
    void setDocumentationKey(final String docKey) {
    }


    CompiledFunction bind(final CompiledFunction originalInv, final ScriptFunction fn, final Object self, final Object[] args) {
        final MethodHandle boundInvoker = bindInvokeHandle(originalInv.createComposableInvoker(), fn, self, args);

        if (isConstructor()) {
            return new CompiledFunction(boundInvoker, bindConstructHandle(originalInv.createComposableConstructor(), fn, args), null);
        }

        return new CompiledFunction(boundInvoker);
    }

    /**
     * Is this a ScriptFunction generated with strict semantics?
     * @return true if strict, false otherwise
     */
    public final boolean isStrict() {
        return (flags & IS_STRICT) != 0;
    }

    /**
     * Return the complete internal function name for this
     * data, not anonymous or similar. May be identical
     * @return internal function name
     */
    protected String getFunctionName() {
        return getName();
    }

    final boolean isBuiltin() {
        return (flags & IS_BUILTIN) != 0;
    }

    final boolean isConstructor() {
        return (flags & IS_CONSTRUCTOR) != 0;
    }

    abstract boolean needsCallee();

    /**
     * Returns true if this is a non-strict, non-built-in function that requires non-primitive this argument
     * according to ECMA 10.4.3.
     * @return true if this argument must be an object
     */
    final boolean needsWrappedThis() {
        return (flags & USES_THIS) != 0 && (flags & IS_STRICT_OR_BUILTIN) == 0;
    }

    String toSource() {
        return "function " + (name == null ? "" : name) + "() { [native code] }";
    }

    String getName() {
        return name;
    }

    /**
     * Get this function as a String containing its source code. If no source code
     * exists in this ScriptFunction, its contents will be displayed as {@code [native code]}
     *
     * @return string representation of this function
     */
    @Override
    public String toString() {
        return name.isEmpty() ? "<anonymous>" : name;
    }

    /**
     * Verbose description of data
     * @return verbose description
     */
    public String toStringVerbose() {
        final StringBuilder sb = new StringBuilder();

        sb.append("name='").
                append(name.isEmpty() ? "<anonymous>" : name).
                append("' ").
                append(code.size()).
                append(" invokers=").
                append(code);

        return sb.toString();
    }

    /**
     * Pick the best invoker, i.e. the one version of this method with as narrow and specific
     * types as possible. If the call site arguments are objects, but boxed primitives we can
     * also try to get a primitive version of the method and do an unboxing filter, but then
     * we need to insert a guard that checks the argument is really always a boxed primitive
     * and not suddenly a "real" object
     *
     * @param callSiteType callsite type
     * @return compiled function object representing the best invoker.
     */
    final CompiledFunction getBestInvoker(final MethodType callSiteType, final ScriptObject runtimeScope) {
        return getBestInvoker(callSiteType, runtimeScope, CompiledFunction.NO_FUNCTIONS);
    }

    final CompiledFunction getBestInvoker(final MethodType callSiteType, final ScriptObject runtimeScope, final Collection<CompiledFunction> forbidden) {
        final CompiledFunction cf = getBest(callSiteType, runtimeScope, forbidden);
        assert cf != null;
        return cf;
    }

    final CompiledFunction getBestConstructor(final MethodType callSiteType, final ScriptObject runtimeScope, final Collection<CompiledFunction> forbidden) {
        if (!isConstructor()) {
            throw typeError("not.a.constructor", toSource());
        }
        // Constructor call sites don't have a "this", but getBest is meant to operate on "callee, this, ..." style
        final CompiledFunction cf = getBest(callSiteType.insertParameterTypes(1, Object.class), runtimeScope, forbidden);
        return cf;
    }

    /**
     * If we can have lazy code generation, this is a hook to ensure that the code has been compiled.
     * This does not guarantee the code been installed in this {@code ScriptFunctionData} instance
     */
    protected void ensureCompiled() {
        //empty
    }

    /**
     * Return a generic Object/Object invoker for this method. It will ensure code
     * is generated, get the most generic of all versions of this function and adapt it
     * to Objects.
     *
     * @param runtimeScope the runtime scope. It can be used to evaluate types of scoped variables to guide the
     * optimistic compilation, should the call to this method trigger code compilation. Can be null if current runtime
     * scope is not known, but that might cause compilation of code that will need more deoptimization passes.
     * @return generic invoker of this script function
     */
    final MethodHandle getGenericInvoker(final ScriptObject runtimeScope) {
        // This method has race conditions both on genericsInvoker and genericsInvoker.invoker, but even if invoked
        // concurrently, they'll create idempotent results, so it doesn't matter. We could alternatively implement this
        // using java.util.concurrent.AtomicReferenceFieldUpdater, but it's hardly worth it.
        final GenericInvokers lgenericInvokers = ensureGenericInvokers();
        MethodHandle invoker = lgenericInvokers.invoker;
        if(invoker == null) {
            lgenericInvokers.invoker = invoker = createGenericInvoker(runtimeScope);
        }
        return invoker;
    }

    private MethodHandle createGenericInvoker(final ScriptObject runtimeScope) {
        return makeGenericMethod(getGeneric(runtimeScope).createComposableInvoker());
    }

    final MethodHandle getGenericConstructor(final ScriptObject runtimeScope) {
        // This method has race conditions both on genericsInvoker and genericsInvoker.constructor, but even if invoked
        // concurrently, they'll create idempotent results, so it doesn't matter. We could alternatively implement this
        // using java.util.concurrent.AtomicReferenceFieldUpdater, but it's hardly worth it.
        final GenericInvokers lgenericInvokers = ensureGenericInvokers();
        MethodHandle constructor = lgenericInvokers.constructor;
        if(constructor == null) {
            lgenericInvokers.constructor = constructor = createGenericConstructor(runtimeScope);
        }
        return constructor;
    }

    private MethodHandle createGenericConstructor(final ScriptObject runtimeScope) {
        return makeGenericMethod(getGeneric(runtimeScope).createComposableConstructor());
    }

    private GenericInvokers ensureGenericInvokers() {
        GenericInvokers lgenericInvokers = genericInvokers;
        if(lgenericInvokers == null) {
            genericInvokers = lgenericInvokers = new GenericInvokers();
        }
        return lgenericInvokers;
    }

    private static MethodType widen(final MethodType cftype) {
        final Class<?>[] paramTypes = new Class<?>[cftype.parameterCount()];
        for (int i = 0; i < cftype.parameterCount(); i++) {
            paramTypes[i] = cftype.parameterType(i).isPrimitive() ? cftype.parameterType(i) : Object.class;
        }
        return MH.type(cftype.returnType(), paramTypes);
    }

    /**
     * Used to find an apply to call version that fits this callsite.
     * We cannot just, as in the normal matcher case, return e.g. (Object, Object, int)
     * for (Object, Object, int, int, int) or we will destroy the semantics and get
     * a function that, when padded with undefined values, behaves differently
     * @param type actual call site type
     * @return apply to call that perfectly fits this callsite or null if none found
     */
    CompiledFunction lookupExactApplyToCall(final MethodType type) {
        for (final CompiledFunction cf : code) {
            if (!cf.isApplyToCall()) {
                continue;
            }

            final MethodType cftype = cf.type();
            if (cftype.parameterCount() != type.parameterCount()) {
                continue;
            }

            if (widen(cftype).equals(widen(type))) {
                return cf;
            }
        }

        return null;
    }

    CompiledFunction pickFunction(final MethodType callSiteType, final boolean canPickVarArg) {
        for (final CompiledFunction candidate : code) {
            if (candidate.matchesCallSite(callSiteType, canPickVarArg)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Returns the best function for the specified call site type.
     * @param callSiteType The call site type. Call site types are expected to have the form
     * {@code (callee, this[, args...])}.
     * @param runtimeScope the runtime scope. It can be used to evaluate types of scoped variables to guide the
     * optimistic compilation, should the call to this method trigger code compilation. Can be null if current runtime
     * scope is not known, but that might cause compilation of code that will need more deoptimization passes.
     * @param linkLogicOkay is a CompiledFunction with a LinkLogic acceptable?
     * @return the best function for the specified call site type.
     */
    abstract CompiledFunction getBest(final MethodType callSiteType, final ScriptObject runtimeScope, final Collection<CompiledFunction> forbidden, final boolean linkLogicOkay);

    /**
     * Returns the best function for the specified call site type.
     * @param callSiteType The call site type. Call site types are expected to have the form
     * {@code (callee, this[, args...])}.
     * @param runtimeScope the runtime scope. It can be used to evaluate types of scoped variables to guide the
     * optimistic compilation, should the call to this method trigger code compilation. Can be null if current runtime
     * scope is not known, but that might cause compilation of code that will need more deoptimization passes.
     * @return the best function for the specified call site type.
     */
    final CompiledFunction getBest(final MethodType callSiteType, final ScriptObject runtimeScope, final Collection<CompiledFunction> forbidden) {
        return getBest(callSiteType, runtimeScope, forbidden, true);
    }

    boolean isValidCallSite(final MethodType callSiteType) {
        return callSiteType.parameterCount() >= 2  && // Must have at least (callee, this)
               callSiteType.parameterType(0).isAssignableFrom(ScriptFunction.class); // Callee must be assignable from script function
    }

    CompiledFunction getGeneric(final ScriptObject runtimeScope) {
        return getBest(getGenericType(), runtimeScope, CompiledFunction.NO_FUNCTIONS, false);
    }

    /**
     * Get a method type for a generic invoker.
     * @return the method type for the generic invoker
     */
    abstract MethodType getGenericType();

    /**
     * Allocates an object using this function's allocator.
     *
     * @param map the property map for the allocated object.
     * @return the object allocated using this function's allocator, or null if the function doesn't have an allocator.
     */
    ScriptObject allocate(final PropertyMap map) {
        return null;
    }

    /**
     * Get the property map to use for objects allocated by this function.
     *
     * @param prototype the prototype of the allocated object
     * @return the property map for allocated objects.
     */
    PropertyMap getAllocatorMap(final ScriptObject prototype) {
        return null;
    }

    /**
     * This method is used to create the immutable portion of a bound function.
     * See {@link ScriptFunction#createBound(Object, Object[])}
     *
     * @param fn the original function being bound
     * @param self this reference to bind. Can be null.
     * @param args additional arguments to bind. Can be null.
     */
    ScriptFunctionData makeBoundFunctionData(final ScriptFunction fn, final Object self, final Object[] args) {
        final Object[] allArgs = args == null ? ScriptRuntime.EMPTY_ARRAY : args;
        final int length = args == null ? 0 : args.length;
        // Clear the callee and this flags
        final int boundFlags = flags & ~NEEDS_CALLEE & ~USES_THIS;

        final List<CompiledFunction> boundList = new LinkedList<>();
        final ScriptObject runtimeScope = fn.getScope();
        final CompiledFunction bindTarget = new CompiledFunction(getGenericInvoker(runtimeScope), getGenericConstructor(runtimeScope), null);
        boundList.add(bind(bindTarget, fn, self, allArgs));

        return new FinalScriptFunctionData(name, Math.max(0, getArity() - length), boundList, boundFlags);
    }

    /**
     * Convert this argument for non-strict functions according to ES 10.4.3
     *
     * @param thiz the this argument
     *
     * @return the converted this object
     */
    private Object convertThisObject(final Object thiz) {
        return needsWrappedThis() ? wrapThis(thiz) : thiz;
    }

    static Object wrapThis(final Object thiz) {
        if (!(thiz instanceof ScriptObject)) {
            if (JSType.nullOrUndefined(thiz)) {
                return Context.getGlobal();
            }

            if (isPrimitiveThis(thiz)) {
                return Context.getGlobal().wrapAsObject(thiz);
            }
        }

        return thiz;
    }

    static boolean isPrimitiveThis(final Object obj) {
        return JSType.isString(obj) || obj instanceof Number || obj instanceof Boolean;
    }

    /**
     * Creates an invoker method handle for a bound function.
     *
     * @param targetFn the function being bound
     * @param originalInvoker an original invoker method handle for the function. This can be its generic invoker or
     * any of its specializations.
     * @param self the "this" value being bound
     * @param args additional arguments being bound
     *
     * @return a bound invoker method handle that will bind the self value and the specified arguments. The resulting
     * invoker never needs a callee; if the original invoker needed it, it will be bound to {@code fn}. The resulting
     * invoker still takes an initial {@code this} parameter, but it is always dropped and the bound {@code self} passed
     * to the original invoker on invocation.
     */
    private MethodHandle bindInvokeHandle(final MethodHandle originalInvoker, final ScriptFunction targetFn, final Object self, final Object[] args) {
        // Is the target already bound? If it is, we won't bother binding either callee or self as they're already bound
        // in the target and will be ignored anyway.
        final boolean isTargetBound = targetFn.isBoundFunction();

        final boolean needsCallee = needsCallee(originalInvoker);
        assert needsCallee == needsCallee() : "callee contract violation 2";
        assert !(isTargetBound && needsCallee); // already bound functions don't need a callee

        final Object boundSelf = isTargetBound ? null : convertThisObject(self);
        final MethodHandle boundInvoker;

        if (isVarArg(originalInvoker)) {
            // First, bind callee and this without arguments
            final MethodHandle noArgBoundInvoker;

            if (isTargetBound) {
                // Don't bind either callee or this
                noArgBoundInvoker = originalInvoker;
            } else if (needsCallee) {
                // Bind callee and this
                noArgBoundInvoker = MH.insertArguments(originalInvoker, 0, targetFn, boundSelf);
            } else {
                // Only bind this
                noArgBoundInvoker = MH.bindTo(originalInvoker, boundSelf);
            }
            // Now bind arguments
            if (args.length > 0) {
                boundInvoker = varArgBinder(noArgBoundInvoker, args);
            } else {
                boundInvoker = noArgBoundInvoker;
            }
        } else {
            // If target is already bound, insert additional bound arguments after "this" argument, at position 1.
            final int argInsertPos = isTargetBound ? 1 : 0;
            final Object[] boundArgs = new Object[Math.min(originalInvoker.type().parameterCount() - argInsertPos, args.length + (isTargetBound ? 0 : needsCallee  ? 2 : 1))];
            int next = 0;
            if (!isTargetBound) {
                if (needsCallee) {
                    boundArgs[next++] = targetFn;
                }
                boundArgs[next++] = boundSelf;
            }
            // If more bound args were specified than the function can take, we'll just drop those.
            System.arraycopy(args, 0, boundArgs, next, boundArgs.length - next);
            // If target is already bound, insert additional bound arguments after "this" argument, at position 1;
            // "this" will get dropped anyway by the target invoker. We previously asserted that already bound functions
            // don't take a callee parameter, so we can know that the signature is (this[, args...]) therefore args
            // start at position 1. If the function is not bound, we start inserting arguments at position 0.
            boundInvoker = MH.insertArguments(originalInvoker, argInsertPos, boundArgs);
        }

        if (isTargetBound) {
            return boundInvoker;
        }

        // If the target is not already bound, add a dropArguments that'll throw away the passed this
        return MH.dropArguments(boundInvoker, 0, Object.class);
    }

    /**
     * Creates a constructor method handle for a bound function using the passed constructor handle.
     *
     * @param originalConstructor the constructor handle to bind. It must be a composed constructor.
     * @param fn the function being bound
     * @param args arguments being bound
     *
     * @return a bound constructor method handle that will bind the specified arguments. The resulting constructor never
     * needs a callee; if the original constructor needed it, it will be bound to {@code fn}. The resulting constructor
     * still takes an initial {@code this} parameter and passes it to the underlying original constructor. Finally, if
     * this script function data object has no constructor handle, null is returned.
     */
    private static MethodHandle bindConstructHandle(final MethodHandle originalConstructor, final ScriptFunction fn, final Object[] args) {
        assert originalConstructor != null;

        // If target function is already bound, don't bother binding the callee.
        final MethodHandle calleeBoundConstructor = fn.isBoundFunction() ? originalConstructor :
            MH.dropArguments(MH.bindTo(originalConstructor, fn), 0, ScriptFunction.class);

        if (args.length == 0) {
            return calleeBoundConstructor;
        }

        if (isVarArg(calleeBoundConstructor)) {
            return varArgBinder(calleeBoundConstructor, args);
        }

        final Object[] boundArgs;

        final int maxArgCount = calleeBoundConstructor.type().parameterCount() - 1;
        if (args.length <= maxArgCount) {
            boundArgs = args;
        } else {
            boundArgs = new Object[maxArgCount];
            System.arraycopy(args, 0, boundArgs, 0, maxArgCount);
        }

        return MH.insertArguments(calleeBoundConstructor, 1, boundArgs);
    }

    /**
     * Takes a method handle, and returns a potentially different method handle that can be used in
     * {@code ScriptFunction#invoke(Object, Object...)} or {code ScriptFunction#construct(Object, Object...)}.
     * The returned method handle will be sure to return {@code Object}, and will have all its parameters turned into
     * {@code Object} as well, except for the following ones:
     * <ul>
     *   <li>a last parameter of type {@code Object[]} which is used for vararg functions,</li>
     *   <li>the first argument, which is forced to be {@link ScriptFunction}, in case the function receives itself
     *   (callee) as an argument.</li>
     * </ul>
     *
     * @param mh the original method handle
     *
     * @return the new handle, conforming to the rules above.
     */
    private static MethodHandle makeGenericMethod(final MethodHandle mh) {
        final MethodType type = mh.type();
        final MethodType newType = makeGenericType(type);
        return type.equals(newType) ? mh : mh.asType(newType);
    }

    private static MethodType makeGenericType(final MethodType type) {
        MethodType newType = type.generic();
        if (isVarArg(type)) {
            newType = newType.changeParameterType(type.parameterCount() - 1, Object[].class);
        }
        if (needsCallee(type)) {
            newType = newType.changeParameterType(0, ScriptFunction.class);
        }
        return newType;
    }

    /**
     * Execute this script function.
     *
     * @param self  Target object.
     * @param arguments  Call arguments.
     * @return ScriptFunction result.
     *
     * @throws Throwable if there is an exception/error with the invocation or thrown from it
     */
    Object invoke(final ScriptFunction fn, final Object self, final Object... arguments) throws Throwable {
        final MethodHandle mh      = getGenericInvoker(fn.getScope());
        final Object       selfObj = convertThisObject(self);
        final Object[]     args    = arguments == null ? ScriptRuntime.EMPTY_ARRAY : arguments;

        DebuggerSupport.notifyInvoke(mh);

        if (isVarArg(mh)) {
            if (needsCallee(mh)) {
                return mh.invokeExact(fn, selfObj, args);
            }
            return mh.invokeExact(selfObj, args);
        }

        final int paramCount = mh.type().parameterCount();
        if (needsCallee(mh)) {
            switch (paramCount) {
            case 2:
                return mh.invokeExact(fn, selfObj);
            case 3:
                return mh.invokeExact(fn, selfObj, getArg(args, 0));
            case 4:
                return mh.invokeExact(fn, selfObj, getArg(args, 0), getArg(args, 1));
            case 5:
                return mh.invokeExact(fn, selfObj, getArg(args, 0), getArg(args, 1), getArg(args, 2));
            case 6:
                return mh.invokeExact(fn, selfObj, getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3));
            case 7:
                return mh.invokeExact(fn, selfObj, getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3), getArg(args, 4));
            case 8:
                return mh.invokeExact(fn, selfObj, getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3), getArg(args, 4), getArg(args, 5));
            default:
                return mh.invokeWithArguments(withArguments(fn, selfObj, paramCount, args));
            }
        }

        switch (paramCount) {
        case 1:
            return mh.invokeExact(selfObj);
        case 2:
            return mh.invokeExact(selfObj, getArg(args, 0));
        case 3:
            return mh.invokeExact(selfObj, getArg(args, 0), getArg(args, 1));
        case 4:
            return mh.invokeExact(selfObj, getArg(args, 0), getArg(args, 1), getArg(args, 2));
        case 5:
            return mh.invokeExact(selfObj, getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3));
        case 6:
            return mh.invokeExact(selfObj, getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3), getArg(args, 4));
        case 7:
            return mh.invokeExact(selfObj, getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3), getArg(args, 4), getArg(args, 5));
        default:
            return mh.invokeWithArguments(withArguments(null, selfObj, paramCount, args));
        }
    }

    Object construct(final ScriptFunction fn, final Object... arguments) throws Throwable {
        final MethodHandle mh   = getGenericConstructor(fn.getScope());
        final Object[]     args = arguments == null ? ScriptRuntime.EMPTY_ARRAY : arguments;

        DebuggerSupport.notifyInvoke(mh);

        if (isVarArg(mh)) {
            if (needsCallee(mh)) {
                return mh.invokeExact(fn, args);
            }
            return mh.invokeExact(args);
        }

        final int paramCount = mh.type().parameterCount();
        if (needsCallee(mh)) {
            switch (paramCount) {
            case 1:
                return mh.invokeExact(fn);
            case 2:
                return mh.invokeExact(fn, getArg(args, 0));
            case 3:
                return mh.invokeExact(fn, getArg(args, 0), getArg(args, 1));
            case 4:
                return mh.invokeExact(fn, getArg(args, 0), getArg(args, 1), getArg(args, 2));
            case 5:
                return mh.invokeExact(fn, getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3));
            case 6:
                return mh.invokeExact(fn, getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3), getArg(args, 4));
            case 7:
                return mh.invokeExact(fn, getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3), getArg(args, 4), getArg(args, 5));
            default:
                return mh.invokeWithArguments(withArguments(fn, paramCount, args));
            }
        }

        switch (paramCount) {
        case 0:
            return mh.invokeExact();
        case 1:
            return mh.invokeExact(getArg(args, 0));
        case 2:
            return mh.invokeExact(getArg(args, 0), getArg(args, 1));
        case 3:
            return mh.invokeExact(getArg(args, 0), getArg(args, 1), getArg(args, 2));
        case 4:
            return mh.invokeExact(getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3));
        case 5:
            return mh.invokeExact(getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3), getArg(args, 4));
        case 6:
            return mh.invokeExact(getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3), getArg(args, 4), getArg(args, 5));
        default:
            return mh.invokeWithArguments(withArguments(null, paramCount, args));
        }
    }

    private static Object getArg(final Object[] args, final int i) {
        return i < args.length ? args[i] : UNDEFINED;
    }

    private static Object[] withArguments(final ScriptFunction fn, final int argCount, final Object[] args) {
        final Object[] finalArgs = new Object[argCount];

        int nextArg = 0;
        if (fn != null) {
            //needs callee
            finalArgs[nextArg++] = fn;
        }

        // Don't add more args that there is argCount in the handle (including self and callee).
        for (int i = 0; i < args.length && nextArg < argCount;) {
            finalArgs[nextArg++] = args[i++];
        }

        // If we have fewer args than argCount, pad with undefined.
        while (nextArg < argCount) {
            finalArgs[nextArg++] = UNDEFINED;
        }

        return finalArgs;
    }

    private static Object[] withArguments(final ScriptFunction fn, final Object self, final int argCount, final Object[] args) {
        final Object[] finalArgs = new Object[argCount];

        int nextArg = 0;
        if (fn != null) {
            //needs callee
            finalArgs[nextArg++] = fn;
        }
        finalArgs[nextArg++] = self;

        // Don't add more args that there is argCount in the handle (including self and callee).
        for (int i = 0; i < args.length && nextArg < argCount;) {
            finalArgs[nextArg++] = args[i++];
        }

        // If we have fewer args than argCount, pad with undefined.
        while (nextArg < argCount) {
            finalArgs[nextArg++] = UNDEFINED;
        }

        return finalArgs;
    }
    /**
     * Takes a variable-arity method and binds a variable number of arguments in it. The returned method will filter the
     * vararg array and pass a different array that prepends the bound arguments in front of the arguments passed on
     * invocation
     *
     * @param mh the handle
     * @param args the bound arguments
     *
     * @return the bound method handle
     */
    private static MethodHandle varArgBinder(final MethodHandle mh, final Object[] args) {
        assert args != null;
        assert args.length > 0;
        return MH.filterArguments(mh, mh.type().parameterCount() - 1, MH.bindTo(BIND_VAR_ARGS, args));
    }

    /**
     * Heuristic to figure out if the method handle has a callee argument. If it's type is
     * {@code (ScriptFunction, ...)}, then we'll assume it has a callee argument. We need this as
     * the constructor above is not passed this information, and can't just blindly assume it's false
     * (notably, it's being invoked for creation of new scripts, and scripts have scopes, therefore
     * they also always receive a callee).
     *
     * @param mh the examined method handle
     *
     * @return true if the method handle expects a callee, false otherwise
     */
    protected static boolean needsCallee(final MethodHandle mh) {
        return needsCallee(mh.type());
    }

    static boolean needsCallee(final MethodType type) {
        final int length = type.parameterCount();

        if (length == 0) {
            return false;
        }

        final Class<?> param0 = type.parameterType(0);
        return param0 == ScriptFunction.class || param0 == boolean.class && length > 1 && type.parameterType(1) == ScriptFunction.class;
    }

    /**
     * Check if a javascript function methodhandle is a vararg handle
     *
     * @param mh method handle to check
     *
     * @return true if vararg
     */
    protected static boolean isVarArg(final MethodHandle mh) {
        return isVarArg(mh.type());
    }

    static boolean isVarArg(final MethodType type) {
        return type.parameterType(type.parameterCount() - 1).isArray();
    }

    /**
     * Is this ScriptFunction declared in a dynamic context
     * @return true if in dynamic context, false if not or irrelevant
     */
    public boolean inDynamicContext() {
        return false;
    }

    @SuppressWarnings("unused")
    private static Object[] bindVarArgs(final Object[] array1, final Object[] array2) {
        if (array2 == null) {
            // Must clone it, as we can't allow the receiving method to alter the array
            return array1.clone();
        }

        final int l2 = array2.length;
        if (l2 == 0) {
            return array1.clone();
        }

        final int l1 = array1.length;
        final Object[] concat = new Object[l1 + l2];
        System.arraycopy(array1, 0, concat, 0, l1);
        System.arraycopy(array2, 0, concat, l1, l2);

        return concat;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), ScriptFunctionData.class, name, MH.type(rtype, types));
    }

    /**
     * This class is used to hold the generic invoker and generic constructor pair. It is structured in this way since
     * most functions will never use them, so this way ScriptFunctionData only pays storage cost for one null reference
     * to the GenericInvokers object, instead of two null references for the two method handles.
     */
    private static final class GenericInvokers {
        volatile MethodHandle invoker;
        volatile MethodHandle constructor;
    }

    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        code = new LinkedList<>();
    }
}
