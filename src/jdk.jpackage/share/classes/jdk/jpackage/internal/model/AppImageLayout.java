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


import static jdk.jpackage.internal.util.PathUtils.mapNullablePath;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.PathGroup;


/**
 * Generic app image directory layout.
 *
 * App image layout is a collection of files and directories with specific roles
 * (executables, configuration files, etc.) sharing the same root directory.
 * <p>
 * The layout is "unresolved" if the root directory is an empty path
 * ({@code Path.of("")}) and "resolved" otherwise.
 * <p>
 * The return value of the {@link #runtimeDirectory()} method call is always a
 * path starting with the path returned by the {@link #rootDirectory()} method
 * call. Public methods without parameters and with the return type {@link Path}
 * in the derived interfaces must comply to this constrain.
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
     * Root directory of this app image layout.
     * It should normally be equal to <code>Path.of("")</code> for unresolved layout.
     *
     * @return the root directory of this app image layout
     */
    Path rootDirectory();

    /**
     * Returns a copy of this app image layout with the root directory set to an empty
     * path ({@code Path.of("")}) or this instance if its root directory is already
     * an empty path.
     *
     * @return an app image layout with the root directory set to an empty path
     */
    AppImageLayout resetRootDirectory();

    /**
     * Returns <code>true</code> if the root directory of this app image layout is
     * not an empty path, i.e, if it is not equal to <code>Path.of("")</code>.
     *
     * @return <code>true</code> if the root directory of this app image layout is
     *         not an empty path
     */
    default boolean isResolved() {
        return !rootDirectory().equals(Path.of(""));
    }

    /**
     * Creates a copy of this app image layout resolved at the given root directory.
     *
     * @param root path to a directory at which to resolve the layout
     * @return a copy of this app image layout resolved at the given root directory
     */
    default AppImageLayout resolveAt(Path root) {
        return map(root::resolve);
    }

    /**
     * Returns a copy of this app image layout resolved such that its root directory
     * is set to an empty path ({@code Path.of("")}) or this instance if its root
     * directory is already an empty path.
     *
     * @return an app image layout resolved at {@code Path.of("")} path
     */
    default AppImageLayout unresolve() {
        if (isResolved()) {
            final var root = rootDirectory();
            return map(root::relativize);
        } else {
            return this;
        }
    }

    /**
     * Returns a copy of this app image layout with the specified mapper applied to
     * every path.
     *
     * @param mapper the mapper to use with every path in this app image layout.
     * @return the copy of this app image layout with the specified mapper applied
     *         to every path
     */
    AppImageLayout map(UnaryOperator<Path> mapper);

    /**
     * Default implementation of {@link AppImageLayout} interface.
     */
    record Stub(Path rootDirectory, Path runtimeDirectory) implements AppImageLayout {

        public Stub {
            Objects.requireNonNull(rootDirectory);
        }

        public Stub(Path runtimeDirectory) {
            this(Path.of(""), runtimeDirectory);
        }

        @Override
        public AppImageLayout resetRootDirectory() {
            if (isResolved()) {
                return new Stub(runtimeDirectory);
            } else {
                return this;
            }
        }

        @Override
        public AppImageLayout map(UnaryOperator<Path> mapper) {
            return new Stub(mapNullablePath(mapper, rootDirectory), mapNullablePath(mapper, runtimeDirectory));
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
