/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package compiler.lib.template_framework;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * TODO public?
 */
public class Scope {
    public final Scope parent;
    public final long fuel;
    public final CodeStream stream;

    private class VariableSet {
        public final VariableSet parent;
        public final HashMap<String,ArrayList<String>> variables;
        public final HashMap<String,Integer> totalVariables;

        public VariableSet(VariableSet parent) {
            this.parent = parent;
            this.variables = new HashMap<String,ArrayList<String>>();

            // Initize counts to parent, or zero.
            this.totalVariables = (parent == null) ? new HashMap<String,Integer>()
                                                   : new HashMap<String,Integer>(parent.totalVariables);
        }

        public String sample(String type) {
            return "TODO_" + type;
        }

        public void add(String name, String type) {
            // TODO verify that it does not exist yet
            // Fetch list of variables - if non-existant create a new one.
            ArrayList<String> variablesWithType = variables.get(type);
            if (variablesWithType == null) {
                variablesWithType = new ArrayList<String>();
                variables.put(type, variablesWithType);
            }
            variablesWithType.add(name);

            // Increment count.
            Integer count = totalVariables.get(type);
            if (count == null) {
                count = 0;
            }
            totalVariables.put(type, count + 1);
        }
    }

    public final VariableSet allVariables;
    public final VariableSet mutableVariables;

    public Scope(Scope parent, long fuel) {
        this.parent = parent;
        this.fuel = fuel;
        this.stream = new CodeStream();

        this.allVariables     = new VariableSet(this.parent != null ? this.parent.allVariables : null);
        this.mutableVariables = new VariableSet(this.parent != null ? this.parent.mutableVariables : null);
    }

    public CodeGeneratorLibrary library() {
        return this.parent.library();
    }

    public void close() {
        stream.close();
    }

    public void addVariable(String name, String type, boolean mutable) {
        allVariables.add(name, type);
        if (mutable) {
            mutableVariables.add(name, type);
        }
    }

    public String sampleVariable(String type, boolean mutable) {
        return mutable ? mutableVariables.sample(type) : allVariables.sample(type);
    }
}
