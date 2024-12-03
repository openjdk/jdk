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

import java.util.HashMap;

/**
 * Parameters is required to instantiate a CodeGenerator (e.g. Template).
 *
 * It has a set of key-value pairs, i.e. pairs of argument-name and argument-value.
 * In templates, these are used to fill free variables (e.g. "#{var1}").
 *
 * It also has a unique instantiationID. This is used to differentiate names from
 * the same CodeGenerator (e.g. Template), the ID is simply appended to local variable
 * names to ensure there are no conflicts.
 * TODO public?
 */
public class Parameters {
    private static int instantiationIDCounter = 0;

    private HashMap<String,String> argumentsMap;
    public final int instantiationID;

    /**
     * Create an empty Parameters set, then add key-value pairs afterwards.
     */
    public Parameters() {
        this(new HashMap<String,String>());
    }

    public Parameters(HashMap<String,String> argumentsMap) {
        this.argumentsMap = new HashMap<String,String>(argumentsMap);
        this.instantiationID = instantiationIDCounter++;
    }

    public void add(String name, String value) {
        if (argumentsMap.containsKey(name)) {
            throw new TemplateFrameworkException("Parameter " + name + " cannot be added as " + value +
                                                 ", is already added as " + argumentsMap.get(name));
        }
        argumentsMap.put(name, value);
    }

    public String get(String name) {
        return argumentsMap.get(name);
    }

    // TODO verify that we have exactly the names we expect, and no different.
}
