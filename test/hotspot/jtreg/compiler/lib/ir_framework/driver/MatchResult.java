/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver;

import java.util.List;

/**
 * Class used to store an IR matching result of an IR rule.
 *
 * @see Failure
 * @see IRRule
 */
class MatchResult {
    private final IRRule irRule;
    private List<? extends Failure> failOnFailures = null;
    private List<? extends Failure> countsFailures = null;
    private boolean idealMatch = false;
    private boolean optoAssemblyMatch = false;

    public MatchResult(IRRule irRule) {
        this.irRule = irRule;
    }

    public IRRule getIRRule() {
        return irRule;
    }

    public boolean isIdealMatch() {
        return idealMatch;
    }

    public void setIdealMatch() {
        this.idealMatch = true;
    }

    public boolean isOptoAssemblyMatch() {
        return optoAssemblyMatch;
    }

    public void setOptoAssemblyMatch() {
        this.optoAssemblyMatch = true;
    }


    public boolean hasFailOnFailures() {
        return failOnFailures != null;
    }

    public List<? extends Failure> getFailOnFailures() {
        return failOnFailures;
    }

    public void addFailOnFailures(List<? extends Failure> failOnFailures) {
        this.failOnFailures = failOnFailures;
    }

    public boolean hasCountsFailures() {
        return countsFailures != null;
    }

    public List<? extends Failure> getCountsFailures() {
        return countsFailures;
    }

    public void setCountsFailures(List<? extends Failure> countsFailures) {
        this.countsFailures = countsFailures;
    }

    /**
     * Does this result represent a failure?
     */
    public boolean fail() {
        return failOnFailures != null || countsFailures != null;
    }
}
