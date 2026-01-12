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

import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.DottedVersion;
import jdk.jpackage.internal.util.Result;

interface LinuxRpmSystemEnvironmentMixin {
    Path rpm();
    Path rpmbuild();

    record Stub(Path rpm, Path rpmbuild) implements LinuxRpmSystemEnvironmentMixin {
    }

    static Result<LinuxRpmSystemEnvironmentMixin> create() {

        final var errors = Stream.of(
                Internal.createRpmbuildToolValidator(),
                new ToolValidator(Internal.TOOL_RPM)
        ).map(ToolValidator::validate).filter(Objects::nonNull).toList();

        if (errors.isEmpty()) {
            return Result.ofValue(new Stub(Internal.TOOL_RPM, Internal.TOOL_RPMBUILD));
        } else {
            return Result.ofErrors(errors);
        }
    }

    static final class Internal {
        private static ToolValidator createRpmbuildToolValidator() {
            Pattern pattern = Pattern.compile(" (\\d+\\.\\d+)");
            return new ToolValidator(TOOL_RPMBUILD).setMinimalVersion(
                    TOOL_RPMBUILD_MIN_VERSION).setVersionParser(lines -> {
                        String versionString = lines.limit(1).findFirst().orElseThrow();
                        Matcher matcher = pattern.matcher(versionString);
                        if (matcher.find()) {
                            return matcher.group(1);
                        }
                        return null;
                    });
        }

        private static final Path TOOL_RPM = Path.of("rpm");
        private static final Path TOOL_RPMBUILD = Path.of("rpmbuild");
        private static final DottedVersion TOOL_RPMBUILD_MIN_VERSION = DottedVersion.lazy("4.10");
    }
}
