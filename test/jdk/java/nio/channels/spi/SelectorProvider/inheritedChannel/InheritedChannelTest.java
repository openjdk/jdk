/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4673940 4930794 8211842 6914801
 * @summary Unit tests for inetd feature
 * @requires (os.family == "linux" | os.family == "mac")
 * @library /test/lib
 * @build jdk.test.lib.Utils
 *        jdk.test.lib.Asserts
 *        jdk.test.lib.JDKToolFinder
 *        jdk.test.lib.JDKToolLauncher
 *        jdk.test.lib.Platform
 *        jdk.test.lib.process.*
 *        UnixSocketTest StateTest StateTestService EchoTest EchoService
 *        UnixDomainChannelTest CloseTest Launcher Util
 *        CheckIPv6Test CheckIPv6Service
 * @run testng/othervm/native InheritedChannelTest
 * @key intermittent
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Platform;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;

public class InheritedChannelTest {

    private static final String TEST_CLASSPATH = System.getProperty("test.class.path");
    private static final String TEST_CLASSES = System.getProperty("test.classes");

    private static final Path libraryPath
            = Paths.get(System.getProperty("java.library.path"));

    @DataProvider
    public Object[][] testCases() {
        return new Object[][] {
            { "UnixDomainChannelTest", List.of(UnixDomainChannelTest.class.getName())},
            { "UnixSocketTest", List.of(UnixSocketTest.class.getName())},
            { "StateTest", List.of(StateTest.class.getName(), "-Dtest.classes="+TEST_CLASSES)},
            { "EchoTest",  List.of(EchoTest.class.getName())  },
            { "CheckIPv6Test",  List.of(CheckIPv6Test.class.getName())  },
            { "CloseTest", List.of(CloseTest.class.getName()) },
        };
    }

    @Test(dataProvider = "testCases", timeOut=30000)
    public void test(String desc, List<String> opts) throws Throwable {
        String pathVar = Platform.sharedLibraryPathVariableName();
        System.out.println(pathVar + "=" + libraryPath);

        List<String> args = new ArrayList<>();
        args.add(JDKToolFinder.getJDKTool("java"));
        args.addAll(asList(Utils.getTestJavaOpts()));
        args.addAll(List.of("--add-opens", "java.base/java.io=ALL-UNNAMED",
                            "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED"));
        args.addAll(opts);

        ProcessBuilder pb = new ProcessBuilder(args);

        Map<String, String> env = pb.environment();
        env.put("CLASSPATH", TEST_CLASSPATH);
        env.put(pathVar, libraryPath.toString());

        ProcessTools.executeCommand(pb).shouldHaveExitValue(0);
    }
}
