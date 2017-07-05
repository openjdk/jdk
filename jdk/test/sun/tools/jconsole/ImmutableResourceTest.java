/*
 * Copyright (c) 2005, 2007, Oracle and/or its affiliates. All rights reserved.
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
 *
 *
 *  This isn't the test case: ImmutableResourceTest.sh is.
 *  Refer to ImmutableResourceTest.sh when running this test.
 *
 *  @bug        6287579
 *  @summary    SubClasses of ListResourceBundle should fix getContents()
 */
import java.util.ResourceBundle;

public class ImmutableResourceTest {

    public static void main(String[] args) throws Exception {

        /* Reach under the covers and get the message strings */
        sun.tools.jconsole.resources.JConsoleResources jcr =
            new sun.tools.jconsole.resources.JConsoleResources ();
        Object [][] testData = jcr.getContents();

        /* Shred our copy of the message strings */
        for (int ii = 0; ii < testData.length; ii++) {
            testData[ii][0] = "xxx";
            testData[ii][1] = "yyy";
        }

        /*
         * Try a lookup for the shredded key.
         * If this is successful we have a problem.
         */
        String ss = sun.tools.jconsole.Resources.getText("xxx");
        if ("yyy".equals(ss)) {
            throw new Exception ("SubClasses of ListResourceBundle should fix getContents()");
        }
        System.out.println("...Finished.");
    }
}
