/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2002-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * $Id: SecuritySupport.java,v 1.2.4.1 2005/09/15 08:15:09 suresh_emailid Exp $
 */

package com.sun.org.apache.xml.internal.dtm.ref;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import java.util.Properties;

/**
 * This class is duplicated for each Xalan-Java subpackage so keep it in sync.
 * It is package private and therefore is not exposed as part of the Xalan-Java
 * API.
 *
 * Base class with security related methods that work on JDK 1.1.
 */
class SecuritySupport {

    /*
     * Make this of type Object so that the verifier won't try to
     * prove its type, thus possibly trying to load the SecuritySupport12
     * class.
     */
    private static final Object securitySupport;

    static {
        SecuritySupport ss = null;
        try {
            Class c = Class.forName("java.security.AccessController");
            // if that worked, we're on 1.2.
            /*
            // don't reference the class explicitly so it doesn't
            // get dragged in accidentally.
            c = Class.forName("javax.mail.SecuritySupport12");
            Constructor cons = c.getConstructor(new Class[] { });
            ss = (SecuritySupport)cons.newInstance(new Object[] { });
            */
            /*
             * Unfortunately, we can't load the class using reflection
             * because the class is package private.  And the class has
             * to be package private so the APIs aren't exposed to other
             * code that could use them to circumvent security.  Thus,
             * we accept the risk that the direct reference might fail
             * on some JDK 1.1 JVMs, even though we would never execute
             * this code in such a case.  Sigh...
             */
            ss = new SecuritySupport12();
        } catch (Exception ex) {
            // ignore it
        } finally {
            if (ss == null)
                ss = new SecuritySupport();
            securitySupport = ss;
        }
    }

    /**
     * Return an appropriate instance of this class, depending on whether
     * we're on a JDK 1.1 or J2SE 1.2 (or later) system.
     */
    static SecuritySupport getInstance() {
        return (SecuritySupport)securitySupport;
    }

    ClassLoader getContextClassLoader() {
        return null;
    }

    ClassLoader getSystemClassLoader() {
        return null;
    }

    ClassLoader getParentClassLoader(ClassLoader cl) {
        return null;
    }

    String getSystemProperty(String propName) {
        return System.getProperty(propName);
    }

    FileInputStream getFileInputStream(File file)
        throws FileNotFoundException
    {
        return new FileInputStream(file);
    }

    InputStream getResourceAsStream(ClassLoader cl, String name) {
        InputStream ris;
        if (cl == null) {
            ris = ClassLoader.getSystemResourceAsStream(name);
        } else {
            ris = cl.getResourceAsStream(name);
        }
        return ris;
    }

    boolean getFileExists(File f) {
        return f.exists();
    }

    long getLastModified(File f) {
        return f.lastModified();
    }
}
