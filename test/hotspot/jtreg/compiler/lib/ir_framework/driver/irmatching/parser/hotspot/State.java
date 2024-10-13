/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import compiler.lib.ir_framework.driver.irmatching.parser.TestMethods;

/**
 * This class holds the current state of the parsing of the hotspot_pid* file.
 */
class State {
    private final CompileQueueMessages compileQueueMessages;
    private final LoggedMethods loggedMethods;
    private LoggedMethod loggedMethod = LoggedMethod.DONT_CARE;

    public State(String testClassName, TestMethods testMethods) {
        this.compileQueueMessages = new CompileQueueMessages(testClassName, testMethods);
        this.loggedMethods = new LoggedMethods();
    }

    public LoggedMethods loggedMethods() {
        return loggedMethods;
    }

    public void update(String line) {
        if (compileQueueMessages.isTestMethodQueuedLine(line)) {
            processCompileQueueLine(line);
        } else if (CompilePhaseBlock.isBlockStartLine(line)) {
            processBlockStartLine(line);
        } else if (CompilePhaseBlock.isBlockEndLine(line)) {
            processBlockEndLine();
        } else {
            processNormalLine(line);
        }
    }

    private void processCompileQueueLine(String line) {
        String methodName = compileQueueMessages.parse(line);
        loggedMethods.registerMethod(methodName);
    }

    private void processBlockStartLine(String line) {
        String methodName = compileQueueMessages.findTestMethodName(line);
        if (!methodName.isEmpty()) {
            loggedMethod = loggedMethods.loggedMethod(methodName);
            if (CompilePhaseBlock.isPrintIdealStart(line)) {
                loggedMethod.beginPrintIdealBlock(line);
            } else {
                loggedMethod.beginPrintOptoAssemblyBlock();
            }
        }
    }

    private void processBlockEndLine() {
        loggedMethod.terminateBlock();
    }

    private void processNormalLine(String line) {
        loggedMethod.addLine(line);
    }
}
