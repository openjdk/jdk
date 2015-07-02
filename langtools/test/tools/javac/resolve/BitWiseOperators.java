/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/**@test
 * @bug 8082311
 * @summary Verify that bitwise operators don't allow to mix numeric and boolean operands.
 * @library ../lib
 */

import com.sun.tools.javac.util.StringUtils;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

public class BitWiseOperators extends JavacTestingAbstractThreadedTest {
    public static void main(String... args) {
        new BitWiseOperators().run();
    }

    void run() {
        for (TYPE type1 : TYPE.values()) {
            for (OPERATION op : OPERATION.values()) {
                for (TYPE type2 : TYPE.values()) {
                    runTest(type1, op, type2);
                }
            }
        }
    }

    void runTest(TYPE type1, OPERATION op, TYPE type2) {
        DiagnosticCollector<JavaFileObject> dc = new DiagnosticCollector<>();
        List<JavaSource> files = Arrays.asList(new JavaSource(type1, op, type2));
        comp.getTask(null, null, dc, null, null, files).call();
        if (dc.getDiagnostics().isEmpty() ^ TYPE.compatible(type1, type2)) {
            throw new AssertionError("Unexpected behavior. Type1: " + type1 +
                                                        "; type2: " + type2 +
                                                        "; diagnostics: " + dc.getDiagnostics());
        }
    }

    enum TYPE {
        BYTE,
        CHAR,
        SHORT,
        INT,
        LONG,
        BOOLEAN;

        public static boolean compatible(TYPE op1, TYPE op2) {
            return !(op1 == BOOLEAN ^ op2 == BOOLEAN);
        }
    }

    enum OPERATION {
        BITAND("&"),
        BITOR("|"),
        BITXOR("^");

        String op;

        private OPERATION(String op) {
            this.op = op;
        }

    }

    class JavaSource extends SimpleJavaFileObject {

        String template = "class Test {\n" +
                          "    public Object test(#TYPE1 var1, #TYPE2 var2) {\n" +
                          "        return var1 #OP var2;\n" +
                          "    }\n" +
                          "}";

        String source;

        public JavaSource(TYPE type1, OPERATION op, TYPE type2) {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            source = template.replaceAll("#TYPE1", StringUtils.toLowerCase(type1.name()))
                             .replaceAll("#OP", StringUtils.toLowerCase(op.op))
                             .replaceAll("#TYPE2", StringUtils.toLowerCase(type2.name()));
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

}
