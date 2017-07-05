/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.URIParameter;

import javax.security.auth.login.Configuration;
import javax.security.auth.login.ConfigurationSpi;
import javax.security.auth.login.AppConfigurationEntry;

import com.sun.security.auth.login.ConfigFile;

/**
 * This class wraps the ConfigFile subclass implementation of Configuration
 * inside a ConfigurationSpi implementation that is available from the
 * SUN provider via the Configuration.getInstance calls.
 *
 */
public final class ConfigSpiFile extends ConfigurationSpi {

    private ConfigFile cf;

    public ConfigSpiFile(final Configuration.Parameters params)
        throws java.io.IOException {

        // call in a doPrivileged
        //
        // we have already passed the Configuration.getInstance
        // security check.  also this class is not freely accessible
        // (it is in the "sun" package).
        //
        // we can not put doPrivileged calls into
        // ConfigFile because it is a public com.sun class

        try {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                if (params == null) {
                    cf = new ConfigFile();
                } else {
                    if (!(params instanceof URIParameter)) {
                        throw new IllegalArgumentException
                                ("Unrecognized parameter: " + params);
                    }
                    URIParameter uriParam = (URIParameter)params;

                    cf = new ConfigFile(uriParam.getURI());
                }
                return null;
            }
            });
        } catch (SecurityException se) {

            // if ConfigFile threw a standalone SecurityException
            // (no cause), re-throw it.
            //
            // ConfigFile chains checked IOExceptions to SecurityException.

            Throwable cause = se.getCause();
            if (cause != null && cause instanceof java.io.IOException) {
                throw (java.io.IOException)cause;
            }

            // unrecognized cause
            throw se;
        }

        // if ConfigFile throws some other RuntimeException,
        // let it percolate up naturally.
    }

    protected AppConfigurationEntry[] engineGetAppConfigurationEntry
                (String name) {
        return cf.getAppConfigurationEntry(name);
    }

    protected void engineRefresh() {
        cf.refresh();
    }
}
