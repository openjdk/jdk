/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tools.jdeps;

import com.sun.tools.classfile.Dependencies;
import com.sun.tools.classfile.Dependency;
import com.sun.tools.classfile.Dependency.Location;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 * Filter configured based on the input jdeps option
 * 1. -p and -regex to match target dependencies
 * 2. -filter:package to filter out same-package dependencies
 *    This filter is applied when jdeps parses the class files
 *    and filtered dependencies are not stored in the Analyzer.
 * 3. -module specifies to match target dependencies from the given module
 *    This gets expanded into package lists to be filtered.
 * 4. -filter:archive to filter out same-archive dependencies
 *    This filter is applied later in the Analyzer as the
 *    containing archive of a target class may not be known until
 *    the entire archive
 */
class JdepsFilter implements Dependency.Filter, Analyzer.Filter {
    private final Dependency.Filter filter;
    private final Pattern filterPattern;
    private final boolean filterSamePackage;
    private final boolean filterSameArchive;
    private final boolean findJDKInternals;
    private final Pattern includePattern;
    private final Set<String> includePackages;
    private final Set<String> excludeModules;

    private JdepsFilter(Dependency.Filter filter,
                        Pattern filterPattern,
                        boolean filterSamePackage,
                        boolean filterSameArchive,
                        boolean findJDKInternals,
                        Pattern includePattern,
                        Set<String> includePackages,
                        Set<String> excludeModules) {
        this.filter = filter;
        this.filterPattern = filterPattern;
        this.filterSamePackage = filterSamePackage;
        this.filterSameArchive = filterSameArchive;
        this.findJDKInternals = findJDKInternals;
        this.includePattern = includePattern;
        this.includePackages = includePackages;
        this.excludeModules = excludeModules;
    }

    /**
     * Tests if the given class matches the pattern given in the -include option
     *
     * @param cn fully-qualified name
     */
    public boolean matches(String cn) {
        if (includePackages.isEmpty() && includePattern == null)
            return true;

        int i = cn.lastIndexOf('.');
        String pn = i > 0 ? cn.substring(0, i) : "";
        if (includePackages.contains(pn))
            return true;

        if (includePattern != null)
            return includePattern.matcher(cn).matches();

        return false;
    }

    /**
     * Tests if the given source includes classes specified in includePattern
     * or includePackages filters.
     *
     * This method can be used to determine if the given source should eagerly
     * be processed.
     */
    public boolean matches(Archive source) {
        if (!includePackages.isEmpty() && source.getModule().isNamed()) {
            boolean found = source.getModule().packages()
                                  .stream()
                                  .filter(pn -> includePackages.contains(pn))
                                  .findAny().isPresent();
            if (found)
                return true;
        }
        if (!includePackages.isEmpty() || includePattern != null) {
            return source.reader().entries()
                         .stream()
                         .map(name -> name.replace('/', '.'))
                         .filter(this::matches)
                         .findAny().isPresent();
        }
        return false;
    }

    // ----- Dependency.Filter -----

    @Override
    public boolean accepts(Dependency d) {
        if (d.getOrigin().equals(d.getTarget()))
            return false;

        // filter same package dependency
        String pn = d.getTarget().getPackageName();
        if (filterSamePackage && d.getOrigin().getPackageName().equals(pn)) {
            return false;
        }

        // filter if the target package matches the given filter
        if (filterPattern != null && filterPattern.matcher(pn).matches()) {
            return false;
        }

        // filter if the target matches the given filtered package name or regex
        return filter != null ? filter.accepts(d) : true;
    }

    // ----- Analyzer.Filter ------

    /**
     * Filter depending on the containing archive or module
     */
    @Override
    public boolean accepts(Location origin, Archive originArchive,
                           Location target, Archive targetArchive) {
        if (findJDKInternals) {
            // accepts target that is JDK class but not exported
            Module module = targetArchive.getModule();
            return originArchive != targetArchive &&
                    module.isJDK() && !module.isExported(target.getPackageName());
        } else if (filterSameArchive) {
            // accepts origin and target that from different archive
            return originArchive != targetArchive;
        }
        return true;
    }

    /**
     * Returns true if dependency should be recorded for the given source.
     */
    public boolean accept(Archive source) {
        return !excludeModules.contains(source.getName());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("exclude modules: ")
          .append(excludeModules.stream().sorted().collect(Collectors.joining(",")))
          .append("\n");
        sb.append("filter same archive: ").append(filterSameArchive).append("\n");
        sb.append("filter same package: ").append(filterSamePackage).append("\n");
        return sb.toString();
    }

    static class Builder {
        Dependency.Filter filter;
        Pattern filterPattern;
        boolean filterSamePackage;
        boolean filterSameArchive;
        boolean findJDKInterals;
        // source filters
        Pattern includePattern;
        Set<String> includePackages = new HashSet<>();
        Set<String> includeModules = new HashSet<>();
        Set<String> excludeModules = new HashSet<>();

        public Builder packages(Set<String> packageNames) {
            this.filter = Dependencies.getPackageFilter(packageNames, false);
            return this;
        }
        public Builder regex(Pattern regex) {
            this.filter = Dependencies.getRegexFilter(regex);
            return this;
        }
        public Builder filter(Pattern regex) {
            this.filterPattern = regex;
            return this;
        }
        public Builder filter(boolean samePackage, boolean sameArchive) {
            this.filterSamePackage = samePackage;
            this.filterSameArchive = sameArchive;
            return this;
        }
        public Builder findJDKInternals(boolean value) {
            this.findJDKInterals = value;
            return this;
        }
        public Builder includePattern(Pattern regex) {
            this.includePattern = regex;
            return this;
        }
        public Builder includePackage(String pn) {
            this.includePackages.add(pn);
            return this;
        }
        public Builder includeModules(Set<String> includes) {
            this.includeModules.addAll(includes);
            return this;
        }
        public Builder excludeModules(Set<String> excludes) {
            this.excludeModules.addAll(excludes);
            return this;
        }

        JdepsFilter build() {
            return new JdepsFilter(filter,
                                   filterPattern,
                                   filterSamePackage,
                                   filterSameArchive,
                                   findJDKInterals,
                                   includePattern,
                                   includePackages,
                                   excludeModules.stream()
                                        .filter(mn -> !includeModules.contains(mn))
                                        .collect(Collectors.toSet()));
        }

    }
}
