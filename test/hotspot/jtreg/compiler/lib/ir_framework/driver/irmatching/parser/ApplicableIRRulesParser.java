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

package compiler.lib.ir_framework.driver.irmatching.parser;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.driver.network.testvm.java.ApplicableIRRules;
import compiler.lib.ir_framework.driver.network.testvm.java.IRRuleIds;
import compiler.lib.ir_framework.shared.TestFormat;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Class to parse the Applicable IR Rules emitted by the Test VM and creating {@link TestMethod} objects for each entry.
 *
 * @see TestMethod
 */
public class ApplicableIRRulesParser {
    private final Map<String, TestMethod> testMethods;
    private final Class<?> testClass;

    public ApplicableIRRulesParser(Class<?> testClass) {
        this.testClass = testClass;
        this.testMethods = new HashMap<>();
    }

    /**
     * Parse the Applicable IR rules passed as parameter and return a "test name" -> TestMethod map that contains an
     * entry for each method that needs to be IR matched on.
     */
    public TestMethods parse(ApplicableIRRules applicableIRRules) {
        createTestMethodMap(applicableIRRules);
        // We could have found format errors in @IR annotations. Report them now with an exception.
        TestFormat.throwIfAnyFailures();
        return new TestMethods(testMethods);
    }

    private void createTestMethodMap(ApplicableIRRules applicableIRRules) {
        for (Method m : testClass.getDeclaredMethods()) {
            IR[] irAnnos = m.getAnnotationsByType(IR.class);
            if (irAnnos.length > 0) {
                // Validation of legal @IR attributes and placement of the annotation was already done in Test VM.
                IRRuleIds irRuleIds = applicableIRRules.ruleIds(m.getName());
                validateIRRuleIds(m, irAnnos, irRuleIds);
                if (hasAnyApplicableIRRules(irRuleIds)) {
                    testMethods.put(m.getName(), new TestMethod(m, irAnnos, irRuleIds));
                }
            }
        }
    }

    private void validateIRRuleIds(Method m, IR[] irAnnos, IRRuleIds irRuleIds) {
        TestFramework.check((irRuleIds.isEmpty() || (irRuleIds.first() >= 1 && irRuleIds.last() <= irAnnos.length)),
                            "Invalid IR rule index found in validIrRulesMap for " + m);
    }

    /**
     * Does the list of IR rules contain any applicable IR rules for the given conditions?
     */
    private boolean hasAnyApplicableIRRules(IRRuleIds irRuleIds) {
        return !irRuleIds.isEmpty();
    }
}
