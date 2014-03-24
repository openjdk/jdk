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

import static jdk.nashorn.internal.codegen.CompilerConstants.staticCall;
import static jdk.nashorn.internal.codegen.CompilerConstants.staticCallNoLookup;
import static jdk.nashorn.internal.codegen.CompilerConstants.virtualCallNoLookup;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.lookup.MethodHandleFactory;
import jdk.nashorn.internal.lookup.MethodHandleFunctionality;
import jdk.nashorn.internal.objects.Global;

/**
 * Used to signal to the linker to relink the callee
 */
@SuppressWarnings("serial")
public class RewriteException extends Exception {
    private static final MethodHandleFunctionality MH = MethodHandleFactory.getFunctionality();

    // Runtime scope in effect at the time of the compilation. Used to evaluate types of expressions and prevent overly
    // optimistic assumptions (which will lead to unnecessary deoptimizing recompilations).
    private ScriptObject runtimeScope;
    //contents of bytecode slots
    private Object[] byteCodeSlots;
    private final int[] previousContinuationEntryPoints;

    /** Methodhandle for getting the contents of the bytecode slots in the exception */
    public static final Call GET_BYTECODE_SLOTS       = virtualCallNoLookup(RewriteException.class, "getByteCodeSlots", Object[].class);
    /** Methodhandle for getting the program point in the exception */
    public static final Call GET_PROGRAM_POINT        = virtualCallNoLookup(RewriteException.class, "getProgramPoint", int.class);
    /** Methodhandle for getting the return value for the exception */
    public static final Call GET_RETURN_VALUE         = virtualCallNoLookup(RewriteException.class, "getReturnValueDestructive", Object.class);
    /** Methodhandle for the populate array bootstrap */
    public static final Call BOOTSTRAP                = staticCallNoLookup(RewriteException.class, "populateArrayBootstrap", CallSite.class, Lookup.class, String.class, MethodType.class, int.class);

    /** Methodhandle for populating an array with local variable state */
    private static final Call POPULATE_ARRAY           = staticCall(MethodHandles.lookup(), RewriteException.class, "populateArray", Object[].class, Object[].class, int.class, Object[].class);

    /**
     * Bootstrap method for populate array
     * @param lookup     lookup
     * @param name       name (ignored)
     * @param type       method type for signature
     * @param startIndex start index to start writing to
     * @return callsite to array populator (constant)
     */
    public static CallSite populateArrayBootstrap(final MethodHandles.Lookup lookup, final String name, final MethodType type, final int startIndex) {
        MethodHandle mh = POPULATE_ARRAY.methodHandle();
        mh = MH.insertArguments(mh, 1, startIndex);
        mh = MH.asCollector(mh, Object[].class, type.parameterCount() - 1);
        mh = MH.asType(mh, type);
        return new ConstantCallSite(mh);
    }

    /**
     * Constructor for a rewrite exception thrown from an optimistic function.
     * @param e the {@link UnwarrantedOptimismException} that triggered this exception.
     * @param byteCodeSlots contents of local variable slots at the time of rewrite at the program point
     */
    public RewriteException(final UnwarrantedOptimismException e, final Object[] byteCodeSlots, final String[] byteCodeSymbolNames, final ScriptObject runtimeScope) {
        this(e, byteCodeSlots, byteCodeSymbolNames, runtimeScope, null);
    }

    /**
     * Constructor for a rewrite exception thrown from a rest-of method.
     * @param e the {@link UnwarrantedOptimismException} that triggered this exception.
     * @param byteCodeSlots contents of local variable slots at the time of rewrite at the program point
     * @param previousContinuationEntryPoints an array of continuation entry points that were already executed during
     * one logical invocation of the function (a rest-of triggering a rest-of triggering a...)
     */
    public RewriteException(final UnwarrantedOptimismException e, final Object[] byteCodeSlots, final String[] byteCodeSymbolNames, final ScriptObject runtimeScope, final int[] previousContinuationEntryPoints) {
        super("", e, false, Context.DEBUG);
        this.byteCodeSlots = byteCodeSlots;
        this.runtimeScope = mergeSlotsWithScope(byteCodeSlots, byteCodeSymbolNames, runtimeScope);
        this.previousContinuationEntryPoints = previousContinuationEntryPoints;
    }

    private static ScriptObject mergeSlotsWithScope(final Object[] byteCodeSlots, final String[] byteCodeSymbolNames,
            final ScriptObject runtimeScope) {
        final ScriptObject locals = Global.newEmptyInstance();
        final int l = Math.min(byteCodeSlots.length, byteCodeSymbolNames.length);
        for(int i = 0; i < l; ++i) {
            final String name = byteCodeSymbolNames[i];
            final Object value = byteCodeSlots[i];
            if(name != null) {
                locals.set(name, value, true);
            }
        }
        locals.setProto(runtimeScope);
        return locals;
    }

    /**
     * Array populator used for saving the local variable state into the array contained in the
     * RewriteException
     * @param arrayToBePopluated array to be populated
     * @param startIndex start index to write to
     * @param items items with which to populate the array
     * @return the populated array - same array object
     */
    public static Object[] populateArray(final Object[] arrayToBePopluated, final int startIndex, final Object[] items) {
        System.arraycopy(items, 0, arrayToBePopluated, startIndex, items.length);
        return arrayToBePopluated;
    }

    private UnwarrantedOptimismException getUOE() {
        return (UnwarrantedOptimismException)getCause();
    }
    /**
     * Get return value. This method is destructive, after it is invoked subsequent invocation of either
     * {@link #getByteCodeSlots()} or this method will return null. This method is invoked from the generated
     * continuation code as the last step before continuing the execution, and we need to make sure we don't hang on to
     * either the entry bytecode slot values or the return value and prevent them from being garbage collected.
     * @return return value
     */
    public Object getReturnValueDestructive() {
        assert byteCodeSlots != null;
        byteCodeSlots = null;
        runtimeScope = null;
        return getUOE().getReturnValueDestructive();
    }

    private Object getReturnValueNonDestructive() {
        return getUOE().getReturnValueNonDestructive();
    }
    /**
     * Get return type
     * @return return type
     */
    public Type getReturnType() {
        return getUOE().getReturnType();
    }

    /**
     * Get the program point.
     * @return program point.
     */
    public int getProgramPoint() {
        return getUOE().getProgramPoint();
    }

    /**
     * Get the bytecode slot contents.
     * @return bytecode slot contents.
     */
    public Object[] getByteCodeSlots() {
        return byteCodeSlots;
    }

    /**
     * @return an array of continuation entry points that were already executed during one logical invocation of the
     * function (a rest-of triggering a rest-of triggering a...)
     */
    public int[] getPreviousContinuationEntryPoints() {
        return previousContinuationEntryPoints;
    }

    /**
     * Returns the runtime scope that was in effect when the exception was thrown.
     * @return the runtime scope.
     */
    public ScriptObject getRuntimeScope() {
        return runtimeScope;
    }

    private static String stringify(final Object returnValue) {
        if (returnValue == null) {
            return "null";
        }
        final String str = returnValue.toString();
        return returnValue instanceof Long ? (str + 'L') : str;
    }

    @Override
    public String getMessage() {
        return "programPoint=" + getProgramPoint() + " slots=" + Arrays.asList(byteCodeSlots) + ", returnValue=" + stringify(getReturnValueNonDestructive()) + ", returnType=" + getReturnType();
    }

    /**
     * Short toString function for message
     * @return short message
     */
    public String getMessageShort() {
        return "[programPoint=" + getProgramPoint() + " returnType=" + getReturnType() + " (" + stringify(getReturnValueNonDestructive()) + ")]";
    }

}
