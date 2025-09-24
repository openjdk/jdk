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

import static jdk.jpackage.internal.cli.StandardOption.APPCLASS;
import static jdk.jpackage.internal.cli.StandardOption.APP_VERSION;
import static jdk.jpackage.internal.cli.StandardOption.LAUNCHER_AS_SERVICE;
import static jdk.jpackage.internal.cli.StandardOption.NAME;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import jdk.jpackage.internal.cli.Options;


/**
 * Description of an external application image.
 */
public interface ExternalApplication {

    /**
     * Returns the list of additional launchers configured for the application.
     * <p>
     * Returns an empty list for an application without additional launchers.
     * @return the list of additional launchers configured for the application
     */
    List<LauncherInfo> getAddLaunchers();

    /**
     * Returns application version.
     * @return the application version
     */
    String getAppVersion();

    /**
     * Returns application name.
     * @return the application name
     */
    String getAppName();

    /**
     * Returns main launcher name.
     * @return the main launcher name
     */
    String getLauncherName();

    /**
     * Returns main class name.
     * @return the main class name
     */
    String getMainClass();

    /**
     * Returns additional properties.
     * @return the additional properties
     */
    Options getExtra();

    /**
     * Additional launcher description.
     *
     * @param name    the name of the additional launcher, see
     *                {@link Launcher#name()}
     * @param service {@code true} if the additional launcher should be installed as
     *                service, see {@link Launcher#isService()}
     * @param extra   platform-specific properties of the additional launcher
     */
    record LauncherInfo(String name, boolean service, Options extra) {
        public LauncherInfo {
            Objects.requireNonNull(name);
            Objects.requireNonNull(extra);
            if (name.isBlank()) {
                throw new IllegalArgumentException();
            }
        }

        public LauncherInfo(String name, boolean service) {
            this(name, service, Options.concat());
        }

        public LauncherInfo(Options options) {
            this(NAME.getFrom(options), LAUNCHER_AS_SERVICE.getFrom(options), options.copyWithout(NAME.id(), LAUNCHER_AS_SERVICE.id()));
        }

        /**
         * Returns {@code Options} representation of this instance.
         * <p>
         * Return value contains {@link #NAME} and {@link #LAUNCHER_AS_SERVICE}
         * values merged with the {@code Options} instance returned by the
         * {@link #extra()} method.
         *
         * @return the {@code Options} representation of this instance
         */
        public Options asOptions() {
            return Options.concat(Options.of(Map.of(NAME, name, LAUNCHER_AS_SERVICE, service)), extra);
        }
    }

    static ExternalApplication create(Options appOptions, List<Options> addLauncherOptions) {
        Objects.requireNonNull(appOptions);
        Objects.requireNonNull(addLauncherOptions);

        var addLaunchres = addLauncherOptions.stream().map(LauncherInfo::new).toList();

        var appVersion = APP_VERSION.getFrom(appOptions);
        var appName = NAME.getFrom(appOptions);
        var mainClass = APPCLASS.getFrom(appOptions);
        var extra = appOptions.copyWithout(APP_VERSION.id(), NAME.id(), APPCLASS.id());

        return new ExternalApplication() {

            @Override
            public List<LauncherInfo> getAddLaunchers() {
                return addLaunchres;
            }

            @Override
            public String getAppVersion() {
                return appVersion;
            }

            @Override
            public String getAppName() {
                return appName;
            }

            @Override
            public String getLauncherName() {
                return appName;
            }

            @Override
            public String getMainClass() {
                return mainClass;
            }

            @Override
            public Options getExtra() {
                return extra;
            }
        };
    }
}
