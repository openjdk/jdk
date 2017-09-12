/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.util;

import java.util.List;
import java.util.Set;

import javax.xml.ws.handler.Handler;

/**
 * Used to hold a list of handlers and a set of roles from an
 * annotated endpoint. At runtime, these are created by the
 * HandlerAnnotationProcessor at the request of client and
 * server code to create the handler chains.
 *
 * @see com.sun.xml.internal.ws.util.HandlerAnnotationProcessor
 *
 * @author JAX-WS Development Team
 */
public class HandlerAnnotationInfo {

    private List<Handler> handlers;
    private Set<String> roles;

    /**
     * Return the handlers specified by the handler chain descriptor.
     *
     * @return A list of jax-ws handler objects.
     */
    public List<Handler> getHandlers() {
        return handlers;
    }

    /**
     * This method should only be called by HandlerAnnotationProcessor.
     *
     * @param handlers The handlers specified by the handler chain descriptor.
     */
    public void setHandlers(List<Handler> handlers) {
        this.handlers = handlers;
    }

    /**
     * Return the roles contained in the handler chain descriptor.
     *
     * @return A set of roles.
     */
    public Set<String> getRoles() {
        return roles;
    }

    /**
     * This method should only be called by HandlerAnnotationProcessor.
     *
     * @param roles The roles contained in the handler chain descriptor.
     */
    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

}
