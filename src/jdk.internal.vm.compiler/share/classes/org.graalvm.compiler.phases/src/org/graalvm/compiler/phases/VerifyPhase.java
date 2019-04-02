/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/***
 * Verifies a {@linkplain #verify graph} or {@linkplain #verifyClass class} against one or more
 * invariants.
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
        verify(graph, context);
    }

    /**
     * Checks {@code graph} against some invariants.
     *
     * @throws VerificationError if the verification fails
     */
    protected abstract void verify(StructuredGraph graph, C context);

    /**
     * Checks {@code clazz} against some invariants.
     *
     * @param clazz the class to verify
     * @param metaAccess an object to get a {@link ResolvedJavaType} for {@code clazz}
     * @throws VerificationError if the class violates some invariant
     */
    public void verifyClass(Class<?> clazz, MetaAccessProvider metaAccess) {
    }
}
