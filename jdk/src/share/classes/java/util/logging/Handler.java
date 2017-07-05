/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.UnsupportedEncodingException;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * A <tt>Handler</tt> object takes log messages from a <tt>Logger</tt> and
 * exports them.  It might for example, write them to a console
 * or write them to a file, or send them to a network logging service,
 * or forward them to an OS log, or whatever.
 * <p>
 * A <tt>Handler</tt> can be disabled by doing a <tt>setLevel(Level.OFF)</tt>
 * and can  be re-enabled by doing a <tt>setLevel</tt> with an appropriate level.
 * <p>
 * <tt>Handler</tt> classes typically use <tt>LogManager</tt> properties to set
 * default values for the <tt>Handler</tt>'s <tt>Filter</tt>, <tt>Formatter</tt>,
 * and <tt>Level</tt>.  See the specific documentation for each concrete
 * <tt>Handler</tt> class.
 *
 *
 * @since 1.4
 */

public abstract class Handler {
    private static final int offValue = Level.OFF.intValue();
    private final LogManager manager = LogManager.getLogManager();

    // We're using volatile here to avoid synchronizing getters, which
    // would prevent other threads from calling isLoggable()
    // while publish() is executing.
    // On the other hand, setters will be synchronized to exclude concurrent
    // execution with more complex methods, such as StreamHandler.publish().
    // We wouldn't want 'level' to be changed by another thread in the middle
    // of the execution of a 'publish' call.
    private volatile Filter filter;
    private volatile Formatter formatter;
    private volatile Level logLevel = Level.ALL;
    private volatile ErrorManager errorManager = new ErrorManager();
    private volatile String encoding;

    /**
     * Default constructor.  The resulting <tt>Handler</tt> has a log
     * level of <tt>Level.ALL</tt>, no <tt>Formatter</tt>, and no
     * <tt>Filter</tt>.  A default <tt>ErrorManager</tt> instance is installed
     * as the <tt>ErrorManager</tt>.
     */
    protected Handler() {
    }

    /**
     * Package-private constructor for chaining from subclass constructors
     * that wish to configure the handler with specific default and/or
     * specified values.
     *
     * @param defaultLevel       a default {@link Level} to configure if one is
     *                           not found in LogManager configuration properties
     * @param defaultFormatter   a default {@link Formatter} to configure if one is
     *                           not specified by {@code specifiedFormatter} parameter
     *                           nor found in LogManager configuration properties
     * @param specifiedFormatter if not null, this is the formatter to configure
     */
    Handler(Level defaultLevel, Formatter defaultFormatter,
            Formatter specifiedFormatter) {

        LogManager manager = LogManager.getLogManager();
        String cname = getClass().getName();

        final Level level = manager.getLevelProperty(cname + ".level", defaultLevel);
        final Filter filter = manager.getFilterProperty(cname + ".filter", null);
        final Formatter formatter = specifiedFormatter == null
                                    ? manager.getFormatterProperty(cname + ".formatter", defaultFormatter)
                                    : specifiedFormatter;
        final String encoding = manager.getStringProperty(cname + ".encoding", null);

        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                setLevel(level);
                setFilter(filter);
                setFormatter(formatter);
                try {
                    setEncoding(encoding);
                } catch (Exception ex) {
                    try {
                        setEncoding(null);
                    } catch (Exception ex2) {
                        // doing a setEncoding with null should always work.
                        // assert false;
                    }
                }
                return null;
            }
        }, null, LogManager.controlPermission);
    }

    /**
     * Publish a <tt>LogRecord</tt>.
     * <p>
     * The logging request was made initially to a <tt>Logger</tt> object,
     * which initialized the <tt>LogRecord</tt> and forwarded it here.
     * <p>
     * The <tt>Handler</tt>  is responsible for formatting the message, when and
     * if necessary.  The formatting should include localization.
     *
     * @param  record  description of the log event. A null record is
     *                 silently ignored and is not published
     */
    public abstract void publish(LogRecord record);

    /**
     * Flush any buffered output.
     */
    public abstract void flush();

    /**
     * Close the <tt>Handler</tt> and free all associated resources.
     * <p>
     * The close method will perform a <tt>flush</tt> and then close the
     * <tt>Handler</tt>.   After close has been called this <tt>Handler</tt>
     * should no longer be used.  Method calls may either be silently
     * ignored or may throw runtime exceptions.
     *
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have <tt>LoggingPermission("control")</tt>.
     */
    public abstract void close() throws SecurityException;

    /**
     * Set a <tt>Formatter</tt>.  This <tt>Formatter</tt> will be used
     * to format <tt>LogRecords</tt> for this <tt>Handler</tt>.
     * <p>
     * Some <tt>Handlers</tt> may not use <tt>Formatters</tt>, in
     * which case the <tt>Formatter</tt> will be remembered, but not used.
     *
     * @param newFormatter the <tt>Formatter</tt> to use (may not be null)
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have <tt>LoggingPermission("control")</tt>.
     */
    public synchronized void setFormatter(Formatter newFormatter) throws SecurityException {
        checkPermission();
        // Check for a null pointer:
        newFormatter.getClass();
        formatter = newFormatter;
    }

    /**
     * Return the <tt>Formatter</tt> for this <tt>Handler</tt>.
     * @return the <tt>Formatter</tt> (may be null).
     */
    public Formatter getFormatter() {
        return formatter;
    }

    /**
     * Set the character encoding used by this <tt>Handler</tt>.
     * <p>
     * The encoding should be set before any <tt>LogRecords</tt> are written
     * to the <tt>Handler</tt>.
     *
     * @param encoding  The name of a supported character encoding.
     *        May be null, to indicate the default platform encoding.
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have <tt>LoggingPermission("control")</tt>.
     * @exception  UnsupportedEncodingException if the named encoding is
     *          not supported.
     */
    public synchronized void setEncoding(String encoding)
                        throws SecurityException, java.io.UnsupportedEncodingException {
        checkPermission();
        if (encoding != null) {
            try {
                if(!java.nio.charset.Charset.isSupported(encoding)) {
                    throw new UnsupportedEncodingException(encoding);
                }
            } catch (java.nio.charset.IllegalCharsetNameException e) {
                throw new UnsupportedEncodingException(encoding);
            }
        }
        this.encoding = encoding;
    }

    /**
     * Return the character encoding for this <tt>Handler</tt>.
     *
     * @return  The encoding name.  May be null, which indicates the
     *          default encoding should be used.
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * Set a <tt>Filter</tt> to control output on this <tt>Handler</tt>.
     * <P>
     * For each call of <tt>publish</tt> the <tt>Handler</tt> will call
     * this <tt>Filter</tt> (if it is non-null) to check if the
     * <tt>LogRecord</tt> should be published or discarded.
     *
     * @param   newFilter  a <tt>Filter</tt> object (may be null)
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have <tt>LoggingPermission("control")</tt>.
     */
    public synchronized void setFilter(Filter newFilter) throws SecurityException {
        checkPermission();
        filter = newFilter;
    }

    /**
     * Get the current <tt>Filter</tt> for this <tt>Handler</tt>.
     *
     * @return  a <tt>Filter</tt> object (may be null)
     */
    public Filter getFilter() {
        return filter;
    }

    /**
     * Define an ErrorManager for this Handler.
     * <p>
     * The ErrorManager's "error" method will be invoked if any
     * errors occur while using this Handler.
     *
     * @param em  the new ErrorManager
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have <tt>LoggingPermission("control")</tt>.
     */
    public synchronized void setErrorManager(ErrorManager em) {
        checkPermission();
        if (em == null) {
           throw new NullPointerException();
        }
        errorManager = em;
    }

    /**
     * Retrieves the ErrorManager for this Handler.
     *
     * @return the ErrorManager for this Handler
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have <tt>LoggingPermission("control")</tt>.
     */
    public ErrorManager getErrorManager() {
        checkPermission();
        return errorManager;
    }

   /**
     * Protected convenience method to report an error to this Handler's
     * ErrorManager.  Note that this method retrieves and uses the ErrorManager
     * without doing a security check.  It can therefore be used in
     * environments where the caller may be non-privileged.
     *
     * @param msg    a descriptive string (may be null)
     * @param ex     an exception (may be null)
     * @param code   an error code defined in ErrorManager
     */
    protected void reportError(String msg, Exception ex, int code) {
        try {
            errorManager.error(msg, ex, code);
        } catch (Exception ex2) {
            System.err.println("Handler.reportError caught:");
            ex2.printStackTrace();
        }
    }

    /**
     * Set the log level specifying which message levels will be
     * logged by this <tt>Handler</tt>.  Message levels lower than this
     * value will be discarded.
     * <p>
     * The intention is to allow developers to turn on voluminous
     * logging, but to limit the messages that are sent to certain
     * <tt>Handlers</tt>.
     *
     * @param newLevel   the new value for the log level
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have <tt>LoggingPermission("control")</tt>.
     */
    public synchronized void setLevel(Level newLevel) throws SecurityException {
        if (newLevel == null) {
            throw new NullPointerException();
        }
        checkPermission();
        logLevel = newLevel;
    }

    /**
     * Get the log level specifying which messages will be
     * logged by this <tt>Handler</tt>.  Message levels lower
     * than this level will be discarded.
     * @return  the level of messages being logged.
     */
    public Level getLevel() {
        return logLevel;
    }

    /**
     * Check if this <tt>Handler</tt> would actually log a given <tt>LogRecord</tt>.
     * <p>
     * This method checks if the <tt>LogRecord</tt> has an appropriate
     * <tt>Level</tt> and  whether it satisfies any <tt>Filter</tt>.  It also
     * may make other <tt>Handler</tt> specific checks that might prevent a
     * handler from logging the <tt>LogRecord</tt>. It will return false if
     * the <tt>LogRecord</tt> is null.
     *
     * @param record  a <tt>LogRecord</tt>
     * @return true if the <tt>LogRecord</tt> would be logged.
     *
     */
    public boolean isLoggable(LogRecord record) {
        final int levelValue = getLevel().intValue();
        if (record.getLevel().intValue() < levelValue || levelValue == offValue) {
            return false;
        }
        final Filter filter = getFilter();
        if (filter == null) {
            return true;
        }
        return filter.isLoggable(record);
    }

    // Package-private support method for security checks.
    // We check that the caller has appropriate security privileges
    // to update Handler state and if not throw a SecurityException.
    void checkPermission() throws SecurityException {
        manager.checkPermission();
    }
}
