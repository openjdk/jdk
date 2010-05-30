/*
 * Copyright (c) 1995, 2005, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.io.*;
import java.net.URL;
import java.net.MalformedURLException;
import java.awt.*;
import java.applet.*;
import sun.tools.jar.*;


/**
 * Sample applet panel class. The panel manages and manipulates the
 * applet as it is being loaded. It forks a seperate thread in a new
 * thread group to call the applet's init(), start(), stop(), and
 * destroy() methods.
 *
 * @author      Arthur van Hoff
 */
class AppletViewerPanel extends AppletPanel {

    /* Are we debugging? */
    static boolean debug = false;

    /**
     * The document url.
     */
    URL documentURL;

    /**
     * The base url.
     */
    URL baseURL;

    /**
     * The attributes of the applet.
     */
    Hashtable atts;

    /*
     * JDK 1.1 serialVersionUID
     */
    private static final long serialVersionUID = 8890989370785545619L;

    /**
     * Construct an applet viewer and start the applet.
     */
    AppletViewerPanel(URL documentURL, Hashtable atts) {
        this.documentURL = documentURL;
        this.atts = atts;

        String att = getParameter("codebase");
        if (att != null) {
            if (!att.endsWith("/")) {
                att += "/";
            }
            try {
                baseURL = new URL(documentURL, att);
            } catch (MalformedURLException e) {
            }
        }
        if (baseURL == null) {
            String file = documentURL.getFile();
            int i = file.lastIndexOf('/');
            if (i >= 0 && i < file.length() - 1) {
                try {
                    baseURL = new URL(documentURL, file.substring(0, i + 1));
                } catch (MalformedURLException e) {
                }
            }
        }

        // when all is said & done, baseURL shouldn't be null
        if (baseURL == null)
                baseURL = documentURL;


    }

    /**
     * Get an applet parameter.
     */
    public String getParameter(String name) {
        return (String)atts.get(name.toLowerCase());
    }

    /**
     * Get the document url.
     */
    public URL getDocumentBase() {
        return documentURL;

    }

    /**
     * Get the base url.
     */
    public URL getCodeBase() {
        return baseURL;
    }

    /**
     * Get the width.
     */
    public int getWidth() {
        String w = getParameter("width");
        if (w != null) {
            return Integer.valueOf(w).intValue();
        }
        return 0;
    }


    /**
     * Get the height.
     */
    public int getHeight() {
        String h = getParameter("height");
        if (h != null) {
            return Integer.valueOf(h).intValue();
        }
        return 0;
    }

    /**
     * Get initial_focus
     */
    public boolean hasInitialFocus()
    {

        // 6234219: Do not set initial focus on an applet
        // during startup if applet is targeted for
        // JDK 1.1/1.2. [stanley.ho]
        if (isJDK11Applet() || isJDK12Applet())
            return false;

        String initialFocus = getParameter("initial_focus");

        if (initialFocus != null)
        {
            if (initialFocus.toLowerCase().equals("false"))
                return false;
        }

        return true;
    }

    /**
     * Get the code parameter
     */
    public String getCode() {
        return getParameter("code");
    }


    /**
     * Return the list of jar files if specified.
     * Otherwise return null.
     */
    public String getJarFiles() {
        return getParameter("archive");
    }

    /**
     * Return the value of the object param
     */
    public String getSerializedObject() {
        return getParameter("object");// another name?
    }


    /**
     * Get the applet context. For now this is
     * also implemented by the AppletPanel class.
     */
    public AppletContext getAppletContext() {
        return (AppletContext)getParent();
    }

    static void debug(String s) {
        if(debug)
            System.err.println("AppletViewerPanel:::" + s);
    }

    static void debug(String s, Throwable t) {
        if(debug) {
            t.printStackTrace();
            debug(s);
        }
    }
}
