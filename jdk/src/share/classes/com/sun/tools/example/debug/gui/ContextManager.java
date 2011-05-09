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

import com.sun.jdi.*;
import com.sun.tools.example.debug.event.*;
import com.sun.tools.example.debug.bdi.*;

public class ContextManager {

    private ClassManager classManager;
    private ExecutionManager runtime;

    private String mainClassName;
    private String vmArguments;
    private String commandArguments;
    private String remotePort;

    private ThreadReference currentThread;

    private boolean verbose;

    private ArrayList<ContextListener> contextListeners = new ArrayList<ContextListener>();

    public ContextManager(Environment env) {
        classManager = env.getClassManager();
        runtime = env.getExecutionManager();
        mainClassName = "";
        vmArguments = "";
        commandArguments = "";
        currentThread = null;

        ContextManagerListener listener = new ContextManagerListener();
        runtime.addJDIListener(listener);
        runtime.addSessionListener(listener);
    }

    // Program execution defaults.

    //### Should there be change listeners for these?
    //### They would be needed if we expected a dialog to be
    //### synchronized with command input while it was open.

    public String getMainClassName() {
        return mainClassName;
    }

    public void setMainClassName(String mainClassName) {
        this.mainClassName = mainClassName;
    }

    public String getVmArguments() {
        return processClasspathDefaults(vmArguments);
    }

    public void setVmArguments(String vmArguments) {
        this.vmArguments = vmArguments;
    }

    public String getProgramArguments() {
        return commandArguments;
    }

    public void setProgramArguments(String commandArguments) {
        this.commandArguments = commandArguments;
    }

    public String getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(String remotePort) {
        this.remotePort = remotePort;

    }


    // Miscellaneous debugger session preferences.

    public boolean getVerboseFlag() {
        return verbose;
    }

    public void setVerboseFlag(boolean verbose) {
        this.verbose = verbose;
    }


    // Thread focus.

    public ThreadReference getCurrentThread() {
        return currentThread;
    }

    public void setCurrentThread(ThreadReference t) {
        if (t != currentThread) {
            currentThread = t;
            notifyCurrentThreadChanged(t);
        }
    }

    public void setCurrentThreadInvalidate(ThreadReference t) {
        currentThread = t;
        notifyCurrentFrameChanged(runtime.threadInfo(t),
                                  0, true);
    }

    public void invalidateCurrentThread() {
        notifyCurrentFrameChanged(null, 0, true);
    }


    // If a view is displaying the current thread, it may
    // choose to indicate which frame is current in the
    // sense of the command-line UI.  It may also "warp" the
    // selection to that frame when changed by an 'up' or 'down'
    // command. Hence, a notifier is provided.

    /******
    public int getCurrentFrameIndex() {
        return getCurrentFrameIndex(currentThreadInfo);
    }
    ******/

    public int getCurrentFrameIndex(ThreadReference t) {
        return getCurrentFrameIndex(runtime.threadInfo(t));
    }

    //### Used in StackTraceTool.
    public int getCurrentFrameIndex(ThreadInfo tinfo) {
        if (tinfo == null) {
            return 0;
        }
        Integer currentFrame = (Integer)tinfo.getUserObject();
        if (currentFrame == null) {
            return 0;
        } else {
            return currentFrame.intValue();
        }
    }

    public int moveCurrentFrameIndex(ThreadReference t, int count) throws VMNotInterruptedException {
        return setCurrentFrameIndex(t,count, true);
    }

    public int setCurrentFrameIndex(ThreadReference t, int newIndex) throws VMNotInterruptedException {
        return setCurrentFrameIndex(t, newIndex, false);
    }

    public int setCurrentFrameIndex(int newIndex) throws VMNotInterruptedException {
        if (currentThread == null) {
            return 0;
        } else {
            return setCurrentFrameIndex(currentThread, newIndex, false);
        }
    }

    private int setCurrentFrameIndex(ThreadReference t, int x, boolean relative) throws VMNotInterruptedException {
        boolean sameThread = t.equals(currentThread);
        ThreadInfo tinfo = runtime.threadInfo(t);
        if (tinfo == null) {
            return 0;
        }
        int maxIndex = tinfo.getFrameCount()-1;
        int oldIndex = getCurrentFrameIndex(tinfo);
        int newIndex = relative? oldIndex + x : x;
        if (newIndex > maxIndex) {
            newIndex = maxIndex;
        } else  if (newIndex < 0) {
            newIndex = 0;
        }
        if (!sameThread || newIndex != oldIndex) {  // don't recurse
            setCurrentFrameIndex(tinfo, newIndex);
        }
        return newIndex - oldIndex;
    }

    private void setCurrentFrameIndex(ThreadInfo tinfo, int index) {
        tinfo.setUserObject(new Integer(index));
        //### In fact, the value may not have changed at this point.
        //### We need to signal that the user attempted to change it,
        //### however, so that the selection can be "warped" to the
        //### current location.
        notifyCurrentFrameChanged(tinfo.thread(), index);
    }

    public StackFrame getCurrentFrame() throws VMNotInterruptedException {
        return getCurrentFrame(runtime.threadInfo(currentThread));
    }

    public StackFrame getCurrentFrame(ThreadReference t) throws VMNotInterruptedException {
        return getCurrentFrame(runtime.threadInfo(t));
    }

    public StackFrame getCurrentFrame(ThreadInfo tinfo) throws VMNotInterruptedException {
        int index = getCurrentFrameIndex(tinfo);
        try {
            // It is possible, though unlikely, that the VM was interrupted
            // before the thread created its Java stack.
            return tinfo.getFrame(index);
        } catch (FrameIndexOutOfBoundsException e) {
            return null;
        }
    }

    public void addContextListener(ContextListener cl) {
        contextListeners.add(cl);
    }

    public void removeContextListener(ContextListener cl) {
        contextListeners.remove(cl);
    }

    //### These notifiers are fired only in response to USER-INITIATED changes
    //### to the current thread and current frame.  When the current thread is set automatically
    //### after a breakpoint hit or step completion, no event is generated.  Instead,
    //### interested parties are expected to listen for the BreakpointHit and StepCompleted
    //### events.  This convention is unclean, and I believe that it reflects a defect in
    //### in the current architecture.  Unfortunately, however, we cannot guarantee the
    //### order in which various listeners receive a given event, and the handlers for
    //### the very same events that cause automatic changes to the current thread may also
    //### need to know the current thread.

    private void notifyCurrentThreadChanged(ThreadReference t) {
        ThreadInfo tinfo = null;
        int index = 0;
        if (t != null) {
            tinfo = runtime.threadInfo(t);
            index = getCurrentFrameIndex(tinfo);
        }
        notifyCurrentFrameChanged(tinfo, index, false);
    }

    private void notifyCurrentFrameChanged(ThreadReference t, int index) {
        notifyCurrentFrameChanged(runtime.threadInfo(t),
                                  index, false);
    }

    private void notifyCurrentFrameChanged(ThreadInfo tinfo, int index,
                                           boolean invalidate) {
        ArrayList<ContextListener> l =  new ArrayList<ContextListener>(contextListeners);
        CurrentFrameChangedEvent evt =
            new CurrentFrameChangedEvent(this, tinfo, index, invalidate);
        for (int i = 0; i < l.size(); i++) {
            l.get(i).currentFrameChanged(evt);
        }
    }

    private class ContextManagerListener extends JDIAdapter
                       implements SessionListener, JDIListener {

        // SessionListener

        @Override
        public void sessionStart(EventObject e) {
            invalidateCurrentThread();
        }

        @Override
        public void sessionInterrupt(EventObject e) {
            setCurrentThreadInvalidate(currentThread);
        }

        @Override
        public void sessionContinue(EventObject e) {
            invalidateCurrentThread();
        }

        // JDIListener

        @Override
        public void locationTrigger(LocationTriggerEventSet e) {
            setCurrentThreadInvalidate(e.getThread());
        }

        @Override
        public void exception(ExceptionEventSet e) {
            setCurrentThreadInvalidate(e.getThread());
        }

        @Override
        public void vmDisconnect(VMDisconnectEventSet e) {
            invalidateCurrentThread();
        }

    }


    /**
     * Add a -classpath argument to the arguments passed to the exec'ed
     * VM with the contents of CLASSPATH environment variable,
     * if -classpath was not already specified.
     *
     * @param javaArgs the arguments to the VM being exec'd that
     *                 potentially has a user specified -classpath argument.
     * @return a javaArgs whose -classpath option has been added
     */

    private String processClasspathDefaults(String javaArgs) {
        if (javaArgs.indexOf("-classpath ") == -1) {
            StringBuffer munged = new StringBuffer(javaArgs);
            SearchPath classpath = classManager.getClassPath();
            if (classpath.isEmpty()) {
                String envcp = System.getProperty("env.class.path");
                if ((envcp != null) && (envcp.length() > 0)) {
                    munged.append(" -classpath " + envcp);
                }
            } else {
                munged.append(" -classpath " + classpath.asString());
            }
            return munged.toString();
        } else {
            return javaArgs;
        }
    }

    private String appendPath(String path1, String path2) {
        if (path1 == null || path1.length() == 0) {
            return path2 == null ? "." : path2;
        } else if (path2 == null || path2.length() == 0) {
            return path1;
        } else {
            return path1  + File.pathSeparator + path2;
        }
    }

}
