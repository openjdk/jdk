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

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.util.PathUtils;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.test.mock.CommandActionSpec;
import jdk.jpackage.test.mock.CommandActionSpecs;
import jdk.jpackage.test.mock.CommandMockSpec;

public final class WixToolMock {

    public CommandMockSpec create() {
        Objects.requireNonNull(type);
        Objects.requireNonNull(version);

        CommandActionSpec action;
        switch (type) {
            case CANDLE3 -> {
                action = candleAction(fips, version);
            }
            case LIGHT3 -> {
                action = lightAction(version);
            }
            case WIX4 -> {
                action = wixAction(version);
            }
            default -> {
                throw ExceptionBox.reachedUnreachable();
            }
        }

        var toolPath = Optional.ofNullable(dir).map(d -> {
            return d.resolve(type.fileName);
        }).orElse(type.fileName);
        var mockName = PathUtils.replaceSuffix(toolPath, "");

        return new CommandMockSpec(toolPath, mockName, CommandActionSpecs.build().action(action).create());
    }

    public WixToolMock fips(Boolean v) {
        fips = v;
        return this;
    }

    public WixToolMock fips() {
        return fips(true);
    }

    public WixToolMock dir(Path v) {
        dir = v;
        return this;
    }

    public WixToolMock version(String v) {
        version = v;
        return this;
    }

    public WixToolMock candle(String version) {
        return type(WixTool.CANDLE3).version(version);
    }

    public WixToolMock light(String version) {
        return type(WixTool.LIGHT3).version(version);
    }

    public WixToolMock wix(String version) {
        return type(WixTool.WIX4).version(version);
    }

    private WixToolMock type(WixTool v) {
        type = v;
        return this;
    }

    private static CommandActionSpec candleAction(boolean fips, String version) {
        Objects.requireNonNull(version);
        var sb = new StringBuilder();
        sb.append(version);
        if (fips) {
            sb.append("; fips");
        }
        return CommandActionSpec.create(sb.toString(), context -> {
            if (List.of("-?").equals(context.args())) {
                if (fips) {
                    context.err().println("error CNDL0308 : The Federal Information Processing Standard (FIPS) appears to be enabled on the machine");
                    return Optional.of(308);
                }
            } else if (!List.of("-fips").equals(context.args())) {
                throw context.unexpectedArguments();
            }

            var out = context.out();
            List.of(
                    "Windows Installer XML Toolset Compiler version " + version,
                    "Copyright (c) .NET Foundation and contributors. All rights reserved.",
                    "",
                    " usage:  candle.exe [-?] [-nologo] [-out outputFile] sourceFile [sourceFile ...] [@responseFile]"
            ).forEach(out::println);

            return Optional.of(0);
        });
    }

    private static CommandActionSpec lightAction(String version) {
        Objects.requireNonNull(version);
        return CommandActionSpec.create(version, context -> {
            if (List.of("-?").equals(context.args())) {
                var out = context.out();
                List.of(
                        "Windows Installer XML Toolset Linker version " + version,
                        "Copyright (c) .NET Foundation and contributors. All rights reserved.",
                        "",
                        " usage:  light.exe [-?] [-b bindPath] [-nologo] [-out outputFile] objectFile [objectFile ...] [@responseFile]"
                ).forEach(out::println);
                return Optional.of(0);
            } else {
                throw context.unexpectedArguments();
            }
        });
    }

    private static CommandActionSpec wixAction(String version) {
        Objects.requireNonNull(version);
        return CommandActionSpec.create(version, context -> {
            if (List.of("--version").equals(context.args())) {
                context.out().println(version);
                return Optional.of(0);
            } else {
                throw context.unexpectedArguments();
            }
        });
    }

    private enum WixTool {
        CANDLE3("candle"),
        LIGHT3("light"),
        WIX4("wix"),
        ;

        WixTool(String name) {
            this.fileName = Path.of(Objects.requireNonNull(name) + ".exe");
        }

        final Path fileName;
    }

    private Path dir;
    private WixTool type;
    private String version;
    private boolean fips;
}
