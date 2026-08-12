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

record DefaultDebToolsMock(
        String versionDpkg,
        String versionFakeroot,
        LinuxPackageLookupMock packageLookup) implements DebToolsMock {

    DefaultDebToolsMock {
        Objects.requireNonNull(versionDpkg);
        Objects.requireNonNull(versionFakeroot);
        if (versionDpkg.isBlank()) {
            throw new IllegalArgumentException();
        }
        if (versionFakeroot.isBlank()) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(packageLookup);
    }

    @Override
    public List<CommandMockSpec> mocks() {
        return Stream.of(dpkg(), dpkgdeb(), fakeroot()).map(action -> {
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
        return String.format("DEB Env %s; fakeroot=%s%s",
                versionDpkg, versionFakeroot, withPackageLookup() ? "; ldd" : "");
    }

    private CommandActionSpec dpkg() {
        var ver = versionDpkg("dpkg", versionDpkg);
        return CommandActionSpec.create("dpkg", context -> {
            if (context.args().contains("--version")) {
                context.out().println(ver);
            } else if (context.args().equals(List.of("-s", "coreutils"))) {
                var out = context.out();
                out.println("Package: coreutils");
                out.println("Essential: yes");
                out.println("Status: install ok installed");
            } else if (context.args().equals(List.of("--print-architecture"))) {
                context.out().println(LinuxHelper.getDefaultPackageArch(PackageType.LINUX_DEB));
            }
            return Optional.of(0);
        });
    }

    private CommandActionSpec dpkgdeb() {
        var ver = versionDpkg("dpkg-deb", versionDpkg);
        return CommandActionSpec.create("dpkg-deb", context -> {
            if (context.args().contains("--version")) {
                context.out().println(versionDpkg("dpkg-deb", ver));
            }
            return Optional.of(0);
        });
    }

    private CommandActionSpec fakeroot() {
        var ver = String.format("fakeroot version %s", versionFakeroot);
        return CommandActionSpec.create("fakeroot", context -> {
            if (context.args().contains("--version")) {
                context.out().println(ver);
            }
            return Optional.of(0);
        });
    }

    private static String versionDpkg(String tool, String version) {
        Objects.requireNonNull(tool);
        Objects.requireNonNull(version);
        return String.format("Debian '%s' package management program version %s (%s).",
                tool, version, LinuxHelper.getDefaultPackageArch(PackageType.LINUX_DEB));
    }
}
