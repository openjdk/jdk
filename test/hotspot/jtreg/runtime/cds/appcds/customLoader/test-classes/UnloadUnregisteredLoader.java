/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import jdk.test.whitebox.WhiteBox;
import jdk.test.lib.classloader.ClassUnloadCommon;
import java.util.List;
import java.util.Set;

public class UnloadUnregisteredLoader {
    public static void main(String args[]) throws Exception {
        String path = args[0];
        URL url = new File(path).toURI().toURL();
        URL[] urls = new URL[] {url};
        WhiteBox wb = WhiteBox.getWhiteBox();
        String className = "CustomLoadee";

        for (int i=0; i<5; i++) {
            doit(urls, className, (i == 0));

            Set<String> aliveClasses = ClassUnloadCommon.triggerUnloading(List.of(className));
            ClassUnloadCommon.failIf(!aliveClasses.isEmpty(), "should have been unloaded: " + aliveClasses);
        }
    }

    public static void doit(URL urls[], String className, boolean isFirstTime) throws Exception {
        ClassLoader appLoader = UnloadUnregisteredLoader.class.getClassLoader();
        CustomLoader custLoader = new CustomLoader(urls, appLoader);

        // Part 1 -- load CustomLoadee. It should be loaded from archive when isFirstTime==true

        Class klass = custLoader.loadClass(className);
        WhiteBox wb = WhiteBox.getWhiteBox();
        if (wb.isSharedClass(UnloadUnregisteredLoader.class)) {
            if (isFirstTime) {
                // First time: we should be able to load the class from the CDS archive
                if (!wb.isSharedClass(klass)) {
                    throw new RuntimeException("wb.isSharedClass(klass) should be true for first time");
                }
            } else {
                // Second time:  the class in the CDS archive is not available, because it has not been cleaned
                // up (see bug 8140287), so we must load the class dynamically.
                //
                // FIXME: after 8140287 is fixed, class should be shard regardless of isFirstTime.
                if (wb.isSharedClass(klass)) {
                    throw new RuntimeException("wb.isSharedClass(klass) should be false for second time");
                }
            }
        }

        // Part 2
        //
        // CustomLoadee5 is never loaded from the archive, because the classfile bytes don't match
        // CustomLoadee5Child is never loaded from the archive, its super is not loaded from the archive
        try (InputStream in = appLoader.getResourceAsStream("CustomLoadee5.class")) {
            byte[] b = in.readAllBytes();
            Util.replace(b, "this is", "DAS IST"); // Modify the bytecodes
            Class<?> c = custLoader.myDefineClass(b, 0, b.length);
            System.out.println(c.newInstance());
            if (!"DAS IST CustomLoadee5".equals(c.newInstance().toString())) {
                throw new RuntimeException("Bytecode modification not successful");
            }
            if (wb.isSharedClass(c)) {
                throw new RuntimeException(c + "should not be loaded from CDS");
            }
        }

        // When isFirstTime==true, the VM will try to load the archived copy of CustomLoadee5Child,
        // but it will fail (because CustomLoadee5 was not loaded from the archive) and will recover
        // by decoding the class from its classfile data.
        // This failure should not leave the JVM in an inconsistent state.
        Class<?> child = custLoader.loadClass("CustomLoadee5Child");
        if (wb.isSharedClass(child)) {
            throw new RuntimeException(child + "should not be loaded from CDS");
        }
    }

    static class CustomLoader extends URLClassLoader {
        public CustomLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        public Class<?> myDefineClass(byte[] b, int off, int len)
            throws ClassFormatError
        {
            return super.defineClass(b, off, len);
        }
    }
}
