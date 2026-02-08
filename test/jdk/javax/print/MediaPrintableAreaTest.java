/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, BELLSOFT. All rights reserved.
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


import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintJobAttributeSet;
import javax.print.attribute.PrintJobAttributeSet;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaSizeName;

/*
 * @test
 * @bug 8372952
 * @key printer
 * @summary MediaPrintableArea size should be less or equal to media size
 * @requires (os.family == "linux" | os.family == "mac")
 */

public class MediaPrintableAreaTest {

    public static void main(String[] args) {
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);

        for (PrintService ps : printServices) {
            testPrintService(ps);
        }
    }

    private static void testPrintService(PrintService printServices) {
        Media[] medias = (Media[]) printServices.getSupportedAttributeValues(Media.class, null, null);
        PrintJobAttributeSet attrs = new HashPrintJobAttributeSet();

        for (Media m : medias) {
            if (!(m instanceof MediaSizeName msn)) {
                continue;
            }
            MediaSize mediaSize = MediaSize.getMediaSizeForName(msn);
            if (mediaSize == null) {
                continue;
            }
            attrs.add(msn);
            MediaPrintableArea[] printableAreas = (MediaPrintableArea[]) printServices
                    .getSupportedAttributeValues(MediaPrintableArea.class, null, attrs);
            if (printableAreas == null || printableAreas.length == 0) {
                continue;
            }
            final double acceptableDiff = -4.0;
            final int units = MediaPrintableArea.MM;
            for (MediaPrintableArea mpa : printableAreas) {
                double mediaWidth = mediaSize.getX(units);
                double mediaHeight = mediaSize.getY(units);
                double printableWidth = mpa.getX(units) + mpa.getWidth(units);
                double printableHeight = mpa.getY(units) + mpa.getHeight(units);
                if ((mediaWidth - printableWidth) < acceptableDiff ||
                        (mediaHeight - printableHeight) < acceptableDiff) {
                    String msg = String.format("Media %s less than media printable area. %n" +
                                    "Media size width: %f, height: %f.%nPrintable area: width: %f, height %f",
                            msn.getName(), mediaWidth, mediaHeight, printableWidth, printableHeight);
                    throw new RuntimeException(msg);
                }
            }
        }
    }

}
