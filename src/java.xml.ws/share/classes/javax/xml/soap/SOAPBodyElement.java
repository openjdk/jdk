/*
 * Copyright (c) 2004, 2015, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.soap;

/**
 * A {@code SOAPBodyElement} object represents the contents in
 * a {@code SOAPBody} object.  The {@code SOAPFault} interface
 * is a {@code SOAPBodyElement} object that has been defined.
 * <P>
 * A new {@code SOAPBodyElement} object can be created and added
 * to a {@code SOAPBody} object with the {@code SOAPBody}
 * method {@code addBodyElement}. In the following line of code,
 * {@code sb} is a {@code SOAPBody} object, and
 * {@code myName} is a {@code Name} object.
 * <pre>{@code
 *    SOAPBodyElement sbe = sb.addBodyElement(myName);
 * }</pre>
 *
 * @since 1.6
 */
public interface SOAPBodyElement extends SOAPElement {
}
