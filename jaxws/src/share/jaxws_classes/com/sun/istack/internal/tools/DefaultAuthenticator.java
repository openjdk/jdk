/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.istack.internal.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.Authenticator;
import java.net.Authenticator.RequestorType;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.xml.sax.Locator;
import org.xml.sax.helpers.LocatorImpl;

/**
 * @author Vivek Pandey
 * @author Lukas Jungmann
 */
public class DefaultAuthenticator extends Authenticator {

    private static DefaultAuthenticator instance;
    private static Authenticator systemAuthenticator = getCurrentAuthenticator();
    private String proxyUser;
    private String proxyPasswd;
    private final List<AuthInfo> authInfo = new ArrayList<AuthInfo>();
    private static int counter = 0;

    DefaultAuthenticator() {
        //try undocumented but often used properties
        if (System.getProperty("http.proxyUser") != null) {
            proxyUser = System.getProperty("http.proxyUser");
        } else {
            proxyUser = System.getProperty("proxyUser");
        }
        if (System.getProperty("http.proxyPassword") != null) {
            proxyPasswd = System.getProperty("http.proxyPassword");
        } else {
            proxyPasswd = System.getProperty("proxyPassword");
        }
    }

    public static synchronized DefaultAuthenticator getAuthenticator() {
        if (instance == null) {
            instance = new DefaultAuthenticator();
            Authenticator.setDefault(instance);
        }
        counter++;
        return instance;
    }

    public static synchronized void reset() {
        --counter;
        if (instance != null && counter == 0) {
            Authenticator.setDefault(systemAuthenticator);
        }
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        //If user sets proxy user and passwd and the RequestType is from proxy server then create
        // PasswordAuthentication using proxyUser and proxyPasswd;
        if ((getRequestorType() == RequestorType.PROXY) && proxyUser != null && proxyPasswd != null) {
            return new PasswordAuthentication(proxyUser, proxyPasswd.toCharArray());
        }
        for (AuthInfo auth : authInfo) {
            if (auth.matchingHost(getRequestingURL())) {
                return new PasswordAuthentication(auth.getUser(), auth.getPassword().toCharArray());
            }
        }
        return null;
    }

    /**
     * Proxy authorization string in form of username:password.
     *
     * @param proxyAuth
     */
    public void setProxyAuth(String proxyAuth) {
        if (proxyAuth == null) {
            this.proxyUser = null;
            this.proxyPasswd = null;
        } else {
            int i = proxyAuth.indexOf(':');
            if (i < 0) {
                this.proxyUser = proxyAuth;
                this.proxyPasswd = "";
            } else if (i == 0) {
                this.proxyUser = "";
                this.proxyPasswd = proxyAuth.substring(1);
            } else {
                this.proxyUser = proxyAuth.substring(0, i);
                this.proxyPasswd = proxyAuth.substring(i + 1);
            }
        }
    }

    public void setAuth(File f, Receiver l) {
        Receiver listener = l == null ? new DefaultRImpl() : l;
        BufferedReader in = null;
        FileInputStream fi = null;
        InputStreamReader is = null;
        try {
            String text;
            LocatorImpl locator = new LocatorImpl();
            locator.setSystemId(f.getAbsolutePath());
            try {
                fi = new FileInputStream(f);
                is = new InputStreamReader(fi, "UTF-8");
                in = new BufferedReader(is);
            } catch (UnsupportedEncodingException e) {
                listener.onError(e, locator);
                return;
            } catch (FileNotFoundException e) {
                listener.onError(e, locator);
                return;
            }
            try {
                int lineno = 1;
                locator.setSystemId(f.getCanonicalPath());
                while ((text = in.readLine()) != null) {
                    locator.setLineNumber(lineno++);
                    //ignore empty lines and treat those starting with '#' as comments
                    if ("".equals(text.trim()) || text.startsWith("#")) {
                        continue;
                    }
                    try {
                        AuthInfo ai = parseLine(text);
                        authInfo.add(ai);
                    } catch (Exception e) {
                        listener.onParsingError(text, locator);
                    }
                }
            } catch (IOException e) {
                listener.onError(e, locator);
                Logger.getLogger(DefaultAuthenticator.class.getName()).log(Level.SEVERE, e.getMessage(), e);
            }
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (is != null) {
                    is.close();
                }
                if (fi != null) {
                    fi.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(DefaultAuthenticator.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private AuthInfo parseLine(String text) throws Exception {
        URL url;
        try {
            url = new URL(text);
        } catch (MalformedURLException mue) {
            //possible cause of this can be that password contains
            //character which has to be encoded in URL,
            //such as '@', ')', '#' and few others
            //so try to recreate the URL with encoded string
            //between 2nd ':' and last '@'
            int i = text.indexOf(':', text.indexOf(':') + 1) + 1;
            int j = text.lastIndexOf('@');
            String encodedUrl =
                    text.substring(0, i)
                    + URLEncoder.encode(text.substring(i, j), "UTF-8")
                    + text.substring(j);
            url = new URL(encodedUrl);
        }

        String authinfo = url.getUserInfo();

        if (authinfo != null) {
            int i = authinfo.indexOf(':');

            if (i >= 0) {
                String user = authinfo.substring(0, i);
                String password = authinfo.substring(i + 1);
                return new AuthInfo(
                        new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getFile()),
                        user, URLDecoder.decode(password, "UTF-8"));
            }
        }
        throw new Exception();
    }

    static Authenticator getCurrentAuthenticator() {
        final Field f = getTheAuthenticator();
        if (f == null) {
            return null;
        }

        try {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    f.setAccessible(true);
                    return null;
                }
            });
            return (Authenticator) f.get(null);
        } catch (Exception ex) {
            return null;
        } finally {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    f.setAccessible(false);
                    return null;
                }
            });
        }
    }

    private static Field getTheAuthenticator() {
        try {
            return Authenticator.class.getDeclaredField("theAuthenticator");
        } catch (Exception ex) {
            return null;
        }
    }

    public static interface Receiver {

        void onParsingError(String line, Locator loc);

        void onError(Exception e, Locator loc);
    }

    private static class DefaultRImpl implements Receiver {

        @Override
        public void onParsingError(String line, Locator loc) {
            System.err.println(getLocationString(loc) + ": " + line);
        }

        @Override
        public void onError(Exception e, Locator loc) {
            System.err.println(getLocationString(loc) + ": " + e.getMessage());
            Logger.getLogger(DefaultAuthenticator.class.getName()).log(Level.SEVERE, e.getMessage(), e);
        }

        private String getLocationString(Locator l) {
            return "[" + l.getSystemId() + "#" + l.getLineNumber() + "]";
        }
    }

    /**
     * Represents authorization information needed by
     * {@link DefaultAuthenticator} to authenticate access to remote resources.
     *
     * @author Vivek Pandey
     * @author Lukas Jungmann
     */
    final static class AuthInfo {

        private final String user;
        private final String password;
        private final Pattern urlPattern;

        public AuthInfo(URL url, String user, String password) {
            String u = url.toExternalForm().replaceFirst("\\?", "\\\\?");
            this.urlPattern = Pattern.compile(u.replace("*", ".*"), Pattern.CASE_INSENSITIVE);
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
         * Returns if the requesting host and port are associated with this
         * {@link AuthInfo}
         */
        public boolean matchingHost(URL requestingURL) {
            return urlPattern.matcher(requestingURL.toExternalForm()).matches();
        }
    }
}
