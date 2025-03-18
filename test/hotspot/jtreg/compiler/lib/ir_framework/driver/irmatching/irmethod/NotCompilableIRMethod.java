/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching.irmethod;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.RunMode;

import java.lang.reflect.Method;

/**
 * This class represents a special IR method which was not compiled by the IR framework, but this was explicitly allowed
 * by "allowNotCompilable". This happens when the compiler bails out of a compilation (i.e. no compilation) but we treat
 * this as valid case. Any associated IR rules pass silently.
 *
 * @see IR
 * @see Test
 */
public class NotCompilableIRMethod implements IRMethodMatchable {
    private final Method method;
    private final int ruleCount;

    public NotCompilableIRMethod(Method method, int ruleCount) {
        this.method = method;
        this.ruleCount = ruleCount;
    }

    @Override
    public String name() {
        return method.getName();
    }

    /**
     * Directly return a {@link NotCompilableIRMethodMatchResult} as we do not need to match IR rules individually.
     */
    @Override
    public NotCompilableIRMethodMatchResult match() {
        return new NotCompilableIRMethodMatchResult(method, ruleCount);
    }
}
