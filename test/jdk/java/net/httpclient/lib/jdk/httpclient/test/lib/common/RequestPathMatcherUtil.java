/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.httpclient.test.lib.common;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility which parses a request path and finds a best match registered handler
 */
public class RequestPathMatcherUtil {

    public record Resolved<T>(String bestMatchedPath, T handler) {
    }

    /**
     * Matches the {@code path} against the registered {@code pathHandlers} and returns the best
     * matched handler. If no handler is found for the {@code path}, then this method throws
     * an {@link IllegalArgumentException}
     *
     * @param path         The request path
     * @param pathHandlers The handlers for each of the registered paths
     * @param <T>
     * @return The resolved result
     * @throws IllegalArgumentException if no handler could be located for the {@code path}
     * @throws NullPointerException if {@code pathHandlers} is null
     */
    public static <T> Resolved<T> findHandler(final String path, final Map<String, T> pathHandlers) {
        Objects.requireNonNull(pathHandlers, "pathHandlers is null");
        final String fpath = (path == null || path.isEmpty()) ? "/" : path;
        final AtomicReference<String> bestMatch = new AtomicReference<>("");
        final AtomicReference<T> href = new AtomicReference<>();
        pathHandlers.forEach((key, value) -> {
            if (fpath.startsWith(key) && key.length() > bestMatch.get().length()) {
                bestMatch.set(key);
                href.set(value);
            }
        });
        final T handler = href.get();
        if (handler == null) {
            System.err.println("No handler found for path: " + path);
            throw new IllegalArgumentException("No handler found for path " + path);
        }
        return new Resolved<T>(bestMatch.get(), handler);
    }
}
