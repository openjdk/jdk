/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8198317
 * @summary Enhance JavacTool.getTask for flexibility
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox
 * @run main T8198317
 */

import java.io.*;
import java.lang.reflect.*;
import javax.tools.ToolProvider;

import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.util.Log;

import toolbox.ToolBox;
import toolbox.TestRunner;

public class T8198317 extends TestRunner{
    ToolBox tb;

    public T8198317() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String[] args) throws Exception {
        T8198317 t = new T8198317();
        t.runTests();
    }

    @Test
    public void testLogSettingInJavacTool() throws Exception {
        // TODO Situation: out is null and the value is not set in the context.
        // TODO Situation: out is not null and out is not a PrintWriter.

        // Situation: out is not null and out is a PrintWriter.
        PrintWriter expectedPW = new PrintWriter(System.out);
        JavacTaskImpl task2 = (JavacTaskImpl) ToolProvider
                .getSystemJavaCompiler()
                .getTask(expectedPW, null, null, null, null, null);
        PrintWriter writer2 = task2.getContext().get(Log.errKey);
        if (!expectedPW.equals(writer2)) {
            throw new Error("The PrintWriter is set uncorrectly.");
        }
    }
}
