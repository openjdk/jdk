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

import java.util.Objects;
import java.util.Optional;

/**
 * Property for summary.
 */
public enum StandardProperty implements Property {

    //
    // Keep items in the order they should be printed in the summary.
    //

    OPERATION("summary.property.operation", "summary.property.operation.format"),

    MAC_SIGN_APP_IMAGE_OPERATION("summary.property.operation", "summary.property.mac-sign-app-image.format"),

    OUTPUT_BUNDLE("summary.property.output-bundle"),

    LINUX_PACKAGE_NAME("summary.property.package-name"),

    WIN_MSI_PRODUCT_CODE("summary.property.product-code"),

    WIN_MSI_UPGRADE_CODE("summary.property.upgrade-code"),

    VERSION("summary.property.version"),

    WIN_WIX_VERSION("summary.property.wix-version"),

    LINUX_DISABLE_REQUIRED_PACKAGES_SEARCH("summary.property.required-packages-search", "summary.value.disabled"),

    ;

    StandardProperty(String label, Optional<String> valueFormatter) {
        this.label = Objects.requireNonNull(label);
        this.valueFormatter = Objects.requireNonNull(valueFormatter);
    }

    StandardProperty(String label, String valueFormatter) {
        this(label, Optional.of(valueFormatter));
    }

    StandardProperty(String label) {
        this(label, Optional.empty());
    }

    String label() {
        return label;
    }

    @Override
    public Optional<String> valueFormatter() {
        return valueFormatter;
    }

    @Override
    public String formatLabel() {
        return I18N.getString(label);
    }

    private final String label;
    private final Optional<String> valueFormatter;
}
