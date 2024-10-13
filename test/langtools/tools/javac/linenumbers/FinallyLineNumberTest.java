/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8134759
 * @summary Add LineNumberTable attribute for return bytecodes split around finally code
 * @enablePreview
 */

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;

import java.io.IOException;
import java.util.List;

public class FinallyLineNumberTest {
    public static void main(String[] args) throws Exception {
        // check that we have 5 consecutive entries for method()
        List<LineNumberInfo> lines = findEntries();
        if (lines == null) {
            throw new Exception("finally line number table could not be loaded");
        }
        if (lines.size() != 5) {
            // Help debug
            System.err.println("LineTable error, got lines:");
            for (LineNumberInfo e : lines) {
                System.err.println(e.lineNumber());
            }
            throw new Exception("finally line number table incorrect: length=" + lines.size() + " expected length=5");
        }

        // return null line, for the load null operation
        int current = lines.get(0).lineNumber();
        int first = current;

        // finally line
        current = lines.get(1).lineNumber();
        if (current != first + 2) {
            throw new Exception("finally line number table incorrect: got=" + current + " expected=" + (first + 2));
        }

        // return null line, for the return operation
        current = lines.get(2).lineNumber();
        if (current != first) {
            throw new Exception("finally line number table incorrect: got=" + current + " expected=" + first);
        }

        // for when exception is thrown
        current = lines.get(3).lineNumber();
        if (current != first + 2) {
            throw new Exception("finally line number table incorrect: got=" + current + " expected=" + (first + 2));
        }

        // the '}' closing the finally block
        current = lines.get(4).lineNumber();
        if (current != first + 3) {
            throw new Exception("finally line number table incorrect: got=" + current + " expected=" + (first + 3));
        }
    }

    static List<LineNumberInfo> findEntries() throws IOException {
        ClassModel self = ClassFile.of().parse(FinallyLineNumberTest.class.getResourceAsStream("FinallyLineNumberTest.class").readAllBytes());
        for (MethodModel m : self.methods()) {
            if (m.methodName().equalsString("method")) {
                CodeAttribute code_attribute = m.findAttribute(Attributes.code()).orElseThrow();
                for (Attribute<?> at : code_attribute.attributes()) {
                    if (at instanceof LineNumberTableAttribute lineAt) {
                        return lineAt.lineNumbers();
                    }
                }
            }
        }
        return null;
    }

    // This method should get LineNumberTable entries for:
    // *) The load of the null
    // *) The finally code for when an exception is *not* thrown
    // *) The actual return, which should have the same line as the load of the null
    // *) The finally code for when an exception *is* thrown, should have the same line as above finally code
    public static String method(int field) {
        try {
            return null;
        } finally {
            field+=1; // Dummy
        }
    }
}
