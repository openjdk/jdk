/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.common;

import jdk.vm.ci.code.CompilationRequest;

/**
 * A unique identifier for a compilation. Compiled code can be mapped to a single compilation id.
 * The reverse is not true since the compiler might bailout in which case no code is installed.
 */
public interface CompilationIdentifier {

    enum Verbosity {
        /**
         * Only the unique identifier of the compilation.
         */
        ID,
        /**
         * Only the name of the compilation unit.
         */
        NAME,
        /**
         * {@link #ID} + a readable description.
         */
        DETAILED
    }

    CompilationRequestIdentifier INVALID_COMPILATION_ID = new CompilationRequestIdentifier() {

        @Override
        public String toString() {
            return toString(Verbosity.DETAILED);
        }

        @Override
        public String toString(Verbosity verbosity) {
            return "InvalidCompilationID";
        }

        @Override
        public CompilationRequest getRequest() {
            return null;
        }

    };

    /**
     * This method is a shortcut for {@link #toString(Verbosity)} with {@link Verbosity#DETAILED}.
     */
    @Override
    String toString();

    /**
     * Creates a String representation for this compilation identifier with a given
     * {@link Verbosity}.
     */
    String toString(Verbosity verbosity);
}
