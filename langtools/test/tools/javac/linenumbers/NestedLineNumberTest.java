/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8061778
 * @summary  Wrong LineNumberTable for default constructors
 */

import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.Method;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.LineNumberTable_attribute;
import com.sun.tools.classfile.LineNumberTable_attribute.Entry;

import java.io.IOException;

public class NestedLineNumberTest {

    public static void main(String[] args) throws Exception {
        Entry[] lines = findEntries();
        if (lines == null || lines.length != 1) {
            int found = lines == null ? 0 : lines.length;
            error(String.format("LineNumberTable contains wrong number of entries - expected %d, found %d", 1, found));
        }

        int line = lines[0].line_number;
        if (line != 78) {
            error(String.format("LineNumberTable contains wrong line number - expected %d, found %d", 78, line));
        }
    }

    static Entry[] findEntries() throws IOException, ConstantPoolException {
        ClassFile self = ClassFile.read(NestedLineNumberTest.Test.class.getResourceAsStream("NestedLineNumberTest$Test.class"));
        for (Method m : self.methods) {
            if ("<init>".equals(m.getName(self.constant_pool))) {
                Code_attribute code_attribute = (Code_attribute)m.attributes.get(Attribute.Code);
                for (Attribute at : code_attribute.attributes) {
                    if (Attribute.LineNumberTable.equals(at.getName(self.constant_pool))) {
                        return ((LineNumberTable_attribute)at).line_number_table;
                    }
                }
            }
        }
        return null;
    }

    static void error(String msg) {
        throw new AssertionError(msg);
    }

    // The default constructor in this class should get only one LineNumberTable entry,
    // pointing to the first line of the declaration of class Test.
    static class Test {
        static class Empty { }
    }
}
