/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
        Map<String, Long> nameMap = new HashMap<String, Long>(predefinedClipboardNames.length, 1.0f);
        Map<Long, String> formatMap = new HashMap<Long, String>(predefinedClipboardNames.length, 1.0f);
        for (int i = 1; i < predefinedClipboardNames.length; i++) {
            nameMap.put(predefinedClipboardNames[i], new Long(i));
            formatMap.put(new Long(i), predefinedClipboardNames[i]);
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

    public static final Long L_CF_TIFF = predefinedClipboardNameMap.get(predefinedClipboardNames[CF_TIFF]);

    // Image file formats with java.awt.Image representation:
    private static final Long[] imageFormats = new Long[] {
        L_CF_TIFF
    };


    private CDataTransferer() {}

    private static CDataTransferer fTransferer;

    public static synchronized CDataTransferer getInstanceImpl() {
        if (fTransferer == null) {
            fTransferer = new CDataTransferer();
        }

        return fTransferer;
    }

    public String getDefaultUnicodeEncoding() {
        return "utf-16le";
    }

    public boolean isLocaleDependentTextFormat(long format) {
        return format == CF_STRING;
    }

    public boolean isFileFormat(long format) {
        return format == CF_FILE;
    }

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

    protected Long[] getImageFormatsAsLongArray() {
        return imageFormats;
    }

    public byte[] translateTransferable(Transferable contents, DataFlavor flavor, long format) throws IOException
        {
            byte[] bytes = super.translateTransferable(contents, flavor, format);

            // 9-12-02 VL: we may need to do something like Windows here.
            //if (format == CF_HTML) {
            //    bytes = HTMLSupport.convertToHTMLFormat(bytes);
            //}

            return bytes;
        }

    protected Object translateBytesOrStream(InputStream stream, byte[] bytes, DataFlavor flavor, long format,
                                            Transferable transferable) throws IOException
        {
            // 5-28-03 VL: [Radar 3266030]
            // We need to do like Windows does here.
            if (format == CF_HTML && flavor.isFlavorTextType()) {
                if (stream == null) {
                    stream = new ByteArrayInputStream(bytes);
                    bytes = null;
                }

                stream = new HTMLDecodingInputStream(stream);
            }

            if (format == CF_URL && URL.class.equals(flavor.getRepresentationClass()))
            {
                if (bytes == null) {
                    bytes = inputStreamToByteArray(stream);
                    stream = null;
                }

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

    protected String getNativeForFormat(long format) {
        String returnValue = null;

        // The most common case - just index the array of predefined names:
        if (format >= 0 && format < predefinedClipboardNames.length) {
            returnValue = predefinedClipboardNames[(int) format];
        } else {
            Long formatObj = new Long(format);
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

    public ToolkitThreadBlockedHandler getToolkitThreadBlockedHandler() {
        return handler;
    }

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
    protected String[] dragQueryFile(final byte[] bytes) {
        if (bytes == null) return null;
        if (new String(bytes).startsWith("Unsupported type")) return null;
        return nativeDragQueryFile(bytes);
    }

    private native byte[] imageDataToPlatformImageBytes(int[] rData, int nW, int nH);

    /**
     * Translates a byte array which contains
     * platform-specific image data in the given format into an Image.
     */
    protected Image platformImageBytesToImage(byte[] bytes, long format)
        throws IOException
    {
        return getImageForByteStream(bytes);
    }

    private native Image getImageForByteStream(byte[] bytes);

    @Override
    protected ByteArrayOutputStream convertFileListToBytes(ArrayList<String> fileList) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (int i = 0; i < fileList.size(); i++)
        {
            byte[] bytes = fileList.get(i).getBytes();
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


// ---- Code borrowed from WDataTransferer: ----
// This will come handy for supporting HTML data.

final class HTMLSupport {
    public static final String ENCODING = "UTF-8";

    public static final String VERSION = "Version:";
    public static final String START_HTML = "StartHTML:";
    public static final String END_HTML = "EndHTML:";
    public static final String START_FRAGMENT = "StartFragment:";
    public static final String END_FRAGMENT = "EndFragment:";
    public static final String START_FRAGMENT_CMT = "<!--StartFragment-->";
    public static final String END_FRAGMENT_CMT = "<!--EndFragment-->";
    public static final String EOLN = "\r\n";

    private static final String VERSION_NUM = "0.9";
    private static final String HTML_START_END = "-1";

    private static final int PADDED_WIDTH = 10;

    private static final int HEADER_LEN =
        VERSION.length() + VERSION_NUM.length() + EOLN.length() +
        START_HTML.length() + HTML_START_END.length() + EOLN.length() +
        END_HTML.length() + HTML_START_END.length() + EOLN.length() +
        START_FRAGMENT.length() + PADDED_WIDTH + EOLN.length() +
        END_FRAGMENT.length() + PADDED_WIDTH + EOLN.length() +
        START_FRAGMENT_CMT.length() + EOLN.length();
    private static final String HEADER_LEN_STR =
        toPaddedString(HEADER_LEN, PADDED_WIDTH);

    private static final String TRAILER = END_FRAGMENT_CMT + EOLN + '\0';

    private static String toPaddedString(int n, int width) {
        String string = "" + n;
        int len = string.length();
        if (n >= 0 && len < width) {
            char[] array = new char[width - len];
            Arrays.fill(array, '0');
            StringBuffer buffer = new StringBuffer();
            buffer.append(array);
            buffer.append(string);
            string = buffer.toString();
        }
        return string;
    }

    public static byte[] convertToHTMLFormat(byte[] bytes) {
        StringBuffer header = new StringBuffer(HEADER_LEN);
        header.append(VERSION);
        header.append(VERSION_NUM);
        header.append(EOLN);
        header.append(START_HTML);
        header.append(HTML_START_END);
        header.append(EOLN);
        header.append(END_HTML);
        header.append(HTML_START_END);
        header.append(EOLN);
        header.append(START_FRAGMENT);
        header.append(HEADER_LEN_STR);
        header.append(EOLN);
        header.append(END_FRAGMENT);
        // Strip terminating NUL byte from array
        header.append(toPaddedString(HEADER_LEN + bytes.length - 1,
                                     PADDED_WIDTH));
        header.append(EOLN);
        header.append(START_FRAGMENT_CMT);
        header.append(EOLN);

        byte[] headerBytes = null, trailerBytes = null;

        try {
            headerBytes = new String(header).getBytes(ENCODING);
            trailerBytes = TRAILER.getBytes(ENCODING);
        } catch (UnsupportedEncodingException cannotHappen) {
        }

        byte[] retval = new byte[headerBytes.length + bytes.length - 1 +
            trailerBytes.length];

        System.arraycopy(headerBytes, 0, retval, 0, headerBytes.length);
        System.arraycopy(bytes, 0, retval, headerBytes.length,
                         bytes.length - 1);
        System.arraycopy(trailerBytes, 0, retval,
                         headerBytes.length + bytes.length - 1,
                         trailerBytes.length);

        return retval;
    }
}

/**
* This stream takes an InputStream which provides data in CF_HTML format,
 * strips off the description and context to extract the original HTML data.
 */
class HTMLDecodingInputStream extends InputStream {

    private final BufferedInputStream bufferedStream;
    private boolean descriptionParsed = false;
    private boolean closed = false;
    private int index;
    private int end;

    // InputStreamReader uses an 8K buffer. The size is not customizable.
    public static final int BYTE_BUFFER_LEN = 8192;

    // CharToByteUTF8.getMaxBytesPerChar returns 3, so we should not buffer
    // more chars than 3 times the number of bytes we can buffer.
    public static final int CHAR_BUFFER_LEN = BYTE_BUFFER_LEN / 3;

    private static final String FAILURE_MSG =
        "Unable to parse HTML description: ";
    private static final String INVALID_MSG = " invalid";

    public HTMLDecodingInputStream(InputStream bytestream) throws IOException {
        bufferedStream = new BufferedInputStream(bytestream, BYTE_BUFFER_LEN);
    }

    private void parseDescription() throws IOException {
        bufferedStream.mark(BYTE_BUFFER_LEN);

        BufferedReader bufferedReader = new BufferedReader
            (new InputStreamReader(bufferedStream, HTMLSupport.ENCODING),
             CHAR_BUFFER_LEN);
        String version = bufferedReader.readLine().trim();
        if (version == null || !version.startsWith(HTMLSupport.VERSION)) {
            // Not MS-compliant HTML text. Return raw text from read().
            index = 0;
            end = -1;
            bufferedStream.reset();
            return;
        }

        String input;
        boolean startHTML, endHTML, startFragment, endFragment;
        startHTML = endHTML = startFragment = endFragment = false;

        try {
            do {
                input = bufferedReader.readLine().trim();
                if (input == null) {
                    close();
                    throw new IOException(FAILURE_MSG);
                } else if (input.startsWith(HTMLSupport.START_HTML)) {
                    int val = Integer.parseInt
                    (input.substring(HTMLSupport.START_HTML.length(),
                                     input.length()).trim());
                    if (val >= 0) {
                        index = val;
                        startHTML = true;
                    } else if (val != -1) {
                        close();
                        throw new IOException(FAILURE_MSG +
                                              HTMLSupport.START_HTML +
                                              INVALID_MSG);
                    }
                } else if (input.startsWith(HTMLSupport.END_HTML)) {
                    int val = Integer.parseInt
                    (input.substring(HTMLSupport.END_HTML.length(),
                                     input.length()).trim());
                    if (val >= 0) {
                        end = val;
                        endHTML = true;
                    } else if (val != -1) {
                        close();
                        throw new IOException(FAILURE_MSG +
                                              HTMLSupport.END_HTML +
                                              INVALID_MSG);
                    }
                } else if (!startHTML && !endHTML &&
                           input.startsWith(HTMLSupport.START_FRAGMENT)) {
                    index = Integer.parseInt
                    (input.substring(HTMLSupport.START_FRAGMENT.length(),
                                     input.length()).trim());
                    if (index < 0) {
                        close();
                        throw new IOException(FAILURE_MSG +
                                              HTMLSupport.START_FRAGMENT +
                                              INVALID_MSG);
                    }
                    startFragment = true;
                } else if (!startHTML && !endHTML &&
                           input.startsWith(HTMLSupport.END_FRAGMENT)) {
                    end = Integer.parseInt
                    (input.substring(HTMLSupport.END_FRAGMENT.length(),
                                     input.length()).trim());
                    if (end < 0) {
                        close();
                        throw new IOException(FAILURE_MSG +
                                              HTMLSupport.END_FRAGMENT +
                                              INVALID_MSG);
                    }
                    endFragment = true;
                }
            } while (!((startHTML && endHTML) ||
                       (startFragment && endFragment)));
        } catch (NumberFormatException e) {
            close();
            throw new IOException(FAILURE_MSG + e);
        }

        bufferedStream.reset();

        for (int i = 0; i < index; i++) {
            if (bufferedStream.read() == -1) {
                close();
                throw new IOException(FAILURE_MSG +
                                      "Byte stream ends in description.");
            }
        }
    }

    public int read() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }

        if (!descriptionParsed) {
            parseDescription(); // initializes 'index' and 'end'
            descriptionParsed = true;
        }

        if (end != -1 && index >= end) {
            return -1;
        }

        int retval = bufferedStream.read();
        if (retval == -1) {
            index = end = 0; // so future read() calls will fail quickly
            return -1;
        }

        index++;
    //    System.out.print((char)retval);
        return retval;
    }

    public void close() throws IOException {
        if (!closed) {
            closed = true;
            bufferedStream.close();
        }
    }
}
