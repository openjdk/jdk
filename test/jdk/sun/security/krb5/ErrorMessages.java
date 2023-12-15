/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8316771
 * @library /test/lib
 * @modules java.security.jgss/sun.security.krb5.internal:+open
 * @summary make sure each error code has a message
 */

import jdk.test.lib.Asserts;
import sun.security.krb5.internal.Krb5;

import java.lang.reflect.Field;
import java.util.Hashtable;

public class ErrorMessages {
    public static void main(String[] args) throws Exception {
        boolean isError = false;
        int count = 0;
        int size = -1;
        for (Field v : Krb5.class.getDeclaredFields()) {
            // The spec of the Class::getDeclaredFields method claims
            // "The elements in the returned array are not sorted and
            // are not in any particular order". However, the current
            // implementation seems to be listing them in the order
            // they appear in the code.
            if (v.getName().equals("errMsgList")) {
                v.setAccessible(true);
                size = ((Hashtable)v.get(null)).size();
                break;
            }
            if (v.getName().equals("KDC_ERR_NONE")) {
                isError = true;
            }
            if (!isError) continue;
            Asserts.assertNotEquals(Krb5.getErrorMessage((int)v.get(null)),
                    null, "No msg for " + v);
            count++;
        }
        Asserts.assertEQ(count, size, "Different size");
    }
}
