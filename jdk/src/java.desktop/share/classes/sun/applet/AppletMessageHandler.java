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

import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.text.MessageFormat;

/**
 * An hanlder of localized messages.
 *
 * @author      Koji Uno
 */
class AppletMessageHandler {
    private static ResourceBundle rb;
    private String baseKey = null;

    static {
        try {
            rb = ResourceBundle.getBundle
                ("sun.applet.resources.MsgAppletViewer");
        } catch (MissingResourceException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
    }

    AppletMessageHandler(String baseKey) {
        this.baseKey = baseKey;
    }

    String getMessage(String key) {
        return rb.getString(getQualifiedKey(key));
    }

    String getMessage(String key, Object arg){
        String basemsgfmt = rb.getString(getQualifiedKey(key));
        MessageFormat msgfmt = new MessageFormat(basemsgfmt);
        Object msgobj[] = new Object[1];
        if (arg == null) {
            arg = "null"; // mimic java.io.PrintStream.print(String)
        }
        msgobj[0] = arg;
        return msgfmt.format(msgobj);
    }

    String getMessage(String key, Object arg1, Object arg2) {
        String basemsgfmt = rb.getString(getQualifiedKey(key));
        MessageFormat msgfmt = new MessageFormat(basemsgfmt);
        Object msgobj[] = new Object[2];
        if (arg1 == null) {
            arg1 = "null";
        }
        if (arg2 == null) {
            arg2 = "null";
        }
        msgobj[0] = arg1;
        msgobj[1] = arg2;
        return msgfmt.format(msgobj);
    }

    String getMessage(String key, Object arg1, Object arg2, Object arg3) {
        String basemsgfmt = rb.getString(getQualifiedKey(key));
        MessageFormat msgfmt = new MessageFormat(basemsgfmt);
        Object msgobj[] = new Object[3];
        if (arg1 == null) {
            arg1 = "null";
        }
        if (arg2 == null) {
            arg2 = "null";
        }
        if (arg3 == null) {
            arg3 = "null";
        }
        msgobj[0] = arg1;
        msgobj[1] = arg2;
        msgobj[2] = arg3;
        return msgfmt.format(msgobj);
    }

    String getMessage(String key, Object arg[]) {
        String basemsgfmt = rb.getString(getQualifiedKey(key));
        MessageFormat msgfmt = new MessageFormat(basemsgfmt);
        return msgfmt.format(arg);
    }

    String getQualifiedKey(String subKey) {
        return baseKey + "." + subKey;
    }
}
