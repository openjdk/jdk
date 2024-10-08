/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8182774
 * @enablePreview
 * @summary test on class with a verification error
 * @modules jdk.jdeps/com.sun.tools.javap
 */

import java.io.*;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.nio.file.Path;
import java.util.*;

public class VerificationTest {
    public static void main(String... args) throws Exception {
        new VerificationTest().run();
    }

    void run() throws Exception {
        String testClasses = System.getProperty("test.classes");
        String invalidClass = "InvalidClass";
        ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS).buildTo(Path.of(testClasses, invalidClass + ".class"), ClassDesc.of(invalidClass), clb ->
                clb.withMethodBody("methodWithMissingStackMap", ConstantDescs.MTD_void, 0, cob ->
                        cob.iconst_0().ifThen(tb -> tb.nop()).return_()));
        String out = javap("-verify", "-classpath", testClasses, invalidClass);
        if (!out.contains("Expecting a stackmap frame at branch target")) {
            throw new Exception("Expected output not found");
        }
    }

    String javap(String... args) throws Exception {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        int rc = com.sun.tools.javap.Main.run(args, out);
        out.close();
        System.out.println(sw.toString());
        if (rc < 0)
            throw new Exception("javap exited, rc=" + rc);
        return sw.toString();
    }
}
