/*
 * Copyright (c) 2000, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import javax.print.DocFlavor;
import javax.print.MultiDocPrintService;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.Attribute;
import javax.print.attribute.AttributeSet;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.HashPrintServiceAttributeSet;
import javax.print.attribute.PrintRequestAttribute;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.PrintServiceAttribute;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.attribute.standard.PrinterName;

public class PrintServiceLookupProvider extends PrintServiceLookup {

    private String defaultPrinter;
    private PrintService defaultPrintService;
    private String[] printers; /* excludes the default printer */
    private PrintService[] printServices; /* includes the default printer */

    private static final int DEFAULT_REFRESH_TIME = 240;  // 4 minutes
    private static final int MINIMUM_REFRESH_TIME = 120;  // 2 minutes
    private static final boolean pollServices;
    private static final int refreshTime;

    static {
        /* The system property "sun.java2d.print.polling"
         * can be used to force the printing code to poll or not poll
         * for PrintServices.
         */
        String pollStr = java.security.AccessController.doPrivileged(
            new sun.security.action.GetPropertyAction("sun.java2d.print.polling"));
        pollServices = !("false".equalsIgnoreCase(pollStr));

        /* The system property "sun.java2d.print.minRefreshTime"
         * can be used to specify minimum refresh time (in seconds)
         * for polling PrintServices.  The default is 240.
         */
        String refreshTimeStr = java.security.AccessController.doPrivileged(
            new sun.security.action.GetPropertyAction(
                "sun.java2d.print.minRefreshTime"));
        refreshTime = (refreshTimeStr != null)
                      ? getRefreshTime(refreshTimeStr)
                      : DEFAULT_REFRESH_TIME;

        java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<Void>() {
                public Void run() {
                    System.loadLibrary("awt");
                    return null;
                }
            });
    }

    private static int getRefreshTime(final String refreshTimeStr) {
        try {
            int minRefreshTime = Integer.parseInt(refreshTimeStr);
            return (minRefreshTime < MINIMUM_REFRESH_TIME)
                   ? MINIMUM_REFRESH_TIME
                   : minRefreshTime;
        } catch (NumberFormatException e) {
            return DEFAULT_REFRESH_TIME;
        }
    }

    /* The singleton win32 print lookup service.
     * Code that is aware of this field and wants to use it must first
     * see if its null, and if so instantiate it by calling a method such as
     * javax.print.PrintServiceLookup.defaultPrintService() so that the
     * same instance is stored there.
     */
    private static PrintServiceLookupProvider win32PrintLUS;

    /* Think carefully before calling this. Preferably don't call it. */
    public static PrintServiceLookupProvider getWin32PrintLUS() {
        if (win32PrintLUS == null) {
            /* This call is internally synchronized.
             * When it returns an instance of this class will have
             * been instantiated - else there's a JDK internal error.
             */
            PrintServiceLookup.lookupDefaultPrintService();
        }
        return win32PrintLUS;
    }

    public PrintServiceLookupProvider() {

        if (win32PrintLUS == null) {
            win32PrintLUS = this;

            // start the local printer listener thread
            Thread thr = new Thread(null, new PrinterChangeListener(),
                                    "PrinterListener", 0, false);
            thr.setDaemon(true);
            thr.start();

            if (pollServices) {
                // start the remote printer listener thread
                Thread remThr = new Thread(null, new RemotePrinterChangeListener(),
                                           "RemotePrinterListener", 0, false);
                remThr.setDaemon(true);
                remThr.start();
            }
        } /* else condition ought to never happen! */
    }

    /* Want the PrintService which is default print service to have
     * equality of reference with the equivalent in list of print services
     * This isn't required by the API and there's a risk doing this will
     * lead people to assume its guaranteed.
     */
    public synchronized PrintService[] getPrintServices() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPrintJobAccess();
        }
        if (printServices == null) {
            refreshServices();
        }
        return printServices;
    }

    private synchronized void refreshServices() {
        printers = getAllPrinterNames();
        if (printers == null) {
            // In Windows it is safe to assume no default if printers == null so we
            // don't get the default.
            printServices = new PrintService[0];
            return;
        }

        PrintService[] newServices = new PrintService[printers.length];
        PrintService defService = getDefaultPrintService();
        for (int p = 0; p < printers.length; p++) {
            if (defService != null &&
                printers[p].equals(defService.getName())) {
                newServices[p] = defService;
            } else {
                if (printServices == null) {
                    newServices[p] = new Win32PrintService(printers[p]);
                } else {
                    int j;
                    for (j = 0; j < printServices.length; j++) {
                        if ((printServices[j]!= null) &&
                            (printers[p].equals(printServices[j].getName()))) {
                            newServices[p] = printServices[j];
                            printServices[j] = null;
                            break;
                        }
                    }
                    if (j == printServices.length) {
                        newServices[p] = new Win32PrintService(printers[p]);
                    }
                }
            }
        }

        // Look for deleted services and invalidate these
        if (printServices != null) {
            for (int j=0; j < printServices.length; j++) {
                if ((printServices[j] instanceof Win32PrintService) &&
                    (!printServices[j].equals(defaultPrintService))) {
                    ((Win32PrintService)printServices[j]).invalidateService();
                }
            }
        }
        printServices = newServices;
    }


    public synchronized PrintService getPrintServiceByName(String name) {

        if (name == null || name.isEmpty()) {
            return null;
        } else {
            /* getPrintServices() is now very fast. */
            PrintService[] printServices = getPrintServices();
            for (int i=0; i<printServices.length; i++) {
                if (printServices[i].getName().equals(name)) {
                    return printServices[i];
                }
            }
            return null;
        }
    }

    @SuppressWarnings("unchecked") // Cast to Class<PrintServiceAttribute>
    boolean matchingService(PrintService service,
                            PrintServiceAttributeSet serviceSet) {
        if (serviceSet != null) {
            Attribute [] attrs =  serviceSet.toArray();
            Attribute serviceAttr;
            for (int i=0; i<attrs.length; i++) {
                serviceAttr
                    = service.getAttribute((Class<PrintServiceAttribute>)attrs[i].getCategory());
                if (serviceAttr == null || !serviceAttr.equals(attrs[i])) {
                    return false;
                }
            }
        }
        return true;
    }

    public PrintService[] getPrintServices(DocFlavor flavor,
                                           AttributeSet attributes) {

        SecurityManager security = System.getSecurityManager();
        if (security != null) {
          security.checkPrintJobAccess();
        }
        PrintRequestAttributeSet requestSet = null;
        PrintServiceAttributeSet serviceSet = null;

        if (attributes != null && !attributes.isEmpty()) {

            requestSet = new HashPrintRequestAttributeSet();
            serviceSet = new HashPrintServiceAttributeSet();

            Attribute[] attrs = attributes.toArray();
            for (int i=0; i<attrs.length; i++) {
                if (attrs[i] instanceof PrintRequestAttribute) {
                    requestSet.add(attrs[i]);
                } else if (attrs[i] instanceof PrintServiceAttribute) {
                    serviceSet.add(attrs[i]);
                }
            }
        }

        /*
         * Special case: If client is asking for a particular printer
         * (by name) then we can save time by getting just that service
         * to check against the rest of the specified attributes.
         */
        PrintService[] services = null;
        if (serviceSet != null && serviceSet.get(PrinterName.class) != null) {
            PrinterName name = (PrinterName)serviceSet.get(PrinterName.class);
            PrintService service = getPrintServiceByName(name.getValue());
            if (service == null || !matchingService(service, serviceSet)) {
                services = new PrintService[0];
            } else {
                services = new PrintService[1];
                services[0] = service;
            }
        } else {
            services = getPrintServices();
        }

        if (services.length == 0) {
            return services;
        } else {
            ArrayList<PrintService> matchingServices = new ArrayList<>();
            for (int i=0; i<services.length; i++) {
                try {
                    if (services[i].
                        getUnsupportedAttributes(flavor, requestSet) == null) {
                        matchingServices.add(services[i]);
                    }
                } catch (IllegalArgumentException e) {
                }
            }
            services = new PrintService[matchingServices.size()];
            return matchingServices.toArray(services);
        }
    }

    /*
     * return empty array as don't support multi docs
     */
    public MultiDocPrintService[]
        getMultiDocPrintServices(DocFlavor[] flavors,
                                 AttributeSet attributes) {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
          security.checkPrintJobAccess();
        }
        return new MultiDocPrintService[0];
    }


    public synchronized PrintService getDefaultPrintService() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
          security.checkPrintJobAccess();
        }


        // Windows does not have notification for a change in default
        // so we always get the latest.
        defaultPrinter = getDefaultPrinterName();
        if (defaultPrinter == null) {
            return null;
        }

        if ((defaultPrintService != null) &&
            defaultPrintService.getName().equals(defaultPrinter)) {

            return defaultPrintService;
        }

         // Not the same as default so proceed to get new PrintService.

        // clear defaultPrintService
        defaultPrintService = null;

        if (printServices != null) {
            for (int j=0; j<printServices.length; j++) {
                if (defaultPrinter.equals(printServices[j].getName())) {
                    defaultPrintService = printServices[j];
                    break;
                }
            }
        }

        if (defaultPrintService == null) {
            defaultPrintService = new Win32PrintService(defaultPrinter);
        }
        return defaultPrintService;
    }

    private final class PrinterChangeListener implements Runnable {
        @Override
        public void run() {
            notifyLocalPrinterChange(); // busy loop in the native code
        }
    }

    /* Windows provides *PrinterChangeNotification* functions that provides
       information about printer status changes of the local printers but not
       network printers.
       Alternatively, Windows provides a way through which one can get the
       network printer status changes by using WMI, RegistryKeyChange combination,
       which is a slightly complex mechanism.
       The Windows WMI offers an async and sync method to read through registry
       via the WQL query. The async method is considered dangerous as it leaves
       open a channel until we close it. But the async method has the advantage of
       being notified of a change in registry by calling callback without polling for it.
       The sync method uses the polling mechanism to notify.
       RegistryValueChange cannot be used in combination with WMI to get registry
       value change notification because of an error that may be generated because the
       scope of the query would be too big to handle(at times).
       Hence an alternative mechanism is chosen via the EnumPrinters by polling for the
       count of printer status changes(add\remove) and based on it update the printers
       list.
    */
    class RemotePrinterChangeListener implements Comparator<String>, Runnable {

        RemotePrinterChangeListener() {
        }

        @Override
        public int compare(String o1, String o2) {
            return ((o1 == null)
                    ? ((o2 == null) ? 0 : 1)
                    : ((o2 == null) ? -1 : o1.compareTo(o2)));
        }

        @Override
        public void run() {
            // Init the list of remote printers
            String[] prevRemotePrinters = getRemotePrintersNames();
            if (prevRemotePrinters != null) {
                Arrays.sort(prevRemotePrinters, this);
            }

            while (true) {
                try {
                    Thread.sleep(refreshTime * 1000);
                } catch (InterruptedException e) {
                    break;
                }

                String[] currentRemotePrinters = getRemotePrintersNames();
                if (currentRemotePrinters != null) {
                    Arrays.sort(currentRemotePrinters, this);
                }
                if (!Arrays.equals(prevRemotePrinters, currentRemotePrinters)) {
                    // The list of remote printers got updated,
                    // so update the cached list printers which
                    // includes both local and network printers
                    refreshServices();

                    // store the current data for next comparison
                    prevRemotePrinters = currentRemotePrinters;
                }
            }
        }
    }

    private native String getDefaultPrinterName();
    private native String[] getAllPrinterNames();
    private native void notifyLocalPrinterChange();
    private native String[] getRemotePrintersNames();
}
