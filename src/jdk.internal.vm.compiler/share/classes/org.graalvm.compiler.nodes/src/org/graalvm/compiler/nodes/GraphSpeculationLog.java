/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import jdk.internal.vm.compiler.collections.EconomicMap;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * A wrapper around a {@link SpeculationLog} instance.
 *
 * This should be used when the wrapped instance may be accessed by multiple threads. Due to races,
 * such an instance can return true for a call to {@link SpeculationLog#maySpeculate} but still fail
 * (i.e. raise an {@link IllegalArgumentException}) when {@link SpeculationLog#speculate} is called.
 *
 * A {@link GraphSpeculationLog} must only be used by a single thread and is typically closely
 * coupled with a {@link StructuredGraph} (hence the name).
 */
public final class GraphSpeculationLog implements SpeculationLog {

    private final SpeculationLog log;
    private final EconomicMap<SpeculationReason, JavaConstant> speculations;

    public GraphSpeculationLog(SpeculationLog log) {
        this.log = log;
        this.speculations = EconomicMap.create();
    }

    /**
     * Unwraps {@code log} if it is a {@link GraphSpeculationLog}.
     */
    public static SpeculationLog unwrap(SpeculationLog log) {
        if (log instanceof GraphSpeculationLog) {
            return ((GraphSpeculationLog) log).log;
        }
        return log;
    }

    /**
     * Determines if the compiler is allowed to speculate with {@code reason}. Note that a
     * {@code true} return value guarantees that a subsequent call to
     * {@link #speculate(SpeculationReason)} with an argument {@linkplain Object#equals(Object)
     * equal} to {@code reason} will succeed.
     */
    @Override
    public boolean maySpeculate(SpeculationReason reason) {
        JavaConstant speculation = speculations.get(reason);
        if (speculation == null) {
            if (log.maySpeculate(reason)) {
                try {
                    speculation = log.speculate(reason);
                    speculations.put(reason, speculation);
                } catch (IllegalArgumentException e) {
                    // The speculation was disabled by another thread in between
                    // the call to log.maySpeculate and log.speculate
                    speculation = null;
                }
            }
        }
        return speculation != null;
    }

    @Override
    public JavaConstant speculate(SpeculationReason reason) {
        if (maySpeculate(reason)) {
            JavaConstant speculation = speculations.get(reason);
            assert speculation != null;
            return speculation;
        }
        throw new IllegalArgumentException("Cannot make speculation with reason " + reason + " as it is known to fail");
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GraphSpeculationLog) {
            GraphSpeculationLog that = (GraphSpeculationLog) obj;
            return this.log == that.log;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return log.hashCode();
    }

    @Override
    public void collectFailedSpeculations() {
        log.collectFailedSpeculations();
    }

    /**
     * Returns if this log has speculations.
     *
     * @return true if {@link #maySpeculate(SpeculationReason)} has ever returned {@code true} for
     *         this object
     */
    @Override
    public boolean hasSpeculations() {
        return !speculations.isEmpty();
    }
}
