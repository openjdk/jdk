/*
 * Copyright 2000-2002 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jndi.url.dns;


import java.net.MalformedURLException;
import java.util.Hashtable;

import javax.naming.*;
import javax.naming.spi.ResolveResult;
import com.sun.jndi.dns.*;
import com.sun.jndi.toolkit.url.GenericURLDirContext;


/**
 * A DNS URL context resolves names that are DNS pseudo-URLs.
 * See com.sun.jndi.dns.DnsUrl for a description of the URL format.
 *
 * @author Scott Seligman
 */


public class dnsURLContext extends GenericURLDirContext {

    public dnsURLContext(Hashtable env) {
        super(env);
    }

    /**
     * Resolves the host and port of "url" to a root context connected
     * to the named DNS server, and returns the domain name as the
     * remaining name.
     */
    protected ResolveResult getRootURLContext(String url, Hashtable env)
            throws NamingException {

        DnsUrl dnsUrl;
        try {
            dnsUrl = new DnsUrl(url);
        } catch (MalformedURLException e) {
            throw new InvalidNameException(e.getMessage());
        }

        DnsUrl[] urls = new DnsUrl[] { dnsUrl };
        String domain = dnsUrl.getDomain();

        return new ResolveResult(
                DnsContextFactory.getContext(".", urls, env),
                new CompositeName().add(domain));
    }
}
