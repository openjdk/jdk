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
import java.util.Map;


/**
 * Class to collect all Java Messages sent with tag {@link MessageTag#PRINT_TIMES}. These are only generated when the
 * user runs with {@code -DPrintTimes=true} and represent the execution times for methods.
 */
class MethodTimes implements JavaMessage {
    private final Map<String, Long> methodTimes;

    public MethodTimes(Map<String, Long> methodTimes) {
        this.methodTimes = methodTimes;
    }

    @Override
    public void print() {
        if (methodTimes.isEmpty()) {
            return;
        }

        System.out.println();
        System.out.println("Test Execution Times");
        System.out.println("--------------------");

        int maxWidthNames = maxMethodNameWidth();
        int maxDurationsWidth = maxDurationsWidth();
        List<Map.Entry<String, Long>>  sortedMethodTimes = sortByDurationAsc();

        for (Map.Entry<String, Long> entry : sortedMethodTimes) {
            System.out.printf("- %-" + (maxWidthNames + 3) + "s %" + maxDurationsWidth + "d ns%n",
                              entry.getKey() + ":", entry.getValue());
        }

        System.out.println();
    }

    private int maxMethodNameWidth() {
        return methodTimes.keySet().stream()
                .mapToInt(String::length)
                .max()
                .orElseThrow();
    }

    private int maxDurationsWidth() {
        return methodTimes.values().stream()
                .mapToInt(v -> Long.toString(v).length())
                .max()
                .orElseThrow();
    }

    private List<Map.Entry<String, Long>> sortByDurationAsc() {
        return methodTimes.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .toList();
    }

}
