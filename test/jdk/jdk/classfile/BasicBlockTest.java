/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing ClassFile complex basic blocks affecting SM generator.
 * @run junit BasicBlockTest
 */
import java.io.InputStream;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import org.junit.jupiter.api.Test;

class BasicBlockTest {

       public void npeInResolveMystery() {
            int i=0; Object key;
            Object[] a= new Object[0];
            for (; i < 0; i++) {
                if ((key = a[i]) == null) {}
            }
        }

    void exponentialComplexityInJointNeedLocalPartial(boolean a) {
        while (a) {
            if ((a || a) && (a || a) && (a || a) && (a || a) && (a || a) && (a || a)) {} else
            if ((a || a) && (a || a) && (a || a) && (a || a) && (a || a) && (a || a)) {} else
            if ((a || a) && (a || a) && (a || a) && (a || a) && (a || a) && (a || a)) {} else
            if ((a || a) && (a || a) && (a || a) && (a || a) && (a || a) && (a || a)) {} else
            if ((a || a) && (a || a) && (a || a) && (a || a) && (a || a) && (a || a)) {} else
            if ((a || a) && (a || a) && (a || a) && (a || a) && (a || a) && (a || a)) {}
        }
    }

    @Test
    void testPatternsCausingBasicBlockTroubles() throws IOException {
        try (InputStream in = BasicBlockTest.class.getResourceAsStream("BasicBlockTest.class")) {
            var cc = ClassFile.of();
            var classModel = cc.parse(in.readAllBytes());
            cc.build(classModel.thisClass().asSymbol(), classModel::forEach);
        }
    }
}
