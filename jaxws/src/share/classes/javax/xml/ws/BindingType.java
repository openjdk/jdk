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

package javax.xml.ws;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 *  The <code>BindingType</code> annotation is used to
 *  specify the binding to use for a web service
 *  endpoint implementation class.
 *  <p>
 *  This annotation may be overriden programmatically or via
 *  deployment descriptors, depending on the platform in use.
 *
 *  @since JAX-WS 2.0
 *
 **/
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BindingType {
     /**
      * A binding identifier (a URI).
      * If not specified, the default is the SOAP 1.1 / HTTP binding.
      * <p>
      * See the <code>SOAPBinding</code> and <code>HTTPBinding</code>
      * for the definition of the standard binding identifiers.
      *
      * @see javax.xml.ws.Binding
      * @see javax.xml.ws.soap.SOAPBinding#SOAP11HTTP_BINDING
      * @see javax.xml.ws.soap.SOAPBinding#SOAP12HTTP_BINDING
      * @see javax.xml.ws.http.HTTPBinding#HTTP_BINDING
      */
     String value() default "" ;
}
