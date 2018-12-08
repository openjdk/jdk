/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.debug;

import java.util.Arrays;
import java.util.regex.Pattern;

import org.graalvm.compiler.debug.DebugContext.Scope;

/**
 * Implements the filter specified by options such as {@link DebugOptions#Dump},
 * {@link DebugOptions#Log}, {@link DebugOptions#Count} and {@link DebugOptions#Time}.
 *
 * See <a href="DumpHelp.txt">here</a> for a description of the filter syntax.
 *
 * <p>
 * These options enable the associated debug facility if their filter matches the
 * {@linkplain Scope#getQualifiedName() name} of the current scope. For the
 * {@link DebugOptions#Dump} and {@link DebugOptions#Log} options, the log or dump level is set. The
 * {@link DebugOptions#Count} and {@link DebugOptions#Time} options don't have a level, for them
 * {@code level = 0} means disabled and a {@code level > 0} means enabled.
 * <p>
 * The syntax for a filter is explained <a href="file:doc-files/DumpHelp.txt">here</a>.
 */
final class DebugFilter {

    public static DebugFilter parse(String spec) {
        if (spec == null) {
            return null;
        }
        return new DebugFilter(spec.split(","));
    }

    private final Term[] terms;

    private DebugFilter(String[] terms) {
        if (terms.length == 0) {
            this.terms = null;
        } else {
            this.terms = new Term[terms.length];
            for (int i = 0; i < terms.length; i++) {
                String t = terms[i];
                int idx = t.indexOf(':');

                String pattern;
                int level;
                if (idx < 0) {
                    if (t.startsWith("~")) {
                        pattern = t.substring(1);
                        level = 0;
                    } else {
                        pattern = t;
                        level = DebugContext.BASIC_LEVEL;
                    }
                } else {
                    pattern = t.substring(0, idx);
                    if (idx + 1 < t.length()) {
                        String levelString = t.substring(idx + 1);
                        try {
                            level = Integer.parseInt(levelString);
                        } catch (NumberFormatException e) {
                            switch (levelString) {
                                case "basic":
                                    level = DebugContext.BASIC_LEVEL;
                                    break;
                                case "info":
                                    level = DebugContext.INFO_LEVEL;
                                    break;
                                case "verbose":
                                    level = DebugContext.VERBOSE_LEVEL;
                                    break;
                                default:
                                    throw new IllegalArgumentException("Unknown dump level: \"" + levelString + "\" expected basic, info, verbose or an integer");
                            }
                        }

                    } else {
                        level = DebugContext.BASIC_LEVEL;
                    }
                }

                this.terms[i] = new Term(pattern, level);
            }
        }
    }

    /**
     * Check whether a given input is matched by this filter, and determine the log level.
     */
    public int matchLevel(String input) {
        if (terms == null) {
            return DebugContext.BASIC_LEVEL;
        } else {
            int defaultLevel = 0;
            int level = -1;
            for (Term t : terms) {
                if (t.isMatchAny()) {
                    defaultLevel = t.level;
                } else if (t.matches(input)) {
                    level = t.level;
                }
            }
            return level == -1 ? defaultLevel : level;
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("DebugFilter");
        if (terms != null) {
            buf.append(Arrays.toString(terms));
        } else {
            buf.append("[]");
        }
        return buf.toString();
    }

    private static class Term {

        private final Pattern pattern;
        public final int level;

        Term(String filter, int level) {
            this.level = level;
            if (filter.isEmpty() || filter.equals("*")) {
                this.pattern = null;
            } else if (filter.contains("*") || filter.contains("?")) {
                this.pattern = Pattern.compile(MethodFilter.createGlobString(filter));
            } else {
                this.pattern = Pattern.compile(".*" + MethodFilter.createGlobString(filter) + ".*");
            }
        }

        /**
         * Determines if a given input is matched by this filter.
         */
        public boolean matches(String input) {
            return pattern == null || pattern.matcher(input).matches();
        }

        public boolean isMatchAny() {
            return pattern == null;
        }

        @Override
        public String toString() {
            return (pattern == null ? ".*" : pattern.toString()) + ":" + level;
        }
    }
}
