/*
 * Copyright (c) 1999, 2006, Oracle and/or its affiliates. All rights reserved.
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

package sun.applet;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Vector;
import sun.net.www.ParseUtil;

/**
 * The main entry point into AppletViewer.
 */
public class Main {
    /**
     * The file which contains all of the AppletViewer specific properties.
     */
    static File theUserPropertiesFile;

    /**
     * The default key/value pairs for the required user-specific properties.
     */
    static final String [][] avDefaultUserProps = {
        // There's a bootstrapping problem here.  If we don't have a proxyHost,
        // then we will not be able to connect to a URL outside the firewall;
        // however, there's no way for us to set the proxyHost without starting
        // AppletViewer.  This problem existed before the re-write.
        {"http.proxyHost", ""},
        {"http.proxyPort", "80"},
        {"package.restrict.access.sun", "true"}
    };

    static {
        File userHome = new File(System.getProperty("user.home"));
        // make sure we can write to this location
        userHome.canWrite();

        theUserPropertiesFile = new File(userHome, ".appletviewer");
    }

    // i18n
    private static AppletMessageHandler amh = new AppletMessageHandler("appletviewer");

    /**
     * Member variables set according to options passed in to AppletViewer.
     */
    private boolean debugFlag = false;
    private boolean helpFlag  = false;
    private String  encoding  = null;
    private boolean noSecurityFlag  = false;
    private static boolean cmdLineTestFlag = false;

    /**
     * The list of valid URLs passed in to AppletViewer.
     */
    private static Vector urlList = new Vector(1);

    // This is used in init().  Getting rid of this is desirable but depends
    // on whether the property that uses it is necessary/standard.
    public static final String theVersion = System.getProperty("java.version");

    /**
     * The main entry point into AppletViewer.
     */
    public static void main(String [] args) {
        Main m = new Main();
        int ret = m.run(args);

        // Exit immediately if we got some sort of error along the way.
        // For debugging purposes, if we have passed in "-XcmdLineTest" we
        // force a premature exit.
        if ((ret != 0) || (cmdLineTestFlag))
            System.exit(ret);
    }

    private int run(String [] args) {
        // DECODE ARGS
        try {
            if (args.length == 0) {
                usage();
                return 0;
            }
            for (int i = 0; i < args.length; ) {
                int j = decodeArg(args, i);
                if (j == 0) {
                    throw new ParseException(lookup("main.err.unrecognizedarg",
                                                    args[i]));
                }
                i += j;
            }
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            return 1;
        }

        // CHECK ARGUMENTS
        if (helpFlag) {
            usage();
            return 0;
        }

        if (urlList.size() == 0) {
            System.err.println(lookup("main.err.inputfile"));
            return 1;
        }

        if (debugFlag) {
            // START A DEBUG SESSION
            // Given the current architecture, we will end up decoding the
            // arguments again, but at least we are guaranteed to have
            // arguments which are valid.
            return invokeDebugger(args);
        }

        // INSTALL THE SECURITY MANAGER (if necessary)
        if (!noSecurityFlag && (System.getSecurityManager() == null))
            init();

        // LAUNCH APPLETVIEWER FOR EACH URL
        for (int i = 0; i < urlList.size(); i++) {
            try {
                // XXX 5/17 this parsing method should be changed/fixed so that
                // it doesn't do both parsing of the html file and launching of
                // the AppletPanel
                AppletViewer.parse((URL) urlList.elementAt(i), encoding);
            } catch (IOException e) {
                System.err.println(lookup("main.err.io", e.getMessage()));
                return 1;
            }
        }
        return 0;
    }

    private static void usage() {
        System.out.println(lookup("usage"));
    }

    /**
     * Decode a single argument in an array and return the number of elements
     * used.
     *
     * @param args The array of arguments.
     * @param i    The argument to decode.
     * @return     The number of array elements used when the argument was
     *             decoded.
     * @exception ParseException
     *             Thrown when there is a problem with something in the
     *             argument array.
     */
    private int decodeArg(String [] args, int i) throws ParseException {
        String arg = args[i];
        int argc = args.length;

        if ("-help".equalsIgnoreCase(arg) || "-?".equals(arg)) {
            helpFlag = true;
            return 1;
        } else if ("-encoding".equals(arg) && (i < argc - 1)) {
            if (encoding != null)
                throw new ParseException(lookup("main.err.dupoption", arg));
            encoding = args[++i];
            return 2;
        } else if ("-debug".equals(arg)) {
            debugFlag = true;
            return 1;
        } else if ("-Xnosecurity".equals(arg)) {
            // This is an undocumented (and, in the future, unsupported)
            // flag which prevents AppletViewer from installing its own
            // SecurityManager.

            System.err.println();
            System.err.println(lookup("main.warn.nosecmgr"));
            System.err.println();

            noSecurityFlag = true;
            return 1;
        } else if ("-XcmdLineTest".equals(arg)) {
            // This is an internal flag which should be used for command-line
            // testing.  It instructs AppletViewer to force a premature exit
            // immediately after the applet has been launched.
            cmdLineTestFlag = true;
            return 1;
        } else if (arg.startsWith("-")) {
            throw new ParseException(lookup("main.err.unsupportedopt", arg));
        } else {
            // we found what we hope is a url
            URL url = parseURL(arg);
            if (url != null) {
                urlList.addElement(url);
                return 1;
            }
        }
        return 0;
    }

    /**
     * Following the relevant RFC, construct a valid URL based on the passed in
     * string.
     *
     * @param url  a string which represents either a relative or absolute URL.
     * @return     a URL when the passed in string can be interpreted according
     *             to the RFC, <code>null</code> otherwise.
     * @exception  ParseException
     *             Thrown when we are unable to construct a proper URL from the
     *             passed in string.
     */
    private URL parseURL(String url) throws ParseException {
        URL u = null;
        // prefix of the urls with 'file' scheme
        String prefix = "file:";

        try {
            if (url.indexOf(':') <= 1)
            {
                // appletviewer accepts only unencoded filesystem paths
                u = ParseUtil.fileToEncodedURL(new File(url));
            } else if (url.startsWith(prefix) &&
                       url.length() != prefix.length() &&
                       !(new File(url.substring(prefix.length())).isAbsolute()))
            {
                // relative file URL, like this "file:index.html"
                // ensure that this file URL is absolute
                // ParseUtil.fileToEncodedURL should be done last (see 6329251)
                String path = ParseUtil.fileToEncodedURL(new File(System.getProperty("user.dir"))).getPath() +
                    url.substring(prefix.length());
                u = new URL("file", "", path);
            } else {
                // appletviewer accepts only encoded urls
                u = new URL(url);
            }
        } catch (MalformedURLException e) {
            throw new ParseException(lookup("main.err.badurl",
                                            url, e.getMessage()));
        }

        return u;
    }

    /**
     * Invoke the debugger with the arguments passed in to appletviewer.
     *
     * @param args The arguments passed into the debugger.
     * @return     <code>0</code> if the debugger is invoked successfully,
     *             <code>1</code> otherwise.
     */
    private int invokeDebugger(String [] args) {
        // CONSTRUCT THE COMMAND LINE
        String [] newArgs = new String[args.length + 1];
        int current = 0;

        // Add a -classpath argument that prevents
        // the debugger from launching appletviewer with the default of
        // ".". appletviewer's classpath should never contain valid
        // classes since they will result in security exceptions.
        // Ideally, the classpath should be set to "", but the VM won't
        // allow an empty classpath, so a phony directory name is used.
        String phonyDir = System.getProperty("java.home") +
                          File.separator + "phony";
        newArgs[current++] = "-Djava.class.path=" + phonyDir;

        // Appletviewer's main class is the debuggee
        newArgs[current++] = "sun.applet.Main";

        // Append all the of the original appletviewer arguments,
        // leaving out the "-debug" option.
        for (int i = 0; i < args.length; i++) {
            if (!("-debug".equals(args[i]))) {
                newArgs[current++] = args[i];
            }
        }

        // LAUNCH THE DEBUGGER
        // Reflection is used for two reasons:
        // 1) The debugger classes are on classpath and thus must be loaded
        // by the application class loader. (Currently, appletviewer are
        // loaded through the boot class path out of rt.jar.)
        // 2) Reflection removes any build dependency between appletviewer
        // and jdb.
        try {
            Class c = Class.forName("com.sun.tools.example.debug.tty.TTY", true,
                                    ClassLoader.getSystemClassLoader());
            Method m = c.getDeclaredMethod("main",
                                           new Class[] { String[].class });
            m.invoke(null, new Object[] { newArgs });
        } catch (ClassNotFoundException cnfe) {
            System.err.println(lookup("main.debug.cantfinddebug"));
            return 1;
        } catch (NoSuchMethodException nsme) {
            System.err.println(lookup("main.debug.cantfindmain"));
            return 1;
        } catch (InvocationTargetException ite) {
            System.err.println(lookup("main.debug.exceptionindebug"));
            return 1;
        } catch (IllegalAccessException iae) {
            System.err.println(lookup("main.debug.cantaccess"));
            return 1;
        }
        return 0;
    }

    private void init() {
        // GET APPLETVIEWER USER-SPECIFIC PROPERTIES
        Properties avProps = getAVProps();

        // ADD OTHER RANDOM PROPERTIES
        // XXX 5/18 need to revisit why these are here, is there some
        // standard for what is available?

        // Standard browser properties
        avProps.put("browser", "sun.applet.AppletViewer");
        avProps.put("browser.version", "1.06");
        avProps.put("browser.vendor", "Sun Microsystems Inc.");
        avProps.put("http.agent", "Java(tm) 2 SDK, Standard Edition v" + theVersion);

        // Define which packages can be extended by applets
        // XXX 5/19 probably not needed, not checked in AppletSecurity
        avProps.put("package.restrict.definition.java", "true");
        avProps.put("package.restrict.definition.sun", "true");

        // Define which properties can be read by applets.
        // A property named by "key" can be read only when its twin
        // property "key.applet" is true.  The following ten properties
        // are open by default.  Any other property can be explicitly
        // opened up by the browser user by calling appletviewer with
        // -J-Dkey.applet=true
        avProps.put("java.version.applet", "true");
        avProps.put("java.vendor.applet", "true");
        avProps.put("java.vendor.url.applet", "true");
        avProps.put("java.class.version.applet", "true");
        avProps.put("os.name.applet", "true");
        avProps.put("os.version.applet", "true");
        avProps.put("os.arch.applet", "true");
        avProps.put("file.separator.applet", "true");
        avProps.put("path.separator.applet", "true");
        avProps.put("line.separator.applet", "true");

        // Read in the System properties.  If something is going to be
        // over-written, warn about it.
        Properties sysProps = System.getProperties();
        for (Enumeration e = sysProps.propertyNames(); e.hasMoreElements(); ) {
            String key = (String) e.nextElement();
            String val = (String) sysProps.getProperty(key);
            String oldVal;
            if ((oldVal = (String) avProps.setProperty(key, val)) != null)
                System.err.println(lookup("main.warn.prop.overwrite", key,
                                          oldVal, val));
        }

        // INSTALL THE PROPERTY LIST
        System.setProperties(avProps);

        // Create and install the security manager
        if (!noSecurityFlag) {
            System.setSecurityManager(new AppletSecurity());
        } else {
            System.err.println(lookup("main.nosecmgr"));
        }

        // REMIND: Create and install a socket factory!
    }

    /**
     * Read the AppletViewer user-specific properties.  Typically, these
     * properties should reside in the file $USER/.appletviewer.  If this file
     * does not exist, one will be created.  Information for this file will
     * be gleaned from $USER/.hotjava/properties.  If that file does not exist,
     * then default values will be used.
     *
     * @return     A Properties object containing all of the AppletViewer
     *             user-specific properties.
     */
    private Properties getAVProps() {
        Properties avProps = new Properties();

        File dotAV = theUserPropertiesFile;
        if (dotAV.exists()) {
            // we must have already done the conversion
            if (dotAV.canRead()) {
                // just read the file
                avProps = getAVProps(dotAV);
            } else {
                // send out warning and use defaults
                System.err.println(lookup("main.warn.cantreadprops",
                                          dotAV.toString()));
                avProps = setDefaultAVProps();
            }
        } else {
            // create the $USER/.appletviewer file

            // see if $USER/.hotjava/properties exists
            File userHome = new File(System.getProperty("user.home"));
            File dotHJ = new File(userHome, ".hotjava");
            dotHJ = new File(dotHJ, "properties");
            if (dotHJ.exists()) {
                // just read the file
                avProps = getAVProps(dotHJ);
            } else {
                // send out warning and use defaults
                System.err.println(lookup("main.warn.cantreadprops",
                                          dotHJ.toString()));
                avProps = setDefaultAVProps();
            }

            // SAVE THE FILE
            try {
                FileOutputStream out = new FileOutputStream(dotAV);
                avProps.store(out, lookup("main.prop.store"));
                out.close();
            } catch (IOException e) {
                System.err.println(lookup("main.err.prop.cantsave",
                                          dotAV.toString()));
            }
        }
        return avProps;
    }

    /**
     * Set the AppletViewer user-specific properties to be the default values.
     *
     * @return     A Properties object containing all of the AppletViewer
     *             user-specific properties, set to the default values.
     */
    private Properties setDefaultAVProps() {
        Properties avProps = new Properties();
        for (int i = 0; i < avDefaultUserProps.length; i++) {
            avProps.setProperty(avDefaultUserProps[i][0],
                                avDefaultUserProps[i][1]);
        }
        return avProps;
    }

    /**
     * Given a file, find only the properties that are setable by AppletViewer.
     *
     * @param inFile A Properties file from which we select the properties of
     *             interest.
     * @return     A Properties object containing all of the AppletViewer
     *             user-specific properties.
     */
    private Properties getAVProps(File inFile) {
        Properties avProps  = new Properties();

        // read the file
        Properties tmpProps = new Properties();
        try {
            FileInputStream in = new FileInputStream(inFile);
            tmpProps.load(new BufferedInputStream(in));
            in.close();
        } catch (IOException e) {
            System.err.println(lookup("main.err.prop.cantread",
                                      inFile.toString()));
        }

        // pick off the properties we care about
        for (int i = 0; i < avDefaultUserProps.length; i++) {
            String value = tmpProps.getProperty(avDefaultUserProps[i][0]);
            if (value != null) {
                // the property exists in the file, so replace the default
                avProps.setProperty(avDefaultUserProps[i][0], value);
            } else {
                // just use the default
                avProps.setProperty(avDefaultUserProps[i][0],
                                    avDefaultUserProps[i][1]);
            }
        }
        return avProps;
    }

    /**
     * Methods for easier i18n handling.
     */

    private static String lookup(String key) {
        return amh.getMessage(key);
    }

    private static String lookup(String key, String arg0) {
        return amh.getMessage(key, arg0);
    }

    private static String lookup(String key, String arg0, String arg1) {
        return amh.getMessage(key, arg0, arg1);
    }

    private static String lookup(String key, String arg0, String arg1,
                                 String arg2) {
        return amh.getMessage(key, arg0, arg1, arg2);
    }

    class ParseException extends RuntimeException
    {
        public ParseException(String msg) {
            super(msg);
        }

        public ParseException(Throwable t) {
            super(t.getMessage());
            this.t = t;
        }

        Throwable t = null;
    }
}
