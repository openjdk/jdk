/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;

public class HotSpotSpeculationLog implements SpeculationLog {

    /** Written by the C++ code that performs deoptimization. */
    private volatile Object lastFailed;

    /** All speculations that have been a deoptimization reason. */
    private Set<SpeculationReason> failedSpeculations;

    /** Strong references to all reasons embededded in the current nmethod. */
    private volatile Collection<SpeculationReason> speculations;

    @Override
    public synchronized void collectFailedSpeculations() {
        if (lastFailed != null) {
            if (failedSpeculations == null) {
                failedSpeculations = new HashSet<>(2);
            }
            failedSpeculations.add((SpeculationReason) lastFailed);
            lastFailed = null;
            speculations = null;
        }
    }

    @Override
    public boolean maySpeculate(SpeculationReason reason) {
        if (failedSpeculations != null && failedSpeculations.contains(reason)) {
            return false;
        }
        return true;
    }

    @Override
    public JavaConstant speculate(SpeculationReason reason) {
        assert maySpeculate(reason);

        /*
         * Objects referenced from nmethods are weak references. We need a strong reference to the
         * reason objects that are embedded in nmethods, so we add them to the speculations
         * collection.
         */
        if (speculations == null) {
            synchronized (this) {
                if (speculations == null) {
                    speculations = new ConcurrentLinkedQueue<>();
                }
            }
        }
        speculations.add(reason);

        return HotSpotObjectConstantImpl.forObject(reason);
    }
}
