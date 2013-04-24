/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @bug 8010659
 * @summary Javac Crashes while building OpenJFX
 * @library /tools/javac/lib
 * @build ToolBox
 * @run main CompilerCrashWhenMixingBinariesAndSourcesTest
 */

public class CompilerCrashWhenMixingBinariesAndSourcesTest {
    private static final String ASource =
            "class A {\n" +
            "        void test() {new B(){};}\n" +
            "}";
    private static final String BSource =
            "class B extends C {}";
    private static final String CSource =
            "class C extends D {\n" +
            "        String m(int i) {return null;}\n" +
            "}";
    private static final String DSource =
            "class D {\n" +
            "        Object m(int i) {return null;}\n" +
            "}";

    public static void main (String[] args) throws Exception{
        ToolBox.JavaToolArgs javacParams = new ToolBox.JavaToolArgs()
                .setSources(ASource, BSource, CSource, DSource);
        ToolBox.javac(javacParams);

        ToolBox.rm("A.class");
        ToolBox.rm("A$1.class");
        ToolBox.rm("C.class");
        ToolBox.rm("D.class");

        javacParams = new ToolBox.JavaToolArgs()
                .setOptions("-cp", ".")
                .setSources(ASource, CSource, DSource);
        ToolBox.javac(javacParams);
    }
}
