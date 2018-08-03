/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @summary converted from VM Testbase nsk/jdi/VirtualMachine/redefineClasses/redefineclasses023.
 * VM Testbase keywords: [jpda, jdi, redefine, quarantine]
 * VM Testbase comments: 8065773
 * VM Testbase readme:
 * DESCRIPTION:
 *     The test against the method com.sun.jdi.VirtualMachine.redefineClasses()
 *     and checks up the following assertion:
 *        "If canUnrestrictedlyRedefineClasses() is false, changing class
 *        modifiers will throw UnsupportedOperationException exception"
 *     The test consists of the following files:
 *         redefineclasses023.java             - debugger
 *         redefineclasses023a.java            - debuggee
 *         redefineclasses023b.java            - an initial redefined class
 *         newclassXX/redefineclasses023b.java - redefining debuggee's class
 *     This test performs the following cases:
 *      1. newclass01 - changing <public> class-modifier to <protected>
 *      2. newclass02 - changing <public> class-modifier to <private>
 *      3. newclass03 - changing <public> class-modifier to <private package>
 *     The test checks two different cases for suspended debugee and not
 *     suspended one.
 * COMMENTS:
 *
 * @library /vmTestbase
 *          /test/lib
 * @run driver jdk.test.lib.FileInstaller . .
 * @build nsk.jdi.VirtualMachine.redefineClasses.redefineclasses023
 *        nsk.jdi.VirtualMachine.redefineClasses.redefineclasses023a
 *
 * @comment compile newclassXX to bin/newclassXX
 *          with full debug info
 * @run driver nsk.share.ExtraClassesBuilder
 *      -g:lines,source,vars
 *      newclass01 newclass02 newclass03
 *
 * @run main/othervm PropertyResolvingWrapper
 *      nsk.jdi.VirtualMachine.redefineClasses.redefineclasses023
 *      ./bin
 *      -verbose
 *      -arch=${os.family}-${os.simpleArch}
 *      -waittime=5
 *      -debugee.vmkind=java
 *      -transport.address=dynamic
 *      "-debugee.vmkeys=${test.vm.opts} ${test.java.opts}"
 */

