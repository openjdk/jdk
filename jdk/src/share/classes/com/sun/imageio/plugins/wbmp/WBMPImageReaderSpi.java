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

package com.sun.imageio.plugins.wbmp;

import java.util.Locale;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ServiceRegistry;
import java.io.IOException;
import javax.imageio.ImageReader;
import javax.imageio.IIOException;
import com.sun.imageio.plugins.common.ReaderUtil;

public class WBMPImageReaderSpi extends ImageReaderSpi {

    private static final int MAX_WBMP_WIDTH = 1024;
    private static final int MAX_WBMP_HEIGHT = 768;

    private static String [] writerSpiNames =
        {"com.sun.imageio.plugins.wbmp.WBMPImageWriterSpi"};
    private static String[] formatNames = {"wbmp", "WBMP"};
    private static String[] entensions = {"wbmp"};
    private static String[] mimeType = {"image/vnd.wap.wbmp"};

    private boolean registered = false;

    public WBMPImageReaderSpi() {
        super("Sun Microsystems, Inc.",
              "1.0",
              formatNames,
              entensions,
              mimeType,
              "com.sun.imageio.plugins.wbmp.WBMPImageReader",
              new Class[] { ImageInputStream.class },
              writerSpiNames,
              true,
              null, null, null, null,
              true,
              WBMPMetadata.nativeMetadataFormatName,
              "com.sun.imageio.plugins.wbmp.WBMPMetadataFormat",
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
        return "Standard WBMP Image Reader";
    }

    public boolean canDecodeInput(Object source) throws IOException {
        if (!(source instanceof ImageInputStream)) {
            return false;
        }

        ImageInputStream stream = (ImageInputStream)source;

        stream.mark();
        int type = stream.readByte();   // TypeField
        int fixHeaderField = stream.readByte();
        // check WBMP "header"
        if (type != 0 || fixHeaderField != 0) {
            // while WBMP reader does not support ext WBMP headers
            stream.reset();
            return false;
        }

        int width = ReaderUtil.readMultiByteInteger(stream);
        int height = ReaderUtil.readMultiByteInteger(stream);
        // check image dimension
        if (width <= 0 || height <= 0) {
            stream.reset();
            return false;
        }

        long dataLength = stream.length();
        if (dataLength == -1) {
            // We can't verify that amount of data in the stream
            // corresponds to image dimension because we do not know
            // the length of the data stream.
            // Assuming that wbmp image are used for mobile devices,
            // let's introduce an upper limit for image dimension.
            // In case if exact amount of raster data is unknown,
            // let's reject images with dimension above the limit.
            stream.reset();
            return (width < MAX_WBMP_WIDTH) && (height < MAX_WBMP_HEIGHT);
        }

        dataLength -= stream.getStreamPosition();
        stream.reset();

        long scanSize = (width / 8) + ((width % 8) == 0 ? 0 : 1);

        return (dataLength == scanSize * height);
    }

    public ImageReader createReaderInstance(Object extension)
        throws IIOException {
        return new WBMPImageReader(this);
    }
}
