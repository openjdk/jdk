/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
package sun.net.www.protocol.http;

import java.net.URL;
import java.net.PasswordAuthentication;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import sun.util.logging.PlatformLogger;

/**
 * Proxy class for loading NTLMAuthentication, so as to remove static
 * dependancy.
 */
class NTLMAuthenticationProxy {
    private static Method supportsTA;
    private static final String clazzStr = "sun.net.www.protocol.http.ntlm.NTLMAuthentication";
    private static final String supportsTAStr = "supportsTransparentAuth";

    static final NTLMAuthenticationProxy proxy = tryLoadNTLMAuthentication();
    static final boolean supported = proxy != null ? true : false;
    static final boolean supportsTransparentAuth = supported ? supportsTransparentAuth(supportsTA) : false;

    private final Constructor<? extends AuthenticationInfo> threeArgCtr;
    private final Constructor<? extends AuthenticationInfo> fiveArgCtr;

    private NTLMAuthenticationProxy(Constructor<? extends AuthenticationInfo> threeArgCtr,
                                    Constructor<? extends AuthenticationInfo> fiveArgCtr) {
        this.threeArgCtr = threeArgCtr;
        this.fiveArgCtr = fiveArgCtr;
    }


    AuthenticationInfo create(boolean isProxy,
                              URL url,
                              PasswordAuthentication pw) {
        try {
            return threeArgCtr.newInstance(isProxy, url, pw);
        } catch (ReflectiveOperationException roe) {
            finest(roe);
        }

        return null;
    }

    AuthenticationInfo create(boolean isProxy,
                              String host,
                              int port,
                              PasswordAuthentication pw) {
        try {
            return fiveArgCtr.newInstance(isProxy, host, port, pw);
        } catch (ReflectiveOperationException roe) {
            finest(roe);
        }

        return null;
    }

    /* Returns true if the NTLM implementation supports transparent
     * authentication (try with the current users credentials before
     * prompting for username and password, etc).
     */
    private static boolean supportsTransparentAuth(Method method) {
        try {
            return (Boolean)method.invoke(null);
        } catch (ReflectiveOperationException roe) {
            finest(roe);
        }

        return false;
    }

    /**
     * Loads the NTLM authentiation implementation through reflection. If
     * the class is present, then it must have the required constructors and
     * method. Otherwise, it is considered an error.
     */
    @SuppressWarnings("unchecked")
    private static NTLMAuthenticationProxy tryLoadNTLMAuthentication() {
        Class<? extends AuthenticationInfo> cl;
        Constructor<? extends AuthenticationInfo> threeArg, fiveArg;
        try {
            cl = (Class<? extends AuthenticationInfo>)Class.forName(clazzStr, true, null);
            if (cl != null) {
                threeArg = cl.getConstructor(boolean.class,
                                             URL.class,
                                             PasswordAuthentication.class);
                fiveArg = cl.getConstructor(boolean.class,
                                            String.class,
                                            int.class,
                                            PasswordAuthentication.class);
                supportsTA = cl.getDeclaredMethod(supportsTAStr);
                return new NTLMAuthenticationProxy(threeArg,
                                                   fiveArg);
            }
        } catch (ClassNotFoundException cnfe) {
            finest(cnfe);
        } catch (ReflectiveOperationException roe) {
            throw new AssertionError(roe);
        }

        return null;
    }

    static void finest(Exception e) {
        PlatformLogger logger = HttpURLConnection.getHttpLogger();
        logger.finest("NTLMAuthenticationProxy: " + e);
    }
}
