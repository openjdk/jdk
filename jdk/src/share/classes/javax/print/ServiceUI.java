/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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

package javax.print;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.KeyboardFocusManager;
import javax.print.attribute.Attribute;
import javax.print.attribute.AttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Destination;
import javax.print.attribute.standard.Fidelity;

import sun.print.ServiceDialog;
import sun.print.SunAlternateMedia;

/** This class is a collection of UI convenience methods which provide a
 * graphical user dialog for browsing print services looked up through the Java
 * Print Service API.
 * <p>
 * The dialogs follow a standard pattern of acting as a continue/cancel option
 *for a user as well as allowing the user to select the print service to use
 *and specify choices such as paper size and number of copies.
 * <p>
 * The dialogs are designed to work with pluggable print services though the
 * public APIs of those print services.
 * <p>
 * If a print service provides any vendor extensions these may be made
 * accessible to the user through a vendor supplied tab panel Component.
 * Such a vendor extension is encouraged to use Swing! and to support its
 * accessibility APIs.
 * The vendor extensions should return the settings as part of the
 * AttributeSet.
 * Applications which want to preserve the user settings should use those
 * settings to specify the print job.
 * Note that this class is not referenced by any other part of the Java
 * Print Service and may not be included in profiles which cannot depend
 * on the presence of the AWT packages.
 */

public class ServiceUI {


    /**
     * Presents a dialog to the user for selecting a print service (printer).
     * It is displayed at the location specified by the application and
     * is modal.
     * If the specification is invalid or would make the dialog not visible it
     * will be displayed at a location determined by the implementation.
     * The dialog blocks its calling thread and is application modal.
     * <p>
     * The dialog may include a tab panel with custom UI lazily obtained from
     * the PrintService's ServiceUIFactory when the PrintService is browsed.
     * The dialog will attempt to locate a MAIN_UIROLE first as a JComponent,
     * then as a Panel. If there is no ServiceUIFactory or no matching role
     * the custom tab will be empty or not visible.
     * <p>
     * The dialog returns the print service selected by the user if the user
     * OK's the dialog and null if the user cancels the dialog.
     * <p>
     * An application must pass in an array of print services to browse.
     * The array must be non-null and non-empty.
     * Typically an application will pass in only PrintServices capable
     * of printing a particular document flavor.
     * <p>
     * An application may pass in a PrintService to be initially displayed.
     * A non-null parameter must be included in the array of browsable
     * services.
     * If this parameter is null a service is chosen by the implementation.
     * <p>
     * An application may optionally pass in the flavor to be printed.
     * If this is non-null choices presented to the user can be better
     * validated against those supported by the services.
     * An application must pass in a PrintRequestAttributeSet for returning
     * user choices.
     * On calling the PrintRequestAttributeSet may be empty, or may contain
     * application-specified values.
     * <p>
     * These are used to set the initial settings for the initially
     * displayed print service. Values which are not supported by the print
     * service are ignored. As the user browses print services, attributes
     * and values are copied to the new display. If a user browses a
     * print service which does not support a particular attribute-value, the
     * default for that service is used as the new value to be copied.
     * <p>
     * If the user cancels the dialog, the returned attributes will not reflect
     * any changes made by the user.
     *
     * A typical basic usage of this method may be :
     * <pre>{@code
     * PrintService[] services = PrintServiceLookup.lookupPrintServices(
     *                            DocFlavor.INPUT_STREAM.JPEG, null);
     * PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
     * if (services.length > 0) {
     *    PrintService service =  ServiceUI.printDialog(null, 50, 50,
     *                                               services, services[0],
     *                                               null,
     *                                               attributes);
     *    if (service != null) {
     *     ... print ...
     *    }
     * }
     * }</pre>
     *
     * @param gc used to select screen. null means primary or default screen.
     * @param x location of dialog including border in screen coordinates
     * @param y location of dialog including border in screen coordinates
     * @param services to be browsable, must be non-null.
     * @param defaultService - initial PrintService to display.
     * @param flavor - the flavor to be printed, or null.
     * @param attributes on input is the initial application supplied
     * preferences. This cannot be null but may be empty.
     * On output the attributes reflect changes made by the user.
     * @return print service selected by the user, or null if the user
     * cancelled the dialog.
     * @throws HeadlessException if GraphicsEnvironment.isHeadless()
     * returns true.
     * @throws IllegalArgumentException if services is null or empty,
     * or attributes is null, or the initial PrintService is not in the
     * list of browsable services.
     */
    public static PrintService printDialog(GraphicsConfiguration gc,
                                           int x, int y,
                                           PrintService[] services,
                                           PrintService defaultService,
                                           DocFlavor flavor,
                                           PrintRequestAttributeSet attributes)
        throws HeadlessException
    {
        int defaultIndex = -1;

        if (GraphicsEnvironment.isHeadless()) {
            throw new HeadlessException();
        } else if ((services == null) || (services.length == 0)) {
            throw new IllegalArgumentException("services must be non-null " +
                                               "and non-empty");
        } else if (attributes == null) {
            throw new IllegalArgumentException("attributes must be non-null");
        }

        if (defaultService != null) {
            for (int i = 0; i < services.length; i++) {
                if (services[i].equals(defaultService)) {
                    defaultIndex = i;
                    break;
                }
            }

            if (defaultIndex < 0) {
                throw new IllegalArgumentException("services must contain " +
                                                   "defaultService");
            }
        } else {
            defaultIndex = 0;
        }

        // For now we set owner to null. In the future, it may be passed
        // as an argument.
        Window owner = null;

        Rectangle gcBounds = (gc == null) ?  GraphicsEnvironment.
            getLocalGraphicsEnvironment().getDefaultScreenDevice().
            getDefaultConfiguration().getBounds() : gc.getBounds();

        ServiceDialog dialog;
        if (owner instanceof Frame) {
            dialog = new ServiceDialog(gc,
                                       x + gcBounds.x,
                                       y + gcBounds.y,
                                       services, defaultIndex,
                                       flavor, attributes,
                                       (Frame)owner);
        } else {
            dialog = new ServiceDialog(gc,
                                       x + gcBounds.x,
                                       y + gcBounds.y,
                                       services, defaultIndex,
                                       flavor, attributes,
                                       (Dialog)owner);
        }
        Rectangle dlgBounds = dialog.getBounds();

        // get union of all GC bounds
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gs = ge.getScreenDevices();
        for (int j=0; j<gs.length; j++) {
            gcBounds =
                gcBounds.union(gs[j].getDefaultConfiguration().getBounds());
        }

        // if portion of dialog is not within the gc boundary
        if (!gcBounds.contains(dlgBounds)) {
            // put in the center relative to parent frame/dialog
            dialog.setLocationRelativeTo(owner);
        }
        dialog.show();

        if (dialog.getStatus() == ServiceDialog.APPROVE) {
            PrintRequestAttributeSet newas = dialog.getAttributes();
            Class<?> dstCategory = Destination.class;
            Class<?> amCategory = SunAlternateMedia.class;
            Class<?> fdCategory = Fidelity.class;

            if (attributes.containsKey(dstCategory) &&
                !newas.containsKey(dstCategory))
            {
                attributes.remove(dstCategory);
            }

            if (attributes.containsKey(amCategory) &&
                !newas.containsKey(amCategory))
            {
                attributes.remove(amCategory);
            }

            attributes.addAll(newas);

            Fidelity fd = (Fidelity)attributes.get(fdCategory);
            if (fd != null) {
                if (fd == Fidelity.FIDELITY_TRUE) {
                    removeUnsupportedAttributes(dialog.getPrintService(),
                                                flavor, attributes);
                }
            }
        }

        return dialog.getPrintService();
    }

    /**
     * POSSIBLE FUTURE API: This method may be used down the road if we
     * decide to allow developers to explicitly display a "page setup" dialog.
     * Currently we use that functionality internally for the AWT print model.
     */
    /*
    public static void pageDialog(GraphicsConfiguration gc,
                                  int x, int y,
                                  PrintService service,
                                  DocFlavor flavor,
                                  PrintRequestAttributeSet attributes)
        throws HeadlessException
    {
        if (GraphicsEnvironment.isHeadless()) {
            throw new HeadlessException();
        } else if (service == null) {
            throw new IllegalArgumentException("service must be non-null");
        } else if (attributes == null) {
            throw new IllegalArgumentException("attributes must be non-null");
        }

        ServiceDialog dialog = new ServiceDialog(gc, x, y, service,
                                                 flavor, attributes);
        dialog.show();

        if (dialog.getStatus() == ServiceDialog.APPROVE) {
            PrintRequestAttributeSet newas = dialog.getAttributes();
            Class amCategory = SunAlternateMedia.class;

            if (attributes.containsKey(amCategory) &&
                !newas.containsKey(amCategory))
            {
                attributes.remove(amCategory);
            }

            attributes.addAll(newas.values());
        }

        dialog.getOwner().dispose();
    }
    */

    /**
     * Removes any attributes from the given AttributeSet that are
     * unsupported by the given PrintService/DocFlavor combination.
     */
    private static void removeUnsupportedAttributes(PrintService ps,
                                                    DocFlavor flavor,
                                                    AttributeSet aset)
    {
        AttributeSet asUnsupported = ps.getUnsupportedAttributes(flavor,
                                                                 aset);

        if (asUnsupported != null) {
            Attribute[] usAttrs = asUnsupported.toArray();

            for (int i=0; i<usAttrs.length; i++) {
                Class<? extends Attribute> category = usAttrs[i].getCategory();

                if (ps.isAttributeCategorySupported(category)) {
                    Attribute attr =
                        (Attribute)ps.getDefaultAttributeValue(category);

                    if (attr != null) {
                        aset.add(attr);
                    } else {
                        aset.remove(category);
                    }
                } else {
                    aset.remove(category);
                }
            }
        }
    }
}
