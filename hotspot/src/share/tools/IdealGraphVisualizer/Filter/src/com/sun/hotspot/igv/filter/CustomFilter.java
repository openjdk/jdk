/*
 * Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.filter;

import com.sun.hotspot.igv.graph.Diagram;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.Repository;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 *
 * @author Thomas Wuerthinger
 */
public class CustomFilter extends AbstractFilter {

    public static final String JAVASCRIPT_HELPER_ID = "JavaScriptHelper";
    private static ScriptEngineAbstraction engine;
    private String code;
    private String name;

    public CustomFilter(String name, String code) {
        this.name = name;
        this.code = code;
        getProperties().setProperty("name", name);
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public void setName(String s) {
        name = s;
        fireChangedEvent();
    }

    public void setCode(String s) {
        code = s;
        fireChangedEvent();
    }

    @Override
    public OpenCookie getEditor() {
        return new OpenCookie() {

            public void open() {
                openInEditor();
            }
        };
    }

    public boolean openInEditor() {
        EditFilterDialog dialog = new EditFilterDialog(CustomFilter.this);
        dialog.setVisible(true);
        return dialog.wasAccepted();
    }

    @Override
    public String toString() {
        return getName();
    }

    public static ScriptEngineAbstraction getEngine() {
        if (engine == null) {

            ScriptEngineAbstraction chosen = null;
            try {
                Collection<? extends ScriptEngineAbstraction> list = Lookup.getDefault().lookupAll(ScriptEngineAbstraction.class);
                for (ScriptEngineAbstraction s : list) {
                    if (s.initialize(getJsHelperText())) {
                        if (chosen == null || !(chosen instanceof JavaSE6ScriptEngine)) {
                            chosen = s;
                        }
                    }
                }
            } catch (NoClassDefFoundError ncdfe) {
                Logger.getLogger("global").log(Level.SEVERE, null, ncdfe);
            }

            if (chosen == null) {
                NotifyDescriptor message = new NotifyDescriptor.Message("Could not find a scripting engine. Please make sure that the Rhino scripting engine is available. Otherwise filter cannot be used.", NotifyDescriptor.ERROR_MESSAGE);
                DialogDisplayer.getDefault().notifyLater(message);
                chosen = new NullScriptEngine();
            }

            engine = chosen;
        }

        return engine;
    }

    private static String getJsHelperText() {
        InputStream is = null;
        StringBuilder sb = new StringBuilder("importPackage(Packages.com.sun.hotspot.igv.filter);importPackage(Packages.com.sun.hotspot.igv.graph);importPackage(Packages.com.sun.hotspot.igv.data);importPackage(Packages.com.sun.hotspot.igv.util);importPackage(java.awt);");
        try {
            FileSystem fs = Repository.getDefault().getDefaultFileSystem();
            FileObject fo = fs.getRoot().getFileObject(JAVASCRIPT_HELPER_ID);
            is = fo.getInputStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            String s;
            while ((s = r.readLine()) != null) {
                sb.append(s);
                sb.append("\n");
            }

        } catch (IOException ex) {
            Logger.getLogger("global").log(Level.SEVERE, null, ex);
        } finally {
            try {
                is.close();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        return sb.toString();
    }

    public void apply(Diagram d) {
        getEngine().execute(d, code);
    }
}
