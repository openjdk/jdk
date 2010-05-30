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

public class ApplicationTool extends JPanel {

    private Environment env;
    private ExecutionManager runtime;

    private TypeScript script;

    private static final String PROMPT = "Input:";

    public ApplicationTool(Environment env) {

        super(new BorderLayout());

        this.env = env;
        this.runtime = env.getExecutionManager();

        this.script = new TypeScript(PROMPT, false); // No implicit echo.
        this.add(script);

        script.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                runtime.sendLineToApplication(script.readln());
            }
        });

        runtime.addApplicationEchoListener(new TypeScriptOutputListener(script));
        runtime.addApplicationOutputListener(new TypeScriptOutputListener(script));
        runtime.addApplicationErrorListener(new TypeScriptOutputListener(script));

        //### should clean up on exit!

    }

    /******
    public void setFont(Font f) {
        script.setFont(f);
    }
    ******/

}
