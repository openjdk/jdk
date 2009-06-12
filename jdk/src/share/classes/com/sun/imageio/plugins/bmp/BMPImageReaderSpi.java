/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.imageio.plugins.bmp;

import java.util.Locale;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ServiceRegistry;
import java.io.IOException;
import javax.imageio.ImageReader;
import javax.imageio.IIOException;

public class BMPImageReaderSpi extends ImageReaderSpi {

    private static String [] writerSpiNames =
        {"com.sun.imageio.plugins.bmp.BMPImageWriterSpi"};
    private static String[] formatNames = {"bmp", "BMP"};
    private static String[] entensions = {"bmp"};
    private static String[] mimeType = {"image/bmp"};

    private boolean registered = false;

    public BMPImageReaderSpi() {
        super("Sun Microsystems, Inc.",
              "1.0",
              formatNames,
              entensions,
              mimeType,
              "com.sun.imageio.plugins.bmp.BMPImageReader",
              new Class[] { ImageInputStream.class },
              writerSpiNames,
              false,
              null, null, null, null,
              true,
              BMPMetadata.nativeMetadataFormatName,
              "com.sun.imageio.plugins.bmp.BMPMetadataFormat",
              null, null);
    }

    public void onRegistration(ServiceRegistry registry,
                               Class<?> category) {
        if (registered) {
            return;
        }
        registered = true;
    }

    public String getDescription(Locale locale) {
        return "Standard BMP Image Reader";
    }

    public boolean canDecodeInput(Object source) throws IOException {
        if (!(source instanceof ImageInputStream)) {
            return false;
        }

        ImageInputStream stream = (ImageInputStream)source;
        byte[] b = new byte[2];
        stream.mark();
        stream.readFully(b);
        stream.reset();

        return (b[0] == 0x42) && (b[1] == 0x4d);
    }

    public ImageReader createReaderInstance(Object extension)
        throws IIOException {
        return new BMPImageReader(this);
    }
}
