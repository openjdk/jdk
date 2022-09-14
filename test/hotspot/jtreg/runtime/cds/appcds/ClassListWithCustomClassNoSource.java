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
 *
 */

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;


public class ClassListWithCustomClassNoSource {
    private static byte[] helloBytes;
    private static final String HELLO = "Hello";
    static class CL extends ClassLoader {
        private ProtectionDomain pd;
        public CL(String name, ClassLoader parent, ProtectionDomain protD) {
            super(name, parent);
            pd = protD;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (pd == null) {
                pd = new ProtectionDomain(null, null);
            }
            return defineClass(name, helloBytes, 0, helloBytes.length, pd);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new RuntimeException("Invalid arg, Use 1, 2, or 3");
        }

        ClassLoader thisLoader = ClassListWithCustomClassNoSource.class.getClassLoader();
        helloBytes = thisLoader.getResourceAsStream(HELLO + ".class").readAllBytes();

        switch(args[0]) {
        case "1":
            Class<?> cls1 = (new CL("HelloLoader", null, null)).loadClass(HELLO);
            System.out.println(HELLO + " was successfully loaded by " + cls1.getClassLoader().getName());
            break;
        case "2":
            ProtectionDomain p = ClassListWithCustomClassNoSource.class.getProtectionDomain();
            Class<?> cls2 = (new CL("HelloLoader", null, p)).loadClass(HELLO);
            System.out.println(HELLO + " was successfully loaded by " + cls2.getClassLoader().getName());
            break;
        case "3":
            URL url = ClassListWithCustomClassNoSource.class.getProtectionDomain().getCodeSource().getLocation();
            URLClassLoader urlLoader = new URLClassLoader("HelloClassLoader", new URL[] {url}, null);
            Class<?> cls = urlLoader.loadClass(HELLO);
            if (cls != null) {
                System.out.println(HELLO + " was loaded by " + cls.getClassLoader().getName());
                if (urlLoader != cls.getClassLoader()) {
                    System.out.println(HELLO + " was not loaded by " + urlLoader.getName());
                }
            } else {
                System.out.println(HELLO + " is not loaded");
            }
            break;
        default:
            throw new RuntimeException("Should have one argument,  1, 2 or 3");
        }
    }
}
