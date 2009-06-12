/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.imageio.plugins.gif;

import java.awt.image.ColorModel;
import java.awt.image.SampleModel;
import java.util.Locale;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;
import com.sun.imageio.plugins.common.PaletteBuilder;

public class GIFImageWriterSpi extends ImageWriterSpi {

    private static final String vendorName = "Sun Microsystems, Inc.";

    private static final String version = "1.0";

    private static final String[] names = { "gif", "GIF" };

    private static final String[] suffixes = { "gif" };

    private static final String[] MIMETypes = { "image/gif" };

    private static final String writerClassName =
    "com.sun.imageio.plugins.gif.GIFImageWriter";

    private static final String[] readerSpiNames = {
        "com.sun.imageio.plugins.gif.GIFImageReaderSpi"
    };

    public GIFImageWriterSpi() {
        super(vendorName,
              version,
              names,
              suffixes,
              MIMETypes,
              writerClassName,
              new Class[] { ImageOutputStream.class },
              readerSpiNames,
              true,
              GIFWritableStreamMetadata.NATIVE_FORMAT_NAME,
              "com.sun.imageio.plugins.gif.GIFStreamMetadataFormat",
              null, null,
              true,
              GIFWritableImageMetadata.NATIVE_FORMAT_NAME,
              "com.sun.imageio.plugins.gif.GIFImageMetadataFormat",
              null, null
              );
    }

    public boolean canEncodeImage(ImageTypeSpecifier type) {
        if (type == null) {
            throw new IllegalArgumentException("type == null!");
        }

        SampleModel sm = type.getSampleModel();
        ColorModel cm = type.getColorModel();

        boolean canEncode = sm.getNumBands() == 1 &&
            sm.getSampleSize(0) <= 8 &&
            sm.getWidth() <= 65535 &&
            sm.getHeight() <= 65535 &&
            (cm == null || cm.getComponentSize()[0] <= 8);

        if (canEncode) {
            return true;
        } else {
            return PaletteBuilder.canCreatePalette(type);
        }
    }

    public String getDescription(Locale locale) {
        return "Standard GIF image writer";
    }

    public ImageWriter createWriterInstance(Object extension) {
        return new GIFImageWriter(this);
    }
}
