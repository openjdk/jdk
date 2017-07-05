/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

// This class serves as the base class for all the wrappers.
import sun.util.logging.PlatformLogger;

abstract class XWrapperBase {
    static final PlatformLogger log = PlatformLogger.getLogger("sun.awt.X11.wrappers");

    public String toString() {
        String ret = "";

        ret += getName() + " = " + getFieldsAsString();

        return ret;
    }

    String getFieldsAsString() {
        return "";
    }

    String getName() {
        return "XWrapperBase";
    }
    public void zero() {
        log.finest("Cleaning memory");
        if (getPData() != 0) {
            XlibWrapper.unsafe.setMemory(getPData(), (long)getDataSize(), (byte)0);
        }
    }
    public abstract int getDataSize();
    String getWindow(long window) {
        XBaseWindow w = XToolkit.windowToXWindow(window);
        if (w == null) {
            return Long.toHexString(window);
        } else {
            return w.toString();
        }
    }
    public abstract long getPData();
    public XEvent clone() {
        long copy = XlibWrapper.unsafe.allocateMemory(getDataSize());
        XlibWrapper.unsafe.copyMemory(getPData(), copy, getDataSize());
        return new XEvent(copy);
    }
}
