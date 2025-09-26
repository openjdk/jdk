/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.Map;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.util.LocalizedExceptionBuilder;
import jdk.jpackage.internal.util.MultiResourceBundle;
import jdk.jpackage.internal.util.StringBundle;

final class I18N {

    static String getString(String key) {
        return BUNDLE.getString(key);
    }

    static String format(String key, Object ... args) {
        return BUNDLE.format(key, args);
    }

    static LocalizedExceptionBuilder<?> buildException() {
        return LocalizedExceptionBuilder.buildLocalizedException(BUNDLE);
    }

    static ConfigException.Builder buildConfigException() {
        return ConfigException.build(BUNDLE);
    }

    static ConfigException.Builder buildConfigException(String msgId, Object ... args) {
        return ConfigException.build(BUNDLE, msgId, args);
    }

    static ConfigException.Builder buildConfigException(Throwable t) {
        return ConfigException.build(BUNDLE, t);
    }

    private static final StringBundle BUNDLE;

    static {
        var prefix = "jdk.jpackage.internal.resources.";
        BUNDLE = StringBundle.fromResourceBundle(MultiResourceBundle.create(
                prefix + "MainResources",
                Map.of(
                        OperatingSystem.LINUX, List.of(prefix + "LinuxResources"),
                        OperatingSystem.MACOS, List.of(prefix + "MacResources"),
                        OperatingSystem.WINDOWS, List.of(prefix + "WinResources", prefix + "WinResourcesNoL10N")
                )
        ));
    }
}
