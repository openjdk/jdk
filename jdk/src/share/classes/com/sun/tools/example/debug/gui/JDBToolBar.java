/*
 * Copyright (c) 1998, 1999, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.tools.example.debug.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

import com.sun.jdi.*;
import com.sun.tools.example.debug.bdi.*;

class JDBToolBar extends JToolBar {

    Environment env;

    ExecutionManager runtime;
    ClassManager classManager;
    SourceManager sourceManager;

    CommandInterpreter interpreter;

    JDBToolBar(Environment env) {

        this.env = env;
        this.runtime = env.getExecutionManager();
        this.classManager = env.getClassManager();
        this.sourceManager = env.getSourceManager();
        this.interpreter = new CommandInterpreter(env, true);

        //===== Configure toolbar here =====

        addTool("Run application", "run", "run");
        addTool("Connect to application", "connect", "connect");
        addSeparator();

        addTool("Step into next line", "step", "step");
        addTool("Step over next line", "next", "next");
//      addSeparator();

//      addTool("Step into next instruction", "stepi", "stepi");
//      addTool("Step over next instruction", "nexti", "nexti");
//      addSeparator();

        addTool("Step out of current method call", "step up", "step up");
        addSeparator();

        addTool("Suspend execution", "interrupt", "interrupt");
        addTool("Continue execution", "cont", "cont");
        addSeparator();

//      addTool("Display current stack", "where", "where");
//      addSeparator();

        addTool("Move up one stack frame", "up", "up");
        addTool("Move down one stack frame", "down", "down");
//      addSeparator();

//      addTool("Display command list", "help", "help");
//      addSeparator();

//      addTool("Exit debugger", "exit", "exit");

        //==================================

    }

    private void addTool(String toolTip, String labelText, String command) {
        JButton button = new JButton(labelText);
        button.setToolTipText(toolTip);
        final String cmd = command;
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                interpreter.executeCommand(cmd);
            }
        });
        this.add(button);
    }

}
