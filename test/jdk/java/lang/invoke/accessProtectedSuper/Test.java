/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8022718
 * @summary Runtime accessibility checking: protected class, if extended, should be accessible from another package
 * @enablePreview
 * @compile -XDignore.symbol.file BogoLoader.java MethodInvoker.java Test.java anotherpkg/MethodSupplierOuter.java
 * @run main/othervm Test
 */

import java.lang.classfile.ClassTransform;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;

import static java.lang.classfile.ClassFile.ACC_PRIVATE;
import static java.lang.classfile.ClassFile.ACC_PROTECTED;
import static java.lang.classfile.ClassFile.ACC_PUBLIC;

interface MyFunctionalInterface {

    void invokeMethodReference();
}

public class Test {

    public static void main(String[] argv) throws Throwable {
        ClassTransform makeProtectedNop = ClassTransform.ACCEPT_ALL;
        ClassTransform makeProtectedMod = (cb, ce) -> {
            if (ce instanceof InnerClassesAttribute ica) {
                cb.accept(InnerClassesAttribute.of(ica.classes().stream().map(ici -> {
                    // AccessFlags doesn't support inner class flags yet
                    var flags = (ACC_PROTECTED | ici.flagsMask()) & ~(ACC_PRIVATE | ACC_PUBLIC);
                    System.out.println("visitInnerClass: name = " + ici.innerClass().asInternalName()
                            + ", outerName = " + ici.outerClass().map(ClassEntry::asInternalName).orElse("null")
                            + ", innerName = " + ici.innerName().map(Utf8Entry::stringValue).orElse("null")
                            + ", access original = 0x" + Integer.toHexString(ici.flagsMask())
                            + ", access modified to 0x" + Integer.toHexString(flags));
                    return InnerClassInfo.of(ici.innerClass(), ici.outerClass(), ici.innerName(), flags);
                }).toList()));
            } else {
                cb.accept(ce);
            }
        };

        int errors = 0;
        errors += tryModifiedInvocation(makeProtectedNop);
        errors += tryModifiedInvocation(makeProtectedMod);

        if (errors > 0) {
            throw new Error("FAIL; there were errors");
        }
    }

    private static int tryModifiedInvocation(ClassTransform makeProtected)
            throws Throwable {
        var replace = new HashMap<String, ClassTransform>();
        replace.put("anotherpkg.MethodSupplierOuter$MethodSupplier", makeProtected);
        var in_bogus = new HashSet<String>();
        in_bogus.add("MethodInvoker");
        in_bogus.add("MyFunctionalInterface");
        in_bogus.add("anotherpkg.MethodSupplierOuter"); // seems to be never loaded
        in_bogus.add("anotherpkg.MethodSupplierOuter$MethodSupplier");

        BogoLoader bl = new BogoLoader(in_bogus, replace);
        try {
            Class<?> isw = bl.loadClass("MethodInvoker");
            Method meth = isw.getMethod("invoke");
            Object result = meth.invoke(null);
        } catch (Throwable th) {
            System.out.flush();
            Thread.sleep(250); // Let Netbeans get its I/O sorted out.
            th.printStackTrace();
            System.err.flush();
            Thread.sleep(250); // Let Netbeans get its I/O sorted out.
            return 1;
        }
        return 0;
    }
}
