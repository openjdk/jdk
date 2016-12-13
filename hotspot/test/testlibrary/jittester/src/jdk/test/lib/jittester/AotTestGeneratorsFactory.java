/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class AotTestGeneratorsFactory implements Function<String[], List<TestsGenerator>> {
    private static final String AOT_OPTIONS = "-XX:+UseAOT -XX:AOTLibrary=./aottest.so";
    private static final String AOT_COMPILER_BUILD_ACTION
            = "@build compiler.aot.AotCompiler";
    private static final String AOT_COMPILER_RUN_ACTION_PREFIX
            = "@run driver compiler.aot.AotCompiler -libname aottest.so -class ";

    @Override
    public List<TestsGenerator> apply(String[] input) {
        List<TestsGenerator> result = new ArrayList<>();
        for (String generatorName : input) {
            switch (generatorName) {
                case "ByteCode":
                    result.add(new ByteCodeGenerator("aot_bytecode_tests",
                            AotTestGeneratorsFactory::generateBytecodeHeader, AOT_OPTIONS));
                    break;
                case "JavaCode":
                    result.add(new JavaCodeGenerator("aot_java_tests",
                            AotTestGeneratorsFactory::generateJavaHeader, AOT_OPTIONS));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown generator: " + generatorName);
            }
        }
        return result;
    }

    private static String[] generateBytecodeHeader(String mainClassName) {
        return new String[]{
            AOT_COMPILER_BUILD_ACTION,
            AOT_COMPILER_RUN_ACTION_PREFIX + mainClassName
        };
    }

    private static String[] generateJavaHeader(String mainClassName) {
        return new String[]{
            "@compile " + mainClassName + ".java",
            AOT_COMPILER_BUILD_ACTION,
            AOT_COMPILER_RUN_ACTION_PREFIX + mainClassName
        };
    }
}
