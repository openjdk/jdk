/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.management;

import java.beans.ConstructorProperties;
import java.util.List;

/**
 * Diagnostic command information. It contains the description of a
 * diagnostic command.
 *
 * @author  Frederic Parain
 * @since   7u4
 */

public class DiagnosticCommandInfo {
    private final String name;
    private final String description;
    private final String impact;
    private final boolean enabled;
    private final List<DiagnosticCommandArgumentInfo> arguments;

    /**
     * Returns the diagnostic command name
     *
     * @return the diagnostic command name
     */
    public String getName() {
        return name;
    }

   /**
     * Returns the diagnostic command description
     *
     * @return the diagnostic command description
     */
    public String getDescription() {
        return description;
    }

     /**
     * Returns the potential impact of the diagnostic command execution
     *         on the Java virtual machine behavior
     *
     * @return the potential impact of the diagnostic command execution
     *         on the Java virtual machine behavior
     */
    public String getImpact() {
        return impact;
    }

    /**
     * Returns {@code true} if the diagnostic command is enabled,
     *         {@code false} otherwise. The enabled/disabled
     *         status of a diagnostic command can evolve during
     *         the lifetime of the Java virtual machine.
     *
     * @return {@code true} if the diagnostic command is enabled,
     *         {@code false} otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the list of the diagnostic command arguments description.
     * If the diagnostic command has no arguments, it returns an empty list.
     *
     * @return a list of the diagnostic command arguments description
     */
    public List<DiagnosticCommandArgumentInfo> getArgumentsInfo() {
        return arguments;
    }

    @ConstructorProperties({"name", "description","impact","enabled",
                "argumentsInfo"})
    public DiagnosticCommandInfo(String name, String description,
                                 String impact, boolean enabled,
                                 List<DiagnosticCommandArgumentInfo> arguments)
    {
        this.name = name;
        this.description = description;
        this.impact = impact;
        this.enabled = enabled;
        this.arguments = arguments;
    }
}
