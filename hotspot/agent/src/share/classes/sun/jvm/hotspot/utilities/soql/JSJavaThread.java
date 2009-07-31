/*
 * Copyright 2004-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.jvm.hotspot.utilities.soql;

import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;

/**
 * Wraps a JavaThread instance in the target VM.
 */
public class JSJavaThread extends JSJavaInstance {
    public JSJavaThread(Instance threadOop, JSJavaFactory fac) {
        super(threadOop, fac);
        // JavaThread retrieved from java.lang.Thread instance may be null.
        // This is the case for threads not-started and for zombies. Wherever
        // appropriate, check for null instead of resulting in NullPointerException.
        this.jthread = OopUtilities.threadOopGetJavaThread(threadOop);
    }

    public JSJavaThread(JavaThread jt, JSJavaFactory fac) {
        super((Instance) jt.getThreadObj(), fac);
        this.jthread = jt;
    }

    public String toString() {
        String name = getName();
        StringBuffer buf = new StringBuffer();
        buf.append("Thread (address=");
        buf.append(getOop().getHandle());
        buf.append(", name=");
        if (name != null) {
            buf.append(name);
        } else {
            buf.append("<unnamed>");
        }
        buf.append(')');
        return buf.toString();
    }

    protected Object getFieldValue(String name) {
        if (name.equals("name")) {
            return getName();
        } else if (name.equals("frames")) {
            return getFrames();
        } else if (name.equals("monitors")) {
            return getOwnedMonitors();
        } else {
            return super.getFieldValue(name);
        }
    }

    protected String[] getFieldNames() {
        String[] flds = super.getFieldNames();
        String[] res = new String[flds.length + 2];
        System.arraycopy(flds, 0, res, 0, flds.length);
        res[flds.length] = "frames";
        res[flds.length + 1] = "monitors";
        return res;
    }

    protected boolean hasField(String name) {
        if (name.equals("frames") || name.equals("monitors")) {
            return true;
        } else {
            return super.hasField(name);
        }
    }

    //-- Internals only below this point
    private String getName() {
        return OopUtilities.threadOopGetName(getOop());
    }

    private synchronized JSList getFrames() {
        if (framesCache == null) {
            final List list = new ArrayList(0);
            if (jthread != null) {
                JavaVFrame jvf = jthread.getLastJavaVFrameDbg();
                while (jvf != null) {
                    list.add(jvf);
                    jvf = jvf.javaSender();
                }
            }
            framesCache = factory.newJSList(list);
        }
        return framesCache;
    }

    private synchronized JSList getOwnedMonitors() {
        if (monitorsCache == null) {
            final List ownedMonitors = new ArrayList(0);
            if (jthread != null) {
                List lockedObjects = new ArrayList(); // List<OopHandle>

                ObjectMonitor waitingMonitor = jthread.getCurrentWaitingMonitor();
                OopHandle waitingObj = null;
                if (waitingMonitor != null) {
                   // save object of current wait() call (if any) for later comparison
                   waitingObj = waitingMonitor.object();
                }

                ObjectMonitor pendingMonitor = jthread.getCurrentPendingMonitor();
                OopHandle pendingObj = null;
                if (pendingMonitor != null) {
                    // save object of current enter() call (if any) for later comparison
                    pendingObj = pendingMonitor.object();
                }

                JavaVFrame frame = jthread.getLastJavaVFrameDbg();
                while (frame != null) {
                    List frameMonitors = frame.getMonitors();  // List<MonitorInfo>
                    for (Iterator miItr = frameMonitors.iterator(); miItr.hasNext(); ) {
                        MonitorInfo mi = (MonitorInfo) miItr.next();

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
                    }
                    frame = (JavaVFrame) frame.javaSender();
                }

                // now convert List<OopHandle> to List<Oop>
                ObjectHeap heap = VM.getVM().getObjectHeap();
                for (Iterator loItr = lockedObjects.iterator(); loItr.hasNext(); ) {
                    ownedMonitors.add(heap.newOop((OopHandle)loItr.next()));
                }
            }
            monitorsCache = factory.newJSList(ownedMonitors);
        }
        return monitorsCache;
    }

    private JavaThread jthread;
    private JSList framesCache;
    private JSList monitorsCache;
}
