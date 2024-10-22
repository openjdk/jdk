/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.util.ListResourceBundle;
import java.util.Map;
import jdk.internal.util.OperatingSystem;

import java.util.ResourceBundle;
import static java.util.stream.Collectors.toMap;
import java.util.stream.Stream;

class I18N {

    static String getString(String key) {
        return BUNDLE.getString(key);
    }

    private static class MultiResourceBundle extends ListResourceBundle {

        MultiResourceBundle(ResourceBundle... bundles) {
            contents = Stream.of(bundles).map(bundle -> {
                return bundle.keySet().stream().map(key -> {
                    return Map.entry(key, bundle.getObject(key));
                });
            }).flatMap(x -> x).collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (o, n) -> {
                // Override old value with the new one
                return n;
            })).entrySet().stream().map(e -> {
                return new Object[]{e.getKey(), e.getValue()};
            }).toArray(Object[][]::new);
        }

        @Override
        protected Object[][] getContents() {
            return contents;
        }

        private final Object[][] contents;
    }

    private static final MultiResourceBundle BUNDLE;

    static {
        List<String> bundleNames = new ArrayList<>();

        bundleNames.add("jdk.jpackage.internal.resources.MainResources");

        if (OperatingSystem.isLinux()) {
            bundleNames.add("jdk.jpackage.internal.resources.LinuxResources");
        } else if (OperatingSystem.isWindows()) {
            bundleNames.add("jdk.jpackage.internal.resources.WinResources");
            bundleNames.add("jdk.jpackage.internal.resources.WinResourcesNoL10N");
        } else if (OperatingSystem.isMacOS()) {
            bundleNames.add("jdk.jpackage.internal.resources.MacResources");
        } else {
            throw new IllegalStateException("Unknown platform");
        }

        BUNDLE = new MultiResourceBundle(bundleNames.stream().map(ResourceBundle::getBundle)
                .toArray(ResourceBundle[]::new));
    }
}
