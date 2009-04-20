/*
 * Copyright 2000-2007 Sun Microsystems, Inc.  All Rights Reserved.
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


package java.util.logging;

import java.io.*;
import java.util.*;
import java.security.*;
import java.lang.ref.WeakReference;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.net.URL;
import sun.security.action.GetPropertyAction;

/**
 * There is a single global LogManager object that is used to
 * maintain a set of shared state about Loggers and log services.
 * <p>
 * This LogManager object:
 * <ul>
 * <li> Manages a hierarchical namespace of Logger objects.  All
 *      named Loggers are stored in this namespace.
 * <li> Manages a set of logging control properties.  These are
 *      simple key-value pairs that can be used by Handlers and
 *      other logging objects to configure themselves.
 * </ul>
 * <p>
 * The global LogManager object can be retrieved using LogManager.getLogManager().
 * The LogManager object is created during class initialization and
 * cannot subsequently be changed.
 * <p>
 * At startup the LogManager class is located using the
 * java.util.logging.manager system property.
 * <p>
 * By default, the LogManager reads its initial configuration from
 * a properties file "lib/logging.properties" in the JRE directory.
 * If you edit that property file you can change the default logging
 * configuration for all uses of that JRE.
 * <p>
 * In addition, the LogManager uses two optional system properties that
 * allow more control over reading the initial configuration:
 * <ul>
 * <li>"java.util.logging.config.class"
 * <li>"java.util.logging.config.file"
 * </ul>
 * These two properties may be set via the Preferences API, or as
 * command line property definitions to the "java" command, or as
 * system property definitions passed to JNI_CreateJavaVM.
 * <p>
 * If the "java.util.logging.config.class" property is set, then the
 * property value is treated as a class name.  The given class will be
 * loaded, an object will be instantiated, and that object's constructor
 * is responsible for reading in the initial configuration.  (That object
 * may use other system properties to control its configuration.)  The
 * alternate configuration class can use <tt>readConfiguration(InputStream)</tt>
 * to define properties in the LogManager.
 * <p>
 * If "java.util.logging.config.class" property is <b>not</b> set,
 * then the "java.util.logging.config.file" system property can be used
 * to specify a properties file (in java.util.Properties format). The
 * initial logging configuration will be read from this file.
 * <p>
 * If neither of these properties is defined then, as described
 * above, the LogManager will read its initial configuration from
 * a properties file "lib/logging.properties" in the JRE directory.
 * <p>
 * The properties for loggers and Handlers will have names starting
 * with the dot-separated name for the handler or logger.
 * <p>
 * The global logging properties may include:
 * <ul>
 * <li>A property "handlers".  This defines a whitespace or comma separated
 * list of class names for handler classes to load and register as
 * handlers on the root Logger (the Logger named "").  Each class
 * name must be for a Handler class which has a default constructor.
 * Note that these Handlers may be created lazily, when they are
 * first used.
 *
 * <li>A property "&lt;logger&gt;.handlers". This defines a whitespace or
 * comma separated list of class names for handlers classes to
 * load and register as handlers to the specified logger. Each class
 * name must be for a Handler class which has a default constructor.
 * Note that these Handlers may be created lazily, when they are
 * first used.
 *
 * <li>A property "&lt;logger&gt;.useParentHandlers". This defines a boolean
 * value. By default every logger calls its parent in addition to
 * handling the logging message itself, this often result in messages
 * being handled by the root logger as well. When setting this property
 * to false a Handler needs to be configured for this logger otherwise
 * no logging messages are delivered.
 *
 * <li>A property "config".  This property is intended to allow
 * arbitrary configuration code to be run.  The property defines a
 * whitespace or comma separated list of class names.  A new instance will be
 * created for each named class.  The default constructor of each class
 * may execute arbitrary code to update the logging configuration, such as
 * setting logger levels, adding handlers, adding filters, etc.
 * </ul>
 * <p>
 * Note that all classes loaded during LogManager configuration are
 * first searched on the system class path before any user class path.
 * That includes the LogManager class, any config classes, and any
 * handler classes.
 * <p>
 * Loggers are organized into a naming hierarchy based on their
 * dot separated names.  Thus "a.b.c" is a child of "a.b", but
 * "a.b1" and a.b2" are peers.
 * <p>
 * All properties whose names end with ".level" are assumed to define
 * log levels for Loggers.  Thus "foo.level" defines a log level for
 * the logger called "foo" and (recursively) for any of its children
 * in the naming hierarchy.  Log Levels are applied in the order they
 * are defined in the properties file.  Thus level settings for child
 * nodes in the tree should come after settings for their parents.
 * The property name ".level" can be used to set the level for the
 * root of the tree.
 * <p>
 * All methods on the LogManager object are multi-thread safe.
 *
 * @since 1.4
*/

public class LogManager {
    // The global LogManager object
    private static LogManager manager;

    private final static Handler[] emptyHandlers = { };
    private Properties props = new Properties();
    private PropertyChangeSupport changes
                         = new PropertyChangeSupport(LogManager.class);
    private final static Level defaultLevel = Level.INFO;

    // Table of known loggers.  Maps names to Loggers.
    private Hashtable<String,WeakReference<Logger>> loggers =
        new Hashtable<String,WeakReference<Logger>>();
    // Tree of known loggers
    private LogNode root = new LogNode(null);
    private Logger rootLogger;

    // Have we done the primordial reading of the configuration file?
    // (Must be done after a suitable amount of java.lang.System
    // initialization has been done)
    private volatile boolean readPrimordialConfiguration;
    // Have we initialized global (root) handlers yet?
    // This gets set to false in readConfiguration
    private boolean initializedGlobalHandlers = true;
    // True if JVM death is imminent and the exit hook has been called.
    private boolean deathImminent;

    static {
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
                public Object run() {
                    String cname = null;
                    try {
                        cname = System.getProperty("java.util.logging.manager");
                        if (cname != null) {
                            try {
                                Class clz = ClassLoader.getSystemClassLoader().loadClass(cname);
                                manager = (LogManager) clz.newInstance();
                            } catch (ClassNotFoundException ex) {
                                Class clz = Thread.currentThread().getContextClassLoader().loadClass(cname);
                                manager = (LogManager) clz.newInstance();
                            }
                        }
                    } catch (Exception ex) {
                        System.err.println("Could not load Logmanager \"" + cname + "\"");
                        ex.printStackTrace();
                    }
                    if (manager == null) {
                        manager = new LogManager();
                    }

                    // Create and retain Logger for the root of the namespace.
                    manager.rootLogger = manager.new RootLogger();
                    manager.addLogger(manager.rootLogger);

                    // Adding the global Logger. Doing so in the Logger.<clinit>
                    // would deadlock with the LogManager.<clinit>.
                    Logger.global.setLogManager(manager);
                    manager.addLogger(Logger.global);

                    // We don't call readConfiguration() here, as we may be running
                    // very early in the JVM startup sequence.  Instead readConfiguration
                    // will be called lazily in getLogManager().
                    return null;
                }
            });
    }


    // This private class is used as a shutdown hook.
    // It does a "reset" to close all open handlers.
    private class Cleaner extends Thread {

        private Cleaner() {
            /* Set context class loader to null in order to avoid
             * keeping a strong reference to an application classloader.
             */
            this.setContextClassLoader(null);
        }

        public void run() {
            // This is to ensure the LogManager.<clinit> is completed
            // before synchronized block. Otherwise deadlocks are possible.
            LogManager mgr = manager;

            // If the global handlers haven't been initialized yet, we
            // don't want to initialize them just so we can close them!
            synchronized (LogManager.this) {
                // Note that death is imminent.
                deathImminent = true;
                initializedGlobalHandlers = true;
            }

            // Do a reset to close all active handlers.
            reset();
        }
    }


    /**
     * Protected constructor.  This is protected so that container applications
     * (such as J2EE containers) can subclass the object.  It is non-public as
     * it is intended that there only be one LogManager object, whose value is
     * retrieved by calling Logmanager.getLogManager.
     */
    protected LogManager() {
        // Add a shutdown hook to close the global handlers.
        try {
            Runtime.getRuntime().addShutdownHook(new Cleaner());
        } catch (IllegalStateException e) {
            // If the VM is already shutting down,
            // We do not need to register shutdownHook.
        }
    }

    /**
     * Return the global LogManager object.
     */
    public static LogManager getLogManager() {
        if (manager != null) {
            manager.readPrimordialConfiguration();
        }
        return manager;
    }

    private void readPrimordialConfiguration() {
        if (!readPrimordialConfiguration) {
            synchronized (this) {
                if (!readPrimordialConfiguration) {
                    // If System.in/out/err are null, it's a good
                    // indication that we're still in the
                    // bootstrapping phase
                    if (System.out == null) {
                        return;
                    }
                    readPrimordialConfiguration = true;
                    try {
                        AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                                public Object run() throws Exception {
                                    readConfiguration();
                                    return null;
                                }
                            });
                    } catch (Exception ex) {
                        // System.err.println("Can't read logging configuration:");
                        // ex.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Adds an event listener to be invoked when the logging
     * properties are re-read. Adding multiple instances of
     * the same event Listener results in multiple entries
     * in the property event listener table.
     *
     * @param l  event listener
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have LoggingPermission("control").
     * @exception NullPointerException if the PropertyChangeListener is null.
     */
    public void addPropertyChangeListener(PropertyChangeListener l) throws SecurityException {
        if (l == null) {
            throw new NullPointerException();
        }
        checkAccess();
        changes.addPropertyChangeListener(l);
    }

    /**
     * Removes an event listener for property change events.
     * If the same listener instance has been added to the listener table
     * through multiple invocations of <CODE>addPropertyChangeListener</CODE>,
     * then an equivalent number of
     * <CODE>removePropertyChangeListener</CODE> invocations are required to remove
     * all instances of that listener from the listener table.
     * <P>
     * Returns silently if the given listener is not found.
     *
     * @param l  event listener (can be null)
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have LoggingPermission("control").
     */
    public void removePropertyChangeListener(PropertyChangeListener l) throws SecurityException {
        checkAccess();
        changes.removePropertyChangeListener(l);
    }

    // Package-level method.
    // Find or create a specified logger instance. If a logger has
    // already been created with the given name it is returned.
    // Otherwise a new logger instance is created and registered
    // in the LogManager global namespace.
    synchronized Logger demandLogger(String name) {
        Logger result = getLogger(name);
        if (result == null) {
            result = new Logger(name, null);
            addLogger(result);
            result = getLogger(name);
        }
        return result;
    }

    // If logger.getUseParentHandlers() returns 'true' and any of the logger's
    // parents have levels or handlers defined, make sure they are instantiated.
    private void processParentHandlers(Logger logger, String name) {
        int ix = 1;
        for (;;) {
            int ix2 = name.indexOf(".", ix);
            if (ix2 < 0) {
                break;
            }
            String pname = name.substring(0,ix2);

            if (getProperty(pname+".level")    != null ||
                getProperty(pname+".handlers") != null) {
                // This pname has a level/handlers definition.
                // Make sure it exists.
                demandLogger(pname);
            }
            ix = ix2+1;
        }
    }

    // Add new per logger handlers.
    // We need to raise privilege here. All our decisions will
    // be made based on the logging configuration, which can
    // only be modified by trusted code.
    private void loadLoggerHandlers(final Logger logger, final String name,
                                    final String handlersPropertyName) {
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            public Object run() {
                if (logger != rootLogger) {
                    boolean useParent = getBooleanProperty(name + ".useParentHandlers", true);
                    if (!useParent) {
                        logger.setUseParentHandlers(false);
                    }
                }

                String names[] = parseClassNames(handlersPropertyName);
                for (int i = 0; i < names.length; i++) {
                    String word = names[i];
                    try {
                        Class   clz = ClassLoader.getSystemClassLoader().loadClass(word);
                        Handler hdl = (Handler) clz.newInstance();
                        try {
                            // Check if there is a property defining the
                            // this handler's level.
                            String levs = getProperty(word + ".level");
                            if (levs != null) {
                                hdl.setLevel(Level.parse(levs));
                            }
                        } catch (Exception ex) {
                            System.err.println("Can't set level for " + word);
                            // Probably a bad level. Drop through.
                        }
                        // Add this Handler to the logger
                        logger.addHandler(hdl);
                    } catch (Exception ex) {
                        System.err.println("Can't load log handler \"" + word + "\"");
                        System.err.println("" + ex);
                        ex.printStackTrace();
                    }
                }
                return null;
            }});
    }

    /**
     * Add a named logger.  This does nothing and returns false if a logger
     * with the same name is already registered.
     * <p>
     * The Logger factory methods call this method to register each
     * newly created Logger.
     * <p>
     * The application should retain its own reference to the Logger
     * object to avoid it being garbage collected.  The LogManager
     * may only retain a weak reference.
     *
     * @param   logger the new logger.
     * @return  true if the argument logger was registered successfully,
     *          false if a logger of that name already exists.
     * @exception NullPointerException if the logger name is null.
     */
    public synchronized boolean addLogger(Logger logger) {
        final String name = logger.getName();
        if (name == null) {
            throw new NullPointerException();
        }

        WeakReference<Logger> ref = loggers.get(name);
        if (ref != null) {
            if (ref.get() == null) {
                // Hashtable holds stale weak reference
                // to a logger which has been GC-ed.
                // Allow to register new one.
                loggers.remove(name);
            } else {
                // We already have a registered logger with the given name.
                return false;
            }
        }

        // We're adding a new logger.
        // Note that we are creating a weak reference here.
        loggers.put(name, new WeakReference<Logger>(logger));

        // Apply any initial level defined for the new logger.
        Level level = getLevelProperty(name+".level", null);
        if (level != null) {
            doSetLevel(logger, level);
        }

        // Do we have a per logger handler too?
        // Note: this will add a 200ms penalty
        loadLoggerHandlers(logger, name, name+".handlers");
        processParentHandlers(logger, name);

        // Find the new node and its parent.
        LogNode node = findNode(name);
        node.loggerRef = new WeakReference<Logger>(logger);
        Logger parent = null;
        LogNode nodep = node.parent;
        while (nodep != null) {
            WeakReference<Logger> nodeRef = nodep.loggerRef;
            if (nodeRef != null) {
                parent = nodeRef.get();
                if (parent != null) {
                    break;
                }
            }
            nodep = nodep.parent;
        }

        if (parent != null) {
            doSetParent(logger, parent);
        }
        // Walk over the children and tell them we are their new parent.
        node.walkAndSetParent(logger);

        return true;
    }


    // Private method to set a level on a logger.
    // If necessary, we raise privilege before doing the call.
    private static void doSetLevel(final Logger logger, final Level level) {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null) {
            // There is no security manager, so things are easy.
            logger.setLevel(level);
            return;
        }
        // There is a security manager.  Raise privilege before
        // calling setLevel.
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            public Object run() {
                logger.setLevel(level);
                return null;
            }});
    }



    // Private method to set a parent on a logger.
    // If necessary, we raise privilege before doing the setParent call.
    private static void doSetParent(final Logger logger, final Logger parent) {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null) {
            // There is no security manager, so things are easy.
            logger.setParent(parent);
            return;
        }
        // There is a security manager.  Raise privilege before
        // calling setParent.
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            public Object run() {
                logger.setParent(parent);
                return null;
            }});
    }

    // Find a node in our tree of logger nodes.
    // If necessary, create it.
    private LogNode findNode(String name) {
        if (name == null || name.equals("")) {
            return root;
        }
        LogNode node = root;
        while (name.length() > 0) {
            int ix = name.indexOf(".");
            String head;
            if (ix > 0) {
                head = name.substring(0,ix);
                name = name.substring(ix+1);
            } else {
                head = name;
                name = "";
            }
            if (node.children == null) {
                node.children = new HashMap<String,LogNode>();
            }
            LogNode child = node.children.get(head);
            if (child == null) {
                child = new LogNode(node);
                node.children.put(head, child);
            }
            node = child;
        }
        return node;
    }

    /**
     * Method to find a named logger.
     * <p>
     * Note that since untrusted code may create loggers with
     * arbitrary names this method should not be relied on to
     * find Loggers for security sensitive logging.
     * <p>
     * @param name name of the logger
     * @return  matching logger or null if none is found
     */
    public synchronized Logger getLogger(String name) {
        WeakReference<Logger> ref = loggers.get(name);
        if (ref == null) {
            return null;
        }
        Logger logger = ref.get();
        if (logger == null) {
            // Hashtable holds stale weak reference
            // to a logger which has been GC-ed.
            loggers.remove(name);
        }
        return logger;
    }

    /**
     * Get an enumeration of known logger names.
     * <p>
     * Note:  Loggers may be added dynamically as new classes are loaded.
     * This method only reports on the loggers that are currently registered.
     * <p>
     * @return  enumeration of logger name strings
     */
    public synchronized Enumeration<String> getLoggerNames() {
        return loggers.keys();
    }

    /**
     * Reinitialize the logging properties and reread the logging configuration.
     * <p>
     * The same rules are used for locating the configuration properties
     * as are used at startup.  So normally the logging properties will
     * be re-read from the same file that was used at startup.
     * <P>
     * Any log level definitions in the new configuration file will be
     * applied using Logger.setLevel(), if the target Logger exists.
     * <p>
     * A PropertyChangeEvent will be fired after the properties are read.
     *
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have LoggingPermission("control").
     * @exception  IOException if there are IO problems reading the configuration.
     */
    public void readConfiguration() throws IOException, SecurityException {
        checkAccess();

        // if a configuration class is specified, load it and use it.
        String cname = System.getProperty("java.util.logging.config.class");
        if (cname != null) {
            try {
                // Instantiate the named class.  It is its constructor's
                // responsibility to initialize the logging configuration, by
                // calling readConfiguration(InputStream) with a suitable stream.
                try {
                    Class clz = ClassLoader.getSystemClassLoader().loadClass(cname);
                    clz.newInstance();
                    return;
                } catch (ClassNotFoundException ex) {
                    Class clz = Thread.currentThread().getContextClassLoader().loadClass(cname);
                    clz.newInstance();
                    return;
                }
            } catch (Exception ex) {
                System.err.println("Logging configuration class \"" + cname + "\" failed");
                System.err.println("" + ex);
                // keep going and useful config file.
            }
        }

        String fname = System.getProperty("java.util.logging.config.file");
        if (fname == null) {
            fname = System.getProperty("java.home");
            if (fname == null) {
                throw new Error("Can't find java.home ??");
            }
            File f = new File(fname, "lib");
            f = new File(f, "logging.properties");
            fname = f.getCanonicalPath();
        }
        InputStream in = new FileInputStream(fname);
        BufferedInputStream bin = new BufferedInputStream(in);
        try {
            readConfiguration(bin);
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    /**
     * Reset the logging configuration.
     * <p>
     * For all named loggers, the reset operation removes and closes
     * all Handlers and (except for the root logger) sets the level
     * to null.  The root logger's level is set to Level.INFO.
     *
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have LoggingPermission("control").
     */

    public void reset() throws SecurityException {
        checkAccess();
        synchronized (this) {
            props = new Properties();
            // Since we are doing a reset we no longer want to initialize
            // the global handlers, if they haven't been initialized yet.
            initializedGlobalHandlers = true;
        }
        Enumeration enum_ = getLoggerNames();
        while (enum_.hasMoreElements()) {
            String name = (String)enum_.nextElement();
            resetLogger(name);
        }
    }


    // Private method to reset an individual target logger.
    private void resetLogger(String name) {
        Logger logger = getLogger(name);
        if (logger == null) {
            return;
        }
        // Close all the Logger's handlers.
        Handler[] targets = logger.getHandlers();
        for (int i = 0; i < targets.length; i++) {
            Handler h = targets[i];
            logger.removeHandler(h);
            try {
                h.close();
            } catch (Exception ex) {
                // Problems closing a handler?  Keep going...
            }
        }
        if (name != null && name.equals("")) {
            // This is the root logger.
            logger.setLevel(defaultLevel);
        } else {
            logger.setLevel(null);
        }
    }

    // get a list of whitespace separated classnames from a property.
    private String[] parseClassNames(String propertyName) {
        String hands = getProperty(propertyName);
        if (hands == null) {
            return new String[0];
        }
        hands = hands.trim();
        int ix = 0;
        Vector<String> result = new Vector<String>();
        while (ix < hands.length()) {
            int end = ix;
            while (end < hands.length()) {
                if (Character.isWhitespace(hands.charAt(end))) {
                    break;
                }
                if (hands.charAt(end) == ',') {
                    break;
                }
                end++;
            }
            String word = hands.substring(ix, end);
            ix = end+1;
            word = word.trim();
            if (word.length() == 0) {
                continue;
            }
            result.add(word);
        }
        return result.toArray(new String[result.size()]);
    }

    /**
     * Reinitialize the logging properties and reread the logging configuration
     * from the given stream, which should be in java.util.Properties format.
     * A PropertyChangeEvent will be fired after the properties are read.
     * <p>
     * Any log level definitions in the new configuration file will be
     * applied using Logger.setLevel(), if the target Logger exists.
     *
     * @param ins       stream to read properties from
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have LoggingPermission("control").
     * @exception  IOException if there are problems reading from the stream.
     */
    public void readConfiguration(InputStream ins) throws IOException, SecurityException {
        checkAccess();
        reset();

        // Load the properties
        props.load(ins);
        // Instantiate new configuration objects.
        String names[] = parseClassNames("config");

        for (int i = 0; i < names.length; i++) {
            String word = names[i];
            try {
                Class clz = ClassLoader.getSystemClassLoader().loadClass(word);
                clz.newInstance();
            } catch (Exception ex) {
                System.err.println("Can't load config class \"" + word + "\"");
                System.err.println("" + ex);
                // ex.printStackTrace();
            }
        }

        // Set levels on any pre-existing loggers, based on the new properties.
        setLevelsOnExistingLoggers();

        // Notify any interested parties that our properties have changed.
        changes.firePropertyChange(null, null, null);

        // Note that we need to reinitialize global handles when
        // they are first referenced.
        synchronized (this) {
            initializedGlobalHandlers = false;
        }
    }

    /**
     * Get the value of a logging property.
     * The method returns null if the property is not found.
     * @param name      property name
     * @return          property value
     */
    public String getProperty(String name) {
        return props.getProperty(name);
    }

    // Package private method to get a String property.
    // If the property is not defined we return the given
    // default value.
    String getStringProperty(String name, String defaultValue) {
        String val = getProperty(name);
        if (val == null) {
            return defaultValue;
        }
        return val.trim();
    }

    // Package private method to get an integer property.
    // If the property is not defined or cannot be parsed
    // we return the given default value.
    int getIntProperty(String name, int defaultValue) {
        String val = getProperty(name);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    // Package private method to get a boolean property.
    // If the property is not defined or cannot be parsed
    // we return the given default value.
    boolean getBooleanProperty(String name, boolean defaultValue) {
        String val = getProperty(name);
        if (val == null) {
            return defaultValue;
        }
        val = val.toLowerCase();
        if (val.equals("true") || val.equals("1")) {
            return true;
        } else if (val.equals("false") || val.equals("0")) {
            return false;
        }
        return defaultValue;
    }

    // Package private method to get a Level property.
    // If the property is not defined or cannot be parsed
    // we return the given default value.
    Level getLevelProperty(String name, Level defaultValue) {
        String val = getProperty(name);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Level.parse(val.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    // Package private method to get a filter property.
    // We return an instance of the class named by the "name"
    // property. If the property is not defined or has problems
    // we return the defaultValue.
    Filter getFilterProperty(String name, Filter defaultValue) {
        String val = getProperty(name);
        try {
            if (val != null) {
                Class clz = ClassLoader.getSystemClassLoader().loadClass(val);
                return (Filter) clz.newInstance();
            }
        } catch (Exception ex) {
            // We got one of a variety of exceptions in creating the
            // class or creating an instance.
            // Drop through.
        }
        // We got an exception.  Return the defaultValue.
        return defaultValue;
    }


    // Package private method to get a formatter property.
    // We return an instance of the class named by the "name"
    // property. If the property is not defined or has problems
    // we return the defaultValue.
    Formatter getFormatterProperty(String name, Formatter defaultValue) {
        String val = getProperty(name);
        try {
            if (val != null) {
                Class clz = ClassLoader.getSystemClassLoader().loadClass(val);
                return (Formatter) clz.newInstance();
            }
        } catch (Exception ex) {
            // We got one of a variety of exceptions in creating the
            // class or creating an instance.
            // Drop through.
        }
        // We got an exception.  Return the defaultValue.
        return defaultValue;
    }

    // Private method to load the global handlers.
    // We do the real work lazily, when the global handlers
    // are first used.
    private synchronized void initializeGlobalHandlers() {
        if (initializedGlobalHandlers) {
            return;
        }

        initializedGlobalHandlers = true;

        if (deathImminent) {
            // Aaargh...
            // The VM is shutting down and our exit hook has been called.
            // Avoid allocating global handlers.
            return;
        }
        loadLoggerHandlers(rootLogger, null, "handlers");
    }


    private Permission ourPermission = new LoggingPermission("control", null);

    /**
     * Check that the current context is trusted to modify the logging
     * configuration.  This requires LoggingPermission("control").
     * <p>
     * If the check fails we throw a SecurityException, otherwise
     * we return normally.
     *
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have LoggingPermission("control").
     */
    public void checkAccess() throws SecurityException {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null) {
            return;
        }
        sm.checkPermission(ourPermission);
    }

    // Nested class to represent a node in our tree of named loggers.
    private static class LogNode {
        HashMap<String,LogNode> children;
        WeakReference<Logger> loggerRef;
        LogNode parent;

        LogNode(LogNode parent) {
            this.parent = parent;
        }

        // Recursive method to walk the tree below a node and set
        // a new parent logger.
        void walkAndSetParent(Logger parent) {
            if (children == null) {
                return;
            }
            Iterator<LogNode> values = children.values().iterator();
            while (values.hasNext()) {
                LogNode node = values.next();
                WeakReference<Logger> ref = node.loggerRef;
                Logger logger = (ref == null) ? null : ref.get();
                if (logger == null) {
                    node.walkAndSetParent(parent);
                } else {
                    doSetParent(logger, parent);
                }
            }
        }
    }

    // We use a subclass of Logger for the root logger, so
    // that we only instantiate the global handlers when they
    // are first needed.
    private class RootLogger extends Logger {

        private RootLogger() {
            super("", null);
            setLevel(defaultLevel);
        }

        public void log(LogRecord record) {
            // Make sure that the global handlers have been instantiated.
            initializeGlobalHandlers();
            super.log(record);
        }

        public void addHandler(Handler h) {
            initializeGlobalHandlers();
            super.addHandler(h);
        }

        public void removeHandler(Handler h) {
            initializeGlobalHandlers();
            super.removeHandler(h);
        }

        public Handler[] getHandlers() {
            initializeGlobalHandlers();
            return super.getHandlers();
        }
    }


    // Private method to be called when the configuration has
    // changed to apply any level settings to any pre-existing loggers.
    synchronized private void setLevelsOnExistingLoggers() {
        Enumeration enum_ = props.propertyNames();
        while (enum_.hasMoreElements()) {
            String key = (String)enum_.nextElement();
            if (!key.endsWith(".level")) {
                // Not a level definition.
                continue;
            }
            int ix = key.length() - 6;
            String name = key.substring(0, ix);
            Level level = getLevelProperty(key, null);
            if (level == null) {
                System.err.println("Bad level value for property: " + key);
                continue;
            }
            Logger l = getLogger(name);
            if (l == null) {
                continue;
            }
            l.setLevel(level);
        }
    }

    // Management Support
    private static LoggingMXBean loggingMXBean = null;
    /**
     * String representation of the
     * {@link javax.management.ObjectName} for {@link LoggingMXBean}.
     * @since 1.5
     */
    public final static String LOGGING_MXBEAN_NAME
        = "java.util.logging:type=Logging";

    /**
     * Returns <tt>LoggingMXBean</tt> for managing loggers.
     * The <tt>LoggingMXBean</tt> can also obtained from the
     * {@link java.lang.management.ManagementFactory#getPlatformMBeanServer
     * platform <tt>MBeanServer</tt>} method.
     *
     * @return a {@link LoggingMXBean} object.
     *
     * @see java.lang.management.ManagementFactory
     * @since 1.5
     */
    public static synchronized LoggingMXBean  getLoggingMXBean() {
        if (loggingMXBean == null) {
            loggingMXBean =  new Logging();
        }
        return loggingMXBean;
    }

}
