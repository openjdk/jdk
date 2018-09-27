/*
 * Copyright (c) 2006, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     6507179
 * @summary Ensure that "-source" option isn't ignored.
 * @author  Scott Seligman
 * @modules jdk.javadoc
 * @run main/fail SourceOption 7
 * @run main      SourceOption 9
 * @run main      SourceOption
 */

/*
 * In order to test whether or not the -source option is working
 * correctly, this test tries to parse source code that contains
 * a feature that is not available in at least one of the currently
 * supported previous versions.
 *
 * Parsing such code should be expected to fail; if the action
 * passes, that means the -source option is (incorrectly) ineffective.
 *
 * Additional actions are performed to ensure that the source
 * provided is valid for the current release of the JDK.
 *
 * As support for older versions of the platform are dropped, the
 * source code (currently p/LambdaConstructTest.java) will need to
 * be updated with a more recent feature.
 */

import com.sun.javadoc.*;

public class SourceOption extends Doclet {

    public static void main(String[] args) {
        String[] params;
        if ((args == null) || (args.length==0)) {
            params = new String[]{"p"};
            System.out.println("NOTE : -source not provided, default taken");
        } else {
            params = new String[]{"-source", args[0], "p"};
            System.out.println("NOTE : -source will be: " + args[0]);
        }

        if (com.sun.tools.javadoc.Main.execute(
                "javadoc",
                "SourceOption",
                SourceOption.class.getClassLoader(),
                params) != 0)
        throw new Error("Javadoc encountered warnings or errors.");

    }

    public static boolean start(RootDoc root) {
        root.classes();         // force parser into action
        return true;
    }
}
