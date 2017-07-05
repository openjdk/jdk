/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal.plugins;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import jdk.tools.jlink.internal.Utils;
import jdk.tools.jlink.plugin.PluginException;

/**
 *
 * Filter resource resources using path matcher.
 */
public class ResourceFilter implements Predicate<String> {
    private static final FileSystem JRT_FILE_SYSTEM = Utils.jrtFileSystem();

    final boolean negate;
    final List<PathMatcher> matchers;

    public ResourceFilter(String[] patterns) throws IOException {
        this(patterns, false);
    }

    public ResourceFilter(String[] patterns, boolean negate) throws IOException {
        this.negate = negate;
        this.matchers = new ArrayList<>();

        for (String pattern : patterns) {
            if (pattern.startsWith("@")) {
                File file = new File(pattern.substring(1));

                if (file.exists()) {
                    List<String> lines;

                    try {
                        lines = Files.readAllLines(file.toPath());
                    } catch (IOException ex) {
                        throw new PluginException(ex);
                    }

                    for (String line : lines) {
                        PathMatcher matcher = Utils.getPathMatcher(JRT_FILE_SYSTEM, line);
                        matchers.add(matcher);
                    }
                }
            } else {
                PathMatcher matcher = Utils.getPathMatcher(JRT_FILE_SYSTEM, pattern);
                matchers.add(matcher);
            }
        }
    }

    @Override
    public boolean test(String name) {
        Path path = JRT_FILE_SYSTEM.getPath(name);

        for (PathMatcher matcher : matchers) {
            if (matcher.matches(path)) {
                return !negate;
            }
        }

        return negate;
    }
}
