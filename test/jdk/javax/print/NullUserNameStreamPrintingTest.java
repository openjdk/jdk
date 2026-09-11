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
 * @bug 8385100
 * @summary Should not get NPE if atttribute set is null.
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.Graphics;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.StreamPrintService;
import javax.print.StreamPrintServiceFactory;

public class NullUserNameStreamPrintingTest implements Printable {

    public static void main(String[] args) throws PrintException {

        DocFlavor flavor = DocFlavor.SERVICE_FORMATTED.PRINTABLE;
        String mime = "application/postscript";
        StreamPrintServiceFactory[] factories =
            StreamPrintServiceFactory.lookupStreamPrintServiceFactories(flavor, mime);
        if (factories.length == 0) {
            throw new RuntimeException("Unable to find PostScript print service factory");
        }
        StreamPrintServiceFactory factory = factories[0];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StreamPrintService service = factory.getPrintService(output);

        DocPrintJob job = service.createPrintJob();

        Doc doc = new SimpleDoc(new NullUserNameStreamPrintingTest(), flavor, null);
        System.setProperty("user.name", "");
        job.print(doc, null);
    }

    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex)
                     throws PrinterException {
        if (pageIndex > 0) {
            return NO_SUCH_PAGE;
        }
        return PAGE_EXISTS;
     }
}
