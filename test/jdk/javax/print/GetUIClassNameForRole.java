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

/*
 * @test
 * @bug 8392783
 * @summary Win32PrintService.getUIClassNamesForRole 
 *          throws ArrayIndexOutOfBoundsException
 * @requires (os.family == "windows")
 * @run main GetUIClassNameForRole
 */

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.ServiceUIFactory;

public class GetUIClassNameForRole {
    private static final int DOCUMENT_PROPERTIES_ROLE =
            ServiceUIFactory.RESERVED_UIROLE + 100;

    public static void main(String[] args) {

        for (PrintService service :
                PrintServiceLookup.lookupPrintServices(null, null)) {
            if (!service.getClass().getName()
                    .equals("sun.print.Win32PrintService")) {
                continue;
            }


            ServiceUIFactory factory = service.getServiceUIFactory();
            String[] names = factory.getUIClassNamesForRole(
                    DOCUMENT_PROPERTIES_ROLE);

            if (names == null || names.length != 1) {
                throw new RuntimeException(
                        "Expected one Document Properties UI class name");
            }

            if (factory.getUI(DOCUMENT_PROPERTIES_ROLE, names[0]) == null) {
                throw new RuntimeException(
                        "Unable to create Document Properties UI");
            }
        }

    }
}
