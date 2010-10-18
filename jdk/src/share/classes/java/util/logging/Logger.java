/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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


package java.util.logging;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.security.*;
import java.lang.ref.WeakReference;

/**
 * A Logger object is used to log messages for a specific
 * system or application component.  Loggers are normally named,
 * using a hierarchical dot-separated namespace.  Logger names
 * can be arbitrary strings, but they should normally be based on
 * the package name or class name of the logged component, such
 * as java.net or javax.swing.  In addition it is possible to create
 * "anonymous" Loggers that are not stored in the Logger namespace.
 * <p>
 * Logger objects may be obtained by calls on one of the getLogger
 * factory methods.  These will either create a new Logger or
 * return a suitable existing Logger. It is important to note that
 * the Logger returned by one of the {@code getLogger} factory methods
 * may be garbage collected at any time if a strong reference to the
 * Logger is not kept.
 * <p>
 * Logging messages will be forwarded to registered Handler
 * objects, which can forward the messages to a variety of
 * destinations, including consoles, files, OS logs, etc.
 * <p>
 * Each Logger keeps track of a "parent" Logger, which is its
 * nearest existing ancestor in the Logger namespace.
 * <p>
 * Each Logger has a "Level" associated with it.  This reflects
 * a minimum Level that this logger cares about.  If a Logger's
 * level is set to <tt>null</tt>, then its effective level is inherited
 * from its parent, which may in turn obtain it recursively from its
 * parent, and so on up the tree.
 * <p>
 * The log level can be configured based on the properties from the
 * logging configuration file, as described in the description
 * of the LogManager class.  However it may also be dynamically changed
 * by calls on the Logger.setLevel method.  If a logger's level is
 * changed the change may also affect child loggers, since any child
 * logger that has <tt>null</tt> as its level will inherit its
 * effective level from its parent.
 * <p>
 * On each logging call the Logger initially performs a cheap
 * check of the request level (e.g., SEVERE or FINE) against the
 * effective log level of the logger.  If the request level is
 * lower than the log level, the logging call returns immediately.
 * <p>
 * After passing this initial (cheap) test, the Logger will allocate
 * a LogRecord to describe the logging message.  It will then call a
 * Filter (if present) to do a more detailed check on whether the
 * record should be published.  If that passes it will then publish
 * the LogRecord to its output Handlers.  By default, loggers also
 * publish to their parent's Handlers, recursively up the tree.
 * <p>
 * Each Logger may have a ResourceBundle name associated with it.
 * The named bundle will be used for localizing logging messages.
 * If a Logger does not have its own ResourceBundle name, then
 * it will inherit the ResourceBundle name from its parent,
 * recursively up the tree.
 * <p>
 * Most of the logger output methods take a "msg" argument.  This
 * msg argument may be either a raw value or a localization key.
 * During formatting, if the logger has (or inherits) a localization
 * ResourceBundle and if the ResourceBundle has a mapping for the msg
 * string, then the msg string is replaced by the localized value.
 * Otherwise the original msg string is used.  Typically, formatters use
 * java.text.MessageFormat style formatting to format parameters, so
 * for example a format string "{0} {1}" would format two parameters
 * as strings.
 * <p>
 * When mapping ResourceBundle names to ResourceBundles, the Logger
 * will first try to use the Thread's ContextClassLoader.  If that
 * is null it will try the SystemClassLoader instead.  As a temporary
 * transition feature in the initial implementation, if the Logger is
 * unable to locate a ResourceBundle from the ContextClassLoader or
 * SystemClassLoader the Logger will also search up the class stack
 * and use successive calling ClassLoaders to try to locate a ResourceBundle.
 * (This call stack search is to allow containers to transition to
 * using ContextClassLoaders and is likely to be removed in future
 * versions.)
 * <p>
 * Formatting (including localization) is the responsibility of
 * the output Handler, which will typically call a Formatter.
 * <p>
 * Note that formatting need not occur synchronously.  It may be delayed
 * until a LogRecord is actually written to an external sink.
 * <p>
 * The logging methods are grouped in five main categories:
 * <ul>
 * <li><p>
 *     There are a set of "log" methods that take a log level, a message
 *     string, and optionally some parameters to the message string.
 * <li><p>
 *     There are a set of "logp" methods (for "log precise") that are
 *     like the "log" methods, but also take an explicit source class name
 *     and method name.
 * <li><p>
 *     There are a set of "logrb" method (for "log with resource bundle")
 *     that are like the "logp" method, but also take an explicit resource
 *     bundle name for use in localizing the log message.
 * <li><p>
 *     There are convenience methods for tracing method entries (the
 *     "entering" methods), method returns (the "exiting" methods) and
 *     throwing exceptions (the "throwing" methods).
 * <li><p>
 *     Finally, there are a set of convenience methods for use in the
 *     very simplest cases, when a developer simply wants to log a
 *     simple string at a given log level.  These methods are named
 *     after the standard Level names ("severe", "warning", "info", etc.)
 *     and take a single argument, a message string.
 * </ul>
 * <p>
 * For the methods that do not take an explicit source name and
 * method name, the Logging framework will make a "best effort"
 * to determine which class and method called into the logging method.
 * However, it is important to realize that this automatically inferred
 * information may only be approximate (or may even be quite wrong!).
 * Virtual machines are allowed to do extensive optimizations when
 * JITing and may entirely remove stack frames, making it impossible
 * to reliably locate the calling class and method.
 * <P>
 * All methods on Logger are multi-thread safe.
 * <p>
 * <b>Subclassing Information:</b> Note that a LogManager class may
 * provide its own implementation of named Loggers for any point in
 * the namespace.  Therefore, any subclasses of Logger (unless they
 * are implemented in conjunction with a new LogManager class) should
 * take care to obtain a Logger instance from the LogManager class and
 * should delegate operations such as "isLoggable" and "log(LogRecord)"
 * to that instance.  Note that in order to intercept all logging
 * output, subclasses need only override the log(LogRecord) method.
 * All the other logging methods are implemented as calls on this
 * log(LogRecord) method.
 *
 * @since 1.4
 */


public class Logger {
    private static final Handler emptyHandlers[] = new Handler[0];
    private static final int offValue = Level.OFF.intValue();
    private LogManager manager;
    private String name;
    private final CopyOnWriteArrayList<Handler> handlers =
        new CopyOnWriteArrayList<Handler>();
    private String resourceBundleName;
    private volatile boolean useParentHandlers = true;
    private volatile Filter filter;
    private boolean anonymous;

    private ResourceBundle catalog;     // Cached resource bundle
    private String catalogName;         // name associated with catalog
    private Locale catalogLocale;       // locale associated with catalog

    // The fields relating to parent-child relationships and levels
    // are managed under a separate lock, the treeLock.
    private static Object treeLock = new Object();
    // We keep weak references from parents to children, but strong
    // references from children to parents.
    private volatile Logger parent;    // our nearest parent.
    private ArrayList<LogManager.LoggerWeakRef> kids;   // WeakReferences to loggers that have us as parent
    private volatile Level levelObject;
    private volatile int levelValue;  // current effective level value

    /**
     * GLOBAL_LOGGER_NAME is a name for the global logger.
     *
     * @since 1.6
     */
    public static final String GLOBAL_LOGGER_NAME = "global";

    /**
     * Return global logger object with the name Logger.GLOBAL_LOGGER_NAME.
     *
     * @return global logger object
     * @since 1.7
     */
    public static final Logger getGlobal() {
        return global;
    }

    /**
     * The "global" Logger object is provided as a convenience to developers
     * who are making casual use of the Logging package.  Developers
     * who are making serious use of the logging package (for example
     * in products) should create and use their own Logger objects,
     * with appropriate names, so that logging can be controlled on a
     * suitable per-Logger granularity. Developers also need to keep a
     * strong reference to their Logger objects to prevent them from
     * being garbage collected.
     * <p>
     * @deprecated Initialization of this field is prone to deadlocks.
     * The field must be initialized by the Logger class initialization
     * which may cause deadlocks with the LogManager class initialization.
     * In such cases two class initialization wait for each other to complete.
     * The preferred way to get the global logger object is via the call
     * <code>Logger.getGlobal()</code>.
     * For compatibility with old JDK versions where the
     * <code>Logger.getGlobal()</code> is not available use the call
     * <code>Logger.getLogger(Logger.GLOBAL_LOGGER_NAME)</code>
     * or <code>Logger.getLogger("global")</code>.
     */
    @Deprecated
    public static final Logger global = new Logger(GLOBAL_LOGGER_NAME);

    /**
     * Protected method to construct a logger for a named subsystem.
     * <p>
     * The logger will be initially configured with a null Level
     * and with useParentHandlers set to true.
     *
     * @param   name    A name for the logger.  This should
     *                          be a dot-separated name and should normally
     *                          be based on the package name or class name
     *                          of the subsystem, such as java.net
     *                          or javax.swing.  It may be null for anonymous Loggers.
     * @param   resourceBundleName  name of ResourceBundle to be used for localizing
     *                          messages for this logger.  May be null if none
     *                          of the messages require localization.
     * @throws MissingResourceException if the resourceBundleName is non-null and
     *             no corresponding resource can be found.
     */
    protected Logger(String name, String resourceBundleName) {
        this.manager = LogManager.getLogManager();
        if (resourceBundleName != null) {
            // Note: we may get a MissingResourceException here.
            setupResourceInfo(resourceBundleName);
        }
        this.name = name;
        levelValue = Level.INFO.intValue();
    }

    // This constructor is used only to create the global Logger.
    // It is needed to break a cyclic dependence between the LogManager
    // and Logger static initializers causing deadlocks.
    private Logger(String name) {
        // The manager field is not initialized here.
        this.name = name;
        levelValue = Level.INFO.intValue();
    }

    // It is called from the LogManager.<clinit> to complete
    // initialization of the global Logger.
    void setLogManager(LogManager manager) {
        this.manager = manager;
    }

    private void checkAccess() throws SecurityException {
        if (!anonymous) {
            if (manager == null) {
                // Complete initialization of the global Logger.
                manager = LogManager.getLogManager();
            }
            manager.checkAccess();
        }
    }

    /**
     * Find or create a logger for a named subsystem.  If a logger has
     * already been created with the given name it is returned.  Otherwise
     * a new logger is created.
     * <p>
     * If a new logger is created its log level will be configured
     * based on the LogManager configuration and it will configured
     * to also send logging output to its parent's Handlers.  It will
     * be registered in the LogManager global namespace.
     * <p>
     * Note: The LogManager may only retain a weak reference to the newly
     * created Logger. It is important to understand that a previously
     * created Logger with the given name may be garbage collected at any
     * time if there is no strong reference to the Logger. In particular,
     * this means that two back-to-back calls like
     * {@code getLogger("MyLogger").log(...)} may use different Logger
     * objects named "MyLogger" if there is no strong reference to the
     * Logger named "MyLogger" elsewhere in the program.
     *
     * @param   name            A name for the logger.  This should
     *                          be a dot-separated name and should normally
     *                          be based on the package name or class name
     *                          of the subsystem, such as java.net
     *                          or javax.swing
     * @return a suitable Logger
     * @throws NullPointerException if the name is null.
     */
    public static synchronized Logger getLogger(String name) {
        LogManager manager = LogManager.getLogManager();
        return manager.demandLogger(name);
    }

    /**
     * Find or create a logger for a named subsystem.  If a logger has
     * already been created with the given name it is returned.  Otherwise
     * a new logger is created.
     * <p>
     * If a new logger is created its log level will be configured
     * based on the LogManager and it will configured to also send logging
     * output to its parent's Handlers.  It will be registered in
     * the LogManager global namespace.
     * <p>
     * Note: The LogManager may only retain a weak reference to the newly
     * created Logger. It is important to understand that a previously
     * created Logger with the given name may be garbage collected at any
     * time if there is no strong reference to the Logger. In particular,
     * this means that two back-to-back calls like
     * {@code getLogger("MyLogger", ...).log(...)} may use different Logger
     * objects named "MyLogger" if there is no strong reference to the
     * Logger named "MyLogger" elsewhere in the program.
     * <p>
     * If the named Logger already exists and does not yet have a
     * localization resource bundle then the given resource bundle
     * name is used.  If the named Logger already exists and has
     * a different resource bundle name then an IllegalArgumentException
     * is thrown.
     * <p>
     * @param   name    A name for the logger.  This should
     *                          be a dot-separated name and should normally
     *                          be based on the package name or class name
     *                          of the subsystem, such as java.net
     *                          or javax.swing
     * @param   resourceBundleName  name of ResourceBundle to be used for localizing
     *                          messages for this logger. May be <CODE>null</CODE> if none of
     *                          the messages require localization.
     * @return a suitable Logger
     * @throws MissingResourceException if the resourceBundleName is non-null and
     *             no corresponding resource can be found.
     * @throws IllegalArgumentException if the Logger already exists and uses
     *             a different resource bundle name.
     * @throws NullPointerException if the name is null.
     */
    public static synchronized Logger getLogger(String name, String resourceBundleName) {
        LogManager manager = LogManager.getLogManager();
        Logger result = manager.demandLogger(name);
        if (result.resourceBundleName == null) {
            // Note: we may get a MissingResourceException here.
            result.setupResourceInfo(resourceBundleName);
        } else if (!result.resourceBundleName.equals(resourceBundleName)) {
            throw new IllegalArgumentException(result.resourceBundleName +
                                " != " + resourceBundleName);
        }
        return result;
    }


    /**
     * Create an anonymous Logger.  The newly created Logger is not
     * registered in the LogManager namespace.  There will be no
     * access checks on updates to the logger.
     * <p>
     * This factory method is primarily intended for use from applets.
     * Because the resulting Logger is anonymous it can be kept private
     * by the creating class.  This removes the need for normal security
     * checks, which in turn allows untrusted applet code to update
     * the control state of the Logger.  For example an applet can do
     * a setLevel or an addHandler on an anonymous Logger.
     * <p>
     * Even although the new logger is anonymous, it is configured
     * to have the root logger ("") as its parent.  This means that
     * by default it inherits its effective level and handlers
     * from the root logger.
     * <p>
     *
     * @return a newly created private Logger
     */
    public static Logger getAnonymousLogger() {
        return getAnonymousLogger(null);
    }

    /**
     * Create an anonymous Logger.  The newly created Logger is not
     * registered in the LogManager namespace.  There will be no
     * access checks on updates to the logger.
     * <p>
     * This factory method is primarily intended for use from applets.
     * Because the resulting Logger is anonymous it can be kept private
     * by the creating class.  This removes the need for normal security
     * checks, which in turn allows untrusted applet code to update
     * the control state of the Logger.  For example an applet can do
     * a setLevel or an addHandler on an anonymous Logger.
     * <p>
     * Even although the new logger is anonymous, it is configured
     * to have the root logger ("") as its parent.  This means that
     * by default it inherits its effective level and handlers
     * from the root logger.
     * <p>
     * @param   resourceBundleName  name of ResourceBundle to be used for localizing
     *                          messages for this logger.
     *          May be null if none of the messages require localization.
     * @return a newly created private Logger
     * @throws MissingResourceException if the resourceBundleName is non-null and
     *             no corresponding resource can be found.
     */
    public static synchronized Logger getAnonymousLogger(String resourceBundleName) {
        LogManager manager = LogManager.getLogManager();
        // cleanup some Loggers that have been GC'ed
        manager.drainLoggerRefQueueBounded();
        Logger result = new Logger(null, resourceBundleName);
        result.anonymous = true;
        Logger root = manager.getLogger("");
        result.doSetParent(root);
        return result;
    }

    /**
     * Retrieve the localization resource bundle for this
     * logger for the current default locale.  Note that if
     * the result is null, then the Logger will use a resource
     * bundle inherited from its parent.
     *
     * @return localization bundle (may be null)
     */
    public ResourceBundle getResourceBundle() {
        return findResourceBundle(getResourceBundleName());
    }

    /**
     * Retrieve the localization resource bundle name for this
     * logger.  Note that if the result is null, then the Logger
     * will use a resource bundle name inherited from its parent.
     *
     * @return localization bundle name (may be null)
     */
    public String getResourceBundleName() {
        return resourceBundleName;
    }

    /**
     * Set a filter to control output on this Logger.
     * <P>
     * After passing the initial "level" check, the Logger will
     * call this Filter to check if a log record should really
     * be published.
     *
     * @param   newFilter  a filter object (may be null)
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have LoggingPermission("control").
     */
    public void setFilter(Filter newFilter) throws SecurityException {
        checkAccess();
        filter = newFilter;
    }

    /**
     * Get the current filter for this Logger.
     *
     * @return  a filter object (may be null)
     */
    public Filter getFilter() {
        return filter;
    }

    /**
     * Log a LogRecord.
     * <p>
     * All the other logging methods in this class call through
     * this method to actually perform any logging.  Subclasses can
     * override this single method to capture all log activity.
     *
     * @param record the LogRecord to be published
     */
    public void log(LogRecord record) {
        if (record.getLevel().intValue() < levelValue || levelValue == offValue) {
            return;
        }
        Filter theFilter = filter;
        if (theFilter != null && !theFilter.isLoggable(record)) {
            return;
        }

        // Post the LogRecord to all our Handlers, and then to
        // our parents' handlers, all the way up the tree.

        Logger logger = this;
        while (logger != null) {
            for (Handler handler : logger.getHandlers()) {
                handler.publish(record);
            }

            if (!logger.getUseParentHandlers()) {
                break;
            }

            logger = logger.getParent();
        }
    }

    // private support method for logging.
    // We fill in the logger name, resource bundle name, and
    // resource bundle and then call "void log(LogRecord)".
    private void doLog(LogRecord lr) {
        lr.setLoggerName(name);
        String ebname = getEffectiveResourceBundleName();
        if (ebname != null) {
            lr.setResourceBundleName(ebname);
            lr.setResourceBundle(findResourceBundle(ebname));
        }
        log(lr);
    }


    //================================================================
    // Start of convenience methods WITHOUT className and methodName
    //================================================================

    /**
     * Log a message, with no arguments.
     * <p>
     * If the logger is currently enabled for the given message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     * @param   level   One of the message level identifiers, e.g., SEVERE
     * @param   msg     The string message (or a key in the message catalog)
     */
    public void log(Level level, String msg) {
        if (level.intValue() < levelValue || levelValue == offValue) {
            return;
        }
        LogRecord lr = new LogRecord(level, msg);
        doLog(lr);
    }

    /**
     * Log a message, with one object parameter.
     * <p>
     * If the logger is currently enabled for the given message
     * level then a corresponding LogRecord is created and forwarded
     * to all the registered output Handler objects.
     * <p>
     * @param   level   One of the message level identifiers, e.g., SEVERE
     * @param   msg     The string message (or a key in the message catalog)
     * @param   param1  parameter to the message
     */
    public void log(Level level, String msg, Object param1) {
        if (level.intValue() < levelValue || levelValue == offValue) {
            return;
        }
        LogRecord lr = new LogRecord(level, msg);
        Object params[] = { param1 };
        lr.setParameters(params);
        doLog(lr);
    }

    /**
     * Log a message, with an array of object arguments.
     * <p>
     * If the logger is currently enabled for the given message
     * level then a corresponding LogRecord is created and forwarded
     * to all the registered output Handler objects.
     * <p>
     * @param   level   One of the message level identifiers, e.g., SEVERE
     * @param   msg     The string message (or a key in the message catalog)
     * @param   params  array of parameters to the message
     */
    public void log(Level level, String msg, Object params[]) {
        if (level.intValue() < levelValue || levelValue == offValue) {
            return;
        }
        LogRecord lr = new LogRecord(level, msg);
        lr.setParameters(params);
        doLog(lr);
    }

    /**
     * Log a message, with associated Throwable information.
     * <p>
     * If the logger is currently enabled for the given message
     * level then the given arguments are stored in a LogRecord
     * which is forwarded to all registered output handlers.
     * <p>
     * Note that the thrown argument is stored in the LogRecord thrown
     * property, rather than the LogRecord parameters property.  Thus is it
     * processed specially by output Formatters and is not treated
     * as a formatting parameter to the LogRecord message property.
     * <p>
     * @param   level   One of the message level identifiers, e.g., SEVERE
     * @param   msg     The string message (or a key in the message catalog)
     * @param   thrown  Throwable associated with log message.
     */
    public void log(Level level, String msg, Throwable thrown) {
        if (level.intValue() < levelValue || levelValue == offValue) {
            return;
        }
        LogRecord lr = new LogRecord(level, msg);
        lr.setThrown(thrown);
        doLog(lr);
    }

    //================================================================
    // Start of convenience methods WITH className and methodName
    //================================================================

    /**
     * Log a message, specifying source class and method,
     * with no arguments.
     * <p>
     * If the logger is currently enabled for the given message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     * @param   level   One of the message level identifiers, e.g., SEVERE
     * @param   sourceClass    name of class that issued the logging request
     * @param   sourceMethod   name of method that issued the logging request
     * @param   msg     The string message (or a key in the message catalog)
     */
    public void logp(Level level, String sourceClass, String sourceMethod, String msg) {
        if (level.intValue() < levelValue || levelValue == offValue) {
            return;
        }
        LogRecord lr = new LogRecord(level, msg);
        lr.setSourceClassName(sourceClass);
        lr.setSourceMethodName(sourceMethod);
        doLog(lr);
    }

    /**
     * Log a message, specifying source class and method,
     * with a single object parameter to the log message.
     * <p>
     * If the logger is currently enabled for the given message
     * level then a corresponding LogRecord is created and forwarded
     * to all the registered output Handler objects.
     * <p>
     * @param   level   One of the message level identifiers, e.g., SEVERE
     * @param   sourceClass    name of class that issued the logging request
     * @param   sourceMethod   name of method that issued the logging request
     * @param   msg      The string message (or a key in the message catalog)
     * @param   param1    Parameter to the log message.
     */
    public void logp(Level level, String sourceClass, String sourceMethod,
                                                String msg, Object param1) {
        if (level.intValue() < levelValue || levelValue == offValue) {
            return;
        }
        LogRecord lr = new LogRecord(level, msg);
        lr.setSourceClassName(sourceClass);
        lr.setSourceMethodName(sourceMethod);
        Object params[] = { param1 };
        lr.setParameters(params);
        doLog(lr);
    }

    /**
     * Log a message, specifying source class and method,
     * with an array of object arguments.
     * <p>
     * If the logger is currently enabled for the given message
     * level then a corresponding LogRecord is created and forwarded
     * to all the registered output Handler objects.
     * <p>
     * @param   level   One of the message level identifiers, e.g., SEVERE
     * @param   sourceClass    name of class that issued the logging request
     * @param   sourceMethod   name of method that issued the logging request
     * @param   msg     The string message (or a key in the message catalog)
     * @param   params  Array of parameters to the message
     */
    public void logp(Level level, String sourceClass, String sourceMethod,
                                                String msg, Object params[]) {
        if (level.intValue() < levelValue || levelValue == offValue) {
            return;
        }
        LogRecord lr = new LogRecord(level, msg);
        lr.setSourceClassName(sourceClass);
        lr.setSourceMethodName(sourceMethod);
        lr.setParameters(params);
        doLog(lr);
    }

    /**
     * Log a message, specifying source class and method,
     * with associated Throwable information.
     * <p>
     * If the logger is currently enabled for the given message
     * level then the given arguments are stored in a LogRecord
     * which is forwarded to all registered output handlers.
     * <p>
     * Note that the thrown argument is stored in the LogRecord thrown
     * property, rather than the LogRecord parameters property.  Thus is it
     * processed specially by output Formatters and is not treated
     * as a formatting parameter to the LogRecord message property.
     * <p>
     * @param   level   One of the message level identifiers, e.g., SEVERE
     * @param   sourceClass    name of class that issued the logging request
     * @param   sourceMethod   name of method that issued the logging request
     * @param   msg     The string message (or a key in the message catalog)
     * @param   thrown  Throwable associated with log message.
     */
    public void logp(Level level, String sourceClass, String sourceMethod,
                                                        String msg, Throwable thrown) {
        if (level.intValue() < levelValue || levelValue == offValue) {
            return;
        }
        LogRecord lr = new LogRecord(level, msg);
        lr.setSourceClassName(sourceClass);
        lr.setSourceMethodName(sourceMethod);
        lr.setThrown(thrown);
        doLog(lr);
    }


    //=========================================================================
    // Start of convenience methods WITH className, methodName and bundle name.
    //=========================================================================

    // Private support method for logging for "logrb" methods.
    // We fill in the logger name, resource bundle name, and
    // resource bundle and then call "void log(LogRecord)".
    private void doLog(LogRecord lr, String rbname) {
        lr.setLoggerName(name);
        if (rbname != null) {
            lr.setResourceBundleName(rbname);
            lr.setResourceBundle(findResourceBundle(rbname));
        }
        log(lr);
    }

    /**
     * Log a message, specifying source class, method, and resource bundle name
     * with no arguments.
     * <p>
     * If the logger is currently enabled for the given message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     * The msg string is localized using the named resource bundle.  If the
     * resource bundle name is null, or an empty String or invalid
     * then the msg string is not localized.
     * <p>
     * @param   level   One of the message level identifiers, e.g., SEVERE
     * @param   sourceClass    name of class that issued the logging request
     * @param   sourceMethod   name of method that issued the logging request
     * @param   bundleName     name of resource bundle to localize msg,
     *                         can be null
     * @param   msg     The string message (or a key in the message catalog)
     */

    public void logrb(Level level, String sourceClass, String sourceMethod,
                                String bundleName, String msg) {
        if (level.intValue() < levelValue || levelValue == offValue) {
            return;
        }
        LogRecord lr = new LogRecord(level, msg);
        lr.setSourceClassName(sourceClass);
        lr.setSourceMethodName(sourceMethod);
        doLog(lr, bundleName);
    }

    /**
     * Log a message, specifying source class, method, and resource bundle name,
     * with a single object parameter to the log message.
     * <p>
     * If the logger is currently enabled for the given message
     * level then a corresponding LogRecord is created and forwarded
     * to all the registered output Handler objects.
     * <p>
     * The msg string is localized using the named resource bundle.  If the
     * resource bundle name is null, or an empty String or invalid
     * then the msg string is not localized.
     * <p>
     * @param   level   One of the message level identifiers, e.g., SEVERE
     * @param   sourceClass    name of class that issued the logging request
     * @param   sourceMethod   name of method that issued the logging request
     * @param   bundleName     name of resource bundle to localize msg,
     *                         can be null
     * @param   msg      The string message (or a key in the message catalog)
     * @param   param1    Parameter to the log message.
     */
    public void logrb(Level level, String sourceClass, String sourceMethod,
                                String bundleName, String msg, Object param1) {
        if (level.intValue() < levelValue || levelValue == offValue) {
            return;
        }
        LogRecord lr = new LogRecord(level, msg);
        lr.setSourceClassName(sourceClass);
        lr.setSourceMethodName(sourceMethod);
        Object params[] = { param1 };
        lr.setParameters(params);
        doLog(lr, bundleName);
    }

    /**
     * Log a message, specifying source class, method, and resource bundle name,
     * with an array of object arguments.
     * <p>
     * If the logger is currently enabled for the given message
     * level then a corresponding LogRecord is created and forwarded
     * to all the registered output Handler objects.
     * <p>
     * The msg string is localized using the named resource bundle.  If the
     * resource bundle name is null, or an empty String or invalid
     * then the msg string is not localized.
     * <p>
     * @param   level   One of the message level identifiers, e.g., SEVERE
     * @param   sourceClass    name of class that issued the logging request
     * @param   sourceMethod   name of method that issued the logging request
     * @param   bundleName     name of resource bundle to localize msg,
     *                         can be null.
     * @param   msg     The string message (or a key in the message catalog)
     * @param   params  Array of parameters to the message
     */
    public void logrb(Level level, String sourceClass, String sourceMethod,
                                String bundleName, String msg, Object params[]) {
        if (level.intValue() < levelValue || levelValue == offValue) {
            return;
        }
        LogRecord lr = new LogRecord(level, msg);
        lr.setSourceClassName(sourceClass);
        lr.setSourceMethodName(sourceMethod);
        lr.setParameters(params);
        doLog(lr, bundleName);
    }

    /**
     * Log a message, specifying source class, method, and resource bundle name,
     * with associated Throwable information.
     * <p>
     * If the logger is currently enabled for the given message
     * level then the given arguments are stored in a LogRecord
     * which is forwarded to all registered output handlers.
     * <p>
     * The msg string is localized using the named resource bundle.  If the
     * resource bundle name is null, or an empty String or invalid
     * then the msg string is not localized.
     * <p>
     * Note that the thrown argument is stored in the LogRecord thrown
     * property, rather than the LogRecord parameters property.  Thus is it
     * processed specially by output Formatters and is not treated
     * as a formatting parameter to the LogRecord message property.
     * <p>
     * @param   level   One of the message level identifiers, e.g., SEVERE
     * @param   sourceClass    name of class that issued the logging request
     * @param   sourceMethod   name of method that issued the logging request
     * @param   bundleName     name of resource bundle to localize msg,
     *                         can be null
     * @param   msg     The string message (or a key in the message catalog)
     * @param   thrown  Throwable associated with log message.
     */
    public void logrb(Level level, String sourceClass, String sourceMethod,
                                        String bundleName, String msg, Throwable thrown) {
        if (level.intValue() < levelValue || levelValue == offValue) {
            return;
        }
        LogRecord lr = new LogRecord(level, msg);
        lr.setSourceClassName(sourceClass);
        lr.setSourceMethodName(sourceMethod);
        lr.setThrown(thrown);
        doLog(lr, bundleName);
    }


    //======================================================================
    // Start of convenience methods for logging method entries and returns.
    //======================================================================

    /**
     * Log a method entry.
     * <p>
     * This is a convenience method that can be used to log entry
     * to a method.  A LogRecord with message "ENTRY", log level
     * FINER, and the given sourceMethod and sourceClass is logged.
     * <p>
     * @param   sourceClass    name of class that issued the logging request
     * @param   sourceMethod   name of method that is being entered
     */
    public void entering(String sourceClass, String sourceMethod) {
        if (Level.FINER.intValue() < levelValue) {
            return;
        }
        logp(Level.FINER, sourceClass, sourceMethod, "ENTRY");
    }

    /**
     * Log a method entry, with one parameter.
     * <p>
     * This is a convenience method that can be used to log entry
     * to a method.  A LogRecord with message "ENTRY {0}", log level
     * FINER, and the given sourceMethod, sourceClass, and parameter
     * is logged.
     * <p>
     * @param   sourceClass    name of class that issued the logging request
     * @param   sourceMethod   name of method that is being entered
     * @param   param1         parameter to the method being entered
     */
    public void entering(String sourceClass, String sourceMethod, Object param1) {
        if (Level.FINER.intValue() < levelValue) {
            return;
        }
        Object params[] = { param1 };
        logp(Level.FINER, sourceClass, sourceMethod, "ENTRY {0}", params);
    }

    /**
     * Log a method entry, with an array of parameters.
     * <p>
     * This is a convenience method that can be used to log entry
     * to a method.  A LogRecord with message "ENTRY" (followed by a
     * format {N} indicator for each entry in the parameter array),
     * log level FINER, and the given sourceMethod, sourceClass, and
     * parameters is logged.
     * <p>
     * @param   sourceClass    name of class that issued the logging request
     * @param   sourceMethod   name of method that is being entered
     * @param   params         array of parameters to the method being entered
     */
    public void entering(String sourceClass, String sourceMethod, Object params[]) {
        if (Level.FINER.intValue() < levelValue) {
            return;
        }
        String msg = "ENTRY";
        if (params == null ) {
           logp(Level.FINER, sourceClass, sourceMethod, msg);
           return;
        }
        for (int i = 0; i < params.length; i++) {
            msg = msg + " {" + i + "}";
        }
        logp(Level.FINER, sourceClass, sourceMethod, msg, params);
    }

    /**
     * Log a method return.
     * <p>
     * This is a convenience method that can be used to log returning
     * from a method.  A LogRecord with message "RETURN", log level
     * FINER, and the given sourceMethod and sourceClass is logged.
     * <p>
     * @param   sourceClass    name of class that issued the logging request
     * @param   sourceMethod   name of the method
     */
    public void exiting(String sourceClass, String sourceMethod) {
        if (Level.FINER.intValue() < levelValue) {
            return;
        }
        logp(Level.FINER, sourceClass, sourceMethod, "RETURN");
    }


    /**
     * Log a method return, with result object.
     * <p>
     * This is a convenience method that can be used to log returning
     * from a method.  A LogRecord with message "RETURN {0}", log level
     * FINER, and the gives sourceMethod, sourceClass, and result
     * object is logged.
     * <p>
     * @param   sourceClass    name of class that issued the logging request
     * @param   sourceMethod   name of the method
     * @param   result  Object that is being returned
     */
    public void exiting(String sourceClass, String sourceMethod, Object result) {
        if (Level.FINER.intValue() < levelValue) {
            return;
        }
        Object params[] = { result };
        logp(Level.FINER, sourceClass, sourceMethod, "RETURN {0}", result);
    }

    /**
     * Log throwing an exception.
     * <p>
     * This is a convenience method to log that a method is
     * terminating by throwing an exception.  The logging is done
     * using the FINER level.
     * <p>
     * If the logger is currently enabled for the given message
     * level then the given arguments are stored in a LogRecord
     * which is forwarded to all registered output handlers.  The
     * LogRecord's message is set to "THROW".
     * <p>
     * Note that the thrown argument is stored in the LogRecord thrown
     * property, rather than the LogRecord parameters property.  Thus is it
     * processed specially by output Formatters and is not treated
     * as a formatting parameter to the LogRecord message property.
     * <p>
     * @param   sourceClass    name of class that issued the logging request
     * @param   sourceMethod  name of the method.
     * @param   thrown  The Throwable that is being thrown.
     */
    public void throwing(String sourceClass, String sourceMethod, Throwable thrown) {
        if (Level.FINER.intValue() < levelValue || levelValue == offValue ) {
            return;
        }
        LogRecord lr = new LogRecord(Level.FINER, "THROW");
        lr.setSourceClassName(sourceClass);
        lr.setSourceMethodName(sourceMethod);
        lr.setThrown(thrown);
        doLog(lr);
    }

    //=======================================================================
    // Start of simple convenience methods using level names as method names
    //=======================================================================

    /**
     * Log a SEVERE message.
     * <p>
     * If the logger is currently enabled for the SEVERE message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     * @param   msg     The string message (or a key in the message catalog)
     */
    public void severe(String msg) {
        if (Level.SEVERE.intValue() < levelValue) {
            return;
        }
        log(Level.SEVERE, msg);
    }

    /**
     * Log a WARNING message.
     * <p>
     * If the logger is currently enabled for the WARNING message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     * @param   msg     The string message (or a key in the message catalog)
     */
    public void warning(String msg) {
        if (Level.WARNING.intValue() < levelValue) {
            return;
        }
        log(Level.WARNING, msg);
    }

    /**
     * Log an INFO message.
     * <p>
     * If the logger is currently enabled for the INFO message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     * @param   msg     The string message (or a key in the message catalog)
     */
    public void info(String msg) {
        if (Level.INFO.intValue() < levelValue) {
            return;
        }
        log(Level.INFO, msg);
    }

    /**
     * Log a CONFIG message.
     * <p>
     * If the logger is currently enabled for the CONFIG message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     * @param   msg     The string message (or a key in the message catalog)
     */
    public void config(String msg) {
        if (Level.CONFIG.intValue() < levelValue) {
            return;
        }
        log(Level.CONFIG, msg);
    }

    /**
     * Log a FINE message.
     * <p>
     * If the logger is currently enabled for the FINE message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     * @param   msg     The string message (or a key in the message catalog)
     */
    public void fine(String msg) {
        if (Level.FINE.intValue() < levelValue) {
            return;
        }
        log(Level.FINE, msg);
    }

    /**
     * Log a FINER message.
     * <p>
     * If the logger is currently enabled for the FINER message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     * @param   msg     The string message (or a key in the message catalog)
     */
    public void finer(String msg) {
        if (Level.FINER.intValue() < levelValue) {
            return;
        }
        log(Level.FINER, msg);
    }

    /**
     * Log a FINEST message.
     * <p>
     * If the logger is currently enabled for the FINEST message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     * @param   msg     The string message (or a key in the message catalog)
     */
    public void finest(String msg) {
        if (Level.FINEST.intValue() < levelValue) {
            return;
        }
        log(Level.FINEST, msg);
    }

    //================================================================
    // End of convenience methods
    //================================================================

    /**
     * Set the log level specifying which message levels will be
     * logged by this logger.  Message levels lower than this
     * value will be discarded.  The level value Level.OFF
     * can be used to turn off logging.
     * <p>
     * If the new level is null, it means that this node should
     * inherit its level from its nearest ancestor with a specific
     * (non-null) level value.
     *
     * @param newLevel   the new value for the log level (may be null)
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have LoggingPermission("control").
     */
    public void setLevel(Level newLevel) throws SecurityException {
        checkAccess();
        synchronized (treeLock) {
            levelObject = newLevel;
            updateEffectiveLevel();
        }
    }

    /**
     * Get the log Level that has been specified for this Logger.
     * The result may be null, which means that this logger's
     * effective level will be inherited from its parent.
     *
     * @return  this Logger's level
     */
    public Level getLevel() {
        return levelObject;
    }

    /**
     * Check if a message of the given level would actually be logged
     * by this logger.  This check is based on the Loggers effective level,
     * which may be inherited from its parent.
     *
     * @param   level   a message logging level
     * @return  true if the given message level is currently being logged.
     */
    public boolean isLoggable(Level level) {
        if (level.intValue() < levelValue || levelValue == offValue) {
            return false;
        }
        return true;
    }

    /**
     * Get the name for this logger.
     * @return logger name.  Will be null for anonymous Loggers.
     */
    public String getName() {
        return name;
    }

    /**
     * Add a log Handler to receive logging messages.
     * <p>
     * By default, Loggers also send their output to their parent logger.
     * Typically the root Logger is configured with a set of Handlers
     * that essentially act as default handlers for all loggers.
     *
     * @param   handler a logging Handler
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have LoggingPermission("control").
     */
    public void addHandler(Handler handler) throws SecurityException {
        // Check for null handler
        handler.getClass();
        checkAccess();
        handlers.add(handler);
    }

    /**
     * Remove a log Handler.
     * <P>
     * Returns silently if the given Handler is not found or is null
     *
     * @param   handler a logging Handler
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have LoggingPermission("control").
     */
    public void removeHandler(Handler handler) throws SecurityException {
        checkAccess();
        if (handler == null) {
            return;
        }
        handlers.remove(handler);
    }

    /**
     * Get the Handlers associated with this logger.
     * <p>
     * @return  an array of all registered Handlers
     */
    public Handler[] getHandlers() {
        return handlers.toArray(emptyHandlers);
    }

    /**
     * Specify whether or not this logger should send its output
     * to its parent Logger.  This means that any LogRecords will
     * also be written to the parent's Handlers, and potentially
     * to its parent, recursively up the namespace.
     *
     * @param useParentHandlers   true if output is to be sent to the
     *          logger's parent.
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have LoggingPermission("control").
     */
    public void setUseParentHandlers(boolean useParentHandlers) {
        checkAccess();
        this.useParentHandlers = useParentHandlers;
    }

    /**
     * Discover whether or not this logger is sending its output
     * to its parent logger.
     *
     * @return  true if output is to be sent to the logger's parent
     */
    public boolean getUseParentHandlers() {
        return useParentHandlers;
    }

    // Private utility method to map a resource bundle name to an
    // actual resource bundle, using a simple one-entry cache.
    // Returns null for a null name.
    // May also return null if we can't find the resource bundle and
    // there is no suitable previous cached value.

    private synchronized ResourceBundle findResourceBundle(String name) {
        // Return a null bundle for a null name.
        if (name == null) {
            return null;
        }

        Locale currentLocale = Locale.getDefault();

        // Normally we should hit on our simple one entry cache.
        if (catalog != null && currentLocale == catalogLocale
                                        && name == catalogName) {
            return catalog;
        }

        // Use the thread's context ClassLoader.  If there isn't one,
        // use the SystemClassloader.
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }
        try {
            catalog = ResourceBundle.getBundle(name, currentLocale, cl);
            catalogName = name;
            catalogLocale = currentLocale;
            return catalog;
        } catch (MissingResourceException ex) {
            // Woops.  We can't find the ResourceBundle in the default
            // ClassLoader.  Drop through.
        }


        // Fall back to searching up the call stack and trying each
        // calling ClassLoader.
        for (int ix = 0; ; ix++) {
            Class clz = sun.reflect.Reflection.getCallerClass(ix);
            if (clz == null) {
                break;
            }
            ClassLoader cl2 = clz.getClassLoader();
            if (cl2 == null) {
                cl2 = ClassLoader.getSystemClassLoader();
            }
            if (cl == cl2) {
                // We've already checked this classloader.
                continue;
            }
            cl = cl2;
            try {
                catalog = ResourceBundle.getBundle(name, currentLocale, cl);
                catalogName = name;
                catalogLocale = currentLocale;
                return catalog;
            } catch (MissingResourceException ex) {
                // Ok, this one didn't work either.
                // Drop through, and try the next one.
            }
        }

        if (name.equals(catalogName)) {
            // Return the previous cached value for that name.
            // This may be null.
            return catalog;
        }
        // Sorry, we're out of luck.
        return null;
    }

    // Private utility method to initialize our one entry
    // resource bundle cache.
    // Note: for consistency reasons, we are careful to check
    // that a suitable ResourceBundle exists before setting the
    // ResourceBundleName.
    private synchronized void setupResourceInfo(String name) {
        if (name == null) {
            return;
        }
        ResourceBundle rb = findResourceBundle(name);
        if (rb == null) {
            // We've failed to find an expected ResourceBundle.
            throw new MissingResourceException("Can't find " + name + " bundle", name, "");
        }
        resourceBundleName = name;
    }

    /**
     * Return the parent for this Logger.
     * <p>
     * This method returns the nearest extant parent in the namespace.
     * Thus if a Logger is called "a.b.c.d", and a Logger called "a.b"
     * has been created but no logger "a.b.c" exists, then a call of
     * getParent on the Logger "a.b.c.d" will return the Logger "a.b".
     * <p>
     * The result will be null if it is called on the root Logger
     * in the namespace.
     *
     * @return nearest existing parent Logger
     */
    public Logger getParent() {
        // Note: this used to be synchronized on treeLock.  However, this only
        // provided memory semantics, as there was no guarantee that the caller
        // would synchronize on treeLock (in fact, there is no way for external
        // callers to so synchronize).  Therefore, we have made parent volatile
        // instead.
        return parent;
    }

    /**
     * Set the parent for this Logger.  This method is used by
     * the LogManager to update a Logger when the namespace changes.
     * <p>
     * It should not be called from application code.
     * <p>
     * @param  parent   the new parent logger
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have LoggingPermission("control").
     */
    public void setParent(Logger parent) {
        if (parent == null) {
            throw new NullPointerException();
        }
        manager.checkAccess();
        doSetParent(parent);
    }

    // Private method to do the work for parenting a child
    // Logger onto a parent logger.
    private void doSetParent(Logger newParent) {

        // System.err.println("doSetParent \"" + getName() + "\" \""
        //                              + newParent.getName() + "\"");

        synchronized (treeLock) {

            // Remove ourself from any previous parent.
            LogManager.LoggerWeakRef ref = null;
            if (parent != null) {
                // assert parent.kids != null;
                for (Iterator<LogManager.LoggerWeakRef> iter = parent.kids.iterator(); iter.hasNext(); ) {
                    ref = iter.next();
                    Logger kid =  ref.get();
                    if (kid == this) {
                        // ref is used down below to complete the reparenting
                        iter.remove();
                        break;
                    } else {
                        ref = null;
                    }
                }
                // We have now removed ourself from our parents' kids.
            }

            // Set our new parent.
            parent = newParent;
            if (parent.kids == null) {
                parent.kids = new ArrayList<LogManager.LoggerWeakRef>(2);
            }
            if (ref == null) {
                // we didn't have a previous parent
                ref = manager.new LoggerWeakRef(this);
            }
            ref.setParentRef(new WeakReference<Logger>(parent));
            parent.kids.add(ref);

            // As a result of the reparenting, the effective level
            // may have changed for us and our children.
            updateEffectiveLevel();

        }
    }

    // Package-level method.
    // Remove the weak reference for the specified child Logger from the
    // kid list. We should only be called from LoggerWeakRef.dispose().
    final void removeChildLogger(LogManager.LoggerWeakRef child) {
        synchronized (treeLock) {
            for (Iterator<LogManager.LoggerWeakRef> iter = kids.iterator(); iter.hasNext(); ) {
                LogManager.LoggerWeakRef ref = iter.next();
                if (ref == child) {
                    iter.remove();
                    return;
                }
            }
        }
    }

    // Recalculate the effective level for this node and
    // recursively for our children.

    private void updateEffectiveLevel() {
        // assert Thread.holdsLock(treeLock);

        // Figure out our current effective level.
        int newLevelValue;
        if (levelObject != null) {
            newLevelValue = levelObject.intValue();
        } else {
            if (parent != null) {
                newLevelValue = parent.levelValue;
            } else {
                // This may happen during initialization.
                newLevelValue = Level.INFO.intValue();
            }
        }

        // If our effective value hasn't changed, we're done.
        if (levelValue == newLevelValue) {
            return;
        }

        levelValue = newLevelValue;

        // System.err.println("effective level: \"" + getName() + "\" := " + level);

        // Recursively update the level on each of our kids.
        if (kids != null) {
            for (int i = 0; i < kids.size(); i++) {
                LogManager.LoggerWeakRef ref = kids.get(i);
                Logger kid =  ref.get();
                if (kid != null) {
                    kid.updateEffectiveLevel();
                }
            }
        }
    }


    // Private method to get the potentially inherited
    // resource bundle name for this Logger.
    // May return null
    private String getEffectiveResourceBundleName() {
        Logger target = this;
        while (target != null) {
            String rbn = target.getResourceBundleName();
            if (rbn != null) {
                return rbn;
            }
            target = target.getParent();
        }
        return null;
    }


}
