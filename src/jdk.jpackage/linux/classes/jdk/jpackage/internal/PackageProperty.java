/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.model.ConfigException;

/**
 * Linux package property.
 *
 * @param name             the property name (e.g.: "Architecture", "Version")
 * @param expectedValue    the expected value of the property
 * @param subtrituteString the substitute string in the jpackage resource if
 *                         applicable (e.g.: "APPLICATION_PACKAGE",
 *                         "APPLICATION_VERSION")
 * @param resourceName     the name of the custom resource file from the
 *                         resource directory in which this package property can
 *                         be set
 */
record PackageProperty(String name, String expectedValue, Optional<String> subtrituteString, String customResource) {

    PackageProperty {
        Objects.requireNonNull(name);
        Objects.requireNonNull(expectedValue);
        Objects.requireNonNull(subtrituteString);
        Objects.requireNonNull(customResource);
    }

    PackageProperty(String name, String expectedValue, String subtrituteString, String resourceName) {
        this(name, expectedValue, Optional.of(subtrituteString), resourceName);
    }

    PackageProperty(String name, String expectedValue, String resourceName) {
        this(name, expectedValue, Optional.empty(), resourceName);
    }

    private String formatErrorMessage(String actualValue) {
        Objects.requireNonNull(actualValue);
        return I18N.format("error.unexpected-package-property",
                name, expectedValue, actualValue, customResource);
    }

    private String formatAdvice(String actualValue) {
        Objects.requireNonNull(actualValue);
        return subtrituteString.map(ss -> {
            return I18N.format("error.unexpected-package-property.advice", ss, actualValue, name, customResource);
        }).orElseGet(() -> {
            return I18N.format("error.unexpected-default-package-property.advice", name, customResource);
        });
    }

    ConfigException verifyValue(String actualValue) {
        Objects.requireNonNull(actualValue);

        if (expectedValue.equals(actualValue)) {
            return null;
        }

        return new ConfigException(formatErrorMessage(actualValue), formatAdvice(actualValue));
    }
}
