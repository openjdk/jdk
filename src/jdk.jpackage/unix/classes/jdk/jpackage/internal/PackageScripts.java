/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import jdk.jpackage.internal.resources.ResourceLocator;

/**
 * Shell scripts of a package.
 */
final class PackageScripts<T extends Enum<T> & Supplier<OverridableResource>> {

    static <T extends Enum<T> & Supplier<OverridableResource>> PackageScripts<T> create(
            Class<T> scriptIdsType) {
        return new PackageScripts<>(scriptIdsType);
    }

    PackageScripts(Class<T> scriptIdsType) {
        scripts = EnumSet.allOf(scriptIdsType).stream().collect(
                Collectors.toMap(UnaryOperator.identity(), scriptId -> {
                    return new ShellScriptResource(scriptId.name()).setResource(
                            scriptId.get());
                }));
    }

    PackageScripts<T> setSubstitutionData(T id, Map<String, String> data) {
        scripts.get(id).getResource().setSubstitutionData(data);
        return this;
    }

    PackageScripts<T> setSubstitutionData(Map<String, String> data) {
        scripts.values().forEach(
                script -> script.getResource().setSubstitutionData(data));
        return this;
    }

    PackageScripts<T> setResourceDir(Path v) throws IOException {
        for (var script : scripts.values()) {
            script.getResource().setResourceDir(v);
        }
        return this;
    }

    void saveInFolder(Path folder) throws IOException {
        for (var script : scripts.values()) {
            script.saveInFolder(folder);
        }
    }

    static class ResourceConfig {

        ResourceConfig(String defaultName, String categoryId) {
            this.defaultName = defaultName;
            this.category = I18N.getString(categoryId);
        }

        OverridableResource createResource() {
            var resource = new OverridableResource(defaultName,
                    ResourceLocator.class).setCategory(category);
            return getDefaultPublicName().map(resource::setPublicName).orElse(
                    resource);
        }

        private Optional<String> getDefaultPublicName() {
            final String wellKnownSuffix = ".template";
            if (defaultName.endsWith(wellKnownSuffix)) {
                return Optional.of(defaultName.substring(0, defaultName.length()
                        - wellKnownSuffix.length()));
            }
            return Optional.ofNullable(null);
        }

        private final String defaultName;
        private final String category;
    }

    private final Map<T, ShellScriptResource> scripts;
}
