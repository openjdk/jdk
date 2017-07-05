/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.tools.internal.ws.wscompile;

import com.sun.istack.internal.NotNull;
import com.sun.tools.internal.ws.resources.WscompileMessages;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.LocatorImpl;

import java.io.*;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vivek Pandey
 */
public class DefaultAuthenticator extends Authenticator {

    private final List<AuthInfo> authInfo = new ArrayList<AuthInfo>();
    private final ErrorReceiver errReceiver;
    private final String proxyUser;
    private final String proxyPasswd;

    //can user.home value be null?
    public static final String defaultAuthfile = System.getProperty("user.home")+ System.getProperty("file.separator")+".metro"+System.getProperty("file.separator")+"auth";
    private File authFile = new File(defaultAuthfile);
    private boolean giveError;

    public DefaultAuthenticator(@NotNull ErrorReceiver receiver, @NotNull File authfile) throws BadCommandLineException {
        this.errReceiver = receiver;
        this.proxyUser = System.getProperty("http.proxyUser");
        this.proxyPasswd = System.getProperty("http.proxyPassword");

        if(authfile != null){
            this.authFile = authfile;
            this.giveError = true;
        }

        if(!authFile.exists()){
            try {
                error(new SAXParseException(WscompileMessages.WSIMPORT_AUTH_FILE_NOT_FOUND(authFile.getCanonicalPath(), defaultAuthfile), null));
            } catch (IOException e) {
                error(new SAXParseException(WscompileMessages.WSIMPORT_FAILED_TO_PARSE(authFile,e.getMessage()), null));
            }
            return;
        }

        if(!authFile.canRead()){
            error(new SAXParseException("Authorization file: "+authFile + " does not have read permission!", null));
            return;
        }
        parseAuth();
    }

    protected PasswordAuthentication getPasswordAuthentication() {
        //If user sets proxy user and passwd and the RequestType is from proxy server then create
        // PasswordAuthentication using proxyUser and proxyClass;
        if((getRequestorType() == RequestorType.PROXY) && proxyUser != null && proxyPasswd != null){
            return new PasswordAuthentication(proxyUser, proxyPasswd.toCharArray());
        }
        for(AuthInfo auth:authInfo){
            if(auth.matchingHost(getRequestingURL())){
                return new PasswordAuthentication(auth.getUser(), auth.getPassword().toCharArray());
            }
        }
        return null;
    }

    private void parseAuth() {
        errReceiver.info(new SAXParseException(WscompileMessages.WSIMPORT_READING_AUTH_FILE(authFile), null));

        BufferedReader in;
        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(authFile), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            error(new SAXParseException(e.getMessage(), null));
            return;
        } catch (FileNotFoundException e) {
            error(new SAXParseException(WscompileMessages.WSIMPORT_AUTH_FILE_NOT_FOUND(authFile, defaultAuthfile), null, e));
            return;
        }
        String text;
        LocatorImpl locator = new LocatorImpl();
        try {
            int lineno = 1;

            locator.setSystemId(authFile.getCanonicalPath());

            while ((text = in.readLine()) != null) {
                locator.setLineNumber(lineno++);
                try {
                    URL url = new URL(text);
                    String authinfo = url.getUserInfo();

                    if (authinfo != null) {
                        int i = authinfo.indexOf(':');

                        if (i >= 0) {
                            String user = authinfo.substring(0, i);
                            String password = authinfo.substring(i + 1);
                            authInfo.add(new AuthInfo(new URL(text), user, password));
                        } else {
                            error(new SAXParseException(WscompileMessages.WSIMPORT_ILLEGAL_AUTH_INFO(url), locator));
                        }
                    } else {
                        error(new SAXParseException(WscompileMessages.WSIMPORT_ILLEGAL_AUTH_INFO(url), locator));
                    }

                } catch (NumberFormatException e) {
                    error(new SAXParseException(WscompileMessages.WSIMPORT_ILLEGAL_AUTH_INFO(text), locator));
                }
            }
            in.close();
        } catch (IOException e) {
            error(new SAXParseException(WscompileMessages.WSIMPORT_FAILED_TO_PARSE(authFile,e.getMessage()), locator));
        }
    }

    /**
     * When user provides authfile explicitly using -Xauthfile we throw error otherwise show the mesage by default with -Xdebug flag
     */
    private void error(SAXParseException e){
        if(giveError){
            errReceiver.error(e);
        } else{
            errReceiver.debug(e);
        }
    }
}
