/*
 * Copyright (c) 1999, 2006, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt.datatransfer;

import java.awt.EventQueue;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.FlavorTable;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorListener;
import java.awt.datatransfer.FlavorEvent;
import java.awt.datatransfer.UnsupportedFlavorException;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;

import java.io.IOException;

import sun.awt.AppContext;
import sun.awt.PeerEvent;
import sun.awt.SunToolkit;
import sun.awt.EventListenerAggregate;


/**
 * Serves as a common, helper superclass for the Win32 and X11 system
 * Clipboards.
 *
 * @author Danila Sinopalnikov
 * @author Alexander Gerasimov
 *
 * @since 1.3
 */
public abstract class SunClipboard extends Clipboard
    implements PropertyChangeListener {

    public static final FlavorTable flavorMap =
        (FlavorTable)SystemFlavorMap.getDefaultFlavorMap();

    private AppContext contentsContext = null;

    private final Object CLIPBOARD_FLAVOR_LISTENER_KEY;

    /**
     * A number of <code>FlavorListener</code>s currently registered
     * on this clipboard across all <code>AppContext</code>s.
     */
    private volatile int numberOfFlavorListeners = 0;

    /**
     * A set of <code>DataFlavor</code>s that is available on
     * this clipboard. It is used for tracking changes
     * of <code>DataFlavor</code>s available on this clipboard.
     */
    private volatile Set currentDataFlavors;


    public SunClipboard(String name) {
        super(name);
        CLIPBOARD_FLAVOR_LISTENER_KEY = new StringBuffer(name + "_CLIPBOARD_FLAVOR_LISTENER_KEY");
    }

    public synchronized void setContents(Transferable contents,
                                         ClipboardOwner owner) {
        // 4378007 : Toolkit.getSystemClipboard().setContents(null, null)
        // should throw NPE
        if (contents == null) {
            throw new NullPointerException("contents");
        }

        initContext();

        final ClipboardOwner oldOwner = this.owner;
        final Transferable oldContents = this.contents;

        try {
            this.owner = owner;
            this.contents = new TransferableProxy(contents, true);

            setContentsNative(contents);
        } finally {
            if (oldOwner != null && oldOwner != owner) {
                EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        oldOwner.lostOwnership(SunClipboard.this, oldContents);
                    }
                });
            }
        }
    }

    private synchronized void initContext() {
        final AppContext context = AppContext.getAppContext();

        if (contentsContext != context) {
            // Need to synchronize on the AppContext to guarantee that it cannot
            // be disposed after the check, but before the listener is added.
            synchronized (context) {
                if (context.isDisposed()) {
                    throw new IllegalStateException("Can't set contents from disposed AppContext");
                }
                context.addPropertyChangeListener
                    (AppContext.DISPOSED_PROPERTY_NAME, this);
            }
            if (contentsContext != null) {
                contentsContext.removePropertyChangeListener
                    (AppContext.DISPOSED_PROPERTY_NAME, this);
            }
            contentsContext = context;
        }
    }

    public synchronized Transferable getContents(Object requestor) {
        if (contents != null) {
            return contents;
        }
        return new ClipboardTransferable(this);
    }


    /**
     * @return the contents of this clipboard if it has been set from the same
     *         AppContext as it is currently retrieved or null otherwise
     * @since 1.5
     */
    private synchronized Transferable getContextContents() {
        AppContext context = AppContext.getAppContext();
        return (context == contentsContext) ? contents : null;
    }


    /**
     * @see java.awt.Clipboard#getAvailableDataFlavors
     * @since 1.5
     */
    public DataFlavor[] getAvailableDataFlavors() {
        Transferable cntnts = getContextContents();
        if (cntnts != null) {
            return cntnts.getTransferDataFlavors();
        }

        long[] formats = getClipboardFormatsOpenClose();

        return DataTransferer.getInstance().
            getFlavorsForFormatsAsArray(formats, flavorMap);
    }

    /**
     * @see java.awt.Clipboard#isDataFlavorAvailable
     * @since 1.5
     */
    public boolean isDataFlavorAvailable(DataFlavor flavor) {
        if (flavor == null) {
            throw new NullPointerException("flavor");
        }

        Transferable cntnts = getContextContents();
        if (cntnts != null) {
            return cntnts.isDataFlavorSupported(flavor);
        }

        long[] formats = getClipboardFormatsOpenClose();

        return formatArrayAsDataFlavorSet(formats).contains(flavor);
    }

    /**
     * @see java.awt.Clipboard#getData
     * @since 1.5
     */
    public Object getData(DataFlavor flavor)
        throws UnsupportedFlavorException, IOException {
        if (flavor == null) {
            throw new NullPointerException("flavor");
        }

        Transferable cntnts = getContextContents();
        if (cntnts != null) {
            return cntnts.getTransferData(flavor);
        }

        long format = 0;
        byte[] data = null;
        Transferable localeTransferable = null;

        try {
            openClipboard(null);

            long[] formats = getClipboardFormats();
            Long lFormat = DataTransferer.getInstance().
                    getFlavorsForFormats(formats, flavorMap).get(flavor);

            if (lFormat == null) {
                throw new UnsupportedFlavorException(flavor);
            }

            format = lFormat.longValue();
            data = getClipboardData(format);

            if (DataTransferer.getInstance().isLocaleDependentTextFormat(format)) {
                localeTransferable = createLocaleTransferable(formats);
            }

        } finally {
            closeClipboard();
        }

        return DataTransferer.getInstance().
                translateBytes(data, flavor, format, localeTransferable);
    }

    /**
     * The clipboard must be opened.
     *
     * @since 1.5
     */
    protected Transferable createLocaleTransferable(long[] formats) throws IOException {
        return null;
    }

    /**
     * @throws IllegalStateException if the clipboard has not been opened
     */
    public void openClipboard(SunClipboard newOwner) {}
    public void closeClipboard() {}

    public abstract long getID();

    public void propertyChange(PropertyChangeEvent evt) {
        if (AppContext.DISPOSED_PROPERTY_NAME.equals(evt.getPropertyName()) &&
            Boolean.TRUE.equals(evt.getNewValue())) {
            final AppContext disposedContext = (AppContext)evt.getSource();
            lostOwnershipLater(disposedContext);
        }
    }

    protected void lostOwnershipImpl() {
        lostOwnershipLater(null);
    }

    /**
     * Clears the clipboard state (contents, owner and contents context) and
     * notifies the current owner that ownership is lost. Does nothing if the
     * argument is not <code>null</code> and is not equal to the current
     * contents context.
     *
     * @param disposedContext the AppContext that is disposed or
     *        <code>null</code> if the ownership is lost because another
     *        application acquired ownership.
     */
    protected void lostOwnershipLater(final AppContext disposedContext) {
        final AppContext context = this.contentsContext;
        if (context == null) {
            return;
        }

        final Runnable runnable = new Runnable() {
                public void run() {
                    final SunClipboard sunClipboard = SunClipboard.this;
                    ClipboardOwner owner = null;
                    Transferable contents = null;

                    synchronized (sunClipboard) {
                        final AppContext context = sunClipboard.contentsContext;

                        if (context == null) {
                            return;
                        }

                        if (disposedContext == null || context == disposedContext) {
                            owner = sunClipboard.owner;
                            contents = sunClipboard.contents;
                            sunClipboard.contentsContext = null;
                            sunClipboard.owner = null;
                            sunClipboard.contents = null;
                            sunClipboard.clearNativeContext();
                            context.removePropertyChangeListener
                                (AppContext.DISPOSED_PROPERTY_NAME, sunClipboard);
                        } else {
                            return;
                        }
                    }
                    if (owner != null) {
                        owner.lostOwnership(sunClipboard, contents);
                    }
                }
            };

        SunToolkit.postEvent(context, new PeerEvent(this, runnable,
                                                    PeerEvent.PRIORITY_EVENT));
    }

    protected abstract void clearNativeContext();

    protected abstract void setContentsNative(Transferable contents);

    /**
     * @since 1.5
     */
    protected long[] getClipboardFormatsOpenClose() {
        try {
            openClipboard(null);
            return getClipboardFormats();
        } finally {
            closeClipboard();
        }
    }

    /**
     * Returns zero-length array (not null) if the number of available formats is zero.
     *
     * @throws IllegalStateException if formats could not be retrieved
     */
    protected abstract long[] getClipboardFormats();

    protected abstract byte[] getClipboardData(long format) throws IOException;


    private static Set formatArrayAsDataFlavorSet(long[] formats) {
        return (formats == null) ? null :
                DataTransferer.getInstance().
                getFlavorsForFormatsAsSet(formats, flavorMap);
    }


    public synchronized void addFlavorListener(FlavorListener listener) {
        if (listener == null) {
            return;
        }
        AppContext appContext = AppContext.getAppContext();
        EventListenerAggregate contextFlavorListeners = (EventListenerAggregate)
                appContext.get(CLIPBOARD_FLAVOR_LISTENER_KEY);
        if (contextFlavorListeners == null) {
            contextFlavorListeners = new EventListenerAggregate(FlavorListener.class);
            appContext.put(CLIPBOARD_FLAVOR_LISTENER_KEY, contextFlavorListeners);
        }
        contextFlavorListeners.add(listener);

        if (numberOfFlavorListeners++ == 0) {
            long[] currentFormats = null;
            try {
                openClipboard(null);
                currentFormats = getClipboardFormats();
            } catch (IllegalStateException exc) {
            } finally {
                closeClipboard();
            }
            currentDataFlavors = formatArrayAsDataFlavorSet(currentFormats);

            registerClipboardViewerChecked();
        }
    }

    public synchronized void removeFlavorListener(FlavorListener listener) {
        if (listener == null) {
            return;
        }
        AppContext appContext = AppContext.getAppContext();
        EventListenerAggregate contextFlavorListeners = (EventListenerAggregate)
                appContext.get(CLIPBOARD_FLAVOR_LISTENER_KEY);
        if (contextFlavorListeners == null){
            //else we throw NullPointerException, but it is forbidden
            return;
        }
        if (contextFlavorListeners.remove(listener) &&
                --numberOfFlavorListeners == 0) {
            unregisterClipboardViewerChecked();
            currentDataFlavors = null;
        }
    }

    public synchronized FlavorListener[] getFlavorListeners() {
        EventListenerAggregate contextFlavorListeners = (EventListenerAggregate)
                AppContext.getAppContext().get(CLIPBOARD_FLAVOR_LISTENER_KEY);
        return contextFlavorListeners == null ? new FlavorListener[0] :
                (FlavorListener[])contextFlavorListeners.getListenersCopy();
    }

    public boolean areFlavorListenersRegistered() {
        return (numberOfFlavorListeners > 0);
    }

    protected abstract void registerClipboardViewerChecked();

    protected abstract void unregisterClipboardViewerChecked();

    /**
     * Checks change of the <code>DataFlavor</code>s and, if necessary,
     * posts notifications on <code>FlavorEvent</code>s to the
     * AppContexts' EDTs.
     * The parameter <code>formats</code> is null iff we have just
     * failed to get formats available on the clipboard.
     *
     * @param formats data formats that have just been retrieved from
     *        this clipboard
     */
    public void checkChange(long[] formats) {
        Set prevDataFlavors = currentDataFlavors;
        currentDataFlavors = formatArrayAsDataFlavorSet(formats);

        if ((prevDataFlavors != null) && (currentDataFlavors != null) &&
                prevDataFlavors.equals(currentDataFlavors)) {
            // we've been able to successfully get available on the clipboard
            // DataFlavors this and previous time and they are coincident;
            // don't notify
            return;
        }

        class SunFlavorChangeNotifier implements Runnable {
            private final FlavorListener flavorListener;

            SunFlavorChangeNotifier(FlavorListener flavorListener) {
                this.flavorListener = flavorListener;
            }

            public void run() {
                if (flavorListener != null) {
                    flavorListener.flavorsChanged(new FlavorEvent(SunClipboard.this));
                }
            }
        };

        for (Iterator it = AppContext.getAppContexts().iterator(); it.hasNext();) {
            AppContext appContext = (AppContext)it.next();
            if (appContext == null || appContext.isDisposed()) {
                continue;
            }
            EventListenerAggregate flavorListeners = (EventListenerAggregate)
                    appContext.get(CLIPBOARD_FLAVOR_LISTENER_KEY);
            if (flavorListeners != null) {
                FlavorListener[] flavorListenerArray =
                        (FlavorListener[])flavorListeners.getListenersInternal();
                for (int i = 0; i < flavorListenerArray.length; i++) {
                    SunToolkit.postEvent(appContext, new PeerEvent(this,
                            new SunFlavorChangeNotifier(flavorListenerArray[i]),
                            PeerEvent.PRIORITY_EVENT));
                }
            }
        }
    }

}
