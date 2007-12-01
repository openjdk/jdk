/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.net.httpserver;

import com.sun.net.httpserver.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import sun.net.www.MessageHeader;
import java.util.*;
import javax.security.auth.*;
import javax.security.auth.callback.*;
import javax.security.auth.login.*;

public class AuthFilter extends Filter {

    private Authenticator authenticator;

    public AuthFilter (Authenticator authenticator) {
        this.authenticator = authenticator;
    }

    public String description () {
        return "Authentication filter";
    }

    public void setAuthenticator (Authenticator a) {
        authenticator = a;
    }

    public void consumeInput (HttpExchange t) throws IOException {
        InputStream i = t.getRequestBody();
        byte[] b = new byte [4096];
        while (i.read (b) != -1);
        i.close ();
    }

    /**
     * The filter's implementation, which is invoked by the server
     */
    public void doFilter (HttpExchange t, Filter.Chain chain) throws IOException
    {
        if (authenticator != null) {
            Authenticator.Result r = authenticator.authenticate (t);
            if (r instanceof Authenticator.Success) {
                Authenticator.Success s = (Authenticator.Success)r;
                ExchangeImpl e = ExchangeImpl.get (t);
                e.setPrincipal (s.getPrincipal());
                chain.doFilter (t);
            } else if (r instanceof Authenticator.Retry) {
                Authenticator.Retry ry = (Authenticator.Retry)r;
                consumeInput (t);
                t.sendResponseHeaders (ry.getResponseCode(), -1);
            } else if (r instanceof Authenticator.Failure) {
                Authenticator.Failure f = (Authenticator.Failure)r;
                consumeInput (t);
                t.sendResponseHeaders (f.getResponseCode(), -1);
            }
        } else {
            chain.doFilter (t);
        }
    }
}
