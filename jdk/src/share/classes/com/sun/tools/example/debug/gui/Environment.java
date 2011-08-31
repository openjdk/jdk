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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package com.sun.tools.example.debug.gui;

import java.io.*;
import com.sun.jdi.*;
import com.sun.tools.example.debug.bdi.*;

public class Environment {

    private SourceManager sourceManager;
    private ClassManager classManager;
    private ContextManager contextManager;
    private MonitorListModel monitorListModel;
    private ExecutionManager runtime;

    private PrintWriter typeScript;

    private boolean verbose;

    public Environment() {
        this.classManager = new ClassManager(this);
        //### Order of the next three lines is important!  (FIX THIS)
        this.runtime = new ExecutionManager();
        this.sourceManager = new SourceManager(this);
        this.contextManager = new ContextManager(this);
        this.monitorListModel = new MonitorListModel(this);
    }

    // Services used by debugging tools.

    public SourceManager getSourceManager() {
        return sourceManager;
    }

    public ClassManager getClassManager() {
        return classManager;
    }

    public ContextManager getContextManager() {
        return contextManager;
    }

    public MonitorListModel getMonitorListModel() {
        return monitorListModel;
    }

    public ExecutionManager getExecutionManager() {
        return runtime;
    }

    //### TODO:
    //### Tools should attach/detach from environment
    //### via a property, which should call an 'addTool'
    //### method when set to maintain a registry of
    //### tools for exit-time cleanup, etc.  Tool
    //### class constructors should be argument-free, so
    //### that they may be instantiated by bean builders.
    //### Will also need 'removeTool' in case property
    //### value is changed.
    //
    // public void addTool(Tool t);
    // public void removeTool(Tool t);

     public void terminate() {
         System.exit(0);
     }

    // public void refresh();    // notify all tools to refresh their views


    // public void addStatusListener(StatusListener l);
    // public void removeStatusListener(StatusListener l);

    // public void addOutputListener(OutputListener l);
    // public void removeOutputListener(OutputListener l);

    public void setTypeScript(PrintWriter writer) {
        typeScript = writer;
    }

    public void error(String message) {
        if (typeScript != null) {
            typeScript.println(message);
        } else {
            System.out.println(message);
        }
    }

    public void failure(String message) {
        if (typeScript != null) {
            typeScript.println(message);
        } else {
            System.out.println(message);
        }
    }

    public void notice(String message) {
        if (typeScript != null) {
            typeScript.println(message);
        } else {
            System.out.println(message);
        }
    }

    public OutputSink getOutputSink() {
        return new OutputSink(typeScript);
    }

    public void viewSource(String fileName) {
        //### HACK ###
        //### Should use listener here.
        com.sun.tools.example.debug.gui.GUI.srcTool.showSourceFile(fileName);
    }

    public void viewLocation(Location locn) {
        //### HACK ###
        //### Should use listener here.
        //### Should we use sourceForLocation here?
        com.sun.tools.example.debug.gui.GUI.srcTool.showSourceForLocation(locn);
    }

    //### Also in 'ContextManager'.  Do we need both?

    public boolean getVerboseFlag() {
        return verbose;
    }

    public void setVerboseFlag(boolean verbose) {
        this.verbose = verbose;
    }

}
