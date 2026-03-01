/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import java.io.IOException;
import java.util.ArrayList;
import jdk.jpackage.internal.model.StandardPackageType;
import jdk.jpackage.internal.util.CommandOutputControl;
import jdk.jpackage.internal.util.Result;

record LinuxPackageArch(String value) {

    static Result<LinuxPackageArch> create(StandardPackageType pkgType) {
        switch (pkgType) {
            case LINUX_RPM -> {
                return rpm().map(LinuxPackageArch::new);
            }
            case LINUX_DEB -> {
                return deb().map(LinuxPackageArch::new);
            }
            default -> {
                throw new IllegalArgumentException();
            }
        }
    }

    private static Result<String> deb() {
        var exec = Executor.of("dpkg", "--print-architecture").saveOutput(true);
        return Result.of(exec::executeExpectSuccess, IOException.class)
                .flatMap(LinuxPackageArch::getStdoutFirstLine);
    }

    private static Result<String> rpm() {
        var errors = new ArrayList<Exception>();
        for (var tool : RpmArchReader.values()) {
            var result = tool.getRpmArch();
            if (result.hasValue()) {
                return result;
            } else {
                errors.addAll(result.errors());
            }
        }

        return Result.ofErrors(errors);
    }

    /*
     * Various ways to get rpm arch. Needed to address JDK-8233143. rpmbuild is mandatory for
     * rpm packaging, try it first. rpm is optional and may not be available, use as the last
     * resort.
     */
    private enum RpmArchReader {
        RPMBUILD("rpmbuild", "--eval=%{_target_cpu}"),
        RPM("rpm", "--eval=%{_target_cpu}");

        RpmArchReader(String... cmdline) {
            this.cmdline = cmdline;
        }

        Result<String> getRpmArch() {
            var exec = Executor.of(cmdline).saveOutput(true);
            return Result.of(exec::executeExpectSuccess, IOException.class)
                    .flatMap(LinuxPackageArch::getStdoutFirstLine);
        }

        private final String[] cmdline;
    }

    private static Result<String> getStdoutFirstLine(CommandOutputControl.Result result) {
        return Result.of(() -> {
            return result.stdout().stream().findFirst().orElseThrow(result::unexpected);
        }, IOException.class);
    }
}
