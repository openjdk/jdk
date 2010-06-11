/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.jdi;

import sun.jvm.hotspot.oops.Oop;
import sun.jvm.hotspot.oops.Instance;
import sun.jvm.hotspot.oops.OopUtilities;
import sun.jvm.hotspot.runtime.JavaThread;
import com.sun.jdi.*;
import java.util.*;

public class ThreadGroupReferenceImpl extends ObjectReferenceImpl
    implements ThreadGroupReference
{
    ThreadGroupReferenceImpl(VirtualMachine aVm, sun.jvm.hotspot.oops.Oop oRef) {
        super(aVm, oRef);
    }

    protected String description() {
        return "ThreadGroupReference " + uniqueID();
    }

    public String name() {
        return OopUtilities.threadGroupOopGetName(ref());
    }

    public ThreadGroupReference parent() {
        return (ThreadGroupReferenceImpl)vm.threadGroupMirror(
               (Instance)OopUtilities.threadGroupOopGetParent(ref()));
    }

    public void suspend() {
        vm.throwNotReadOnlyException("ThreadGroupReference.suspend()");
    }

    public void resume() {
        vm.throwNotReadOnlyException("ThreadGroupReference.resume()");
    }

    public List threads() {
        // Each element of this array is the Oop for a thread;
        // NOTE it is not the JavaThread that we need to create
        // a ThreadReferenceImpl.
        Oop[] myThreads = OopUtilities.threadGroupOopGetThreads(ref());

        ArrayList myList = new ArrayList(myThreads.length);
        for (int ii = 0; ii < myThreads.length; ii++) {
            JavaThread jt = OopUtilities.threadOopGetJavaThread(myThreads[ii]);
            if (jt != null) {
                ThreadReferenceImpl xx = (ThreadReferenceImpl)vm.threadMirror(jt);
                myList.add(xx);
            }
        }
        return myList;
    }

    public List threadGroups() {
        Oop[] myGroups = OopUtilities.threadGroupOopGetGroups(ref());
        ArrayList myList = new ArrayList(myGroups.length);
        for (int ii = 0; ii < myGroups.length; ii++) {
            ThreadGroupReferenceImpl xx = (ThreadGroupReferenceImpl)vm.threadGroupMirror(
                                          (Instance)myGroups[ii]);
            myList.add(xx);

        }
        return myList;
    }

    public String toString() {
        return "instance of " + referenceType().name() +
               "(name='" + name() + "', " + "id=" + uniqueID() + ")";
    }
}
