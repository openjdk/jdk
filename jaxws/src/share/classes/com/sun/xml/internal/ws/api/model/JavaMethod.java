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

package com.sun.xml.internal.ws.api.model;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.model.soap.SOAPBinding;

import java.lang.reflect.Method;

/**
 * Abstracts the annotated {@link Method} of a SEI.
 *
 * @author Vivek Pandey
 */
public interface JavaMethod {

    /**
     * Gets the root {@link SEIModel} that owns this model.
     */
    SEIModel getOwner();

    /**
     * On the server side, it uses this for invocation of the web service
     *
     * <p>
     * {@link @WebService}(endpointInterface="I")
     * class A { }
     *
     * In this case, it retuns A's method
     *
     * <p>
     * {@link @WebService}(endpointInterface="I")
     * class A implements I { }
     * In this case, it returns A's method
     *
     * <p>
     * {@link @WebService}
     * class A { }
     * In this case, it returns A's method
     *
     * @return Returns the java {@link Method}
     */
    @NotNull Method getMethod();

    /**
     * This should be used if you want to access annotations on WebMethod
     * Returns the SEI method if there is one.
     *
     * <p>
     * {@link @WebService}(endpointInterface="I")
     * class A { }
     * In this case, it retuns I's method
     *
     * <p>
     * {@link @WebService}(endpointInterface="I")
     * class A implements I { }
     * In this case, it returns I's method
     *
     * <p>
     * {@link @WebService}
     * class A { }
     * In this case, it returns A's method
     *
     * @return Returns the java {@link Method}
     */
    @NotNull Method getSEIMethod();

    /**
     * @return Returns the {@link MEP}.
     */
    MEP getMEP();

    /**
     * Binding object - a {@link SOAPBinding} isntance.
     *
     * @return the Binding object
     */
    SOAPBinding getBinding();

    /**
     * Gives the wsdl:operation@name value
     */
    @NotNull String getOperationName();


    /**
     * Gives the request wsdl:message@name value
     */
    @NotNull String getRequestMessageName();

    /**
     * Gives the response wsdl:messageName value
     * @return null if its a oneway operation that is getMEP().isOneWay()==true.
     * @see com.sun.xml.internal.ws.api.model.MEP#isOneWay()
     */
    @Nullable String getResponseMessageName();

}
