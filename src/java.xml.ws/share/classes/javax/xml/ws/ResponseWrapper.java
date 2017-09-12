/*
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.ws;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
/**
 * Used to annotate methods in the Service Endpoint Interface with the response
 * wrapper bean to be used at runtime. The default value of the {@code localName} is
 * the {@code operationName} as defined in {@code WebMethod} annotation appended with
 * {@code Response} and the {@code targetNamespace} is the target namespace of the SEI.
 * <p> When starting from Java this annotation is used resolve
 * overloading conflicts in document literal mode. Only the {@code className}
 * is required in this case.
 *
 *  @since 1.6, JAX-WS 2.0
**/

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ResponseWrapper {

    /**
     * Element's local name.
     * @return local name
     */
    public String localName() default "";

    /**
     * Element's namespace name.
     * @return target namespace name
     */
    public String targetNamespace() default "";

    /**
     * Response wrapper bean name.
     * @return bean name
     */
    public String className() default "";

    /**
     * wsdl:part name for the wrapper part
     *
     * @return wsdl:part name
     * @since 1.7, JAX-WS 2.2
     */
    public String partName() default "";

}
