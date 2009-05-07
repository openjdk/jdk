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
 *  The <code>WebServiceRef</code> annotation is used to
 *  define a reference to a web service and
 *  (optionally) an injection target for it.
 *
 *  Web service references are resources in the Java EE 5 sense.
 *
 *  @see javax.annotation.Resource
 *
 *  @since JAX-WS 2.0
 *
**/

@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WebServiceRef {
     /**
      * The JNDI name of the resource.  For field annotations,
      * the default is the field name.  For method annotations,
      * the default is the JavaBeans property name corresponding
      * to the method.  For class annotations, there is no default
      * and this MUST be specified.
      */
     String name() default "";

     /**
      * The Java type of the resource.  For field annotations,
      * the default is the type of the field.  For method annotations,
      * the default is the type of the JavaBeans property.
      * For class annotations, there is no default and this MUST be
      * specified.
      */
     Class type() default Object.class ;

     /**
      * A product specific name that this resource should be mapped to.
      * The name of this resource, as defined by the <code>name</code>
      * element or defaulted, is a name that is local to the application
      * component using the resource.  (It's a name in the JNDI
      * <code>java:comp/env</code> namespace.)  Many application servers
      * provide a way to map these local names to names of resources
      * known to the application server.  This mapped name is often a
      * <i>global</i> JNDI name, but may be a name of any form. <p>
      *
      * Application servers are not required to support any particular
      * form or type of mapped name, nor the ability to use mapped names.
      * The mapped name is product-dependent and often installation-dependent.
      * No use of a mapped name is portable.
      */
     String mappedName() default "";

     /**
      * The service class, always a type extending
      * <code>javax.xml.ws.Service</code>. This element MUST be specified
      * whenever the type of the reference is a service endpoint interface.
      */
     Class value() default Object.class ;

     /**
      * A URL pointing to the WSDL document for the web service.
      * If not specified, the WSDL location specified by annotations
      * on the resource type is used instead.
      */
     String wsdlLocation() default "";
}
