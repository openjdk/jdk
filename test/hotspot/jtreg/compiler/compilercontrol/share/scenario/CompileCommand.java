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

package compiler.compilercontrol.share.scenario;

import compiler.compilercontrol.share.method.MethodDescriptor;

/**
 * Compile Command description interface
 */
public class CompileCommand {
    public final Command command;
    public final MethodDescriptor methodDescriptor;
    public final Scenario.Compiler compiler;
    public final Scenario.Type type;

    public CompileCommand(Command command,
                          MethodDescriptor methodDescriptor,
                          Scenario.Compiler compiler,
                          Scenario.Type type) {
        this.command = command;
        this.methodDescriptor = methodDescriptor;
        this.compiler = compiler;
        this.type = type;
    }

    /**
     * Shows that this compile command is valid
     *
     * @return true if this is a valid command
     */
    public boolean isValid() {
        if (command == Command.NONEXISTENT) {
            return false;
        }
        return methodDescriptor.isValid();
    }

    /**
     * Prints compile command to the system output
     */
    public void print() {
        System.out.printf("%s (type: %s): %s (valid: %b)%n", command.name(),
                type.name(), methodDescriptor.getString(), isValid());
    }
}
