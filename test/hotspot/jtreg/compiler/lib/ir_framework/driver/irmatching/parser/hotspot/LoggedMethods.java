/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching.parser.hotspot;

import compiler.lib.ir_framework.TestFramework;

import java.util.HashMap;
import java.util.Map;

/**
 * This class keeps track of all {@link LoggedMethod} instances.
 */
public class LoggedMethods {
    private final Map<String, LoggedMethod> loggedMethods = new HashMap<>();

    public Map<String, LoggedMethod> loggedMethods() {
        return loggedMethods;
    }

    public LoggedMethod loggedMethod(String methodName) {
        LoggedMethod loggedMethod = loggedMethods.get(methodName);
        TestFramework.check(loggedMethod != null, "must exist");
        return loggedMethod;
    }

    /**
     * Always creates a new LoggedMethod to drop the already collected ideal and opto assembly outputs of the same method.
     * We only want to keep the very last compilation of a method which is the one requested by the framework.
     */
    public void registerMethod(String methodName) {
        loggedMethods.put(methodName, new LoggedMethod());
    }

}
