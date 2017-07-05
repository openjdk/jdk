/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.jvm.hotspot.runtime;

import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.runtime.solaris_sparc.SolarisSPARCJavaThreadPDAccess;
import sun.jvm.hotspot.runtime.solaris_x86.SolarisX86JavaThreadPDAccess;
import sun.jvm.hotspot.runtime.solaris_amd64.SolarisAMD64JavaThreadPDAccess;
import sun.jvm.hotspot.runtime.win32_amd64.Win32AMD64JavaThreadPDAccess;
import sun.jvm.hotspot.runtime.win32_x86.Win32X86JavaThreadPDAccess;
import sun.jvm.hotspot.runtime.linux_x86.LinuxX86JavaThreadPDAccess;
import sun.jvm.hotspot.runtime.linux_amd64.LinuxAMD64JavaThreadPDAccess;
import sun.jvm.hotspot.runtime.linux_sparc.LinuxSPARCJavaThreadPDAccess;
import sun.jvm.hotspot.runtime.bsd_x86.BsdX86JavaThreadPDAccess;
import sun.jvm.hotspot.runtime.bsd_amd64.BsdAMD64JavaThreadPDAccess;
import sun.jvm.hotspot.utilities.*;

public class Threads {
    private static JavaThreadFactory threadFactory;
    private static AddressField      threadListField;
    private static CIntegerField     numOfThreadsField;
    private static VirtualConstructor virtualConstructor;
    private static JavaThreadPDAccess access;

    static {
        VM.registerVMInitializedObserver(new Observer() {
            public void update(Observable o, Object data) {
                initialize(VM.getVM().getTypeDataBase());
            }
        });
    }

    private static synchronized void initialize(TypeDataBase db) {
        Type type = db.lookupType("Threads");

        threadListField = type.getAddressField("_thread_list");
        numOfThreadsField = type.getCIntegerField("_number_of_threads");

        // Instantiate appropriate platform-specific JavaThreadFactory
        String os  = VM.getVM().getOS();
        String cpu = VM.getVM().getCPU();

        access = null;
        // FIXME: find the platform specific PD class by reflection?
        if (os.equals("solaris")) {
            if (cpu.equals("sparc")) {
                access = new SolarisSPARCJavaThreadPDAccess();
            } else if (cpu.equals("x86")) {
                access = new SolarisX86JavaThreadPDAccess();
            } else if (cpu.equals("amd64")) {
                access = new SolarisAMD64JavaThreadPDAccess();
            }
        } else if (os.equals("win32")) {
            if (cpu.equals("x86")) {
                access =  new Win32X86JavaThreadPDAccess();
            } else if (cpu.equals("amd64")) {
                access =  new Win32AMD64JavaThreadPDAccess();
            }
        } else if (os.equals("linux")) {
            if (cpu.equals("x86")) {
                access = new LinuxX86JavaThreadPDAccess();
            } else if (cpu.equals("amd64")) {
                access = new LinuxAMD64JavaThreadPDAccess();
            } else if (cpu.equals("sparc")) {
                access = new LinuxSPARCJavaThreadPDAccess();
            } else {
              try {
                access = (JavaThreadPDAccess)
                  Class.forName("sun.jvm.hotspot.runtime.linux_" +
                     cpu.toLowerCase() + ".Linux" + cpu.toUpperCase() +
                     "JavaThreadPDAccess").newInstance();
              } catch (Exception e) {
                throw new RuntimeException("OS/CPU combination " + os + "/" + cpu +
                                           " not yet supported");
              }
            }
        } else if (os.equals("bsd")) {
            if (cpu.equals("x86")) {
                access = new BsdX86JavaThreadPDAccess();
            } else if (cpu.equals("amd64") || cpu.equals("x86_64")) {
                access = new BsdAMD64JavaThreadPDAccess();
            }
        } else if (os.equals("darwin")) {
            if (cpu.equals("amd64") || cpu.equals("x86_64")) {
                access = new BsdAMD64JavaThreadPDAccess();
            }
        }

        if (access == null) {
            throw new RuntimeException("OS/CPU combination " + os + "/" + cpu +
            " not yet supported");
        }

        virtualConstructor = new VirtualConstructor(db);
        // Add mappings for all known thread types
        virtualConstructor.addMapping("JavaThread", JavaThread.class);
        if (!VM.getVM().isCore()) {
            virtualConstructor.addMapping("CompilerThread", CompilerThread.class);
        }
        // for now, use JavaThread itself. fix it later with appropriate class if needed
        virtualConstructor.addMapping("SurrogateLockerThread", JavaThread.class);
        virtualConstructor.addMapping("JvmtiAgentThread", JvmtiAgentThread.class);
        virtualConstructor.addMapping("ServiceThread", ServiceThread.class);
    }

    public Threads() {
    }

    /** NOTE: this returns objects of type JavaThread, CompilerThread,
      JvmtiAgentThread, and ServiceThread.
      The latter four are subclasses of the former. Most operations
      (fetching the top frame, etc.) are only allowed to be performed on
      a "pure" JavaThread. For this reason, {@link
      sun.jvm.hotspot.runtime.JavaThread#isJavaThread} has been
      changed from the definition in the VM (which returns true for
      all of these thread types) to return true for JavaThreads and
      false for the three subclasses. FIXME: should reconsider the
      inheritance hierarchy; see {@link
      sun.jvm.hotspot.runtime.JavaThread#isJavaThread}. */
    public JavaThread first() {
        Address threadAddr = threadListField.getValue();
        if (threadAddr == null) {
            return null;
        }

        return createJavaThreadWrapper(threadAddr);
    }

    public int getNumberOfThreads() {
        return (int) numOfThreadsField.getValue();
    }

    /** Routine for instantiating appropriately-typed wrapper for a
      JavaThread. Currently needs to be public for OopUtilities to
      access it. */
    public JavaThread createJavaThreadWrapper(Address threadAddr) {
        try {
            JavaThread thread = (JavaThread)virtualConstructor.instantiateWrapperFor(threadAddr);
            thread.setThreadPDAccess(access);
            return thread;
        } catch (Exception e) {
            throw new RuntimeException("Unable to deduce type of thread from address " + threadAddr +
            " (expected type JavaThread, CompilerThread, ServiceThread, JvmtiAgentThread, or SurrogateLockerThread)", e);
        }
    }

    /** Memory operations */
    public void oopsDo(AddressVisitor oopVisitor) {
        // FIXME: add more of VM functionality
        for (JavaThread thread = first(); thread != null; thread = thread.next()) {
            thread.oopsDo(oopVisitor);
        }
    }

    // refer to Threads::owning_thread_from_monitor_owner
    public JavaThread owningThreadFromMonitor(Address o) {
        if (o == null) return null;
        for (JavaThread thread = first(); thread != null; thread = thread.next()) {
            if (o.equals(thread.threadObjectAddress())) {
                return thread;
            }
        }

        for (JavaThread thread = first(); thread != null; thread = thread.next()) {
          if (thread.isLockOwned(o))
            return thread;
        }
        return null;
    }

    public JavaThread owningThreadFromMonitor(ObjectMonitor monitor) {
        return owningThreadFromMonitor(monitor.owner());
    }

    // refer to Threads::get_pending_threads
    // Get list of Java threads that are waiting to enter the specified monitor.
    public List getPendingThreads(ObjectMonitor monitor) {
        List pendingThreads = new ArrayList();
        for (JavaThread thread = first(); thread != null; thread = thread.next()) {
            if (thread.isCompilerThread()) {
                continue;
            }
            ObjectMonitor pending = thread.getCurrentPendingMonitor();
            if (monitor.equals(pending)) {
                pendingThreads.add(thread);
            }
        }
        return pendingThreads;
    }

    // Get list of Java threads that have called Object.wait on the specified monitor.
    public List getWaitingThreads(ObjectMonitor monitor) {
        List pendingThreads = new ArrayList();
        for (JavaThread thread = first(); thread != null; thread = thread.next()) {
            ObjectMonitor waiting = thread.getCurrentWaitingMonitor();
            if (monitor.equals(waiting)) {
                pendingThreads.add(thread);
            }
        }
        return pendingThreads;
    }

    // FIXME: add other accessors
}
