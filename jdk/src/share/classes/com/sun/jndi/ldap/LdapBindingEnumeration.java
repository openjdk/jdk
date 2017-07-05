/*
 * Copyright 1999-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jndi.ldap;

import java.util.Vector;
import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.spi.*;

import com.sun.jndi.toolkit.ctx.Continuation;

final class LdapBindingEnumeration extends LdapNamingEnumeration {

    LdapBindingEnumeration(LdapCtx homeCtx, LdapResult answer, Name remain,
        Continuation cont) throws NamingException
    {
        super(homeCtx, answer, remain, cont);
    }

    protected NameClassPair
      createItem(String dn, Attributes attrs, Vector respCtls)
        throws NamingException {

        Object obj = null;
        String atom = getAtom(dn);

        if (attrs.get(Obj.JAVA_ATTRIBUTES[Obj.CLASSNAME]) != null) {
            // serialized object or object reference
            obj = Obj.decodeObject(attrs);
        }
        if (obj == null) {
            // DirContext object
            obj = new LdapCtx(homeCtx, dn);
        }

        CompositeName cn = new CompositeName();
        cn.add(atom);

        try {
            obj = DirectoryManager.getObjectInstance(obj, cn, homeCtx,
                homeCtx.envprops, attrs);

        } catch (NamingException e) {
            throw e;

        } catch (Exception e) {
            NamingException ne =
                new NamingException(
                        "problem generating object using object factory");
            ne.setRootCause(e);
            throw ne;
        }

        Binding binding;
        if (respCtls != null) {
           binding = new BindingWithControls(cn.toString(), obj,
                                homeCtx.convertControls(respCtls));
        } else {
            binding = new Binding(cn.toString(), obj);
        }
        binding.setNameInNamespace(dn);
        return binding;
    }

    protected LdapNamingEnumeration
    getReferredResults(LdapReferralContext refCtx) throws NamingException{
        // repeat the original operation at the new context
        return (LdapNamingEnumeration) refCtx.listBindings(listArg);
    }
}
