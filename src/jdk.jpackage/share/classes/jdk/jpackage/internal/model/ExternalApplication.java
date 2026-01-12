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
package jdk.jpackage.internal.model;

import static jdk.jpackage.internal.cli.StandardOption.APP_VERSION;
import static jdk.jpackage.internal.cli.StandardOption.NAME;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.cli.StandardAppImageFileOption.AppImageFileOptionScope;
import jdk.jpackage.internal.cli.WithOptionIdentifier;


/**
 * Description of an external application image.
 */
public sealed interface ExternalApplication {

    /**
     * Returns the main launcher configured for the application.
     * @return the main launcher configured for the application
     */
    LauncherInfo mainLauncher();

    /**
     * Returns the list of additional launchers configured for the application.
     * <p>
     * Returns an empty list for an application without additional launchers.
     * @return the list of additional launchers configured for the application
     */
    List<LauncherInfo> addLaunchers();

    /**
     * Returns application version.
     * @return the application version
     */
    String appVersion();

    /**
     * Returns application name.
     * @return the application name
     */
    String appName();

    /**
     * Returns additional properties.
     * @return the additional properties
     */
    Options extra();

    /**
     * Launcher description.
     */
    sealed interface LauncherInfo {

        /**
         * Gets the name of the launcher.
         * @return the name of the launcher
         */
        String name();

        /**
         * Gets optional properties of the launcher.
         * @return the optional properties of the launcher
         */
        Options extra();

        /**
         * Returns {@code Options} representation of this instance.
         * <p>
         * Return value contains the value of {@link #NAME} property merged with the
         * {@code Options} instance returned by the {@link #extra()} method.
         *
         * @return the {@code Options} representation of this instance
         */
        default Options asOptions() {
            return Options.concat(Options.of(Map.of(NAME, name())), extra());
        }

        static LauncherInfo create(Options options) {
            return new Internal.DefaultLauncherInfo(options);
        }
    }

    static ExternalApplication create(Options appOptions, List<Options> addLauncherOptions, OperatingSystem os) {

        var launcherRecognizedOptions = AppImageFileOptionScope.LAUNCHER.options(os).map(WithOptionIdentifier::id).toList();

        var recognizedOptions = Stream.concat(
                launcherRecognizedOptions.stream(),
                AppImageFileOptionScope.APP.options(os).map(WithOptionIdentifier::id)
        ).toList();

        appOptions = appOptions.copyWith(recognizedOptions);

        var addLaunchres = addLauncherOptions.stream().map(options -> {
            return options.copyWith(launcherRecognizedOptions);
        }).map(Internal.DefaultLauncherInfo::new).map(LauncherInfo.class::cast).toList();

        return new Internal.DefaultExternalApplication(appOptions, addLaunchres);
    }


    static final class Internal {

        private Internal() {
        }

        private record DefaultExternalApplication(Options options, List<LauncherInfo> addLaunchers) implements ExternalApplication {

            DefaultExternalApplication {
                Objects.requireNonNull(options);
                Objects.requireNonNull(addLaunchers);
            }

            @Override
            public String appVersion() {
                return APP_VERSION.getFrom(options);
            }

            @Override
            public String appName() {
                return NAME.getFrom(options);
            }

            @Override
            public Options extra() {
                return options
                        .copyWithout(APP_VERSION.id(), NAME.id())
                        .copyWithout(AppImageFileOptionScope.LAUNCHER.options().map(WithOptionIdentifier::id).toList());
            }

            @Override
            public LauncherInfo mainLauncher() {
                return new DefaultLauncherInfo(options.copyWithout(extra().ids()));
            }
        }

        private record DefaultLauncherInfo(Options options) implements LauncherInfo {

            DefaultLauncherInfo {
                Objects.requireNonNull(options);
            }

            @Override
            public String name() {
                return NAME.getFrom(options);
            }

            @Override
            public Options extra() {
                return options
                        .copyWithout(NAME.id())
                        .copyWithout(AppImageFileOptionScope.APP.options().map(WithOptionIdentifier::id).toList());
            }
        }
    }
}
