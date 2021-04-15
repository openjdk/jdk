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
 * @bug 8200559
 * @summary Test class definition by use of instrumentation API.
 * @library /test/lib
 * @modules java.instrument
 * @run main RedefineClassHelper
 * @run main/othervm -javaagent:redefineagent.jar DefineClassInstrumentation
 */

import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AllPermission;
import java.security.ProtectionDomain;

public class DefineClassInstrumentation {

    public static void main(String[] unused) throws Throwable {
        doDefine(null);
        doDefine(new ProtectionDomain(null, null));
    }

    private static void doDefine(ProtectionDomain pd) {
        try {
            URLClassLoader loader = new URLClassLoader(new URL[0], null);

            byte[] classFile;
            try (InputStream inputStream = DefineClassInstrumentation.class.getResourceAsStream("DefineClassInstrumentation.class")) {
                classFile = inputStream.readAllBytes();
            }
            Class<?> c = RedefineClassHelper.instrumentation.defineClass(loader, pd, classFile);
            if (c == DefineClassInstrumentation.class) {
                throw new RuntimeException("Class defined by system loader");
            }
            if (pd == null) {
                if (!c.getProtectionDomain().getPermissions().implies(new AllPermission())) {
                    throw new RuntimeException("Protection domain not set to default protection domain");
                }
            } else if (pd != c.getProtectionDomain()) {
                throw new RuntimeException("Protection domain not set correctly");
            }
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException("Failed class definition");
        }
    }
}
