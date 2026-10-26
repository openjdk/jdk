/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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


/*
 * @test
 *
 * @bug 8391440
 * @summary StackFrame.PopFrames on a virtual thread whose frozen caller is a compiled frame
 * VM Testbase keywords: [quick, jpda, jdwp]
 * VM Testbase readme:
 * DESCRIPTION
 *     This test performs checking for
 *         command set: StackFrame
 *         command: PopFrames
 *     Same as popframes002, the tested method joins another virtual thread
 *     so the caller frame stays frozen in the stack chunk. Here the
 *     debuggee runs with -Xcomp and the breakpoint is in a separate
 *     method stop() called by the tested method, so the tested method
 *     is still compiled when it unmounts and its caller stays a frozen
 *     compiled frame. The debugger pops the frame of the tested method, the
 *     caller of the breakpoint frame. The debuggee checks that the
 *     tested method was invoked exactly two times.
 *     Test checks that debugee accept the command packet and
 *     replies with correct reply packet. Also test checks that
 *     after the frames are popped the tested method is invoked
 *     second time.
 *     Test consists of two compoments:
 *         debugger: popframes001
 *         debuggee: popframes001a
 *     First, debugger uses nsk.share support classes to launch debuggee
 *     and obtain Transport object, that represents JDWP transport channel.
 *     Next, debugger waits for tested class loaded, requests methodIDs
 *     for tested method and stop, sets breakpoint in stop and wait for
 *     breakpoint reached. After breakpoint event is received with a
 *     thread, debugger gets the second stack frame for this thread and
 *     checks it belongs to the tested method.
 *     Then, debugger creates command packet for StackFrame.PopFrames
 *     command with the found threadID and frameID, writes this packet
 *     to the transport channel, and waits for a reply packet.
 *     When reply packet is received, debugger parses the packet structure
 *     and checks if no reply data returned in the packet.
 *     Then, checks if the tested method will be invoked second time.
 *     To do so, it resumes debuggee and waits for BREAKPOINT event
 *     will be received again. If expected BREAKPOINT event is not received,
 *     debugger complains an error.
 *     Finally, debugger disconnects debuggee, waits for it exits
 *     and exits too with the proper exit code.
 *
 * @requires vm.continuations
 * @library /vmTestbase /test/hotspot/jtreg/vmTestbase
 *          /test/lib
 * @build nsk.jdwp.StackFrame.PopFrames.popframes001a
 * @run driver
 *      nsk.jdwp.StackFrame.PopFrames.popframes001
 *      -arch=${os.family}-${os.simpleArch}
 *      -verbose
 *      -waittime=5
 *      -debugee.vmkind=java
 *      -transport.address=dynamic
 *      -popCaller
 *      -debugee.vmkeys="${test.vm.opts} ${test.java.opts}
 *                       -Xcomp -XX:+PreserveFramePointer
 *                       -XX:CompileCommand=dontinline,*::testedMethod
 *                       -XX:CompileCommand=dontinline,*::stop
 *                       -Dtest.thread.factory=Virtual
 *                       -Dpopframes001.compiledCaller=true
 *                       -Dpopframes001.unmount=true"
 */
