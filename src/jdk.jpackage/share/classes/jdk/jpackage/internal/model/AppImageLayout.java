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
 * Generic app image directory layout.
 *
 * App image layout is a collection of files and directories with specific roles
 * (executables, configuration files, etc.) sharing the same root directory.
 *
 * The layout is "unresolved" if the root directory is an empty string and
 * "resolved" otherwise.
 */
public interface AppImageLayout {

    /**
     * A path to Java runtime directory.
     * The directory should have standard JDK subdirectories like "bin", "lib", etc.
     *
     * @return Java runtime sub-directory within this app image
     */
    Path runtimeDirectory();

    /**
     * Root directory of this app image.
     * It should normally be equal to <code>Path.of("")</code> for unresolved layout.
     *
     * @return the root directory of this app image
     */
    Path rootDirectory();

    /**
     * Creates a copy of this app image resolved at the given root directory.
     *
     * @param root path to a directory at which to resolve the layout
     * @return a copy of this app image resolved at the given root directory
     */
    AppImageLayout resolveAt(Path root);

    /**
     * Default implementation of {@link AppImageLayout} interface.
    */
    record Stub(Path rootDirectory, Path runtimeDirectory) implements AppImageLayout {

        @Override
        public AppImageLayout resolveAt(Path base) {
            return new Stub(resolveNullablePath(base, rootDirectory), resolveNullablePath(base, runtimeDirectory));
        }
    }

    /**
     * Creates {@link PathGroup} object from the given {@link AppImageLayout}
     * instance.
     *
     * It will call every non-static accessible method without parameters and with
     * {@link Path} return type of the given {@link AppImageLayout} instance except
     * {@link #rootDirectory()} method.
     * <p>
     * For every call, it will save the return value in the output {@link PathGroup}
     * object under the key equals the name of a function used in the call.
     *
     * @param appImageLayout source layout object
     * @return {@link PathGroup} object constructed from the given source layout
     *         object
     */
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
                .filter(m -> !"rootDirectory".equals(m.getName()))
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
