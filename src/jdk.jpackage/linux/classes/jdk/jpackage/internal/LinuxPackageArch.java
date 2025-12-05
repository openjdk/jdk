/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

import java.io.IOException;
import java.util.NoSuchElementException;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.internal.model.StandardPackageType;
import jdk.jpackage.internal.util.Result;

final class LinuxPackageArch {

    static String getValue(StandardPackageType pkgType) {
        switch (pkgType) {
            case LINUX_RPM -> {
                return RpmPackageArch.VALUE;
            }
            case LINUX_DEB -> {
                return DebPackageArch.VALUE;
            }
            default -> {
                throw new IllegalArgumentException();
            }
        }
    }

    private static class DebPackageArch {

        static final String VALUE = toSupplier(DebPackageArch::getValue).get();

        private static String getValue() throws IOException {
            return Executor.of("dpkg", "--print-architecture").saveOutput(true)
                    .executeExpectSuccess().getOutput().get(0);
        }
    }

    private static class RpmPackageArch {

        /*
         * Various ways to get rpm arch. Needed to address JDK-8233143. rpmbuild is mandatory for
         * rpm packaging, try it first. rpm is optional and may not be available, use as the last
         * resort.
         */
        private static enum RpmArchReader {
            Rpmbuild("rpmbuild", "--eval=%{_target_cpu}"),
            Rpm("rpm", "--eval=%{_target_cpu}");

            RpmArchReader(String... cmdline) {
                this.cmdline = cmdline;
            }

            Result<String> getRpmArch() {
                try {
                    return Result.ofValue(Executor.of(cmdline).saveOutput(true).executeExpectSuccess()).flatMap(result -> {
                        try {
                            return Result.ofValue(result.stdout().getFirstLineOfOutput());
                        } catch (NoSuchElementException ex) {
                            return Result.ofError(ex);
                        }
                    });
                } catch (IOException ex) {
                    return Result.ofError(ex);
                }
            }

            private final String[] cmdline;
        }

        static final String VALUE = toSupplier(RpmPackageArch::getValue).get();

        private static String getValue() throws IOException {
            for (var rpmArchReader : RpmArchReader.values()) {
                var result = rpmArchReader.getRpmArch();
                if (result.hasValue()) {
                    return result.value().orElseThrow();
                }
            }
            throw new JPackageException("error.rpm-arch-not-detected");
        }
    }
}
