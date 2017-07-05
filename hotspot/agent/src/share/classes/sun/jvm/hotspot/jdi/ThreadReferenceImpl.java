/*
 * Copyright 2002-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

package sun.jvm.hotspot.jdi;

import sun.jvm.hotspot.debugger.OopHandle;
import sun.jvm.hotspot.runtime.VMObject;
import sun.jvm.hotspot.runtime.JavaThread;
import sun.jvm.hotspot.runtime.OSThread;
//import sun.jvm.hotspot.runtime.StackFrameStream;
import sun.jvm.hotspot.runtime.JavaVFrame;
import sun.jvm.hotspot.runtime.JavaThreadState;
import sun.jvm.hotspot.runtime.MonitorInfo;
import sun.jvm.hotspot.runtime.ObjectMonitor;
import sun.jvm.hotspot.oops.Oop;
import sun.jvm.hotspot.oops.ObjectHeap;
import sun.jvm.hotspot.oops.Instance;
import sun.jvm.hotspot.oops.OopUtilities;
import sun.jvm.hotspot.oops.Klass;
import sun.jvm.hotspot.utilities.Assert;
import com.sun.jdi.*;
import java.util.*;

public class ThreadReferenceImpl extends ObjectReferenceImpl
             implements ThreadReference, /* imports */ JVMTIThreadState {

    private JavaThread myJavaThread;
    private ArrayList frames;    // StackFrames
    private List ownedMonitors; // List<ObjectReferenceImpl>
    private List ownedMonitorsInfo; // List<MonitorInfo>
    private ObjectReferenceImpl currentContendingMonitor;

    ThreadReferenceImpl(VirtualMachine aVm, sun.jvm.hotspot.runtime.JavaThread aRef) {
        // We are given a JavaThread and save it in our myJavaThread field.
        // But, our parent class is an ObjectReferenceImpl so we need an Oop
        // for it.  JavaThread is a wrapper around a Thread Oop so we get
        // that Oop and give it to our super.
        // We can get it back again by calling ref().
        super(aVm, (Instance)aRef.getThreadObj());
        myJavaThread = aRef;
    }

    ThreadReferenceImpl(VirtualMachine vm, Instance oRef) {
        // Instance must be of type java.lang.Thread
        super(vm, oRef);

        // JavaThread retrieved from java.lang.Thread instance may be null.
        // This is the case for threads not-started and for zombies. Wherever
        // appropriate, check for null instead of resulting in NullPointerException.
        myJavaThread = OopUtilities.threadOopGetJavaThread(oRef);
    }

    // return value may be null. refer to the comment in constructor.
    JavaThread getJavaThread() {
        return myJavaThread;
    }

    protected String description() {
        return "ThreadReference " + uniqueID();
    }

    /**
     * Note that we only cache the name string while suspended because
     * it can change via Thread.setName arbitrarily
     */
    public String name() {
        return OopUtilities.threadOopGetName(ref());
    }

    public void suspend() {
        vm.throwNotReadOnlyException("ThreadReference.suspend()");
    }

    public void resume() {
        vm.throwNotReadOnlyException("ThreadReference.resume()");
    }

    public int suspendCount() {
        // all threads are "suspended" when we attach to process or core.
        // we interpret this as one suspend.
        return 1;
    }

    public void stop(ObjectReference throwable) throws InvalidTypeException {
        vm.throwNotReadOnlyException("ThreadReference.stop()");
    }

    public void interrupt() {
        vm.throwNotReadOnlyException("ThreadReference.interrupt()");
    }

    // refer to jvmtiEnv::GetThreadState
    private int jvmtiGetThreadState() {
        // get most state bits
        int state = OopUtilities.threadOopGetThreadStatus(ref());
        // add more state bits
        if (myJavaThread != null) {
            JavaThreadState jts = myJavaThread.getThreadState();
            if (myJavaThread.isBeingExtSuspended()) {
                state |= JVMTI_THREAD_STATE_SUSPENDED;
            }
            if (jts == JavaThreadState.IN_NATIVE) {
                state |= JVMTI_THREAD_STATE_IN_NATIVE;
            }
            OSThread osThread = myJavaThread.getOSThread();
            if (osThread != null && osThread.interrupted()) {
                state |= JVMTI_THREAD_STATE_INTERRUPTED;
            }
        }
        return state;
    }

    public int status() {
        int state = jvmtiGetThreadState();
        int status = THREAD_STATUS_UNKNOWN;
        // refer to map2jdwpThreadStatus in util.c (back-end)
        if (! ((state & JVMTI_THREAD_STATE_ALIVE) != 0) ) {
            if ((state & JVMTI_THREAD_STATE_TERMINATED) != 0) {
                status = THREAD_STATUS_ZOMBIE;
            } else {
                status = THREAD_STATUS_NOT_STARTED;
            }
        } else {
            if ((state & JVMTI_THREAD_STATE_SLEEPING) != 0) {
                status = THREAD_STATUS_SLEEPING;
            } else if ((state & JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER) != 0) {
                status = THREAD_STATUS_MONITOR;
            } else if ((state & JVMTI_THREAD_STATE_WAITING) != 0) {
                status = THREAD_STATUS_WAIT;
            } else if ((state & JVMTI_THREAD_STATE_RUNNABLE) != 0) {
                status = THREAD_STATUS_RUNNING;
            }
        }
        return status;
    }

    public boolean isSuspended() { //fixme jjh
        // If we want to support doing this for a VM which was being
        // debugged, then we need to fix this.
        // In the meantime, we will say all threads are suspended,
        // otherwise, some things won't work, like the jdb 'up' cmd.
        return true;
    }

    public boolean isAtBreakpoint() { //fixme jjh
        // If we want to support doing this for a VM which was being
        // debugged, then we need to fix this.
        return false;
    }

    public ThreadGroupReference threadGroup() {
        return (ThreadGroupReferenceImpl)vm.threadGroupMirror(
               (Instance)OopUtilities.threadOopGetThreadGroup(ref()));
    }

    public int frameCount() throws IncompatibleThreadStateException  { //fixme jjh
        privateFrames(0, -1);
        return frames.size();
    }

    public List frames() throws IncompatibleThreadStateException  {
        return privateFrames(0, -1);
    }

    public StackFrame frame(int index) throws IncompatibleThreadStateException  {
        List list = privateFrames(index, 1);
        return (StackFrame)list.get(0);
    }

    public List frames(int start, int length)
                              throws IncompatibleThreadStateException  {
        if (length < 0) {
            throw new IndexOutOfBoundsException(
                "length must be greater than or equal to zero");
        }
        return privateFrames(start, length);
    }

    /**
     * Private version of frames() allows "-1" to specify all
     * remaining frames.
     */

    private List privateFrames(int start, int length)
                              throws IncompatibleThreadStateException  {
        if (myJavaThread == null) {
            // for zombies and yet-to-be-started threads we need to throw exception
            throw new IncompatibleThreadStateException();
        }
        if (frames == null) {
            frames = new ArrayList(10);
            JavaVFrame myvf = myJavaThread.getLastJavaVFrameDbg();
            while (myvf != null) {
                StackFrame myFrame = new StackFrameImpl(vm, this, myvf);
                //fixme jjh null should be a Location
                frames.add(myFrame);
                myvf = (JavaVFrame)myvf.javaSender();
            }
        }

        List retVal;
        if (frames.size() == 0) {
            retVal = new ArrayList(0);
        } else {
            int toIndex = start + length;
            if (length == -1) {
                toIndex = frames.size();
            }
            retVal = frames.subList(start, toIndex);
        }
        return Collections.unmodifiableList(retVal);
    }

    // refer to JvmtiEnvBase::get_owned_monitors
    public List ownedMonitors()  throws IncompatibleThreadStateException {
        if (vm.canGetOwnedMonitorInfo() == false) {
            throw new UnsupportedOperationException();
        }

        if (myJavaThread == null) {
           throw new IncompatibleThreadStateException();
        }

        if (ownedMonitors != null) {
            return ownedMonitors;
        }

        ownedMonitorsWithStackDepth();

        for (Iterator omi = ownedMonitorsInfo.iterator(); omi.hasNext(); ) {
            //FIXME : Change the MonitorInfoImpl cast to com.sun.jdi.MonitorInfo
            //        when hotspot start building with jdk1.6.
            ownedMonitors.add(((MonitorInfoImpl)omi.next()).monitor());
        }

        return ownedMonitors;
    }

    // new method since 1.6.
    // Real body will be supplied later.
    public List ownedMonitorsAndFrames() throws IncompatibleThreadStateException {
        if (!vm.canGetMonitorFrameInfo()) {
            throw new UnsupportedOperationException(
                "target does not support getting Monitor Frame Info");
        }

        if (myJavaThread == null) {
           throw new IncompatibleThreadStateException();
        }

        if (ownedMonitorsInfo != null) {
            return ownedMonitorsInfo;
        }

        ownedMonitorsWithStackDepth();
        return ownedMonitorsInfo;
    }

    private void ownedMonitorsWithStackDepth() {

        ownedMonitorsInfo = new ArrayList();
        List lockedObjects = new ArrayList(); // List<OopHandle>
        List stackDepth = new ArrayList(); // List<int>
        ObjectMonitor waitingMonitor = myJavaThread.getCurrentWaitingMonitor();
        ObjectMonitor pendingMonitor = myJavaThread.getCurrentPendingMonitor();
        OopHandle waitingObj = null;
        if (waitingMonitor != null) {
            // save object of current wait() call (if any) for later comparison
            waitingObj = waitingMonitor.object();
        }
        OopHandle pendingObj = null;
        if (pendingMonitor != null) {
            // save object of current enter() call (if any) for later comparison
            pendingObj = pendingMonitor.object();
        }

        JavaVFrame frame = myJavaThread.getLastJavaVFrameDbg();
        int depth=0;
        while (frame != null) {
            List frameMonitors = frame.getMonitors();  // List<MonitorInfo>
            for (Iterator miItr = frameMonitors.iterator(); miItr.hasNext(); ) {
                sun.jvm.hotspot.runtime.MonitorInfo mi = (sun.jvm.hotspot.runtime.MonitorInfo) miItr.next();
                if (mi.eliminated() && frame.isCompiledFrame()) {
                  continue; // skip eliminated monitor
                }
                OopHandle obj = mi.owner();
                if (obj == null) {
                    // this monitor doesn't have an owning object so skip it
                    continue;
                }

                if (obj.equals(waitingObj)) {
                    // the thread is waiting on this monitor so it isn't really owned
                    continue;
                }

                if (obj.equals(pendingObj)) {
                    // the thread is pending on this monitor so it isn't really owned
                    continue;
                }

                boolean found = false;
                for (Iterator loItr = lockedObjects.iterator(); loItr.hasNext(); ) {
                    // check for recursive locks
                    if (obj.equals(loItr.next())) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    // already have this object so don't include it
                    continue;
                }
                // add the owning object to our list
                lockedObjects.add(obj);
                stackDepth.add(new Integer(depth));
            }
            frame = (JavaVFrame) frame.javaSender();
            depth++;
        }

        // now convert List<OopHandle> to List<ObjectReference>
        ObjectHeap heap = vm.saObjectHeap();
        Iterator stk = stackDepth.iterator();
        for (Iterator loItr = lockedObjects.iterator(); loItr.hasNext(); ) {
            Oop obj = heap.newOop((OopHandle)loItr.next());
            ownedMonitorsInfo.add(new MonitorInfoImpl(vm, vm.objectMirror(obj), this,
                                                              ((Integer)stk.next()).intValue()));
        }
    }

    // refer to JvmtiEnvBase::get_current_contended_monitor
    public ObjectReference currentContendedMonitor()
                              throws IncompatibleThreadStateException  {
        if (vm.canGetCurrentContendedMonitor() == false) {
            throw new UnsupportedOperationException();
        }

        if (myJavaThread == null) {
           throw new IncompatibleThreadStateException();
        }
        ObjectMonitor mon = myJavaThread.getCurrentWaitingMonitor();
        if (mon == null) {
           // thread is not doing an Object.wait() call
           mon = myJavaThread.getCurrentPendingMonitor();
           if (mon != null) {
               OopHandle handle = mon.object();
               // If obj == NULL, then ObjectMonitor is raw which doesn't count
               // as contended for this API
               return vm.objectMirror(vm.saObjectHeap().newOop(handle));
           } else {
               // no contended ObjectMonitor
               return null;
           }
        } else {
           // thread is doing an Object.wait() call
           OopHandle handle = mon.object();
           if (Assert.ASSERTS_ENABLED) {
               Assert.that(handle != null, "Object.wait() should have an object");
           }
           Oop obj = vm.saObjectHeap().newOop(handle);
           return vm.objectMirror(obj);
        }
    }


    public void popFrames(StackFrame frame) throws IncompatibleThreadStateException {
        vm.throwNotReadOnlyException("ThreadReference.popFrames()");
    }

    public void forceEarlyReturn(Value returnValue) throws IncompatibleThreadStateException {
        vm.throwNotReadOnlyException("ThreadReference.forceEarlyReturn()");
    }

    public String toString() {
        return "instance of " + referenceType().name() +
               "(name='" + name() + "', " + "id=" + uniqueID() + ")";
    }
}
