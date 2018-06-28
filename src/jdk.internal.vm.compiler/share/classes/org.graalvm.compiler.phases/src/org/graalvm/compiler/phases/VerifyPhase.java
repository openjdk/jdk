/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.phases;

import org.graalvm.compiler.nodes.StructuredGraph;

/***
 * This phase serves as a verification, in order to check the graph for certain properties. The
 * {@link #verify(StructuredGraph, Object)} method will be used as an assertion, and implements the
 * actual check. Instead of returning false, it is also valid to throw an {@link VerificationError}
 * in the implemented {@link #verify(StructuredGraph, Object)} method.
 */
public abstract class VerifyPhase<C> extends BasePhase<C> {

    /**
     * Thrown when verification performed by a {@link VerifyPhase} fails.
     */
    @SuppressWarnings("serial")
    public static class VerificationError extends AssertionError {

        public VerificationError(String message) {
            super(message);
        }

        public VerificationError(String format, Object... args) {
            super(String.format(format, args));
        }

        public VerificationError(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @Override
    protected final void run(StructuredGraph graph, C context) {
        assert verify(graph, context);
    }

    /**
     * Performs the actual verification.
     *
     * @throws VerificationError if the verification fails
     */
    protected abstract boolean verify(StructuredGraph graph, C context);
}
