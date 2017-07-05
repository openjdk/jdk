/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.xjc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;

import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.ValidatorHandler;

import com.sun.xml.internal.bind.v2.WellKnownNamespace;

import org.xml.sax.SAXException;

/**
 * Wraps a JAXP {@link Schema} object and lazily instanciate it.
 *
 * Also fix bug 6246922.
 *
 * This object is thread-safe. There should be only one instance of
 * this for the whole VM.
 *
 * @author Kohsuke Kawaguchi
 */
public final class SchemaCache {

    private Schema schema;

    private final URL source;

    public SchemaCache(URL source) {
        this.source = source;
    }

    public ValidatorHandler newValidator() {
        synchronized(this) {
            if(schema==null) {
                try {
                    schema = SchemaFactory.newInstance(WellKnownNamespace.XML_SCHEMA).newSchema(source);
                } catch (SAXException e) {
                    // we make sure that the schema is correct before we ship.
                    throw new AssertionError(e);
                }
            }
        }

        ValidatorHandler handler = schema.newValidatorHandler();
        fixValidatorBug6246922(handler);

        return handler;
    }


    /**
     * Fix the bug 6246922 if we are running inside Tiger.
     */
    private void fixValidatorBug6246922(ValidatorHandler handler) {
        try {
            Field f = handler.getClass().getDeclaredField("errorReporter");
            f.setAccessible(true);
            Object errorReporter = f.get(handler);

            Method get = errorReporter.getClass().getDeclaredMethod("getMessageFormatter",String.class);
            Object currentFormatter = get.invoke(errorReporter,"http://www.w3.org/TR/xml-schema-1");
            if(currentFormatter!=null)
                return;

            // otherwise attempt to set
            Method put = null;
            for( Method m : errorReporter.getClass().getDeclaredMethods() ) {
                if(m.getName().equals("putMessageFormatter")) {
                    put = m;
                    break;
                }
            }
            if(put==null)       return; // unable to find the putMessageFormatter

            ClassLoader cl = errorReporter.getClass().getClassLoader();
            String className = "com.sun.org.apache.xerces.internal.impl.xs.XSMessageFormatter";
            Class xsformatter;
            if(cl==null) {
                xsformatter = Class.forName(className);
            } else {
                xsformatter = cl.loadClass(className);
            }

            put.invoke(errorReporter,"http://www.w3.org/TR/xml-schema-1",xsformatter.newInstance());
        } catch( Throwable t ) {
            // this code is heavily relying on an implementation detail of JAXP RI,
            // so any error is likely because of the incompatible change in it.
            // don't die if that happens. Just continue. The worst case is a illegible
            // error messages, which are much better than not compiling schemas at all.
        }
    }
}
