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

import java.util.Map;
import java.util.stream.IntStream;
import jdk.jpackage.internal.util.CompositeProxy;

public interface MacApplication extends Application, MacApplicationMixin {

    default DottedVersion shortVersion() {
        var verComponents = DottedVersion.lazy(version()).getComponents();
        return DottedVersion.greedy(IntStream.range(0, 3).mapToObj(idx -> {
            if (idx < verComponents.length) {
                return verComponents[idx].toString();
            } else {
                return "0";
            }
        }).collect(joining(".")));
    }

    default boolean sign() {
        return signingConfig().isPresent();
    }

    @Override
    default Map<String, String> extraAppImageFileData() {
        return Map.of(ExtraAppImageFileField.SIGNED.fieldName(), Boolean.toString(sign()));
    }

    public static MacApplication create(Application app, MacApplicationMixin mixin) {
        return CompositeProxy.create(MacApplication.class, app, mixin);
    }

    public enum ExtraAppImageFileField {
        SIGNED("signed");

        ExtraAppImageFileField(String fieldName) {
            this.fieldName = fieldName;
        }

        public String fieldName() {
            return fieldName;
        }

        private final String fieldName;
    }
}
