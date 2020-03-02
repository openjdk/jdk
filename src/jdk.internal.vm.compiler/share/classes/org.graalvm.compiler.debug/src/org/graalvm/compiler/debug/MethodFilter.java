/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.Signature;

/**
 * This class implements a method filter that can filter based on class name, method name and
 * parameters. This filter is a collection of "base filters", each of which may be negated. The
 * syntax for a filter is explained <a href="file:doc-files/MethodFilterHelp.txt">here</a>.
 */
public final class MethodFilter {

    private final ArrayList<BaseFilter> positiveFilters;
    private final ArrayList<BaseFilter> negativeFilters;

    private MethodFilter(ArrayList<BaseFilter> positiveFilters, ArrayList<BaseFilter> negativeFilters) {
        this.positiveFilters = positiveFilters;
        this.negativeFilters = negativeFilters;
    }

    /**
     * Parses a string containing a list of comma separated, possibly negated filter patterns into a
     * filter.
     */
    public static MethodFilter parse(String commaSeparatedPatterns) {
        String[] filters = commaSeparatedPatterns.split(",");
        ArrayList<BaseFilter> positiveFilters = new ArrayList<>();
        ArrayList<BaseFilter> negativeFilters = new ArrayList<>();
        for (int i = 0; i < filters.length; i++) {
            String pattern = filters[i].trim();
            boolean positive = true;
            if (pattern.startsWith("~")) {
                positive = false;
                pattern = pattern.substring(1);
            }
            BaseFilter filter = new BaseFilter(pattern);
            if (positive) {
                positiveFilters.add(filter);
            } else {
                negativeFilters.add(filter);
            }
        }
        return new MethodFilter(positiveFilters, negativeFilters);
    }

    /**
     * Cached instances matching nothing or everything, respectively.
     */
    private static MethodFilter matchNothingInstance = null;
    private static MethodFilter matchAllInstance = null;

    /**
     * Creates a MethodFilter instance that does not match anything.
     */
    public static MethodFilter matchNothing() {
        if (matchNothingInstance == null) {
            matchNothingInstance = new MethodFilter(new ArrayList<>(), new ArrayList<>());
        }
        return matchNothingInstance;
    }

    /**
     * Creates a MethodFilter instance that matches everything.
     */
    public static MethodFilter matchAll() {
        if (matchAllInstance == null) {
            ArrayList<BaseFilter> matchAllFilter = new ArrayList<>();
            matchAllFilter.add(new BaseFilter("*"));
            matchAllInstance = new MethodFilter(matchAllFilter, new ArrayList<>());
        }
        return matchAllInstance;
    }

    /**
     * Determines whether this is an empty filter that does not match anything.
     */
    public boolean matchesNothing() {
        return this.positiveFilters.isEmpty() && this.negativeFilters.isEmpty();
    }

    /**
     * Returns a string representation of all the base filters in this filter set.
     */
    @Override
    public String toString() {
        String positive = positiveFilters.stream().map(BaseFilter::toString).collect(Collectors.joining(", "));
        String negative = negativeFilters.stream().map(filter -> filter.toString(false)).collect(Collectors.joining(", "));
        if (positiveFilters.isEmpty()) {
            return negative;
        } else if (negativeFilters.isEmpty()) {
            return positive;
        } else {
            return positive + ", " + negative;
        }
    }

    /**
     * Determines if a given method is matched by this filter.
     */
    public boolean matches(JavaMethod method) {
        return matches(baseFilter -> baseFilter.matches(method));
    }

    /**
     * Determines if a given method with a given class and signature is matched by this filter.
     */
    public boolean matches(String javaClassName, String name, Signature sig) {
        return matches(baseFilter -> baseFilter.matches(javaClassName, name, sig));
    }

    /**
     * Determines if a given class name is matched by this filter.
     */
    public boolean matchesClassName(String className) {
        return matches(baseFilter -> baseFilter.matchesClassName(className));
    }

    private boolean matches(Predicate<BaseFilter> predicate) {
        // No match if any negative filter matches.
        for (BaseFilter negative : negativeFilters) {
            if (predicate.test(negative)) {
                return false;
            }
        }

        // At least one positive filter should normally match. But as a special case, if there are
        // only negative filters (and none of them matched), consider this a match.
        if (!negativeFilters.isEmpty() && positiveFilters.isEmpty()) {
            return true;
        }

        // Otherwise, match if there is at least one matching positive filter.
        for (BaseFilter positive : positiveFilters) {
            if (predicate.test(positive)) {
                return true;
            }
        }

        return false;
    }

    public static String createGlobString(String pattern) {
        return Pattern.quote(pattern).replace("?", "\\E.\\Q").replace("*", "\\E.*\\Q");
    }

    private static Pattern createClassGlobPattern(String pattern) {
        if (pattern.length() == 0) {
            return null;
        } else if (pattern.contains(".")) {
            return Pattern.compile(createGlobString(pattern));
        } else {
            return Pattern.compile("([^\\.\\$]*[\\.\\$])*" + createGlobString(pattern));
        }
    }

    private static final class BaseFilter {
        private final Pattern clazz;
        private final Pattern methodName;
        private final Pattern[] signature;

        private BaseFilter(String sourcePattern) {
            String pattern = sourcePattern.trim();

            // extract parameter part
            int pos = pattern.indexOf('(');
            if (pos != -1) {
                if (pattern.charAt(pattern.length() - 1) != ')') {
                    throw new IllegalArgumentException("missing ')' at end of method filter pattern: " + pattern);
                }
                String[] signatureClasses = pattern.substring(pos + 1, pattern.length() - 1).split(";", -1);
                signature = new Pattern[signatureClasses.length];
                for (int i = 0; i < signatureClasses.length; i++) {
                    signature[i] = createClassGlobPattern(signatureClasses[i].trim());
                }
                pattern = pattern.substring(0, pos);
            } else {
                signature = null;
            }

            // If there is at least one "." then everything before the last "." is the class name.
            // Otherwise, the pattern contains only the method name.
            pos = pattern.lastIndexOf('.');
            if (pos != -1) {
                clazz = createClassGlobPattern(pattern.substring(0, pos));
                methodName = Pattern.compile(createGlobString(pattern.substring(pos + 1)));
            } else {
                clazz = null;
                methodName = Pattern.compile(createGlobString(pattern));
            }
        }

        /**
         * Determines if the class part of this filter matches a given class name.
         */
        private boolean matchesClassName(String className) {
            return clazz == null || clazz.matcher(className).matches();
        }

        private boolean matches(JavaMethod o) {
            // check method name first, since MetaUtil.toJavaName is expensive
            if (methodName != null && !methodName.matcher(o.getName()).matches()) {
                return false;
            }
            if (clazz != null && !clazz.matcher(o.getDeclaringClass().toJavaName()).matches()) {
                return false;
            }
            return matchesSignature(o.getSignature());
        }

        private boolean matchesSignature(Signature sig) {
            if (signature == null) {
                return true;
            }
            if (sig.getParameterCount(false) != signature.length) {
                return false;
            }
            for (int i = 0; i < signature.length; i++) {
                JavaType type = sig.getParameterType(i, null);
                String javaName = type.toJavaName();
                if (signature[i] != null && !signature[i].matcher(javaName).matches()) {
                    return false;
                }
            }
            return true;
        }

        private boolean matches(String javaClassName, String name, Signature sig) {
            assert sig != null || signature == null;
            // check method name first, since MetaUtil.toJavaName is expensive
            if (methodName != null && !methodName.matcher(name).matches()) {
                return false;
            }
            if (clazz != null && !clazz.matcher(javaClassName).matches()) {
                return false;
            }
            return matchesSignature(sig);
        }

        @Override
        public String toString() {
            return toString(true);
        }

        private String toString(boolean positive) {
            StringBuilder buf = new StringBuilder("MethodFilter[");
            String sep = "";
            if (!positive) {
                buf.append(sep).append("NOT");
                sep = ", ";
            }
            if (clazz != null) {
                buf.append(sep).append("clazz=").append(clazz);
                sep = ", ";
            }
            if (methodName != null) {
                buf.append(sep).append("methodName=").append(methodName);
                sep = ", ";
            }
            if (signature != null) {
                buf.append(sep).append("signature=").append(Arrays.toString(signature));
                sep = ", ";
            }
            return buf.append("]").toString();
        }
    }
}
