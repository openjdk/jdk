/*
 * Copyright (c) 1999, 2001, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.jdi.*;
import com.sun.jdi.request.*;
import com.sun.jdi.event.*;
import java.util.*;
import java.io.*;


/**
 * Framework used by all JDI regression tests
 */
abstract public class JDIScaffold {
    private boolean shouldTrace = false;
    private VMConnection connection;
    private VirtualMachine vm;
    private EventRequestManager requestManager;
    private List listeners = Collections.synchronizedList(new LinkedList());
    ThreadReference vmStartThread = null;
    boolean vmDied = false;
    boolean vmDisconnected = false;

    static private class ArgInfo {
        String targetVMArgs = "";
        String targetAppCommandLine = "";
        String connectorSpec = "com.sun.jdi.CommandLineLaunch:";
        int traceFlags = 0;
    }

    static public interface TargetListener {
        boolean eventSetReceived(EventSet set);
        boolean eventSetComplete(EventSet set);
        boolean eventReceived(Event event);
        boolean breakpointReached(BreakpointEvent event);
        boolean exceptionThrown(ExceptionEvent event);
        boolean stepCompleted(StepEvent event);
        boolean classPrepared(ClassPrepareEvent event);
        boolean classUnloaded(ClassUnloadEvent event);
        boolean methodEntered(MethodEntryEvent event);
        boolean methodExited(MethodExitEvent event);
        boolean fieldAccessed(AccessWatchpointEvent event);
        boolean fieldModified(ModificationWatchpointEvent event);
        boolean threadStarted(ThreadStartEvent event);
        boolean threadDied(ThreadDeathEvent event);
        boolean vmStarted(VMStartEvent event);
        boolean vmDied(VMDeathEvent event);
        boolean vmDisconnected(VMDisconnectEvent event);
    }

    static public class TargetAdapter implements TargetListener {
        public boolean eventSetReceived(EventSet set) {
            return false;
        }
        public boolean eventSetComplete(EventSet set) {
            return false;
        }
        public boolean eventReceived(Event event) {
            return false;
        }
        public boolean breakpointReached(BreakpointEvent event) {
            return false;
        }
        public boolean exceptionThrown(ExceptionEvent event) {
            return false;
        }
        public boolean stepCompleted(StepEvent event) {
            return false;
        }
        public boolean classPrepared(ClassPrepareEvent event) {
            return false;
        }
        public boolean classUnloaded(ClassUnloadEvent event) {
            return false;
        }
        public boolean methodEntered(MethodEntryEvent event) {
            return false;
        }
        public boolean methodExited(MethodExitEvent event) {
            return false;
        }
        public boolean fieldAccessed(AccessWatchpointEvent event) {
            return false;
        }
        public boolean fieldModified(ModificationWatchpointEvent event) {
            return false;
        }
        public boolean threadStarted(ThreadStartEvent event) {
            return false;
        }
        public boolean threadDied(ThreadDeathEvent event) {
            return false;
        }
        public boolean vmStarted(VMStartEvent event) {
            return false;
        }
        public boolean vmDied(VMDeathEvent event) {
            return false;
        }
        public boolean vmDisconnected(VMDisconnectEvent event) {
            return false;
        }
    }

    private class EventHandler implements Runnable {
        EventHandler() {
            Thread thread = new Thread(this);
            thread.setDaemon(true);
            thread.start();
        }

        private boolean notifyEvent(TargetListener listener, Event event) {
            if (listener.eventReceived(event) == true) {
                return true;
            } else if (event instanceof BreakpointEvent) {
                return listener.breakpointReached((BreakpointEvent)event);
            } else if (event instanceof ExceptionEvent) {
                return listener.exceptionThrown((ExceptionEvent)event);
            } else if (event instanceof StepEvent) {
                return listener.stepCompleted((StepEvent)event);
            } else if (event instanceof ClassPrepareEvent) {
                return listener.classPrepared((ClassPrepareEvent)event);
            } else if (event instanceof ClassUnloadEvent) {
                return listener.classUnloaded((ClassUnloadEvent)event);
            } else if (event instanceof MethodEntryEvent) {
                return listener.methodEntered((MethodEntryEvent)event);
            } else if (event instanceof MethodExitEvent) {
                return listener.methodExited((MethodExitEvent)event);
            } else if (event instanceof AccessWatchpointEvent) {
                return listener.fieldAccessed((AccessWatchpointEvent)event);
            } else if (event instanceof ModificationWatchpointEvent) {
                return listener.fieldModified((ModificationWatchpointEvent)event);
            } else if (event instanceof ThreadStartEvent) {
                return listener.threadStarted((ThreadStartEvent)event);
            } else if (event instanceof ThreadDeathEvent) {
                return listener.threadDied((ThreadDeathEvent)event);
            } else if (event instanceof VMStartEvent) {
                return listener.vmStarted((VMStartEvent)event);
            } else if (event instanceof VMDeathEvent) {
                return listener.vmDied((VMDeathEvent)event);
            } else if (event instanceof VMDisconnectEvent) {
                return listener.vmDisconnected((VMDisconnectEvent)event);
            } else {
                throw new InternalError("Unknown event type: " + event.getClass());
            }
        }

        private void traceSuspendPolicy(int policy) {
            if (shouldTrace) {
                switch (policy) {
                case EventRequest.SUSPEND_NONE:
                    traceln("JDI: runloop: suspend = SUSPEND_NONE");
                    break;
                case EventRequest.SUSPEND_ALL:
                    traceln("JDI: runloop: suspend = SUSPEND_ALL");
                    break;
                case EventRequest.SUSPEND_EVENT_THREAD:
                    traceln("JDI: runloop: suspend = SUSPEND_EVENT_THREAD");
                    break;
                }
            }
        }

        public void run() {
            boolean connected = true;
            do {
                try {
                    EventSet set = vm.eventQueue().remove();
                    traceSuspendPolicy(set.suspendPolicy());
                    synchronized (listeners) {
                        ListIterator iter = listeners.listIterator();
                        while (iter.hasNext()) {
                            TargetListener listener = (TargetListener)iter.next();
                            traceln("JDI: runloop: listener = " + listener);
                            if (listener.eventSetReceived(set) == true) {
                                iter.remove();
                            } else {
                                Iterator jter = set.iterator();
                                while (jter.hasNext()) {
                                    Event event = (Event)jter.next();
                                    traceln("JDI: runloop:    event = " + event.getClass());
                                    if (event instanceof VMDisconnectEvent) {
                                        connected = false;
                                    }
                                    if (notifyEvent(listener, event) == true) {
                                        iter.remove();
                                        break;
                                    }
                                }
                                traceln("JDI: runloop:   end of events loop");
                                if (listener.eventSetComplete(set) == true) {
                                    iter.remove();
                                }
                            }
                        traceln("JDI: runloop: end of listener");
                        }
                    }
                } catch (InterruptedException e) {
                }
                traceln("JDI: runloop: end of outer loop");
            } while (connected);
        }
    }

    /**
     * Constructor
     */
    public JDIScaffold() {
    }

    public void enableScaffoldTrace() {
        this.shouldTrace = true;
    }

    public void disableScaffoldTrace() {
        this.shouldTrace = false;
    }


    /*
     * Test cases should implement tests in runTests and should
     * initiate testing by calling run().
     */
    abstract protected void runTests() throws Exception;

    final public void startTests() throws Exception {
        try {
            runTests();
        } finally {
            shutdown();
        }
    }

    protected void println(String str) {
        System.err.println(str);
    }

    protected void traceln(String str) {
        if (shouldTrace) {
            println(str);
        }
    }

    private ArgInfo parseArgs(String args[]) {
        ArgInfo argInfo = new ArgInfo();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-connect")) {
                i++;
                argInfo.connectorSpec = args[i];
            } else if (args[i].equals("-trace")) {
                i++;
                argInfo.traceFlags = Integer.decode(args[i]).intValue();
            } else if (args[i].startsWith("-J")) {
                argInfo.targetVMArgs += (args[i].substring(2) + ' ');

                /*
                 * classpath can span two arguments so we need to handle
                 * it specially.
                 */
                if (args[i].equals("-J-classpath")) {
                    i++;
                    argInfo.targetVMArgs += (args[i] + ' ');
                }
            } else {
                argInfo.targetAppCommandLine += (args[i] + ' ');
            }
        }
        return argInfo;
    }

    public void connect(String args[]) {
        ArgInfo argInfo = parseArgs(args);

        argInfo.targetVMArgs += VMConnection.getDebuggeeVMOptions();
        connection = new VMConnection(argInfo.connectorSpec,
                                      argInfo.traceFlags);
        if (!connection.isLaunch()) {
            throw new UnsupportedOperationException(
                                 "Listening and Attaching not yet supported");
        }

        /*
         * Add a listener to track VM start/death/disconnection and
         * to update status fields accordingly.
         */
        addListener(new TargetAdapter() {
                        public boolean vmStarted(VMStartEvent event) {
                            traceln("JDI: listener1:  got VMStart");
                            synchronized(JDIScaffold.this) {
                                vmStartThread = event.thread();
                                JDIScaffold.this.notifyAll();
                            }
                            return false;
                        }

                        public boolean vmDied(VMDeathEvent event) {
                            traceln("JDI: listener1:  got VMDeath");
                            synchronized(JDIScaffold.this) {
                                vmDied = true;
                                JDIScaffold.this.notifyAll();
                            }
                            return false;
                        }

                        public boolean vmDisconnected(VMDisconnectEvent event) {
                            traceln("JDI: listener1:  got VMDisconnectedEvent");
                            synchronized(JDIScaffold.this) {
                                vmDisconnected = true;
                                JDIScaffold.this.notifyAll();
                            }
                            return false;
                        }
                    });

        if (connection.connector().name().equals("com.sun.jdi.CommandLineLaunch")) {
            if (argInfo.targetVMArgs.length() > 0) {
                if (connection.connectorArg("options").length() > 0) {
                    throw new IllegalArgumentException("VM options in two places");
                }
                connection.setConnectorArg("options", argInfo.targetVMArgs);
            }
            if (argInfo.targetAppCommandLine.length() > 0) {
                if (connection.connectorArg("main").length() > 0) {
                    throw new IllegalArgumentException("Command line in two places");
                }
                connection.setConnectorArg("main", argInfo.targetAppCommandLine);
            }
        }

        vm = connection.open();
        requestManager = vm.eventRequestManager();
        new EventHandler();
    }


    public VirtualMachine vm() {
        return vm;
    }

    public EventRequestManager eventRequestManager() {
        return requestManager;
    }

    public void addListener(TargetListener listener) {
        listeners.add(listener);
    }

    public void removeListener(TargetListener listener) {
        listeners.remove(listener);
    }

    public synchronized ThreadReference waitForVMStart() {
        while ((vmStartThread == null) && !vmDisconnected) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }

        if (vmStartThread == null) {
            throw new VMDisconnectedException();
        }

        return vmStartThread;
    }

    public synchronized void waitForVMDeath() {
        while (!vmDied && !vmDisconnected) {
            try {
                traceln("JDI: waitForVMDeath:  waiting");
                wait();
            } catch (InterruptedException e) {
            }
        }
        traceln("JDI: waitForVMDeath:  done waiting");

        if (!vmDied) {
            throw new VMDisconnectedException();
        }
    }

    public Event waitForRequestedEvent(final EventRequest request) {
        class EventNotification {
            Event event;
            boolean disconnected = false;
        }
        final EventNotification en = new EventNotification();

        TargetAdapter adapter = new TargetAdapter() {
            public boolean eventReceived(Event event) {
                if (request.equals(event.request())) {
                    synchronized (en) {
                        en.event = event;
                        en.notifyAll();
                    }
                    return true;
                } else if (event instanceof VMDisconnectEvent) {
                    synchronized (en) {
                        en.disconnected = true;
                        en.notifyAll();
                    }
                    return true;
                } else {
                    return false;
                }
            }
        };

        addListener(adapter);

        try {
            synchronized (en) {
                vm.resume();
                while (!en.disconnected && (en.event == null)) {
                    en.wait();
                }
            }
        } catch (InterruptedException e) {
            return null;
        }

        if (en.disconnected) {
            throw new RuntimeException("VM Disconnected before requested event occurred");
        }
        return en.event;
    }

    private StepEvent doStep(ThreadReference thread, int gran, int depth) {
        final StepRequest sr =
                  requestManager.createStepRequest(thread, gran, depth);

        sr.addClassExclusionFilter("java.*");
        sr.addClassExclusionFilter("javax.*");
        sr.addClassExclusionFilter("sun.*");
        sr.addClassExclusionFilter("com.sun.*");
        sr.addClassExclusionFilter("com.oracle.*");
        sr.addClassExclusionFilter("oracle.*");
        sr.addClassExclusionFilter("jdk.internal.*");
        sr.addCountFilter(1);
        sr.enable();
        StepEvent retEvent = (StepEvent)waitForRequestedEvent(sr);
        requestManager.deleteEventRequest(sr);
        return retEvent;
    }

    public StepEvent stepIntoInstruction(ThreadReference thread) {
        return doStep(thread, StepRequest.STEP_MIN, StepRequest.STEP_INTO);
    }

    public StepEvent stepIntoLine(ThreadReference thread) {
        return doStep(thread, StepRequest.STEP_LINE, StepRequest.STEP_INTO);
    }

    public StepEvent stepOverInstruction(ThreadReference thread) {
        return doStep(thread, StepRequest.STEP_MIN, StepRequest.STEP_OVER);
    }

    public StepEvent stepOverLine(ThreadReference thread) {
        return doStep(thread, StepRequest.STEP_LINE, StepRequest.STEP_OVER);
    }

    public StepEvent stepOut(ThreadReference thread) {
        return doStep(thread, StepRequest.STEP_LINE, StepRequest.STEP_OUT);
    }

    public BreakpointEvent resumeTo(Location loc) {
        final BreakpointRequest request =
            requestManager.createBreakpointRequest(loc);
        request.addCountFilter(1);
        request.enable();
        return (BreakpointEvent)waitForRequestedEvent(request);
    }

    public ReferenceType findReferenceType(String name) {
        List rts = vm.classesByName(name);
        Iterator iter = rts.iterator();
        while (iter.hasNext()) {
            ReferenceType rt = (ReferenceType)iter.next();
            if (rt.name().equals(name)) {
                return rt;
            }
        }
        return null;
    }

    public Method findMethod(ReferenceType rt, String name, String signature) {
        List methods = rt.methods();
        Iterator iter = methods.iterator();
        while (iter.hasNext()) {
            Method method = (Method)iter.next();
            if (method.name().equals(name) &&
                method.signature().equals(signature)) {
                return method;
            }
        }
        return null;
    }

    public Location findLocation(ReferenceType rt, int lineNumber)
                         throws AbsentInformationException {
        List locs = rt.locationsOfLine(lineNumber);
        if (locs.size() == 0) {
            throw new IllegalArgumentException("Bad line number");
        } else if (locs.size() > 1) {
            throw new IllegalArgumentException("Line number has multiple locations");
        }

        return (Location)locs.get(0);
    }

    public BreakpointEvent resumeTo(String clsName, String methodName,
                                         String methodSignature) {
        ReferenceType rt = findReferenceType(clsName);
        if (rt == null) {
            rt = resumeToPrepareOf(clsName).referenceType();
        }

        Method method = findMethod(rt, methodName, methodSignature);
        if (method == null) {
            throw new IllegalArgumentException("Bad method name/signature");
        }

        return resumeTo(method.location());
    }

    public BreakpointEvent resumeTo(String clsName, int lineNumber) throws AbsentInformationException {
        ReferenceType rt = findReferenceType(clsName);
        if (rt == null) {
            rt = resumeToPrepareOf(clsName).referenceType();
        }

        return resumeTo(findLocation(rt, lineNumber));
    }

    public ClassPrepareEvent resumeToPrepareOf(String className) {
        final ClassPrepareRequest request =
            requestManager.createClassPrepareRequest();
        request.addClassFilter(className);
        request.addCountFilter(1);
        request.enable();
        return (ClassPrepareEvent)waitForRequestedEvent(request);
    }

    public void resumeToVMDeath() {
        // If we are very close to VM death, we might get a VM disconnect
        // before resume is complete. In that case ignore any VMDisconnectException
        // and let the waitForVMDeath to clean up.
        try {
            traceln("JDI: resumeToVMDeath:  resuming");
            vm.resume();
            traceln("JDI: resumeToVMDeath:  resumed");
        } catch (VMDisconnectedException e) {
            // clean up below
        }
        waitForVMDeath();
    }

    public void shutdown() {
        shutdown(null);
    }

    public void shutdown(String message) {
        if ((connection != null) && !vmDied) {
            try {
                connection.disposeVM();
            } catch (VMDisconnectedException e) {
                // Shutting down after the VM has gone away. This is
                // not an error, and we just ignore it.
            }
        }
        if (message != null) {
            System.out.println(message);
        }
    }
}
