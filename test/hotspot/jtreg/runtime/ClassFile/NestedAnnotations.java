 /*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test NestedAnnotations
 * @summary The JVM handles nested annotations
 * @bug 8364655
 * @requires vm.flagless
 * @library /test/lib
 * @library /testlibrary/asm
 * @modules java.base/jdk.internal.misc
 *          java.desktop
 *          java.management
 * @run driver NestedAnnotations
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;

import static org.objectweb.asm.Opcodes.*;

public class NestedAnnotations {
    static void test() throws Exception {
        var cw = new ClassWriter(0);
        cw.visit(V17, 0, "Annotations", null, "java/lang/Object", null);
        final int number_of_annotations = 65535;
        var av = cw.visitAnnotation("LTest;", true);
        var stack = new ArrayList<AnnotationVisitor>(number_of_annotations + 1);
        stack.add(av);
        for (int i = 0; i < number_of_annotations; i++) {
            stack.add(av = av.visitAnnotation("value", "LTest;"));
        }
        for (int i = number_of_annotations; i != 0;) {
            stack.get(--i).visitEnd();
        }

        cw.visitEnd();
        // Does not matter whether the class is hidden, used for simplicity's sake.
        MethodHandles.lookup().defineHiddenClass(cw.toByteArray(), true);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("testIt")) {
            test();
        } else {
            OutputAnalyzer oa = ProcessTools.executeTestJava("NestedAnnotations", "testIt");
            oa.shouldHaveExitValue(0);
        }
    }
}

