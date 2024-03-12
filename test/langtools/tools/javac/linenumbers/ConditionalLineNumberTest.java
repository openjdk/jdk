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
 * @test
 * @bug 8034091
 * @summary Add LineNumberTable attributes for conditional operator (?:) split across several lines.
 * @enablePreview
 */

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ConditionalLineNumberTest {
    public static void main(String[] args) throws Exception {
        // check that we have 5 consecutive entries for method()
        List<LineNumberInfo> lines = findEntries();
        if (lines == null || lines.size() != 5)
            throw new Exception("conditional line number table incorrect");

        int current = lines.get(0).lineNumber();
        for (LineNumberInfo e : lines) {
            if (e.lineNumber() != current)
                throw new Exception("conditional line number table incorrect");
            current++;
        }
    }

    static List<LineNumberInfo> findEntries() throws IOException {
        ClassModel self = ClassFile.of().parse(ConditionalLineNumberTest.class.getResourceAsStream("ConditionalLineNumberTest.class").readAllBytes());
        for (MethodModel m : self.methods()) {
            if (m.methodName().equalsString("method")) {
                CodeAttribute code_attribute = m.findAttribute(Attributes.CODE).orElse(null);
                assert code_attribute != null;
                for (Attribute<?> at : code_attribute.attributes()) {
                    if (at instanceof LineNumberTableAttribute) {
                        return ((LineNumberTableAttribute)at).lineNumbers();
                    }
                }
            }
        }
        return null;
    }

    // This method should get one LineNumberTable entry per line
    // in the method body.
    public static String method(int field) {
        String s = field % 2 == 0 ?
            (field == 0 ? "false"
             : "true" + field) : //Breakpoint
            "false" + field; //Breakpoint
        return s;
    }
}
