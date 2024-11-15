/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.memory.SystemDictionary;
import sun.jvm.hotspot.oops.DefaultHeapVisitor;
import sun.jvm.hotspot.oops.Klass;
import sun.jvm.hotspot.oops.ObjectHeap;
import sun.jvm.hotspot.oops.Oop;
import sun.jvm.hotspot.oops.OopUtilities;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VMLocksPrinter {
    private PrintStream tty;
    private Threads threads = VM.getVM().getThreads();

    public VMLocksPrinter(PrintStream tty) {
        this.tty = tty;
    }

    private String ownerThreadName(Address addr) {
        for (int i = 0; i < threads.getNumberOfThreads(); i++) {
            JavaThread thread = threads.getJavaThreadAt(i);
            if (thread.getAddress().equals(addr)) {
                return thread.getThreadName();
            }
        }
        return "Unknnown thread (Might be non-Java Thread)";
    }
    public void printVMLocks() {
         int maxNum = Mutex.maxNum();
         for (int i = 0; i < maxNum; i++) {
         Mutex mutex = new Mutex(Mutex.at(i));
            if (mutex.owner() != null) {
                tty.println("Internal VM Mutex " + mutex.name() + " is owned by " + ownerThreadName(mutex.owner())
                        + " with address: " + mutex.owner());
               }
         }
    }
}
