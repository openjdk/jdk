/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8266625
 * @summary The method DiagnosticSource#findLine returns wrong results when using the boundary values
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox
 * @run main DiagnosticSourceTest
 */

import javax.tools.ToolProvider;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.sun.tools.javac.util.DiagnosticSource;
import static com.sun.tools.javac.util.LayoutCharacters.*;
import toolbox.ToolBox;

public class DiagnosticSourceTest {
    public static void main(String[] args) {
        ToolBox tb = new ToolBox();

        String code = "public class T {\n}"; // 18 characters
        var fileObject = new ToolBox.JavaSource(code);
        DiagnosticSource ds = new DiagnosticSource(fileObject, null);
        tb.checkEqual(Arrays.asList("1", "1", "public class T {"), getActualOutputList(ds, 0));
        tb.checkEqual(Arrays.asList("1", "4", "public class T {"), getActualOutputList(ds, 3));
        tb.checkEqual(Arrays.asList("2", "1", "}"), getActualOutputList(ds, 17));
        tb.checkEqual(Arrays.asList("0", "0", null), getActualOutputList(ds,18));
        tb.checkEqual(Arrays.asList("0", "0", null), getActualOutputList(ds, 19));
        tb.checkEqual(Arrays.asList("0", "0", null), getActualOutputList(ds, 20));

        code = "public class T {\n}\n"; // 19 characters
        fileObject = new ToolBox.JavaSource(code);
        ds = new DiagnosticSource(fileObject, null);
        tb.checkEqual(Arrays.asList("1", "1", "public class T {"), getActualOutputList(ds, 0));
        tb.checkEqual(Arrays.asList("1", "4", "public class T {"), getActualOutputList(ds, 3));
        tb.checkEqual(Arrays.asList("2", "1", "}"), getActualOutputList(ds, 17));
        tb.checkEqual(Arrays.asList("2", "2", "}"), getActualOutputList(ds, 18));
        tb.checkEqual(Arrays.asList("0", "0", null), getActualOutputList(ds, 19));
        tb.checkEqual(Arrays.asList("0", "0", null), getActualOutputList(ds, 20));
    }

    public static List<String> getActualOutputList(DiagnosticSource ds, int pos) {
        return Arrays.asList(
                Integer.toString(ds.getLineNumber(pos)),
                Integer.toString(ds.getColumnNumber(pos, false)),
                ds.getLine(pos));
    }
}
