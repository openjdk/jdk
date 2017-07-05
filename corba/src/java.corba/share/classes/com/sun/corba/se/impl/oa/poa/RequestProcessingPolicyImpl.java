/*
 * Copyright (c) 1997, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.oa.poa;

import org.omg.CORBA.*;
import org.omg.PortableServer.*;

public class RequestProcessingPolicyImpl
    extends org.omg.CORBA.LocalObject implements RequestProcessingPolicy {

    public RequestProcessingPolicyImpl(RequestProcessingPolicyValue
                                       value) {
        this.value = value;
    }

    public RequestProcessingPolicyValue value() {
        return value;
    }

    public int policy_type()
    {
        return REQUEST_PROCESSING_POLICY_ID.value ;
    }

    public Policy copy() {
        return new RequestProcessingPolicyImpl(value);
    }

    public void destroy() {
        value = null;
    }

    private RequestProcessingPolicyValue value;

    public String toString()
    {
        String type = null ;
        switch (value.value()) {
            case RequestProcessingPolicyValue._USE_ACTIVE_OBJECT_MAP_ONLY :
                type = "USE_ACTIVE_OBJECT_MAP_ONLY" ;
                break ;
            case RequestProcessingPolicyValue._USE_DEFAULT_SERVANT :
                type = "USE_DEFAULT_SERVANT" ;
                break ;
            case RequestProcessingPolicyValue._USE_SERVANT_MANAGER :
                type = "USE_SERVANT_MANAGER" ;
                break ;
        }

        return "RequestProcessingPolicy[" + type + "]" ;
    }
}
