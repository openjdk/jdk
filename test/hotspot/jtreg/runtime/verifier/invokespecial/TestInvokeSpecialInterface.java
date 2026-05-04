/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=verified
 * @compile Run.java UseMethodRef.jasm UseInterfaceMethodRef.jasm TestInvokeSpecialInterface.java
 * @run main/othervm TestInvokeSpecialInterface true
 */

/*
 * @test id=unverified
 * @compile Run.java UseMethodRef.jasm UseInterfaceMethodRef.jasm TestInvokeSpecialInterface.java
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:-BytecodeVerificationRemote TestInvokeSpecialInterface false
 */

public class TestInvokeSpecialInterface {
    public static void main(String[] args) throws Throwable {
        if (args[0].equals("true")) {
            check_verified();
        } else {
            check_unverified();
        }
    }

    static void check_verified() {
        String veMsg = "interface method to invoke is not in a direct superinterface";
        try {
            UseMethodRef t = new UseMethodRef();
            UseMethodRef.test(t);
        }
        catch(VerifyError ve) {
            if (ve.getMessage().contains(veMsg)) {
                System.out.println("Got expected: " + ve);
            } else {
                throw new RuntimeException("Unexpected VerifyError thrown", ve);
            }
        }

        try {
            UseInterfaceMethodRef t = new UseInterfaceMethodRef();
            UseInterfaceMethodRef.test(t);
        }
        catch(VerifyError ve) {
            if (ve.getMessage().contains(veMsg)) {
                System.out.println("Got expected: " + ve);
            } else {
                throw new RuntimeException("Unexpected VerifyError thrown", ve);
            }
        }
    }

    static void check_unverified() {
        try {
            UseMethodRef t = new UseMethodRef();
            UseMethodRef.test(t);
        }
        catch(IncompatibleClassChangeError icce) {
            String icceMsg = "Method 'void java.lang.Runnable.run()' must be InterfaceMethodref constant";
            if (icce.getMessage().contains(icceMsg)) {
                System.out.println("Got expected: " + icce);
            } else {
                throw new RuntimeException("Unexpected IncompatibleClassChangeError", icce);
            }
        }

        try {
            UseInterfaceMethodRef t = new UseInterfaceMethodRef();
            UseInterfaceMethodRef.test(t);
        }
        catch(IncompatibleClassChangeError icce) {
            String icceMsg = "Interface method reference: 'void java.lang.Runnable.run()', is not in a direct superinterface of UseInterfaceMethodRef";
            if (icce.getMessage().contains(icceMsg)) {
                System.out.println("Got expected: " + icce);
            } else {
                throw new RuntimeException("Unexpected IncompatibleClassChangeError", icce);
            }
        }
    }
}
