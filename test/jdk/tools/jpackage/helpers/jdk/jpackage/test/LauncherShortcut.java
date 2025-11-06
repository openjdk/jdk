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
package jdk.jpackage.test;

import static java.util.stream.Collectors.toMap;
import static jdk.jpackage.test.AdditionalLauncher.getAdditionalLauncherProperties;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import jdk.jpackage.test.AdditionalLauncher.PropertyFile;

public enum LauncherShortcut {

    LINUX_SHORTCUT("linux-shortcut"),

    WIN_DESKTOP_SHORTCUT("win-shortcut"),

    WIN_START_MENU_SHORTCUT("win-menu");

    public enum StartupDirectory {
        DEFAULT("true"),
        APP_DIR("app-dir"),
        ;

        StartupDirectory(String stringValue) {
            this.stringValue = Objects.requireNonNull(stringValue);
        }

        public String asStringValue() {
            return stringValue;
        }

        /**
         * Returns shortcut startup directory or an empty {@link Optional} instance if
         * the value of the {@code str} parameter evaluates to {@code false}.
         *
         * @param str the value of a shortcut startup directory
         * @return shortcut startup directory or an empty {@link Optional} instance
         * @throws IllegalArgumentException if the value of the {@code str} parameter is
         *                                  unrecognized
         */
        static Optional<StartupDirectory> parse(String str) {
            Objects.requireNonNull(str);
            return Optional.ofNullable(VALUE_MAP.get(str)).or(() -> {
                if (Boolean.TRUE.toString().equals(str)) {
                    return Optional.of(StartupDirectory.DEFAULT);
                } else if (Boolean.FALSE.toString().equals(str)) {
                    return Optional.empty();
                } else {
                    throw new IllegalArgumentException(String.format(
                            "Unrecognized launcher shortcut startup directory: [%s]", str));
                }
            });
        }

        private final String stringValue;

        private static final Map<String, StartupDirectory> VALUE_MAP =
                Stream.of(values()).collect(toMap(StartupDirectory::asStringValue, x -> x));
    }

    LauncherShortcut(String propertyName) {
        this.propertyName = Objects.requireNonNull(propertyName);
    }

    public String propertyName() {
        return propertyName;
    }

    public String appImageFilePropertyName() {
        return propertyName;
    }

    public String optionName() {
        return "--" + propertyName;
    }

    Optional<StartupDirectory> expectShortcut(JPackageCommand cmd, Optional<AppImageFile> predefinedAppImage, String launcherName) {
        Objects.requireNonNull(predefinedAppImage);

        if (cmd.isMainLauncher(launcherName)) {
            return findMainLauncherShortcut(cmd);
        } else {
            String[] propertyName = new String[1];
            return findAddLauncherShortcut(cmd, predefinedAppImage.map(appImage -> {
                propertyName[0] = appImageFilePropertyName();
                return new PropertyFile(appImage.addLaunchers().get(launcherName));
            }).orElseGet(() -> {
                propertyName[0] = this.propertyName;
                return getAdditionalLauncherProperties(cmd, launcherName);
            })::findProperty, propertyName[0]);
        }
    }


    public interface InvokeShortcutSpec {
        String launcherName();
        LauncherShortcut shortcut();
        Optional<Path> expectedWorkDirectory();
        List<String> commandLine();

        default Executor.Result execute() {
            return HelloApp.configureAndExecute(0, Executor.of(commandLine()).dumpOutput());
        }

        record Stub(
                String launcherName,
                LauncherShortcut shortcut,
                Optional<Path> expectedWorkDirectory,
                List<String> commandLine) implements InvokeShortcutSpec {

            public Stub {
                Objects.requireNonNull(launcherName);
                Objects.requireNonNull(shortcut);
                Objects.requireNonNull(expectedWorkDirectory);
                Objects.requireNonNull(commandLine);
            }
        }
    }


    private Optional<StartupDirectory> findMainLauncherShortcut(JPackageCommand cmd) {
        if (cmd.hasArgument(optionName())) {
            var value = Optional.ofNullable(cmd.getArgumentValue(optionName())).filter(optionValue -> {
                return !optionValue.startsWith("-");
            });
            if (value.isPresent()) {
                return value.flatMap(StartupDirectory::parse);
            } else {
                return Optional.of(StartupDirectory.DEFAULT);
            }
        } else {
            return Optional.empty();
        }
    }

    private Optional<StartupDirectory> findAddLauncherShortcut(JPackageCommand cmd,
            Function<String, Optional<String>> addlauncherProperties, String propertyName) {
        var explicit = addlauncherProperties.apply(propertyName);
        if (explicit.isPresent()) {
            return explicit.flatMap(StartupDirectory::parse);
        } else {
            return findMainLauncherShortcut(cmd);
        }
    }

    private final String propertyName;
}
