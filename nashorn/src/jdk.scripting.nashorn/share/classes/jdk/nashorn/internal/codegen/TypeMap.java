/*
 * Copyright (c) 2010-2014, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.runtime.ScriptFunction;

/**
 * A data structure that maps one or several function nodes (by their unique id:s, not by
 * the FunctionNode object itself, due to copy on write changing it several times through
 * code generation.
 */
public class TypeMap {
    private final Map<Integer, Type[]> paramTypeMap  = new HashMap<>();
    private final Map<Integer, Type>   returnTypeMap = new HashMap<>();
    private final boolean needsCallee;

    /**
     * Constructor
     * @param functionNodeId function node id
     * @param type           method type found at runtime corresponding to parameter guess
     * @param needsCallee    does the function using this type map need a callee
     */
    public TypeMap(final int functionNodeId, final MethodType type, final boolean needsCallee) {
        final Type[] types = new Type[type.parameterCount()];
        int pos = 0;
        for (final Class<?> p : type.parameterArray()) {
            types[pos++] = Type.typeFor(p);
        }
        paramTypeMap.put(functionNodeId, types);
        returnTypeMap.put(functionNodeId, Type.typeFor(type.returnType()));

        this.needsCallee = needsCallee;
    }

    /**
     * Returns the array of parameter types for a particular function node
     * @param functionNodeId the ID of the function node
     * @return an array of parameter types
     * @throws NoSuchElementException if the type map has no mapping for the requested function
     */
    public Type[] getParameterTypes(final int functionNodeId) {
        final Type[] paramTypes = paramTypeMap.get(functionNodeId);
        if (paramTypes == null) {
            throw new NoSuchElementException(Integer.toString(functionNodeId));
        }
        return paramTypes.clone();
    }

    MethodType getCallSiteType(final FunctionNode functionNode) {
        final Type[] types = paramTypeMap.get(functionNode.getId());
        if (types == null) {
            return null;
        }

        MethodType mt = MethodType.methodType(returnTypeMap.get(functionNode.getId()).getTypeClass());
        if (needsCallee) {
            mt = mt.appendParameterTypes(ScriptFunction.class);
        }

        mt = mt.appendParameterTypes(Object.class); //this

        for (final Type type : types) {
            if (type == null) {
                return null; // not all parameter information is supplied
            }
            mt = mt.appendParameterTypes(type.getTypeClass());
        }

        return mt;
    }

    /**
     * Does the function using this TypeMap need a callee argument. This is used
     * to compute correct param index offsets in {@link jdk.nashorn.internal.codegen.ApplySpecialization}
     * @return true if a callee is needed, false otherwise
     */
    public boolean needsCallee() {
        return needsCallee;
    }

    /**
     * Get the parameter type for this parameter position, or
     * null if now known
     * @param functionNode functionNode
     * @param pos position
     * @return parameter type for this callsite if known
     */
    Type get(final FunctionNode functionNode, final int pos) {
        final Type[] types = paramTypeMap.get(functionNode.getId());
        assert types == null || pos < types.length : "fn = " + functionNode.getId() + " " + "types=" + Arrays.toString(types) + " || pos=" + pos + " >= length=" + types.length + " in " + this;
        if (types != null && pos < types.length) {
            return types[pos];
        }
        return null;
    }

    boolean has(final FunctionNode functionNode) {
        final int id = functionNode.getId();
        final Type[] paramTypes = paramTypeMap.get(id);
        assert (paramTypes == null) == (returnTypeMap.get(id) == null) : "inconsistent param and return types in param map";
        return paramTypes != null;
    }

    @Override
    public String toString() {
        return toString("");
    }

    String toString(final String prefix) {
        final StringBuilder sb = new StringBuilder();

        if (paramTypeMap.isEmpty()) {
            sb.append(prefix).append("\t<empty>");
            return sb.toString();
        }

        for (final Map.Entry<Integer, Type[]> entry : paramTypeMap.entrySet()) {
            final int id = entry.getKey();
            sb.append(prefix).append('\t');
            sb.append("function ").append(id).append('\n');
            sb.append(prefix).append("\t\tparamTypes=");
            if (entry.getValue() == null) {
                sb.append("[]");
            } else {
                sb.append(Arrays.toString(entry.getValue()));
            }
            sb.append('\n');
            sb.append(prefix).append("\t\treturnType=");
            final Type ret = returnTypeMap.get(id);
            sb.append(ret == null ? "N/A" : ret);
            sb.append('\n');
        }

        return sb.toString();
    }
}
