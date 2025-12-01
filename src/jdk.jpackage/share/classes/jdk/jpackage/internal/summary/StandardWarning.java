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
package jdk.jpackage.internal.summary;

import java.util.Optional;

/**
 * Warning in summary.
 */
public enum StandardWarning implements Warning {

    //
    // Keep items in the order they should be printed in the summary.
    //

    MAC_SIGNED_PREDEFINED_APP_IMAGE_WITHOUT_PACKAGE_FILE("warning.per.user.app.image.signed"),

    MAC_SIGNED_PKG_WITH_UNSIGNED_PREDEFINED_APP_IMAGE("warning.unsigned.app.image"),

    MAC_NON_STANDARD_APP_CONTENT("warning.non-standard-app-content"),

    MAC_BUNDLE_NAME_TOO_LONG("warning.bundle-name-too-long-warning"),

    LINUX_DEB_MISSING_LICENSE_GILE("message.debs-like-licenses"),

    ;

    StandardWarning(String valueFormatter) {
        this.valueFormatter = Optional.of(valueFormatter);
    }

    @Override
    public Optional<String> valueFormatter() {
        return valueFormatter;
    }

    private final Optional<String> valueFormatter;
}
