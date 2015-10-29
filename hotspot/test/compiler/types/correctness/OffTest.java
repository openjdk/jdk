/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @test CorrectnessTest
 * @bug 8038418
 * @library /testlibrary /test/lib
 * @modules java.base/sun.misc
 *          java.management
 * @ignore 8066173
 * @compile execution/TypeConflict.java execution/TypeProfile.java
 *          execution/MethodHandleDelegate.java
 * @build CorrectnessTest
 * @build OffTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/timeout=1200 OffTest
 */

import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.ProcessTools;
import jdk.test.lib.Utils;
import java.util.Random;
import scenarios.ProfilingType;

public class OffTest {
    private static final String[] OPTIONS = {
            "-Xbootclasspath/a:.",
            "-XX:+IgnoreUnrecognizedVMOptions",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+WhiteBoxAPI",
            "-XX:CompileCommand=exclude,execution/*::methodNotToCompile",
            "-XX:CompileCommand=dontinline,scenarios/Scenario::collectReturnType",
            "", // -XX:TypeProfileLevel=?
            "", // -XX:?UseTypeSpeculation
            CorrectnessTest.class.getName(),
            "", // ProfilingType.name()
    };

    private static final String TYPE_PROFILE_LEVEL = "TypeProfileLevel";
    private static final String USE_TYPE_SPECULATION = "UseTypeSpeculation";
    private static final int TYPE_PROFILE_LEVEL_LENGTH = 3;
    private static final int TYPE_PROFILE_LEVEL_BOUND = 3;
    private static final int DEFAULT_COUNT = 10;
    private static final int PROFILING_TYPE_INDEX = OPTIONS.length - 1;
    private static final int TYPE_PROFILE_INDEX = OPTIONS.length - 4;
    private static final int USE_TYPE_SPECULATION_INDEX = OPTIONS.length - 3;
    private static final Random RNG = Utils.getRandomInstance();

    public static void main(String[] args) throws Exception {
        int count = DEFAULT_COUNT;
        if (args.length > 0) {
            count = Integer.parseInt(args[0]) ;
        }
        for (int i = 0; i < count; ++i) {
            runTest();
        }
    }

    private static void runTest() throws Exception {
        String useTypeSpeculation = "-XX:" + (RNG.nextBoolean() ? "+" : "-") +  USE_TYPE_SPECULATION;
        String typeProfileLevel = "-XX:" + TYPE_PROFILE_LEVEL + "=" + randomTypeProfileLevel();
        ProfilingType type = randomProfileType();
        OPTIONS[TYPE_PROFILE_INDEX] = typeProfileLevel;
        OPTIONS[USE_TYPE_SPECULATION_INDEX] = useTypeSpeculation;
        OPTIONS[PROFILING_TYPE_INDEX] = type.name();
        ProcessBuilder processBuilder = ProcessTools.createJavaProcessBuilder(/* addTestVmOptions= */ true, OPTIONS);
        OutputAnalyzer outputAnalyzer = new OutputAnalyzer(processBuilder.start());
        outputAnalyzer.shouldHaveExitValue(0);
    }

    private static ProfilingType randomProfileType() {
        ProfilingType[] value = ProfilingType.values();
        return value[RNG.nextInt(value.length)];
    }

    private static String randomTypeProfileLevel() {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < TYPE_PROFILE_LEVEL_LENGTH; ++i) {
            stringBuilder.append(RNG.nextInt(TYPE_PROFILE_LEVEL_BOUND));
        }
        return stringBuilder.toString();
    }
}
