/*
 * Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.httpserver;

import java.util.*;
import java.util.function.BiPredicate;

class ContextList {

    private static final System.Logger LOGGER = System.getLogger(ContextList.class.getName());

    private final LinkedList<HttpContextImpl> list = new LinkedList<>();

    public synchronized void add(HttpContextImpl ctx) {
        assert ctx != null;
        // `findContext(String protocol, String path, ContextPathMatcher matcher)`
        // expects the protocol to be lower-cased using ROOT locale, hence:
        assert ctx.getProtocol().equals(ctx.getProtocol().toLowerCase(Locale.ROOT));
        assert ctx.getPath() != null;
        // `ContextPathMatcher` expects context paths to be non-empty:
        assert !ctx.getPath().isEmpty();
        if (contains(ctx)) {
            throw new IllegalArgumentException("cannot add context to list");
        }
        list.add(ctx);
    }

    boolean contains(HttpContextImpl ctx) {
        return findContext(ctx.getProtocol(), ctx.getPath(), ContextPathMatcher.EXACT) != null;
    }

    public synchronized int size() {
        return list.size();
    }

   /**
    * {@return the context with the longest case-sensitive prefix match}
    *
    * @param protocol the request protocol
    * @param path the request path
    */
    HttpContextImpl findContext(String protocol, String path) {
        var matcher = ContextPathMatcher.ofConfiguredPrefixPathMatcher();
        return findContext(protocol, path, matcher);
    }

    private synchronized HttpContextImpl findContext(String protocol, String path, ContextPathMatcher matcher) {
        protocol = protocol.toLowerCase(Locale.ROOT);
        String longest = "";
        HttpContextImpl lc = null;
        for (HttpContextImpl ctx: list) {
            if (!ctx.getProtocol().equals(protocol)) {
                continue;
            }
            String cpath = ctx.getPath();
            if (!matcher.test(cpath, path)) {
                continue;
            }
            if (cpath.length() > longest.length()) {
                longest = cpath;
                lc = ctx;
            }
        }
        return lc;
    }

    private enum ContextPathMatcher implements BiPredicate<String, String> {

        /**
         * Tests if both the request path and the context path are identical.
         */
        EXACT(String::equals),

        /**
         * Tests <em>string prefix matches</em> where the request path string
         * starts with the context path string.
         *
         * <h3>Examples</h3>
         *
         * <table>
         * <thead>
         * <tr>
         *   <th rowspan="2">Context path</th>
         *   <th colspan="4">Request path</th>
         * </tr>
         * <tr>
         *     <th>/foo</th>
         *     <th>/foo/</th>
         *     <th>/foo/bar</th>
         *     <th>/foobar</th>
         * </tr>
         * </thead>
         * <tbody>
         * <tr>
         *   <td>/</td>
         *   <td>Y</td>
         *   <td>Y</td>
         *   <td>Y</td>
         *   <td>Y</td>
         * </tr>
         * <tr>
         *   <td>/foo</td>
         *   <td>Y</td>
         *   <td>Y</td>
         *   <td>Y</td>
         *   <td>Y</td>
         * </tr>
         * <tr>
         *   <td>/foo/</td>
         *   <td>N</td>
         *   <td>Y</td>
         *   <td>Y</td>
         *   <td>N</td>
         * </tr>
         * </tbody>
         * </table>
         */
        STRING_PREFIX((contextPath, requestPath) -> requestPath.startsWith(contextPath)),

        /**
         * Tests <em>path prefix matches</em> where path segments must have an
         * exact match.
         *
         * <h3>Examples</h3>
         *
         * <table>
         * <thead>
         * <tr>
         *   <th rowspan="2">Context path</th>
         *   <th colspan="4">Request path</th>
         * </tr>
         * <tr>
         *     <th>/foo</th>
         *     <th>/foo/</th>
         *     <th>/foo/bar</th>
         *     <th>/foobar</th>
         * </tr>
         * </thead>
         * <tbody>
         * <tr>
         *   <td>/</td>
         *   <td>Y</td>
         *   <td>Y</td>
         *   <td>Y</td>
         *   <td>Y</td>
         * </tr>
         * <tr>
         *   <td>/foo</td>
         *   <td>Y</td>
         *   <td>Y</td>
         *   <td>Y</td>
         *   <td>N</td>
         * </tr>
         * <tr>
         *   <td>/foo/</td>
         *   <td>N</td>
         *   <td>Y</td>
         *   <td>Y</td>
         *   <td>N</td>
         * </tr>
         * </tbody>
         * </table>
         */
        PATH_PREFIX((contextPath, requestPath) -> {

            // Fast-path for `/`
            if ("/".equals(contextPath)) {
                return true;
            }

            // Does the request path prefix match?
            if (requestPath.startsWith(contextPath)) {

                // Is it an exact match?
                int contextPathLength = contextPath.length();
                if (requestPath.length() == contextPathLength) {
                    return true;
                }

                // Is it a path-prefix match?
                assert contextPathLength > 0;
                return
                        // Case 1: The request path starts with the context
                        // path, but the context path has an extra path
                        // separator suffix. For instance, the context path is
                        // `/foo/` and the request path is `/foo/bar`.
                        contextPath.charAt(contextPathLength - 1) == '/' ||
                                // Case 2: The request path starts with the
                                // context path, but the request path has an
                                // extra path separator suffix. For instance,
                                // context path is `/foo` and the request path
                                // is `/foo/` or `/foo/bar`.
                                requestPath.charAt(contextPathLength) == '/';

            }

            return false;

        });

        private final BiPredicate<String, String> predicate;

        ContextPathMatcher(BiPredicate<String, String> predicate) {
            this.predicate = predicate;
        }

        @Override
        public boolean test(String contextPath, String requestPath) {
            return predicate.test(contextPath, requestPath);
        }

        private static ContextPathMatcher ofConfiguredPrefixPathMatcher() {
            var propertyName = "sun.net.httpserver.pathMatcher";
            var propertyValueDefault = "pathPrefix";
            var propertyValue = System.getProperty(propertyName, propertyValueDefault);
            return switch (propertyValue) {
                case "pathPrefix" -> ContextPathMatcher.PATH_PREFIX;
                case "stringPrefix" -> ContextPathMatcher.STRING_PREFIX;
                default -> {
                    LOGGER.log(
                            System.Logger.Level.WARNING,
                            "System property \"{}\" contains an invalid value: \"{}\". Falling back to the default: \"{}\"",
                            propertyName, propertyValue, propertyValueDefault);
                    yield ContextPathMatcher.PATH_PREFIX;
                }
            };
        }

    }

    public synchronized void remove(String protocol, String path)
        throws IllegalArgumentException
    {
        HttpContextImpl ctx = findContext(protocol, path, ContextPathMatcher.EXACT);
        if (ctx == null) {
            throw new IllegalArgumentException("cannot remove element from list");
        }
        list.remove(ctx);
    }

    public synchronized void remove(HttpContextImpl context)
        throws IllegalArgumentException
    {
        for (HttpContextImpl ctx: list) {
            if (ctx.equals(context)) {
                list.remove(ctx);
                return;
            }
        }
        throw new IllegalArgumentException("no such context in list");
    }
}
