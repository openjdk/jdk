/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.debug;

import org.graalvm.compiler.debug.internal.DebugScope;

/**
 * A utility for scoping a change to the current debug {@linkplain DebugScope#setConfig(DebugConfig)
 * configuration}. For example:
 *
 * <pre>
 *     DebugConfig config = ...;
 *     try (DebugConfigScope s = new DebugConfigScope(config)) {
 *         // ...
 *     }
 * </pre>
 */
public class DebugConfigScope implements AutoCloseable {

    private final DebugConfig current;

    /**
     * Sets the current debug {@linkplain DebugScope#setConfig(DebugConfig) configuration} to a
     * given value and creates an object that when {@linkplain #close() closed} resets the
     * configuration to the {@linkplain DebugScope#getConfig() current} configuration.
     */
    public DebugConfigScope(DebugConfig config) {
        this.current = DebugScope.getConfig();
        DebugScope.getInstance().setConfig(config);
    }

    @Override
    public void close() {
        DebugScope.getInstance().setConfig(current);
    }
}
