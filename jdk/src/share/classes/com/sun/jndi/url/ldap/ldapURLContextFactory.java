/*
 * Copyright 1999-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jndi.url.ldap;

import java.util.Hashtable;
import javax.naming.*;
import javax.naming.directory.DirContext;
import javax.naming.spi.*;
import com.sun.jndi.ldap.LdapCtx;
import com.sun.jndi.ldap.LdapCtxFactory;
import com.sun.jndi.ldap.LdapURL;

/**
 * An LDAP URL context factory.
 *
 * @author Rosanna Lee
 * @author Scott Seligman
 * @author Vincent Ryan
 */

public class ldapURLContextFactory implements ObjectFactory {

    public Object getObjectInstance(Object urlInfo, Name name, Context nameCtx,
            Hashtable<?,?> env) throws Exception {

        if (urlInfo == null) {
            return new ldapURLContext(env);
        } else {
            return LdapCtxFactory.getLdapCtxInstance(urlInfo, env);
        }
    }

    static ResolveResult getUsingURLIgnoreRootDN(String url, Hashtable env)
            throws NamingException {
        LdapURL ldapUrl = new LdapURL(url);
        DirContext ctx = new LdapCtx("", ldapUrl.getHost(), ldapUrl.getPort(),
            env, ldapUrl.useSsl());
        String dn = (ldapUrl.getDN() != null ? ldapUrl.getDN() : "");

        // Represent DN as empty or single-component composite name.
        CompositeName remaining = new CompositeName();
        if (!"".equals(dn)) {
            // if nonempty, add component
            remaining.add(dn);
        }

        return new ResolveResult(ctx, remaining);
    }
}
