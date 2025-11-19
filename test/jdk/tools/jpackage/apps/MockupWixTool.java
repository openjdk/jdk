/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MockupWixTool {

    public static void main(String[] args) throws Exception {
        var type = Type.valueOf(getProperty("type"));
        try {
            switch (type) {
                case WIX4 -> {
                    if (List.of("--version").equals(List.of(args))) {
                        printWix4Version();
                        return;
                    }
                }
                case LIGHT3 -> {
                    if (List.of("-?").equals(List.of(args))) {
                        printLight3Version();
                        return;
                    }
                }
                case CANDLE3 -> {
                    if (List.of("-fips").equals(List.of(args))) {
                        printCandle3Version();
                        return;
                    } else if (List.of("-?").equals(List.of(args))) {
                        if (findProperty("fips").map(Boolean::parseBoolean).orElse(false)) {
                            System.err.println("error CNDL0308 : The Federal Information Processing Standard (FIPS) appears to be enabled on the machine");
                            System.exit(308);
                        } else {
                            printCandle3Version();
                        }
                        return;
                    }
                }
            }
            throw new IllegalArgumentException(String.format("Unexpected arguments %s for %s type", List.of(args), type));
        } catch (Exception ex) {
            var errorResponseFile = Path.of(getProperty("error-file"));
            Files.createDirectories(errorResponseFile.getParent());
            try (var w = Files.newBufferedWriter(errorResponseFile)) {
                ex.printStackTrace(new PrintWriter(w));
            }
            throw ex;
        }
    }

    public enum Type {
        CANDLE3,
        LIGHT3,
        WIX4
    }

    private static void printCandle3Version() {
        List.of(
                "Windows Installer XML Toolset Compiler version " + getProperty("version"),
                "Copyright (c) .NET Foundation and contributors. All rights reserved.",
                "",
                " usage:  candle.exe [-?] [-nologo] [-out outputFile] sourceFile [sourceFile ...] [@responseFile]"
        ).forEach(System.out::println);
    }

    private static void printLight3Version() {
        List.of(
                "Windows Installer XML Toolset Linker version " + getProperty("version"),
                "Copyright (c) .NET Foundation and contributors. All rights reserved.",
                "",
                " usage:  light.exe [-?] [-b bindPath] [-nologo] [-out outputFile] objectFile [objectFile ...] [@responseFile]"
        ).forEach(System.out::println);
    }

    private static void printWix4Version() {
        System.out.println(getProperty("version"));
    }

    private static String getProperty(String name) {
        return findProperty(name).get();
    }

    private static Optional<String> findProperty(String name) {
        return Optional.ofNullable(System.getProperty("jpackage.test.MockupWixTool." + Objects.requireNonNull(name)));
    }

}
