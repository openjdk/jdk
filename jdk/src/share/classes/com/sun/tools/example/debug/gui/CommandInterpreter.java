/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.util.*;

import com.sun.jdi.*;
import com.sun.tools.example.debug.bdi.*;

public class CommandInterpreter {

    boolean echo;

    Environment env;

    private ContextManager context;
    private ExecutionManager runtime;
    private ClassManager classManager;
    private SourceManager sourceManager;

    private OutputSink out; //### Hack!  Should be local in each method used.
    private String lastCommand = "help";

    public CommandInterpreter(Environment env) {
        this(env, true);
    }

    public CommandInterpreter(Environment env, boolean echo) {
        this.env = env;
        this.echo = echo;
        this.runtime = env.getExecutionManager();
        this.context = env.getContextManager();
        this.classManager = env.getClassManager();
        this.sourceManager = env.getSourceManager();
    }

    private ThreadReference[] threads = null;

    /*
     * The numbering of threads is relative to the current set of threads,
     * and may be affected by the creation and termination of new threads.
     * Commands issued using such thread ids will only give reliable behavior
     * relative to what was shown earlier in 'list' commands if the VM is interrupted.
     * We need a better scheme.
     */

    private ThreadReference[] threads() throws NoSessionException {
        if (threads == null) {
            ThreadIterator ti = new ThreadIterator(getDefaultThreadGroup());
            List<ThreadReference> tlist = new ArrayList<ThreadReference>();
            while (ti.hasNext()) {
                tlist.add(ti.nextThread());
            }
            threads = tlist.toArray(new ThreadReference[tlist.size()]);
        }
        return threads;
    }

    private ThreadReference findThread(String idToken) throws NoSessionException {
        String id;
        ThreadReference thread = null;
        if (idToken.startsWith("t@")) {
            id = idToken.substring(2);
        } else {
            id = idToken;
        }
        try {
            ThreadReference[] threads = threads();
            long threadID = Long.parseLong(id, 16);
            for (ThreadReference thread2 : threads) {
                if (thread2.uniqueID() == threadID) {
                    thread = thread2;
                    break;
                }
            }
            if (thread == null) {
                //env.failure("No thread for id \"" + idToken + "\"");
                env.failure("\"" + idToken + "\" is not a valid thread id.");
            }
        } catch (NumberFormatException e) {
            env.error("Thread id \"" + idToken + "\" is ill-formed.");
            thread = null;
        }
        return thread;
    }

    private ThreadIterator allThreads() throws NoSessionException {
        threads = null;
        //### Why not use runtime.allThreads().iterator() ?
        return new ThreadIterator(runtime.topLevelThreadGroups());
    }

    private ThreadIterator currentThreadGroupThreads() throws NoSessionException {
        threads = null;
        return new ThreadIterator(getDefaultThreadGroup());
    }

    private ThreadGroupIterator allThreadGroups() throws NoSessionException {
        threads = null;
        return new ThreadGroupIterator(runtime.topLevelThreadGroups());
    }

    private ThreadGroupReference defaultThreadGroup;

    private ThreadGroupReference getDefaultThreadGroup() throws NoSessionException {
        if (defaultThreadGroup == null) {
            defaultThreadGroup = runtime.systemThreadGroup();
        }
        return defaultThreadGroup;
    }

    private void setDefaultThreadGroup(ThreadGroupReference tg) {
        defaultThreadGroup = tg;
    }

    /*
     * Command handlers.
     */

    // Command: classes

    private void commandClasses() throws NoSessionException {
        OutputSink out = env.getOutputSink();
        //out.println("** classes list **");
        for (ReferenceType refType : runtime.allClasses()) {
            out.println(refType.name());
        }
        out.show();
    }


    // Command: methods

    private void commandMethods(StringTokenizer t) throws NoSessionException {
        if (!t.hasMoreTokens()) {
            env.error("No class specified.");
            return;
        }
        String idClass = t.nextToken();
        ReferenceType cls = findClass(idClass);
        if (cls != null) {
            List<Method> methods = cls.allMethods();
            OutputSink out = env.getOutputSink();
            for (int i = 0; i < methods.size(); i++) {
                Method method = methods.get(i);
                out.print(method.declaringType().name() + " " +
                            method.name() + "(");
                Iterator<String> it = method.argumentTypeNames().iterator();
                if (it.hasNext()) {
                    while (true) {
                        out.print(it.next());
                        if (!it.hasNext()) {
                            break;
                        }
                        out.print(", ");
                    }
                }
                out.println(")");
            }
            out.show();
        } else {
            //### Should validate class name syntax.
            env.failure("\"" + idClass + "\" is not a valid id or class name.");
        }
    }

    private ReferenceType findClass(String pattern) throws NoSessionException {
        List<ReferenceType> results = runtime.findClassesMatchingPattern(pattern);
        if (results.size() > 0) {
            //### Should handle multiple results sensibly.
            return results.get(0);
        }
        return null;
    }

    // Command: threads

    private void commandThreads(StringTokenizer t) throws NoSessionException {
        if (!t.hasMoreTokens()) {
            OutputSink out = env.getOutputSink();
            printThreadGroup(out, getDefaultThreadGroup(), 0);
            out.show();
            return;
        }
        String name = t.nextToken();
        ThreadGroupReference tg = findThreadGroup(name);
        if (tg == null) {
            env.failure(name + " is not a valid threadgroup name.");
        } else {
            OutputSink out = env.getOutputSink();
            printThreadGroup(out, tg, 0);
            out.show();
        }
    }

    private ThreadGroupReference findThreadGroup(String name) throws NoSessionException {
        //### Issue: Uniqueness of thread group names is not enforced.
        ThreadGroupIterator tgi = allThreadGroups();
        while (tgi.hasNext()) {
            ThreadGroupReference tg = tgi.nextThreadGroup();
            if (tg.name().equals(name)) {
                return tg;
            }
        }
        return null;
    }

    private int printThreadGroup(OutputSink out, ThreadGroupReference tg, int iThread) {
        out.println("Group " + tg.name() + ":");
        List<ThreadReference> tlist = tg.threads();
        int maxId = 0;
        int maxName = 0;
        for (int i = 0 ; i < tlist.size() ; i++) {
            ThreadReference thr = tlist.get(i);
            int len = Utils.description(thr).length();
            if (len > maxId) {
                maxId = len;
            }
            String name = thr.name();
            int iDot = name.lastIndexOf('.');
            if (iDot >= 0 && name.length() > iDot) {
                name = name.substring(iDot + 1);
            }
            if (name.length() > maxName) {
                maxName = name.length();
        }
        }
        String maxNumString = String.valueOf(iThread + tlist.size());
        int maxNumDigits = maxNumString.length();
        for (int i = 0 ; i < tlist.size() ; i++) {
            ThreadReference thr = tlist.get(i);
            char buf[] = new char[80];
            for (int j = 0; j < 79; j++) {
                buf[j] = ' ';
            }
            buf[79] = '\0';
            StringBuffer sbOut = new StringBuffer();
            sbOut.append(buf);

            // Right-justify the thread number at start of output string
            String numString = String.valueOf(iThread + i + 1);
            sbOut.insert(maxNumDigits - numString.length(),
                         numString);
            sbOut.insert(maxNumDigits, ".");

            int iBuf = maxNumDigits + 2;
            sbOut.insert(iBuf, Utils.description(thr));
            iBuf += maxId + 1;
            String name = thr.name();
            int iDot = name.lastIndexOf('.');
            if (iDot >= 0 && name.length() > iDot) {
                name = name.substring(iDot + 1);
            }
            sbOut.insert(iBuf, name);
            iBuf += maxName + 1;
            sbOut.insert(iBuf, Utils.getStatus(thr));
            sbOut.setLength(79);
            out.println(sbOut.toString());
        }
        for (ThreadGroupReference tg0 : tg.threadGroups()) {
            if (!tg.equals(tg0)) {  // TODO ref mgt
                iThread += printThreadGroup(out, tg0, iThread + tlist.size());
            }
        }
        return tlist.size();
    }

    // Command: threadgroups

    private void commandThreadGroups() throws NoSessionException {
        ThreadGroupIterator it = allThreadGroups();
        int cnt = 0;
        OutputSink out = env.getOutputSink();
        while (it.hasNext()) {
            ThreadGroupReference tg = it.nextThreadGroup();
            ++cnt;
            out.println("" + cnt + ". " + Utils.description(tg) + " " + tg.name());
        }
        out.show();
    }

    // Command: thread

    private void commandThread(StringTokenizer t) throws NoSessionException {
        if (!t.hasMoreTokens()) {
            env.error("Thread number not specified.");
            return;
        }
        ThreadReference thread = findThread(t.nextToken());
        if (thread != null) {
            //### Should notify user.
            context.setCurrentThread(thread);
        }
    }

    // Command: threadgroup

    private void commandThreadGroup(StringTokenizer t) throws NoSessionException {
        if (!t.hasMoreTokens()) {
            env.error("Threadgroup name not specified.");
            return;
        }
        String name = t.nextToken();
        ThreadGroupReference tg = findThreadGroup(name);
        if (tg == null) {
            env.failure(name + " is not a valid threadgroup name.");
        } else {
            //### Should notify user.
            setDefaultThreadGroup(tg);
        }
    }

    // Command: run

    private void commandRun(StringTokenizer t) throws NoSessionException {
        if (doLoad(false, t)) {
            env.notice("Running ...");
        }
    }

    // Command: load

    private void commandLoad(StringTokenizer t) throws NoSessionException {
        if (doLoad(true, t)) {}
    }

    private boolean doLoad(boolean suspended,
                           StringTokenizer t) throws NoSessionException {

        String clname;

        if (!t.hasMoreTokens()) {
            clname = context.getMainClassName();
            if (!clname.equals("")) {
                // Run from prevously-set class name.
                try {
                    String vmArgs = context.getVmArguments();
                    runtime.run(suspended,
                                vmArgs,
                                clname,
                                context.getProgramArguments());
                    return true;
                } catch (VMLaunchFailureException e) {
                    env.failure("Attempt to launch main class \"" + clname + "\" failed.");
                }
            } else {
                env.failure("No main class specified and no current default defined.");
            }
        } else {
            clname = t.nextToken();
            StringBuffer sbuf = new StringBuffer();
            // Allow VM arguments to be specified here?
            while (t.hasMoreTokens()) {
                String tok = t.nextToken();
                sbuf.append(tok);
                if (t.hasMoreTokens()) {
                    sbuf.append(' ');
                }
            }
            String args = sbuf.toString();
            try {
                String vmArgs = context.getVmArguments();
                runtime.run(suspended, vmArgs, clname, args);
                context.setMainClassName(clname);
                //context.setVmArguments(vmArgs);
                context.setProgramArguments(args);
                return true;
            } catch (VMLaunchFailureException e) {
                env.failure("Attempt to launch main class \"" + clname + "\" failed.");
            }
        }
        return false;
    }

    // Command: connect

    private void commandConnect(StringTokenizer t) {
        try {
            LaunchTool.queryAndLaunchVM(runtime);
        } catch (VMLaunchFailureException e) {
            env.failure("Attempt to connect failed.");
        }
    }

    // Command: attach

    private void commandAttach(StringTokenizer t) {
        String portName;
        if (!t.hasMoreTokens()) {
            portName = context.getRemotePort();
            if (!portName.equals("")) {
                try {
                    runtime.attach(portName);
                } catch (VMLaunchFailureException e) {
                    env.failure("Attempt to attach to port \"" + portName + "\" failed.");
                }
            } else {
                env.failure("No port specified and no current default defined.");
            }
        } else {
            portName = t.nextToken();
            try {
                runtime.attach(portName);
            } catch (VMLaunchFailureException e) {
                env.failure("Attempt to attach to port \"" + portName + "\" failed.");
            }
            context.setRemotePort(portName);
        }
    }

    // Command: detach

    private void commandDetach(StringTokenizer t) throws NoSessionException {
        runtime.detach();
    }

    // Command: interrupt

    private void commandInterrupt(StringTokenizer t) throws NoSessionException {
        runtime.interrupt();
    }

    // Command: suspend

    private void commandSuspend(StringTokenizer t) throws NoSessionException {
        if (!t.hasMoreTokens()) {
            // Suspend all threads in the current thread group.
            //### Issue: help message says default is all threads.
            //### Behavior here agrees with 'jdb', however.
            ThreadIterator ti = currentThreadGroupThreads();
            while (ti.hasNext()) {
                // TODO - don't suspend debugger threads
                ti.nextThread().suspend();
            }
            env.notice("All (non-system) threads suspended.");
        } else {
            while (t.hasMoreTokens()) {
                ThreadReference thread = findThread(t.nextToken());
                if (thread != null) {
                    //thread.suspend();
                    runtime.suspendThread(thread);
                }
            }
        }
    }

    // Command: resume

    private void commandResume(StringTokenizer t) throws NoSessionException {
         if (!t.hasMoreTokens()) {
            // Suspend all threads in the current thread group.
            //### Issue: help message says default is all threads.
            //### Behavior here agrees with 'jdb', however.
            ThreadIterator ti = currentThreadGroupThreads();
            while (ti.hasNext()) {
                // TODO - don't suspend debugger threads
                ti.nextThread().resume();
            }
            env.notice("All threads resumed.");
         } else {
             while (t.hasMoreTokens()) {
                ThreadReference thread = findThread(t.nextToken());
                if (thread != null) {
                    //thread.resume();
                    runtime.resumeThread(thread);
                }
             }
         }
    }

    // Command: cont

    private void commandCont() throws NoSessionException {
        try {
            runtime.go();
        } catch (VMNotInterruptedException e) {
            //### failure?
            env.notice("Target VM is already running.");
        }
    }

    // Command: step

    private void commandStep(StringTokenizer t) throws NoSessionException{
        ThreadReference current = context.getCurrentThread();
        if (current == null) {
            env.failure("No current thread.");
            return;
        }
        try {
            if (t.hasMoreTokens() &&
                t.nextToken().toLowerCase().equals("up")) {
                runtime.stepOut(current);
            } else {
                runtime.stepIntoLine(current);
            }
        } catch (AbsentInformationException e) {
            env.failure("No linenumber information available -- " +
                            "Try \"stepi\" to step by instructions.");
        }
    }

    // Command: stepi

    private void commandStepi() throws NoSessionException {
        ThreadReference current = context.getCurrentThread();
        if (current == null) {
            env.failure("No current thread.");
            return;
        }
        runtime.stepIntoInstruction(current);
    }

    // Command: next

    private void commandNext() throws NoSessionException {
        ThreadReference current = context.getCurrentThread();
        if (current == null) {
            env.failure("No current thread.");
            return;
        }
        try {
            runtime.stepOverLine(current);
        } catch (AbsentInformationException e) {
            env.failure("No linenumber information available -- " +
                            "Try \"nexti\" to step by instructions.");
        }
    }

    // Command: nexti  (NEW)

    private void commandNexti() throws NoSessionException {
        ThreadReference current = context.getCurrentThread();
        if (current == null) {
            env.failure("No current thread.");
            return;
        }
        runtime.stepOverInstruction(current);
    }

    // Command: kill

    private void commandKill(StringTokenizer t) throws NoSessionException {
        //### Should change the way in which thread ids and threadgroup names
        //### are distinguished.
         if (!t.hasMoreTokens()) {
            env.error("Usage: kill <threadgroup name> or <thread id>");
            return;
        }
        while (t.hasMoreTokens()) {
            String idToken = t.nextToken();
            ThreadReference thread = findThread(idToken);
            if (thread != null) {
                runtime.stopThread(thread);
                env.notice("Thread " + thread.name() + " killed.");
                return;
            } else {
                /* Check for threadgroup name, NOT skipping "system". */
                //### Should skip "system"?  Classic 'jdb' does this.
                //### Should deal with possible non-uniqueness of threadgroup names.
                ThreadGroupIterator itg = allThreadGroups();
                while (itg.hasNext()) {
                    ThreadGroupReference tg = itg.nextThreadGroup();
                    if (tg.name().equals(idToken)) {
                        ThreadIterator it = new ThreadIterator(tg);
                        while (it.hasNext()) {
                            runtime.stopThread(it.nextThread());
                        }
                        env.notice("Threadgroup " + tg.name() + "killed.");
                        return;
                    }
                }
                env.failure("\"" + idToken +
                            "\" is not a valid threadgroup or id.");
            }
        }
    }


    /*************
    // TODO
    private void commandCatchException(StringTokenizer t) throws NoSessionException {}
    // TODO
    private void commandIgnoreException(StringTokenizer t) throws NoSessionException {}
    *************/

    // Command: up

    //### Print current frame after command?

    int readCount(StringTokenizer t) {
        int cnt = 1;
        if (t.hasMoreTokens()) {
            String idToken = t.nextToken();
            try {
                cnt = Integer.valueOf(idToken).intValue();
            } catch (NumberFormatException e) {
                cnt = -1;
            }
        }
        return cnt;
    }

    void commandUp(StringTokenizer t) throws NoSessionException {
        ThreadReference current = context.getCurrentThread();
        if (current == null) {
            env.failure("No current thread.");
            return;
        }
        int nLevels = readCount(t);
        if (nLevels <= 0) {
            env.error("usage: up [n frames]");
            return;
        }
        try {
            int delta = context.moveCurrentFrameIndex(current, -nLevels);
            if (delta == 0) {
                env.notice("Already at top of stack.");
            } else if (-delta < nLevels) {
                env.notice("Moved up " + delta + " frames to top of stack.");
            }
        } catch (VMNotInterruptedException e) {
            env.failure("Target VM must be in interrupted state.");
        }
    }

    private void commandDown(StringTokenizer t) throws NoSessionException {
        ThreadReference current = context.getCurrentThread();
        if (current == null) {
            env.failure("No current thread.");
            return;
        }
        int nLevels = readCount(t);
        if (nLevels <= 0) {
            env.error("usage: down [n frames]");
            return;
        }
        try {
            int delta = context.moveCurrentFrameIndex(current, nLevels);
            if (delta == 0) {
                env.notice("Already at bottom of stack.");
            } else if (delta < nLevels) {
                env.notice("Moved down " + delta + " frames to bottom of stack.");
            }
        } catch (VMNotInterruptedException e) {
            env.failure("Target VM must be in interrupted state.");
        }
    }

    // Command: frame

    private void commandFrame(StringTokenizer t) throws NoSessionException {
        ThreadReference current = context.getCurrentThread();
        if (current == null) {
            env.failure("No current thread.");
            return;
        }
        if (!t.hasMoreTokens()) {
            env.error("usage: frame <frame-index>");
            return;
        }
        String idToken = t.nextToken();
        int n;
        try {
            n = Integer.valueOf(idToken).intValue();
        } catch (NumberFormatException e) {
            n = 0;
        }
        if (n <= 0) {
            env.error("use positive frame index");
            return;
        }
        try {
            int delta = context.setCurrentFrameIndex(current, n);
            if (delta == 0) {
                env.notice("Frame unchanged.");
            } else if (delta < 0) {
                env.notice("Moved up " + -delta + " frames.");
            } else {
                env.notice("Moved down " + delta + " frames.");
            }
        } catch (VMNotInterruptedException e) {
            env.failure("Target VM must be in interrupted state.");
        }
    }

    // Command: where

    //### Should we insist that VM be interrupted here?
    //### There is an inconsistency between the 'where' command
    //### and 'up' and 'down' in this respect.

    private void commandWhere(StringTokenizer t, boolean showPC)
                                                throws NoSessionException {
        ThreadReference current = context.getCurrentThread();
        if (!t.hasMoreTokens()) {
            if (current == null) {
                env.error("No thread specified.");
                return;
            }
            dumpStack(current, showPC);
        } else {
            String token = t.nextToken();
            if (token.toLowerCase().equals("all")) {
                ThreadIterator it = allThreads();
                while (it.hasNext()) {
                    ThreadReference thread = it.next();
                    out.println(thread.name() + ": ");
                    dumpStack(thread, showPC);
                }
            } else {
                ThreadReference thread = findThread(t.nextToken());
                //### Do we want to set current thread here?
                //### Should notify user of change.
                if (thread != null) {
                    context.setCurrentThread(thread);
                }
                dumpStack(thread, showPC);
            }
        }
    }

    private void dumpStack(ThreadReference thread, boolean showPC) {
        //### Check for these.
        //env.failure("Thread no longer exists.");
        //env.failure("Target VM must be in interrupted state.");
        //env.failure("Current thread isn't suspended.");
        //### Should handle extremely long stack traces sensibly for user.
        List<StackFrame> stack = null;
        try {
            stack = thread.frames();
        } catch (IncompatibleThreadStateException e) {
            env.failure("Thread is not suspended.");
        }
        //### Fix this!
        //### Previously mishandled cases where thread was not current.
        //### Now, prints all of the stack regardless of current frame.
        int frameIndex = 0;
        //int frameIndex = context.getCurrentFrameIndex();
        if (stack == null) {
            env.failure("Thread is not running (no stack).");
        } else {
            OutputSink out = env.getOutputSink();
            int nFrames = stack.size();
            for (int i = frameIndex; i < nFrames; i++) {
                StackFrame frame = stack.get(i);
                Location loc = frame.location();
                Method meth = loc.method();
                out.print("  [" + (i + 1) + "] ");
                out.print(meth.declaringType().name());
                out.print('.');
                out.print(meth.name());
                out.print(" (");
                if (meth.isNative()) {
                    out.print("native method");
                } else if (loc.lineNumber() != -1) {
                    try {
                        out.print(loc.sourceName());
                    } catch (AbsentInformationException e) {
                        out.print("<unknown>");
                    }
                    out.print(':');
                    out.print(loc.lineNumber());
                }
                out.print(')');
                if (showPC) {
                    long pc = loc.codeIndex();
                    if (pc != -1) {
                        out.print(", pc = " + pc);
                    }
                }
                out.println();
            }
            out.show();
        }
    }

    private void listEventRequests() throws NoSessionException {
        // Print set breakpoints
        List<EventRequestSpec> specs = runtime.eventRequestSpecs();
        if (specs.isEmpty()) {
            env.notice("No breakpoints/watchpoints/exceptions set.");
        } else {
            OutputSink out = env.getOutputSink();
            out.println("Current breakpoints/watchpoints/exceptions set:");
            for (EventRequestSpec bp : specs) {
                out.println("\t" + bp);
            }
            out.show();
        }
    }

    private BreakpointSpec parseBreakpointSpec(String bptSpec) {
        StringTokenizer t = new StringTokenizer(bptSpec);
        BreakpointSpec bpSpec = null;
//        try {
            String token = t.nextToken("@:( \t\n\r");
            // We can't use hasMoreTokens here because it will cause any leading
            // paren to be lost.
            String rest;
            try {
                rest = t.nextToken("").trim();
            } catch (NoSuchElementException e) {
                rest = null;
            }
            if ((rest != null) && rest.startsWith("@")) {
                t = new StringTokenizer(rest.substring(1));
                String sourceName = token;
                String lineToken = t.nextToken();
                int lineNumber = Integer.valueOf(lineToken).intValue();
                if (t.hasMoreTokens()) {
                    return null;
                }
                bpSpec = runtime.createSourceLineBreakpoint(sourceName,
                                                            lineNumber);
            } else if ((rest != null) && rest.startsWith(":")) {
                t = new StringTokenizer(rest.substring(1));
                String classId = token;
                String lineToken = t.nextToken();
                int lineNumber = Integer.valueOf(lineToken).intValue();
                if (t.hasMoreTokens()) {
                    return null;
                }
                bpSpec = runtime.createClassLineBreakpoint(classId, lineNumber);
            } else {
                // Try stripping method from class.method token.
                int idot = token.lastIndexOf('.');
                if ( (idot <= 0) ||        /* No dot or dot in first char */
                     (idot >= token.length() - 1) ) { /* dot in last char */
                    return null;
                }
                String methodName = token.substring(idot + 1);
                String classId = token.substring(0, idot);
                List<String> argumentList = null;
                if (rest != null) {
                    if (!rest.startsWith("(") || !rest.endsWith(")")) {
                        //### Should throw exception with error message
                        //out.println("Invalid method specification: "
                        //            + methodName + rest);
                        return null;
                    }
                    // Trim the parens
                    //### What about spaces in arglist?
                    rest = rest.substring(1, rest.length() - 1);
                    argumentList = new ArrayList<String>();
                    t = new StringTokenizer(rest, ",");
                    while (t.hasMoreTokens()) {
                        argumentList.add(t.nextToken());
                    }
                }
                bpSpec = runtime.createMethodBreakpoint(classId,
                                                       methodName,
                                                       argumentList);
            }
//        } catch (Exception e) {
//            env.error("Exception attempting to create breakpoint: " + e);
//            return null;
//        }
        return bpSpec;
    }

    private void commandStop(StringTokenizer t) throws NoSessionException {
        String token;

        if (!t.hasMoreTokens()) {
            listEventRequests();
        } else {
            token = t.nextToken();
            // Ignore optional "at" or "in" token.
            // Allowed for backward compatibility.
            if (token.equals("at") || token.equals("in")) {
                if (t.hasMoreTokens()) {
                    token = t.nextToken();
                } else {
                    env.error("Missing breakpoint specification.");
                    return;
                }
            }
            BreakpointSpec bpSpec = parseBreakpointSpec(token);
            if (bpSpec != null) {
                //### Add sanity-checks for deferred breakpoint.
                runtime.install(bpSpec);
            } else {
                env.error("Ill-formed breakpoint specification.");
            }
        }
    }

    private void commandClear(StringTokenizer t) throws NoSessionException {
        if (!t.hasMoreTokens()) {
            // Print set breakpoints
            listEventRequests();
            return;
        }
        //### need 'clear all'
        BreakpointSpec bpSpec = parseBreakpointSpec(t.nextToken());
        if (bpSpec != null) {
            List<EventRequestSpec> specs = runtime.eventRequestSpecs();

            if (specs.isEmpty()) {
                env.notice("No breakpoints set.");
            } else {
                List<EventRequestSpec> toDelete = new ArrayList<EventRequestSpec>();
                for (EventRequestSpec spec : specs) {
                    if (spec.equals(bpSpec)) {
                        toDelete.add(spec);
                    }
                }
                // The request used for matching should be found
                if (toDelete.size() <= 1) {
                    env.notice("No matching breakpoint set.");
                }
                for (EventRequestSpec spec : toDelete) {
                    runtime.delete(spec);
                }
            }
        } else {
            env.error("Ill-formed breakpoint specification.");
        }
    }

    // Command: list

    private void commandList(StringTokenizer t) throws NoSessionException {
        ThreadReference current = context.getCurrentThread();
        if (current == null) {
            env.error("No thread specified.");
            return;
        }
        Location loc;
        try {
            StackFrame frame = context.getCurrentFrame(current);
            if (frame == null) {
                env.failure("Thread has not yet begun execution.");
                return;
            }
            loc = frame.location();
        } catch (VMNotInterruptedException e) {
            env.failure("Target VM must be in interrupted state.");
            return;
        }
        SourceModel source = sourceManager.sourceForLocation(loc);
        if (source == null) {
            if (loc.method().isNative()) {
                env.failure("Current method is native.");
                return;
            }
            env.failure("No source available for " + Utils.locationString(loc) + ".");
            return;
        }
        ReferenceType refType = loc.declaringType();
        int lineno = loc.lineNumber();
        if (t.hasMoreTokens()) {
            String id = t.nextToken();
            // See if token is a line number.
            try {
                lineno = Integer.valueOf(id).intValue();
            } catch (NumberFormatException nfe) {
                // It isn't -- see if it's a method name.
                List<Method> meths = refType.methodsByName(id);
                if (meths == null || meths.size() == 0) {
                    env.failure(id +
                                " is not a valid line number or " +
                                "method name for class " +
                                refType.name());
                    return;
                } else if (meths.size() > 1) {
                    env.failure(id +
                                " is an ambiguous method name in" +
                                refType.name());
                    return;
                }
                loc = meths.get(0).location();
                lineno = loc.lineNumber();
            }
        }
        int startLine = (lineno > 4) ? lineno - 4 : 1;
        int endLine = startLine + 9;
        String sourceLine = source.sourceLine(lineno);
        if (sourceLine == null) {
            env.failure("" +
                        lineno +
                        " is an invalid line number for " +
                        refType.name());
        } else {
            OutputSink out = env.getOutputSink();
            for (int i = startLine; i <= endLine; i++) {
                sourceLine = source.sourceLine(i);
                if (sourceLine == null) {
                    break;
                }
                out.print(i);
                out.print("\t");
                if (i == lineno) {
                    out.print("=> ");
                } else {
                    out.print("   ");
                }
                out.println(sourceLine);
            }
            out.show();
        }
    }

    // Command: use
    // Get or set the source file path list.

    private void commandUse(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            out.println(sourceManager.getSourcePath().asString());
        } else {
            //### Should throw exception for invalid path.
            //### E.g., vetoable property change.
            sourceManager.setSourcePath(new SearchPath(t.nextToken()));
        }
    }

    // Command: sourcepath
    // Get or set the source file path list.  (Alternate to 'use'.)

    private void commandSourcepath(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            out.println(sourceManager.getSourcePath().asString());
        } else {
            //### Should throw exception for invalid path.
            //### E.g., vetoable property change.
            sourceManager.setSourcePath(new SearchPath(t.nextToken()));
        }
    }

    // Command: classpath
    // Get or set the class file path list.

    private void commandClasspath(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            out.println(classManager.getClassPath().asString());
        } else {
            //### Should throw exception for invalid path.
            //### E.g., vetoable property change.
            classManager.setClassPath(new SearchPath(t.nextToken()));
        }
    }

    // Command: view
    // Display source for source file or class.

    private void commandView(StringTokenizer t) throws NoSessionException {
        if (!t.hasMoreTokens()) {
            env.error("Argument required");
        } else {
            String name = t.nextToken();
            if (name.endsWith(".java") ||
                name.indexOf(File.separatorChar) >= 0) {
                env.viewSource(name);
            } else {
                //### JDI crashes taking line number for class.
                /*****
                ReferenceType cls = findClass(name);
                if (cls != null) {
                    env.viewLocation(cls.location());
                } else {
                    env.failure("No such class");
                }
                *****/
                String fileName = name.replace('.', File.separatorChar) + ".java";
                env.viewSource(fileName);
            }
        }
    }

    // Command: locals
    // Print all local variables in current stack frame.

    private void commandLocals() throws NoSessionException {
        ThreadReference current = context.getCurrentThread();
        if (current == null) {
            env.failure("No default thread specified: " +
                        "use the \"thread\" command first.");
            return;
        }
        StackFrame frame;
        try {
            frame = context.getCurrentFrame(current);
            if (frame == null) {
                env.failure("Thread has not yet created any stack frames.");
                return;
            }
        } catch (VMNotInterruptedException e) {
            env.failure("Target VM must be in interrupted state.");
            return;
        }

        List<LocalVariable> vars;
        try {
            vars = frame.visibleVariables();
            if (vars == null || vars.size() == 0) {
                env.failure("No local variables");
                return;
            }
        } catch (AbsentInformationException e) {
            env.failure("Local variable information not available." +
                        " Compile with -g to generate variable information");
            return;
        }

        OutputSink out = env.getOutputSink();
        out.println("Method arguments:");
        for (LocalVariable var : vars) {
            if (var.isArgument()) {
                printVar(out, var, frame);
            }
        }
        out.println("Local variables:");
        for (LocalVariable var : vars) {
            if (!var.isArgument()) {
                printVar(out, var, frame);
            }
        }
        out.show();
        return;
    }

    /**
     * Command: monitor
     * Monitor an expression
     */
    private void commandMonitor(StringTokenizer t) throws NoSessionException {
        if (!t.hasMoreTokens()) {
            env.error("Argument required");
        } else {
            env.getMonitorListModel().add(t.nextToken(""));
        }
    }

    /**
     * Command: unmonitor
     * Unmonitor an expression
     */
    private void commandUnmonitor(StringTokenizer t) throws NoSessionException {
        if (!t.hasMoreTokens()) {
            env.error("Argument required");
        } else {
            env.getMonitorListModel().remove(t.nextToken(""));
        }
    }

    // Print a stack variable.

    private void printVar(OutputSink out, LocalVariable var, StackFrame frame) {
        out.print("  " + var.name());
        if (var.isVisible(frame)) {
            Value val = frame.getValue(var);
            out.println(" = " + val.toString());
        } else {
            out.println(" is not in scope");
        }
    }

    // Command: print
    // Evaluate an expression.

    private void commandPrint(StringTokenizer t, boolean dumpObject) throws NoSessionException {
        if (!t.hasMoreTokens()) {
            //### Probably confused if expresion contains whitespace.
            env.error("No expression specified.");
            return;
        }
        ThreadReference current = context.getCurrentThread();
        if (current == null) {
            env.failure("No default thread specified: " +
                        "use the \"thread\" command first.");
            return;
        }
        StackFrame frame;
        try {
            frame = context.getCurrentFrame(current);
            if (frame == null) {
                env.failure("Thread has not yet created any stack frames.");
                return;
            }
        } catch (VMNotInterruptedException e) {
            env.failure("Target VM must be in interrupted state.");
            return;
        }
        while (t.hasMoreTokens()) {
            String expr = t.nextToken("");
            Value val = null;
            try {
                val = runtime.evaluate(frame, expr);
            } catch(Exception e) {
                env.error("Exception: " + e);
                //### Fix this!
            }
            if (val == null) {
                return;  // Error message already printed
            }
            OutputSink out = env.getOutputSink();
            if (dumpObject && (val instanceof ObjectReference) &&
                                 !(val instanceof StringReference)) {
                ObjectReference obj = (ObjectReference)val;
                ReferenceType refType = obj.referenceType();
                out.println(expr + " = " + val.toString() + " {");
                dump(out, obj, refType, refType);
                out.println("}");
            } else {
                out.println(expr + " = " + val.toString());
            }
            out.show();
        }
    }

    private void dump(OutputSink out,
                      ObjectReference obj, ReferenceType refType,
                      ReferenceType refTypeBase) {
        for (Field field : refType.fields()) {
            out.print("    ");
            if (!refType.equals(refTypeBase)) {
                out.print(refType.name() + ".");
            }
            out.print(field.name() + ": ");
            Object o = obj.getValue(field);
            out.println((o == null) ? "null" : o.toString()); // Bug ID 4374471
        }
        if (refType instanceof ClassType) {
            ClassType sup = ((ClassType)refType).superclass();
            if (sup != null) {
                dump(out, obj, sup, refTypeBase);
            }
        } else if (refType instanceof InterfaceType) {
            for (InterfaceType sup : ((InterfaceType)refType).superinterfaces()) {
                dump(out, obj, sup, refTypeBase);
            }
        }
    }

    /*
     * Display help message.
     */

    private void help() {
        out.println("** command list **");
        out.println("threads [threadgroup]     -- list threads");
        out.println("thread <thread id>        -- set default thread");
        out.println("suspend [thread id(s)]    -- suspend threads (default: all)");
        out.println("resume [thread id(s)]     -- resume threads (default: all)");
        out.println("where [thread id] | all   -- dump a thread's stack");
        out.println("wherei [thread id] | all  -- dump a thread's stack, with pc info");
        out.println("threadgroups              -- list threadgroups");
        out.println("threadgroup <name>        -- set current threadgroup\n");
//      out.println("print <expression>        -- print value of expression");
        out.println("dump <expression>         -- print all object information\n");
//      out.println("eval <expression>         -- evaluate expression (same as print)");
        out.println("locals                    -- print all local variables in current stack frame\n");
        out.println("classes                   -- list currently known classes");
        out.println("methods <class id>        -- list a class's methods\n");
        out.println("stop [in] <class id>.<method>[(argument_type,...)] -- set a breakpoint in a method");
        out.println("stop [at] <class id>:<line> -- set a breakpoint at a line");
        out.println("up [n frames]             -- move up a thread's stack");
        out.println("down [n frames]           -- move down a thread's stack");
        out.println("frame <frame-id>           -- to a frame");
        out.println("clear <class id>.<method>[(argument_type,...)]   -- clear a breakpoint in a method");
        out.println("clear <class id>:<line>   -- clear a breakpoint at a line");
        out.println("clear                     -- list breakpoints");
        out.println("step                      -- execute current line");
        out.println("step up                   -- execute until the current method returns to its caller");
        out.println("stepi                     -- execute current instruction");
        out.println("next                      -- step one line (step OVER calls)");
        out.println("nexti                     -- step one instruction (step OVER calls)");
        out.println("cont                      -- continue execution from breakpoint\n");
//      out.println("catch <class id>          -- break for the specified exception");
//      out.println("ignore <class id>         -- ignore when the specified exception\n");
        out.println("view classname|filename   -- display source file");
        out.println("list [line number|method] -- print source code context at line or method");
        out.println("use <source file path>    -- display or change the source path\n");
//### new
        out.println("sourcepath <source file path>    -- display or change the source path\n");
//### new
        out.println("classpath <class file path>    -- display or change the class path\n");
        out.println("monitor <expression>      -- evaluate an expression each time the program stops\n");
        out.println("unmonitor <monitor#>      -- delete a monitor\n");
        out.println("read <filename>           -- read and execute a command file\n");
//      out.println("memory                    -- report memory usage");
//      out.println("gc                        -- free unused objects\n");
        out.println("run <class> [args]        -- start execution of a Java class");
        out.println("run                       -- re-execute last class run");
        out.println("load <class> [args]       -- start execution of a Java class, initially suspended");
        out.println("load                      -- re-execute last class run, initially suspended");
        out.println("attach <portname>         -- debug existing process\n");
        out.println("detach                    -- detach from debuggee process\n");
        out.println("kill <thread(group)>      -- kill a thread or threadgroup\n");
        out.println("!!                        -- repeat last command");
        out.println("help (or ?)               -- list commands");
        out.println("exit (or quit)            -- exit debugger");
    }

    /*
     * Execute a command.
     */

    public void executeCommand(String command) {
        //### Treatment of 'out' here is dirty...
        out = env.getOutputSink();
        if (echo) {
            out.println(">>> " + command);
        }
        StringTokenizer t = new StringTokenizer(command);
        try {
            String cmd;
            if (t.hasMoreTokens()) {
                cmd = t.nextToken().toLowerCase();
                lastCommand = cmd;
            } else {
                cmd = lastCommand;
            }
            if (cmd.equals("print")) {
                commandPrint(t, false);
            } else if (cmd.equals("eval")) {
                commandPrint(t, false);
            } else if (cmd.equals("dump")) {
                commandPrint(t, true);
            } else if (cmd.equals("locals")) {
                commandLocals();
            } else if (cmd.equals("classes")) {
                commandClasses();
            } else if (cmd.equals("methods")) {
                commandMethods(t);
            } else if (cmd.equals("threads")) {
                commandThreads(t);
            } else if (cmd.equals("thread")) {
                commandThread(t);
            } else if (cmd.equals("suspend")) {
                commandSuspend(t);
            } else if (cmd.equals("resume")) {
                commandResume(t);
            } else if (cmd.equals("cont")) {
                commandCont();
            } else if (cmd.equals("threadgroups")) {
                commandThreadGroups();
            } else if (cmd.equals("threadgroup")) {
                commandThreadGroup(t);
            } else if (cmd.equals("run")) {
                commandRun(t);
            } else if (cmd.equals("load")) {
                commandLoad(t);
            } else if (cmd.equals("connect")) {
                commandConnect(t);
            } else if (cmd.equals("attach")) {
                commandAttach(t);
            } else if (cmd.equals("detach")) {
                commandDetach(t);
            } else if (cmd.equals("interrupt")) {
                commandInterrupt(t);
//### Not implemented.
//          } else if (cmd.equals("catch")) {
//              commandCatchException(t);
//### Not implemented.
//          } else if (cmd.equals("ignore")) {
//              commandIgnoreException(t);
            } else if (cmd.equals("step")) {
                commandStep(t);
            } else if (cmd.equals("stepi")) {
                commandStepi();
            } else if (cmd.equals("next")) {
                commandNext();
            } else if (cmd.equals("nexti")) {
                commandNexti();
            } else if (cmd.equals("kill")) {
                commandKill(t);
            } else if (cmd.equals("where")) {
                commandWhere(t, false);
            } else if (cmd.equals("wherei")) {
                commandWhere(t, true);
            } else if (cmd.equals("up")) {
                commandUp(t);
            } else if (cmd.equals("down")) {
                commandDown(t);
            } else if (cmd.equals("frame")) {
                commandFrame(t);
            } else if (cmd.equals("stop")) {
                commandStop(t);
            } else if (cmd.equals("clear")) {
                commandClear(t);
            } else if (cmd.equals("list")) {
                commandList(t);
            } else if (cmd.equals("use")) {
                commandUse(t);
            } else if (cmd.equals("sourcepath")) {
                commandSourcepath(t);
            } else if (cmd.equals("classpath")) {
                commandClasspath(t);
            } else if (cmd.equals("monitor")) {
                commandMonitor(t);
            } else if (cmd.equals("unmonitor")) {
                commandUnmonitor(t);
            } else if (cmd.equals("view")) {
                commandView(t);
//          } else if (cmd.equals("read")) {
//              readCommand(t);
            } else if (cmd.equals("help") || cmd.equals("?")) {
                help();
            } else if (cmd.equals("quit") || cmd.equals("exit")) {
                try {
                    runtime.detach();
                } catch (NoSessionException e) {
                    // ignore
                }
                env.terminate();
            } else {
                //### Dubious repeat-count feature inherited from 'jdb'
                if (t.hasMoreTokens()) {
                    try {
                        int repeat = Integer.parseInt(cmd);
                        String subcom = t.nextToken("");
                        while (repeat-- > 0) {
                            executeCommand(subcom);
                        }
                        return;
                    } catch (NumberFormatException exc) {
                    }
                }
                out.println("huh? Try help...");
                out.flush();
            }
        } catch (NoSessionException e) {
            out.println("There is no currently attached VM session.");
            out.flush();
        } catch (Exception e) {
            out.println("Internal exception: " + e.toString());
            out.flush();
            System.out.println("JDB internal exception: " + e.toString());
            e.printStackTrace();
        }
        out.show();
    }
}
