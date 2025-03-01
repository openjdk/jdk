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

package compiler.lib.test_generator;
import java.util.Objects;

/**
 * Represents a segment of code, including static declarations, method calls, methods, and import statements.
 */
public final class CodeSegment {
    private final String statics;
    private final StringBuilder calls;
    private final StringBuilder methods;
    private final String imports;

    /**
     * Constructs a CodeSegment with specified components.
     *
     * @param statics  Static declarations.
     * @param calls    Initial method calls.
     * @param methods  Initial methods.
     * @param imports  Import statements.
     * @throws NullPointerException if statics or imports are null.
     */
    public CodeSegment(String statics, String calls, String methods, String imports) {
        this.statics = Objects.requireNonNull(statics, "statics cannot be null");
        this.calls = new StringBuilder(calls != null ? calls : "");
        this.methods = new StringBuilder(methods != null ? methods : "");
        this.imports = Objects.requireNonNull(imports, "imports cannot be null");
    }

    /**
     * Retrieves the static declarations.
     *
     * @return Static declarations as a String.
     */
    public String getStatics() {
        return statics;
    }

    /**
     * Appends additional method calls.
     *
     * @param additionalCalls Method calls to append.
     */
    public void appendCalls(String additionalCalls) {
        if (additionalCalls != null && !additionalCalls.isEmpty()) {
            this.calls.append(additionalCalls);
        }
    }

    /**
     * Retrieves the current method calls.
     *
     * @return Method calls as a String.
     */
    public String getCalls() {
        return calls.toString();
    }

    /**
     * Appends an additional method.
     *
     * @param additionalMethod Method to append.
     */
    public void appendMethods(String additionalMethod) {
        if (additionalMethod != null && !additionalMethod.isEmpty()) {
            this.methods.append(additionalMethod);
        }
    }

    /**
     * Retrieves the current methods.
     *
     * @return Methods as a String.
     */
    public String getMethods() {
        return methods.toString();
    }

    /**
     * Retrieves the import statements.
     *
     * @return Import statements as a String.
     */
    public String getImports() {
        return imports;
    }
}
