/*
 * Copyright (c) 1996, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.rmi.registry;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.io.FilePermission;
import java.io.IOException;
import java.net.*;
import java.rmi.*;
import java.rmi.server.ObjID;
import java.rmi.server.RemoteServer;
import java.rmi.server.ServerNotActiveException;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Policy;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.text.MessageFormat;
import sun.rmi.server.LoaderHandler;
import sun.rmi.server.UnicastServerRef;
import sun.rmi.server.UnicastServerRef2;
import sun.rmi.transport.LiveRef;
import sun.rmi.transport.ObjectTable;
import sun.rmi.transport.Target;

/**
 * A "registry" exists on every node that allows RMI connections to
 * servers on that node.  The registry on a particular node contains a
 * transient database that maps names to remote objects.  When the
 * node boots, the registry database is empty.  The names stored in the
 * registry are pure and are not parsed.  A service storing itself in
 * the registry may want to prefix its name of the service by a package
 * name (although not required), to reduce name collisions in the
 * registry.
 *
 * The LocateRegistry class is used to obtain registry for different hosts.
 *
 * @see java.rmi.registry.LocateRegistry
 */
public class RegistryImpl extends java.rmi.server.RemoteServer
        implements Registry
{

    /* indicate compatibility with JDK 1.1.x version of class */
    private static final long serialVersionUID = 4666870661827494597L;
    private Hashtable<String, Remote> bindings
        = new Hashtable<>(101);
    private static Hashtable<InetAddress, InetAddress> allowedAccessCache
        = new Hashtable<>(3);
    private static RegistryImpl registry;
    private static ObjID id = new ObjID(ObjID.REGISTRY_ID);

    private static ResourceBundle resources = null;

    /**
     * Construct a new RegistryImpl on the specified port with the
     * given custom socket factory pair.
     */
    public RegistryImpl(int port,
                        RMIClientSocketFactory csf,
                        RMIServerSocketFactory ssf)
        throws RemoteException
    {
        if (port == Registry.REGISTRY_PORT && System.getSecurityManager() != null) {
            // grant permission for default port only.
            try {
                AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
                    public Void run() throws RemoteException {
                        LiveRef lref = new LiveRef(id, port, csf, ssf);
                        setup(new UnicastServerRef2(lref));
                        return null;
                    }
                }, null, new SocketPermission("localhost:"+port, "listen,accept"));
            } catch (PrivilegedActionException pae) {
                throw (RemoteException)pae.getException();
            }
        } else {
            LiveRef lref = new LiveRef(id, port, csf, ssf);
            setup(new UnicastServerRef2(lref));
        }
    }

    /**
     * Construct a new RegistryImpl on the specified port.
     */
    public RegistryImpl(int port)
        throws RemoteException
    {
        if (port == Registry.REGISTRY_PORT && System.getSecurityManager() != null) {
            // grant permission for default port only.
            try {
                AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
                    public Void run() throws RemoteException {
                        LiveRef lref = new LiveRef(id, port);
                        setup(new UnicastServerRef(lref));
                        return null;
                    }
                }, null, new SocketPermission("localhost:"+port, "listen,accept"));
            } catch (PrivilegedActionException pae) {
                throw (RemoteException)pae.getException();
            }
        } else {
            LiveRef lref = new LiveRef(id, port);
            setup(new UnicastServerRef(lref));
        }
    }

    /*
     * Create the export the object using the parameter
     * <code>uref</code>
     */
    private void setup(UnicastServerRef uref)
        throws RemoteException
    {
        /* Server ref must be created and assigned before remote
         * object 'this' can be exported.
         */
        ref = uref;
        uref.exportObject(this, null, true);
    }

    /**
     * Returns the remote object for specified name in the registry.
     * @exception RemoteException If remote operation failed.
     * @exception NotBound If name is not currently bound.
     */
    public Remote lookup(String name)
        throws RemoteException, NotBoundException
    {
        synchronized (bindings) {
            Remote obj = bindings.get(name);
            if (obj == null)
                throw new NotBoundException(name);
            return obj;
        }
    }

    /**
     * Binds the name to the specified remote object.
     * @exception RemoteException If remote operation failed.
     * @exception AlreadyBoundException If name is already bound.
     */
    public void bind(String name, Remote obj)
        throws RemoteException, AlreadyBoundException, AccessException
    {
        checkAccess("Registry.bind");
        synchronized (bindings) {
            Remote curr = bindings.get(name);
            if (curr != null)
                throw new AlreadyBoundException(name);
            bindings.put(name, obj);
        }
    }

    /**
     * Unbind the name.
     * @exception RemoteException If remote operation failed.
     * @exception NotBound If name is not currently bound.
     */
    public void unbind(String name)
        throws RemoteException, NotBoundException, AccessException
    {
        checkAccess("Registry.unbind");
        synchronized (bindings) {
            Remote obj = bindings.get(name);
            if (obj == null)
                throw new NotBoundException(name);
            bindings.remove(name);
        }
    }

    /**
     * Rebind the name to a new object, replaces any existing binding.
     * @exception RemoteException If remote operation failed.
     */
    public void rebind(String name, Remote obj)
        throws RemoteException, AccessException
    {
        checkAccess("Registry.rebind");
        bindings.put(name, obj);
    }

    /**
     * Returns an enumeration of the names in the registry.
     * @exception RemoteException If remote operation failed.
     */
    public String[] list()
        throws RemoteException
    {
        String[] names;
        synchronized (bindings) {
            int i = bindings.size();
            names = new String[i];
            Enumeration<String> enum_ = bindings.keys();
            while ((--i) >= 0)
                names[i] = enum_.nextElement();
        }
        return names;
    }

    /**
     * Check that the caller has access to perform indicated operation.
     * The client must be on same the same host as this server.
     */
    public static void checkAccess(String op) throws AccessException {

        try {
            /*
             * Get client host that this registry operation was made from.
             */
            final String clientHostName = getClientHost();
            InetAddress clientHost;

            try {
                clientHost = java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedExceptionAction<InetAddress>() {
                        public InetAddress run()
                            throws java.net.UnknownHostException
                        {
                            return InetAddress.getByName(clientHostName);
                        }
                    });
            } catch (PrivilegedActionException pae) {
                throw (java.net.UnknownHostException) pae.getException();
            }

            // if client not yet seen, make sure client allowed access
            if (allowedAccessCache.get(clientHost) == null) {

                if (clientHost.isAnyLocalAddress()) {
                    throw new AccessException(
                        "Registry." + op + " disallowed; origin unknown");
                }

                try {
                    final InetAddress finalClientHost = clientHost;

                    java.security.AccessController.doPrivileged(
                        new java.security.PrivilegedExceptionAction<Void>() {
                            public Void run() throws java.io.IOException {
                                /*
                                 * if a ServerSocket can be bound to the client's
                                 * address then that address must be local
                                 */
                                (new ServerSocket(0, 10, finalClientHost)).close();
                                allowedAccessCache.put(finalClientHost,
                                                       finalClientHost);
                                return null;
                            }
                    });
                } catch (PrivilegedActionException pae) {
                    // must have been an IOException

                    throw new AccessException(
                        "Registry." + op + " disallowed; origin " +
                        clientHost + " is non-local host");
                }
            }
        } catch (ServerNotActiveException ex) {
            /*
             * Local call from this VM: allow access.
             */
        } catch (java.net.UnknownHostException ex) {
            throw new AccessException("Registry." + op +
                                      " disallowed; origin is unknown host");
        }
    }

    public static ObjID getID() {
        return id;
    }

    /**
     * Retrieves text resources from the locale-specific properties file.
     */
    private static String getTextResource(String key) {
        if (resources == null) {
            try {
                resources = ResourceBundle.getBundle(
                    "sun.rmi.registry.resources.rmiregistry");
            } catch (MissingResourceException mre) {
            }
            if (resources == null) {
                // throwing an Error is a bit extreme, methinks
                return ("[missing resource file: " + key + "]");
            }
        }

        String val = null;
        try {
            val = resources.getString(key);
        } catch (MissingResourceException mre) {
        }

        if (val == null) {
            return ("[missing resource: " + key + "]");
        } else {
            return (val);
        }
    }

    /**
     * Main program to start a registry. <br>
     * The port number can be specified on the command line.
     */
    public static void main(String args[])
    {
        // Create and install the security manager if one is not installed
        // already.
        if (System.getSecurityManager() == null) {
            System.setSecurityManager(new RMISecurityManager());
        }

        try {
            /*
             * Fix bugid 4147561: When JDK tools are executed, the value of
             * the CLASSPATH environment variable for the shell in which they
             * were invoked is no longer incorporated into the application
             * class path; CLASSPATH's only effect is to be the value of the
             * system property "env.class.path".  To preserve the previous
             * (JDK1.1 and JDK1.2beta3) behavior of this tool, however, its
             * CLASSPATH should still be considered when resolving classes
             * being unmarshalled.  To effect this old behavior, a class
             * loader that loads from the file path specified in the
             * "env.class.path" property is created and set to be the context
             * class loader before the remote object is exported.
             */
            String envcp = System.getProperty("env.class.path");
            if (envcp == null) {
                envcp = ".";            // preserve old default behavior
            }
            URL[] urls = sun.misc.URLClassPath.pathToURLs(envcp);
            ClassLoader cl = new URLClassLoader(urls);

            /*
             * Fix bugid 4242317: Classes defined by this class loader should
             * be annotated with the value of the "java.rmi.server.codebase"
             * property, not the "file:" URLs for the CLASSPATH elements.
             */
            sun.rmi.server.LoaderHandler.registerCodebaseLoader(cl);

            Thread.currentThread().setContextClassLoader(cl);

            final int regPort = (args.length >= 1) ? Integer.parseInt(args[0])
                                                   : Registry.REGISTRY_PORT;
            try {
                registry = AccessController.doPrivileged(
                    new PrivilegedExceptionAction<RegistryImpl>() {
                        public RegistryImpl run() throws RemoteException {
                            return new RegistryImpl(regPort);
                        }
                    }, getAccessControlContext(regPort));
            } catch (PrivilegedActionException ex) {
                throw (RemoteException) ex.getException();
            }

            // prevent registry from exiting
            while (true) {
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                }
            }
        } catch (NumberFormatException e) {
            System.err.println(MessageFormat.format(
                getTextResource("rmiregistry.port.badnumber"),
                args[0] ));
            System.err.println(MessageFormat.format(
                getTextResource("rmiregistry.usage"),
                "rmiregistry" ));
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(1);
    }

    /**
     * Generates an AccessControlContext with minimal permissions.
     * The approach used here is taken from the similar method
     * getAccessControlContext() in the sun.applet.AppletPanel class.
     */
    private static AccessControlContext getAccessControlContext(int port) {
        // begin with permissions granted to all code in current policy
        PermissionCollection perms = AccessController.doPrivileged(
            new java.security.PrivilegedAction<PermissionCollection>() {
                public PermissionCollection run() {
                    CodeSource codesource = new CodeSource(null,
                        (java.security.cert.Certificate[]) null);
                    Policy p = java.security.Policy.getPolicy();
                    if (p != null) {
                        return p.getPermissions(codesource);
                    } else {
                        return new Permissions();
                    }
                }
            });

        /*
         * Anyone can connect to the registry and the registry can connect
         * to and possibly download stubs from anywhere. Downloaded stubs and
         * related classes themselves are more tightly limited by RMI.
         */
        perms.add(new SocketPermission("*", "connect,accept"));
        perms.add(new SocketPermission("localhost:"+port, "listen,accept"));

        perms.add(new RuntimePermission("accessClassInPackage.sun.jvmstat.*"));
        perms.add(new RuntimePermission("accessClassInPackage.sun.jvm.hotspot.*"));

        perms.add(new FilePermission("<<ALL FILES>>", "read"));

        /*
         * Create an AccessControlContext that consists of a single
         * protection domain with only the permissions calculated above.
         */
        ProtectionDomain pd = new ProtectionDomain(
            new CodeSource(null,
                (java.security.cert.Certificate[]) null), perms);
        return new AccessControlContext(new ProtectionDomain[] { pd });
    }
}
