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

/**
 * A Formatter provides support for formatting LogRecords.
 * <p>
 * Typically each logging Handler will have a Formatter associated
 * with it.  The Formatter takes a LogRecord and converts it to
 * a string.
 * <p>
 * Some formatters (such as the XMLFormatter) need to wrap head
 * and tail strings around a set of formatted records. The getHeader
 * and getTail methods can be used to obtain these strings.
 *
 * @since 1.4
 */

public abstract class Formatter {

    /**
     * Construct a new formatter.
     */
    protected Formatter() {
    }

    /**
     * Format the given log record and return the formatted string.
     * <p>
     * The resulting formatted String will normally include a
     * localized and formatted version of the LogRecord's message field.
     * It is recommended to use the {@link Formatter#formatMessage}
     * convenience method to localize and format the message field.
     *
     * @param record the log record to be formatted.
     * @return the formatted log record
     */
    public abstract String format(LogRecord record);


    /**
     * Return the header string for a set of formatted records.
     * <p>
     * This base class returns an empty string, but this may be
     * overridden by subclasses.
     *
     * @param   h  The target handler (can be null)
     * @return  header string
     */
    public String getHead(Handler h) {
        return "";
    }

    /**
     * Return the tail string for a set of formatted records.
     * <p>
     * This base class returns an empty string, but this may be
     * overridden by subclasses.
     *
     * @param   h  The target handler (can be null)
     * @return  tail string
     */
    public String getTail(Handler h) {
        return "";
    }


    /**
     * Localize and format the message string from a log record.  This
     * method is provided as a convenience for Formatter subclasses to
     * use when they are performing formatting.
     * <p>
     * The message string is first localized to a format string using
     * the record's ResourceBundle.  (If there is no ResourceBundle,
     * or if the message key is not found, then the key is used as the
     * format string.)  The format String uses java.text style
     * formatting.
     * <ul>
     * <li>If there are no parameters, no formatter is used.
     * <li>Otherwise, if the string contains "{0" then
     *     java.text.MessageFormat  is used to format the string.
     * <li>Otherwise no formatting is performed.
     * </ul>
     * <p>
     *
     * @param  record  the log record containing the raw message
     * @return   a localized and formatted message
     */
    public synchronized String formatMessage(LogRecord record) {
        String format = record.getMessage();
        java.util.ResourceBundle catalog = record.getResourceBundle();
        if (catalog != null) {
            try {
                format = catalog.getString(record.getMessage());
            } catch (java.util.MissingResourceException ex) {
                // Drop through.  Use record message as format
                format = record.getMessage();
            }
        }
        // Do the formatting.
        try {
            Object parameters[] = record.getParameters();
            if (parameters == null || parameters.length == 0) {
                // No parameters.  Just return format string.
                return format;
            }
            // Is it a java.text style format?
            // Ideally we could match with
            // Pattern.compile("\\{\\d").matcher(format).find())
            // However the cost is 14% higher, so we cheaply check for
            // 1 of the first 4 parameters
            if (format.indexOf("{0") >= 0 || format.indexOf("{1") >=0 ||
                        format.indexOf("{2") >=0|| format.indexOf("{3") >=0) {
                return java.text.MessageFormat.format(format, parameters);
            }
            return format;

        } catch (Exception ex) {
            // Formatting failed: use localized format string.
            return format;
        }
    }
}
