/*
 * Copyright (c) 1996, 2006, Oracle and/or its affiliates. All rights reserved.
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

package java.sql;

import java.util.Iterator;
import java.sql.Driver;
import java.util.ServiceLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;


/**
 * <P>The basic service for managing a set of JDBC drivers.<br>
 * <B>NOTE:</B> The {@link <code>DataSource</code>} interface, new in the
 * JDBC 2.0 API, provides another way to connect to a data source.
 * The use of a <code>DataSource</code> object is the preferred means of
 * connecting to a data source.
 *
 * <P>As part of its initialization, the <code>DriverManager</code> class will
 * attempt to load the driver classes referenced in the "jdbc.drivers"
 * system property. This allows a user to customize the JDBC Drivers
 * used by their applications. For example in your
 * ~/.hotjava/properties file you might specify:
 * <pre>
 * <CODE>jdbc.drivers=foo.bah.Driver:wombat.sql.Driver:bad.taste.ourDriver</CODE>
 * </pre>
 *<P> The <code>DriverManager</code> methods <code>getConnection</code> and
 * <code>getDrivers</code> have been enhanced to support the Java Standard Edition
 * <a href="../../../technotes/guides/jar/jar.html#Service%20Provider">Service Provider</a> mechanism. JDBC 4.0 Drivers must
 * include the file <code>META-INF/services/java.sql.Driver</code>. This file contains the name of the JDBC drivers
 * implementation of <code>java.sql.Driver</code>.  For example, to load the <code>my.sql.Driver</code> class,
 * the <code>META-INF/services/java.sql.Driver</code> file would contain the entry:
 * <pre>
 * <code>my.sql.Driver</code>
 * </pre>
 *
 * <P>Applications no longer need to explictly load JDBC drivers using <code>Class.forName()</code>. Existing programs
 * which currently load JDBC drivers using <code>Class.forName()</code> will continue to work without
 * modification.
 *
 * <P>When the method <code>getConnection</code> is called,
 * the <code>DriverManager</code> will attempt to
 * locate a suitable driver from amongst those loaded at
 * initialization and those loaded explicitly using the same classloader
 * as the current applet or application.
 *
 * <P>
 * Starting with the Java 2 SDK, Standard Edition, version 1.3, a
 * logging stream can be set only if the proper
 * permission has been granted.  Normally this will be done with
 * the tool PolicyTool, which can be used to grant <code>permission
 * java.sql.SQLPermission "setLog"</code>.
 * @see Driver
 * @see Connection
 */
public class DriverManager {


    /**
     * The <code>SQLPermission</code> constant that allows the
     * setting of the logging stream.
     * @since 1.3
     */
    final static SQLPermission SET_LOG_PERMISSION =
        new SQLPermission("setLog");

    //--------------------------JDBC 2.0-----------------------------

    /**
     * Retrieves the log writer.
     *
     * The <code>getLogWriter</code> and <code>setLogWriter</code>
     * methods should be used instead
     * of the <code>get/setlogStream</code> methods, which are deprecated.
     * @return a <code>java.io.PrintWriter</code> object
     * @see #setLogWriter
     * @since 1.2
     */
    public static java.io.PrintWriter getLogWriter() {
            return logWriter;
    }

    /**
     * Sets the logging/tracing <code>PrintWriter</code> object
     * that is used by the <code>DriverManager</code> and all drivers.
     * <P>
     * There is a minor versioning problem created by the introduction
     * of the method <code>setLogWriter</code>.  The
     * method <code>setLogWriter</code> cannot create a <code>PrintStream</code> object
     * that will be returned by <code>getLogStream</code>---the Java platform does
     * not provide a backward conversion.  As a result, a new application
     * that uses <code>setLogWriter</code> and also uses a JDBC 1.0 driver that uses
     * <code>getLogStream</code> will likely not see debugging information written
     * by that driver.
     *<P>
     * Starting with the Java 2 SDK, Standard Edition, version 1.3 release, this method checks
     * to see that there is an <code>SQLPermission</code> object before setting
     * the logging stream.  If a <code>SecurityManager</code> exists and its
     * <code>checkPermission</code> method denies setting the log writer, this
     * method throws a <code>java.lang.SecurityException</code>.
     *
     * @param out the new logging/tracing <code>PrintStream</code> object;
     *      <code>null</code> to disable logging and tracing
     * @throws SecurityException
     *    if a security manager exists and its
     *    <code>checkPermission</code> method denies
     *    setting the log writer
     *
     * @see SecurityManager#checkPermission
     * @see #getLogWriter
     * @since 1.2
     */
    public static void setLogWriter(java.io.PrintWriter out) {

        SecurityManager sec = System.getSecurityManager();
        if (sec != null) {
            sec.checkPermission(SET_LOG_PERMISSION);
        }
            logStream = null;
            logWriter = out;
    }


    //---------------------------------------------------------------

    /**
     * Attempts to establish a connection to the given database URL.
     * The <code>DriverManager</code> attempts to select an appropriate driver from
     * the set of registered JDBC drivers.
     *
     * @param url a database url of the form
     * <code> jdbc:<em>subprotocol</em>:<em>subname</em></code>
     * @param info a list of arbitrary string tag/value pairs as
     * connection arguments; normally at least a "user" and
     * "password" property should be included
     * @return a Connection to the URL
     * @exception SQLException if a database access error occurs
     */
    public static Connection getConnection(String url,
        java.util.Properties info) throws SQLException {

        // Gets the classloader of the code that called this method, may
        // be null.
        ClassLoader callerCL = DriverManager.getCallerClassLoader();

        return (getConnection(url, info, callerCL));
    }

    /**
     * Attempts to establish a connection to the given database URL.
     * The <code>DriverManager</code> attempts to select an appropriate driver from
     * the set of registered JDBC drivers.
     *
     * @param url a database url of the form
     * <code>jdbc:<em>subprotocol</em>:<em>subname</em></code>
     * @param user the database user on whose behalf the connection is being
     *   made
     * @param password the user's password
     * @return a connection to the URL
     * @exception SQLException if a database access error occurs
     */
    public static Connection getConnection(String url,
        String user, String password) throws SQLException {
        java.util.Properties info = new java.util.Properties();

        // Gets the classloader of the code that called this method, may
        // be null.
        ClassLoader callerCL = DriverManager.getCallerClassLoader();

        if (user != null) {
            info.put("user", user);
        }
        if (password != null) {
            info.put("password", password);
        }

        return (getConnection(url, info, callerCL));
    }

    /**
     * Attempts to establish a connection to the given database URL.
     * The <code>DriverManager</code> attempts to select an appropriate driver from
     * the set of registered JDBC drivers.
     *
     * @param url a database url of the form
     *  <code> jdbc:<em>subprotocol</em>:<em>subname</em></code>
     * @return a connection to the URL
     * @exception SQLException if a database access error occurs
     */
    public static Connection getConnection(String url)
        throws SQLException {

        java.util.Properties info = new java.util.Properties();

        // Gets the classloader of the code that called this method, may
        // be null.
        ClassLoader callerCL = DriverManager.getCallerClassLoader();

        return (getConnection(url, info, callerCL));
    }

    /**
     * Attempts to locate a driver that understands the given URL.
     * The <code>DriverManager</code> attempts to select an appropriate driver from
     * the set of registered JDBC drivers.
     *
     * @param url a database URL of the form
     *     <code>jdbc:<em>subprotocol</em>:<em>subname</em></code>
     * @return a <code>Driver</code> object representing a driver
     * that can connect to the given URL
     * @exception SQLException if a database access error occurs
     */
    public static Driver getDriver(String url)
        throws SQLException {
        java.util.Vector drivers = null;

        println("DriverManager.getDriver(\"" + url + "\")");

        if (!initialized) {
            initialize();
        }

        synchronized (DriverManager.class){
            // use the read copy of the drivers vector
            drivers = readDrivers;
        }

        // Gets the classloader of the code that called this method, may
        // be null.
        ClassLoader callerCL = DriverManager.getCallerClassLoader();

        // Walk through the loaded drivers attempting to locate someone
        // who understands the given URL.
        for (int i = 0; i < drivers.size(); i++) {
            DriverInfo di = (DriverInfo)drivers.elementAt(i);
            // If the caller does not have permission to load the driver then
            // skip it.
            if ( getCallerClass(callerCL, di.driverClassName ) !=
                 di.driverClass ) {
                println("    skipping: " + di);
                continue;
            }
            try {
                println("    trying " + di);
                if (di.driver.acceptsURL(url)) {
                    // Success!
                    println("getDriver returning " + di);
                    return (di.driver);
                }
            } catch (SQLException ex) {
                // Drop through and try the next driver.
            }
        }

        println("getDriver: no suitable driver");
        throw new SQLException("No suitable driver", "08001");
    }


    /**
     * Registers the given driver with the <code>DriverManager</code>.
     * A newly-loaded driver class should call
     * the method <code>registerDriver</code> to make itself
     * known to the <code>DriverManager</code>.
     *
     * @param driver the new JDBC Driver that is to be registered with the
     *               <code>DriverManager</code>
     * @exception SQLException if a database access error occurs
     */
    public static synchronized void registerDriver(java.sql.Driver driver)
        throws SQLException {
        if (!initialized) {
            initialize();
        }

        DriverInfo di = new DriverInfo();

        di.driver = driver;
        di.driverClass = driver.getClass();
        di.driverClassName = di.driverClass.getName();

        // Not Required -- drivers.addElement(di);

        writeDrivers.addElement(di);
        println("registerDriver: " + di);

        /* update the read copy of drivers vector */
        readDrivers = (java.util.Vector) writeDrivers.clone();

    }

    /**
     * Drops a driver from the <code>DriverManager</code>'s list.
     *  Applets can only deregister drivers from their own classloaders.
     *
     * @param driver the JDBC Driver to drop
     * @exception SQLException if a database access error occurs
     */
    public static synchronized void deregisterDriver(Driver driver)
        throws SQLException {
        // Gets the classloader of the code that called this method,
        // may be null.
        ClassLoader callerCL = DriverManager.getCallerClassLoader();
        println("DriverManager.deregisterDriver: " + driver);

        // Walk through the loaded drivers.
        int i;
        DriverInfo di = null;
        for (i = 0; i < writeDrivers.size(); i++) {
            di = (DriverInfo)writeDrivers.elementAt(i);
            if (di.driver == driver) {
                break;
            }
        }
        // If we can't find the driver just return.
        if (i >= writeDrivers.size()) {
            println("    couldn't find driver to unload");
            return;
        }

        // If the caller does not have permission to load the driver then
        // throw a security exception.
        if (getCallerClass(callerCL, di.driverClassName ) != di.driverClass ) {
            throw new SecurityException();
        }

        // Remove the driver.  Other entries in drivers get shuffled down.
        writeDrivers.removeElementAt(i);

        /* update the read copy of drivers vector */
        readDrivers = (java.util.Vector) writeDrivers.clone();
    }

    /**
     * Retrieves an Enumeration with all of the currently loaded JDBC drivers
     * to which the current caller has access.
     *
     * <P><B>Note:</B> The classname of a driver can be found using
     * <CODE>d.getClass().getName()</CODE>
     *
     * @return the list of JDBC Drivers loaded by the caller's class loader
     */
    public static java.util.Enumeration<Driver> getDrivers() {
        java.util.Vector<Driver> result = new java.util.Vector<>();
        java.util.Vector drivers = null;

        if (!initialized) {
            initialize();
        }

        synchronized (DriverManager.class){
            // use the readcopy of drivers
            drivers  = readDrivers;
       }

        // Gets the classloader of the code that called this method, may
        // be null.
        ClassLoader callerCL = DriverManager.getCallerClassLoader();

        // Walk through the loaded drivers.
        for (int i = 0; i < drivers.size(); i++) {
            DriverInfo di = (DriverInfo)drivers.elementAt(i);
            // If the caller does not have permission to load the driver then
            // skip it.
            if ( getCallerClass(callerCL, di.driverClassName ) != di.driverClass ) {
                println("    skipping: " + di);
                continue;
            }
            result.addElement(di.driver);
        }

        return (result.elements());
    }


    /**
     * Sets the maximum time in seconds that a driver will wait
     * while attempting to connect to a database.
     *
     * @param seconds the login time limit in seconds; zero means there is no limit
     * @see #getLoginTimeout
     */
    public static void setLoginTimeout(int seconds) {
        loginTimeout = seconds;
    }

    /**
     * Gets the maximum time in seconds that a driver can wait
     * when attempting to log in to a database.
     *
     * @return the driver login time limit in seconds
     * @see #setLoginTimeout
     */
    public static int getLoginTimeout() {
        return (loginTimeout);
    }

    /**
     * Sets the logging/tracing PrintStream that is used
     * by the <code>DriverManager</code>
     * and all drivers.
     *<P>
     * In the Java 2 SDK, Standard Edition, version 1.3 release, this method checks
     * to see that there is an <code>SQLPermission</code> object before setting
     * the logging stream.  If a <code>SecurityManager</code> exists and its
     * <code>checkPermission</code> method denies setting the log writer, this
     * method throws a <code>java.lang.SecurityException</code>.
     *
     * @param out the new logging/tracing PrintStream; to disable, set to <code>null</code>
     * @deprecated
     * @throws SecurityException if a security manager exists and its
     *    <code>checkPermission</code> method denies setting the log stream
     *
     * @see SecurityManager#checkPermission
     * @see #getLogStream
     */
    public static void setLogStream(java.io.PrintStream out) {

        SecurityManager sec = System.getSecurityManager();
        if (sec != null) {
            sec.checkPermission(SET_LOG_PERMISSION);
        }

        logStream = out;
        if ( out != null )
            logWriter = new java.io.PrintWriter(out);
        else
            logWriter = null;
    }

    /**
     * Retrieves the logging/tracing PrintStream that is used by the <code>DriverManager</code>
     * and all drivers.
     *
     * @return the logging/tracing PrintStream; if disabled, is <code>null</code>
     * @deprecated
     * @see #setLogStream
     */
    public static java.io.PrintStream getLogStream() {
        return logStream;
    }

    /**
     * Prints a message to the current JDBC log stream.
     *
     * @param message a log or tracing message
     */
    public static void println(String message) {
        synchronized (logSync) {
            if (logWriter != null) {
                logWriter.println(message);

                // automatic flushing is never enabled, so we must do it ourselves
                logWriter.flush();
            }
        }
    }

    //------------------------------------------------------------------------

    // Returns the class object that would be created if the code calling the
    // driver manager had loaded the driver class, or null if the class
    // is inaccessible.
    private static Class getCallerClass(ClassLoader callerClassLoader,
                                        String driverClassName) {
        Class callerC = null;

        try {
            callerC = Class.forName(driverClassName, true, callerClassLoader);
        }
        catch (Exception ex) {
            callerC = null;           // being very careful
        }

        return callerC;
    }

    private static void loadInitialDrivers() {
        String drivers;
        try {
            drivers = (String)  AccessController.doPrivileged(new PrivilegedAction() {
                public Object run() {
                    return System.getProperty("jdbc.drivers");
                }
            });
        } catch (Exception ex) {
            drivers = null;
        }
        // If the driver is packaged as a Service Provider, load it.
        // Get all the drivers through the classloader
        // exposed as a java.sql.Driver.class service.
        // ServiceLoader.load() replaces the sun.misc.Providers()

        AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {

                ServiceLoader<Driver> loadedDrivers = ServiceLoader.load(Driver.class);
                Iterator driversIterator = loadedDrivers.iterator();

                /* Load these drivers, so that they can be instantiated.
                 * It may be the case that the driver class may not be there
                 * i.e. there may be a packaged driver with the service class
                 * as implementation of java.sql.Driver but the actual class
                 * may be missing. In that case a java.util.ServiceConfigurationError
                 * will be thrown at runtime by the VM trying to locate
                 * and load the service.
                 *
                 * Adding a try catch block to catch those runtime errors
                 * if driver not available in classpath but it's
                 * packaged as service and that service is there in classpath.
                 */
                try{
                    while(driversIterator.hasNext()) {
                        println(" Loading done by the java.util.ServiceLoader :  "+driversIterator.next());
                    }
                } catch(Throwable t) {
                // Do nothing
                }
                return null;
            }
        });

        println("DriverManager.initialize: jdbc.drivers = " + drivers);
        if (drivers == null) {
            return;
        }
        while (drivers.length() != 0) {
            int x = drivers.indexOf(':');
            String driver;
            if (x < 0) {
                driver = drivers;
                drivers = "";
            } else {
                driver = drivers.substring(0, x);
                drivers = drivers.substring(x+1);
            }
            if (driver.length() == 0) {
                continue;
            }
            try {
                println("DriverManager.Initialize: loading " + driver);
                Class.forName(driver, true,
                              ClassLoader.getSystemClassLoader());
            } catch (Exception ex) {
                println("DriverManager.Initialize: load failed: " + ex);
            }
        }
    }


    //  Worker method called by the public getConnection() methods.
    private static Connection getConnection(
        String url, java.util.Properties info, ClassLoader callerCL) throws SQLException {
        java.util.Vector drivers = null;
        /*
         * When callerCl is null, we should check the application's
         * (which is invoking this class indirectly)
         * classloader, so that the JDBC driver class outside rt.jar
         * can be loaded from here.
         */
        synchronized(DriverManager.class) {
          // synchronize loading of the correct classloader.
          if(callerCL == null) {
              callerCL = Thread.currentThread().getContextClassLoader();
           }
        }

        if(url == null) {
            throw new SQLException("The url cannot be null", "08001");
        }

        println("DriverManager.getConnection(\"" + url + "\")");

        if (!initialized) {
            initialize();
        }

        synchronized (DriverManager.class){
            // use the readcopy of drivers
            drivers = readDrivers;
        }

        // Walk through the loaded drivers attempting to make a connection.
        // Remember the first exception that gets raised so we can reraise it.
        SQLException reason = null;
        for (int i = 0; i < drivers.size(); i++) {
            DriverInfo di = (DriverInfo)drivers.elementAt(i);

            // If the caller does not have permission to load the driver then
            // skip it.
            if ( getCallerClass(callerCL, di.driverClassName ) != di.driverClass ) {
                println("    skipping: " + di);
                continue;
            }
            try {
                println("    trying " + di);
                Connection result = di.driver.connect(url, info);
                if (result != null) {
                    // Success!
                    println("getConnection returning " + di);
                    return (result);
                }
            } catch (SQLException ex) {
                if (reason == null) {
                    reason = ex;
                }
            }
        }

        // if we got here nobody could connect.
        if (reason != null)    {
            println("getConnection failed: " + reason);
            throw reason;
        }

        println("getConnection: no suitable driver found for "+ url);
        throw new SQLException("No suitable driver found for "+ url, "08001");
    }


    // Class initialization.
    static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        loadInitialDrivers();
        println("JDBC DriverManager initialized");
    }

    /* Prevent the DriverManager class from being instantiated. */
    private DriverManager(){}

    /* write copy of the drivers vector */
    private static java.util.Vector writeDrivers = new java.util.Vector();

    /* write copy of the drivers vector */
    private static java.util.Vector readDrivers = new java.util.Vector();

    private static int loginTimeout = 0;
    private static java.io.PrintWriter logWriter = null;
    private static java.io.PrintStream logStream = null;
    private static boolean initialized = false;

    private static Object logSync = new Object();

    /* Returns the caller's class loader, or null if none */
    private static native ClassLoader getCallerClassLoader();

}

// DriverInfo is a package-private support class.
class DriverInfo {
    Driver         driver;
    Class          driverClass;
    String         driverClassName;

    public String toString() {
        return ("driver[className=" + driverClassName + "," + driver + "]");
    }
}
