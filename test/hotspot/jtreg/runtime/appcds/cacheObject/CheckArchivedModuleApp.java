/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import java.io.File;
import java.lang.module.ModuleDescriptor;
import java.util.Set;
import sun.hotspot.WhiteBox;

//
// Test archived system module graph when open archive heap objects are mapped:
//
public class CheckArchivedModuleApp {
    static WhiteBox wb;
    public static void main(String args[]) throws Exception {
        wb = WhiteBox.getWhiteBox();

        if (!wb.areOpenArchiveHeapObjectsMapped()) {
            System.out.println("Archived open_archive_heap objects are not mapped.");
            System.out.println("This may happen during normal operation. Test Skipped.");
            return;
        }

        boolean expectArchived = "yes".equals(args[0]);
        checkModuleDescriptors(expectArchived);
    }

    private static void checkModuleDescriptors(boolean expectArchived) {
        Set<Module> modules = ModuleLayer.boot().modules();
        for (Module m : modules) {
            ModuleDescriptor md = m.getDescriptor();
            String name = md.name();
            if (expectArchived) {
                if (wb.isShared(md)) {
                    System.out.println(name + " is archived. Expected.");
                } else {
                    throw new RuntimeException(
                        "FAILED. " + name + " is not archived. Expect archived.");
                }
            } else {
                if (!wb.isShared(md)) {
                    System.out.println(name + " is not archived. Expected.");
                } else {
                    throw new RuntimeException(
                        "FAILED. " + name + " is archived. Expect not archived.");
                }
            }
        }
    }
}
