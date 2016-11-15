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

// TODO: Implement "[to ModuleName {, ModuleName}]".
// Only minimal form of exports directive is needed now so it was not implemented in full form.
/**
 * Represents a Java module {@code exports} directive.
 * For example {@code "exports foo.bar;"}.
 * @author Tomas Kraus
 */

public class JExportsDirective extends JModuleDirective {

    /**
     * Creates an instance of Java module {@code exports} directive.
     * @param name name of package to be exported in this directive.
     * @throws IllegalArgumentException if the name argument is {@code null}.
     */
    JExportsDirective(final String name) {
        super(name);
    }

    /**
     * Gets the type of this module directive.
     * @return type of this module directive. Will always return {@code Type.ExportsDirective}.
     */
    @Override
    public Type getType() {
        return Type.ExportsDirective;
    }

    /**
     * Print source code of this module directive.
     * @param f Java code formatter.
     * @return provided instance of Java code formatter.
     */
    @Override
    public JFormatter generate(final JFormatter f) {
        f.p("exports").p(name).p(';').nl();
        return f;
    }

}
