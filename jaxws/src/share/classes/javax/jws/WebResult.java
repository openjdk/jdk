/*
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

/*
 * Copyright (c) 2004 by BEA Systems, Inc. All Rights Reserved.
 */

package javax.jws;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Customizes the mapping of the return value to a WSDL part and XML element.
 *
 * @author Copyright (c) 2004 by BEA Systems, Inc. All Rights Reserved.
 */
@Retention(value = RetentionPolicy.RUNTIME)
    @Target(value = {ElementType.METHOD})
    public @interface WebResult
{

    /**
     * Name of return value.
     * <p>
     * If the operation is rpc style and @WebResult.partName has not been specified, this is the name of the wsdl:part
     * representing the return value.
     * <br>
     * If the operation is document style or the return value maps to a header, this is the local name of the XML
     * element representing the return value.
     *
     * @specdefault
     *   If the operation is document style and the parameter style is BARE, {@code @WebParam.operationName}+"Response".<br>
     *   Otherwise, "return."
     */
    String name() default "";

    /**
     * The name of the wsdl:part representing this return value.
     * <p>
     * This is only used if the operation is rpc style, or if the operation is document style and the parameter style
     * is BARE.
     *
     * @specdefault {@code @WebResult.name}
     *
     * @since 2.0
     */
    String partName() default "";

    /**
     * The XML namespace for the return value.
     * <p>
     * Only used if the operation is document style or the return value maps to a header.
     * If the target namespace is set to "", this represents the empty namespace.
     *
     * @specdefault
     *   If the operation is document style, the parameter style is WRAPPED, and the return value does not map to a
     *   header, the empty namespace.<br>
     *   Otherwise, the targetNamespace for the Web Service.
     */
    String targetNamespace() default "";

    /**
     * If true, the result is pulled from a message header rather then the message body.
     *
     * @since 2.0
     */
    boolean header() default false;
}
