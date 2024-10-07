/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, BELLSOFT. All rights reserved.
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
 * @bug 7001133
 * @summary OutOfMemoryError by CustomMediaSizeName implementation
 * @run main CustomMediaSizeNameOOMETest
 * @run main/timeout=300/othervm -Xmx8m CustomMediaSizeNameOOMETest
*/

import javax.print.PrintService;
import javax.print.PrintServiceLookup;

public class CustomMediaSizeNameOOMETest {

    private static final int MILLIS = 3000;

    public static void main(String[] args) {

        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        if (services == null || services.length == 0) {
            return;
        }

        for (PrintService service : services) {
            service.getUnsupportedAttributes(null, null);
        }

        long time = System.currentTimeMillis() + MILLIS;

        do {
            for (int i = 0; i < 2000; i++) {
                for (PrintService service : services) {
                    service.getAttributes();
                }
            }
        } while (time > System.currentTimeMillis());
    }
}
