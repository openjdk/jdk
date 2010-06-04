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

import java.io.*;
import java.util.*;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.event.*;

import com.sun.jdi.*;
import com.sun.jdi.event.*;

import com.sun.tools.example.debug.bdi.*;
import com.sun.tools.example.debug.event.*;

public class CommandTool extends JPanel {

    private Environment env;

    private ContextManager context;
    private ExecutionManager runtime;
    private SourceManager sourceManager;

    private TypeScript script;

    private static final String DEFAULT_CMD_PROMPT = "Command:";

    public CommandTool(Environment env) {

        super(new BorderLayout());

        this.env = env;
        this.context = env.getContextManager();
        this.runtime = env.getExecutionManager();
        this.sourceManager = env.getSourceManager();

        script = new TypeScript(DEFAULT_CMD_PROMPT, false); //no echo
        this.add(script);

        final CommandInterpreter interpreter =
            new CommandInterpreter(env);

        // Establish handler for incoming commands.

        script.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                interpreter.executeCommand(script.readln());
            }
        });

        // Establish ourselves as the listener for VM diagnostics.

        OutputListener diagnosticsListener =
            new TypeScriptOutputListener(script, true);
        runtime.addDiagnosticsListener(diagnosticsListener);

        // Establish ourselves as the shared debugger typescript.

        env.setTypeScript(new PrintWriter(new TypeScriptWriter(script)));

        // Handle VM events.

        TTYDebugListener listener = new TTYDebugListener(diagnosticsListener);

        runtime.addJDIListener(listener);
        runtime.addSessionListener(listener);
        runtime.addSpecListener(listener);
        context.addContextListener(listener);

        //### remove listeners on exit!

    }

    private class TTYDebugListener implements
            JDIListener, SessionListener, SpecListener, ContextListener {

        private OutputListener diagnostics;

        TTYDebugListener(OutputListener diagnostics) {
            this.diagnostics = diagnostics;
        }

        // JDIListener

        public void accessWatchpoint(AccessWatchpointEventSet e) {
            setThread(e);
            for (EventIterator it = e.eventIterator(); it.hasNext(); ) {
                Event evt = it.nextEvent();
                diagnostics.putString("Watchpoint hit: " +
                                      locationString(e));
            }
        }

        public void classPrepare(ClassPrepareEventSet e) {
            if (context.getVerboseFlag()) {
                String name = e.getReferenceType().name();
                diagnostics.putString("Class " + name + " loaded");
            }
        }

        public void classUnload(ClassUnloadEventSet e) {
            if (context.getVerboseFlag()) {
                diagnostics.putString("Class " + e.getClassName() +
                                      " unloaded.");
            }
        }

        public void exception(ExceptionEventSet e) {
            setThread(e);
            String name = e.getException().referenceType().name();
            diagnostics.putString("Exception: " + name);
        }

        public void locationTrigger(LocationTriggerEventSet e) {
            String locString = locationString(e);
            setThread(e);
            for (EventIterator it = e.eventIterator(); it.hasNext(); ) {
                Event evt = it.nextEvent();
                if (evt instanceof BreakpointEvent) {
                    diagnostics.putString("Breakpoint hit: " + locString);
                } else if (evt instanceof StepEvent) {
                    diagnostics.putString("Step completed: " + locString);
                } else if (evt instanceof MethodEntryEvent) {
                    diagnostics.putString("Method entered: " + locString);
                } else if (evt instanceof MethodExitEvent) {
                    diagnostics.putString("Method exited: " + locString);
                } else {
                    diagnostics.putString("UNKNOWN event: " + e);
                }
            }
        }

        public void modificationWatchpoint(ModificationWatchpointEventSet e) {
            setThread(e);
            for (EventIterator it = e.eventIterator(); it.hasNext(); ) {
                Event evt = it.nextEvent();
                diagnostics.putString("Watchpoint hit: " +
                                      locationString(e));
            }
        }

        public void threadDeath(ThreadDeathEventSet e) {
            if (context.getVerboseFlag()) {
                diagnostics.putString("Thread " + e.getThread() +
                                      " ended.");
            }
        }

        public void threadStart(ThreadStartEventSet e) {
            if (context.getVerboseFlag()) {
                diagnostics.putString("Thread " + e.getThread() +
                                      " started.");
            }
        }

        public void vmDeath(VMDeathEventSet e) {
            script.setPrompt(DEFAULT_CMD_PROMPT);
            diagnostics.putString("VM exited");
        }

        public void vmDisconnect(VMDisconnectEventSet e) {
            script.setPrompt(DEFAULT_CMD_PROMPT);
            diagnostics.putString("Disconnected from VM");
        }

        public void vmStart(VMStartEventSet e) {
            script.setPrompt(DEFAULT_CMD_PROMPT);
            diagnostics.putString("VM started");
        }

        // SessionListener

        public void sessionStart(EventObject e) {}

        public void sessionInterrupt(EventObject e) {
            Thread.yield();  // fetch output
            diagnostics.putString("VM interrupted by user.");
            script.setPrompt(DEFAULT_CMD_PROMPT);
        }

        public void sessionContinue(EventObject e) {
            diagnostics.putString("Execution resumed.");
            script.setPrompt(DEFAULT_CMD_PROMPT);
        }

        // SpecListener

        public void breakpointSet(SpecEvent e) {
            EventRequestSpec spec = e.getEventRequestSpec();
            diagnostics.putString("Breakpoint set at " + spec + ".");
        }
        public void breakpointDeferred(SpecEvent e) {
            EventRequestSpec spec = e.getEventRequestSpec();
            diagnostics.putString("Breakpoint will be set at " +
                                  spec + " when its class is loaded.");
        }
        public void breakpointDeleted(SpecEvent e) {
            EventRequestSpec spec = e.getEventRequestSpec();
            diagnostics.putString("Breakpoint at " + spec.toString() + " deleted.");
        }
        public void breakpointResolved(SpecEvent e) {
            EventRequestSpec spec = e.getEventRequestSpec();
            diagnostics.putString("Breakpoint resolved to " + spec.toString() + ".");
        }
        public void breakpointError(SpecErrorEvent e) {
            EventRequestSpec spec = e.getEventRequestSpec();
            diagnostics.putString("Deferred breakpoint at " +
                                  spec + " could not be resolved:" +
                                  e.getReason());
        }

//### Add info for watchpoints and exceptions

        public void watchpointSet(SpecEvent e) {
        }
        public void watchpointDeferred(SpecEvent e) {
        }
        public void watchpointDeleted(SpecEvent e) {
        }
        public void watchpointResolved(SpecEvent e) {
        }
        public void watchpointError(SpecErrorEvent e) {
        }

        public void exceptionInterceptSet(SpecEvent e) {
        }
        public void exceptionInterceptDeferred(SpecEvent e) {
        }
        public void exceptionInterceptDeleted(SpecEvent e) {
        }
        public void exceptionInterceptResolved(SpecEvent e) {
        }
        public void exceptionInterceptError(SpecErrorEvent e) {
        }


        // ContextListener.

        // If the user selects a new current thread or frame, update prompt.

        public void currentFrameChanged(CurrentFrameChangedEvent e) {
            // Update prompt only if affect thread is current.
            ThreadReference thread = e.getThread();
            if (thread == context.getCurrentThread()) {
                script.setPrompt(promptString(thread, e.getIndex()));
            }
        }

    }

    private String locationString(LocatableEventSet e) {
        Location loc = e.getLocation();
        return "thread=\"" + e.getThread().name() +
            "\", " + Utils.locationString(loc);
    }

    private void setThread(LocatableEventSet e) {
        if (!e.suspendedNone()) {
            Thread.yield();  // fetch output
            script.setPrompt(promptString(e.getThread(), 0));
            //### Current thread should be set elsewhere, e.g.,
            //### in ContextManager
            //### context.setCurrentThread(thread);
        }
    }

    private String promptString(ThreadReference thread, int frameIndex) {
        if (thread == null) {
            return DEFAULT_CMD_PROMPT;
        } else {
            // Frame indices are presented to user as indexed from 1.
            return (thread.name() + "[" + (frameIndex + 1) + "]:");
        }
    }
}
