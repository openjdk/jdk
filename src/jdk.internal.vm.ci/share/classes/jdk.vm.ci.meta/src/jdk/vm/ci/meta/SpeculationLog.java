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
     * Marker class that indicates that a speculation has no reason.
     */
    final class NoSpeculationReason implements SpeculationReason {
    }

    class Speculation {
        private SpeculationReason reason;

        public Speculation(SpeculationReason reason) {
            this.reason = reason;
        }

        public SpeculationReason getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return reason.toString();
        }
    }

    Speculation NO_SPECULATION = new Speculation(new NoSpeculationReason());

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
     * Registers a speculation performed by the compiler. The compiler must guard every call to this
     * method for a specific reason with a call to {@link #maySpeculate(SpeculationReason)}.
     *
     * This API is subject to a benign race where a during the course of a compilation another
     * thread might fail a speculation such that {@link #maySpeculate(SpeculationReason)} will
     * return false but an earlier call returned true. This method will still return a working
     * {@link Speculation} in that case but the compile will eventually be invalidated and the
     * compile attempted again without the now invalid speculation.
     *
     * @param reason an object representing the reason for the speculation
     * @return a compiler constant encapsulating the provided reason. It is usually passed as an
     *         argument to the deoptimization function.
     */
    Speculation speculate(SpeculationReason reason);

    /**
     * Returns if this log has speculations.
     *
     * @return true if there are speculations, false otherwise
     */
    boolean hasSpeculations();

    /**
     * Given a {@link JavaConstant} previously returned from
     * {@link MetaAccessProvider#encodeSpeculation(Speculation)} return the original
     * {@link Speculation} object.
     */
    Speculation lookupSpeculation(JavaConstant constant);
}
