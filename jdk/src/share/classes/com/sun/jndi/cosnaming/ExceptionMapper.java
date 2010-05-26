/*
 * Copyright (c) 1999, 2005, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jndi.cosnaming;

import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.spi.*;

import org.omg.CosNaming.*;
import org.omg.CosNaming.NamingContextPackage.*;
import org.omg.CORBA.*;

/**
  * A convenience class to map the COS Naming exceptions to the JNDI exceptions.
  * @author Raj Krishnamurthy
  */

public final class ExceptionMapper {
    private ExceptionMapper() {} // ensure no instance
    private static final boolean debug = false;

    public static final NamingException mapException(Exception e,
        CNCtx ctx, NameComponent[] inputName) throws NamingException {
        if (e instanceof NamingException) {
            return (NamingException)e;
        }

        if (e instanceof RuntimeException) {
            throw (RuntimeException)e;
        }

        NamingException ne;
        if (e instanceof NotFound) {
            if (ctx.federation) {
                return tryFed((NotFound)e, ctx, inputName);

            } else {
                ne = new NameNotFoundException();
            }

        } else if (e instanceof CannotProceed) {

            ne = new CannotProceedException();
            NamingContext nc = ((CannotProceed) e).cxt;
            NameComponent[] rest = ((CannotProceed) e).rest_of_name;

            // %%% We assume that rest returns *all* unprocessed components.
            // Don't' know if that is a good assumption, given
            // NotFound doesn't set rest as expected. -RL
            if (inputName != null && (inputName.length > rest.length)) {
                NameComponent[] resolvedName =
                    new NameComponent[inputName.length - rest.length];
                System.arraycopy(inputName, 0, resolvedName, 0, resolvedName.length);
                // Wrap resolved NamingContext inside a CNCtx
                // Guess that its name (which is relative to ctx)
                // is the part of inputName minus rest_of_name
                ne.setResolvedObj(new CNCtx(ctx._orb, ctx.orbTracker, nc,
                                                ctx._env,
                    ctx.makeFullName(resolvedName)));
            } else {
                ne.setResolvedObj(ctx);
            }

            ne.setRemainingName(CNNameParser.cosNameToName(rest));

        } else if (e instanceof InvalidName) {
            ne = new InvalidNameException();
        } else if (e instanceof AlreadyBound) {
            ne = new NameAlreadyBoundException();
        } else if (e instanceof NotEmpty) {
            ne = new ContextNotEmptyException();
        } else {
            ne = new NamingException("Unknown reasons");
        }

        ne.setRootCause(e);
        return ne;
    }

    private static final NamingException tryFed(NotFound e, CNCtx ctx,
        NameComponent[] inputName) throws NamingException {
        NameComponent[] rest = ((NotFound) e).rest_of_name;

        if (debug) {
            System.out.println(((NotFound)e).why.value());
            System.out.println(rest.length);
        }

        // %%% Using 1.2 & 1.3 Sun's tnameserv, 'rest' contains only the first
        // component that failed, not *rest* as advertized. This is useless
        // because what if you have something like aa/aa/aa/aa/aa.
        // If one of those is not found, you get "aa" as 'rest'.
        if (rest.length == 1 && inputName != null) {
            // Check that we're not talking to 1.2/1.3 Sun tnameserv
            NameComponent lastIn = inputName[inputName.length-1];
            if (rest[0].id.equals(lastIn.id) &&
                rest[0].kind != null &&
                rest[0].kind.equals(lastIn.kind)) {
                // Might be legit
                ;
            } else {
                // Due to 1.2/1.3 bug that always returns single-item 'rest'
                NamingException ne = new NameNotFoundException();
                ne.setRemainingName(CNNameParser.cosNameToName(rest));
                ne.setRootCause(e);
                throw ne;
            }
        }
        // Fixed in 1.4; perform calculations based on correct (1.4) behavior

        // Calculate the components of the name that has been resolved
        NameComponent[] resolvedName = null;
        int len = 0;
        if (inputName != null && (inputName.length >= rest.length)) {

            if (e.why == NotFoundReason.not_context) {
                // First component of rest is found but not a context; keep it
                // as part of resolved name
                len = inputName.length - (rest.length - 1);

                // Remove resolved component from rest
                if (rest.length == 1) {
                    // No more remaining
                    rest = null;
                } else {
                    NameComponent[] tmp = new NameComponent[rest.length-1];
                    System.arraycopy(rest, 1, tmp, 0, tmp.length);
                    rest = tmp;
                }
            } else {
                len = inputName.length - rest.length;
            }

            if (len > 0) {
                resolvedName = new NameComponent[len];
                System.arraycopy(inputName, 0, resolvedName, 0, len);
            }
        }

        // Create CPE and set common fields
        CannotProceedException cpe = new CannotProceedException();
        cpe.setRootCause(e);
        if (rest != null && rest.length > 0) {
            cpe.setRemainingName(CNNameParser.cosNameToName(rest));
        }
        cpe.setEnvironment(ctx._env);

        if (debug) {
            System.out.println("rest of name: " + cpe.getRemainingName());
        }

        // Lookup resolved name to get resolved object
        final java.lang.Object resolvedObj =
            (resolvedName != null) ? ctx.callResolve(resolvedName) : ctx;

        if (resolvedObj instanceof javax.naming.Context) {
            // obj is a context and child is not found
            // try getting its nns dynamically by constructing
            // a Reference containing obj.
            RefAddr addr = new RefAddr("nns") {
                public java.lang.Object getContent() {
                    return resolvedObj;
                }
                private static final long serialVersionUID =
                    669984699392133792L;
            };
            Reference ref = new Reference("java.lang.Object", addr);

            // Resolved name has trailing slash to indicate nns
            CompositeName cname = new CompositeName();
            cname.add(""); // add trailing slash

            cpe.setResolvedObj(ref);
            cpe.setAltName(cname);
            cpe.setAltNameCtx((javax.naming.Context)resolvedObj);

            return cpe;
        } else {
            // Not a context, use object factory to transform object.

            Name cname = CNNameParser.cosNameToName(resolvedName);
            java.lang.Object resolvedObj2;
            try {
                resolvedObj2 = NamingManager.getObjectInstance(resolvedObj,
                    cname, ctx, ctx._env);
            } catch (NamingException ge) {
                throw ge;
            } catch (Exception ge) {
                NamingException ne = new NamingException(
                    "problem generating object using object factory");
                ne.setRootCause(ge);
                throw ne;
            }

            // If a context, continue operation with context
            if (resolvedObj2 instanceof javax.naming.Context) {
                cpe.setResolvedObj(resolvedObj2);
            } else {
                // Add trailing slash
                cname.add("");
                cpe.setAltName(cname);

                // Create nns reference
                final java.lang.Object rf2 = resolvedObj2;
                RefAddr addr = new RefAddr("nns") {
                    public java.lang.Object getContent() {
                        return rf2;
                    }
                    private static final long serialVersionUID =
                        -785132553978269772L;
                };
                Reference ref = new Reference("java.lang.Object", addr);
                cpe.setResolvedObj(ref);
                cpe.setAltNameCtx(ctx);
            }
            return cpe;
        }
    }
}
