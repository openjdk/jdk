/*
 * Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.example.debug.bdi;

import com.sun.jdi.*;
import com.sun.jdi.request.*;
import com.sun.jdi.connect.*;
import com.sun.tools.example.debug.expr.ExpressionParser;
import com.sun.tools.example.debug.expr.ParseException;

import java.io.*;
import java.util.*;

import com.sun.tools.example.debug.event.*;

import javax.swing.SwingUtilities;

/**
 * Move this towards being only state and functionality
 * that spans across Sessions (and thus VMs).
 */
public class ExecutionManager {

    private Session session;

    /**
     * Get/set JDI trace mode.
     */
    int traceMode = VirtualMachine.TRACE_NONE;

  //////////////////    Listener registration    //////////////////

  // Session Listeners

    ArrayList<SessionListener> sessionListeners = new ArrayList<SessionListener>();

    public void addSessionListener(SessionListener listener) {
        sessionListeners.add(listener);
    }

    public void removeSessionListener(SessionListener listener) {
        sessionListeners.remove(listener);
    }

  // Spec Listeners

  ArrayList<SpecListener> specListeners = new ArrayList<SpecListener>();

    public void addSpecListener(SpecListener cl) {
        specListeners.add(cl);
    }

    public void removeSpecListener(SpecListener cl) {
        specListeners.remove(cl);
    }

    // JDI Listeners

    ArrayList<JDIListener> jdiListeners = new ArrayList<JDIListener>();

    /**
     * Adds a JDIListener
     */
    public void addJDIListener(JDIListener jl) {
        jdiListeners.add(jl);
    }

    /**
     * Adds a JDIListener - at the specified position
     */
    public void addJDIListener(int index, JDIListener jl) {
        jdiListeners.add(index, jl);
    }

    /**
     * Removes a JDIListener
     */
    public void removeJDIListener(JDIListener jl) {
        jdiListeners.remove(jl);
    }

  // App Echo Listeners

    private ArrayList<OutputListener> appEchoListeners = new ArrayList<OutputListener>();

    public void addApplicationEchoListener(OutputListener l) {
        appEchoListeners.add(l);
    }

    public void removeApplicationEchoListener(OutputListener l) {
        appEchoListeners.remove(l);
    }

  // App Output Listeners

    private ArrayList<OutputListener> appOutputListeners = new ArrayList<OutputListener>();

    public void addApplicationOutputListener(OutputListener l) {
        appOutputListeners.add(l);
    }

    public void removeApplicationOutputListener(OutputListener l) {
        appOutputListeners.remove(l);
    }

  // App Error Listeners

    private ArrayList<OutputListener> appErrorListeners = new ArrayList<OutputListener>();

    public void addApplicationErrorListener(OutputListener l) {
        appErrorListeners.add(l);
    }

    public void removeApplicationErrorListener(OutputListener l) {
        appErrorListeners.remove(l);
    }

  // Diagnostic Listeners

    private ArrayList<OutputListener> diagnosticsListeners = new ArrayList<OutputListener>();

    public void addDiagnosticsListener(OutputListener l) {
        diagnosticsListeners.add(l);
    }

    public void removeDiagnosticsListener(OutputListener l) {
        diagnosticsListeners.remove(l);
    }

  ///////////    End Listener Registration    //////////////

    //### We probably don't want this public
    public VirtualMachine vm() {
        return session == null ? null : session.vm;
    }

    void ensureActiveSession() throws NoSessionException {
        if (session == null) {
         throw new NoSessionException();
      }
    }

    public EventRequestManager eventRequestManager() {
        return vm() == null ? null : vm().eventRequestManager();
    }

    /**
     * Get JDI trace mode.
     */
    public int getTraceMode(int mode) {
        return traceMode;
    }

    /**
     * Set JDI trace mode.
     */
    public void setTraceMode(int mode) {
        traceMode = mode;
        if (session != null) {
            session.setTraceMode(mode);
        }
    }

    /**
     * Determine if VM is interrupted, i.e, present and not running.
     */
    public boolean isInterrupted() /* should: throws NoSessionException */ {
//      ensureActiveSession();
        return session.interrupted;
    }

    /**
     * Return a list of ReferenceType objects for all
     * currently loaded classes and interfaces.
     * Array types are not returned.
     */
    public List<ReferenceType> allClasses() throws NoSessionException {
        ensureActiveSession();
        return vm().allClasses();
    }

    /**
     * Return a ReferenceType object for the currently
     * loaded class or interface whose fully-qualified
     * class name is specified, else return null if there
     * is none.
     *
     * In general, we must return a list of types, because
     * multiple class loaders could have loaded a class
     * with the same fully-qualified name.
     */
    public List<ReferenceType> findClassesByName(String name) throws NoSessionException {
        ensureActiveSession();
        return vm().classesByName(name);
    }

    /**
     * Return a list of ReferenceType objects for all
     * currently loaded classes and interfaces whose name
     * matches the given pattern.  The pattern syntax is
     * open to some future revision, but currently consists
     * of a fully-qualified class name in which the first
     * component may optionally be a "*" character, designating
     * an arbitrary prefix.
     */
    public List<ReferenceType> findClassesMatchingPattern(String pattern)
                                                throws NoSessionException {
        ensureActiveSession();
        List<ReferenceType> result = new ArrayList<ReferenceType>();  //### Is default size OK?
        if (pattern.startsWith("*.")) {
            // Wildcard matches any leading package name.
            pattern = pattern.substring(1);
            for (ReferenceType type : vm().allClasses()) {
                if (type.name().endsWith(pattern)) {
                    result.add(type);
                }
            }
            return result;
        } else {
            // It's a class name.
            return vm().classesByName(pattern);
        }
    }

    /*
     * Return a list of ThreadReference objects corresponding
     * to the threads that are currently active in the VM.
     * A thread is removed from the list just before the
     * thread terminates.
     */

    public List<ThreadReference> allThreads() throws NoSessionException {
        ensureActiveSession();
        return vm().allThreads();
    }

    /*
     * Return a list of ThreadGroupReference objects corresponding
     * to the top-level threadgroups that are currently active in the VM.
     * Note that a thread group may be empty, or contain no threads as
     * descendents.
     */

    public List<ThreadGroupReference> topLevelThreadGroups() throws NoSessionException {
        ensureActiveSession();
        return vm().topLevelThreadGroups();
    }

    /*
     * Return the system threadgroup.
     */

    public ThreadGroupReference systemThreadGroup()
                                                throws NoSessionException {
        ensureActiveSession();
        return vm().topLevelThreadGroups().get(0);
    }

    /*
     * Evaluate an expression.
     */

    public Value evaluate(final StackFrame f, String expr)
        throws ParseException,
                                            InvocationException,
                                            InvalidTypeException,
                                            ClassNotLoadedException,
                                            NoSessionException,
                                            IncompatibleThreadStateException {
        ExpressionParser.GetFrame frameGetter = null;
        ensureActiveSession();
        if (f != null) {
            frameGetter = new ExpressionParser.GetFrame() {
                @Override
                public StackFrame get() /* throws IncompatibleThreadStateException */ {
                    return f;
                }
            };
        }
        return ExpressionParser.evaluate(expr, vm(), frameGetter);
    }


    /*
     * Start a new VM.
     */

    public void run(boolean suspended,
                    String vmArgs,
                    String className,
                    String args) throws VMLaunchFailureException {

        endSession();

        //### Set a breakpoint on 'main' method.
        //### Would be cleaner if we could just bring up VM already suspended.
        if (suspended) {
            //### Set breakpoint at 'main(java.lang.String[])'.
            List<String> argList = new ArrayList<String>(1);
            argList.add("java.lang.String[]");
            createMethodBreakpoint(className, "main", argList);
        }

        String cmdLine = className + " " + args;

        startSession(new ChildSession(this, vmArgs, cmdLine,
                                      appInput, appOutput, appError,
                                      diagnostics));
    }

    /*
     * Attach to an existing VM.
     */
    public void attach(String portName) throws VMLaunchFailureException {
        endSession();

        //### Changes made here for connectors have broken the
        //### the 'Session' abstraction.  The 'Session.attach()'
        //### method is intended to encapsulate all of the various
        //### ways in which session start-up can fail. (maddox 12/18/98)

        /*
         * Now that attaches and launches both go through Connectors,
         * it may be worth creating a new subclass of Session for
         * attach sessions.
         */
        VirtualMachineManager mgr = Bootstrap.virtualMachineManager();
        AttachingConnector connector = mgr.attachingConnectors().get(0);
        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("port").setValue(portName);

        Session newSession = internalAttach(connector, arguments);
        if (newSession != null) {
            startSession(newSession);
        }
    }

    private Session internalAttach(AttachingConnector connector,
                                   Map<String, Connector.Argument> arguments) {
        try {
            VirtualMachine vm = connector.attach(arguments);
            return new Session(vm, this, diagnostics);
        } catch (IOException ioe) {
            diagnostics.putString("\n Unable to attach to target VM: " +
                                  ioe.getMessage());
        } catch (IllegalConnectorArgumentsException icae) {
            diagnostics.putString("\n Invalid connector arguments: " +
                                  icae.getMessage());
        }
        return null;
    }

    private Session internalListen(ListeningConnector connector,
                                   Map<String, Connector.Argument> arguments) {
        try {
            VirtualMachine vm = connector.accept(arguments);
            return new Session(vm, this, diagnostics);
        } catch (IOException ioe) {
            diagnostics.putString(
                  "\n Unable to accept connection to target VM: " +
                                  ioe.getMessage());
        } catch (IllegalConnectorArgumentsException icae) {
            diagnostics.putString("\n Invalid connector arguments: " +
                                  icae.getMessage());
        }
        return null;
    }

    /*
     * Connect via user specified arguments
     * @return true on success
     */
    public boolean explictStart(Connector connector, Map<String, Connector.Argument> arguments)
                                           throws VMLaunchFailureException {
        Session newSession = null;

        endSession();

        if (connector instanceof LaunchingConnector) {
            // we were launched, use ChildSession
            newSession = new ChildSession(this, (LaunchingConnector)connector,
                                          arguments,
                                          appInput, appOutput, appError,
                                          diagnostics);
        } else if (connector instanceof AttachingConnector) {
            newSession = internalAttach((AttachingConnector)connector,
                                        arguments);
        } else if (connector instanceof ListeningConnector) {
            newSession = internalListen((ListeningConnector)connector,
                                        arguments);
        } else {
            diagnostics.putString("\n Unknown connector: " + connector);
        }
        if (newSession != null) {
            startSession(newSession);
        }
        return newSession != null;
    }

    /*
     * Detach from VM.  If VM was started by debugger, terminate it.
     */
    public void detach() throws NoSessionException {
        ensureActiveSession();
        endSession();
    }

    private void startSession(Session s) throws VMLaunchFailureException {
        if (!s.attach()) {
            throw new VMLaunchFailureException();
        }
        session = s;
        EventRequestManager em = vm().eventRequestManager();
        ClassPrepareRequest classPrepareRequest = em.createClassPrepareRequest();
        //### We must allow the deferred breakpoints to be resolved before
        //### we continue executing the class.  We could optimize if there
        //### were no deferred breakpoints outstanding for a particular class.
        //### Can we do this with JDI?
        classPrepareRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        classPrepareRequest.enable();
        ClassUnloadRequest classUnloadRequest = em.createClassUnloadRequest();
        classUnloadRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        classUnloadRequest.enable();
        ThreadStartRequest threadStartRequest = em.createThreadStartRequest();
        threadStartRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        threadStartRequest.enable();
        ThreadDeathRequest threadDeathRequest = em.createThreadDeathRequest();
        threadDeathRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        threadDeathRequest.enable();
        ExceptionRequest exceptionRequest =
                                em.createExceptionRequest(null, false, true);
        exceptionRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        exceptionRequest.enable();
        validateThreadInfo();
        session.interrupted = true;
        notifySessionStart();
    }

    void endSession() {
        if (session != null) {
            session.detach();
            session = null;
            invalidateThreadInfo();
            notifySessionDeath();
        }
    }

    /*
     * Suspend all VM activity.
     */

    public void interrupt() throws NoSessionException {
        ensureActiveSession();
        vm().suspend();
        //### Is it guaranteed that the interrupt has happened?
        validateThreadInfo();
        session.interrupted = true;
        notifyInterrupted();
    }

    /*
     * Resume interrupted VM.
     */

    public void go() throws NoSessionException, VMNotInterruptedException {
        ensureActiveSession();
        invalidateThreadInfo();
        session.interrupted = false;
        notifyContinued();
        vm().resume();
    }

    /*
     * Stepping.
     */
    void clearPreviousStep(ThreadReference thread) {
        /*
         * A previous step may not have completed on this thread;
         * if so, it gets removed here.
         */
         EventRequestManager mgr = vm().eventRequestManager();
         for (StepRequest request : mgr.stepRequests()) {
             if (request.thread().equals(thread)) {
                 mgr.deleteEventRequest(request);
                 break;
             }
         }
    }

    private void generalStep(ThreadReference thread, int size, int depth)
                        throws NoSessionException {
        ensureActiveSession();
        invalidateThreadInfo();
        session.interrupted = false;
        notifyContinued();

        clearPreviousStep(thread);
        EventRequestManager reqMgr = vm().eventRequestManager();
        StepRequest request = reqMgr.createStepRequest(thread,
                                                       size, depth);
        // We want just the next step event and no others
        request.addCountFilter(1);
        request.enable();
        vm().resume();
    }

    public void stepIntoInstruction(ThreadReference thread)
                        throws NoSessionException {
        generalStep(thread, StepRequest.STEP_MIN, StepRequest.STEP_INTO);
    }

    public void stepOverInstruction(ThreadReference thread)
                        throws NoSessionException {
        generalStep(thread, StepRequest.STEP_MIN, StepRequest.STEP_OVER);
    }

    public void stepIntoLine(ThreadReference thread)
                        throws NoSessionException,
                        AbsentInformationException {
        generalStep(thread, StepRequest.STEP_LINE, StepRequest.STEP_INTO);
    }

    public void stepOverLine(ThreadReference thread)
                        throws NoSessionException,
                        AbsentInformationException {
        generalStep(thread, StepRequest.STEP_LINE, StepRequest.STEP_OVER);
    }

    public void stepOut(ThreadReference thread)
                        throws NoSessionException {
        generalStep(thread, StepRequest.STEP_MIN, StepRequest.STEP_OUT);
    }

    /*
     * Thread control.
     */

    public void suspendThread(ThreadReference thread) throws NoSessionException {
        ensureActiveSession();
        thread.suspend();
    }

    public void resumeThread(ThreadReference thread) throws NoSessionException {
        ensureActiveSession();
        thread.resume();
    }

    public void stopThread(ThreadReference thread) throws NoSessionException {
        ensureActiveSession();
        //### Need an exception now.  Which one to use?
        //thread.stop();
    }

    /*
     * ThreadInfo objects -- Allow query of thread status and stack.
     */

    private List<ThreadInfo> threadInfoList = new LinkedList<ThreadInfo>();
    //### Should be weak! (in the value, not the key)
    private HashMap<ThreadReference, ThreadInfo> threadInfoMap = new HashMap<ThreadReference, ThreadInfo>();

    public ThreadInfo threadInfo(ThreadReference thread) {
        if (session == null || thread == null) {
            return null;
        }
        ThreadInfo info = threadInfoMap.get(thread);
        if (info == null) {
            //### Should not hardcode initial frame count and prefetch here!
            //info = new ThreadInfo(thread, 10, 10);
            info = new ThreadInfo(thread);
            if (session.interrupted) {
                info.validate();
            }
            threadInfoList.add(info);
            threadInfoMap.put(thread, info);
        }
        return info;
    }

     void validateThreadInfo() {
        session.interrupted = true;
        for (ThreadInfo threadInfo : threadInfoList) {
            threadInfo.validate();
            }
    }

    private void invalidateThreadInfo() {
        if (session != null) {
            session.interrupted = false;
            for (ThreadInfo threadInfo : threadInfoList) {
                threadInfo.invalidate();
            }
        }
    }

    void removeThreadInfo(ThreadReference thread) {
        ThreadInfo info = threadInfoMap.get(thread);
        if (info != null) {
            info.invalidate();
            threadInfoMap.remove(thread);
            threadInfoList.remove(info);
        }
    }

    /*
     * Listen for Session control events.
     */

    private void notifyInterrupted() {
      ArrayList<SessionListener> l = new ArrayList<SessionListener>(sessionListeners);
        EventObject evt = new EventObject(this);
        for (int i = 0; i < l.size(); i++) {
            l.get(i).sessionInterrupt(evt);
        }
    }

    private void notifyContinued() {
        ArrayList<SessionListener> l = new ArrayList<SessionListener>(sessionListeners);
        EventObject evt = new EventObject(this);
        for (int i = 0; i < l.size(); i++) {
            l.get(i).sessionContinue(evt);
        }
    }

    private void notifySessionStart() {
        ArrayList<SessionListener> l = new ArrayList<SessionListener>(sessionListeners);
        EventObject evt = new EventObject(this);
        for (int i = 0; i < l.size(); i++) {
            l.get(i).sessionStart(evt);
        }
    }

    private void notifySessionDeath() {
/*** noop for now
        ArrayList<SessionListener> l = new ArrayList<SessionListener>(sessionListeners);
        EventObject evt = new EventObject(this);
        for (int i = 0; i < l.size(); i++) {
            ((SessionListener)l.get(i)).sessionDeath(evt);
        }
****/
    }

    /*
     * Listen for input and output requests from the application
     * being debugged.  These are generated only when the debuggee
     * is spawned as a child of the debugger.
     */

    private Object inputLock = new Object();
    private LinkedList<String> inputBuffer = new LinkedList<String>();

    private void resetInputBuffer() {
        synchronized (inputLock) {
            inputBuffer = new LinkedList<String>();
        }
    }

    public void sendLineToApplication(String line) {
        synchronized (inputLock) {
            inputBuffer.addFirst(line);
            inputLock.notifyAll();
        }
    }

    private InputListener appInput = new InputListener() {
        @Override
        public String getLine() {
            // Don't allow reader to be interrupted -- catch and retry.
            String line = null;
            while (line == null) {
                synchronized (inputLock) {
                    try {
                        while (inputBuffer.size() < 1) {
                            inputLock.wait();
                        }
                        line = inputBuffer.removeLast();
                    } catch (InterruptedException e) {}
                }
            }
            // We must not be holding inputLock here, as the listener
            // that we call to echo a line might call us re-entrantly
            // to provide another line of input.
            // Run in Swing event dispatcher thread.
            final String input = line;
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    echoInputLine(input);
                }
            });
            return line;
        }
    };

    private static String newline = System.getProperty("line.separator");

    private void echoInputLine(String line) {
        ArrayList<OutputListener> l = new ArrayList<OutputListener>(appEchoListeners);
        for (int i = 0; i < l.size(); i++) {
            OutputListener ol = l.get(i);
            ol.putString(line);
            ol.putString(newline);
        }
    }

    private OutputListener appOutput = new OutputListener() {
      @Override
        public void putString(String string) {
            ArrayList<OutputListener> l = new ArrayList<OutputListener>(appEchoListeners);
            for (int i = 0; i < l.size(); i++) {
                l.get(i).putString(string);
            }
        }
    };

    private OutputListener appError = new OutputListener() {
      @Override
        public void putString(String string) {
            ArrayList<OutputListener> l = new ArrayList<OutputListener>(appEchoListeners);
            for (int i = 0; i < l.size(); i++) {
                l.get(i).putString(string);
            }
        }
    };

   private OutputListener diagnostics = new OutputListener() {
      @Override
        public void putString(String string) {
            ArrayList<OutputListener> l = new ArrayList<OutputListener>(diagnosticsListeners);
            for (int i = 0; i < l.size(); i++) {
                l.get(i).putString(string);
            }
        }
   };

  /////////////    Spec Request Creation/Deletion/Query   ///////////

    private EventRequestSpecList specList = new EventRequestSpecList(this);

    public BreakpointSpec
    createSourceLineBreakpoint(String sourceName, int line) {
        return specList.createSourceLineBreakpoint(sourceName, line);
    }

    public BreakpointSpec
    createClassLineBreakpoint(String classPattern, int line) {
        return specList.createClassLineBreakpoint(classPattern, line);
    }

    public BreakpointSpec
    createMethodBreakpoint(String classPattern,
                           String methodId, List<String> methodArgs) {
        return specList.createMethodBreakpoint(classPattern,
                                                 methodId, methodArgs);
    }

    public ExceptionSpec
    createExceptionIntercept(String classPattern,
                             boolean notifyCaught,
                             boolean notifyUncaught) {
        return specList.createExceptionIntercept(classPattern,
                                                   notifyCaught,
                                                   notifyUncaught);
    }

    public AccessWatchpointSpec
    createAccessWatchpoint(String classPattern, String fieldId) {
        return specList.createAccessWatchpoint(classPattern, fieldId);
    }

    public ModificationWatchpointSpec
    createModificationWatchpoint(String classPattern, String fieldId) {
        return specList.createModificationWatchpoint(classPattern,
                                                       fieldId);
    }

    public void delete(EventRequestSpec spec) {
        specList.delete(spec);
    }

    void resolve(ReferenceType refType) {
        specList.resolve(refType);
    }

    public void install(EventRequestSpec spec) {
        specList.install(spec, vm());
    }

    public List<EventRequestSpec> eventRequestSpecs() {
        return specList.eventRequestSpecs();
    }
}
