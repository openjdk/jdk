/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.security.BasicPermission;

/**
 * This class defines web service permissions.
 * <p>
 * Web service Permissions are identified by name (also referred to as
 * a "target name") alone. There are no actions associated
 * with them.
 * <p>
 * The following permission target name is defined:
 * <dl>
 *   <dt>publishEndpoint
 * </dl>
 * The {@code publishEndpoint} permission allows publishing a
 * web service endpoint using the {@code publish} methods
 * defined by the {@code javax.xml.ws.Endpoint} class.
 * <p>
 * Granting {@code publishEndpoint} allows the application to be
 * exposed as a network service. Depending on the security of the runtime and
 * the security of the application, this may introduce a security hole that
 * is remotely exploitable.
 *
 * @see javax.xml.ws.Endpoint
 * @see java.security.BasicPermission
 * @see java.security.Permission
 * @see java.security.Permissions
 * @see java.lang.SecurityManager
 * @see java.net.SocketPermission
 * @since 1.6
 */
public final class WebServicePermission extends BasicPermission {

    private static final long serialVersionUID = -146474640053770988L;

    /**
     * Creates a new permission with the specified name.
     *
     * @param name the name of the {@code WebServicePermission}
     */
    public WebServicePermission(String name) {
        super(name);
    }

    /**
     * Creates a new permission with the specified name and actions.
     *
     * The {@code actions} parameter is currently unused and
     * it should be {@code null}.
     *
     * @param name the name of the {@code WebServicePermission}
     * @param actions should be {@code null}
     */
    public WebServicePermission(String name, String actions) {
        super(name, actions);
    }

}
