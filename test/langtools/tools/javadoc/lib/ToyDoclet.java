/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.RootDoc;

@SuppressWarnings("deprecation")
public class ToyDoclet {

    public static boolean start(RootDoc root) {
        String whoami = "I am a toy doclet";
        root.printNotice("Notice: " + whoami);
        boolean status = false;
        for (ClassDoc cls : root.classes()) {
            if (!status) status = true;
            root.printNotice("Classes: " + cls);
            printClassMembers(root, cls);
        }
        for (ClassDoc cls : root.specifiedClasses()) {
            if (!status) status = true;
            root.printNotice("Specified-classes: " + cls);
            printClassMembers(root, cls);
        }
        for (PackageDoc pkg : root.specifiedPackages()) {
            if (!status) status = true;
            root.printNotice("Specified-packages: " + pkg);
        }
        return status;
    }

    static void printClassMembers(RootDoc root, ClassDoc cls) {
        root.printNotice("Members for: " + cls);
        root.printNotice("  extends " + Arrays.asList(cls.superclass()));
        root.printNotice("  Fields: ");
        printMembers(root, cls.fields());
        root.printNotice("  Constructor: ");
        printMembers(root, cls.constructors());
        root.printNotice("  Method: ");
        printMembers(root, cls.methods());
        if (cls.superclass() != null && !cls.superclassType().toString().equals("java.lang.Object"))
            printClassMembers(root, cls.superclass());
    }

    static void printMembers(RootDoc root, ProgramElementDoc[] pgmDocs) {
        for (ProgramElementDoc pgmDoc : pgmDocs) {
            root.printNotice("     " + pgmDoc + ", Comments: " + pgmDoc.getRawCommentText());
        }
    }

    public static int optionLength(String option) {
        System.out.println("option: " + option);
        return 0;  // all options are unsupported
    }
}
