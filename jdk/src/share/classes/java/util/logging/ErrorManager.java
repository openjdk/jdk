/*
 * Copyright 2001-2004 Sun Microsystems, Inc.  All Rights Reserved.
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
 * ErrorManager objects can be attached to Handlers to process
 * any error that occurs on a Handler during Logging.
 * <p>
 * When processing logging output, if a Handler encounters problems
 * then rather than throwing an Exception back to the issuer of
 * the logging call (who is unlikely to be interested) the Handler
 * should call its associated ErrorManager.
 */

public class ErrorManager {
   private boolean reported = false;

    /*
     * We declare standard error codes for important categories of errors.
     */

    /**
     * GENERIC_FAILURE is used for failure that don't fit
     * into one of the other categories.
     */
    public final static int GENERIC_FAILURE = 0;
    /**
     * WRITE_FAILURE is used when a write to an output stream fails.
     */
    public final static int WRITE_FAILURE = 1;
    /**
     * FLUSH_FAILURE is used when a flush to an output stream fails.
     */
    public final static int FLUSH_FAILURE = 2;
    /**
     * CLOSE_FAILURE is used when a close of an output stream fails.
     */
    public final static int CLOSE_FAILURE = 3;
    /**
     * OPEN_FAILURE is used when an open of an output stream fails.
     */
    public final static int OPEN_FAILURE = 4;
    /**
     * FORMAT_FAILURE is used when formatting fails for any reason.
     */
    public final static int FORMAT_FAILURE = 5;

    /**
     * The error method is called when a Handler failure occurs.
     * <p>
     * This method may be overridden in subclasses.  The default
     * behavior in this base class is that the first call is
     * reported to System.err, and subsequent calls are ignored.
     *
     * @param msg    a descriptive string (may be null)
     * @param ex     an exception (may be null)
     * @param code   an error code defined in ErrorManager
     */
    public synchronized void error(String msg, Exception ex, int code) {
        if (reported) {
            // We only report the first error, to avoid clogging
            // the screen.
            return;
        }
        reported = true;
        String text = "java.util.logging.ErrorManager: " + code;
        if (msg != null) {
            text = text + ": " + msg;
        }
        System.err.println(text);
        if (ex != null) {
            ex.printStackTrace();
        }
    }
}
