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

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import sun.hotspot.WhiteBox;
import sun.management.*;
import com.sun.management.*;
import com.oracle.java.testlibrary.*;

public final class VmFlagTest<T> {
    public static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    private static final String NONEXISTENT_FLAG = "NonexistentFlag";
    private final String flagName;
    private final BiConsumer<T, T> test;
    private final BiConsumer<String, T> set;
    private final Function<String, T> get;

    protected VmFlagTest(String flagName, BiConsumer<String, T> set,
            Function<String, T> get, boolean isPositive) {
        this.flagName = flagName;
        this.set = set;
        this.get = get;
        if (isPositive) {
            test = this::testPositive;
        } else {
            test = this::testNegative;
        }
    }

    private void setNewValue(T value) {
        set.accept(flagName, value);
    }

    private T getValue() {
        T t = get.apply(flagName);
        System.out.println("T = " + t);
        return t;
    }

    protected static <T> void runTest(String existentFlag, T[] tests,
            BiConsumer<String, T> set, Function<String, T> get) {
        runTest(existentFlag, tests, tests, set, get);
    }

    protected static <T> void runTest(String existentFlag, T[] tests,
            T[] results, BiConsumer<String, T> set, Function<String, T> get) {
        if (existentFlag != null) {
            new VmFlagTest(existentFlag, set, get, true).test(tests, results);
        }
        new VmFlagTest(NONEXISTENT_FLAG, set, get, false).test(tests, results);
    }

    public final void test(T[] tests, T[] results) {
        Asserts.assertEQ(tests.length, results.length, "[TESTBUG] tests.length != results.length");
        for (int i = 0, n = tests.length ; i < n; ++i) {
            test.accept(tests[i], results[i]);
        }
    }

    protected String getVMOptionAsString() {
        HotSpotDiagnosticMXBean diagnostic
                = ManagementFactoryHelper.getDiagnosticMXBean();
        VMOption tmp;
        try {
            tmp = diagnostic.getVMOption(flagName);
        } catch (IllegalArgumentException e) {
            tmp = null;
        }
        return tmp == null ? null : tmp.getValue();
    }

    private void testPositive(T value, T expected) {
        Asserts.assertEQ(getVMOptionAsString(), asString(getValue()));
        setNewValue(value);
        String newValue = getVMOptionAsString();
        Asserts.assertEQ(newValue, asString(expected));
        Asserts.assertEQ(getVMOptionAsString(), asString(getValue()));
    }

    private void testNegative(T value, T expected) {
        String oldValue = getVMOptionAsString();
        Asserts.assertEQ(oldValue, asString(getValue()));
        setNewValue(value);
        String newValue = getVMOptionAsString();
        Asserts.assertEQ(oldValue, newValue);
    }

    private String asString(Object value) {
        return value == null ? null : "" + value;
    }
}

