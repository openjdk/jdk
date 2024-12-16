/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.template_framework;

/**
 * {link BaseScope} is the outer-most scope, and determines the {@link CodeGeneratorLibrary} which is used.
 */
final class BaseScope extends Scope {
    public static final int DEFAULT_FUEL = 50;
    private final CodeGeneratorLibrary codeGeneratorLibrary;

    public BaseScope(long fuel, CodeGeneratorLibrary codeGeneratorLibrary) {
        super(null, fuel);
        this.codeGeneratorLibrary = (codeGeneratorLibrary != null) ? codeGeneratorLibrary
                                                                   : CodeGeneratorLibrary.standard();
    }

    public BaseScope(CodeGeneratorLibrary codeGeneratorLibrary) {
        this(DEFAULT_FUEL, codeGeneratorLibrary);
    };

    @Override
    public CodeGeneratorLibrary library() {
        return codeGeneratorLibrary;
    }

    /**
     * Collect all the generated code and return it as a String.
     */
    public String toString() {
        return stream.toString();
    }
}
