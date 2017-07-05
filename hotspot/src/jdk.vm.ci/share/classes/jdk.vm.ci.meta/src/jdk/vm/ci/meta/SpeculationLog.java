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

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages a list of unique deoptimization reasons.
 *
 */
public abstract class SpeculationLog {
    private volatile Object lastFailed;
    private volatile Collection<Object> speculations;
    private Set<Object> failedSpeculations;

    public synchronized void collectFailedSpeculations() {
        if (lastFailed != null) {
            if (failedSpeculations == null) {
                failedSpeculations = new HashSet<>(2);
            }
            failedSpeculations.add(lastFailed);
            lastFailed = null;
            speculations = null;
        }
    }

    public boolean maySpeculate(Object reason) {
        if (failedSpeculations != null && failedSpeculations.contains(reason)) {
            return false;
        }
        return true;
    }

    protected void addSpeculation(Object reason) {
        assert maySpeculate(reason);
        if (speculations == null) {
            synchronized (this) {
                if (speculations == null) {
                    speculations = new ConcurrentLinkedQueue<>();
                }
            }
        }
        speculations.add(reason);
    }

    public abstract JavaConstant speculate(Object reason);
}
