/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 * @compile strictFields.jasm
 * @run main/othervm -Xverify:remote runtime.valhalla.inlinetypes.verifier.StrictFields
 */

package runtime.valhalla.inlinetypes.verifier;

public class StrictFields {
    public static void main(String[] args) throws Throwable {

        // First load a class where strict should be ignored
        Class<?> c = Class.forName("StrictIgnore");

        // Now load a well-formed strict-using value class
        c = Class.forName("StrictBase");

        // Now a bad class
        try {
            c = Class.forName("PostInitStrict");
            throw new Error("VerifyError was not thrown as expected!");
        } catch (VerifyError ve) {
            // Once strict non-final is possible, expect "Illegal use of putfield on a strict field"
            if (!ve.getMessage().startsWith("All strict final fields must be initialized before super()")) {
                throw new Error("Wrong VerifyError thrown", ve);
            } else {
                System.out.println("Expected VerifyError was thrown");
            }
        }

        // Now a bad class that tries to write to a super class's strict field
        // in the preinit phase
        try {
            c = Class.forName("BadStrictSubPreInit");
            throw new Error("VerifyError was not thrown as expected!");
        } catch (VerifyError ve) {
            if (!ve.getMessage().startsWith("Bad type on operand stack")) {
                throw new Error("Wrong VerifyError thrown", ve);
            } else {
                System.out.println("Expected VerifyError was thrown");
            }
        }

        // Now a bad class that tries to write to a super class's strict field
        // in the post phase. This is not a verification error but we test it
        // here for completeness.Expected exception:
        //    java.lang.IllegalAccessError: Update to non-static final field
        //      BadStrictSubPostInit.x attempted from a different class
        //       (BadStrictSubPostInit) than the field's declaring class
        try {
            c = Class.forName("BadStrictSubPostInit");
            Object o = c.newInstance();
            throw new Error("IllegalAccessErrorError was not thrown as expected!");
        } catch (IllegalAccessError iae) {
            if (!iae.getMessage().startsWith("Update to non-static final field")) {
                throw new Error("Wrong IllegalAccessError thrown", iae);
            } else {
                System.out.println("Expected IllegalAccessError was thrown");
            }
        }

    }
}
