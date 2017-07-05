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

package com.sun.xml.internal.ws.api.server;

import com.sun.istack.internal.NotNull;

/**
 * {@link Module} that is an HTTP container.
 *
 * @author Kohsuke Kawaguchi
 * @since 2.1 EA3
 */
public abstract class WebModule extends Module {
    /**
     * Gets the host, port, and context path portion of this module.
     *
     * <p>
     * For example, if this is an web appliation running in a servlet
     * container "http://myhost/myapp", then this method should return
     * this URI.
     *
     * <p>
     * This method follows the convention of the <tt>HttpServletRequest.getContextPath()</tt>,
     * and accepts strings like "http://myhost" (for web applications that are deployed
     * to the root context path), or "http://myhost/foobar" (for web applications
     * that are deployed to context path "/foobar")
     *
     * <p>
     * Notice that this method involves in determining the machine name
     * without relying on HTTP "Host" header.
     */
    public abstract @NotNull String getContextPath();
}
