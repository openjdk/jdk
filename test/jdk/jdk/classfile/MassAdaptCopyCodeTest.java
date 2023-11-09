/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing Classfile massive class adaptation.
 * @run junit MassAdaptCopyCodeTest
 */
import helpers.ByteArrayClassLoader;
import jdk.internal.classfile.ClassModel;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.CodeModel;
import jdk.internal.classfile.CodeTransform;
import jdk.internal.classfile.MethodModel;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MassAdaptCopyCodeTest {

    //final static String testClasses = "target/classes"; // "/w/basejdk/build/linux-x86_64-server-release/jdk/modules/java.base"

    final Map<String, ByteArrayClassLoader.ClassData> classNameToClass = new HashMap<>();
    String base;

    @Test
    void testInstructionAdapt() throws Exception {
        File root = Paths.get(URI.create(MassAdaptCopyCodeTest.class.getResource("MassAdaptCopyCodeTest.class").toString())).getParent().toFile();
        base = root.getCanonicalPath();
        copy(root);
        load();
    }

    void copy(File f) throws Exception {
        if (f.isDirectory()) {
            for (File lf : f.listFiles()) {
                copy(lf);
            }
        }
        else {
            String n = f.getCanonicalPath().substring(base.length() + 1);
            if (n.endsWith(".class") && !n.endsWith("module-info.class") && !n.endsWith("-split.class")) {
                copy(n.substring(0, n.length() - 6).replace(File.separatorChar, '.'),
                     Files.readAllBytes(f.toPath()));
            }
        }
    }

    void copy(String name, byte[] bytes) throws Exception {
        byte[] newBytes = adaptCopy(Classfile.of().parse(bytes));
        classNameToClass.put(name, new ByteArrayClassLoader.ClassData(name, newBytes));
        if (name.contains("/")) throw new RuntimeException(name);
    }

    public byte[] adaptCopy(ClassModel cm) {
        return Classfile.of().transform(cm, (cb, ce) -> {
            if (ce instanceof MethodModel mm) {
                cb.transformMethod(mm, (mb, me) -> {
                    if (me instanceof CodeModel xm) {
                        mb.transformCode(xm, CodeTransform.ACCEPT_ALL);
                    }
                    else
                        mb.with(me);
                });
            }
            else
                cb.with(ce);
        });
    }

    public void load() throws Exception {
        new ByteArrayClassLoader(MassAdaptCopyCodeTest.class.getClassLoader(), classNameToClass)
                .loadAll();
    }
}
