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

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.CompositeProxy;

public interface MacApplication extends Application, MacApplicationMixin {

    default DottedVersion shortVersion() {
        final var verComponents = DottedVersion.lazy(version()).getComponents();
        // Short version should have exactly three components according to
        // https://developer.apple.com/documentation/bundleresources/information-property-list/cfbundleshortversionstring
        int maxComponentCount = 3;
        // However, if the number of components is less than three, historically, jpackage will not add missing components.
        maxComponentCount = Integer.min(maxComponentCount, verComponents.length);
        return DottedVersion.greedy(IntStream.range(0, maxComponentCount).mapToObj(idx -> {
            if (idx < verComponents.length) {
                return verComponents[idx].toString();
            } else {
                return "0";
            }
        }).collect(joining(".")));
    }

    @Override
    default Path appImageDirName() {
        final String suffix;
        if (isRuntime()) {
            suffix = ".jdk";
        } else {
            suffix = ".app";
        }
        return Path.of(Application.super.appImageDirName().toString() + suffix);
    }

    /**
     * Returns {@code true} if the application image of this application should be
     * signed.
     *
     * @return {@code true} if the application image of this application should be
     *         signed
     */
    default boolean sign() {
        return signingConfig().isPresent();
    }

    @Override
    default Map<String, String> extraAppImageFileData() {
        return Stream.of(ExtraAppImageFileField.values()).collect(toMap(ExtraAppImageFileField::fieldName, x -> x.asString(this)));
    }

    public static MacApplication create(Application app, MacApplicationMixin mixin) {
        return CompositeProxy.create(MacApplication.class, app, mixin);
    }

    public enum ExtraAppImageFileField {
        SIGNED("signed", app -> Boolean.toString(app.sign())),
        APP_STORE("app-store", app -> Boolean.toString(app.appStore()));

        ExtraAppImageFileField(String fieldName, Function<MacApplication, String> getter) {
            this.fieldName = fieldName;
            this.getter = getter;
        }

        public String fieldName() {
            return fieldName;
        }

        String asString(MacApplication app) {
            return getter.apply(app);
        }

        private final String fieldName;
        private final Function<MacApplication, String> getter;
    }
}
