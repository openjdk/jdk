/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4909767
 * @summary Verify that omitting Doclet.languageVersion() hides 1.5 language
 *      features from the doclet.
 */

import java.util.List;
import com.sun.javadoc.*;

public class LangVers extends Doclet {

    public static void main(String[] args) {
        String thisFile = "" +
            new java.io.File(System.getProperty("test.src", "."),
                             "LangVers.java");

        if (com.sun.tools.javadoc.Main.execute(
                "javadoc",
                "LangVers",
                LangVers.class.getClassLoader(),
                new String[] {thisFile}) != 0)
            throw new Error("Javadoc encountered warnings or errors.");
    }

    public static boolean start(RootDoc root) {
        ClassDoc fishdoc = root.classNamed("LangVers.Fish");
        System.out.println(fishdoc);
        if (fishdoc.isEnum()) {
            throw new Error("Enums are not hidden.");
        }

        for (MethodDoc meth : fishdoc.methods()) {
            System.out.println(meth);
            if (meth.flatSignature().indexOf('<') >= 0) {
                throw new Error("Type parameters are not hidden.");
            }
        }

        return true;
    }

    public enum Fish {
        One, Two, Red, Blue;

        public void enroll(List<? super Fish> school) {
            school.add(this);
        }
    }
}
