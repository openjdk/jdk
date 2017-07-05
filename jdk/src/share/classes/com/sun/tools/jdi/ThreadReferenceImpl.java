/*
 * Copyright 1998-2005 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 */

package com.sun.tools.jdi;

import com.sun.jdi.*;
import com.sun.jdi.request.BreakpointRequest;
import java.util.*;
import java.lang.ref.WeakReference;

public class ThreadReferenceImpl extends ObjectReferenceImpl
             implements ThreadReference, VMListener {
    static final int SUSPEND_STATUS_SUSPENDED = 0x1;
    static final int SUSPEND_STATUS_BREAK = 0x2;

    private int suspendedZombieCount = 0;

    /*
     * Some objects can only be created while a thread is suspended and are valid
     * only while the thread remains suspended.  Examples are StackFrameImpl
     * and MonitorInfoImpl.  When the thread resumes, these objects have to be
     * marked as invalid so that their methods can throw
     * InvalidStackFrameException if they are called.  To do this, such objects
     * register themselves as listeners of the associated thread.  When the
     * thread is resumed, its listeners are notified and mark themselves
     * invalid.
     * Also, note that ThreadReferenceImpl itself caches some info that
     * is valid only as long as the thread is suspended.  When the thread
     * is resumed, that cache must be purged.
     * Lastly, note that ThreadReferenceImpl and its super, ObjectReferenceImpl
     * cache some info that is only valid as long as the entire VM is suspended.
     * If _any_ thread is resumed, this cache must be purged.  To handle this,
     * both ThreadReferenceImpl and ObjectReferenceImpl register themselves as
     * VMListeners so that they get notified when all threads are suspended and
     * when any thread is resumed.
     */

    // This is cached for the life of the thread
    private ThreadGroupReference threadGroup;

    // This is cached only while this one thread is suspended.  Each time
    // the thread is resumed, we clear this and start with a fresh one.
    private static class LocalCache {
        JDWP.ThreadReference.Status status = null;
        List<StackFrame> frames = null;
        int framesStart = -1;
        int framesLength = 0;
        int frameCount = -1;
        List<ObjectReference> ownedMonitors = null;
        List<MonitorInfo> ownedMonitorsInfo = null;
        ObjectReference contendedMonitor = null;
        boolean triedCurrentContended = false;
    }

    private LocalCache localCache;

    private void resetLocalCache() {
        localCache = new LocalCache();
    }

    // This is cached only while all threads in the VM are suspended
    // Yes, someone could change the name of a thread while it is suspended.
    private static class Cache extends ObjectReferenceImpl.Cache {
        String name = null;
    }
    protected ObjectReferenceImpl.Cache newCache() {
        return new Cache();
    }

    // Listeners - synchronized on vm.state()
    private List<WeakReference<ThreadListener>> listeners = new ArrayList<WeakReference<ThreadListener>>();


    ThreadReferenceImpl(VirtualMachine aVm, long aRef) {
        super(aVm,aRef);
        resetLocalCache();
        vm.state().addListener(this);
    }

    protected String description() {
        return "ThreadReference " + uniqueID();
    }

    /*
     * VMListener implementation
     */
    public boolean vmNotSuspended(VMAction action) {
        if (action.resumingThread() == null) {
            // all threads are being resumed
            synchronized (vm.state()) {
                processThreadAction(new ThreadAction(this,
                                            ThreadAction.THREAD_RESUMABLE));
            }

        }

        /*
         * Othewise, only one thread is being resumed:
         *   if it is us,
         *      we have already done our processThreadAction to notify our
         *      listeners when we processed the resume.
         *   if it is not us,
         *      we don't want to notify our listeners
         *       because we are not being resumed.
         */
        return super.vmNotSuspended(action);
    }

    /**
     * Note that we only cache the name string while suspended because
     * it can change via Thread.setName arbitrarily
     */
    public String name() {
        String name = null;
        try {
            Cache local = (Cache)getCache();

            if (local != null) {
                name = local.name;
            }
            if (name == null) {
                name = JDWP.ThreadReference.Name.process(vm, this)
                                                             .threadName;
                if (local != null) {
                    local.name = name;
                }
            }
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
        return name;
    }

    /*
     * Sends a command to the back end which is defined to do an
     * implicit vm-wide resume.
     */
    PacketStream sendResumingCommand(CommandSender sender) {
        synchronized (vm.state()) {
            processThreadAction(new ThreadAction(this,
                                        ThreadAction.THREAD_RESUMABLE));
            return sender.send();
        }
    }

    public void suspend() {
        try {
            JDWP.ThreadReference.Suspend.process(vm, this);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
        // Don't consider the thread suspended yet. On reply, notifySuspend()
        // will be called.
    }

    public void resume() {
        /*
         * If it's a zombie, we can just update internal state without
         * going to back end.
         */
        if (suspendedZombieCount > 0) {
            suspendedZombieCount--;
            return;
        }

        PacketStream stream;
        synchronized (vm.state()) {
            processThreadAction(new ThreadAction(this,
                                      ThreadAction.THREAD_RESUMABLE));
            stream = JDWP.ThreadReference.Resume.enqueueCommand(vm, this);
        }
        try {
            JDWP.ThreadReference.Resume.waitForReply(vm, stream);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    public int suspendCount() {
        /*
         * If it's a zombie, we maintain the count in the front end.
         */
        if (suspendedZombieCount > 0) {
            return suspendedZombieCount;
        }

        try {
            return JDWP.ThreadReference.SuspendCount.process(vm, this).suspendCount;
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    public void stop(ObjectReference throwable) throws InvalidTypeException {
        validateMirror(throwable);
        // Verify that the given object is a Throwable instance
        List list = vm.classesByName("java.lang.Throwable");
        ClassTypeImpl throwableClass = (ClassTypeImpl)list.get(0);
        if ((throwable == null) ||
            !throwableClass.isAssignableFrom(throwable)) {
             throw new InvalidTypeException("Not an instance of Throwable");
        }

        try {
            JDWP.ThreadReference.Stop.process(vm, this,
                                         (ObjectReferenceImpl)throwable);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    public void interrupt() {
        try {
            JDWP.ThreadReference.Interrupt.process(vm, this);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    private JDWP.ThreadReference.Status jdwpStatus() {
        JDWP.ThreadReference.Status myStatus = localCache.status;
        try {
             if (myStatus == null) {
                 myStatus = JDWP.ThreadReference.Status.process(vm, this);
                if ((myStatus.suspendStatus & SUSPEND_STATUS_SUSPENDED) != 0) {
                    // thread is suspended, we can cache the status.
                    localCache.status = myStatus;
                }
            }
         } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
         return myStatus;
    }

    public int status() {
        return jdwpStatus().threadStatus;
    }

    public boolean isSuspended() {
        return ((suspendedZombieCount > 0) ||
                ((jdwpStatus().suspendStatus & SUSPEND_STATUS_SUSPENDED) != 0));
    }

    public boolean isAtBreakpoint() {
        /*
         * TO DO: This fails to take filters into account.
         */
        try {
            StackFrame frame = frame(0);
            Location location = frame.location();
            List requests = vm.eventRequestManager().breakpointRequests();
            Iterator iter = requests.iterator();
            while (iter.hasNext()) {
                BreakpointRequest request = (BreakpointRequest)iter.next();
                if (location.equals(request.location())) {
                    return true;
                }
            }
            return false;
        } catch (IndexOutOfBoundsException iobe) {
            return false;  // no frames on stack => not at breakpoint
        } catch (IncompatibleThreadStateException itse) {
            // Per the javadoc, not suspended => return false
            return false;
        }
    }

    public ThreadGroupReference threadGroup() {
        /*
         * Thread group can't change, so it's cached once and for all.
         */
        if (threadGroup == null) {
            try {
                threadGroup = JDWP.ThreadReference.ThreadGroup.
                    process(vm, this).group;
            } catch (JDWPException exc) {
                throw exc.toJDIException();
            }
        }
        return threadGroup;
    }

    public int frameCount() throws IncompatibleThreadStateException  {
        try {
            if (localCache.frameCount == -1) {
                localCache.frameCount = JDWP.ThreadReference.FrameCount
                                          .process(vm, this).frameCount;
            }
        } catch (JDWPException exc) {
            switch (exc.errorCode()) {
            case JDWP.Error.THREAD_NOT_SUSPENDED:
            case JDWP.Error.INVALID_THREAD:   /* zombie */
                throw new IncompatibleThreadStateException();
            default:
                throw exc.toJDIException();
            }
        }
        return localCache.frameCount;
    }

    public List<StackFrame> frames() throws IncompatibleThreadStateException  {
        return privateFrames(0, -1);
    }

    public StackFrame frame(int index) throws IncompatibleThreadStateException  {
        List list = privateFrames(index, 1);
        return (StackFrame)list.get(0);
    }

    /**
     * Is the requested subrange within what has been retrieved?
     * local is known to be non-null.  Should only be called from
     * a sync method.
     */
    private boolean isSubrange(LocalCache localCache,
                               int start, int length) {
        if (start < localCache.framesStart) {
            return false;
        }
        if (length == -1) {
            return (localCache.framesLength == -1);
        }
        if (localCache.framesLength == -1) {
            if ((start + length) > (localCache.framesStart +
                                    localCache.frames.size())) {
                throw new IndexOutOfBoundsException();
            }
            return true;
        }
        return ((start + length) <= (localCache.framesStart + localCache.framesLength));
    }

    public List<StackFrame> frames(int start, int length)
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
    synchronized private List<StackFrame> privateFrames(int start, int length)
                              throws IncompatibleThreadStateException  {

        // Lock must be held while creating stack frames so if that two threads
        // do this at the same time, one won't clobber the subset created by the other.

        try {
            if (localCache.frames == null || !isSubrange(localCache, start, length)) {
                JDWP.ThreadReference.Frames.Frame[] jdwpFrames
                    = JDWP.ThreadReference.Frames.
                    process(vm, this, start, length).frames;
                int count = jdwpFrames.length;
                localCache.frames = new ArrayList<StackFrame>(count);

                for (int i = 0; i<count; i++) {
                    if (jdwpFrames[i].location == null) {
                        throw new InternalException("Invalid frame location");
                    }
                    StackFrame frame = new StackFrameImpl(vm, this,
                                                          jdwpFrames[i].frameID,
                                                          jdwpFrames[i].location);
                    // Add to the frame list
                    localCache.frames.add(frame);
                }
                localCache.framesStart = start;
                localCache.framesLength = length;
                return Collections.unmodifiableList(localCache.frames);
            } else {
                int fromIndex = start - localCache.framesStart;
                int toIndex;
                if (length == -1) {
                    toIndex = localCache.frames.size() - fromIndex;
                } else {
                    toIndex = fromIndex + length;
                }
                return Collections.unmodifiableList(localCache.frames.subList(fromIndex, toIndex));
            }
        } catch (JDWPException exc) {
            switch (exc.errorCode()) {
            case JDWP.Error.THREAD_NOT_SUSPENDED:
            case JDWP.Error.INVALID_THREAD:   /* zombie */
                throw new IncompatibleThreadStateException();
            default:
                throw exc.toJDIException();
            }
        }
    }

    public List<ObjectReference> ownedMonitors()  throws IncompatibleThreadStateException  {
        try {
            if (localCache.ownedMonitors == null) {
                localCache.ownedMonitors = Arrays.asList(
                                 (ObjectReference[])JDWP.ThreadReference.OwnedMonitors.
                                         process(vm, this).owned);
                if ((vm.traceFlags & vm.TRACE_OBJREFS) != 0) {
                    vm.printTrace(description() +
                                  " temporarily caching owned monitors"+
                                  " (count = " + localCache.ownedMonitors.size() + ")");
                }
            }
        } catch (JDWPException exc) {
            switch (exc.errorCode()) {
            case JDWP.Error.THREAD_NOT_SUSPENDED:
            case JDWP.Error.INVALID_THREAD:   /* zombie */
                throw new IncompatibleThreadStateException();
            default:
                throw exc.toJDIException();
            }
        }
        return localCache.ownedMonitors;
    }

    public ObjectReference currentContendedMonitor()
                              throws IncompatibleThreadStateException  {
        try {
            if (localCache.contendedMonitor == null &&
                !localCache.triedCurrentContended) {
                localCache.contendedMonitor = JDWP.ThreadReference.CurrentContendedMonitor.
                    process(vm, this).monitor;
                localCache.triedCurrentContended = true;
                if ((localCache.contendedMonitor != null) &&
                    ((vm.traceFlags & vm.TRACE_OBJREFS) != 0)) {
                    vm.printTrace(description() +
                                  " temporarily caching contended monitor"+
                                  " (id = " + localCache.contendedMonitor.uniqueID() + ")");
                }
            }
        } catch (JDWPException exc) {
            switch (exc.errorCode()) {
            case JDWP.Error.INVALID_THREAD:   /* zombie */
                throw new IncompatibleThreadStateException();
            default:
                throw exc.toJDIException();
            }
        }
        return localCache.contendedMonitor;
    }

    public List<MonitorInfo> ownedMonitorsAndFrames()  throws IncompatibleThreadStateException  {
        try {
            if (localCache.ownedMonitorsInfo == null) {
                JDWP.ThreadReference.OwnedMonitorsStackDepthInfo.monitor[] minfo;
                minfo = JDWP.ThreadReference.OwnedMonitorsStackDepthInfo.process(vm, this).owned;

                localCache.ownedMonitorsInfo = new ArrayList<MonitorInfo>(minfo.length);

                for (int i=0; i < minfo.length; i++) {
                    JDWP.ThreadReference.OwnedMonitorsStackDepthInfo.monitor mi =
                                                                         minfo[i];
                    MonitorInfo mon = new MonitorInfoImpl(vm, minfo[i].monitor, this, minfo[i].stack_depth);
                    localCache.ownedMonitorsInfo.add(mon);
                }

                if ((vm.traceFlags & vm.TRACE_OBJREFS) != 0) {
                    vm.printTrace(description() +
                                  " temporarily caching owned monitors"+
                                  " (count = " + localCache.ownedMonitorsInfo.size() + ")");
                    }
                }

        } catch (JDWPException exc) {
            switch (exc.errorCode()) {
            case JDWP.Error.THREAD_NOT_SUSPENDED:
            case JDWP.Error.INVALID_THREAD:   /* zombie */
                throw new IncompatibleThreadStateException();
            default:
                throw exc.toJDIException();
            }
        }
        return localCache.ownedMonitorsInfo;
    }

    public void popFrames(StackFrame frame) throws IncompatibleThreadStateException {
        // Note that interface-wise this functionality belongs
        // here in ThreadReference, but implementation-wise it
        // belongs in StackFrame, so we just forward it.
        if (!frame.thread().equals(this)) {
            throw new IllegalArgumentException("frame does not belong to this thread");
        }
        if (!vm.canPopFrames()) {
            throw new UnsupportedOperationException(
                "target does not support popping frames");
        }
        ((StackFrameImpl)frame).pop();
    }

    public void forceEarlyReturn(Value  returnValue) throws InvalidTypeException,
                                                            ClassNotLoadedException,
                                             IncompatibleThreadStateException {
        if (!vm.canForceEarlyReturn()) {
            throw new UnsupportedOperationException(
                "target does not support the forcing of a method to return early");
        }

        validateMirrorOrNull(returnValue);

        StackFrameImpl sf;
        try {
           sf = (StackFrameImpl)frame(0);
        } catch (IndexOutOfBoundsException exc) {
           throw new InvalidStackFrameException("No more frames on the stack");
        }
        sf.validateStackFrame();
        MethodImpl meth = (MethodImpl)sf.location().method();
        ValueImpl convertedValue  = ValueImpl.prepareForAssignment(returnValue,
                                                                   meth.getReturnValueContainer());

        try {
            JDWP.ThreadReference.ForceEarlyReturn.process(vm, this, convertedValue);
        } catch (JDWPException exc) {
            switch (exc.errorCode()) {
            case JDWP.Error.OPAQUE_FRAME:
                throw new NativeMethodException();
            case JDWP.Error.THREAD_NOT_SUSPENDED:
                throw new IncompatibleThreadStateException(
                         "Thread not suspended");
            case JDWP.Error.THREAD_NOT_ALIVE:
                throw new IncompatibleThreadStateException(
                                     "Thread has not started or has finished");
            case JDWP.Error.NO_MORE_FRAMES:
                throw new InvalidStackFrameException(
                         "No more frames on the stack");
            default:
                throw exc.toJDIException();
            }
        }
    }

    public String toString() {
        return "instance of " + referenceType().name() +
               "(name='" + name() + "', " + "id=" + uniqueID() + ")";
    }

    byte typeValueKey() {
        return JDWP.Tag.THREAD;
    }

    void addListener(ThreadListener listener) {
        synchronized (vm.state()) {
            listeners.add(new WeakReference<ThreadListener>(listener));
        }
    }

    void removeListener(ThreadListener listener) {
        synchronized (vm.state()) {
            Iterator iter = listeners.iterator();
            while (iter.hasNext()) {
                WeakReference ref = (WeakReference)iter.next();
                if (listener.equals(ref.get())) {
                    iter.remove();
                    break;
                }
            }
        }
    }

    /**
     * Propagate the the thread state change information
     * to registered listeners.
     * Must be entered while synchronized on vm.state()
     */
    private void processThreadAction(ThreadAction action) {
        synchronized (vm.state()) {
            Iterator iter = listeners.iterator();
            while (iter.hasNext()) {
                WeakReference ref = (WeakReference)iter.next();
                ThreadListener listener = (ThreadListener)ref.get();
                if (listener != null) {
                    switch (action.id()) {
                        case ThreadAction.THREAD_RESUMABLE:
                            if (!listener.threadResumable(action)) {
                                iter.remove();
                            }
                            break;
                    }
                } else {
                    // Listener is unreachable; clean up
                    iter.remove();
                }
            }

            // Discard our local cache
            resetLocalCache();
        }
    }
}
