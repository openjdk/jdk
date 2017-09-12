/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.bind.annotation.adapters;

import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlElementRefs;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlSchema;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSchemaTypes;
import java.lang.annotation.Target;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.PACKAGE;


/**
 * Use an adapter that implements {@link XmlAdapter} for custom marshaling.
 *
 * <p> <b> Usage: </b> </p>
 *
 * <p> The {@code @XmlJavaTypeAdapter} annotation can be used with the
 * following program elements:
 * <ul>
 *   <li> a JavaBean property </li>
 *   <li> field </li>
 *   <li> parameter </li>
 *   <li> package </li>
 *   <li> from within {@link XmlJavaTypeAdapters} </li>
 * </ul>
 *
 * <p> When {@code @XmlJavaTypeAdapter} annotation is defined on a
 * class, it applies to all references to the class.
 * <p> When {@code @XmlJavaTypeAdapter} annotation is defined at the
 * package level it applies to all references from within the package
 * to {@code @XmlJavaTypeAdapter.type()}.
 * <p> When {@code @XmlJavaTypeAdapter} annotation is defined on the
 * field, property or parameter, then the annotation applies to the
 * field, property or the parameter only.
 * <p> A {@code @XmlJavaTypeAdapter} annotation on a field, property
 * or parameter overrides the {@code @XmlJavaTypeAdapter} annotation
 * associated with the class being referenced by the field, property
 * or parameter.
 * <p> A {@code @XmlJavaTypeAdapter} annotation on a class overrides
 * the {@code @XmlJavaTypeAdapter} annotation specified at the
 * package level for that class.
 *
 * <p>This annotation can be used with the following other annotations:
 * {@link XmlElement}, {@link XmlAttribute}, {@link XmlElementRef},
 * {@link XmlElementRefs}, {@link XmlAnyElement}. This can also be
 * used at the package level with the following annotations:
 * {@link XmlAccessorType}, {@link XmlSchema}, {@link XmlSchemaType},
 * {@link XmlSchemaTypes}.
 *
 * <p><b> Example: </b> See example in {@link XmlAdapter}
 *
 * @author <ul><li>Sekhar Vajjhala, Sun Microsystems Inc.</li> <li> Kohsuke Kawaguchi, Sun Microsystems Inc.</li></ul>
 * @since 1.6, JAXB 2.0
 * @see XmlAdapter
 */

@Retention(RUNTIME) @Target({PACKAGE,FIELD,METHOD,TYPE,PARAMETER})
public @interface XmlJavaTypeAdapter {
    /**
     * Points to the class that converts a value type to a bound type or vice versa.
     * See {@link XmlAdapter} for more details.
     */
    Class<? extends XmlAdapter> value();

    /**
     * If this annotation is used at the package level, then value of
     * the type() must be specified.
     */

    Class type() default DEFAULT.class;

    /**
     * Used in {@link XmlJavaTypeAdapter#type()} to
     * signal that the type be inferred from the signature
     * of the field, property, parameter or the class.
     */

    static final class DEFAULT {}

}
