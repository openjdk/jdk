/*
 * Copyright 2000-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

/**
 * Stream based logging <tt>Handler</tt>.
 * <p>
 * This is primarily intended as a base class or support class to
 * be used in implementing other logging <tt>Handlers</tt>.
 * <p>
 * <tt>LogRecords</tt> are published to a given <tt>java.io.OutputStream</tt>.
 * <p>
 * <b>Configuration:</b>
 * By default each <tt>StreamHandler</tt> is initialized using the following
 * <tt>LogManager</tt> configuration properties.  If properties are not defined
 * (or have invalid values) then the specified default values are used.
 * <ul>
 * <li>   java.util.logging.StreamHandler.level
 *        specifies the default level for the <tt>Handler</tt>
 *        (defaults to <tt>Level.INFO</tt>).
 * <li>   java.util.logging.StreamHandler.filter
 *        specifies the name of a <tt>Filter</tt> class to use
 *         (defaults to no <tt>Filter</tt>).
 * <li>   java.util.logging.StreamHandler.formatter
 *        specifies the name of a <tt>Formatter</tt> class to use
 *        (defaults to <tt>java.util.logging.SimpleFormatter</tt>).
 * <li>   java.util.logging.StreamHandler.encoding
 *        the name of the character set encoding to use (defaults to
 *        the default platform encoding).
 * </ul>
 *
 * @since 1.4
 */

public class StreamHandler extends Handler {
    private LogManager manager = LogManager.getLogManager();
    private OutputStream output;
    private boolean doneHeader;
    private Writer writer;

    // Private method to configure a StreamHandler from LogManager
    // properties and/or default values as specified in the class
    // javadoc.
    private void configure() {
        LogManager manager = LogManager.getLogManager();
        String cname = getClass().getName();

        setLevel(manager.getLevelProperty(cname +".level", Level.INFO));
        setFilter(manager.getFilterProperty(cname +".filter", null));
        setFormatter(manager.getFormatterProperty(cname +".formatter", new SimpleFormatter()));
        try {
            setEncoding(manager.getStringProperty(cname +".encoding", null));
        } catch (Exception ex) {
            try {
                setEncoding(null);
            } catch (Exception ex2) {
                // doing a setEncoding with null should always work.
                // assert false;
            }
        }
    }

    /**
     * Create a <tt>StreamHandler</tt>, with no current output stream.
     */
    public StreamHandler() {
        sealed = false;
        configure();
        sealed = true;
    }

    /**
     * Create a <tt>StreamHandler</tt> with a given <tt>Formatter</tt>
     * and output stream.
     * <p>
     * @param out         the target output stream
     * @param formatter   Formatter to be used to format output
     */
    public StreamHandler(OutputStream out, Formatter formatter) {
        sealed = false;
        configure();
        setFormatter(formatter);
        setOutputStream(out);
        sealed = true;
    }

    /**
     * Change the output stream.
     * <P>
     * If there is a current output stream then the <tt>Formatter</tt>'s
     * tail string is written and the stream is flushed and closed.
     * Then the output stream is replaced with the new output stream.
     *
     * @param out   New output stream.  May not be null.
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have <tt>LoggingPermission("control")</tt>.
     */
    protected synchronized void setOutputStream(OutputStream out) throws SecurityException {
        if (out == null) {
            throw new NullPointerException();
        }
        flushAndClose();
        output = out;
        doneHeader = false;
        String encoding = getEncoding();
        if (encoding == null) {
            writer = new OutputStreamWriter(output);
        } else {
            try {
                writer = new OutputStreamWriter(output, encoding);
            } catch (UnsupportedEncodingException ex) {
                // This shouldn't happen.  The setEncoding method
                // should have validated that the encoding is OK.
                throw new Error("Unexpected exception " + ex);
            }
        }
    }

    /**
     * Set (or change) the character encoding used by this <tt>Handler</tt>.
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
    public void setEncoding(String encoding)
                        throws SecurityException, java.io.UnsupportedEncodingException {
        super.setEncoding(encoding);
        if (output == null) {
            return;
        }
        // Replace the current writer with a writer for the new encoding.
        flush();
        if (encoding == null) {
            writer = new OutputStreamWriter(output);
        } else {
            writer = new OutputStreamWriter(output, encoding);
        }
    }

    /**
     * Format and publish a <tt>LogRecord</tt>.
     * <p>
     * The <tt>StreamHandler</tt> first checks if there is an <tt>OutputStream</tt>
     * and if the given <tt>LogRecord</tt> has at least the required log level.
     * If not it silently returns.  If so, it calls any associated
     * <tt>Filter</tt> to check if the record should be published.  If so,
     * it calls its <tt>Formatter</tt> to format the record and then writes
     * the result to the current output stream.
     * <p>
     * If this is the first <tt>LogRecord</tt> to be written to a given
     * <tt>OutputStream</tt>, the <tt>Formatter</tt>'s "head" string is
     * written to the stream before the <tt>LogRecord</tt> is written.
     *
     * @param  record  description of the log event. A null record is
     *                 silently ignored and is not published
     */
    public synchronized void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        String msg;
        try {
            msg = getFormatter().format(record);
        } catch (Exception ex) {
            // We don't want to throw an exception here, but we
            // report the exception to any registered ErrorManager.
            reportError(null, ex, ErrorManager.FORMAT_FAILURE);
            return;
        }

        try {
            if (!doneHeader) {
                writer.write(getFormatter().getHead(this));
                doneHeader = true;
            }
            writer.write(msg);
        } catch (Exception ex) {
            // We don't want to throw an exception here, but we
            // report the exception to any registered ErrorManager.
            reportError(null, ex, ErrorManager.WRITE_FAILURE);
        }
    }


    /**
     * Check if this <tt>Handler</tt> would actually log a given <tt>LogRecord</tt>.
     * <p>
     * This method checks if the <tt>LogRecord</tt> has an appropriate level and
     * whether it satisfies any <tt>Filter</tt>.  It will also return false if
     * no output stream has been assigned yet or the LogRecord is Null.
     * <p>
     * @param record  a <tt>LogRecord</tt>
     * @return true if the <tt>LogRecord</tt> would be logged.
     *
     */
    public boolean isLoggable(LogRecord record) {
        if (writer == null || record == null) {
            return false;
        }
        return super.isLoggable(record);
    }

    /**
     * Flush any buffered messages.
     */
    public synchronized void flush() {
        if (writer != null) {
            try {
                writer.flush();
            } catch (Exception ex) {
                // We don't want to throw an exception here, but we
                // report the exception to any registered ErrorManager.
                reportError(null, ex, ErrorManager.FLUSH_FAILURE);
            }
        }
    }

    private synchronized void flushAndClose() throws SecurityException {
        checkAccess();
        if (writer != null) {
            try {
                if (!doneHeader) {
                    writer.write(getFormatter().getHead(this));
                    doneHeader = true;
                }
                writer.write(getFormatter().getTail(this));
                writer.flush();
                writer.close();
            } catch (Exception ex) {
                // We don't want to throw an exception here, but we
                // report the exception to any registered ErrorManager.
                reportError(null, ex, ErrorManager.CLOSE_FAILURE);
            }
            writer = null;
            output = null;
        }
    }

    /**
     * Close the current output stream.
     * <p>
     * The <tt>Formatter</tt>'s "tail" string is written to the stream before it
     * is closed.  In addition, if the <tt>Formatter</tt>'s "head" string has not
     * yet been written to the stream, it will be written before the
     * "tail" string.
     *
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have LoggingPermission("control").
     */
    public synchronized void close() throws SecurityException {
        flushAndClose();
    }
}
