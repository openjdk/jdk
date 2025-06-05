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

import jdk.test.whitebox.WhiteBox;

public class ResolvedReferencesWb {
    public static void main(String[] args) throws Exception {
        WhiteBox wb = WhiteBox.getWhiteBox();

        if (args.length < 1) {
            throw new RuntimeException("Test requires arg: [true|false]");
        }

        if (!args[0].equals("true") && !args[0].equals("false")) {
            throw new RuntimeException("Invalid argument: Test requires arg: [true|false]");
        }

        ResolvedReferencesTestApp t = new ResolvedReferencesTestApp();
        Object[] resolvedReferences = wb.getResolvedReferences(ResolvedReferencesTestApp.class);
        boolean isArchived = (args[0].equals("true"));

        if (resolvedReferences.length <= 0) {
            throw new RuntimeException("Resolved reference should not be null");
        }

        boolean foundFoo = false;
        boolean foundBar = false;
        boolean foundQux = false;

        for (Object o : resolvedReferences) {
            if (o != null) {
                foundFoo |= (o.equals("fooString"));
                foundBar |= (o.equals("barString"));
                foundQux |= (o.equals("quxString"));
            }
        }

        if (isArchived) {
            // CDS eagerly resolves all the string literals in the ConstantPool. At this point, all
            // three strings should be in the resolvedReferences array.
            if (!foundFoo || !foundBar || !foundQux) {
                throwException(resolvedReferences, "Incorrect resolved references array, all strings should be present");
            }
        } else {
            // If the class is not archived, the string literals in the ConstantPool are resolved
            // on-demand. At this point, ResolvedReferencesTestApp::<clinit> has been executed
            // so the two strings used there should be in the resolvedReferences array.
            // ResolvedReferencesTestApp::qux() is not executed so "quxString"
            // should not yet be resolved.
            if (!foundFoo || !foundBar || foundQux) {
                throwException(resolvedReferences, "Incorrect resolved references array, quxString should not be archived");
            }
        }
    }

    static void throwException(Object[] resolvedRefs, String errMsg) throws RuntimeException {
        System.out.printf("Resolved References Array Length: %d\n", resolvedRefs.length);
        for (Object o : resolvedRefs) {
            System.out.println(o);
        }
        throw new RuntimeException(errMsg);
    }
}
