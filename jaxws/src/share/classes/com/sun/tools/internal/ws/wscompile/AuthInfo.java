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
package com.sun.tools.internal.ws.wscompile;

import com.sun.istack.internal.NotNull;

import java.net.URL;

/**
 * Represents authorization information needed by {@link com.sun.tools.internal.ws.wscompile.DefaultAuthenticator} to
 * authenticate wsimport to access the wsdl.
 *
 * @author Vivek Pandey
 */

public final class AuthInfo {
    private final String user;
    private final String password;
    private final URL url;

    public AuthInfo(@NotNull URL url, @NotNull String user, @NotNull String password){
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Returns if the requesting host and port are associated with this {@link AuthInfo}
     */
    public boolean matchingHost(@NotNull URL requestingURL) {
        return requestingURL.equals(url);
    }

}
