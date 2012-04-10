/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.addon.accessors;

import com.sun.codemodel.internal.JAnnotationUse;
import com.sun.codemodel.internal.JClass;
import java.io.IOException;
import com.sun.tools.internal.xjc.BadCommandLineException;
import com.sun.tools.internal.xjc.Options;
import com.sun.tools.internal.xjc.Plugin;
import com.sun.tools.internal.xjc.outline.ClassOutline;
import com.sun.tools.internal.xjc.outline.Outline;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import org.xml.sax.ErrorHandler;

/**
 * Generates synchronized methods.
 *
 * @author
 *     Martin Grebac (martin.grebac@sun.com)
 */
public class PluginImpl extends Plugin {

    public String getOptionName() {
        return "Xpropertyaccessors";
    }

    public String getUsage() {
        return "  -Xpropertyaccessors :  Use XmlAccessType PROPERTY instead of FIELD for generated classes";
    }

    @Override
    public int parseArgument(Options opt, String[] args, int i) throws BadCommandLineException, IOException {
        return 0;   // no option recognized
    }

    public boolean run( Outline model, Options opt, ErrorHandler errorHandler ) {

        for( ClassOutline co : model.getClasses() ) {
            Iterator<JAnnotationUse> ann = co.ref.annotations().iterator();
            while (ann.hasNext()) {
               try {
                    JAnnotationUse a = ann.next();
                    Field clazzField = a.getClass().getDeclaredField("clazz");
                    clazzField.setAccessible(true);
                    JClass cl = (JClass) clazzField.get(a);
                    if (cl.equals(model.getCodeModel()._ref(XmlAccessorType.class))) {
                        a.param("value", XmlAccessType.PROPERTY);
                        break;
                    }
                } catch (IllegalArgumentException ex) {
                    Logger.getLogger(PluginImpl.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IllegalAccessException ex) {
                    Logger.getLogger(PluginImpl.class.getName()).log(Level.SEVERE, null, ex);
                } catch (NoSuchFieldException ex) {
                    Logger.getLogger(PluginImpl.class.getName()).log(Level.SEVERE, null, ex);
                } catch (SecurityException ex) {
                    Logger.getLogger(PluginImpl.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return true;
    }

}
