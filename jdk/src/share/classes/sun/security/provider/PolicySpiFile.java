/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.provider;

import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.PolicySpi;
import java.security.ProtectionDomain;
import java.security.URIParameter;

import java.net.MalformedURLException;

/**
 * This class wraps the PolicyFile subclass implementation of Policy
 * inside a PolicySpi implementation that is available from the SUN provider
 * via the Policy.getInstance calls.
 *
 */
public final class PolicySpiFile extends PolicySpi {

    private PolicyFile pf;

    public PolicySpiFile(Policy.Parameters params) {

        if (params == null) {
            pf = new PolicyFile();
        } else {
            if (!(params instanceof URIParameter)) {
                throw new IllegalArgumentException
                        ("Unrecognized policy parameter: " + params);
            }
            URIParameter uriParam = (URIParameter)params;
            try {
                pf = new PolicyFile(uriParam.getURI().toURL());
            } catch (MalformedURLException mue) {
                throw new IllegalArgumentException("Invalid URIParameter", mue);
            }
        }
    }

    protected PermissionCollection engineGetPermissions(CodeSource codesource) {
        return pf.getPermissions(codesource);
    }

    protected PermissionCollection engineGetPermissions(ProtectionDomain d) {
        return pf.getPermissions(d);
    }

    protected boolean engineImplies(ProtectionDomain d, Permission p) {
        return pf.implies(d, p);
    }

    protected void engineRefresh() {
        pf.refresh();
    }
}
