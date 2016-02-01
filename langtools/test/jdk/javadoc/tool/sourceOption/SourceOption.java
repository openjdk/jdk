/*
 * Copyright (c) 2006, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @ignore API modifications
 * @modules jdk.javadoc
 * @run main/fail SourceOption 7
 * @run main      SourceOption 9
 * @run main      SourceOption
 */

/*
 * TEST NOTE
 * With JDK9, this test has been transformed into a NEGATIVE test.
 *
 * Generally speaking, this test should check a feature not in at least
 * one of the currently supported previous versions.  In this manner,
 * a failure of the -source option to be honored would mean a pass of
 * the test, and therefore a failure of the -source option.
 *
 * For JDK9 and JDK10, both support 1.7, which did not support javac's
 * lambda construct.  So we set "-source 1.7" to compile a .java file
 * containing the lambda construct.  javac should fail, thus showing
 * -source to be working.  Thus the test passes.
 *
 * The second jtreg @run command checks to make sure that the source
 * provided is valid for the current release of the JDK.
 *
 *  fixVersion: JDK11
 *      replace ./p/LambdaConstructTest.java with a missing from
 *      JDK8, JDK9, or JDK10.  Set -source below appropriately.
 */

import java.util.Collections;
import java.util.Set;

import javax.lang.model.SourceVersion;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.Doclet.Option;
import jdk.javadoc.doclet.DocletEnvironment;

public class SourceOption implements Doclet {

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

    public boolean run(DocletEnvironment root) {
        root.getIncludedClasses();         // force parser into action
        return true;
    }

    @Override
    public String getName() {
        return "Test";
    }

    @Override
    public Set<Option> getSupportedOptions() {
        return Collections.emptySet();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }
}
