/*
 * Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4252236
 * @summary ActivationGroupDesc should not do early binding of default classname
 * @author Laird Dornin
 *
 * @library ../../../testlibrary
 * @build TestLibrary
 * @run main CheckDefaultGroupName
 */

import java.rmi.activation.*;

/**
 * Test checks the group name for an ActivationGroupDesc which is
 * created with no explicit activation group implementation class name
 * supplied.
 */
public class CheckDefaultGroupName {
    public static void main(String[] args) {
        System.out.println("\n\nRegression test for, 4252236\n\n");

        ActivationGroupDesc groupDesc =
            new ActivationGroupDesc(null, null);

        String className = groupDesc.getClassName();
        if (className != null) {
            TestLibrary.bomb("ActivationGroupDesc had incorrect default" +
                             " group implementation class name: " + className);
        } else {
            System.err.println("test passed, had correct default group" +
                               " implementation class name: " + className +
                               "\n\n");
        }
    }
}
