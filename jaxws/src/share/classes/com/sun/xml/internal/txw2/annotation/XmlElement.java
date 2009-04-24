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

package com.sun.xml.internal.txw2.annotation;

import com.sun.xml.internal.txw2.TypedXmlWriter;
import com.sun.xml.internal.txw2.TXW;
import com.sun.xml.internal.txw2.output.XmlSerializer;

import javax.xml.namespace.QName;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.METHOD;

/**
 * Specifies the name of the XML element.
 *
 * <h2>Used on method</h2>
 * <p>
 * When used on methods declared on interfaces that derive
 * from {@link TypedXmlWriter}, it specifies that the invocation
 * of the method will produce an element of the specified name.
 *
 * <p>
 * The method signature has to match one of the following patterns.
 *
 * <dl>
 *  <dt>Child writer: <tt>TW foo()</tt></dt>
 *  <dd>TW must be an interface derived from {@link TypedXmlWriter}.
 *      When this method is called, a new child element is started,
 *      and its content can be written by using the returned <tt>TW</tt>
 *      object. This child element will be ended when its _commit method
 *      is called.
 *  <dt>Leaf element: <tt>void foo(DT1,DT2,...)</tt></dt>
 *  <dd>DTi must be datatype objects.
 *      When this method is called, a new child element is started,
 *      followed by the whitespace-separated text data from each of
 *      the datatype objects, followed by the end tag.
 * </dl>
 *
 * <h2>Used on interface</h2>
 * <p>
 * When used on interfaces that derive from {@link TypedXmlWriter},
 * it associates an element name with that interface. This name is
 * used in a few places, such as in {@link TXW#create(Class,XmlSerializer)}
 * and {@link TypedXmlWriter#_element(Class)}.
 *
 *
 * @author Kohsuke Kawaguchi
 */
@Retention(RUNTIME)
@Target({METHOD,TYPE})
public @interface XmlElement {
    /**
     * The local name of the element.
     */
    String value() default "";

    /**
     * The namespace URI of this element.
     *
     * <p>
     * If the annotation is on an interface and this paramter is left unspecified,
     * then the namespace URI is taken from {@link XmlNamespace} annotation on
     * the package that the interface is in. If {@link XmlNamespace} annotation
     * doesn't exist, the namespace URI will be "".
     *
     * <p>
     * If the annotation is on a method and this parameter is left unspecified,
     * then the namespace URI is the same as the namespace URI of the writer interface.
     */
    String ns() default "##default";
}
