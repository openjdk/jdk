/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8389259
 * @summary Sanity check that Class.desiredAssertionStatus() works.
 * @library /test/lib
 * @run testng/othervm -esa -ea -Dsys.option=on  -Duser.option=on Sanity
 * @run testng/othervm -dsa -ea -Dsys.option=off -Duser.option=on Sanity
 * @run testng/othervm -esa -da -Dsys.option=on  -Duser.option=off Sanity
 * @run testng/othervm -dsa -da -Dsys.option=off -Duser.option=off Sanity
 */

import org.testng.annotations.Test;
import org.testng.Assert;

public class Sanity {

    @Test
    public void test() throws Exception {
        boolean systemDefaultOn = System.getProperty("sys.option").equals("on");
        boolean userDefaultOn = System.getProperty("user.option").equals("on");

        Assert.assertTrue(int.class.desiredAssertionStatus() == false);
        Assert.assertTrue(Object.class.desiredAssertionStatus() == systemDefaultOn);
        Assert.assertTrue(Object[].class.desiredAssertionStatus() == false);
        Assert.assertTrue(Sanity.class.desiredAssertionStatus() == userDefaultOn);
        Assert.assertTrue(Sanity[].class.desiredAssertionStatus() == false);
    }
}
