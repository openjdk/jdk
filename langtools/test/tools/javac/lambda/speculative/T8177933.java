/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8177933
 * @summary Stackoverflow during compilation, starting jdk-9+163
 *
 * @library /tools/javac/lib
 * @requires !(os.family == "solaris")
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.code
 *          jdk.compiler/com.sun.tools.javac.comp
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @build combo.ComboTestHelper

 * @run main/othervm -Xss512K T8177933
 */

import combo.ComboInstance;
import combo.ComboParameter;
import combo.ComboTask.Result;
import combo.ComboTestHelper;

import javax.lang.model.element.Element;

public class T8177933 extends ComboInstance<T8177933> {

    static final int MAX_DEPTH = 350;

    static class CallExpr implements ComboParameter {
        @Override
        public String expand(String optParameter) {
            Integer n = Integer.parseInt(optParameter);
            if (n == MAX_DEPTH) {
                return "m()";
            } else {
                return "m().#{CALL." + (n + 1) + "}";
            }
        }
    }

    static final String sourceTemplate =
            "class Test {\n" +
            "   Test m() { return null; }\n" +
            "   void test() {\n" +
            "       #{CALL.0};\n" +
            "} }\n";

    public static void main(String[] args) {
        new ComboTestHelper<T8177933>()
                .withDimension("CALL", new CallExpr())
                .run(T8177933::new);
    }

    @Override
    protected void doWork() throws Throwable {
        Result<Iterable<? extends Element>> result = newCompilationTask()
                    .withOption("-XDdev")
                    .withSourceFromTemplate(sourceTemplate)
                    .analyze();
        if (!result.get().iterator().hasNext()) {
            fail("Exception occurred when compiling combo. " + result.compilationInfo());
        }
    }
}
