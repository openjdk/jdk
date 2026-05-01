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
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.parser.hotspot.LoggedMethod;
import compiler.lib.ir_framework.driver.network.testvm.java.IRRuleIds;

import java.lang.reflect.Method;

/**
 * This class represents a test method parsed by {@link ApplicableIRRulesParser}. In combination with the associated
 * {@link LoggedMethod}, a new {@link IRMethod} is created to IR match on later.
 *
 * @see ApplicableIRRulesParser
 * @see LoggedMethod
 * @see IRMethod
 */
public class TestMethod {
    private final Method method;
    private final IR[] irAnnos;
    private final IRRuleIds irRuleIds;

    public TestMethod(Method m, IR[] irAnnos, IRRuleIds irRuleIds) {
        this.method = m;
        this.irAnnos = irAnnos;
        this.irRuleIds = irRuleIds;
    }

    public Method method() {
        return method;
    }

    public IR[] irAnnos() {
        return irAnnos;
    }

    public IRRuleIds irRuleIds() {
        return irRuleIds;
    }
}
