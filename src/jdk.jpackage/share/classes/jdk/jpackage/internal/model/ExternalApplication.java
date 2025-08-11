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

import java.util.List;
import java.util.Map;
import java.util.Objects;


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
    Map<String, String> getExtra();

    /**
     * Additional launcher description.
     */
    record LauncherInfo(String name, boolean service, Map<String, String> extra) {
        public LauncherInfo {
            Objects.requireNonNull(name);
            Objects.requireNonNull(extra);
            if (name.isBlank()) {
                throw new IllegalArgumentException();
            }
        }
    }
}
