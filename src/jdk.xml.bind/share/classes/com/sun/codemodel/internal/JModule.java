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

package com.sun.codemodel.internal;

// Based on modules grammar from http://openjdk.java.net/projects/jigsaw/doc/lang-vm.html

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Represents a Java module.
 * @author Tomas Kraus
 */
public class JModule {

    /** Java module file name. */
    private static final String FILE_NAME = "module-info.java";

    /** Name of this module. Mandatory value. Shall not be {@code null}. */
    private final String name;

    /** {@link Set} of Java module directives. */
    private final Set<JModuleDirective> directives;

    /**
     * Creates an instance of Java module.
     * @param name Java module name. Value can not be {@code null}
     * @param version Java module version.
     */
    JModule(final String name) {
        if (name == null) {
            throw new IllegalArgumentException("Value of name is null");
        }
        this.name = name;
        this.directives = new HashSet<>();
    }

    /**
     * Gets the name of this module.
     * @return name of this module.
     */
    public String name() {
        return name;
    }

    /**
     * Gets module directives set.
     * jUnit helper method.
     * @return Module directives set.
     */
    Set<JModuleDirective> getDirectives() {
        return directives;
    }

    /**
     * Adds a package to the list of Java module exports.
     * The package name shall not be {@code null} or empty {@code String}.
     * @param pkg Java package to be exported.
     */
    public void _exports(final JPackage pkg) {
        directives.add(new JExportsDirective(pkg.name()));
    }

    /**
     * Adds packages to the list of Java module exports.
     * @param pkgs Collection of packages to be added.
     * @param addEmpty Adds also packages without any classes when {@code true}.
     */
    public void _exports(final Collection<JPackage> pkgs, final boolean addEmpty) {
        for (Iterator<JPackage> i = pkgs.iterator(); i.hasNext();) {
            final JPackage pkg = i.next();
            if (addEmpty || pkg.hasClasses()) {
                _exports(pkg);
            }
        }
    }

    /**
     * Adds a module to the list of Java module requirements.
     * The module name shall not be {@code null} or empty {@code String}.
     * @param name Name of required Java module.
     * @param isPublic Use {@code public} modifier.
     * @param isStatic Use {@code static} modifier.
     */
    public void _requires(final String name, final boolean isPublic, final boolean isStatic) {
        directives.add(new JRequiresDirective(name, isPublic, isStatic));
    }

    /**
     * Adds a module to the list of Java module requirements without {@code public} and {@code static} modifiers.
     * The module name shall not be {@code null} or empty {@code String}.
     * @param name Name of required Java module.
     */
    public void _requires(final String name) {
        directives.add(new JRequiresDirective(name, false, false));
    }

    /**
     * Adds all modules to the list of Java module requirements.
     * The module name shall not be {@code null} or empty {@code String}.
     * @param names Names of required Java module.
     * @param isPublic Use {@code public} modifier.
     * @param isStatic Use {@code static} modifier.
     */
    public void _requires(final boolean isPublic, final boolean isStatic, final String ...names) {
        if (names != null) {
            for (final String reqName : names) {
                _requires(reqName, isPublic, isStatic);
            }
        }
    }

    /**
     * Adds all modules to the list of Java module requirements without {@code public} and {@code static} modifiers.
     * @param names Names of required Java module.
     */
    public void _requires(final String ...names) {
        _requires(false, false, names);
    }

    /**
     * Print source code of Java Module declaration.
     * @param f Java code formatter.
     * @return provided instance of Java code formatter.
     */
    public JFormatter generate(final JFormatter f) {
        f.p("module").p(name);
        f.p('{').nl();
        if (!directives.isEmpty()) {
            f.i();
            for (final JModuleDirective directive : directives) {
                directive.generate(f);
            }
            f.o();
        }
        f.p('}').nl();
        return f;
    }

    /**
     * Create {@code module-info.java} source writer.
     * @return New instance of {@code module-info.java} source writer.
     */
    private JFormatter createModuleInfoSourceFileWriter(final CodeWriter src) throws IOException {
        Writer bw = new BufferedWriter(src.openSource(null, FILE_NAME));
        return new JFormatter(new PrintWriter(bw));
    }

    /**
     * Build {@code module-info.java} source file.
     * @param src Source code writer.
     * @throws IOException if there is any problem with writing the file.
     */
    void build(final CodeWriter src) throws IOException {
        final JFormatter f = createModuleInfoSourceFileWriter(src);
        generate(f);
        f.close();
    }

}
