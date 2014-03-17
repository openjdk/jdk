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

/**
 * Simple class to check writeability, invalid and valid values for concrete VMOption
 */
public class TestDynamicVMOption {

    private final String name;
    private final int value;

    /**
     * Constructor
     *
     * @param name of VM option to test
     */
    public TestDynamicVMOption(String name) {
        this.name = name;
        this.value = DynamicVMOptionChecker.getIntValue(name);
        System.out.println(this.name + " = " + this.value);
    }

    /**
     * Checks that this value can accept valid percentage values and cannot accept invalid percentage values
     *
     * @throws RuntimeException
     */
    public void testPercentageValues() {
        checkInvalidValue(Integer.toString(Integer.MIN_VALUE));
        checkInvalidValue(Integer.toString(Integer.MAX_VALUE));
        checkInvalidValue("-10");
        checkInvalidValue("190");
    }

    /**
     * Reads VM option from PlatformMXBean and parse it to integer value
     *
     * @return value
     */
    public int getIntValue() {
        return DynamicVMOptionChecker.getIntValue(this.name);
    }

    /**
     * Sets VM option value
     *
     * @param value to set
     */
    public void setIntValue(int value) {
        DynamicVMOptionChecker.setIntValue(this.name, value);
    }

    /**
     * Checks that this VM option is dynamically writable
     *
     * @throws RuntimeException if option if not writable
     * @return true
     */
    public boolean checkIsWritable() throws RuntimeException {
        return DynamicVMOptionChecker.checkIsWritable(this.name);
    }

    /**
     * Checks that value for this VM option cannot be set
     *
     * @param value to check
     * @throws RuntimeException on error - when expected exception hasn't been thrown
     */
    public void checkInvalidValue(String value) {
        DynamicVMOptionChecker.checkInvalidValue(this.name, value);
    }

    /**
     * Checks that value for this VM option can be set
     *
     * @param value to check
     * @throws RuntimeException on error - when value in VM is not equal to origin
     */
    public void checkValidValue(String value) {
        DynamicVMOptionChecker.checkValidValue(this.name, value);
    }

}
