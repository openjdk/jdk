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
package com.oracle.java.testlibrary;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;
import java.lang.management.ManagementFactory;

/**
 * Simple class to check writeability, invalid and valid values for VMOption
 */
public class DynamicVMOptionChecker {

    /**
     * Reads VM option from PlatformMXBean and parse it to integer value
     *
     * @param name of option
     * @return parsed value
     */
    public static int getIntValue(String name) {

        VMOption option = ManagementFactory.
                getPlatformMXBean(HotSpotDiagnosticMXBean.class).
                getVMOption(name);

        return Integer.parseInt(option.getValue());
    }

    /**
     * Sets VM option value
     *
     * @param name of option
     * @param value to set
     */
    public static void setIntValue(String name, int value) {
        ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class).setVMOption(name, Integer.toString(value));
    }

    /**
     * Checks that VM option is dynamically writable
     *
     * @param name
     * @throws RuntimeException if option if not writable
     * @return always true
     */
    public static boolean checkIsWritable(String name) {
        VMOption option = ManagementFactory.
                getPlatformMXBean(HotSpotDiagnosticMXBean.class).
                getVMOption(name);

        if (!option.isWriteable()) {
            throw new RuntimeException(name + " is not writable");
        }

        return true;
    }

    /**
     * Checks that value cannot be set
     *
     * @param name of flag
     * @param value string representation of value to set
     * @throws RuntimeException on error - when expected exception hasn't been thrown
     */
    public static void checkInvalidValue(String name, String value) {
        // should throw
        try {
            ManagementFactory.
                    getPlatformMXBean(HotSpotDiagnosticMXBean.class).
                    setVMOption(name, value);

        } catch (IllegalArgumentException e) {
            return;
        }

        throw new RuntimeException("Expected IllegalArgumentException was not thrown, " + name + "= " + value);
    }

    /**
     * Checks that value can be set
     *
     * @param name of flag to set
     * @param value string representation of value to set
     * @throws RuntimeException on error - when value in VM is not equal to origin
     */
    public static void checkValidValue(String name, String value) {
        ManagementFactory.
                getPlatformMXBean(HotSpotDiagnosticMXBean.class).
                setVMOption(name, value);

        VMOption option = ManagementFactory.
                getPlatformMXBean(HotSpotDiagnosticMXBean.class).
                getVMOption(name);

        if (!option.getValue().equals(value)) {
            throw new RuntimeException("Actual value of " + name + " \"" + option.getValue()
                    + "\" not equal origin \"" + value + "\"");
        }
    }

}
