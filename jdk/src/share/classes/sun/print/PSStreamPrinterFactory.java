/*
 * Copyright 2000-2001 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.print;

import java.io.OutputStream;

import javax.print.DocFlavor;
import javax.print.StreamPrintService;
import javax.print.StreamPrintServiceFactory;

public class PSStreamPrinterFactory extends StreamPrintServiceFactory {

    static final String psMimeType = "application/postscript";

    static final DocFlavor[] supportedDocFlavors = {
         DocFlavor.SERVICE_FORMATTED.PAGEABLE,
         DocFlavor.SERVICE_FORMATTED.PRINTABLE,
         DocFlavor.BYTE_ARRAY.GIF,
         DocFlavor.INPUT_STREAM.GIF,
         DocFlavor.URL.GIF,
         DocFlavor.BYTE_ARRAY.JPEG,
         DocFlavor.INPUT_STREAM.JPEG,
         DocFlavor.URL.JPEG,
         DocFlavor.BYTE_ARRAY.PNG,
         DocFlavor.INPUT_STREAM.PNG,
         DocFlavor.URL.PNG,
    };

    public  String getOutputFormat() {
        return psMimeType;
    }

    public DocFlavor[] getSupportedDocFlavors() {
        return getFlavors();
    }

    static DocFlavor[] getFlavors() {
        DocFlavor[] flavors = new DocFlavor[supportedDocFlavors.length];
        System.arraycopy(supportedDocFlavors, 0, flavors, 0, flavors.length);
        return flavors;
    }

    public StreamPrintService getPrintService(OutputStream out) {
        return new PSStreamPrintService(out);
    }

}
