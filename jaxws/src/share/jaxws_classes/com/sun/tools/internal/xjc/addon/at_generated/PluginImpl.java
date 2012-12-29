/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.addon.at_generated;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.sun.codemodel.internal.JAnnotatable;
import com.sun.codemodel.internal.JClass;
import com.sun.codemodel.internal.JFieldVar;
import com.sun.codemodel.internal.JMethod;
import com.sun.tools.internal.xjc.Driver;
import com.sun.tools.internal.xjc.Options;
import com.sun.tools.internal.xjc.Plugin;
import com.sun.tools.internal.xjc.outline.ClassOutline;
import com.sun.tools.internal.xjc.outline.EnumOutline;
import com.sun.tools.internal.xjc.outline.Outline;

import org.xml.sax.ErrorHandler;

/**
 * {@link Plugin} that marks the generated code by using JSR-250's '@Generated'.
 *
 * @author Kohsuke Kawaguchi
 */
public class PluginImpl extends Plugin {

    public String getOptionName() {
        return "mark-generated";
    }

    public String getUsage() {
        return "  -mark-generated    :  mark the generated code as @javax.annotation.Generated";
    }

    private JClass annotation;

    public boolean run( Outline model, Options opt, ErrorHandler errorHandler ) {
        // we want this to work without requiring JSR-250 jar.
        annotation = model.getCodeModel().ref("javax.annotation.Generated");

        for( ClassOutline co : model.getClasses() )
            augument(co);
        for( EnumOutline eo : model.getEnums() )
            augument(eo);

        //TODO: process generated ObjectFactory classes?

        return true;
    }

    private void augument(EnumOutline eo) {
        annotate(eo.clazz);
    }

    /**
     * Adds "@Generated" to the classes, methods, and fields.
     */
    private void augument(ClassOutline co) {
        annotate(co.implClass);
        for (JMethod m : co.implClass.methods())
            annotate(m);
        for (JFieldVar f : co.implClass.fields().values())
            annotate(f);
    }

    private void annotate(JAnnotatable m) {
        m.annotate(annotation)
                .param("value",Driver.class.getName())
                .param("date", getISO8601Date())
                .param("comments", "JAXB RI v" + Options.getBuildID());
    }

    // cache the timestamp so that all the @Generated annotations match
    private String date = null;

    /**
     * calculate the date value in ISO8601 format for the @Generated annotation
     * @return the date value
     */
    private String getISO8601Date() {
        if(date==null) {
            StringBuffer tstamp = new StringBuffer();
            tstamp.append((new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ")).format(new Date()));
            // hack to get ISO 8601 style timezone - is there a better way that doesn't require
            // a bunch of timezone offset calculations?
            tstamp.insert(tstamp.length()-2, ':');
            date = tstamp.toString();
        }
        return date;
    }
}
