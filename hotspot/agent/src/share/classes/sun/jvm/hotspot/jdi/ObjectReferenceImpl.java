/*
 * Copyright 2002-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.*;
import com.sun.jdi.*;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.debugger.OopHandle;
import sun.jvm.hotspot.oops.Oop;
import sun.jvm.hotspot.oops.Mark;
import sun.jvm.hotspot.oops.Instance;
import sun.jvm.hotspot.oops.Array;
import sun.jvm.hotspot.oops.OopUtilities;
import sun.jvm.hotspot.oops.Klass;
import sun.jvm.hotspot.oops.DefaultHeapVisitor;
import sun.jvm.hotspot.runtime.JavaThread;
import sun.jvm.hotspot.runtime.JavaVFrame;
import sun.jvm.hotspot.runtime.MonitorInfo;
import sun.jvm.hotspot.runtime.ObjectMonitor;
import sun.jvm.hotspot.runtime.Threads;
import sun.jvm.hotspot.utilities.Assert;

import java.util.*;

public class ObjectReferenceImpl extends ValueImpl implements ObjectReference {
    private Oop  saObject;
    private long myID;
    private boolean monitorInfoCached = false;
    private ThreadReferenceImpl owningThread = null;
    private List waitingThreads = null; // List<ThreadReferenceImpl>
    private int entryCount = 0;

    private static long nextID = 0L;
    private static synchronized long nextID() {
        return nextID++;
    }

    ObjectReferenceImpl(VirtualMachine aVm, sun.jvm.hotspot.oops.Oop oRef) {
        super(aVm);
        saObject = oRef;
        myID = nextID();
    }

    protected Oop ref() {
        return saObject;
    }

    public Type type() {
        return referenceType();
    }

    public ReferenceType referenceType() {
        Klass myKlass = ref().getKlass();
        return vm.referenceType(myKlass);
    }

    public Value getValue(Field sig) {
        List list = new ArrayList(1);
        list.add(sig);
        Map map = getValues(list);
        return(Value)map.get(sig);
    }

    public Map getValues(List theFields) {
        //validateMirrors(theFields);

        List staticFields = new ArrayList(0);
        int size = theFields.size();
        List instanceFields = new ArrayList(size);

        for (int i=0; i<size; i++) {
            sun.jvm.hotspot.jdi.FieldImpl field =
                (sun.jvm.hotspot.jdi.FieldImpl)theFields.get(i);

            // Make sure the field is valid
            ((ReferenceTypeImpl)referenceType()).validateFieldAccess(field);

            // FIX ME! We need to do some sanity checking
            // here; make sure the field belongs to this
            // object.
            if (field.isStatic()) {
                staticFields.add(field);
            } else {
                instanceFields.add(field);
            }
        }

        // Look up static field(s) first to mimic the JDI implementation
        Map map;
        if (staticFields.size() > 0) {
            map = referenceType().getValues(staticFields);
        } else {
            map = new HashMap(size);
        }

        // Then get instance field(s)
        size = instanceFields.size();
        for (int ii=0; ii<size; ii++){
            FieldImpl fieldImpl = (FieldImpl)instanceFields.get(ii);
            map.put(fieldImpl, fieldImpl.getValue(saObject));
        }

        return map;
    }

    public void setValue(Field field, Value value)
                   throws InvalidTypeException, ClassNotLoadedException {
        vm.throwNotReadOnlyException("ObjectReference.setValue(...)");
    }

    public Value invokeMethod(ThreadReference threadIntf, Method methodIntf,
                              List arguments, int options)
                              throws InvalidTypeException,
                                     IncompatibleThreadStateException,
                                     InvocationException,
                                     ClassNotLoadedException {
        vm.throwNotReadOnlyException("ObjectReference.invokeMethod(...)");
        return null;
    }

    public void disableCollection() {
        vm.throwNotReadOnlyException("ObjectReference.disableCollection()");
    }

    public void enableCollection() {
        vm.throwNotReadOnlyException("ObjectReference.enableCollection()");
    }

    public boolean isCollected() {
        vm.throwNotReadOnlyException("ObjectReference.isCollected()");
        return false;
    }

    public long uniqueID() {
        return myID;
    }

    public List waitingThreads() throws IncompatibleThreadStateException {
        if (vm.canGetMonitorInfo() == false) {
            throw new UnsupportedOperationException();
        }

        if (! monitorInfoCached) {
            computeMonitorInfo();
        }
        return waitingThreads;
    }


    public ThreadReference owningThread() throws IncompatibleThreadStateException {
        if (vm.canGetMonitorInfo() == false) {
            throw new UnsupportedOperationException();
        }

        if (! monitorInfoCached) {
            computeMonitorInfo();
        }
        return owningThread;
    }


    public int entryCount() throws IncompatibleThreadStateException {
        if (vm.canGetMonitorInfo() == false) {
            throw new UnsupportedOperationException();
        }

        if (! monitorInfoCached) {
            computeMonitorInfo();
        }
        return entryCount;
    }

    // new method since 1.6.
    // Real body will be supplied later.
    public List referringObjects(long maxReferrers) {
        if (!vm.canGetInstanceInfo()) {
            throw new UnsupportedOperationException(
                      "target does not support getting instances");
        }
        if (maxReferrers < 0) {
            throw new IllegalArgumentException("maxReferrers is less than zero: "
                                              + maxReferrers);
        }
        final ObjectReference obj = this;
        final List objects = new ArrayList(0);
        final long max = maxReferrers;
                vm.saObjectHeap().iterate(new DefaultHeapVisitor() {
                private long refCount = 0;
                public boolean doObj(Oop oop) {
                                        try {
                                                ObjectReference objref = vm.objectMirror(oop);
                                                List fields = objref.referenceType().allFields();
                                                for (int i=0; i < fields.size(); i++) {
                                                        Field fld = (Field)fields.get(i);
                                                        if (objref.getValue(fld).equals(obj) && !objects.contains(objref)) {
                                                                objects.add(objref);
                                                                refCount++;
                                                        }
                                                }
                                                if (max > 0 && refCount >= max) {
                                                        return true;
                                                }
                                        } catch  (RuntimeException x) {
                                          // Ignore RuntimeException thrown from vm.objectMirror(oop)
                                          // for bad oop. It is possible to see some bad oop
                                          // because heap might be iterating at no safepoint.
                                        }
                                        return false;

                }
            });
        return objects;
    }

    // refer to JvmtiEnvBase::count_locked_objects.
    // Count the number of objects for a lightweight monitor. The obj
    // parameter is object that owns the monitor so this routine will
    // count the number of times the same object was locked by frames
    // in JavaThread. i.e., we count total number of times the same
    // object is (lightweight) locked by given thread.
    private int countLockedObjects(JavaThread jt, Oop obj) {
        int res = 0;
        JavaVFrame frame = jt.getLastJavaVFrameDbg();
        while (frame != null) {
            List monitors = frame.getMonitors();
            OopHandle givenHandle = obj.getHandle();
            for (Iterator itr = monitors.iterator(); itr.hasNext();) {
                MonitorInfo mi = (MonitorInfo) itr.next();
                if (mi.eliminated() && frame.isCompiledFrame()) continue; // skip eliminated monitor
                if (givenHandle.equals(mi.owner())) {
                    res++;
                }
            }
            frame = (JavaVFrame) frame.javaSender();
        }
        return res;
    }

    // wrappers on same named method of Threads class
    // returns List<JavaThread>
    private List getPendingThreads(ObjectMonitor mon) {
        return vm.saVM().getThreads().getPendingThreads(mon);
    }

    // returns List<JavaThread>
    private List getWaitingThreads(ObjectMonitor mon) {
        return vm.saVM().getThreads().getWaitingThreads(mon);
    }

    private JavaThread owningThreadFromMonitor(Address addr) {
        return vm.saVM().getThreads().owningThreadFromMonitor(addr);
    }

    // refer to JvmtiEnv::GetObjectMonitorUsage
    private void computeMonitorInfo() {
        monitorInfoCached = true;
        Mark mark = saObject.getMark();
        ObjectMonitor mon = null;
        Address owner = null;
        // check for heavyweight monitor
        if (! mark.hasMonitor()) {
            // check for lightweight monitor
            if (mark.hasLocker()) {
                owner = mark.locker().getAddress(); // save the address of the Lock word
            }
            // implied else: no owner
        } else {
            // this object has a heavyweight monitor
            mon = mark.monitor();

            // The owner field of a heavyweight monitor may be NULL for no
            // owner, a JavaThread * or it may still be the address of the
            // Lock word in a JavaThread's stack. A monitor can be inflated
            // by a non-owning JavaThread, but only the owning JavaThread
            // can change the owner field from the Lock word to the
            // JavaThread * and it may not have done that yet.
            owner = mon.owner();
        }

        // find the owning thread
        if (owner != null) {
            owningThread = vm.threadMirror(owningThreadFromMonitor(owner));
        }

        // compute entryCount
        if (owningThread != null) {
            if (owningThread.getJavaThread().getAddress().equals(owner)) {
                // the owner field is the JavaThread *
                if (Assert.ASSERTS_ENABLED) {
                    Assert.that(false, "must have heavyweight monitor with JavaThread * owner");
                }
                entryCount = (int) mark.monitor().recursions() + 1;
            } else {
                // The owner field is the Lock word on the JavaThread's stack
                // so the recursions field is not valid. We have to count the
                // number of recursive monitor entries the hard way.
                entryCount = countLockedObjects(owningThread.getJavaThread(), saObject);
            }
        }

        // find the contenders & waiters
        waitingThreads = new ArrayList();
        if (mon != null) {
            // this object has a heavyweight monitor. threads could
            // be contenders or waiters
            // add all contenders
            List pendingThreads = getPendingThreads(mon);
            // convert the JavaThreads to ThreadReferenceImpls
            for (Iterator itrPend = pendingThreads.iterator(); itrPend.hasNext();) {
                waitingThreads.add(vm.threadMirror((JavaThread) itrPend.next()));
            }

            // add all waiters (threads in Object.wait())
            // note that we don't do this JVMTI way. To do it JVMTI way,
            // we would need to access ObjectWaiter list maintained in
            // ObjectMonitor::_queue. But we don't have this struct exposed
            // in vmStructs. We do waiters list in a way similar to getting
            // pending threads list
            List objWaitingThreads = getWaitingThreads(mon);
            // convert the JavaThreads to ThreadReferenceImpls
            for (Iterator itrWait = objWaitingThreads.iterator(); itrWait.hasNext();) {
                waitingThreads.add(vm.threadMirror((JavaThread) itrWait.next()));
            }
        }
    }

    public boolean equals(Object obj) {
        if ((obj != null) && (obj instanceof ObjectReferenceImpl)) {
            ObjectReferenceImpl other = (ObjectReferenceImpl)obj;
            return (ref().equals(other.ref())) &&
                   super.equals(obj);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return saObject.hashCode();
    }

    public String toString() {
        return  "instance of " + referenceType().name() + "(id=" + uniqueID() + ")";
    }
}
