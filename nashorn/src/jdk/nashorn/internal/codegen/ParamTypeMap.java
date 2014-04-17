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

import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.FunctionNode;

/**
 * A data structure that maps one or several function nodes (by their unique id:s, not by
 * the FunctionNode object itself, due to copy on write changing it several times through
 * code generation.
 */
public class ParamTypeMap {
    final Map<Integer, Type[]> map = new HashMap<>();

    /**
     * Constructor
     * @param functionNode functionNode
     * @param type         method type found at runtime corresponding to parameter guess
     */
    public ParamTypeMap(final FunctionNode functionNode, final MethodType type) {
        this(functionNode.getId(), type);
    }

    /**
     * Constructor
     * @param functionNodeId function node id
     * @param type           method type found at runtime corresponding to parameter guess
     */
    public ParamTypeMap(final int functionNodeId, final MethodType type) {
        final Type[] types = new Type[type.parameterCount()];
        int pos = 0;
        for (final Class<?> p : type.parameterArray()) {
            types[pos++] = Type.typeFor(p);
        }
        map.put(functionNodeId, types);
    }

    ParamTypeMap(final Map<FunctionNode, Type[]> typeMap) {
        for (final Map.Entry<FunctionNode, Type[]> entry : typeMap.entrySet()) {
            map.put(entry.getKey().getId(), entry.getValue());
        }
    }
    /**
     * Get the parameter type for this parameter position, or
     * null if now known
     * @param functionNode functionNode
     * @param pos position
     * @return parameter type for this callsite if known
     */
    Type get(final FunctionNode functionNode, final int pos) {
        final Type[] types = map.get(functionNode.getId());
        assert types == null || pos < types.length : "fn = " + functionNode.getId() + " " + "types=" + Arrays.toString(types) + " || pos=" + pos + " >= length=" + types.length + " in " + this;
        if (types != null && pos < types.length) {
            return types[pos];
        }
        return null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("\n[ParamTypeMap]\n");
        if (map.isEmpty()) {
            sb.append("\t{}");
        } else {
            for (final Map.Entry<Integer, Type[]> entry : map.entrySet()) {
                sb.append('\t').append(entry.getKey() + "=>" + ((entry.getValue() == null) ? "[]" : Arrays.toString(entry.getValue()))).append('\n');
            }
        }
        return sb.toString();
    }
}
