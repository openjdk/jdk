/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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


import java.io.*;

/*
 * @test
 *
 * @summary converted from VM Testbase nsk/jvmti/Breakpoint/breakpoint001.
 * VM Testbase keywords: [quick, jpda, jvmti, onload_only_caps, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     This test exercises the JVMTI event Breakpoint.
 *     It verifies that thread info, method info and location of received
 *     Breakpoint events will be the same with two breakpoints previously
 *     set on the methods 'bpMethod()' and 'bpMethod2()' via the function
 *     SetBreakpoint().
 * COMMENTS
 *
 * @requires vm.continuations
 * @library /test/lib
 *
 * @comment make sure breakpoint01 is compiled with full debug info
 * @clean breakpoint01
 * @compile --enable-preview -source ${jdk.version} -g:lines,source,vars breakpoint01.java
 * @run main/othervm/native --enable-preview -agentlib:breakpoint01 breakpoint01
 */


/**
 * This test exercises the JVMTI event <code>Breakpoint</code>.
 * <br>It verifies that thread info, method info and location of
 * received Breakpoint events will be the same with two breakpoints
 * previously set on the methods <code>bpMethod()</code> and
 * <code>bpMethod2()</code> via the function SetBreakpoint().
 */
public class breakpoint01 {
    static {
        try {
            System.loadLibrary("breakpoint01");
        } catch (UnsatisfiedLinkError ule) {
            System.err.println("Could not load \"breakpoint01\" library");
            System.err.println("java.library.path:"
                + System.getProperty("java.library.path"));
            throw ule;
        }
    }

    native int check();

    public static void main(String[] argv) {
        int result = new breakpoint01().runThis();
        if (result != 0 ) {
            throw new RuntimeException("Check returned " + result);
        }
    }

    private int runThis() {
        Runnable virtualThreadTest = () -> {
            Thread.currentThread().setName("breakpoint01Thr");
            System.out.println("Reaching a breakpoint method ...");
            bpMethodV();
            System.out.println("The breakpoint method leaved ...");
        };

        Thread thread = Thread.startVirtualThread(virtualThreadTest);
        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Thread.currentThread().setName("breakpoint01Thr");
        bpMethod();
        return check();
    }

    /**
     * dummy method used only to reach breakpoint set in the agent
     */
    private void bpMethod() {
        int dummyVar = bpMethod2();
    }

    /**
     * dummy method used only to reach breakpoint set in the agent
     */
    private int bpMethod2() {
        return 0;
    }

    /**
     * dummy method used only to reach breakpoint set in the agent
     */
    private void bpMethodV() {
        int dummyVar = bpMethod2V();
    }

    /**
     * dummy method used only to reach breakpoint set in the agent
     */
    private int bpMethod2V() {
        return 0;
    }
}
