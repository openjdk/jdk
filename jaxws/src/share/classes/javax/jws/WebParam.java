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
 * Customizes the mapping of an individual parameter to a Web Service message part and XML element.
 *
 * @author Copyright (c) 2004 by BEA Systems, Inc. All Rights Reserved.
 */
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.PARAMETER})
public @interface WebParam {

    /**
     * The direction in which the parameter flows
     */
    public enum Mode {
        IN,
        OUT,
        INOUT
    };

    /**
     * Name of the parameter.
     * <p>
     * If the operation is rpc style and @WebParam.partName has not been specified, this is name of the wsdl:part
     * representing the parameter.
     * <br>
     * If the operation is document style or the parameter maps to a header, this is the local name of the XML element
     * representing the parameter.
     * <p>
     * A name MUST be specified if the operation is document style, the parameter style is BARE, and the mode is OUT
     * or INOUT.
     *
     * @specdefault
     *   If the operation is document style and the parameter style is BARE, {@code @WebMethod.operationName}.<br>
     *   Otherwise, argN, where N represents the index of the parameter in the method signature (starting at arg0).
     */
    String name() default "";

    /**
     * The name of the wsdl:part representing this parameter.
     * <p>
     * This is only used if the operation is rpc style or if the operation is document style and the parameter style
     * is BARE.
     *
     * @specdefault {@code @WebParam.name}
     *
     * @since 2.0
     */
    String partName() default "";

    /**
     * The XML namespace for the parameter.
     * <p>
     * Only used if the operation is document style or the paramater maps to a header.
     * If the target namespace is set to "", this represents the empty namespace.
     *
     * @specdefault
     *   If the operation is document style, the parameter style is WRAPPED, and the parameter does not map to a
     *   header, the empty namespace.<br>
     *   Otherwise, the targetNamespace for the Web Service.
     */
    String targetNamespace() default "";

    /**
     * The direction in which the parameter is flowing (One of IN, OUT, or INOUT).
     * <p>
     * The OUT and INOUT modes may only be specified for parameter types that conform to the definition of Holder types
     * (JAX-WS 2.0 [5], section 2.3.3).  Parameters that are Holder Types MUST be OUT or INOUT.
     *
     * @specdefault
     *   INOUT if a Holder type.<br>
     *   IN if not a Holder type.
     */
    Mode mode() default Mode.IN;

    /**
     * If true, the parameter is pulled from a message header rather then the message body.
     */
    boolean header() default false;
};
