/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.lwawt.macosx;

import java.awt.*;
import java.awt.image.*;
import sun.awt.image.ImageRepresentation;

import java.io.*;
import java.net.URL;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.*;

import java.awt.datatransfer.*;
import sun.awt.datatransfer.*;

public class CDataTransferer extends DataTransferer {
    private static final Map<String, Long> predefinedClipboardNameMap;
    private static final Map<Long, String> predefinedClipboardFormatMap;

    // See SystemFlavorMap, or the flavormap.properties file:
    // We should define a few more types in flavormap.properties, it's rather slim now.
    private static final String[] predefinedClipboardNames = {
        "",
        "STRING",
        "FILE_NAME",
        "TIFF",
        "RICH_TEXT",
        "HTML",
        "PDF",
        "URL"
    };

    static {
        Map<String, Long> nameMap = new HashMap<>(predefinedClipboardNames.length, 1.0f);
        Map<Long, String> formatMap = new HashMap<>(predefinedClipboardNames.length, 1.0f);
        for (int i = 1; i < predefinedClipboardNames.length; i++) {
            nameMap.put(predefinedClipboardNames[i], (long) i);
            formatMap.put((long) i, predefinedClipboardNames[i]);
        }
        predefinedClipboardNameMap = Collections.synchronizedMap(nameMap);
        predefinedClipboardFormatMap = Collections.synchronizedMap(formatMap);
    }

    public static final int CF_UNSUPPORTED = 0;
    public static final int CF_STRING      = 1;
    public static final int CF_FILE        = 2;
    public static final int CF_TIFF        = 3;
    public static final int CF_RICH_TEXT   = 4;
    public static final int CF_HTML        = 5;
    public static final int CF_PDF         = 6;
    public static final int CF_URL         = 7;
    public static final int CF_PNG         = 10;
    public static final int CF_JPEG        = 11;

    private CDataTransferer() {}

    private static CDataTransferer fTransferer;

    static synchronized CDataTransferer getInstanceImpl() {
        if (fTransferer == null) {
            fTransferer = new CDataTransferer();
        }

        return fTransferer;
    }

    @Override
    public String getDefaultUnicodeEncoding() {
        return "utf-16le";
    }

    @Override
    public boolean isLocaleDependentTextFormat(long format) {
        return format == CF_STRING;
    }

    @Override
    public boolean isFileFormat(long format) {
        return format == CF_FILE;
    }

    @Override
    public boolean isImageFormat(long format) {
        int ifmt = (int)format;
        switch(ifmt) {
            case CF_TIFF:
            case CF_PDF:
            case CF_PNG:
            case CF_JPEG:
                return true;
            default:
                return false;
        }
    }

    @Override
    public Object translateBytes(byte[] bytes, DataFlavor flavor,
                                    long format, Transferable transferable) throws IOException {

            if (format == CF_URL && URL.class.equals(flavor.getRepresentationClass()))
            {
                String charset = getDefaultTextCharset();
                if (transferable != null && transferable.isDataFlavorSupported(javaTextEncodingFlavor)) {
                    try {
                        charset = new String((byte[])transferable.getTransferData(javaTextEncodingFlavor), "UTF-8");
                    } catch (UnsupportedFlavorException cannotHappen) {
                    }
                }

                return new URL(new String(bytes, charset));
            }

            if (format == CF_STRING) {
                bytes = Normalizer.normalize(new String(bytes, "UTF8"), Form.NFC).getBytes("UTF8");
            }

            return super.translateBytes(bytes, flavor, format, transferable);
    }

    @Override
    synchronized protected Long getFormatForNativeAsLong(String str) {
        Long format = predefinedClipboardNameMap.get(str);

        if (format == null) {
            if (java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance()) {
                // Do not try to access native system for the unknown format
                return -1L;
            }
            format = registerFormatWithPasteboard(str);
            predefinedClipboardNameMap.put(str, format);
            predefinedClipboardFormatMap.put(format, str);
        }

        return format;
    }

    /*
     * Adds type to native mapping NSDictionary.
     */
    private native long registerFormatWithPasteboard(String type);

    // Get registered native format string for an index, return null if unknown:
    private native String formatForIndex(long index);

    @Override
    protected String getNativeForFormat(long format) {
        String returnValue = null;

        // The most common case - just index the array of predefined names:
        if (format >= 0 && format < predefinedClipboardNames.length) {
            returnValue = predefinedClipboardNames[(int) format];
        } else {
            Long formatObj = format;
            returnValue = predefinedClipboardFormatMap.get(formatObj);

            // predefinedClipboardFormatMap may not know this format:
            if (returnValue == null) {
                returnValue = formatForIndex(format);

                // Native clipboard may not know this format either:
                if (returnValue != null) {
                    predefinedClipboardNameMap.put(returnValue, formatObj);
                    predefinedClipboardFormatMap.put(formatObj, returnValue);
                }
            }
        }

        if (returnValue == null) {
            returnValue = predefinedClipboardNames[CF_UNSUPPORTED];
        }

        return returnValue;
    }

    private final ToolkitThreadBlockedHandler handler = new CToolkitThreadBlockedHandler();

    @Override
    public ToolkitThreadBlockedHandler getToolkitThreadBlockedHandler() {
        return handler;
    }

    private native byte[] imageDataToPlatformImageBytes(int[] rData, int nW, int nH);
    @Override
    protected byte[] imageToPlatformBytes(Image image, long format) {
        int w = image.getWidth(null);
        int h = image.getHeight(null);
        BufferedImage bimage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics g = bimage.getGraphics();
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();
        Raster raster = bimage.getRaster();
        DataBuffer buffer = raster.getDataBuffer();
        return imageDataToPlatformImageBytes(((DataBufferInt)buffer).getData(),
                                             raster.getWidth(),
                                             raster.getHeight());
    }

    private static native String[] nativeDragQueryFile(final byte[] bytes);
    @Override
    protected String[] dragQueryFile(final byte[] bytes) {
        if (bytes == null) return null;
        if (new String(bytes).startsWith("Unsupported type")) return null;
        return nativeDragQueryFile(bytes);
    }

    private native Image getImageForByteStream(byte[] bytes);
    /**
     * Translates a byte array which contains
     * platform-specific image data in the given format into an Image.
     */
    @Override
    protected Image platformImageBytesToImage(byte[] bytes, long format) throws IOException {
        return getImageForByteStream(bytes);
    }

    @Override
    protected ByteArrayOutputStream convertFileListToBytes(ArrayList<String> fileList) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (String file : fileList) {
            byte[] bytes = file.getBytes();
            bos.write(bytes, 0, bytes.length);
            bos.write(0);
        }
        return bos;
    }

    @Override
    protected boolean isURIListFormat(long format) {
        String nat = getNativeForFormat(format);
        if (nat == null) {
            return false;
        }
        try {
            DataFlavor df = new DataFlavor(nat);
            if (df.getPrimaryType().equals("text") && df.getSubType().equals("uri-list")) {
                return true;
            }
        } catch (Exception e) {
            // Not a MIME format.
        }
        return false;
    }
}


