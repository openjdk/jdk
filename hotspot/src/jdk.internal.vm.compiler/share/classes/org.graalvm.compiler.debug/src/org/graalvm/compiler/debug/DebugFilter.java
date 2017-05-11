/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.debug.GraalDebugConfig.Options;
import org.graalvm.compiler.debug.internal.DebugScope;

/**
 * Implements the filter specified by the {@link Options#Dump}, {@link Options#Log},
 * {@link Options#Count},{@link Options#MethodMeter} and {@link Options#Time} options.
 * <p>
 * These options enable the associated debug facility if their filter matches the
 * {@linkplain DebugScope#getQualifiedName() name} of the {@linkplain Debug#currentScope() current
 * scope}. For the {@link Options#Dump} and {@link Options#Log} options, the log or dump level is
 * set. The {@link Options#Count},{@link Options#MethodMeter} and {@link Options#Time} options don't
 * have a level, for them {@code level = 0} means disabled and a {@code level > 0} means enabled.
 * <p>
 * A filter is a list of comma-separated terms of the form {@code <pattern>[:<level>]}. {@code
 * <pattern>} is interpreted as a glob pattern if it contains a "*" or "?" character. Otherwise, it
 * is interpreted as a substring. If {@code <pattern>} is empty, it matches every scope. If {@code :
 * <level>} is omitted, it defaults to {@link Debug#BASIC_LOG_LEVEL}. The term {@code ~<pattern>} is
 * a shorthand for {@code <pattern>:0} to disable a debug facility for a pattern.
 * <p>
 * The resulting log level of a scope is determined by the <em>last</em> matching term. If no term
 * matches, the log level is 0 (disabled). A filter with no terms matches every scope with a log
 * level of {@link Debug#BASIC_LOG_LEVEL}.
 *
 * <h2>Examples of filters</h2>
 *
 * <ul>
 * <li>(empty string)<br>
 * Matches any scope with log level {@link Debug#BASIC_LOG_LEVEL}.
 *
 * <li>{@code :1}<br>
 * Matches any scope with log level 1.
 *
 * <li>{@code *}<br>
 * Matches any scope with log level {@link Debug#BASIC_LOG_LEVEL}.
 *
 * <li>{@code CodeGen,CodeInstall}<br>
 * Matches scopes containing "CodeGen" or "CodeInstall", both with log level
 * {@link Debug#BASIC_LOG_LEVEL}.
 *
 * <li>{@code CodeGen:2,CodeInstall:1}<br>
 * Matches scopes containing "CodeGen" with log level 2, or "CodeInstall" with log level 1.
 *
 * <li>{@code :1,Dead:2}<br>
 * Matches scopes containing "Dead" with log level 2, and all other scopes with log level 1.
 *
 * <li>{@code :1,Dead:0}<br>
 * Matches all scopes with log level 1, except those containing "Dead".
 *
 * <li>{@code Code*}<br>
 * Matches scopes starting with "Code" with log level {@link Debug#BASIC_LOG_LEVEL}.
 *
 * <li>{@code Code,~Dead}<br>
 * Matches scopes containing "Code" but not "Dead", with log level {@link Debug#BASIC_LOG_LEVEL}.
 * </ul>
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
                        level = Debug.BASIC_LOG_LEVEL;
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
                                    level = Debug.BASIC_LOG_LEVEL;
                                    break;
                                case "info":
                                    level = Debug.INFO_LOG_LEVEL;
                                    break;
                                case "verbose":
                                    level = Debug.VERBOSE_LOG_LEVEL;
                                    break;
                                default:
                                    throw new IllegalArgumentException("Unknown dump level: \"" + levelString + "\" expected basic, info, verbose or an integer");
                            }
                        }

                    } else {
                        level = Debug.BASIC_LOG_LEVEL;
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
            return Debug.BASIC_LOG_LEVEL;
        } else {
            int level = 0;
            for (Term t : terms) {
                if (t.matches(input)) {
                    level = t.level;
                }
            }
            return level;
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
            if (filter.isEmpty()) {
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

        @Override
        public String toString() {
            return (pattern == null ? ".*" : pattern.toString()) + ":" + level;
        }
    }
}
