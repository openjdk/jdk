/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package compiler.compilercontrol.share.processors;

import compiler.compilercontrol.share.scenario.CompileCommand;
import jdk.test.lib.OutputAnalyzer;

import java.util.List;
import java.util.function.Consumer;

public class QuietProcessor implements Consumer<OutputAnalyzer> {
    private final List<CompileCommand> commands;

    public QuietProcessor(List<CompileCommand> compileCommands) {
        commands = compileCommands;
    }

    @Override
    public void accept(OutputAnalyzer outputAnalyzer) {
        for (CompileCommand command : commands) {
            if (command.isValid()) {
                outputAnalyzer.shouldNotContain("CompileCommand: "
                        + command.command.name + " "
                        + command.methodDescriptor.getCanonicalString());
                outputAnalyzer.shouldNotContain("CompileCommand: An error "
                        + "occurred during parsing");
            } else {
                outputAnalyzer.shouldMatch("(CompileCommand: )"
                        + "(unrecognized command)|(Bad pattern)|"
                        + "(An error occurred during parsing)");
            }
        }
    }
}
