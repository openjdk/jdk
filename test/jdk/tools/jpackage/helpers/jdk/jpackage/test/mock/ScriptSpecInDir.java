/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.test.mock;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Specification of a {@link Script} bound to a specific directory.
 */
public class ScriptSpecInDir {

    public ScriptSpecInDir() {
    }

    @Override
    public String toString() {
        return scriptSpec.toString();
    }

    public boolean isPathInDir(Path path) {
        return path.startsWith(dir);
    }

    public ScriptSpecInDir dir(Path v) {
        dir = v;
        return this;
    }

    public ScriptSpecInDir scriptSpec(ScriptSpec v) {
        scriptSpec = v;
        return this;
    }

    public ScriptSpec scriptSpec() {
        Objects.requireNonNull(dir);
        return Objects.requireNonNull(scriptSpec);
    }

    public Script create() {
        return scriptSpec().create();
    }

    private ScriptSpec scriptSpec;
    private Path dir;
}
