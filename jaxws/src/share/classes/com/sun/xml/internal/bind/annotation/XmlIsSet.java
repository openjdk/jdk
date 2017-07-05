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
package com.sun.xml.internal.bind.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlValue;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Designates a boolean field/property as a flag to indicate
 * whether another property is present or not.
 *
 * <p>
 * Sometimes you'd want to map a Java primitive type to an
 * optional element/attribute. Doing this makes it impossible
 * to represent the absence of the property, thus you always
 * end up producing the value when you marshal to XML.
 *
 * For example,
 * <pre>
 * {@link XmlElement}
 * class Foo {
 *      {@link XmlElement}
 *      int x;
 * }
 *
 * marshaller.marshal(new Foo());
 * </pre>
 * and you get:
 * <pre><xmp>
 * <foo><x>0</x></foo>
 * </xmp></pre>
 *
 * <p>
 * By creating a side boolean field/property that has this annotation,
 * you can indicate the absence of the property by setting this boolean
 * to false.
 * <pre>
 * {@link XmlElement}
 * class Foo {
 *      {@link XmlElement}
 *      int x;
 *      {@link XmlIsSet}("x")
 *      boolean xIsPresent;
 * }
 *
 * Foo f = new Foo();
 * f.x = 5;
 * f.xIsPresent = false;
 *
 * marshaller.marshal(f);
 *
 * <xmp>
 * <foo/>
 * </xmp>
 *
 * f.xIsPresent = true;
 * <xmp>
 * <foo><x>5</x></foo>
 * </xmp>
 * </pre>
 *
 * <p>
 * A property/field annotated with {@link XmlIsSet} itself will not show up in XML.
 * It is an error to use this annotation on the same property/field
 * as {@link XmlElement}, {@link XmlAttribute}, {@link XmlValue}, or {@link XmlElementRef},
 * ...<b>TBD</b>.
 *
 * @deprecated
 *      this hasn't been implemented in the RI, and this hasn't been speced yet.
 *      I believe Joe asked for this feature. I'd like to drop this.
 *
 * @author Kohsuke Kawaguchi
 */
@Retention(RUNTIME)
@Target({FIELD,METHOD})
public @interface XmlIsSet {
    /**
     * Specifies the name of the property to attach to.
     */
    String value();
}
