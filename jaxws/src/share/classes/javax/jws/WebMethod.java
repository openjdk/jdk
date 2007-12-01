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

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Customizes a method that is exposed as a Web Service operation.
 * The associated method must be public and its parameters return value,
 * and exceptions must follow the rules defined in JAX-RPC 1.1, section 5.
 *
 *  The method is not required to throw java.rmi.RemoteException.
 *
 * @author Copyright (c) 2004 by BEA Systems, Inc. All Rights Reserved.
 */
@Retention(value = RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface WebMethod {

    /**
     * Name of the wsdl:operation matching this method.
     *
     * @specdefault Name of the Java method.
     */
    String operationName() default "";

    /**
     * The action for this operation.
     * <p>
     * For SOAP bindings, this determines the value of the soap action.
     */
    String action() default "";

    /**
     * Marks a method to NOT be exposed as a web method.
     * <p>
     * Used to stop an inherited method from being exposed as part of this web service.
     * If this element is specified, other elements MUST NOT be specified for the @WebMethod.
     * <p>
     * <i>This member-value is not allowed on endpoint interfaces.</i>
     *
     * @since 2.0
     */
    boolean exclude() default false;
};
