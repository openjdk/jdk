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

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.driver.irmatching.IRMatcher;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Class to hold the Applicable IR Rules sent by the Test VM. It specifies which {@link IR @IR} rules the
 * {@link IRMatcher} need to check. This can be different depending on the used VM flags or the machine the test is run
 * on itself.
 */
public class ApplicableIRRules implements JavaMessage {
    private static final boolean PRINT_APPLICABLE_IR_RULES =
            Boolean.parseBoolean(System.getProperty("PrintApplicableIRRules", "false"))
                    || TestFramework.VERBOSE;

    private final Map<String, IRRuleIds> methods;

    public ApplicableIRRules() {
        this.methods = new HashMap<>();
    }

    public void add(String method, IRRuleIds irRuleIds) {
        methods.put(method, irRuleIds);
    }

    public IRRuleIds ruleIds(String methodName) {
        return methods.computeIfAbsent(methodName, _ -> IRRuleIds.createEmpty());
    }

    public boolean hasNoMethods() {
        return methods.isEmpty();
    }

    @Override
    public void print() {
        if (!PRINT_APPLICABLE_IR_RULES) {
            return;
        }

        System.out.println();
        System.out.println("Applicable IR Rules");
        System.out.println("-------------------");
        for (var entry : methods.entrySet()) {
            String method = entry.getKey();
            String ruleIds = entry.getValue().stream().map(String::valueOf).collect(Collectors.joining(", "));
            System.out.println("- " + method + ": " + ruleIds);
        }
    }
}
