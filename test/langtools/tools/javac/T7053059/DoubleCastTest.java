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
 * @bug 8015499
 * @summary javac, Gen is generating extra checkcast instructions in some corner cases
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 *          java.base/jdk.internal.classfile.impl
 *          jdk.compiler/com.sun.tools.javac.util
 * @run main DoubleCastTest
 */

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.CodeAttribute;
import jdk.internal.classfile.constantpool.ClassEntry;
import jdk.internal.classfile.instruction.TypeCheckInstruction;
import com.sun.tools.javac.util.Assert;

public class DoubleCastTest {
    class C {
        Object x;
        Object m() { return null; }
        void m1(byte[] b) {}
        void m2() {
            Object o;
            Object[] os = null;
            m1((byte[])(o = null));
            m1((byte[])o);
            m1((byte[])(o == null ? o : o));
            m1((byte[])m());
            m1((byte[])os[0]);
            m1((byte[])this.x);
            m1((byte[])((byte []) (o = null)));
        }
    }

    public static void main(String... cmdline) throws Exception {

        ClassModel cls = Classfile.of().parse(Objects.requireNonNull(DoubleCastTest.class.getResourceAsStream("DoubleCastTest$C.class")).readAllBytes());
        for (MethodModel m: cls.methods())
            check(m);
    }

    static void check(MethodModel m) throws Exception {
        boolean last_is_cast = false;
        ClassEntry last_ref = null;
        CodeAttribute ea = m.findAttribute(Attributes.CODE).orElseThrow();
        for (int i = 0; i < ea.elementList().size(); ++i) {
            CodeElement ce = ea.elementList().get(i);
            if (ce instanceof TypeCheckInstruction ins && ins.opcode() == Opcode.CHECKCAST) {
                Assert.check
                    (!(last_is_cast && last_ref == ins.type()),
                     "Double cast found - Test failed");
                last_is_cast = true;
                last_ref = ins.type();
            } else {
                last_is_cast = false;
            }
        }
    }
}
