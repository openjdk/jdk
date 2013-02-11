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

import static jdk.nashorn.internal.runtime.linker.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;

/**
 * A container for data needed to instantiate a specific {@link ScriptFunction} at runtime.
 * Instances of this class are created during codegen and stored in script classes'
 * constants array to reduce function instantiation overhead during runtime.
 */
public final class ScriptFunctionData {

    // per-function object flags
    private static final int IS_STRICT  = 0b0000_0001;
    private static final int IS_BUILTIN = 0b0000_0010;
    private static final int HAS_CALLEE = 0b0000_0100;
    private static final int IS_VARARGS = 0b0000_1000;

    /** Name of the function or "" */
    private final String name;
    /** Source of this function, or null */
    private final Source source;
    /** Map for new instance constructor */
    private PropertyMap  allocatorMap;
    /** Start position and length in source */
    private final long   token;
    /** Number of expected arguments, either taken from FunctionNode or calculated from method handle signature*/
    private int          arity;
    /** Does this function need a callee argument? */
    private final int    flags;

    /** Reference to code for this method. */
    private MethodHandle invoker;
    /** Reference to code for this method when called to create "new" object */
    private MethodHandle constructor;
    /** Constructor to create a new instance. */
    private MethodHandle allocator;
    /** Generic invoker to used in {@link ScriptFunction#invoke(Object, Object...)}. */
    private MethodHandle genericInvoker;
    /** Generic constructor used in {@link ScriptFunction#construct(Object, Object...)}. */
    private MethodHandle genericConstructor;
    /** Specializations - see @SpecializedFunction */
    private MethodHandle[] invokeSpecializations;
    /** Specializations - see @SpecializedFunction */
    private MethodHandle[] constructSpecializations;

    /**
     * Constructor
     * @param fn the function node
     * @param allocatorMap the allocator property map
     */
    public ScriptFunctionData(final FunctionNode fn, final PropertyMap allocatorMap) {

        final long firstToken = fn.getFirstToken();
        final long lastToken  = fn.getLastToken();
        final int  position   = Token.descPosition(firstToken);
        final int  length     = Token.descPosition(lastToken) - position + Token.descLength(lastToken);

        this.name         = fn.isAnonymous() ? "" : fn.getIdent().getName();
        this.source       = fn.getSource();
        this.allocatorMap = allocatorMap;
        this.token        = Token.toDesc(TokenType.FUNCTION, position, length);
        this.arity        = fn.getParameters().size();
        this.flags        = makeFlags(fn.needsCallee(), fn.isVarArg(), fn.isStrictMode(), false);
    }

    /**
     * Constructor
     * @param name the function name
     * @param methodHandle the method handle
     * @param specs array of specialized method handles
     * @param strict strict flag
     * @param builtin builtin flag
     */
    public ScriptFunctionData(final String name, final MethodHandle methodHandle, final MethodHandle[] specs, final boolean strict, final boolean builtin) {
        this.name        = name;
        this.source      = null;
        this.token       = 0;

        final MethodType type     = methodHandle.type();
        final int paramCount      = type.parameterCount();
        final boolean isVarArg    = type.parameterType(paramCount - 1).isArray();
        final boolean needsCallee = needsCallee(methodHandle);

        this.flags = makeFlags(needsCallee, isVarArg, strict, builtin);
        this.arity = isVarArg ? -1 : paramCount - 1; //drop the self param for arity

        if (needsCallee && !isVarArg) {
            this.arity--;
        }

        if (isConstructor(methodHandle)) {
            if (!isVarArg) {
                this.arity--;    // drop the boolean flag for arity
            }
            /*
             * We insert a boolean argument to tell if the method was invoked as
             * constructor or not if the method handle's first argument is boolean.
             */
            this.invoker     = MH.insertArguments(methodHandle, 0, false);
            this.constructor = adaptConstructor(MH.insertArguments(methodHandle, 0, true));

            if (specs != null) {
                this.invokeSpecializations    = new MethodHandle[specs.length];
                this.constructSpecializations = new MethodHandle[specs.length];
                for (int i = 0; i < specs.length; i++) {
                    this.invokeSpecializations[i]    = MH.insertArguments(specs[i], 0, false);
                    this.constructSpecializations[i] = adaptConstructor(MH.insertArguments(specs[i], 0, true));
                }
            }
        } else {
            this.invoker                  = methodHandle;
            this.constructor              = adaptConstructor(methodHandle);
            this.invokeSpecializations    = specs;
            this.constructSpecializations = specs;
        }
    }

    /**
     * Get the arity of the function.
     * @return the arity
     */
    public int getArity() {
        return arity;
    }

    /**
     * Set the arity of the function.
     * @param arity the arity
     */
    public void setArity(int arity) {
        this.arity = arity;
    }

    /**
     * Get the function name.
     * @return function name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the source of the function.
     * @return the source
     */
    public Source getSource() {
        return source;
    }

    /**
     * Get this function as a String containing its source code. If no source code
     * exists in this ScriptFunction, its contents will be displayed as {@code [native code]}
     * @return string representation of this function's source
     */
    public String toSource() {
        if (source != null && token != 0) {
            return source.getString(Token.descPosition(token), Token.descLength(token));
        }

        return "function " + (name == null ? "" : name) + "() { [native code] }";
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append(super.toString())
                .append(" [ ")
                .append(invoker)
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
     * Get the allocator property map.
     * @return the allocator map
     */
    public PropertyMap getAllocatorMap() {
        return allocatorMap;
    }

    /**
     * Get the function's parse token.
     * @return the token
     */
    public long getToken() {
        return token;
    }

    /**
     * Returns true if the function needs a callee argument.
     * @return the needsCallee flag
     */
    public boolean needsCallee() {
        return (flags & HAS_CALLEE) != 0;
    }

    /**
     * Returns true if this is a strict-mode function.
     * @return the strict flag
     */
    public boolean isStrict() {
        return (flags & IS_STRICT) != 0;
    }

    /**
     * Returns true if this is a built-in function.
     * @return the built-in flag
     */
    public boolean isBuiltin() {
        return (flags & IS_BUILTIN) != 0;
    }

    /**
     * Returns true if this is a var-arg function.
     * @return the var-arg flag
     */
    public boolean isVarArg() {
        return (flags & IS_VARARGS) != 0;
    }

    /**
     * Returns true if this is a non-strict, non-built-in function that requires non-primitive this argument
     * according to ECMA 10.4.3.
     * @return true if this argument must be an object
     */
    public boolean needsWrappedThis() {
        return (flags & (IS_STRICT | IS_BUILTIN)) == 0;
    }

    /**
     * Get the method handle used to invoke this function.
     * @return the invoke handle
     */
    public MethodHandle getInvoker() {
        return invoker;
    }

    /**
     * Get the method handle used to invoke this function as a constructor.
     * @return the constructor handle
     */
    public MethodHandle getConstructor() {
        return constructor;
    }

    /**
     * Set the constructor method handle.
     * @param constructor the constructor handle
     */
    public void setConstructor(MethodHandle constructor) {
        this.constructor = constructor;
        this.constructSpecializations = null;
    }

    /**
     * Get the method handle used to allocate a new object for this constructor.
     * @return the allocator handle
     */
    public MethodHandle getAllocator() {
        return allocator;
    }

    /**
     * Get an adapted version of the invoker handle that only uses {@code Object} as parameter and return types.
     * @return the generic invoke handle
     */
    public MethodHandle getGenericInvoker() {
        if (genericInvoker == null) {
            assert invoker != null : "invoker is null";
            genericInvoker = adaptMethodType(invoker);
        }
        return genericInvoker;
    }

    /**
     * Get an adapted version of the constructor handle that only uses {@code Object} as parameter and return types.
     * @return the generic constructor handle
     */
    public MethodHandle getGenericConstructor() {
        if (genericConstructor == null) {
            assert constructor != null : "constructor is null";
            genericConstructor = adaptMethodType(constructor);
        }
        return genericConstructor;
    }

    /**
     * Get the specialized invoke handles for this function.
     * @return array of specialized invoke handles
     */
    public MethodHandle[] getInvokeSpecializations() {
        return invokeSpecializations;
    }

    /**
     * Get the specialized construct handles for this function.
     * @return array of specialized construct handles
     */
    public MethodHandle[] getConstructSpecializations() {
        return constructSpecializations;
    }

    /**
     * Set the method handles for this function.
     * @param invoker the invoker handle
     * @param allocator the allocator handle
     */
    public void setMethodHandles(MethodHandle invoker, MethodHandle allocator) {
        // We can't make method handle fields final because they're not available during codegen
        // and they're set when first called, so we enforce set-once here.
        if (this.invoker == null) {
            this.invoker     = invoker;
            this.constructor = adaptConstructor(invoker);
            this.allocator   = allocator;
        }
    }

    /**
     * Convert boolean flags to int.
     * @param needsCallee needs-callee flag
     * @param isVarArg var-arg flag
     * @param isStrict strict flag
     * @param isBuiltin builtin flag
     * @return int flags
     */
    private static int makeFlags(final boolean needsCallee, final boolean isVarArg, final boolean isStrict, final boolean isBuiltin) {
        int flags = 0;
        if (needsCallee) {
            flags |= HAS_CALLEE;
        }
        if (isVarArg) {
            flags |= IS_VARARGS;
        }
        if (isStrict) {
            flags |= IS_STRICT;
        }
        if (isBuiltin) {
            flags |= IS_BUILTIN;
        }
        return flags;
    }

    /**
     * Test if a methodHandle refers to a constructor.
     * @param methodHandle MethodHandle to test.
     * @return True if method is a constructor.
     */
    private static boolean isConstructor(final MethodHandle methodHandle) {
        return methodHandle.type().parameterCount() >= 1 && methodHandle.type().parameterType(0) == boolean.class;
    }

    /**
     * Heuristic to figure out if the method handle has a callee argument. If it's type is either
     * {@code (boolean, Object, ScriptFunction, ...)} or {@code (Object, ScriptFunction, ...)}, then we'll assume it has
     * a callee argument. We need this as the constructor above is not passed this information, and can't just blindly
     * assume it's false (notably, it's being invoked for creation of new scripts, and scripts have scopes, therefore
     * they also always receive a callee.
     * @param methodHandle the examined method handle
     * @return true if the method handle expects a callee, false otherwise
     */
    private static boolean needsCallee(MethodHandle methodHandle) {
        final MethodType type = methodHandle.type();
        final int len = type.parameterCount();
        if(len == 0) {
            return false;
        }
        if(type.parameterType(0) == boolean.class) {
            return len > 1 && type.parameterType(1) == ScriptFunction.class;
        }
        return type.parameterType(0) == ScriptFunction.class;
    }

    /**
     * Takes a method handle, and returns a potentially different method handle that can be used in
     * {@link ScriptFunction#invoke(Object, Object...)} or {@link ScriptFunction#construct(Object, Object...)}.
     * The returned method handle will be sure to return {@code Object}, and will have all its parameters turned into
     * {@code Object} as well, except for the following ones:
     * <ul>
     *   <li>a last parameter of type {@code Object[]} which is used for vararg functions,</li>
     *   <li>the second argument, which is forced to be {@link ScriptFunction}, in case the function receives itself
     *   (callee) as an argument</li>
     * </ul>
     *
     * @param handle the original method handle
     * @return the new handle, conforming to the rules above.
     */
    private MethodHandle adaptMethodType(final MethodHandle handle) {
        final MethodType type = handle.type();
        MethodType newType = type.generic();
        if (isVarArg()) {
            newType = newType.changeParameterType(type.parameterCount() - 1, Object[].class);
        }
        if (needsCallee()) {
            newType = newType.changeParameterType(0, ScriptFunction.class);
        }
        return type.equals(newType) ? handle : handle.asType(newType);
    }

    /**
     * Adapts a method handle to conform to requirements of a constructor. Right now this consists of making sure its
     * return value is {@code Object}. We might consider moving the caller-this argument swap here too from
     * {@link ScriptFunction#findNewMethod(org.dynalang.dynalink.CallSiteDescriptor)}.
     * @param ctorHandle the constructor method handle
     * @return adapted constructor method handle
     */
    private static MethodHandle adaptConstructor(final MethodHandle ctorHandle) {
        return changeReturnTypeToObject(ctorHandle);
    }

    /**
     * Adapts the method handle so its return type is {@code Object}. If the handle's return type is already
     * {@code Object}, the handle is returned unchanged.
     * @param mh the handle to adapt
     * @return the adapted handle
     */
    private static MethodHandle changeReturnTypeToObject(final MethodHandle mh) {
        return MH.asType(mh, mh.type().changeReturnType(Object.class));
    }
}
