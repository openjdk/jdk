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



/**
 * Used by XmlAccessorType to control serialization of fields or
 * properties.
 *
 * @author Sekhar Vajjhala, Sun Microsystems, Inc.
 * @since JAXB2.0
 * @version $Revision: 1.10 $
 * @see XmlAccessorType
 */

public enum XmlAccessType {
    /**
     * Every getter/setter pair in a JAXB-bound class will be automatically
     * bound to XML, unless annotated by {@link XmlTransient}.
     *
     * Fields are bound to XML only when they are explicitly annotated
     * by some of the JAXB annotations.
     */
    PROPERTY,
    /**
     * Every non static, non transient field in a JAXB-bound class will be automatically
     * bound to XML, unless annotated by {@link XmlTransient}.
     *
     * Getter/setter pairs are bound to XML only when they are explicitly annotated
     * by some of the JAXB annotations.
     */
    FIELD,
    /**
     * Every public getter/setter pair and every public field will be
     * automatically bound to XML, unless annotated by {@link XmlTransient}.
     *
     * Fields or getter/setter pairs that are private, protected, or
     * defaulted to package-only access are bound to XML only when they are
     * explicitly annotated by the appropriate JAXB annotations.
     */
    PUBLIC_MEMBER,
    /**
     * None of the fields or properties is bound to XML unless they
     * are specifically  annotated with some of the JAXB annotations.
     */
    NONE
}
