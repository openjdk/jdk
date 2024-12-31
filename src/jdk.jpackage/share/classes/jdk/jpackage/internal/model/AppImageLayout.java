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
package jdk.jpackage.internal.model;


import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.PathGroup;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;
import static jdk.jpackage.internal.util.PathUtils.resolveNullablePath;


/**
 * App image directory layout.
 */
public interface AppImageLayout {

    /**
     * Path to Java runtime directory.
     */
    Path runtimeDirectory();

    AppImageLayout resolveAt(Path root);

    record Stub(Path runtimeDirectory) implements AppImageLayout {

        @Override
        public AppImageLayout resolveAt(Path base) {
            return new Stub(resolveNullablePath(base, runtimeDirectory));
        }
    }

    public static PathGroup toPathGroup(AppImageLayout appImageLayout) {
        return new PathGroup(Stream.of(appImageLayout.getClass().getInterfaces())
                // For all interfaces (it should be one, but multiple is OK)
                // extending AppImageLayout interface call all non-static methods
                // without parameters and with java.nio.file.Path return type.
                // Create a map from the names of methods called and return values.
                .filter(AppImageLayout.class::isAssignableFrom)
                .map(Class::getMethods)
                .flatMap(Stream::of)
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> {
                    return m.getReturnType().isAssignableFrom(Path.class) && m.getParameterCount() == 0;
                }).<Map.Entry<String, Path>>mapMulti((m, consumer) -> {
                    Optional.ofNullable(toFunction(m::invoke).apply(appImageLayout)).ifPresent(path -> {
                        consumer.accept(Map.entry(m.getName(), (Path)path));
                    });
                }).collect(HashMap::new, (ctnr, e) -> {
                    ctnr.put(e.getKey(), e.getValue());
                }, Map::putAll));
    }
}
