/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.java.testlibrary.cli;

import sun.hotspot.cpuinfo.CPUInfo;
import com.oracle.java.testlibrary.*;

/**
 * Base class for command line options tests that
 * requires specific CPU arch or specific CPU features.
 */
public abstract class CPUSpecificCommandLineOptionTest
              extends CommandLineOptionTest {

    private String cpuArchPattern;
    private String supportedCPUFeatures[];
    private String unsupportedCPUFeatures[];

    /**
     * Create new CPU specific test instance that does not
     * require any CPU features.
     *
     * @param cpuArchPattern Regular expression that should
     *                       match os.arch.
     */
    public CPUSpecificCommandLineOptionTest(String cpuArchPattern) {
        this(cpuArchPattern, null, null);
    }

    /**
     * Create new CPU specific test instance that does not
     * require from CPU support of {@code supportedCPUFeatures} features
     * and no support of {@code unsupportedCPUFeatures}.
     *
     * @param cpuArchPattern Regular expression that should
     *                       match os.arch.
     * @param supportedCPUFeatures Array with names of features that
     *                             should be supported by CPU. If <b>null</b>,
     *                             then no features have to be supported.
     * @param unsupportedCPUFeatures Array with names of features that
     *                               should not be supported by CPU.
     *                               If <b>null</b>, then CPU may support any
     *                               features.
     */
    public CPUSpecificCommandLineOptionTest(String cpuArchPattern,
                                            String supportedCPUFeatures[],
                                            String unsupportedCPUFeatures[]) {
        this.cpuArchPattern = cpuArchPattern;
        this.supportedCPUFeatures = supportedCPUFeatures;
        this.unsupportedCPUFeatures = unsupportedCPUFeatures;
    }

    /**
     * Check that CPU on test box has appropriate architecture, support all
     * required features and does not support all features that should not be
     * supported.
     *
     * @return <b>true</b> if CPU on test box fulfill all requirements.
     */
    @Override
    public boolean checkPreconditions() {
        if (!Platform.getOsArch().matches(cpuArchPattern)) {
            System.out.println("CPU arch does not match " + cpuArchPattern);
            return false;
        }

        if (supportedCPUFeatures != null) {
            for (String feature : supportedCPUFeatures) {
                if (!CPUInfo.hasFeature(feature)) {
                    System.out.println("CPU does not support " + feature +
                                       " feature");
                    return false;
                }
            }
        }

        if (unsupportedCPUFeatures != null) {
            for (String feature : unsupportedCPUFeatures) {
                if (CPUInfo.hasFeature(feature)) {
                    System.out.println("CPU support " + feature + " feature");
                    return false;
                }
            }
        }

        return true;
    }
}

