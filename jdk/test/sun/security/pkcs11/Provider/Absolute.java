/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
/**
 * @test
 * @bug 7003952
 * @summary load DLLs and launch executables using fully qualified path
 */
import java.security.*;
import java.lang.reflect.*;
import sun.security.pkcs11.*;

public class Absolute {

    public static void main(String[] args) throws Exception {
        Constructor cons;
        try {
            Class clazz = Class.forName("sun.security.pkcs11.SunPKCS11");
            cons = clazz.getConstructor(new Class[] {String.class});
        } catch (Exception ex) {
            System.out.println("Skipping test - no PKCS11 provider available");
            return;
        }

        String config =
            System.getProperty("test.src", ".") + "/Absolute.cfg";

        try {
            Object obj = cons.newInstance(new Object[] {config});
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof ProviderException) {
                Throwable cause2 = cause.getCause();
                if ((cause2 == null) ||
                    !cause2.getMessage().startsWith(
                         "Absolute path required for library value:")) {
                    // rethrow
                    throw (ProviderException) cause;
                }
                System.out.println("Caught expected Exception: \n" + cause2);
            }
        }
    }
}
