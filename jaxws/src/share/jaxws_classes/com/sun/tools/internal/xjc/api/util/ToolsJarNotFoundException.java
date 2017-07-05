/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.api.util;

import java.io.File;

/**
 * Signals an error when tools.jar was not found.
 *
 * Simply print out the message obtained by {@link #getMessage()}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class ToolsJarNotFoundException extends Exception {
    /**
     * Location where we expected to find tools.jar
     */
    public final File toolsJar;

    public ToolsJarNotFoundException(File toolsJar) {
        super(calcMessage(toolsJar));
        this.toolsJar = toolsJar;
    }

    private static String calcMessage(File toolsJar) {
        return Messages.TOOLS_JAR_NOT_FOUND.format(toolsJar.getPath());
    }
}
