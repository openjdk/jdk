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
 * @bug 4959985
 * @summary Verify that (verbose) warnings are no longer generated when
 *          the default first-sentence algorithm doesn't match the
 *          BreakIterator algorithm.
 */

import com.sun.javadoc.*;

public class BreakIteratorWarning extends Doclet {

    public static void main(String[] args) {
        String thisFile = "" +
            new java.io.File(System.getProperty("test.src", "."),
                             "BreakIteratorWarning.java");

        if (com.sun.tools.javadoc.Main.execute(
                "javadoc",
                "BreakIteratorWarning",
                BreakIteratorWarning.class.getClassLoader(),
                new String[] {"-Xwerror", thisFile}) != 0)
            throw new Error("Javadoc encountered warnings or errors.");
    }

    public static boolean start(RootDoc root) {
        ClassDoc cd = root.classes()[0];
        FieldDoc fd = cd.fields()[0];
        fd.firstSentenceTags();
        return true;
    }


    /**
     * "He'll never catch up!" the Sicilian cried.  "Inconceivable!"
     * "You keep using that word!" the Spaniard snapped.  "I do not
     * think it means what you think it means."
     *
     * <p> This comment used to trigger a warning, but no longer does.
     */
    public String author = "William Goldman";
}
