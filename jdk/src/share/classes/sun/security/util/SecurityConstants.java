/*
 * Copyright 2003-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.util;

import java.net.SocketPermission;
import java.net.NetPermission;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Permission;
import java.security.BasicPermission;
import java.security.SecurityPermission;
import java.security.AllPermission;

/**
 * Permission constants and string constants used to create permissions
 * used throughout the JDK.
 */
public final class SecurityConstants {
    // Cannot create one of these
    private SecurityConstants () {
    }

    // Commonly used string constants for permission actions used by
    // SecurityManager. Declare here for shortcut when checking permissions
    // in FilePermission, SocketPermission, and PropertyPermission.

    public static final String FILE_DELETE_ACTION = "delete";
    public static final String FILE_EXECUTE_ACTION = "execute";
    public static final String FILE_READ_ACTION = "read";
    public static final String FILE_WRITE_ACTION = "write";
    public static final String FILE_READLINK_ACTION = "readlink";

    public static final String SOCKET_RESOLVE_ACTION = "resolve";
    public static final String SOCKET_CONNECT_ACTION = "connect";
    public static final String SOCKET_LISTEN_ACTION = "listen";
    public static final String SOCKET_ACCEPT_ACTION = "accept";
    public static final String SOCKET_CONNECT_ACCEPT_ACTION = "connect,accept";

    public static final String PROPERTY_RW_ACTION = "read,write";
    public static final String PROPERTY_READ_ACTION = "read";
    public static final String PROPERTY_WRITE_ACTION = "write";

    // Permission constants used in the various checkPermission() calls in JDK.

    // java.lang.Class, java.lang.SecurityManager, java.lang.System,
    // java.net.URLConnection, java.security.AllPermission, java.security.Policy,
    // sun.security.provider.PolicyFile
    public static final AllPermission ALL_PERMISSION = new AllPermission();

    /**
     * Permission type used when AWT is not present.
     */
    private static class FakeAWTPermission extends BasicPermission {
        private static final long serialVersionUID = -1L;
        public FakeAWTPermission(String name) {
            super(name);
        }
        public String toString() {
            return "(\"java.awt.AWTPermission\" \"" + getName() + "\")";
        }
    }

    /**
     * Permission factory used when AWT is not present.
     */
    private static class FakeAWTPermissionFactory
        implements PermissionFactory<FakeAWTPermission>
    {
        @Override
        public FakeAWTPermission newPermission(String name) {
            return new FakeAWTPermission(name);
        }
    }

    /**
     * AWT Permissions used in the JDK.
     */
    public static class AWT {
        private AWT() { }

        /**
         * The class name of the factory to create java.awt.AWTPermission objects.
         */
        private static final String AWTFactory = "sun.awt.AWTPermissionFactory";

        /**
         * The PermissionFactory to create AWT permissions (or fake permissions
         * if AWT is not present).
         */
        private static final PermissionFactory<?> factory = permissionFactory();

        private static PermissionFactory<?> permissionFactory() {
            Class<?> c = AccessController
                .doPrivileged(new PrivilegedAction<Class<?>>() {
                    public Class<?> run() {
                        try {
                           return Class.forName(AWTFactory, true, null);
                        } catch (ClassNotFoundException e) {
                            // not available
                            return null;
                        }
                    }});
            if (c != null) {
                // AWT present
                try {
                    return (PermissionFactory<?>)c.newInstance();
                } catch (InstantiationException x) {
                    throw new InternalError(x.getMessage());
                } catch (IllegalAccessException x) {
                    throw new InternalError(x.getMessage());
                }
            } else {
                // AWT not present
                return new FakeAWTPermissionFactory();
            }
        }

        private static Permission newAWTPermission(String name) {
            return factory.newPermission(name);
        }

        // java.lang.SecurityManager
        public static final Permission TOPLEVEL_WINDOW_PERMISSION =
            newAWTPermission("showWindowWithoutWarningBanner");

        // java.lang.SecurityManager
        public static final Permission ACCESS_CLIPBOARD_PERMISSION =
            newAWTPermission("accessClipboard");

        // java.lang.SecurityManager
        public static final Permission CHECK_AWT_EVENTQUEUE_PERMISSION =
            newAWTPermission("accessEventQueue");

        // java.awt.Dialog
        public static final Permission TOOLKIT_MODALITY_PERMISSION =
            newAWTPermission("toolkitModality");

        // java.awt.Robot
        public static final Permission READ_DISPLAY_PIXELS_PERMISSION =
            newAWTPermission("readDisplayPixels");

        // java.awt.Robot
        public static final Permission CREATE_ROBOT_PERMISSION =
            newAWTPermission("createRobot");

        // java.awt.MouseInfo
        public static final Permission WATCH_MOUSE_PERMISSION =
            newAWTPermission("watchMousePointer");

        // java.awt.Window
        public static final Permission SET_WINDOW_ALWAYS_ON_TOP_PERMISSION =
            newAWTPermission("setWindowAlwaysOnTop");

        // java.awt.Toolkit
        public static final Permission ALL_AWT_EVENTS_PERMISSION =
            newAWTPermission("listenToAllAWTEvents");

        // java.awt.SystemTray
        public static final Permission ACCESS_SYSTEM_TRAY_PERMISSION =
            newAWTPermission("accessSystemTray");
    }

    // java.net.URL
    public static final NetPermission SPECIFY_HANDLER_PERMISSION =
       new NetPermission("specifyStreamHandler");

    // java.net.ProxySelector
    public static final NetPermission SET_PROXYSELECTOR_PERMISSION =
       new NetPermission("setProxySelector");

    // java.net.ProxySelector
    public static final NetPermission GET_PROXYSELECTOR_PERMISSION =
       new NetPermission("getProxySelector");

    // java.net.CookieHandler
    public static final NetPermission SET_COOKIEHANDLER_PERMISSION =
       new NetPermission("setCookieHandler");

    // java.net.CookieHandler
    public static final NetPermission GET_COOKIEHANDLER_PERMISSION =
       new NetPermission("getCookieHandler");

    // java.net.ResponseCache
    public static final NetPermission SET_RESPONSECACHE_PERMISSION =
       new NetPermission("setResponseCache");

    // java.net.ResponseCache
    public static final NetPermission GET_RESPONSECACHE_PERMISSION =
       new NetPermission("getResponseCache");

    // java.lang.SecurityManager, sun.applet.AppletPanel, sun.misc.Launcher
    public static final RuntimePermission CREATE_CLASSLOADER_PERMISSION =
        new RuntimePermission("createClassLoader");

    // java.lang.SecurityManager
    public static final RuntimePermission CHECK_MEMBER_ACCESS_PERMISSION =
        new RuntimePermission("accessDeclaredMembers");

    // java.lang.SecurityManager, sun.applet.AppletSecurity
    public static final RuntimePermission MODIFY_THREAD_PERMISSION =
        new RuntimePermission("modifyThread");

    // java.lang.SecurityManager, sun.applet.AppletSecurity
    public static final RuntimePermission MODIFY_THREADGROUP_PERMISSION =
        new RuntimePermission("modifyThreadGroup");

    // java.lang.Class
    public static final RuntimePermission GET_PD_PERMISSION =
        new RuntimePermission("getProtectionDomain");

    // java.lang.Class, java.lang.ClassLoader, java.lang.Thread
    public static final RuntimePermission GET_CLASSLOADER_PERMISSION =
        new RuntimePermission("getClassLoader");

    // java.lang.Thread
    public static final RuntimePermission STOP_THREAD_PERMISSION =
       new RuntimePermission("stopThread");

    // java.lang.Thread
    public static final RuntimePermission GET_STACK_TRACE_PERMISSION =
       new RuntimePermission("getStackTrace");

    // java.security.AccessControlContext
    public static final SecurityPermission CREATE_ACC_PERMISSION =
       new SecurityPermission("createAccessControlContext");

    // java.security.AccessControlContext
    public static final SecurityPermission GET_COMBINER_PERMISSION =
       new SecurityPermission("getDomainCombiner");

    // java.security.Policy, java.security.ProtectionDomain
    public static final SecurityPermission GET_POLICY_PERMISSION =
        new SecurityPermission ("getPolicy");

    // java.lang.SecurityManager
    public static final SocketPermission LOCAL_LISTEN_PERMISSION =
        new SocketPermission("localhost:1024-", SOCKET_LISTEN_ACTION);
}
