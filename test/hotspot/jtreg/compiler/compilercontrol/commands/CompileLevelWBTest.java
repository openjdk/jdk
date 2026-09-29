/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

/*
 * @test id=exclude-all-levels
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server"
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod
 *                               ${test.main.class}
 */

/*
 * @test id=exclude-mask-1-mixed
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode == "Xmixed" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,1
 *                               ${test.main.class}
 *                               2 3 4
 */

/*
 * @test id=exclude-mask-1-comp
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode == "Xcomp" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,1
 *                               ${test.main.class}
 *                               2 3/4 4
 */

/*
 * @test id=exclude-mask-1-no-tiered
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode == "Xmixed" & vm.flavor == "server" & vm.opt.TieredCompilation == false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,1
 *                               ${test.main.class}
 *                               4
 */

/*
 * @test id=exclude-mask-2-mixed
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode == "Xmixed" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,10
 *                               ${test.main.class}
 *                               1 3 4
 */

/*
 * @test id=exclude-mask-2-comp
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode == "Xcomp" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,10
 *                               ${test.main.class}
 *                               1 3/4 4
 */

/*
 * @test id=exclude-mask-2-no-tiered
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode == "Xcomp" & vm.flavor == "server" & vm.opt.TieredCompilation == false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,10
 *                               ${test.main.class}
 *                               4
 */

/*
 * @test id=exclude-mask-3-mixed
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode == "Xmixed" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,11
 *                               ${test.main.class}
 *                               3 4
 */

/*
 * @test id=exclude-mask-3-comp
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode == "Xcomp" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,11
 *                               ${test.main.class}
 *                               3/4 4
 */

/*
 * @test id=exclude-mask-3-no-tiered
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode == "Xcomp" & vm.flavor == "server" & vm.opt.TieredCompilation == false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,11
 *                               ${test.main.class}
 *                               4
 */

/*
 * @test id=exclude-mask-4-tiered
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,100
 *                               ${test.main.class}
 *                               1 2 4
 */

/*
 * @test id=exclude-mask-4-no-tiered
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation == false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,100
 *                               ${test.main.class}
 *                               4
 */

/*
 * @test id=exclude-mask-5-tiered
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,101
 *                               ${test.main.class}
 *                               2 4
 */

/*
 * @test id=exclude-mask-5-no-tiered
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation == false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,101
 *                               ${test.main.class}
 *                               4
 */

/*
 * @test id=exclude-mask-6-tiered
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,110
 *                               ${test.main.class}
 *                               1 4
 */

/*
 * @test id=exclude-mask-6-no-tiered
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation == false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,110
 *                               ${test.main.class}
 *                               4
 */

/*
 * @test id=exclude-mask-7
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server"
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,111
 *                               ${test.main.class}
 *                               4
 */

/*
 * @test id=exclude-mask-8
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,1000
 *                               ${test.main.class}
 *                               1 2 3
 */

/*
 * @test id=exclude-mask-9
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,1001
 *                               ${test.main.class}
 *                               2 3
 */

/*
 * @test id=exclude-mask-10
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,1010
 *                               ${test.main.class}
 *                               1 3
 */

/*
 * @test id=exclude-mask-11
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,1011
 *                               ${test.main.class}
 *                               3
 */

/*
 * @test id=exclude-mask-12
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,1100
 *                               ${test.main.class}
 *                               1 2
 */

/*
 * @test id=exclude-mask-13
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,1101
 *                               ${test.main.class}
 *                               2
 */

/*
 * @test id=exclude-mask-14
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,1110
 *                               ${test.main.class}
 *                               1
 */

/*
 * @test id=exclude-mask-15
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server"
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,1111
 *                               ${test.main.class}
 */

/*
 * @test id=compileonly-all-levels-mixed
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode == "Xmixed" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod
 *                               ${test.main.class}
 *                               1 2 3 4
 */

/*
 * @test id=compileonly-all-levels-comp
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode == "Xcomp" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod
 *                               ${test.main.class}
 *                               1 2 3/4 4
 */

/*
 * @test id=compileonly-all-levels-no-tiered
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation == false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod
 *                               ${test.main.class}
 *                               4
 */

/*
 * @test id=compileonly-mask-1
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1
 *                               ${test.main.class}
 *                               1
 */

/*
 * @test id=compileonly-mask-2
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,10
 *                               ${test.main.class}
 *                               2
 */

/*
 * @test id=compileonly-mask-3
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,11
 *                               ${test.main.class}
 *                               1 2
 */

/*
 * @test id=compileonly-mask-4
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,100
 *                               ${test.main.class}
 *                               3
 */

/*
 * @test id=compileonly-mask-5
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,101
 *                               ${test.main.class}
 *                               1 3
 */

/*
 * @test id=compileonly-mask-6
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,110
 *                               ${test.main.class}
 *                               2 3
 */

/*
 * @test id=compileonly-mask-7
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,111
 *                               ${test.main.class}
 *                               1 2 3
 */

/*
 * @test id=compileonly-mask-8
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server"
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1000
 *                               ${test.main.class}
 *                               4
 */

/*
 * @test id=compileonly-mask-9-tiered
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1001
 *                               ${test.main.class}
 *                               1 4
 */

/*
 * @test id=compileonly-mask-9-no-tiered
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation == false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1001
 *                               ${test.main.class}
 *                               4
 */

/*
 * @test id=compileonly-mask-10-tiered
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1010
 *                               ${test.main.class}
 *                               2 4
 */

/*
 * @test id=compileonly-mask-10-no-tiered
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation == false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1010
 *                               ${test.main.class}
 *                               4
 */

/*
 * @test id=compileonly-mask-11-tiered
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1011
 *                               ${test.main.class}
 *                               1 2 4
 */

/*
 * @test id=compileonly-mask-11-no-tiered
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation == false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1011
 *                               ${test.main.class}
 *                               4
 */

/*
 * @test id=compileonly-mask-12-mixed
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode == "Xmixed" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1100
 *                               ${test.main.class}
 *                               3 4
 */

/*
 * @test id=compileonly-mask-12-comp
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode == "Xcomp" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1100
 *                               ${test.main.class}
 *                               3/4 4
 */

/*
 * @test id=compileonly-mask-12-no-tiered
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation == false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1100
 *                               ${test.main.class}
 *                               4
 */

/*
 * @test id=compileonly-mask-13-mixed
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode == "Xmixed" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1101
 *                               ${test.main.class}
 *                               1 3 4
 */

/*
 * @test id=compileonly-mask-13-comp
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode == "Xcomp" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1101
 *                               ${test.main.class}
 *                               1 3/4 4
 */

/*
 * @test id=compileonly-mask-13-no-tiered
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation == false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1101
 *                               ${test.main.class}
 *                               4
 */

/*
 * @test id=compileonly-mask-14-mixed
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode == "Xmixed" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1110
 *                               ${test.main.class}
 *                               2 3 4
 */

/*
 * @test id=compileonly-mask-14-comp
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode == "Xcomp" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1110
 *                               ${test.main.class}
 *                               2 3/4 4
 */

/*
 * @test id=compileonly-mask-14-no-tiered
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation == false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1110
 *                               ${test.main.class}
 *                               4
 */

/*
 * @test id=compileonly-mask-15-mixed
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode == "Xmixed" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1111
 *                               ${test.main.class}
 *                               1 2 3 4
 */

/*
 * @test id=compileonly-mask-15-comp
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode == "Xcomp" & vm.flavor == "server" & vm.opt.TieredCompilation != false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1111
 *                               ${test.main.class}
 *                               1 2 3/4 4
 */

/*
 * @test id=compileonly-mask-15-no-tiered
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & vm.opt.TieredCompilation == false
 *         & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *         & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1111
 *                               ${test.main.class}
 *                               4
 */

package compiler.compilercontrol.commands;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

public class CompileLevelWBTest {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final Method testMethod;
    private static boolean[] allowedToCompileAtLevel;
    private static int[] expectedLevelAfterWBCompileRequestAtLevel;

    static {
        try {
            testMethod = CompileLevelWBTest.class.getDeclaredMethod("compiledMethod", new Class[] {int.class, boolean.class});
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    // For Tier4 600 invocation of this method with avg 25 loops for each should be enough
    // to trigger Tier4CompilationThreshold=15000
    private static void compiledMethod(final int a, boolean uncommonTrap) {
        int r = 0;
        if (uncommonTrap) {
            IO.println("==> compiledMethod(): uncommon trap! <==");
        }
        for (int i = 0; i < a % 50; i++) {
            r ^= i;
        }
        if (r == 42) {
            IO.println("xopowo!");
        }
    }

    private static boolean longLoop(int expectedLevel) {
        // To trigger compilation, 100-200 should be enough for C1 and 600-700 for C2
        for (int i = 0; i < 10000; i++) {
            compiledMethod(i, false);
            if ((i & 0xf) == 0) {
                if (isTestMethodCompiledAtLevel(expectedLevel)) {
                    IO.println("==> longLoop(): compiledMethod() has been compiled at iteration " + i + " <==");
                    compiledMethod(i + 1, expectedLevel == 4);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean runTestCode(int expectedLevel) {
        for (int i = 0; i < 10; i++) {
            if (longLoop(expectedLevel)) {
                return true;
            }
        }
        IO.println("==> runTestCode(): " + testMethod.getName() + " has not been compiled <==");
        return false;
    }

    private static boolean isTestMethodCompiledAtLevel(int expectedLevel) {
        int curLevel = WB.getMethodCompilationLevel(testMethod);
        if (curLevel == 0) {
            // we're in interpreter. keep going.
            return false;
        }
        verifyCompileLevel(curLevel);
        Asserts.assertTrue(allowedToCompileAtLevel[curLevel], "The test method should not be compiled at excluded level=" + curLevel);
        return curLevel == expectedLevel;
    }

    public static void main(String[] args) {
        IO.println("==> entering main() <==");
        try {
            parseExpectedLevels(args);
            // Wait until compilers are free, so thresholds are not altered
            waitUntilCompilerQueuesIsAlmostEmpty();

            IO.println("==> starting test <==");
            for (int level = 1; level <= 4; level++) {
                WB.deoptimizeMethod(testMethod);
                WB.clearMethodState(testMethod);

                waitUntil(() -> {
                    int curLevel = WB.getMethodCompilationLevel(testMethod);
                    IO.println("==> waiting for deoptimization, current level: " + curLevel + " <==");
                    return curLevel == 0;
                });

                boolean shouldBeCompilable = allowedToCompileAtLevel[level];
                String expectedResult = shouldBeCompilable ? "" : " NOT";
                IO.println("==> checking compilation at level " + level + " (should" + expectedResult + " be compiled) <==");
                Asserts.assertEquals(shouldBeCompilable, WB.isMethodCompilable(testMethod, level),
                        "WB.isMethodCompilable() returns wrong answer for level " + level);
                Asserts.assertEquals(shouldBeCompilable, WB.enqueueMethodForCompilation(testMethod, level),
                        "Error enqueuing method for compilation at level " + level + " via WhiteBox");
                int expectedLevel = expectedLevelAfterWBCompileRequestAtLevel[level];
                Asserts.assertEquals(shouldBeCompilable, runTestCode(expectedLevel),
                        testMethod.getName() + " was" + expectedResult + " compiled at level " + expectedLevel);
            }
        } finally {
            IO.println("==> exiting main() <==");
        }
    }

    private static void parseExpectedLevels(String[] args) {
        allowedToCompileAtLevel = new boolean[] { false, false, false, false, false };
        expectedLevelAfterWBCompileRequestAtLevel = new int[] { 0, 1, 2, 3, 4, 5 };

        for (String arg : args) {
            try {
                String[] parts = arg.split("/");
                int level = Integer.parseInt(parts[0]);
                int goesToLevel = parts.length > 1 ? Integer.parseInt(parts[1]) : level;
                verifyCompileLevel(level);
                allowedToCompileAtLevel[level] = true;
                expectedLevelAfterWBCompileRequestAtLevel[level] = goesToLevel;
            } catch (NumberFormatException ex) {
                ex.printStackTrace();
            }
        }
    }

    private static void waitUntilCompilerQueuesIsAlmostEmpty() {
        waitUntil(() -> {
            int cqSize = WB.getCompileQueuesSize();
            IO.println("==> Compiler queues size: " + cqSize);
            return cqSize < 5;
        });
    }

    private static void waitUntil(BooleanSupplier condition) {
        for (int maxWait = 30; maxWait > 0; --maxWait) {
            if (condition.getAsBoolean()) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
        }
    }

    private static void verifyCompileLevel(int curLevel) {
        Asserts.assertTrue(curLevel > 0 && curLevel < 5, "Invalid compile level");
    }
}
