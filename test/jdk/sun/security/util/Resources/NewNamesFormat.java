/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6987827
 * @modules java.base/sun.security.util
 *          java.base/sun.security.tools.keytool
 *          jdk.jartool/sun.security.tools.jarsigner
 * @summary security/util/Resources.java needs improvement
 */


import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * This test makes sure that the keys in resources files are using the new
 * format and there is no duplication.
 */
public class NewNamesFormat {
    public static void main(String[] args) throws Exception {
        checkRes("sun.security.util.Resources");
        checkRes("sun.security.util.AuthResources");
        checkRes("sun.security.tools.jarsigner.Resources");
        checkRes("sun.security.tools.keytool.Resources");
    }

    private static void checkRes(String resName) throws Exception {
        System.out.println("Checking " + resName + "...");
        Class clazz = Class.forName(resName);
        Method m = clazz.getMethod("getContents");
        Object[][] contents = (Object[][])m.invoke(clazz.newInstance());
        Set<String> keys = new HashSet<String>();
        for (Object[] pair: contents) {
            String key = (String)pair[0];
            if (keys.contains(key)) {
                System.out.println("Found dup: " + key);
                throw new Exception();
            }
            checkKey(key);
            keys.add(key);
        }
    }

    private static void checkKey(String key) throws Exception {
        for (char c: key.toCharArray()) {
            if (Character.isLetter(c) || Character.isDigit(c) ||
                    c == '{' || c == '}' || c == '.') {
                // OK
            } else {
                System.out.println("Illegal char [" + c + "] in key: " + key);
                throw new Exception();
            }
        }
    }
}
