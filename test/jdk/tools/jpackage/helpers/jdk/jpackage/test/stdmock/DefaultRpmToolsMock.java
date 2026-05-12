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
import java.util.Optional;
import java.util.stream.Stream;
import jdk.jpackage.test.LinuxHelper;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.mock.CommandActionSpec;
import jdk.jpackage.test.mock.CommandActionSpecs;
import jdk.jpackage.test.mock.CommandMockSpec;
import jdk.jpackage.test.mock.Script;

record DefaultRpmToolsMock(String version, LinuxPackageLookupMock packageLookup) implements RpmToolsMock {

    DefaultRpmToolsMock {
        Objects.requireNonNull(version);
        if (version.isBlank()) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(packageLookup);
    }

    @Override
    public Collection<CommandMockSpec> mocks() {
        var versionString = "RPM version " + version;

        var rpm = CommandActionSpec.create("rpm", context -> {
            if (context.args().contains("--version") || context.args().isEmpty()) {
                context.out().println(versionString);
            } else if (context.args().equals(List.of("-q", "rpm"))) {
                context.out().println("rpm-build");
            }
            return Optional.of(0);
        });

        var rpmbuild = CommandActionSpec.create("rpmbuild", context -> {
            if (context.args().contains("--version")) {
                context.out().println(versionString);
            } else if (context.args().contains("--eval=%{_target_cpu}")) {
                context.out().println(LinuxHelper.getDefaultPackageArch(PackageType.LINUX_RPM));
            }
            return Optional.of(0);
        });

        return Stream.of(rpm, rpmbuild).map(action -> {
            return new CommandMockSpec(action.description(), CommandActionSpecs.build().action(action).create());
        }).toList();
    }

    @Override
    public void applyTo(Script.Builder scriptBuilder) {
        mocks().forEach(scriptBuilder::map);
        packageLookup.applyTo(scriptBuilder);
    }

    @Override
    public boolean withPackageLookup() {
        return packageLookup == LinuxPackageLookupMock.ENABLED;
    }

    @Override
    public String toString() {
        return String.format("RPM Env %s%s", version, withPackageLookup() ? "; ldd" : "");
    }
}
