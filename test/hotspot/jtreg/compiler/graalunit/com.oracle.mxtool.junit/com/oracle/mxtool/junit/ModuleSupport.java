/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.mxtool.junit;

import java.io.PrintStream;
import java.util.Set;

/**
 * Interface to {@code java.lang.Module} related functionality.
 */
class ModuleSupport {

    /**
     * @param out stream to use for printing warnings
     */
    ModuleSupport(PrintStream out) {
    }

    /**
     * Exports and opens packages based on {@code spec}. See further documentation in
     * {@code mx_unittest.py}.
     *
     * @param spec
     * @param context
     * @param opened the set of opens performed are added to this set in the format
     *            {@code <module>/<package>=<target-module>(,<target-module>)*} (e.g.
     *            {@code "com.foo/com.foo.util=ALL-NAMED,com.bar"})
     * @param exported the set of exports performed are added to this set in the format
     *            {@code <module>/<package>=<target-module>(,<target-module>)*} (e.g.
     *            {@code "com.foo/com.foo.util=ALL-NAMED,com.bar"})
     */
    void openPackages(String spec, Object context, Set<String> opened, Set<String> exported) {
        // Nop on JDK 8
    }

    /**
     * Updates modules specified in {@code AddExport} annotations on {@code classes} to export and
     * open packages to the annotation classes' declaring modules.
     *
     * @param classes
     * @param opened the set of opens performed are added to this set in the format
     *            {@code <module>/<package>=<target-module>(,<target-module>)*} (e.g.
     *            {@code "com.foo/com.foo.util=ALL-NAMED,com.bar"})
     * @param exported the set of exports performed are added to this set in the format
     *            {@code <module>/<package>=<target-module>(,<target-module>)*} (e.g.
     *            {@code "com.foo/com.foo.util=ALL-NAMED,com.bar"})
     */
    void processAddExportsAnnotations(Set<Class<?>> classes, Set<String> opened, Set<String> exported) {
        // Nop on JDK 8
    }
}
