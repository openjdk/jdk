/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.comp;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.sun.tools.javac.util.List;

import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

/**
 * A Context class, that can keep useful information about unset fields.
 * This information will be produced during flow analysis and used during
 * code generation.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class UnsetFieldsInfo {
    protected static final Context.Key<UnsetFieldsInfo> unsetFieldsInfoKey = new Context.Key<>();

    public static UnsetFieldsInfo instance(Context context) {
        UnsetFieldsInfo instance = context.get(unsetFieldsInfoKey);
        if (instance == null)
            instance = new UnsetFieldsInfo(context);
        return instance;
    }

    @SuppressWarnings("this-escape")
    protected UnsetFieldsInfo(Context context) {
        context.put(unsetFieldsInfoKey, this);
    }

    private WeakHashMap<ClassSymbol, Map<JCTree, Set<VarSymbol>>> unsetFieldsMap = new WeakHashMap<>();

    public void addUnsetFieldsInfo(ClassSymbol csym, JCTree tree, Set<VarSymbol> unsetFields) {
        Map<JCTree, Set<VarSymbol>> treeToFieldsMap = unsetFieldsMap.get(csym);
        if (treeToFieldsMap == null) {
            treeToFieldsMap = new HashMap<>();
            treeToFieldsMap.put(tree, unsetFields);
            unsetFieldsMap.put(csym, treeToFieldsMap);
        } else {
            if (!treeToFieldsMap.containsKey(tree)) {
                // only add if there is no info for the given tree
                treeToFieldsMap.put(tree, unsetFields);
            }
        }
    }

    public Set<VarSymbol> getUnsetFields(ClassSymbol csym, JCTree tree) {
        Map<JCTree, Set<VarSymbol>> treeToFieldsMap = unsetFieldsMap.get(csym);
        if (treeToFieldsMap != null) {
            Set<VarSymbol> result = treeToFieldsMap.get(tree);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public void removeUnsetFieldInfo(ClassSymbol csym, JCTree tree) {
        Map<JCTree, Set<VarSymbol>> treeToFieldsMap = unsetFieldsMap.get(csym);
        if (treeToFieldsMap != null) {
            treeToFieldsMap.remove(tree);
        }
    }
}
