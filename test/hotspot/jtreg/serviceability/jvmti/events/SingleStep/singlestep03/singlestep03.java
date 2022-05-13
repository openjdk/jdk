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
 * @summary converted from VM Testbase nsk/jvmti/SingleStep/singlestep003.
 * VM Testbase keywords: [quick, jpda, jvmti, onload_only_caps, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     This test exercises the JVMTI event SingleStep.
 *     It verifies that no single step event will be generated from
 *     within native methods.
 *     The test works as follows. Breakpoint is set at special method
 *     'bpMethod()'. Upon reaching the breakpoint, agent enables
 *     SingleStep event generation and checks the events. The java part
 *     calls native method 'nativeMethod()' which calls another native
 *     'anotherNativeMethod()' in order to provoke the SingleStep events
 *     from within native methods. When 'bpMethod()' is leaved and
 *     accordingly, the program returns to the calling method 'runThis()',
 *     the agent disables the event generation.
 * COMMENTS
 *
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} singlestep03.java
 * @run main/othervm/native --enable-preview -agentlib:singlestep03 singlestep03 platform
 * @run main/othervm/native --enable-preview -agentlib:singlestep03 singlestep03 virtual
 */

public class singlestep03 {
    static {
        try {
            System.loadLibrary("singlestep03");
        } catch (UnsatisfiedLinkError ule) {
            System.err.println("Could not load \"singlestep03\" library");
            System.err.println("java.library.path:"
                + System.getProperty("java.library.path"));
            throw ule;
        }
    }

    static volatile int result;
    native void nativeMethod();
    native void anotherNativeMethod(int i);

    native int check();

    public static void main(String[] args) throws Exception {
        Thread.Builder builder;
        if ("virtual".equals(args[0])) {
            builder = Thread.ofVirtual();
        } else {
            builder = Thread.ofPlatform();
        }
        Thread thread = builder.start(() -> {
            result = new singlestep03().runThis();
        });
        thread.join();
        if (result != 0) {
            throw new RuntimeException("Unexpected status: " + result);
        }
    }

    private int runThis() {

        System.out.println("\nReaching a breakpoint method ...\n");
        bpMethod();
        System.out.println("The breakpoint method leaved ...");

        return check();
    }

    /**
     * dummy method used to reach breakpoint, enable the SingleStep
     * event in the agent and provoke SingleStep for native method
     */
    private void bpMethod() {
        nativeMethod();
    }
}
