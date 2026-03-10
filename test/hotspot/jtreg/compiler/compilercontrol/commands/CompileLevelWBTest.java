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
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=exclude-mask-1
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,1
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=exclude-mask-2
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,2
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=exclude-mask-3
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,3
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=exclude-mask-4
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,4
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=exclude-mask-5
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,5
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=exclude-mask-6
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,6
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=exclude-mask-7
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,7
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=exclude-mask-8
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,8
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=exclude-mask-9
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,9
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=exclude-mask-10
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,10
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=exclude-mask-11
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,11
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=exclude-mask-12
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,12
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=exclude-mask-13
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,13
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=exclude-mask-14
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,14
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=exclude-mask-15
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=exclude,*.CompileLevelWBTest::compiledMethod,15
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=compileonly-all-levels
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=compileonly-mask-1
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,1
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=compileonly-mask-2
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,2
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=compileonly-mask-3
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,3
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=compileonly-mask-4
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,4
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=compileonly-mask-5
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,5
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=compileonly-mask-6
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,6
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=compileonly-mask-7
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,7
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=compileonly-mask-8
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,8
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=compileonly-mask-9
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,9
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=compileonly-mask-10
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,10
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=compileonly-mask-11
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,11
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=compileonly-mask-12
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,12
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=compileonly-mask-13
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,13
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=compileonly-mask-14
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,14
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

/*
 * @test id=compileonly-mask-15
 * @bug 8313713
 * @summary Test -XX:CompileCommand=compileonly with different compilation levels
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=30 -XX:+WhiteBoxAPI -Xbootclasspath/a:. -XX:+PrintCompilation
 *                               -XX:CompileCommand=BackgroundCompilation,*.CompileLevelWBTest::compiledMethod,false
 *                               -XX:CompileCommand=compileonly,*.CompileLevelWBTest::compiledMethod,15
 *                               compiler.compilercontrol.commands.CompileLevelWBTest
 */

package compiler.compilercontrol.commands;

import jdk.test.lib.Asserts;
import jdk.test.lib.management.InputArguments;
import jdk.test.whitebox.WhiteBox;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompileLevelWBTest {
    public static final int DEFAULT_LEVEL_BITMASK = 15;

    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final Method testMethod;
    private static boolean[] allowedToCompileAtLevel;

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
            System.out.println("==> compiledMethod(): uncommon trap! <==");
        }
        for (int i = 0; i < a % 50; i++) {
            r ^= i;
        }
        if (r == 42) {
            System.out.println("MAGIC!");
        }
    }

    private static boolean longLoop(int expectedLevel) {
        // To trigger compilation, 100-200 should be enough for C1 and 600-700 for C2
        for (int i = 0; i < 1500; i++) {
            compiledMethod(i, false);
            if ((i & 0xf) == 0) {
                if (isTestMethodCompiledAtLevel(expectedLevel)) {
                    System.out.println("==> longLoop(): compiledMethod() has been compiled at iteration " + i + " <==");
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
        System.out.println("==> runTestCode(): " + testMethod.getName() + " has not been compiled <==");
        return false;
    }

    private static boolean isTestMethodCompiledAtLevel(int expectedLevel) {
        int curLevel = WB.getMethodCompilationLevel(testMethod);
        if (curLevel == 0) {
            // we're in interpreter. keep going.
            return false;
        }
        Asserts.assertTrue(curLevel > 0 && curLevel < 5, "Invalid compile level");
        Asserts.assertTrue(allowedToCompileAtLevel[curLevel], "The test method should not be compiled at excluded level=" + curLevel);
        return curLevel == expectedLevel;
    }

    public static void main(String[] args) {
        System.out.println("==> entering main() <==");
        try {
            parseCompileCommandsFromVmInputArgs(testMethod);
            // Wait until compilers are free, so thresholds are not altered
            waitUntilCompilerQueuesIsAlmostEmpty();

            System.out.println("==> starting test <==");
            for (int level = 1; level <= 4; level++) {
                WB.deoptimizeMethod(testMethod);
                WB.clearMethodState(testMethod);

                waitUntil(() -> {
                    int curLevel = WB.getMethodCompilationLevel(testMethod);
                    System.out.println("==> waiting for deoptimization, current level: " + curLevel + " <==");
                    return curLevel == 0;
                });

                boolean shouldBeCompilable = allowedToCompileAtLevel[level];
                String expectedResult = shouldBeCompilable ? "" : " NOT";
                System.out.println("==> checking compilation at level " + level + " (should" + expectedResult + " be compiled) <==");
                Asserts.assertTrue(WB.isMethodCompilable(testMethod, level) == shouldBeCompilable,
                        "WB.isMethodCompilable() returns wrong answer for level " + level);
                Asserts.assertTrue(WB.enqueueMethodForCompilation(testMethod, level) == shouldBeCompilable,
                        "Error enqueuing method for compilation at level " + level + " via WhiteBox");
                Asserts.assertTrue(runTestCode(level) == shouldBeCompilable,
                        testMethod.getName() + " was" + expectedResult + " compiled at level " + level);
            }
        } finally {
            System.out.println("==> exiting main() <==");
        }
    }

    // We expect a very specific format
    private static void parseCompileCommandsFromVmInputArgs(Method method) {
        Pattern compileCommandRe = Pattern.compile("-XX:CompileCommand=(compileonly|exclude),.*"
                + method.getDeclaringClass().getSimpleName() + "::" + method.getName() + "(,([0-9]+))?");
        for (String arg : InputArguments.getVmInputArgs()) {
            Matcher m = compileCommandRe.matcher(arg);
            if (m.matches()) {
                String compileCommand = m.group(1);
                allowedToCompileAtLevel = parseCompileLevelFlags(compileCommand.equals("compileonly"), m.group(3));
                break;
            }
        }
        if (allowedToCompileAtLevel == null) {
            System.out.println("==> No -XX:CompileCommand found, assuming all levels compilable <==");
            allowedToCompileAtLevel = new boolean[] {false, true, true, true, true};
        }

        System.out.println("==> Method " + method.getName() + " allowed compile at levels: " + Arrays.toString(allowedToCompileAtLevel));
    }

    private static boolean[] parseCompileLevelFlags(boolean isIncluded, String flagStr) {
        int flag = flagStr != null ? Integer.parseInt(flagStr) : DEFAULT_LEVEL_BITMASK;
        boolean[] expectLevelCompilation = new boolean[5];
        for (int bit = 0; bit < 4; bit++) {
            boolean flagIsSet = (flag & (1 << bit)) != 0;
            expectLevelCompilation[bit + 1] = flagIsSet == isIncluded;
        }
        return expectLevelCompilation;
    }

    private static void waitUntilCompilerQueuesIsAlmostEmpty() {
        waitUntil(() -> {
            int cqSize = WB.getCompileQueuesSize();
            System.out.println("==> Compiler queues size: " + cqSize);
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
}
