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
 * Specification of a {@link CommandMock}.
 */
public record CommandMockSpec(Path name, Path mockName, CommandActionSpecs actions) {

    public CommandMockSpec {
        Objects.requireNonNull(name);
        Objects.requireNonNull(mockName);
        Objects.requireNonNull(actions);
    }

    public CommandMockSpec(Path name, CommandActionSpecs actions) {
        this(name, Path.of(name.toString() + "-mock"), actions);
    }

    public CommandMockSpec(String name, CommandActionSpecs actions) {
        this(Path.of(name), actions);
    }

    public CommandMockSpec(String name, String mockName, CommandActionSpecs actions) {
        this(Path.of(name), Path.of(mockName), actions);
    }

    public CommandMock.Builder toCommandMockBuilder() {
        return actions.toCommandMockBuilder().name(mockName.toString());
    }

    public boolean isDefaultMockName() {
        return (name.getFileName().toString() + "-mock").equals(mockName.getFileName().toString());
    }

    @Override
    public String toString() {
        return String.format("mock-of(%s)%s", name, actions);
    }
}
