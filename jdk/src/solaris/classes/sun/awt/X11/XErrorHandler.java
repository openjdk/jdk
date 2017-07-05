/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
package sun.awt.X11;

public abstract class XErrorHandler {

    /*
     * Called under AWT lock
     */
    public abstract int handleError(long display, XErrorEvent err);

    /*
     * Forwards all the errors to saved error handler (which was
     * set before XToolkit had been initialized).
     */
    public static class XBaseErrorHandler extends XErrorHandler {
        @Override
        public int handleError(long display, XErrorEvent err) {
            return XToolkit.SAVED_ERROR_HANDLER(display, err);
        }
    }

    /*
     * Instead of validating window id, we simply call XGetWindowProperty,
     * but temporary install this function as the error handler to ignore
     * BadWindow error.
     */
    public static class IgnoreBadWindowHandler extends XBaseErrorHandler {
        @Override
        public int handleError(long display, XErrorEvent err) {
            if (err.get_error_code() == XConstants.BadWindow) {
                return 0;
            }
            return super.handleError(display, err);
        }
        // Shared instance
        private static IgnoreBadWindowHandler theInstance = new IgnoreBadWindowHandler();
        public static IgnoreBadWindowHandler getInstance() {
            return theInstance;
        }
    }

    public static class VerifyChangePropertyHandler extends XBaseErrorHandler {
        @Override
        public int handleError(long display, XErrorEvent err) {
            if (err.get_request_code() == XProtocolConstants.X_ChangeProperty) {
                return 0;
            }
            return super.handleError(display, err);
        }
        // Shared instance
        private static IgnoreBadWindowHandler theInstance = new IgnoreBadWindowHandler();
        public static IgnoreBadWindowHandler getInstance() {
            return theInstance;
        }
    }
}
