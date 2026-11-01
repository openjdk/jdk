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

package jdk.jpackage.test.stdmock;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import jdk.jpackage.internal.model.DottedVersion;
import jdk.jpackage.test.mock.CommandMockSpec;
import jdk.jpackage.test.mock.CommandActionSpecs;

record DefaultWixToolsMock(String version) implements WixToolsMock {

    DefaultWixToolsMock {
        Objects.requireNonNull(version);
        if (version.isBlank()) {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public Collection<CommandMockSpec> mocks() {
        if (DottedVersion.compareComponents(DottedVersion.lazy(version), DottedVersion.greedy("4.0")) < 0) {
            // WiX v3
            return List.of(
                    new WixToolMock().candle(version).create(),
                    new WixToolMock().light(version).create(),
                    new CommandMockSpec("wix.exe", CommandActionSpecs.build().exit(1).create())
            );
        } else {
            // Wix v4
            return List.of(
                    new CommandMockSpec("candle.exe", CommandActionSpecs.build().exit(1).create()),
                    new CommandMockSpec("light.exe", CommandActionSpecs.build().exit(1).create()),
                    new WixToolMock().wix(version).create()
            );
        }
    }

    @Override
    public String toString() {
        return String.format("WiX Env %s", version);
    }
}
