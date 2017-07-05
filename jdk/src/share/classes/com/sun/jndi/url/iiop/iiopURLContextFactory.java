/*
 * Copyright (c) 1999, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jndi.url.iiop;

import javax.naming.*;
import javax.naming.spi.*;

import java.util.Hashtable;

import com.sun.jndi.cosnaming.CNCtx;

/**
 * An IIOP URL context factory.
 *
 * @author Rosanna Lee
 */

public class iiopURLContextFactory implements ObjectFactory {

    public Object getObjectInstance(Object urlInfo, Name name, Context nameCtx,
                                    Hashtable<?,?> env) throws Exception {

//System.out.println("iiopURLContextFactory " + urlInfo);
        if (urlInfo == null) {
            return new iiopURLContext(env);
        }
        if (urlInfo instanceof String) {
            return getUsingURL((String)urlInfo, env);
        } else if (urlInfo instanceof String[]) {
            return getUsingURLs((String[])urlInfo, env);
        } else {
            throw (new IllegalArgumentException(
                    "iiopURLContextFactory.getObjectInstance: " +
                    "argument must be a URL String or array of URLs"));
        }
    }

    /**
      * Resolves 'name' into a target context with remaining name.
      * It only resolves the hostname/port number. The remaining name
      * contains the rest of the name found in the URL.
      *
      * For example, with a iiop URL "iiop://localhost:900/rest/of/name",
      * this method resolves "iiop://localhost:900/" to the "NameService"
      * context on for the ORB at 'localhost' on port 900,
      * and returns as the remaining name "rest/of/name".
      */
    static ResolveResult getUsingURLIgnoreRest(String url, Hashtable<?,?> env)
        throws NamingException {
        return CNCtx.createUsingURL(url, env);
    }

    private static Object getUsingURL(String url, Hashtable<?,?> env)
        throws NamingException {
        ResolveResult res = getUsingURLIgnoreRest(url, env);

        Context ctx = (Context)res.getResolvedObj();
        try {
            return ctx.lookup(res.getRemainingName());
        } finally {
            ctx.close();
        }
    }

    private static Object getUsingURLs(String[] urls, Hashtable<?,?> env) {
        for (int i = 0; i < urls.length; i++) {
            String url = urls[i];
            try {
                Object obj = getUsingURL(url, env);
                if (obj != null) {
                    return obj;
                }
            } catch (NamingException e) {
            }
        }
        return null;    // %%% exception??
    }
}
