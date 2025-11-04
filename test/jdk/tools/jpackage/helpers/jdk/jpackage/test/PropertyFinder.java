/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.jpackage.test.AdditionalLauncher.getAdditionalLauncherProperties;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import jdk.jpackage.test.AdditionalLauncher.PropertyFile;

final class PropertyFinder {

    @FunctionalInterface
    static interface Finder<T> {
        Optional<String> find(T target);

        default Finder<T> defaultValue(String v) {
            return target -> {
                return Optional.of(find(target).orElse(v));
            };
        }

        default Finder<T> defaultValue(Supplier<String> v) {
            return target -> {
                return Optional.of(find(target).orElseGet(v));
            };
        }

        default Finder<T> map(UnaryOperator<String> v) {
            Objects.requireNonNull(v);
            return target -> {
                return find(target).map(v);
            };
        }

        default Finder<T> or(Finder<T> other) {
            return target -> {
                return find(target).or(() -> {
                    return other.find(target);
                });
            };
        }
    }

    static <T> Finder<T> nop() {
        return target -> {
            return Optional.empty();
        };
    }

    static Finder<AppImageFile> appImageFileLauncher(JPackageCommand cmd, String launcherName, String propertyName) {
        Objects.requireNonNull(propertyName);
        if (cmd.isMainLauncher(launcherName)) {
            return target -> {
                return Optional.ofNullable(target.launchers().get(target.mainLauncherName()).get(propertyName));
            };
        } else {
            return target -> {
                return Optional.ofNullable(target.addLaunchers().get(launcherName).get(propertyName));
            };
        }
    }

    static Finder<AppImageFile> appImageFile(Function<AppImageFile, String> propertyGetter) {
        Objects.requireNonNull(propertyGetter);
        return target -> {
            return Optional.of(propertyGetter.apply(target));
        };
    }

    static Finder<AppImageFile> appImageFileOptional(Function<AppImageFile, Optional<String>> propertyGetter) {
        Objects.requireNonNull(propertyGetter);
        return target -> {
            return propertyGetter.apply(target);
        };
    }

    static Finder<PropertyFile> launcherPropertyFile(String propertyName) {
        return target -> {
            return target.findProperty(propertyName);
        };
    }

    static Finder<JPackageCommand> cmdlineBooleanOption(String optionName) {
        return target -> {
            return Optional.of(target.hasArgument(optionName)).map(Boolean::valueOf).map(Object::toString);
        };
    }

    static Finder<JPackageCommand> cmdlineOptionWithValue(String optionName) {
        return target -> {
            return Optional.ofNullable(target.getArgumentValue(optionName));
        };
    }

    static Optional<String> findAppProperty(
            JPackageCommand cmd,
            Finder<JPackageCommand> cmdlineFinder,
            Finder<AppImageFile> appImageFileFinder) {

        Objects.requireNonNull(cmd);
        Objects.requireNonNull(cmdlineFinder);
        Objects.requireNonNull(appImageFileFinder);

        var reply = cmdlineFinder.find(cmd);
        if (reply.isPresent()) {
            return reply;
        } else {
            var appImageFilePath = Optional.ofNullable(cmd.getArgumentValue("--app-image")).map(Path::of);
            return appImageFilePath.map(AppImageFile::load).flatMap(appImageFileFinder::find);
        }
    }

    static Optional<String> findLauncherProperty(
            JPackageCommand cmd,
            String launcherName,
            Finder<JPackageCommand> cmdlineFinder,
            Finder<PropertyFile> addLauncherPropertyFileFinder,
            Finder<AppImageFile> appImageFileFinder) {

        return findLauncherProperty(
                cmd,
                launcherName,
                (theCmd, theLauncherName) -> {
                    return getAdditionalLauncherProperties(theCmd, theLauncherName);
                },
                cmdlineFinder,
                addLauncherPropertyFileFinder,
                appImageFileFinder);
    }

    static Optional<String> findLauncherProperty(
            JPackageCommand cmd,
            String launcherName,
            BiFunction<JPackageCommand, String, PropertyFile> addLauncherPropertyFileGetter,
            Finder<JPackageCommand> cmdlineFinder,
            Finder<PropertyFile> addLauncherPropertyFileFinder,
            Finder<AppImageFile> appImageFileFinder) {

        Objects.requireNonNull(cmd);
        Objects.requireNonNull(addLauncherPropertyFileGetter);
        Objects.requireNonNull(cmdlineFinder);
        Objects.requireNonNull(addLauncherPropertyFileFinder);
        Objects.requireNonNull(appImageFileFinder);

        var mainLauncher = cmd.isMainLauncher(launcherName);

        var appImageFilePath = Optional.ofNullable(cmd.getArgumentValue("--app-image")).map(Path::of);

        Optional<String> reply;

        if (mainLauncher) {
            reply = cmdlineFinder.find(cmd);
        } else if (appImageFilePath.isEmpty()) {
            var props = addLauncherPropertyFileGetter.apply(cmd, launcherName);
            reply = addLauncherPropertyFileFinder.find(props);
        } else {
            reply = Optional.empty();
        }

        if (reply.isPresent()) {
            return reply;
        } else {
            return appImageFilePath.map(AppImageFile::load).flatMap(appImageFileFinder::find);
        }
    }
}
