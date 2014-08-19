/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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

package java.awt;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URI;
import java.net.URL;
import java.net.MalformedURLException;
import java.awt.AWTPermission;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.peer.DesktopPeer;
import sun.awt.SunToolkit;
import sun.awt.HeadlessToolkit;
import java.io.FilePermission;
import sun.security.util.SecurityConstants;

/**
 * The {@code Desktop} class allows a Java application to launch
 * associated applications registered on the native desktop to handle
 * a {@link java.net.URI} or a file.
 *
 * <p> Supported operations include:
 * <ul>
 *   <li>launching the user-default browser to show a specified
 *       URI;</li>
 *   <li>launching the user-default mail client with an optional
 *       {@code mailto} URI;</li>
 *   <li>launching a registered application to open, edit or print a
 *       specified file.</li>
 * </ul>
 *
 * <p> This class provides methods corresponding to these
 * operations. The methods look for the associated application
 * registered on the current platform, and launch it to handle a URI
 * or file. If there is no associated application or the associated
 * application fails to be launched, an exception is thrown.
 *
 * <p> An application is registered to a URI or file type; for
 * example, the {@code "sxi"} file extension is typically registered
 * to StarOffice.  The mechanism of registering, accessing, and
 * launching the associated application is platform-dependent.
 *
 * <p> Each operation is an action type represented by the {@link
 * Desktop.Action} class.
 *
 * <p> Note: when some action is invoked and the associated
 * application is executed, it will be executed on the same system as
 * the one on which the Java application was launched.
 *
 * @since 1.6
 * @author Armin Chen
 * @author George Zhang
 */
public class Desktop {

    /**
     * Represents an action type.  Each platform supports a different
     * set of actions.  You may use the {@link Desktop#isSupported}
     * method to determine if the given action is supported by the
     * current platform.
     * @see java.awt.Desktop#isSupported(java.awt.Desktop.Action)
     * @since 1.6
     */
    public static enum Action {
        /**
         * Represents an "open" action.
         * @see Desktop#open(java.io.File)
         */
        OPEN,
        /**
         * Represents an "edit" action.
         * @see Desktop#edit(java.io.File)
         */
        EDIT,
        /**
         * Represents a "print" action.
         * @see Desktop#print(java.io.File)
         */
        PRINT,
        /**
         * Represents a "mail" action.
         * @see Desktop#mail()
         * @see Desktop#mail(java.net.URI)
         */
        MAIL,
        /**
         * Represents a "browse" action.
         * @see Desktop#browse(java.net.URI)
         */
        BROWSE
    };

    private DesktopPeer peer;

    /**
     * Suppresses default constructor for noninstantiability.
     */
    private Desktop() {
        peer = Toolkit.getDefaultToolkit().createDesktopPeer(this);
    }

    /**
     * Returns the <code>Desktop</code> instance of the current
     * browser context.  On some platforms the Desktop API may not be
     * supported; use the {@link #isDesktopSupported} method to
     * determine if the current desktop is supported.
     * @return the Desktop instance of the current browser context
     * @throws HeadlessException if {@link
     * GraphicsEnvironment#isHeadless()} returns {@code true}
     * @throws UnsupportedOperationException if this class is not
     * supported on the current platform
     * @see #isDesktopSupported()
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public static synchronized Desktop getDesktop(){
        if (GraphicsEnvironment.isHeadless()) throw new HeadlessException();
        if (!Desktop.isDesktopSupported()) {
            throw new UnsupportedOperationException("Desktop API is not " +
                                                    "supported on the current platform");
        }

        sun.awt.AppContext context = sun.awt.AppContext.getAppContext();
        Desktop desktop = (Desktop)context.get(Desktop.class);

        if (desktop == null) {
            desktop = new Desktop();
            context.put(Desktop.class, desktop);
        }

        return desktop;
    }

    /**
     * Tests whether this class is supported on the current platform.
     * If it's supported, use {@link #getDesktop()} to retrieve an
     * instance.
     *
     * @return <code>true</code> if this class is supported on the
     *         current platform; <code>false</code> otherwise
     * @see #getDesktop()
     */
    public static boolean isDesktopSupported(){
        Toolkit defaultToolkit = Toolkit.getDefaultToolkit();
        if (defaultToolkit instanceof SunToolkit) {
            return ((SunToolkit)defaultToolkit).isDesktopSupported();
        }
        return false;
    }

    /**
     * Tests whether an action is supported on the current platform.
     *
     * <p>Even when the platform supports an action, a file or URI may
     * not have a registered application for the action.  For example,
     * most of the platforms support the {@link Desktop.Action#OPEN}
     * action.  But for a specific file, there may not be an
     * application registered to open it.  In this case, {@link
     * #isSupported} may return {@code true}, but the corresponding
     * action method will throw an {@link IOException}.
     *
     * @param action the specified {@link Action}
     * @return <code>true</code> if the specified action is supported on
     *         the current platform; <code>false</code> otherwise
     * @see Desktop.Action
     */
    public boolean isSupported(Action action) {
        return peer.isSupported(action);
    }

    /**
     * Checks if the file is a valid file and readable.
     *
     * @throws SecurityException If a security manager exists and its
     *         {@link SecurityManager#checkRead(java.lang.String)} method
     *         denies read access to the file
     * @throws NullPointerException if file is null
     * @throws IllegalArgumentException if file doesn't exist
     */
    private static void checkFileValidation(File file){
        if (file == null) throw new NullPointerException("File must not be null");

        if (!file.exists()) {
            throw new IllegalArgumentException("The file: "
                                               + file.getPath() + " doesn't exist.");
        }

        file.canRead();
    }

    /**
     * Checks if the action type is supported.
     *
     * @param actionType the action type in question
     * @throws UnsupportedOperationException if the specified action type is not
     *         supported on the current platform
     */
    private void checkActionSupport(Action actionType){
        if (!isSupported(actionType)) {
            throw new UnsupportedOperationException("The " + actionType.name()
                                                    + " action is not supported on the current platform!");
        }
    }


    /**
     *  Calls to the security manager's <code>checkPermission</code> method with
     *  an <code>AWTPermission("showWindowWithoutWarningBanner")</code>
     *  permission.
     */
    private void checkAWTPermission(){
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new AWTPermission(
                                   "showWindowWithoutWarningBanner"));
        }
    }

    /**
     * Launches the associated application to open the file.
     *
     * <p> If the specified file is a directory, the file manager of
     * the current platform is launched to open it.
     *
     * @param file the file to be opened with the associated application
     * @throws NullPointerException if {@code file} is {@code null}
     * @throws IllegalArgumentException if the specified file doesn't
     * exist
     * @throws UnsupportedOperationException if the current platform
     * does not support the {@link Desktop.Action#OPEN} action
     * @throws IOException if the specified file has no associated
     * application or the associated application fails to be launched
     * @throws SecurityException if a security manager exists and its
     * {@link java.lang.SecurityManager#checkRead(java.lang.String)}
     * method denies read access to the file, or it denies the
     * <code>AWTPermission("showWindowWithoutWarningBanner")</code>
     * permission, or the calling thread is not allowed to create a
     * subprocess
     * @see java.awt.AWTPermission
     */
    public void open(File file) throws IOException {
        checkAWTPermission();
        checkExec();
        checkActionSupport(Action.OPEN);
        checkFileValidation(file);

        peer.open(file);
    }

    /**
     * Launches the associated editor application and opens a file for
     * editing.
     *
     * @param file the file to be opened for editing
     * @throws NullPointerException if the specified file is {@code null}
     * @throws IllegalArgumentException if the specified file doesn't
     * exist
     * @throws UnsupportedOperationException if the current platform
     * does not support the {@link Desktop.Action#EDIT} action
     * @throws IOException if the specified file has no associated
     * editor, or the associated application fails to be launched
     * @throws SecurityException if a security manager exists and its
     * {@link java.lang.SecurityManager#checkRead(java.lang.String)}
     * method denies read access to the file, or {@link
     * java.lang.SecurityManager#checkWrite(java.lang.String)} method
     * denies write access to the file, or it denies the
     * <code>AWTPermission("showWindowWithoutWarningBanner")</code>
     * permission, or the calling thread is not allowed to create a
     * subprocess
     * @see java.awt.AWTPermission
     */
    public void edit(File file) throws IOException {
        checkAWTPermission();
        checkExec();
        checkActionSupport(Action.EDIT);
        file.canWrite();
        checkFileValidation(file);

        peer.edit(file);
    }

    /**
     * Prints a file with the native desktop printing facility, using
     * the associated application's print command.
     *
     * @param file the file to be printed
     * @throws NullPointerException if the specified file is {@code
     * null}
     * @throws IllegalArgumentException if the specified file doesn't
     * exist
     * @throws UnsupportedOperationException if the current platform
     *         does not support the {@link Desktop.Action#PRINT} action
     * @throws IOException if the specified file has no associated
     * application that can be used to print it
     * @throws SecurityException if a security manager exists and its
     * {@link java.lang.SecurityManager#checkRead(java.lang.String)}
     * method denies read access to the file, or its {@link
     * java.lang.SecurityManager#checkPrintJobAccess()} method denies
     * the permission to print the file, or the calling thread is not
     * allowed to create a subprocess
     */
    public void print(File file) throws IOException {
        checkExec();
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPrintJobAccess();
        }
        checkActionSupport(Action.PRINT);
        checkFileValidation(file);

        peer.print(file);
    }

    /**
     * Launches the default browser to display a {@code URI}.
     * If the default browser is not able to handle the specified
     * {@code URI}, the application registered for handling
     * {@code URIs} of the specified type is invoked. The application
     * is determined from the protocol and path of the {@code URI}, as
     * defined by the {@code URI} class.
     * <p>
     * If the calling thread does not have the necessary permissions,
     * and this is invoked from within an applet,
     * {@code AppletContext.showDocument()} is used. Similarly, if the calling
     * does not have the necessary permissions, and this is invoked from within
     * a Java Web Started application, {@code BasicService.showDocument()}
     * is used.
     *
     * @param uri the URI to be displayed in the user default browser
     * @throws NullPointerException if {@code uri} is {@code null}
     * @throws UnsupportedOperationException if the current platform
     * does not support the {@link Desktop.Action#BROWSE} action
     * @throws IOException if the user default browser is not found,
     * or it fails to be launched, or the default handler application
     * failed to be launched
     * @throws SecurityException if a security manager exists and it
     * denies the
     * <code>AWTPermission("showWindowWithoutWarningBanner")</code>
     * permission, or the calling thread is not allowed to create a
     * subprocess; and not invoked from within an applet or Java Web Started
     * application
     * @throws IllegalArgumentException if the necessary permissions
     * are not available and the URI can not be converted to a {@code URL}
     * @see java.net.URI
     * @see java.awt.AWTPermission
     * @see java.applet.AppletContext
     */
    public void browse(URI uri) throws IOException {
        SecurityException securityException = null;
        try {
            checkAWTPermission();
            checkExec();
        } catch (SecurityException e) {
            securityException = e;
        }
        checkActionSupport(Action.BROWSE);
        if (uri == null) {
            throw new NullPointerException();
        }
        if (securityException == null) {
            peer.browse(uri);
            return;
        }

        // Calling thread doesn't have necessary priviledges.
        // Delegate to DesktopBrowse so that it can work in
        // applet/webstart.
        URL url = null;
        try {
            url = uri.toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Unable to convert URI to URL", e);
        }
        sun.awt.DesktopBrowse db = sun.awt.DesktopBrowse.getInstance();
        if (db == null) {
            // Not in webstart/applet, throw the exception.
            throw securityException;
        }
        db.browse(url);
    }

    /**
     * Launches the mail composing window of the user default mail
     * client.
     *
     * @throws UnsupportedOperationException if the current platform
     * does not support the {@link Desktop.Action#MAIL} action
     * @throws IOException if the user default mail client is not
     * found, or it fails to be launched
     * @throws SecurityException if a security manager exists and it
     * denies the
     * <code>AWTPermission("showWindowWithoutWarningBanner")</code>
     * permission, or the calling thread is not allowed to create a
     * subprocess
     * @see java.awt.AWTPermission
     */
    public void mail() throws IOException {
        checkAWTPermission();
        checkExec();
        checkActionSupport(Action.MAIL);
        URI mailtoURI = null;
        try{
            mailtoURI = new URI("mailto:?");
            peer.mail(mailtoURI);
        } catch (URISyntaxException e){
            // won't reach here.
        }
    }

    /**
     * Launches the mail composing window of the user default mail
     * client, filling the message fields specified by a {@code
     * mailto:} URI.
     *
     * <p> A <code>mailto:</code> URI can specify message fields
     * including <i>"to"</i>, <i>"cc"</i>, <i>"subject"</i>,
     * <i>"body"</i>, etc.  See <a
     * href="http://www.ietf.org/rfc/rfc2368.txt">The mailto URL
     * scheme (RFC 2368)</a> for the {@code mailto:} URI specification
     * details.
     *
     * @param mailtoURI the specified {@code mailto:} URI
     * @throws NullPointerException if the specified URI is {@code
     * null}
     * @throws IllegalArgumentException if the URI scheme is not
     *         <code>"mailto"</code>
     * @throws UnsupportedOperationException if the current platform
     * does not support the {@link Desktop.Action#MAIL} action
     * @throws IOException if the user default mail client is not
     * found or fails to be launched
     * @throws SecurityException if a security manager exists and it
     * denies the
     * <code>AWTPermission("showWindowWithoutWarningBanner")</code>
     * permission, or the calling thread is not allowed to create a
     * subprocess
     * @see java.net.URI
     * @see java.awt.AWTPermission
     */
    public  void mail(URI mailtoURI) throws IOException {
        checkAWTPermission();
        checkExec();
        checkActionSupport(Action.MAIL);
        if (mailtoURI == null) throw new NullPointerException();

        if (!"mailto".equalsIgnoreCase(mailtoURI.getScheme())) {
            throw new IllegalArgumentException("URI scheme is not \"mailto\"");
        }

        peer.mail(mailtoURI);
    }

    private void checkExec() throws SecurityException {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new FilePermission("<<ALL FILES>>",
                                                  SecurityConstants.FILE_EXECUTE_ACTION));
        }
    }
}
