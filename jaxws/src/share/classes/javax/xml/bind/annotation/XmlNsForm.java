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
 * Enumeration of XML Schema namespace qualifications.
 *
 * <p>See "Package Specification" in javax.xml.bind.package javadoc for
 * additional common information.</p>
 *
 * <p><b>Usage</b>
 * <p>
 * The namespace qualification values are used in the annotations
 * defined in this packge. The enumeration values are mapped as follows:
 *
 * <p>
 * <table border="1" cellpadding="4" cellspacing="3">
 *   <tbody>
 *     <tr>
 *       <td><b>Enum Value<b></td>
 *       <td><b>XML Schema Value<b></td>
 *     </tr>
 *
 *     <tr valign="top">
 *       <td>UNQUALIFIED</td>
 *       <td>unqualified</td>
 *     </tr>
 *     <tr valign="top">
 *       <td>QUALIFIED</td>
 *       <td>qualified</td>
 *     </tr>
 *     <tr valign="top">
 *       <td>UNSET</td>
 *       <td>namespace qualification attribute is absent from the
 *           XML Schema fragment</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * @author Sekhar Vajjhala, Sun Microsystems, Inc.
 * @since JAXB2.0
 * @version $Revision: 1.1 $
 */
public enum XmlNsForm {UNQUALIFIED, QUALIFIED, UNSET}
