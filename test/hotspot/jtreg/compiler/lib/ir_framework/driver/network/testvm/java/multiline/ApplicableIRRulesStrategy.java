/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.network.testvm.java.multiline;

import compiler.lib.ir_framework.driver.network.testvm.java.ApplicableIRRules;
import compiler.lib.ir_framework.driver.network.testvm.java.IRRuleIds;
import compiler.lib.ir_framework.shared.TestFrameworkException;
import compiler.lib.ir_framework.test.ApplicableIRRulesPrinter;

import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated strategy to parse the multi-line Applicable IR Rules message into a new {@link ApplicableIRRules} object.
 */
public class ApplicableIRRulesStrategy implements MultiLineParsingStrategy<ApplicableIRRules> {
    private final ApplicableIRRules applicableIrRules;

    public ApplicableIRRulesStrategy() {
        this.applicableIrRules = new ApplicableIRRules();
    }

    @Override
    public void parseLine(String line) {
        if (line.equals(ApplicableIRRulesPrinter.NO_RULES)) {
            return;
        }

        String[] splitLine = line.split(",");
        if (splitLine.length < 2) {
            throw new TestFrameworkException("Invalid Applicable IR Rules format. No comma found: " + splitLine[0]);
        }
        String testName = splitLine[0];
        IRRuleIds irRulesIds = parseIrRulesIds(splitLine);
        applicableIrRules.add(testName, irRulesIds);
    }

    /**
     * Parse rule indexes from a single line of the Applicable IR Rules in the format: <method,idx1,idx2,...>
     */
    private IRRuleIds parseIrRulesIds(String[] splitLine) {
        List<Integer> irRuleIds = new ArrayList<>();
        for (int i = 1; i < splitLine.length; i++) {
            try {
                irRuleIds.add(Integer.parseInt(splitLine[i]));
            } catch (NumberFormatException e) {
                throw new TestFrameworkException("Invalid Applicable IR Rules format. No number found: " + splitLine[i]);
            }
        }
        return new IRRuleIds(irRuleIds);
    }

    @Override
    public ApplicableIRRules output() {
        return applicableIrRules;
    }
}
