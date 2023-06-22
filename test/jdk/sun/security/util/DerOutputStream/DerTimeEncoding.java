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
 * @bug 8279254
 * @summary Changed time encoding to correctly use UTC between 1950-2050 and GeneralizedTime otherwise
 * @library /test/lib
 * @modules java.base/sun.security.util
 */

import java.util.Date;
import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;

public class DerTimeEncoding {
    public static void main(String args[]) throws Exception {
        //Check that dates after 2050 use GeneralizedTime
        DerOutputStream out = new DerOutputStream();
        Date generalizedTimeDate = new Date(2688854400000L); // Test date is 3/17/2055
        out.putTime(generalizedTimeDate);
        DerValue val = new DerValue(out.toByteArray());
        if (val.tag != DerValue.tag_GeneralizedTime) {
            System.out.println("putTime incorrectly serialized to UTC time instead of GeneralizedTime");
            throw new RuntimeException("Incorrect Der date format");
        }

        //Check dates between 1950-2050 use UTC time
        out = new DerOutputStream();
        Date utcDate = new Date(242092800000L); //Test date is 9/3/1977
        out.putTime(utcDate);
        val = new DerValue(out.toByteArray());
        if (val.tag != DerValue.tag_UtcTime) {
            System.out.println("putTime incorrectly serialized to Generalized time instead of UTC time");
            throw new RuntimeException("Incorrect Der date format");
        }
    }
}