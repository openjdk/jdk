/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.network.testvm.java;

import compiler.lib.ir_framework.test.network.MessageTag;

import java.util.List;

/**
 * Class to collect all Java Messages sent with tag {@link MessageTag#TEST_LIST}. These are only generated when the
 * user runs with {@code -DTest=myTest} and represent the executed tests.
 */
class ExecutedTests implements JavaMessage {
    private final List<String> tests;

    public ExecutedTests(List<String> tests) {
        this.tests = tests;
    }

    @Override
    public void print() {
        if (tests.isEmpty()) {
            return;
        }

        System.out.println();
        System.out.println("Executed Subset of Tests");
        System.out.println("------------------------");
        for (String test : tests) {
            System.out.println("- " + test);
        }
        System.out.println();
    }
}
