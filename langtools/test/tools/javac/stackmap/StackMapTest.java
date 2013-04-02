/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4955930
 * @summary The "method0" StackMap attribute should have two entries instead of three
 * @library /tools/javac/lib
 * @build ToolBox
 * @run compile -source 6 -target 6 StackMapTest.java
 * @run main StackMapTest
 */

import java.nio.file.Path;
import java.nio.file.Paths;

//original test: test/tools/javac/stackmap/T4955930.sh
public class StackMapTest {

    class Test {
        void method0(boolean aboolean) throws Exception {
            label_0:
            while (true) {
                if (aboolean) ;
                else break label_0;
            }
        }
    }

    public static void main(String args[]) throws Exception {
//    "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -source 6 -target 6 T4955930.java

//    "${TESTJAVA}${FS}bin${FS}javap" ${TESTTOOLVMOPTS} -verbose T4955930 > ${TMP1}
        Path pathToClass = Paths.get(System.getProperty("test.classes"),
                "StackMapTest$Test.class");
        ToolBox.JavaToolArgs javapArgs =
                new ToolBox.JavaToolArgs().setAllArgs("-v", pathToClass.toString());

//        grep "StackMapTable: number_of_entries = 2" ${TMP1}
        if (!ToolBox.javap(javapArgs).contains("StackMapTable: number_of_entries = 2"))
            throw new AssertionError("The number of entries of the stack map "
                    + "table should be equal to 2");
    }

}
