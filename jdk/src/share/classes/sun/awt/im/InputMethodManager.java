/*
 * Copyright (c) 1998, 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt.im;

import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.PopupMenu;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.Toolkit;
import sun.awt.AppContext;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InvocationEvent;
import java.awt.im.spi.InputMethodDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.Vector;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import sun.awt.InputMethodSupport;
import sun.awt.SunToolkit;

/**
 * <code>InputMethodManager</code> is an abstract class that manages the input
 * method environment of JVM. There is only one <code>InputMethodManager</code>
 * instance in JVM that is executed under a separate daemon thread.
 * <code>InputMethodManager</code> performs the following:
 * <UL>
 * <LI>
 * Keeps track of the current input context.</LI>
 *
 * <LI>
 * Provides a user interface to switch input methods and notifies the current
 * input context about changes made from the user interface.</LI>
 * </UL>
 *
 * The mechanism for supporting input method switch is as follows. (Note that
 * this may change in future releases.)
 *
 * <UL>
 * <LI>
 * One way is to use platform-dependent window manager's menu (known as the <I>Window
 * menu </I>in Motif and the <I>System menu</I> or <I>Control menu</I> in
 * Win32) on each window which is popped up by clicking the left top box of
 * a window (known as <I>Window menu button</I> in Motif and <I>System menu
 * button</I> in Win32). This happens to be common in both Motif and Win32.</LI>
 *
 * <LI>
 * When more than one input method descriptor can be found or the only input
 * method descriptor found supports multiple locales, a menu item
 * is added to the window (manager) menu. This item label is obtained invoking
 * <code>getTriggerMenuString()</code>. If null is returned by this method, it
 * means that there is only input method or none in the environment. Frame and Dialog
 * invoke this method.</LI>
 *
 * <LI>
 * This menu item means a trigger switch to the user to pop up a selection
 * menu.</LI>
 *
 * <LI>
 * When the menu item of the window (manager) menu has been selected by the
 * user, Frame/Dialog invokes <code>notifyChangeRequest()</code> to notify
 * <code>InputMethodManager</code> that the user wants to switch input methods.</LI>
 *
 * <LI>
 * <code>InputMethodManager</code> displays a pop-up menu to choose an input method.</LI>
 *
 * <LI>
 * <code>InputMethodManager</code> notifies the current <code>InputContext</code> of
 * the selected <code>InputMethod</code>.</LI>
 * </UL>
 *
 * <UL>
 * <LI>
 * The other way is to use user-defined hot key combination to show the pop-up menu to
 * choose an input method.  This is useful for the platforms which do not provide a
 * way to add a menu item in the window (manager) menu.</LI>
 *
 * <LI>
 * When the hot key combination is typed by the user, the component which has the input
 * focus invokes <code>notifyChangeRequestByHotKey()</code> to notify
 * <code>InputMethodManager</code> that the user wants to switch input methods.</LI>
 *
 * <LI>
 * This results in a popup menu and notification to the current input context,
 * as above.</LI>
 * </UL>
 *
 * @see java.awt.im.spi.InputMethod
 * @see sun.awt.im.InputContext
 * @see sun.awt.im.InputMethodAdapter
 * @author JavaSoft International
 */

public abstract class InputMethodManager {

    /**
     * InputMethodManager thread name
     */
    private static final String threadName = "AWT-InputMethodManager";

    /**
     * Object for global locking
     */
    private static final Object LOCK = new Object();

    /**
     * The InputMethodManager instance
     */
    private static InputMethodManager inputMethodManager;

    /**
     * Returns the instance of InputMethodManager. This method creates
     * the instance that is unique in the Java VM if it has not been
     * created yet.
     *
     * @return the InputMethodManager instance
     */
    public static final InputMethodManager getInstance() {
        if (inputMethodManager != null) {
            return inputMethodManager;
        }
        synchronized(LOCK) {
            if (inputMethodManager == null) {
                ExecutableInputMethodManager imm = new ExecutableInputMethodManager();

                // Initialize the input method manager and start a
                // daemon thread if the user has multiple input methods
                // to choose from. Otherwise, just keep the instance.
                if (imm.hasMultipleInputMethods()) {
                    imm.initialize();
                    Thread immThread = new Thread(imm, threadName);
                    immThread.setDaemon(true);
                    immThread.setPriority(Thread.NORM_PRIORITY + 1);
                    immThread.start();
                }
                inputMethodManager = imm;
            }
        }
        return inputMethodManager;
    }

    /**
     * Gets a string for the trigger menu item that should be added to
     * the window manager menu. If no need to display the trigger menu
     * item, null is returned.
     */
    public abstract String getTriggerMenuString();

    /**
     * Notifies InputMethodManager that input method change has been
     * requested by the user. This notification triggers a popup menu
     * for user selection.
     *
     * @param comp Component that has accepted the change
     * request. This component has to be a Frame or Dialog.
     */
    public abstract void notifyChangeRequest(Component comp);

    /**
     * Notifies InputMethodManager that input method change has been
     * requested by the user using the hot key combination. This
     * notification triggers a popup menu for user selection.
     *
     * @param comp Component that has accepted the change
     * request. This component has the input focus.
     */
    public abstract void notifyChangeRequestByHotKey(Component comp);

    /**
     * Sets the current input context so that it will be notified
     * of input method changes initiated from the user interface.
     * Set to real input context when activating; to null when
     * deactivating.
     */
    abstract void setInputContext(InputContext inputContext);

    /**
     * Tries to find an input method locator for the given locale.
     * Returns null if no available input method locator supports
     * the locale.
     */
    abstract InputMethodLocator findInputMethod(Locale forLocale);

    /**
     * Gets the default keyboard locale of the underlying operating system.
     */
    abstract Locale getDefaultKeyboardLocale();

    /**
     * Returns whether multiple input methods are available or not
     */
    abstract boolean hasMultipleInputMethods();

}

/**
 * <code>ExecutableInputMethodManager</code> is the implementation of the
 * <code>InputMethodManager</code> class. It is runnable as a separate
 * thread in the AWT environment.&nbsp;
 * <code>InputMethodManager.getInstance()</code> creates an instance of
 * <code>ExecutableInputMethodManager</code> and executes it as a deamon
 * thread.
 *
 * @see InputMethodManager
 */
class ExecutableInputMethodManager extends InputMethodManager
                                   implements Runnable
{
    // the input context that's informed about selections from the user interface
    private InputContext currentInputContext;

    // Menu item string for the trigger menu.
    private String triggerMenuString;

    // popup menu for selecting an input method
    private InputMethodPopupMenu selectionMenu;
    private static String selectInputMethodMenuTitle;

    // locator and name of host adapter
    private InputMethodLocator hostAdapterLocator;

    // locators for Java input methods
    private int javaInputMethodCount;         // number of Java input methods found
    private Vector<InputMethodLocator> javaInputMethodLocatorList;

    // component that is requesting input method switch
    // must be Frame or Dialog
    private Component requestComponent;

    // input context that is requesting input method switch
    private InputContext requestInputContext;

    // IM preference stuff
    private static final String preferredIMNode = "/sun/awt/im/preferredInputMethod";
    private static final String descriptorKey = "descriptor";
    private Hashtable preferredLocatorCache = new Hashtable();
    private Preferences userRoot;

    ExecutableInputMethodManager() {

        // set up host adapter locator
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        try {
            if (toolkit instanceof InputMethodSupport) {
                InputMethodDescriptor hostAdapterDescriptor =
                    ((InputMethodSupport)toolkit)
                    .getInputMethodAdapterDescriptor();
                if (hostAdapterDescriptor != null) {
                    hostAdapterLocator = new InputMethodLocator(hostAdapterDescriptor, null, null);
                }
            }
        } catch (AWTException e) {
            // if we can't get a descriptor, we'll just have to do without native input methods
        }

        javaInputMethodLocatorList = new Vector<InputMethodLocator>();
        initializeInputMethodLocatorList();
    }

    synchronized void initialize() {
        selectInputMethodMenuTitle = Toolkit.getProperty("AWT.InputMethodSelectionMenu", "Select Input Method");

        triggerMenuString = selectInputMethodMenuTitle;
    }

    public void run() {
        // If there are no multiple input methods to choose from, wait forever
        while (!hasMultipleInputMethods()) {
            try {
                synchronized (this) {
                    wait();
                }
            } catch (InterruptedException e) {
            }
        }

        // Loop for processing input method change requests
        while (true) {
            waitForChangeRequest();
            initializeInputMethodLocatorList();
            try {
                if (requestComponent != null) {
                    showInputMethodMenuOnRequesterEDT(requestComponent);
                } else {
                    // show the popup menu within the event thread
                    EventQueue.invokeAndWait(new Runnable() {
                        public void run() {
                            showInputMethodMenu();
                        }
                    });
                }
            } catch (InterruptedException ie) {
            } catch (InvocationTargetException ite) {
                // should we do anything under these exceptions?
            }
        }
    }

    // Shows Input Method Menu on the EDT of requester component
    // to avoid side effects. See 6544309.
    private void showInputMethodMenuOnRequesterEDT(Component requester)
        throws InterruptedException, InvocationTargetException {

        if (requester == null){
            return;
        }

        class AWTInvocationLock {}
        Object lock = new AWTInvocationLock();

        InvocationEvent event =
                new InvocationEvent(requester,
                                    new Runnable() {
                                        public void run() {
                                            showInputMethodMenu();
                                        }
                                    },
                                    lock,
                                    true);

        AppContext requesterAppContext = SunToolkit.targetToAppContext(requester);
        synchronized (lock) {
            SunToolkit.postEvent(requesterAppContext, event);
            while (!event.isDispatched()) {
                lock.wait();
            }
        }

        Throwable eventThrowable = event.getThrowable();
        if (eventThrowable != null) {
            throw new InvocationTargetException(eventThrowable);
        }
    }

    void setInputContext(InputContext inputContext) {
        if (currentInputContext != null && inputContext != null) {
            // don't throw this exception until 4237852 is fixed
            // throw new IllegalStateException("Can't have two active InputContext at the same time");
        }
        currentInputContext = inputContext;
    }

    public synchronized void notifyChangeRequest(Component comp) {
        if (!(comp instanceof Frame || comp instanceof Dialog))
            return;

        // if busy with the current request, ignore this request.
        if (requestComponent != null)
            return;

        requestComponent = comp;
        notify();
    }

    public synchronized void notifyChangeRequestByHotKey(Component comp) {
        while (!(comp instanceof Frame || comp instanceof Dialog)) {
            if (comp == null) {
                // no Frame or Dialog found in containment hierarchy.
                return;
            }
            comp = comp.getParent();
        }

        notifyChangeRequest(comp);
    }

    public String getTriggerMenuString() {
        return triggerMenuString;
    }

    /*
     * Returns true if the environment indicates there are multiple input methods
     */
    boolean hasMultipleInputMethods() {
        return ((hostAdapterLocator != null) && (javaInputMethodCount > 0)
                || (javaInputMethodCount > 1));
    }

    private synchronized void waitForChangeRequest() {
        try {
            while (requestComponent == null) {
                wait();
            }
        } catch (InterruptedException e) {
        }
    }

    /*
     * initializes the input method locator list for all
     * installed input method descriptors.
     */
    private void initializeInputMethodLocatorList() {
        synchronized (javaInputMethodLocatorList) {
            javaInputMethodLocatorList.clear();
            try {
                AccessController.doPrivileged(new PrivilegedExceptionAction() {
                    public Object run() {
                        for (InputMethodDescriptor descriptor :
                            ServiceLoader.loadInstalled(InputMethodDescriptor.class)) {
                            ClassLoader cl = descriptor.getClass().getClassLoader();
                            javaInputMethodLocatorList.add(new InputMethodLocator(descriptor, cl, null));
                        }
                        return null;
                    }
                });
            }  catch (PrivilegedActionException e) {
                e.printStackTrace();
            }
            javaInputMethodCount = javaInputMethodLocatorList.size();
        }

        if (hasMultipleInputMethods()) {
            // initialize preferences
            if (userRoot == null) {
                userRoot = getUserRoot();
            }
        } else {
            // indicate to clients not to offer the menu
            triggerMenuString = null;
        }
    }

    private void showInputMethodMenu() {

        if (!hasMultipleInputMethods()) {
            requestComponent = null;
            return;
        }

        // initialize pop-up menu
        selectionMenu = InputMethodPopupMenu.getInstance(requestComponent, selectInputMethodMenuTitle);

        // we have to rebuild the menu each time because
        // some input methods (such as IIIMP) may change
        // their list of supported locales dynamically
        selectionMenu.removeAll();

        // get information about the currently selected input method
        // ??? if there's no current input context, what's the point
        // of showing the menu?
        String currentSelection = getCurrentSelection();

        // Add menu item for host adapter
        if (hostAdapterLocator != null) {
            selectionMenu.addOneInputMethodToMenu(hostAdapterLocator, currentSelection);
            selectionMenu.addSeparator();
        }

        // Add menu items for other input methods
        for (int i = 0; i < javaInputMethodLocatorList.size(); i++) {
            InputMethodLocator locator = javaInputMethodLocatorList.get(i);
            selectionMenu.addOneInputMethodToMenu(locator, currentSelection);
        }

        synchronized (this) {
            selectionMenu.addToComponent(requestComponent);
            requestInputContext = currentInputContext;
            selectionMenu.show(requestComponent, 60, 80); // TODO: get proper x, y...
            requestComponent = null;
        }
    }

    private String getCurrentSelection() {
        InputContext inputContext = currentInputContext;
        if (inputContext != null) {
            InputMethodLocator locator = inputContext.getInputMethodLocator();
            if (locator != null) {
                return locator.getActionCommandString();
            }
        }
        return null;
    }

    synchronized void changeInputMethod(String choice) {
        InputMethodLocator locator = null;

        String inputMethodName = choice;
        String localeString = null;
        int index = choice.indexOf('\n');
        if (index != -1) {
            localeString = choice.substring(index + 1);
            inputMethodName = choice.substring(0, index);
        }
        if (hostAdapterLocator.getActionCommandString().equals(inputMethodName)) {
            locator = hostAdapterLocator;
        } else {
            for (int i = 0; i < javaInputMethodLocatorList.size(); i++) {
                InputMethodLocator candidate = javaInputMethodLocatorList.get(i);
                String name = candidate.getActionCommandString();
                if (name.equals(inputMethodName)) {
                    locator = candidate;
                    break;
                }
            }
        }

        if (locator != null && localeString != null) {
            String language = "", country = "", variant = "";
            int postIndex = localeString.indexOf('_');
            if (postIndex == -1) {
                language = localeString;
            } else {
                language = localeString.substring(0, postIndex);
                int preIndex = postIndex + 1;
                postIndex = localeString.indexOf('_', preIndex);
                if (postIndex == -1) {
                    country = localeString.substring(preIndex);
                } else {
                    country = localeString.substring(preIndex, postIndex);
                    variant = localeString.substring(postIndex + 1);
                }
            }
            Locale locale = new Locale(language, country, variant);
            locator = locator.deriveLocator(locale);
        }

        if (locator == null)
            return;

        // tell the input context about the change
        if (requestInputContext != null) {
            requestInputContext.changeInputMethod(locator);
            requestInputContext = null;

            // remember the selection
            putPreferredInputMethod(locator);
        }
    }

    InputMethodLocator findInputMethod(Locale locale) {
        // look for preferred input method first
        InputMethodLocator locator = getPreferredInputMethod(locale);
        if (locator != null) {
            return locator;
        }

        if (hostAdapterLocator != null && hostAdapterLocator.isLocaleAvailable(locale)) {
            return hostAdapterLocator.deriveLocator(locale);
        }

        // Update the locator list
        initializeInputMethodLocatorList();

        for (int i = 0; i < javaInputMethodLocatorList.size(); i++) {
            InputMethodLocator candidate = javaInputMethodLocatorList.get(i);
            if (candidate.isLocaleAvailable(locale)) {
                return candidate.deriveLocator(locale);
            }
        }
        return null;
    }

    Locale getDefaultKeyboardLocale() {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        if (toolkit instanceof InputMethodSupport) {
            return ((InputMethodSupport)toolkit).getDefaultKeyboardLocale();
        } else {
            return Locale.getDefault();
        }
    }

    /**
     * Returns a InputMethodLocator object that the
     * user prefers for the given locale.
     *
     * @param locale Locale for which the user prefers the input method.
     */
    private synchronized InputMethodLocator getPreferredInputMethod(Locale locale) {
        InputMethodLocator preferredLocator = null;

        if (!hasMultipleInputMethods()) {
            // No need to look for a preferred Java input method
            return null;
        }

        // look for the cached preference first.
        preferredLocator = (InputMethodLocator)preferredLocatorCache.get(locale.toString().intern());
        if (preferredLocator != null) {
            return preferredLocator;
        }

        // look for the preference in the user preference tree
        String nodePath = findPreferredInputMethodNode(locale);
        String descriptorName = readPreferredInputMethod(nodePath);
        Locale advertised;

        // get the locator object
        if (descriptorName != null) {
            // check for the host adapter first
            if (hostAdapterLocator != null &&
                hostAdapterLocator.getDescriptor().getClass().getName().equals(descriptorName)) {
                advertised = getAdvertisedLocale(hostAdapterLocator, locale);
                if (advertised != null) {
                    preferredLocator = hostAdapterLocator.deriveLocator(advertised);
                    preferredLocatorCache.put(locale.toString().intern(), preferredLocator);
                }
                return preferredLocator;
            }
            // look for Java input methods
            for (int i = 0; i < javaInputMethodLocatorList.size(); i++) {
                InputMethodLocator locator = javaInputMethodLocatorList.get(i);
                InputMethodDescriptor descriptor = locator.getDescriptor();
                if (descriptor.getClass().getName().equals(descriptorName)) {
                    advertised = getAdvertisedLocale(locator, locale);
                    if (advertised != null) {
                        preferredLocator = locator.deriveLocator(advertised);
                        preferredLocatorCache.put(locale.toString().intern(), preferredLocator);
                    }
                    return preferredLocator;
                }
            }

            // maybe preferred input method information is bogus.
            writePreferredInputMethod(nodePath, null);
        }

        return null;
    }

    private String findPreferredInputMethodNode(Locale locale) {
        if (userRoot == null) {
            return null;
        }

        // create locale node relative path
        String nodePath = preferredIMNode + "/" + createLocalePath(locale);

        // look for the descriptor
        while (!nodePath.equals(preferredIMNode)) {
            try {
                if (userRoot.nodeExists(nodePath)) {
                    if (readPreferredInputMethod(nodePath) != null) {
                        return nodePath;
                    }
                }
            } catch (BackingStoreException bse) {
            }

            // search at parent's node
            nodePath = nodePath.substring(0, nodePath.lastIndexOf('/'));
        }

        return null;
    }

    private String readPreferredInputMethod(String nodePath) {
        if ((userRoot == null) || (nodePath == null)) {
            return null;
        }

        return userRoot.node(nodePath).get(descriptorKey, null);
    }

    /**
     * Writes the preferred input method descriptor class name into
     * the user's Preferences tree in accordance with the given locale.
     *
     * @param inputMethodLocator input method locator to remember.
     */
    private synchronized void putPreferredInputMethod(InputMethodLocator locator) {
        InputMethodDescriptor descriptor = locator.getDescriptor();
        Locale preferredLocale = locator.getLocale();

        if (preferredLocale == null) {
            // check available locales of the input method
            try {
                Locale[] availableLocales = descriptor.getAvailableLocales();
                if (availableLocales.length == 1) {
                    preferredLocale = availableLocales[0];
                } else {
                    // there is no way to know which locale is the preferred one, so do nothing.
                    return;
                }
            } catch (AWTException ae) {
                // do nothing here, either.
                return;
            }
        }

        // for regions that have only one language, we need to regard
        // "xx_YY" as "xx" when putting the preference into tree
        if (preferredLocale.equals(Locale.JAPAN)) {
            preferredLocale = Locale.JAPANESE;
        }
        if (preferredLocale.equals(Locale.KOREA)) {
            preferredLocale = Locale.KOREAN;
        }
        if (preferredLocale.equals(new Locale("th", "TH"))) {
            preferredLocale = new Locale("th");
        }

        // obtain node
        String path = preferredIMNode + "/" + createLocalePath(preferredLocale);

        // write in the preference tree
        writePreferredInputMethod(path, descriptor.getClass().getName());
        preferredLocatorCache.put(preferredLocale.toString().intern(),
            locator.deriveLocator(preferredLocale));

        return;
    }

    private String createLocalePath(Locale locale) {
        String language = locale.getLanguage();
        String country = locale.getCountry();
        String variant = locale.getVariant();
        String localePath = null;
        if (!variant.equals("")) {
            localePath = "_" + language + "/_" + country + "/_" + variant;
        } else if (!country.equals("")) {
            localePath = "_" + language + "/_" + country;
        } else {
            localePath = "_" + language;
        }

        return localePath;
    }

    private void writePreferredInputMethod(String path, String descriptorName) {
        if (userRoot != null) {
            Preferences node = userRoot.node(path);

            // record it
            if (descriptorName != null) {
                node.put(descriptorKey, descriptorName);
            } else {
                node.remove(descriptorKey);
            }
        }
    }

    private Preferences getUserRoot() {
        return (Preferences)AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                return Preferences.userRoot();
            }
        });
    }

    private Locale getAdvertisedLocale(InputMethodLocator locator, Locale locale) {
        Locale advertised = null;

        if (locator.isLocaleAvailable(locale)) {
            advertised = locale;
        } else if (locale.getLanguage().equals("ja")) {
            // for Japanese, Korean, and Thai, check whether the input method supports
            // language or language_COUNTRY.
            if (locator.isLocaleAvailable(Locale.JAPAN)) {
                advertised = Locale.JAPAN;
            } else if (locator.isLocaleAvailable(Locale.JAPANESE)) {
                advertised = Locale.JAPANESE;
            }
        } else if (locale.getLanguage().equals("ko")) {
            if (locator.isLocaleAvailable(Locale.KOREA)) {
                advertised = Locale.KOREA;
            } else if (locator.isLocaleAvailable(Locale.KOREAN)) {
                advertised = Locale.KOREAN;
            }
        } else if (locale.getLanguage().equals("th")) {
            if (locator.isLocaleAvailable(new Locale("th", "TH"))) {
                advertised = new Locale("th", "TH");
            } else if (locator.isLocaleAvailable(new Locale("th"))) {
                advertised = new Locale("th");
            }
        }

        return advertised;
    }
}
