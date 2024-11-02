/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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


import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.PathGroup;
import static jdk.jpackage.internal.util.PathUtils.resolveNullable;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;


/**
 * App image directory layout.
 */
public interface AppImageLayout {

    /**
     * Path to Java run-time directory.
     */
    Path runtimeDirectory();

    AppImageLayout resolveAt(Path root);

    record Impl(Path runtimeDirectory) implements AppImageLayout {

        @Override
        public AppImageLayout resolveAt(Path base) {
            return new Impl(resolveNullable(base, runtimeDirectory));
        }
    }

    class Proxy<T extends AppImageLayout> extends ProxyBase<T> implements AppImageLayout {

        public Proxy(T target) {
            super(target);
        }

        @Override
        public Path runtimeDirectory() {
            return target.runtimeDirectory();
        }

        @Override
        public AppImageLayout resolveAt(Path root) {
            return target.resolveAt(root);
        }
    }

    public static PathGroup toPathGroup(AppImageLayout appImageLayout) {
        return new PathGroup(Stream.of(appImageLayout.getClass().getMethods()).filter(m -> {
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
