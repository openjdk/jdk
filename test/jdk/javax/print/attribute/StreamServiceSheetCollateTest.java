/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4884570
 * @summary SheetCollate support reporting should be consistent
*/

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import javax.print.DocFlavor;
import javax.print.StreamPrintService;
import javax.print.StreamPrintServiceFactory;
import javax.print.attribute.Attribute;
import javax.print.attribute.standard.SheetCollate;

public class StreamServiceSheetCollateTest {

    public static void main(String args[]) {

        StreamPrintServiceFactory[] fact =
          StreamPrintServiceFactory.lookupStreamPrintServiceFactories(
                null, null);

        if (fact.length == 0) {
            return;
        }
        OutputStream out = new ByteArrayOutputStream();
        StreamPrintService sps = fact[0].getPrintService(out);
        if (!sps.isAttributeCategorySupported(SheetCollate.class)) {
            return;
        }
        boolean allSupported = true;
        DocFlavor[] dfs = sps.getSupportedDocFlavors();
        for (DocFlavor f : dfs) {
            Attribute[] attrs = (Attribute[])
               sps.getSupportedAttributeValues(SheetCollate.class, f, null);
            for (Attribute a : attrs) {
                if (!sps.isAttributeValueSupported(a, f, null)) {
                    allSupported = false;
                    System.out.println("Unsupported : " + f + " " + a);
                }
            }
        }
        if (!allSupported) {
            throw new RuntimeException("Inconsistent support reported");
        }
    }
}
