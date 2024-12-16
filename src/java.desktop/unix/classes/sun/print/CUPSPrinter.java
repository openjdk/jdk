/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.print;

import java.net.URL;
import java.net.HttpURLConnection;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import sun.print.IPPPrintService;
import sun.print.CustomMediaSizeName;
import sun.print.CustomMediaTray;
import sun.print.CustomOutputBin;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaTray;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.OutputBin;
import javax.print.attribute.standard.PrinterResolution;
import javax.print.attribute.Size2DSyntax;
import javax.print.attribute.Attribute;
import javax.print.attribute.EnumSyntax;
import javax.print.attribute.standard.PrinterName;


public class CUPSPrinter  {
    private static final String debugPrefix = "CUPSPrinter>> ";
    private static final double PRINTER_DPI = 72.0;
    private boolean initialized;
    private static native String getCupsServer();
    private static native int getCupsPort();
    private static native String getCupsDefaultPrinter();
    private static native String[] getCupsDefaultPrinters();
    private static native boolean canConnect(String server, int port);
    private static native boolean initIDs();
    // These functions need to be synchronized as
    // CUPS does not support multi-threading.
    private static synchronized native String[] getMedia(String printer);
    private static synchronized native float[] getPageSizes(String printer);
    private static synchronized native String[] getOutputBins(String printer);
    private static synchronized native void
        getResolutions(String printer, ArrayList<Integer> resolutionList);
    //public static boolean useIPPMedia = false; will be used later

    private MediaPrintableArea[] cupsMediaPrintables;
    private MediaSizeName[] cupsMediaSNames;
    private CustomMediaSizeName[] cupsCustomMediaSNames;
    private MediaTray[] cupsMediaTrays;
    private OutputBin[] cupsOutputBins;

    public  int nPageSizes = 0;
    public  int nTrays = 0;
    private  String[] media;
    private  String[] outputBins;
    private  float[] pageSizes;
    int[]   resolutionsArray;
    private String printer;

    private static boolean libFound;
    private static String cupsServer = null;
    private static String domainSocketPathname = null;
    private static int cupsPort = 0;

    static {
        initStatic();
    }

    @SuppressWarnings("restricted")
    private static void initStatic() {
        // load awt library to access native code
        System.loadLibrary("awt");
        libFound = initIDs();
        if (libFound) {
           cupsServer = getCupsServer();
           // Is this a local domain socket pathname?
           if (cupsServer != null && cupsServer.startsWith("/")) {
               if (isSandboxedApp()) {
                   domainSocketPathname = cupsServer;
               }
               cupsServer = "localhost";
           }
           cupsPort = getCupsPort();
        }
    }


    CUPSPrinter (String printerName) {
        if (printerName == null) {
            throw new IllegalArgumentException("null printer name");
        }
        printer = printerName;
        cupsMediaSNames = null;
        cupsMediaPrintables = null;
        cupsMediaTrays = null;
        initialized = false;

        if (!libFound) {
            throw new RuntimeException("cups lib not found");
        } else {
            // get page + tray names
            media =  getMedia(printer);
            if (media == null) {
                // either PPD file is not found or printer is unknown
                throw new RuntimeException("error getting PPD");
            }

            // get sizes
            pageSizes = getPageSizes(printer);
            if (pageSizes != null) {
                nPageSizes = pageSizes.length/6;

                nTrays = media.length/2-nPageSizes;
                assert (nTrays >= 0);
            }
            ArrayList<Integer> resolutionList = new ArrayList<>();
            getResolutions(printer, resolutionList);
            resolutionsArray = new int[resolutionList.size()];
            for (int i=0; i < resolutionList.size(); i++) {
                resolutionsArray[i] = resolutionList.get(i);
            }

            outputBins = getOutputBins(printer);
        }
    }


    /**
     * Returns array of MediaSizeNames derived from PPD.
     */
    MediaSizeName[] getMediaSizeNames() {
        initMedia();
        return cupsMediaSNames;
    }


    /**
     * Returns array of Custom MediaSizeNames derived from PPD.
     */
    CustomMediaSizeName[] getCustomMediaSizeNames() {
        initMedia();
        return cupsCustomMediaSNames;
    }

    public int getDefaultMediaIndex() {
        return ((pageSizes.length >1) ? (int)(pageSizes[pageSizes.length -1]) : 0);
    }

    /**
     * Returns array of MediaPrintableArea derived from PPD.
     */
    MediaPrintableArea[] getMediaPrintableArea() {
        initMedia();
        return cupsMediaPrintables;
    }

    /**
     * Returns array of MediaTrays derived from PPD.
     */
    MediaTray[] getMediaTrays() {
        initMedia();
        return cupsMediaTrays;
    }

    /**
     * Returns array of OutputBins derived from PPD.
     */
    OutputBin[] getOutputBins() {
        initMedia();
        return cupsOutputBins;
    }

    /**
     * return the raw packed array of supported printer resolutions.
     */
    int[] getRawResolutions() {
        return resolutionsArray;
    }

    /**
     * Initialize media by translating PPD info to PrintService attributes.
     */
    private synchronized void initMedia() {
        if (initialized) {
            return;
        } else {
            initialized = true;
        }

        if (pageSizes == null) {
            return;
        }

        cupsMediaPrintables = new MediaPrintableArea[nPageSizes];
        cupsMediaSNames = new MediaSizeName[nPageSizes];
        cupsCustomMediaSNames = new CustomMediaSizeName[nPageSizes];

        CustomMediaSizeName msn;
        MediaPrintableArea mpa;
        float length, width, x, y, w, h;

        // initialize names and printables
        for (int i=0; i<nPageSizes; i++) {
            // media width and length
            width = (float)(pageSizes[i*6]/PRINTER_DPI);
            length = (float)(pageSizes[i*6+1]/PRINTER_DPI);
            // media printable area
            x = (float)(pageSizes[i*6+2]/PRINTER_DPI);
            h = (float)(pageSizes[i*6+3]/PRINTER_DPI);
            w = (float)(pageSizes[i*6+4]/PRINTER_DPI);
            y = (float)(pageSizes[i*6+5]/PRINTER_DPI);

            msn = CustomMediaSizeName.create(media[i*2], media[i*2+1],
                                             width, length);

            // add to list of standard MediaSizeNames
            if ((cupsMediaSNames[i] = msn.getStandardMedia()) == null) {
                // add custom if no matching standard media
                cupsMediaSNames[i] = msn;
            }

            // add to list of custom MediaSizeName
            // for internal use of IPPPrintService
            cupsCustomMediaSNames[i] = msn;

            mpa = null;
            try {
                mpa = new MediaPrintableArea(x, y, w, h,
                                             MediaPrintableArea.INCH);
            } catch (IllegalArgumentException e) {
                if (width > 0 && length > 0) {
                    mpa = new MediaPrintableArea(0, 0, width, length,
                                             MediaPrintableArea.INCH);
                }
            }
            cupsMediaPrintables[i] = mpa;
        }

        // initialize trays
        cupsMediaTrays = new MediaTray[nTrays];

        MediaTray mt;
        for (int i=0; i<nTrays; i++) {
            mt = CustomMediaTray.create(media[(nPageSizes+i)*2],
                                        media[(nPageSizes+i)*2+1]);
            cupsMediaTrays[i] = mt;
        }

        if (outputBins == null) {
            cupsOutputBins = new OutputBin[0];
        } else {
            int nBins = outputBins.length / 2;
            cupsOutputBins = new OutputBin[nBins];
            for (int i = 0; i < nBins; i++) {
                cupsOutputBins[i] = CustomOutputBin.createOutputBin(outputBins[i*2], outputBins[i*2+1]);
            }
        }
    }

    /**
     * Get CUPS default printer using IPP.
     * Returns 2 values - index 0 is printer name, index 1 is the uri.
     */
    static String[] getDefaultPrinter() {
        // Try to get user/lpoptions-defined printer name from CUPS
        // if not user-set, then go for server default destination
        String[] printerInfo = new String[2];
        printerInfo[0] = getCupsDefaultPrinter();

        if (printerInfo[0] != null) {
            printerInfo[1] = null;
            return printerInfo.clone();
        }
        try {
            @SuppressWarnings("deprecation")
            URL url = new URL("http", getServer(), getPort(), "");
            final HttpURLConnection urlConnection =
                IPPPrintService.getIPPConnection(url);

            if (urlConnection != null) {
                OutputStream os = null;
                try {
                    os = urlConnection.getOutputStream();
                } catch (Exception e) {
                   IPPPrintService.debug_println(debugPrefix+e);
                }

                if (os == null) {
                    return null;
                }

                AttributeClass[] attCl = {
                    AttributeClass.ATTRIBUTES_CHARSET,
                    AttributeClass.ATTRIBUTES_NATURAL_LANGUAGE,
                    new AttributeClass("requested-attributes",
                                       AttributeClass.TAG_URI,
                                       "printer-uri")
                };

                if (IPPPrintService.writeIPPRequest(os,
                                        IPPPrintService.OP_CUPS_GET_DEFAULT,
                                        attCl)) {

                    HashMap<String, AttributeClass> defaultMap = null;

                    InputStream is = urlConnection.getInputStream();
                    HashMap<String, AttributeClass>[] responseMap = IPPPrintService.readIPPResponse(
                                         is);
                    is.close();

                    if (responseMap != null && responseMap.length > 0) {
                        defaultMap = responseMap[0];
                    } else {
                       IPPPrintService.debug_println(debugPrefix+
                           " empty response map for GET_DEFAULT.");
                    }

                    if (defaultMap == null) {
                        os.close();
                        urlConnection.disconnect();

                        /* CUPS on OS X, as initially configured, considers the
                         * default printer to be the last one used that's
                         * presently available. So if no default was
                         * reported, exec lpstat -d which has all the Apple
                         * special behaviour for this built in.
                         */
                         if (PrintServiceLookupProvider.isMac()) {
                             printerInfo[0] = PrintServiceLookupProvider.
                                                   getDefaultPrinterNameSysV();
                             printerInfo[1] = null;
                             return printerInfo.clone();
                         } else {
                             return null;
                         }
                    }


                    AttributeClass attribClass = defaultMap.get("printer-name");

                    if (attribClass != null) {
                        printerInfo[0] = attribClass.getStringValue();
                        attribClass = defaultMap.get("printer-uri-supported");
                        IPPPrintService.debug_println(debugPrefix+
                          "printer-uri-supported="+attribClass);
                        if (attribClass != null) {
                            printerInfo[1] = attribClass.getStringValue();
                        } else {
                            printerInfo[1] = null;
                        }
                        os.close();
                        urlConnection.disconnect();
                        return printerInfo.clone();
                    }
                }
                os.close();
                urlConnection.disconnect();
            }
        } catch (Exception e) {
        }
        return null;
    }


    /**
     * Get list of all CUPS printers using IPP.
     */
    static String[] getAllPrinters() {

        if (getDomainSocketPathname() != null) {
            String[] printerNames = getCupsDefaultPrinters();
            if (printerNames != null && printerNames.length > 0) {
                String[] printerURIs = new String[printerNames.length];
                for (int i=0; i< printerNames.length; i++) {
                    printerURIs[i] = String.format("ipp://%s:%d/printers/%s",
                            getServer(), getPort(), printerNames[i]);
                }
                return printerURIs;
            }
            return null;
        }

        try {
            @SuppressWarnings("deprecation")
            URL url = new URL("http", getServer(), getPort(), "");

            final HttpURLConnection urlConnection =
                IPPPrintService.getIPPConnection(url);

            if (urlConnection != null) {
                OutputStream os = null;
                try {
                    os = urlConnection.getOutputStream();
                } catch (Exception e) {
                }

                if (os == null) {
                    return null;
                }

                AttributeClass[] attCl = {
                    AttributeClass.ATTRIBUTES_CHARSET,
                    AttributeClass.ATTRIBUTES_NATURAL_LANGUAGE,
                    new AttributeClass("requested-attributes",
                                       AttributeClass.TAG_KEYWORD,
                                       "printer-uri-supported")
                };

                if (IPPPrintService.writeIPPRequest(os,
                                IPPPrintService.OP_CUPS_GET_PRINTERS, attCl)) {

                    InputStream is = urlConnection.getInputStream();
                    HashMap<String, AttributeClass>[] responseMap =
                        IPPPrintService.readIPPResponse(is);

                    is.close();
                    os.close();
                    urlConnection.disconnect();

                    if (responseMap == null || responseMap.length == 0) {
                        return null;
                    }

                    ArrayList<String> printerNames = new ArrayList<>();
                    for (int i=0; i< responseMap.length; i++) {
                        AttributeClass attribClass =
                            responseMap[i].get("printer-uri-supported");

                        if (attribClass != null) {
                            String nameStr = attribClass.getStringValue();
                            printerNames.add(nameStr);
                        }
                    }
                    return printerNames.toArray(new String[] {});
                } else {
                    os.close();
                    urlConnection.disconnect();
                }
            }

        } catch (Exception e) {
        }
        return null;

    }

    /**
     * Returns CUPS server name.
     */
    public static String getServer() {
        return cupsServer;
    }

    /**
     * Returns CUPS port number.
     */
    public static int getPort() {
        return cupsPort;
    }

    /**
     * Returns CUPS domain socket pathname.
     */
    private static String getDomainSocketPathname() {
        return domainSocketPathname;
    }

    private static boolean isSandboxedApp() {
        if (PrintServiceLookupProvider.isMac()) {
            return (System.getenv("APP_SANDBOX_CONTAINER_ID") != null);
        }
        return false;
    }


    /**
     * Detects if CUPS is running.
     */
    public static boolean isCupsRunning() {
        IPPPrintService.debug_println(debugPrefix+"libFound "+libFound);
        if (libFound) {
            String server = getDomainSocketPathname() != null
                    ? getDomainSocketPathname()
                    : getServer();
            IPPPrintService.debug_println(debugPrefix+"CUPS server "+server+
                                          " port "+getPort()+
                                          (getDomainSocketPathname() != null
                                                  ? " use domain socket pathname"
                                                  : ""));
            return canConnect(server, getPort());
        } else {
            return false;
        }
    }


}
