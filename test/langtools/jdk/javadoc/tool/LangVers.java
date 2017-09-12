/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @ignore API, re-evaluate, unsure of this test.
 * @modules jdk.javadoc
 */

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.lang.model.SourceVersion;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;

public class LangVers implements Doclet {

    public static void main(String[] args) {
        String thisFile = "" +
            new java.io.File(System.getProperty("test.src", "."),
                             "LangVers.java");

        String[] toolargs = {
            "-doclet", "LangVers",
            "-docletpath", System.getProperty("test.classes", "."),
        };
        if (jdk.javadoc.internal.tool.Main.execute(toolargs) != 0)
            throw new Error("Javadoc encountered warnings or errors.");
    }

    public boolean run(DocletEnvironment root) {
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
