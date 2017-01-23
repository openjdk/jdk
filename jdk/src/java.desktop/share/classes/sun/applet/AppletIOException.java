/*
 * Copyright (c) 1996, 2014, Oracle and/or its affiliates. All rights reserved.
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

package sun.applet;

import java.io.IOException;

/**
 * An applet IO exception.
 *
 * @author      Koji Uno
 *
 * @deprecated The Applet API is deprecated. See the
 * <a href="../../java/applet/package-summary.html"> java.applet package
 * documentation</a> for further information.
 */
@SuppressWarnings("serial") // JDK implementation class
@Deprecated(since = "9")
public class AppletIOException extends IOException {
    private String key = null;
    private Object msgobj = null;

    public AppletIOException(String key) {
        super(key);
        this.key = key;

    }
    public AppletIOException(String key, Object arg) {
        this(key);
        msgobj = arg;
    }

    public String getLocalizedMessage() {
        if( msgobj != null)
            return amh.getMessage(key, msgobj);
        else
            return amh.getMessage(key);
    }

    private static AppletMessageHandler amh = new AppletMessageHandler("appletioexception");

}
