/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * Parameters is required to instantiate a {@link CodeGenerator} (e.g. {@link Template}).
 * <p>
 * It has a set of parameter key-value pairs. In {@link Template}s, these are used to fill
 * parameter holes specified by {@code #{param}}.
 * <p>
 * The {@link instantiationID} is unique for an instantiation, and allows the variable renaming
 * to avoid variable name collisions when multiple {@link Template}s use the same variable name.
 */
public final class Parameters {
    private static int instantiationIDCounter = 0;

    private HashMap<String,String> parameterMap;

    /**
     * Unique ID used for variable renaming, to avoid name collisions between {@link Template}s.
     */
    public final int instantiationID;

    /**
     * Create an empty Parameters set, then add parameter key-value pairs later.
     */
    public Parameters() {
        this(new HashMap<String,String>());
    }

    /**
     * Create a Parameters set that already has some parameter key-value pairs.
     *
     * @param parameterMap A list of parameter key-value pairs.
     */
    public Parameters(Map<String,String> parameterMap) {
        this.parameterMap = new HashMap<String,String>(parameterMap);
        this.instantiationID = instantiationIDCounter++;
    }

    /**
     * Add a parameter key-value pair to the parameter set.
     *
     * @param name Name of the parameter.
     * @param value Value to be set for the parameter.
     */
    public void add(String name, String value) {
        if (parameterMap.containsKey(name)) {
            throw new TemplateFrameworkException("Parameter " + name + " cannot be added as " + value +
                                                 ", is already added as " + parameterMap.get(name));
        }
        parameterMap.put(name, value);
    }

    /**
     * Add a set of parameter key-value pairs.
     *
     * @param parameterMap Map that contains all the parameter key-value pairs to be added.
     */
    void add(Map<String,String> parameterMap) {
        for (Map.Entry<String,String> e : parameterMap.entrySet()) {
            add(e.getKey(), e.getValue());
        }
    }

    /**
     * Get the parameter value for a specified parameter name, or {@code null} if there is no parameter
     * key-value pair for this parameter name.
     *
     * @param name The name of the parameter.
     * @return Parameter value, or {@code null} if there is no parameter key-value pair for the name.
     */
    public String getOrNull(String name) {
        return parameterMap.get(name);
    }

    /**
     * Get the parameter value for a specified parameter name.
     *
     * @param name The name of the parameter.
     * @param scope For debug printing the "scope-trace".
     * @return Parameter value.
     * @throws TemplateFrameworkException If the parameter for the name does not exist.
     */
    public String get(String name, Scope scope) {
        String param = getOrNull(name);
        if (param == null) {
            scope.print();
            throw new TemplateFrameworkException("Missing parameter '" + name + "'.");
        }
        return param;
    }

    /**
     * Get the parameter value as an int for a specified parameter name.
     *
     * @param name The name of the parameter.
     * @param scope For debug printing the "scope-trace".
     * @return Parameter int value.
     * @throws TemplateFrameworkException If the parameter for the name does not exist, or cannot be
     *                                    parsed as an int.
     */
    public int getInt(String name, Scope scope) {
        String param = get(name, scope);
        return parseInt(param, scope, "for parameter '" + name + "'.");
    }

    /**
     * Get the parameter value as an int for a specified parameter name,
     * or the default value if the parameter is not available.
     *
     * @param name The name of the parameter.
     * @param defaultValue Default value if the parameter name is not available.
     * @param scope For debug printing the "scope-trace".
     * @return Parameter int value if the parameter name is present, else the default value.
     * @throws TemplateFrameworkException If the parameter for the name exists but cannot be
     *                                    parsed as an int.
     */
    public int getIntOrDefault(String name, int defaultValue, Scope scope) {
        String param = getOrNull(name);
        if (param == null) {
            return defaultValue;
        }
        return parseInt(param, scope, "for parameter '" + name + "'.");
    }

    /**
     * Get the parameter value as an long for a specified parameter name,
     * or the default value if the parameter is not available.
     *
     * @param name The name of the parameter.
     * @param defaultValue Default value if the parameter name is not available.
     * @param scope For debug printing the "scope-trace".
     * @return Parameter long value if the parameter name is present, else the default value.
     * @throws TemplateFrameworkException If the parameter for the name exists but cannot be
     *                                    parsed as a long.
     */
    public long getLongOrDefault(String name, long defaultValue, Scope scope) {
        String param = getOrNull(name);
        if (param == null) {
            return defaultValue;
        }
        return parseLong(param, scope, "for parameter '" + name + "'.");
    }

    private static int parseInt(String string, Scope scope, String errorMessage) {
        switch (string) {
            case "min_int" -> { return Integer.MIN_VALUE; }
            case "max_int" -> { return Integer.MAX_VALUE; }
        }
        try {
            return Integer.valueOf(string);
        } catch (NumberFormatException e) {
            scope.print();
            throw new TemplateFrameworkException("Could not parse int from string '" + string + "' " + errorMessage);
        }
    }

    private static long parseLong(String string, Scope scope, String errorMessage) {
        switch (string) {
            case "min_int" -> { return Integer.MIN_VALUE; }
            case "max_int" -> { return Integer.MAX_VALUE; }
            case "min_long" -> { return Long.MIN_VALUE; }
            case "max_long" -> { return Long.MAX_VALUE; }
        }
        try {
            return Long.valueOf(string);
        } catch (NumberFormatException e) {
            scope.print();
            throw new TemplateFrameworkException("Could not parse long from string '" + string + "' " + errorMessage);
        }
    }

    /**
     * Get the parameter map with all parameter key-value pairs.
     *
     * @return Parameter map with all key-value pairs.
     */
    HashMap<String,String> getParameterMap() {
        return parameterMap;
    }

    /**
     * Print all parameter key-value pairs for debugging.
     */
    public void print() {
        System.out.println("  Parameters ID=" + instantiationID);
        for (Map.Entry<String,String> e : parameterMap.entrySet()) {
            System.out.println("    " + e.getKey() + "=" + e.getValue());
        }
    }


    /**
     * Check that only parameter with parameter names from {@code names} are in the parameter set.
     * Useful for parameter verification in {@link ProgrammaticCodeGenerator}s.
     *
     * @param scope For debug printing the "scope-trace".
     * @param names List of allowed parameter names.
     * @throws TemplateFrameworkException If parameter names are used that are not in {@code names}.
     */
    public void checkOnlyHas(Scope scope, String... names) {
        Set<String> set = Set.of(names);
        for (String key : parameterMap.keySet()) {
            if (!set.contains(key)) {
                scope.print();
                throw new TemplateFrameworkException("Parameters have unexpected entry: " + key);
            }
        }
    }
}
