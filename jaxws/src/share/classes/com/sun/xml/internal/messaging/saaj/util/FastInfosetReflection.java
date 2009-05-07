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
/*
 * $Id: FastInfosetReflection.java,v 1.2 2006/06/06 18:46:01 sandoz Exp $
 * $Revision: 1.2 $
 * $Date: 2006/06/06 18:46:01 $
 */



package com.sun.xml.internal.messaging.saaj.util;

import java.lang.reflect.*;
import javax.xml.transform.Source;
import javax.xml.transform.Result;
import java.io.InputStream;
import java.io.OutputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 *
 * @author Santiago.PericasGeertsen@sun.com
 * @author Paul.Sandoz@sun.com
 */
public class FastInfosetReflection {

    /**
     * FI DOMDocumentParser constructor using reflection.
     */
    static Constructor fiDOMDocumentParser_new;

    /**
     * FI <code>DOMDocumentParser.parse()</code> method via reflection.
     */
    static Method fiDOMDocumentParser_parse;

    /**
     * FI DOMDocumentSerializer constructor using reflection.
     */
    static Constructor fiDOMDocumentSerializer_new;

    /**
     * FI <code>FastInfosetSource.serialize(Document)</code> method via reflection.
     */
    static Method fiDOMDocumentSerializer_serialize;

    /**
     * FI <code>FastInfosetSource.setOutputStream(OutputStream)</code> method via reflection.
     */
    static Method fiDOMDocumentSerializer_setOutputStream;

    /**
     * FI FastInfosetSource constructor using reflection.
     */
    static Class fiFastInfosetSource_class;

    /**
     * FI FastInfosetSource constructor using reflection.
     */
    static Constructor fiFastInfosetSource_new;

    /**
     * FI <code>FastInfosetSource.getInputStream()</code> method via reflection.
     */
    static Method fiFastInfosetSource_getInputStream;

    /**
     * FI <code>FastInfosetSource.setInputSTream()</code> method via reflection.
     */
    static Method fiFastInfosetSource_setInputStream;

    /**
     * FI FastInfosetResult constructor using reflection.
     */
    static Constructor fiFastInfosetResult_new;

    /**
     * FI <code>FastInfosetResult.getOutputSTream()</code> method via reflection.
     */
    static Method fiFastInfosetResult_getOutputStream;

    static {
        try {
            Class clazz = null;
            clazz =
                Class.forName("com.sun.xml.internal.fastinfoset.dom.DOMDocumentParser");
            fiDOMDocumentParser_new = clazz.getConstructor(null);
            fiDOMDocumentParser_parse = clazz.getMethod("parse",
                new Class[] { org.w3c.dom.Document.class, java.io.InputStream.class });

            clazz = Class.forName("com.sun.xml.internal.fastinfoset.dom.DOMDocumentSerializer");
            fiDOMDocumentSerializer_new = clazz.getConstructor(null);
            fiDOMDocumentSerializer_serialize = clazz.getMethod("serialize",
                new Class[] { org.w3c.dom.Node.class });
            fiDOMDocumentSerializer_setOutputStream = clazz.getMethod("setOutputStream",
                new Class[] { java.io.OutputStream.class });

            fiFastInfosetSource_class = clazz = Class.forName("com.sun.xml.internal.org.jvnet.fastinfoset.FastInfosetSource");
            fiFastInfosetSource_new = clazz.getConstructor(
                new Class[] { java.io.InputStream.class });
            fiFastInfosetSource_getInputStream = clazz.getMethod("getInputStream", null);
            fiFastInfosetSource_setInputStream = clazz.getMethod("setInputStream",
                new Class[] { java.io.InputStream.class });

            clazz = Class.forName("com.sun.xml.internal.org.jvnet.fastinfoset.FastInfosetResult");
            fiFastInfosetResult_new = clazz.getConstructor(
                new Class[] { java.io.OutputStream.class });
            fiFastInfosetResult_getOutputStream = clazz.getMethod("getOutputStream", null);
        }
        catch (Exception e) {
            // falls through
        }
    }

    // -- DOMDocumentParser ----------------------------------------------

    public static Object DOMDocumentParser_new() throws Exception {
        if (fiDOMDocumentParser_new == null) {
            throw new RuntimeException("Unable to locate Fast Infoset implementation");
        }
        return fiDOMDocumentParser_new.newInstance(null);
    }

    public static void DOMDocumentParser_parse(Object parser,
        Document d, InputStream s) throws Exception
    {
        if (fiDOMDocumentParser_parse == null) {
            throw new RuntimeException("Unable to locate Fast Infoset implementation");
        }
        fiDOMDocumentParser_parse.invoke(parser, new Object[] { d, s });
    }

    // -- DOMDocumentSerializer-------------------------------------------

    public static Object DOMDocumentSerializer_new() throws Exception {
        if (fiDOMDocumentSerializer_new == null) {
            throw new RuntimeException("Unable to locate Fast Infoset implementation");
        }
        return fiDOMDocumentSerializer_new.newInstance(null);
    }

    public static void DOMDocumentSerializer_serialize(Object serializer, Node node)
        throws Exception
    {
        if (fiDOMDocumentSerializer_serialize == null) {
            throw new RuntimeException("Unable to locate Fast Infoset implementation");
        }
        fiDOMDocumentSerializer_serialize.invoke(serializer, new Object[] { node });
    }

    public static void DOMDocumentSerializer_setOutputStream(Object serializer,
        OutputStream os) throws Exception
    {
        if (fiDOMDocumentSerializer_setOutputStream == null) {
            throw new RuntimeException("Unable to locate Fast Infoset implementation");
        }
        fiDOMDocumentSerializer_setOutputStream.invoke(serializer, new Object[] { os });
    }

    // -- FastInfosetSource ----------------------------------------------

    public static boolean isFastInfosetSource(Source source) {
        return source.getClass().getName().equals(
            "com.sun.xml.internal.org.jvnet.fastinfoset.FastInfosetSource");
    }

    public static Class getFastInfosetSource_class() {
        if (fiFastInfosetSource_class == null) {
            throw new RuntimeException("Unable to locate Fast Infoset implementation");
        }

        return fiFastInfosetSource_class;
    }
    public static Source FastInfosetSource_new(InputStream is)
        throws Exception
    {
        if (fiFastInfosetSource_new == null) {
            throw new RuntimeException("Unable to locate Fast Infoset implementation");
        }
        return (Source) fiFastInfosetSource_new.newInstance(new Object[] { is });
    }

    public static InputStream FastInfosetSource_getInputStream(Source source)
        throws Exception
    {
        if (fiFastInfosetSource_getInputStream == null) {
            throw new RuntimeException("Unable to locate Fast Infoset implementation");
        }
        return (InputStream) fiFastInfosetSource_getInputStream.invoke(source, null);
    }

    public static void FastInfosetSource_setInputStream(Source source,
        InputStream is) throws Exception
    {
        if (fiFastInfosetSource_setInputStream == null) {
            throw new RuntimeException("Unable to locate Fast Infoset implementation");
        }
        fiFastInfosetSource_setInputStream.invoke(source, new Object[] { is });
    }

    // -- FastInfosetResult ----------------------------------------------

    public static boolean isFastInfosetResult(Result result) {
        return result.getClass().getName().equals(
            "com.sun.xml.internal.org.jvnet.fastinfoset.FastInfosetResult");
    }

    public static Result FastInfosetResult_new(OutputStream os)
        throws Exception
    {
        if (fiFastInfosetResult_new == null) {
            throw new RuntimeException("Unable to locate Fast Infoset implementation");
        }
        return (Result) fiFastInfosetResult_new.newInstance(new Object[] { os });
    }

    public static OutputStream FastInfosetResult_getOutputStream(Result result)
        throws Exception
    {
        if (fiFastInfosetResult_getOutputStream == null) {
            throw new RuntimeException("Unable to locate Fast Infoset implementation");
        }
        return (OutputStream) fiFastInfosetResult_getOutputStream.invoke(result, null);
    }
}
