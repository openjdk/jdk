/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.cli;

import java.util.Set;
import java.util.function.Consumer;
import jdk.jpackage.internal.model.PackageType;
import static jdk.jpackage.internal.cli.StandardValueConverter.IDENTITY_CONV;
import static jdk.jpackage.internal.cli.StandardValueConverter.PATH_ARRAY_CONV;
import static jdk.jpackage.internal.cli.StandardValueConverter.PATH_CONV;
import static jdk.jpackage.internal.cli.StandardValueConverter.STRING_ARRAY_CONV;

final class OptionSpecBuilder {
    OptionSpec create() {
        return new OptionSpec(name, valueConverter, shortName, supportedPackageTypes, valueValidator);
    }

    OptionSpecBuilder ofString() {
        return valueConverter(IDENTITY_CONV);
    }

    OptionSpecBuilder ofPath() {
        return valueConverter(PATH_CONV);
    }

    OptionSpecBuilder ofDirectory() {
        return ofPath().valueValidator(StandardValueValidator::validateDirectory);
    }

    OptionSpecBuilder ofStringArray() {
        return valueConverter(STRING_ARRAY_CONV);
    }

    OptionSpecBuilder ofPathArray() {
        return valueConverter(PATH_ARRAY_CONV);
    }

    OptionSpecBuilder ofDirectoryArray() {
        return ofPathArray().valueValidator(StandardValueValidator::validateDirectoryArray);
    }

    OptionSpecBuilder ofUrl() {
        return ofString().valueValidator(StandardValueValidator::validateUrl);
    }

    OptionSpecBuilder noValue() {
        return valueValidator(null);
    }

    OptionSpecBuilder name(String v) {
        name = v;
        return this;
    }

    <T> OptionSpecBuilder valueConverter(ValueConverter<? extends T> v) {
        valueConverter = v;
        return this;
    }

    <T> OptionSpecBuilder valueValidator(Consumer<? extends T> v) {
        valueValidator = v;
        return this;
    }

    OptionSpecBuilder shortName(String v) {
        shortName = v;
        return this;
    }

    final OptionSpecBuilder supportedPackageTypes(PackageType... v) {
        return supportedPackageTypes(Set.of(v));
    }

    OptionSpecBuilder supportedPackageTypes(Set<PackageType> v) {
        supportedPackageTypes = v;
        return this;
    }

    String getName() {
        return name;
    }

    ValueConverter<?> getValueConverter() {
        return valueConverter;
    }

    Consumer<?> getValueValidator() {
        return valueValidator;
    }

    String getShortName() {
        return shortName;
    }

    Set<PackageType> getSupportedPackageTypes() {
        return supportedPackageTypes;
    }

    private String name;
    private ValueConverter<?> valueConverter;
    private Consumer<?> valueValidator;
    private String shortName;
    private Set<PackageType> supportedPackageTypes;
}
