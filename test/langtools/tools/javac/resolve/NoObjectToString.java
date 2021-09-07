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
 * @bug 8272564
 * @summary Correct resolution of toString() (and other similar calls) on interfaces
 * @modules jdk.jdeps/com.sun.tools.classfile
 * @compile NoObjectToString.java
 * @run main NoObjectToString
 */

import java.io.*;
import com.sun.tools.classfile.*;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Methodref_info;

public class NoObjectToString {
    public static void main(String... args) throws Exception {
        NoObjectToString c = new NoObjectToString();
        c.run(args);
    }

    void run(String... args) throws Exception {
         //Verify there are no references to Object.toString() in a Test:
        InputStream in = NoObjectToString.class.getResourceAsStream("NoObjectToString$Test.class");
        try {
            ClassFile cf = ClassFile.read(in);
            for (ConstantPool.CPInfo cpinfo: cf.constant_pool.entries()) {
                if (cpinfo.getTag() == ConstantPool.CONSTANT_Methodref) {
                    CONSTANT_Methodref_info ref = (CONSTANT_Methodref_info) cpinfo;
                    String methodDesc = ref.getClassInfo().getName() + "." + ref.getNameAndTypeInfo().getName() + ":" + ref.getNameAndTypeInfo().getType();

                    if ("java/lang/Object.toString:()Ljava/lang/String;".equals(methodDesc)) {
                        throw new AssertionError("Found call to j.l.Object.toString");
                    }
                }
            }
        } catch (ConstantPoolException ignore) {
            throw new AssertionError(ignore);
        } finally {
            in.close();
        }
    }

    class Test {
        void test(I i, J j, K k) {
            i.toString();
            j.toString();
            k.toString();
        }
    }

    interface I {
        public String toString();
    }
    interface J extends I {}
    interface K {}

}
