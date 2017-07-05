/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import com.sun.xml.internal.ws.policy.PolicyException;

/**
 * Abstract class defines interface for policy model marshaller implementations that are specific to underlying
 * persistence layer.
 *
 * @author Marek Potociar
 */
public abstract class PolicyModelMarshaller {
    private static final PolicyModelMarshaller defaultXmlMarshaller = new XmlPolicyModelMarshaller(false);
    private static final PolicyModelMarshaller invisibleAssertionXmlMarshaller = new XmlPolicyModelMarshaller(true);

    /**
     * Default constructor to ensure we have a common model marshaller base, but only our API classes implemented in this
     * package will be able to extend this abstract class. This is to restrict attempts of extending the class from
     * a client code.
     */
    PolicyModelMarshaller() {
        // nothing to instantiate
    }

    /**
     * Marshalls the policy source model using provided storage reference
     *
     * @param model policy source model to be marshalled
     * @param storage reference to underlying storage that should be used for model marshalling
     * @throws PolicyException If marshalling failed
     */
    public abstract void marshal(PolicySourceModel model, Object storage) throws PolicyException;

    /**
     * Marshalls the collection of policy source models using provided storage reference
     *
     * @param models collection of policy source models to be marshalled
     * @param storage reference to underlying storage that should be used for model marshalling
     * @throws PolicyException If marshalling failed
     */
    public abstract void marshal(Collection<PolicySourceModel> models, Object storage) throws PolicyException;

    /**
     * Factory methods that returns a marshaller instance based on input parameter.
     *
     * @param marshallInvisible boolean parameter indicating whether the marshaller
     *        returned by this method does marshall private assertions or not.
     *
     * @return policy model marshaller that either marshalls private assertions or not
     *         based on the input argument.
     */
    public static PolicyModelMarshaller getXmlMarshaller(final boolean marshallInvisible) {
        return (marshallInvisible) ? invisibleAssertionXmlMarshaller : defaultXmlMarshaller;
    }
}
