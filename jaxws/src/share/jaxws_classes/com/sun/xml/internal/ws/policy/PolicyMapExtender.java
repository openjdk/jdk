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

package com.sun.xml.internal.ws.policy;

/**
 * The class provides methods to extend policy map content with new policies
 *
 * @author Marek Potociar (marek.potociar@sun.com)
 */
public final class PolicyMapExtender extends PolicyMapMutator {

    /**
     * This constructor is private to prevent direct instantiation from outside of the class
     */
    private PolicyMapExtender() {
        // nothing to initialize
    }

    public static PolicyMapExtender createPolicyMapExtender() {
        return new PolicyMapExtender();
    }

    public void putServiceSubject(final PolicyMapKey key, final PolicySubject subject) {
        getMap().putSubject(PolicyMap.ScopeType.SERVICE, key, subject);
    }

    public void putEndpointSubject(final PolicyMapKey key, final PolicySubject subject) {
        getMap().putSubject(PolicyMap.ScopeType.ENDPOINT, key, subject);
    }

    public void putOperationSubject(final PolicyMapKey key, final PolicySubject subject) {
        getMap().putSubject(PolicyMap.ScopeType.OPERATION, key, subject);
    }

    public void putInputMessageSubject(final PolicyMapKey key, final PolicySubject subject) {
        getMap().putSubject(PolicyMap.ScopeType.INPUT_MESSAGE, key, subject);
    }

    public void putOutputMessageSubject(final PolicyMapKey key, final PolicySubject subject) {
        getMap().putSubject(PolicyMap.ScopeType.OUTPUT_MESSAGE, key, subject);
    }

    public void putFaultMessageSubject(final PolicyMapKey key, final PolicySubject subject) {
        getMap().putSubject(PolicyMap.ScopeType.FAULT_MESSAGE, key, subject);
    }

}
