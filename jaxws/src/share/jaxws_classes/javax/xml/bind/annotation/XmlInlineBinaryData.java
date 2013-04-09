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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

import javax.xml.transform.Source;
import javax.xml.bind.attachment.AttachmentMarshaller;
import javax.activation.DataHandler;

/**
 * Disable consideration of XOP encoding for datatypes that are bound to
 * base64-encoded binary data in XML.
 *
 * <p>
 * When XOP encoding is enabled as described in {@link AttachmentMarshaller#isXOPPackage()}, this annotation disables datatypes such as {@link java.awt.Image} or {@link Source} or <tt>byte[]</tt> that are bound to base64-encoded binary from being considered for
 * XOP encoding. If a JAXB property is annotated with this annotation or if
 * the JAXB property's base type is annotated with this annotation,
 * neither
 * {@link AttachmentMarshaller#addMtomAttachment(DataHandler, String, String)}
 * nor
 * {@link AttachmentMarshaller#addMtomAttachment(byte[], int, int, String, String, String)} is
 * ever called for the property. The binary data will always be inlined.
 *
 * @author Joseph Fialli
 * @since JAXB2.0
 */
@Retention(RUNTIME)
@Target({FIELD,METHOD,TYPE})
public @interface XmlInlineBinaryData {
}
