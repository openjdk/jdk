/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.meta;

/**
 * Manages unique deoptimization reasons. Reasons are embedded in compiled code and can be
 * invalidated at run time. Subsequent compilations then should not speculate again on such
 * invalidated reasons to avoid repeated deoptimization.
 *
 * All methods of this interface are called by the compiler. There is no need for API to register
 * failed speculations during deoptimization, since every VM has different needs there.
 */
public interface SpeculationLog {

    /**
     * Marker interface for speculation objects that can be added to the speculation log.
     */
    public interface SpeculationReason {
    }

    /**
     * Must be called before compilation, i.e., before a compiler calls {@link #maySpeculate}.
     */
    void collectFailedSpeculations();

    /**
     * If this method returns true, the compiler is allowed to {@link #speculate} with the given
     * reason.
     */
    boolean maySpeculate(SpeculationReason reason);

    /**
     * Registers a speculation that was performed by the compiler.
     *
     * @return A compiler constant encapsulating the provided reason. It is usually passed as an
     *         argument to the deoptimization function.
     */
    JavaConstant speculate(SpeculationReason reason);
}
