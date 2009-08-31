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

package javax.xml.bind.annotation;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import java.lang.annotation.Inherited;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * <p> Controls the ordering of fields and properties in a class. </p>
 *
 * <h3>Usage </h3>
 *
 * <p> <tt> @XmlAccessorOrder </tt> annotation can be used with the following
 * program elements:</p>
 *
 * <ul>
 *   <li> package</li>
 *   <li> a top level class </li>
 * </ul>
 *
 * <p> See "Package Specification" in <tt>javax.xml.bind</tt> package javadoc for
 * additional common information.</p>
 *
 * <p>The effective {@link XmlAccessOrder} on a class is determined
 * as follows:
 *
 * <ul>
 *   <li> If there is a <tt>@XmlAccessorOrder</tt> on a class, then
 *        it is used. </li>
 *   <li> Otherwise, if a <tt>@XmlAccessorOrder </tt> exists on one of
 *        its super classes, then it is inherited (by the virtue of
 *        {@link Inherited})
 *   <li> Otherwise, the <tt>@XmlAccessorOrder</tt> on the package
 *        of the class is used, if it's there.
 *   <li> Otherwise {@link XmlAccessOrder#UNDEFINED}.
 * </ul>
 *
 * <p>This annotation can be used with the following annotations:
 *    {@link XmlType}, {@link XmlRootElement}, {@link XmlAccessorType},
 *    {@link XmlSchema}, {@link XmlSchemaType}, {@link XmlSchemaTypes},
 *    , {@link XmlJavaTypeAdapter}. It can also be used with the
 *    following annotations at the package level: {@link XmlJavaTypeAdapter}.
 *
 * @author Sekhar Vajjhala, Sun Microsystems, Inc.
 * @since JAXB2.0
 * @version  $Revision: 1.12 $
 * @see XmlAccessOrder
 */

@Inherited @Retention(RUNTIME) @Target({PACKAGE, TYPE})
public @interface XmlAccessorOrder {
        XmlAccessOrder value() default XmlAccessOrder.UNDEFINED;
}
