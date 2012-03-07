/*
 * Copyright (c) 2004, 2005, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.xpath;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Properties;

/**
 * Implementation of {@link XPathFactory#newInstance(String)}.
 *
 * @author <a href="Kohsuke.Kawaguchi@Sun.com">Kohsuke Kawaguchi</a>
 * @since 1.5
 */
class XPathFactoryFinder  {

    private static SecuritySupport ss = new SecuritySupport() ;
    /** debug support code. */
    private static boolean debug = false;
    static {
        // Use try/catch block to support applets
        try {
            debug = ss.getSystemProperty("jaxp.debug") != null;
        } catch (Exception _) {
            debug = false;
        }
    }

    /**
     * <p>Cache properties for performance.</p>
     */
        private static Properties cacheProps = new Properties();

        /**
         * <p>First time requires initialization overhead.</p>
         */
        private static boolean firstTime = true;

    /**
     * <p>Conditional debug printing.</p>
     *
     * @param msg to print
     */
    private static void debugPrintln(String msg) {
        if (debug) {
            System.err.println("JAXP: " + msg);
        }
    }

    /**
     * <p><code>ClassLoader</code> to use to find <code>XPathFactory</code>.</p>
     */
    private final ClassLoader classLoader;

    /**
     * <p>Constructor that specifies <code>ClassLoader</code> to use
     * to find <code>XPathFactory</code>.</p>
     *
     * @param loader
     *      to be used to load resource, {@link XPathFactory}, and
     *      {@link SchemaFactoryLoader} implementations during
     *      the resolution process.
     *      If this parameter is null, the default system class loader
     *      will be used.
     */
    public XPathFactoryFinder(ClassLoader loader) {
        this.classLoader = loader;
        if( debug ) {
            debugDisplayClassLoader();
        }
    }

    private void debugDisplayClassLoader() {
        try {
            if( classLoader == ss.getContextClassLoader() ) {
                debugPrintln("using thread context class loader ("+classLoader+") for search");
                return;
            }
        } catch( Throwable _ ) {
            ; // getContextClassLoader() undefined in JDK1.1
        }

        if( classLoader==ClassLoader.getSystemClassLoader() ) {
            debugPrintln("using system class loader ("+classLoader+") for search");
            return;
        }

        debugPrintln("using class loader ("+classLoader+") for search");
    }

    /**
     * <p>Creates a new {@link XPathFactory} object for the specified
     * schema language.</p>
     *
     * @param uri
     *       Identifies the underlying object model.
     *
     * @return <code>null</code> if the callee fails to create one.
     *
     * @throws NullPointerException
     *      If the parameter is null.
     */
    public XPathFactory newFactory(String uri) {
        if(uri==null)        throw new NullPointerException();
        XPathFactory f = _newFactory(uri);
        if (f != null) {
            debugPrintln("factory '" + f.getClass().getName() + "' was found for " + uri);
        } else {
            debugPrintln("unable to find a factory for " + uri);
        }
        return f;
    }

    /**
     * <p>Lookup a {@link XPathFactory} for the given object model.</p>
     *
     * @param uri identifies the object model.
     *
     * @return {@link XPathFactory} for the given object model.
     */
    private XPathFactory _newFactory(String uri) {
        XPathFactory xpathFactory;

        String propertyName = SERVICE_CLASS.getName() + ":" + uri;

        // system property look up
        try {
            debugPrintln("Looking up system property '"+propertyName+"'" );
            String r = ss.getSystemProperty(propertyName);
            if(r!=null) {
                debugPrintln("The value is '"+r+"'");
                xpathFactory = createInstance(r);
                if(xpathFactory != null)    return xpathFactory;
            } else
                debugPrintln("The property is undefined.");
        } catch( Throwable t ) {
            if( debug ) {
                debugPrintln("failed to look up system property '"+propertyName+"'" );
                t.printStackTrace();
            }
        }

        String javah = ss.getSystemProperty( "java.home" );
        String configFile = javah + File.separator +
        "lib" + File.separator + "jaxp.properties";

        String factoryClassName = null ;

        // try to read from $java.home/lib/jaxp.properties
        try {
            if(firstTime){
                synchronized(cacheProps){
                    if(firstTime){
                        File f=new File( configFile );
                        firstTime = false;
                        if(ss.doesFileExist(f)){
                            debugPrintln("Read properties file " + f);
                            cacheProps.load(ss.getFileInputStream(f));
                        }
                    }
                }
            }
            factoryClassName = cacheProps.getProperty(propertyName);
            debugPrintln("found " + factoryClassName + " in $java.home/jaxp.properties");

            if (factoryClassName != null) {
                xpathFactory = createInstance(factoryClassName);
                if(xpathFactory != null){
                    return xpathFactory;
                }
            }
        } catch (Exception ex) {
            if (debug) {
                ex.printStackTrace();
            }
        }

        // try META-INF/services files
        Iterator sitr = createServiceFileIterator();
        while(sitr.hasNext()) {
            URL resource = (URL)sitr.next();
            debugPrintln("looking into " + resource);
            try {
                xpathFactory = loadFromService(uri, resource.toExternalForm(),
                                                ss.getURLInputStream(resource));
                if (xpathFactory != null) {
                    return xpathFactory;
                }
            } catch(IOException e) {
                if( debug ) {
                    debugPrintln("failed to read "+resource);
                    e.printStackTrace();
                }
            }
        }

        // platform default
        if(uri.equals(XPathFactory.DEFAULT_OBJECT_MODEL_URI)) {
            debugPrintln("attempting to use the platform default W3C DOM XPath lib");
            return createInstance("com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl");
        }

        debugPrintln("all things were tried, but none was found. bailing out.");
        return null;
    }

    /** <p>Create class using appropriate ClassLoader.</p>
     *
     * @param className Name of class to create.
     * @return Created class or <code>null</code>.
     */
    private Class createClass(String className) {
            Class clazz;

            // use approprite ClassLoader
            try {
                    if (classLoader != null) {
                            clazz = classLoader.loadClass(className);
                    } else {
                            clazz = Class.forName(className);
                    }
            } catch (Throwable t) {
                if(debug)   t.printStackTrace();
                    return null;
            }

            return clazz;
    }

    /**
     * <p>Creates an instance of the specified and returns it.</p>
     *
     * @param className
     *      fully qualified class name to be instanciated.
     *
     * @return null
     *      if it fails. Error messages will be printed by this method.
     */
    XPathFactory createInstance( String className ) {
        XPathFactory xPathFactory = null;

        debugPrintln("createInstance(" + className + ")");

        // get Class from className
        Class clazz = createClass(className);
        if (clazz == null) {
                debugPrintln("failed to getClass(" + className + ")");
                return null;
        }
        debugPrintln("loaded " + className + " from " + which(clazz));

        // instantiate Class as a XPathFactory
        try {
                xPathFactory = (XPathFactory) clazz.newInstance();
        } catch (ClassCastException classCastException) {
                debugPrintln("could not instantiate " + clazz.getName());
                if (debug) {
                        classCastException.printStackTrace();
                }
                return null;
        } catch (IllegalAccessException illegalAccessException) {
                debugPrintln("could not instantiate " + clazz.getName());
                if (debug) {
                        illegalAccessException.printStackTrace();
                }
                return null;
        } catch (InstantiationException instantiationException) {
                debugPrintln("could not instantiate " + clazz.getName());
                if (debug) {
                        instantiationException.printStackTrace();
                }
                return null;
        }

        return xPathFactory;
    }

    /**
     * <p>Look up a value in a property file.</p>
     *
     * <p>Set <code>debug</code> to <code>true</code> to trace property evaluation.</p>
     *
     * @param objectModel URI of object model to support.
     * @param inputName Name of <code>InputStream</code>.
     * @param in <code>InputStream</code> of properties.
     *
     * @return <code>XPathFactory</code> as determined by <code>keyName</code> value or <code>null</code> if there was an error.
     *
     * @throws IOException If IO error reading from <code>in</code>.
     */
    private XPathFactory loadFromService(
            String objectModel,
            String inputName,
            InputStream in)
            throws IOException {

            XPathFactory xPathFactory = null;
            final Class[] stringClassArray = {"".getClass()};
            final Object[] objectModelObjectArray = {objectModel};
            final String isObjectModelSupportedMethod = "isObjectModelSupported";

            debugPrintln("Reading " + inputName);

            // read from InputStream until a match is found
            BufferedReader configFile = new BufferedReader(new InputStreamReader(in));
            String line = null;
            while ((line = configFile.readLine()) != null) {
                    // '#' is comment char
                    int comment = line.indexOf("#");
                    switch (comment) {
                            case -1: break; // no comment
                            case 0: line = ""; break; // entire line is a comment
                            default: line = line.substring(0, comment); break; // trim comment
                    }

                    // trim whitespace
                    line = line.trim();

                    // any content left on line?
                    if (line.length() == 0) {
                            continue;
                    }

                    // line content is now the name of the class
                    Class clazz = createClass(line);
                    if (clazz == null) {
                            continue;
                    }

                    // create an instance of the Class
                    try {
                            xPathFactory = (XPathFactory) clazz.newInstance();
                    } catch (ClassCastException classCastExcpetion) {
                            xPathFactory = null;
                            continue;
                    } catch (InstantiationException instantiationException) {
                            xPathFactory = null;
                            continue;
                    } catch (IllegalAccessException illegalAccessException) {
                            xPathFactory = null;
                            continue;
                    }

                    // does this Class support desired object model?
                    try {
                            Method isObjectModelSupported = clazz.getMethod(isObjectModelSupportedMethod, stringClassArray);
                            Boolean supported = (Boolean) isObjectModelSupported.invoke(xPathFactory, objectModelObjectArray);
                            if (supported.booleanValue()) {
                                    break;
                            }

                    } catch (NoSuchMethodException noSuchMethodException) {

                    } catch (IllegalAccessException illegalAccessException) {

                    } catch (InvocationTargetException invocationTargetException) {

                    }
                    xPathFactory = null;
            }

            // clean up
            configFile.close();

            // return new instance of XPathFactory or null
            return xPathFactory;
    }

    /** Iterator that lazily computes one value and returns it. */
    private static abstract class SingleIterator implements Iterator {
        private boolean seen = false;

        public final void remove() { throw new UnsupportedOperationException(); }
        public final boolean hasNext() { return !seen; }
        public final Object next() {
            if(seen)    throw new NoSuchElementException();
            seen = true;
            return value();
        }

        protected abstract Object value();
    }

    /**
     * Looks up a value in a property file
     * while producing all sorts of debug messages.
     *
     * @return null
     *      if there was an error.
     */
    private XPathFactory loadFromProperty( String keyName, String resourceName, InputStream in )
        throws IOException {
        debugPrintln("Reading "+resourceName );

        Properties props = new Properties();
        props.load(in);
        in.close();
        String factoryClassName = props.getProperty(keyName);
        if(factoryClassName != null){
            debugPrintln("found "+keyName+" = " + factoryClassName);
            return createInstance(factoryClassName);
        } else {
            debugPrintln(keyName+" is not in the property file");
            return null;
        }
    }

    /**
     * Returns an {@link Iterator} that enumerates all
     * the META-INF/services files that we care.
     */
    private Iterator createServiceFileIterator() {
        if (classLoader == null) {
            return new SingleIterator() {
                protected Object value() {
                    ClassLoader classLoader = XPathFactoryFinder.class.getClassLoader();
                    return ss.getResourceAsURL(classLoader, SERVICE_ID);
                    //return (ClassLoader.getSystemResource( SERVICE_ID ));
                }
            };
        } else {
            try {
                //final Enumeration e = classLoader.getResources(SERVICE_ID);
                final Enumeration e = ss.getResources(classLoader, SERVICE_ID);
                if(!e.hasMoreElements()) {
                    debugPrintln("no "+SERVICE_ID+" file was found");
                }

                // wrap it into an Iterator.
                return new Iterator() {
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    public boolean hasNext() {
                        return e.hasMoreElements();
                    }

                    public Object next() {
                        return e.nextElement();
                    }
                };
            } catch (IOException e) {
                debugPrintln("failed to enumerate resources "+SERVICE_ID);
                if(debug)   e.printStackTrace();
                return new ArrayList().iterator();  // empty iterator
            }
        }
    }

    private static final Class SERVICE_CLASS = XPathFactory.class;
    private static final String SERVICE_ID = "META-INF/services/" + SERVICE_CLASS.getName();



    private static String which( Class clazz ) {
        return which( clazz.getName(), clazz.getClassLoader() );
    }

    /**
     * <p>Search the specified classloader for the given classname.</p>
     *
     * @param classname the fully qualified name of the class to search for
     * @param loader the classloader to search
     *
     * @return the source location of the resource, or null if it wasn't found
     */
    private static String which(String classname, ClassLoader loader) {

        String classnameAsResource = classname.replace('.', '/') + ".class";

        if( loader==null )  loader = ClassLoader.getSystemClassLoader();

        //URL it = loader.getResource(classnameAsResource);
        URL it = ss.getResourceAsURL(loader, classnameAsResource);
        if (it != null) {
            return it.toString();
        } else {
            return null;
        }
    }
}
