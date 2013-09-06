/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.bind.annotation;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import java.lang.annotation.Inherited;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * <p> Controls whether fields or Javabean properties are serialized by default. </p>
 *
 * <p> <b> Usage </b> </p>
 *
 * <p> <tt>@XmlAccessorType</tt> annotation can be used with the following program elements:</p>
 *
 * <ul>
 *   <li> package</li>
 *   <li> a top level class </li>
 * </ul>
 *
 * <p> See "Package Specification" in javax.xml.bind.package javadoc for
 * additional common information.</p>
 *
 * <p>This annotation provides control over the default serialization
 * of properties and fields in a class.
 *
 * <p>The annotation <tt> @XmlAccessorType </tt> on a package applies to
 * all classes in the package. The following inheritance
 * semantics apply:
 *
 * <ul>
 *   <li> If there is a <tt>@XmlAccessorType</tt> on a class, then it
 *        is used. </li>
 *   <li> Otherwise, if a <tt>@XmlAccessorType</tt> exists on one of
 *        its super classes, then it is inherited.
 *   <li> Otherwise, the <tt>@XmlAccessorType </tt> on a package is
 *        inherited.
 * </ul>
 * <p> <b> Defaulting Rules: </b> </p>
 *
 * <p>By default, if <tt>@XmlAccessorType </tt> on a package is absent,
 * then the following package level annotation is assumed.</p>
 * <pre>
 *   &#64;XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
 * </pre>
 * <p> By default, if <tt>@XmlAccessorType</tt> on a class is absent,
 * and none of its super classes is annotated with
 * <tt>@XmlAccessorType</tt>, then the following default on the class
 * is assumed: </p>
 * <pre>
 *   &#64;XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
 * </pre>
 * <p>This annotation can be used with the following annotations:
 *    {@link XmlType}, {@link XmlRootElement}, {@link XmlAccessorOrder},
 *    {@link XmlSchema}, {@link XmlSchemaType}, {@link XmlSchemaTypes},
 *    , {@link XmlJavaTypeAdapter}. It can also be used with the
 *    following annotations at the package level: {@link XmlJavaTypeAdapter}.
 *
 * @author Sekhar Vajjhala, Sun Microsystems, Inc.
 * @since JAXB2.0
 * @see XmlAccessType
 */

@Inherited @Retention(RUNTIME) @Target({PACKAGE, TYPE})
public @interface XmlAccessorType {

    /**
     * Specifies whether fields or properties are serialized.
     *
     * @see XmlAccessType
     */
    XmlAccessType value() default XmlAccessType.PUBLIC_MEMBER;
}
