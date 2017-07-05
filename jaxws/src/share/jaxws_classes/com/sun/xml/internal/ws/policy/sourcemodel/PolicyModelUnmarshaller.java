/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.policy.sourcemodel;

import com.sun.xml.internal.ws.policy.PolicyException;

/**
 * Abstract class defines interface for policy model unmarshaller implementations that are specific to underlying
 * persistence layer.
 *
 * Code that depends on JAX-WS should use com.sun.xml.internal.ws.api.policy.ModelUnmarshaller
 * instead of this class.
 *
 * @author Marek Potociar
 * @author Fabian Ritzmann
 */
public abstract class PolicyModelUnmarshaller {
    private static final PolicyModelUnmarshaller xmlUnmarshaller = new XmlPolicyModelUnmarshaller();

    /**
     * Default constructor to ensure we have a common model unmarshaller base, but only our API classes implemented in this
     * package will be able to extend this abstract class. This is to restrict attempts of extending the class from
     * a client code.
     */
    PolicyModelUnmarshaller() {
        // nothing to intitialize
    }

    /**
     * Unmarshalls single policy source model from provided storage reference. Method expects that the storage
     * cursor to be alread placed on the start of a policy expression. Inner comments and whitespaces are skipped
     * in processing. Any other cursor position results in a PolicyException being thrown.
     *
     * @param storage reference to underlying storage that should be used for model unmarshalling
     * @return unmarshalled policy source model. If no policies are found, returns {@code null}.
     * @throws PolicyException in case of the unmarshalling problems
     */
    public abstract PolicySourceModel unmarshalModel(Object storage) throws PolicyException;

    /**
     * Factory method that returns policy model unmarshaller able to unmarshal
     * policy expressions from XML source.
     *
     * Code that depends on JAX-WS should use com.sun.xml.internal.ws.api.policy.ModelUnmarshaller.getUnmarshaller()
     * instead of this method.
     *
     * @return policy model unmarshaller able to unmarshal policy expressions from XML source.
     */
    public static PolicyModelUnmarshaller getXmlUnmarshaller() {
        return xmlUnmarshaller;
    }
}
