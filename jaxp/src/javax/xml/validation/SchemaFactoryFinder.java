/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.validation;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Implementation of {@link SchemaFactory#newInstance(String)}.
 *
 * @author <a href="Kohsuke.Kawaguchi@Sun.com">Kohsuke Kawaguchi</a>
 * @version $Revision: 1.8 $, $Date: 2010-11-01 04:36:13 $
 * @since 1.5
 */
class SchemaFactoryFinder  {

    /** debug support code. */
    private static boolean debug = false;
    /**
     *<p> Take care of restrictions imposed by java security model </p>
     */
    private static final SecuritySupport ss = new SecuritySupport();
    private static final String DEFAULT_PACKAGE = "com.sun.org.apache.xerces.internal";
    /**
     * <p>Cache properties for performance.</p>
     */
    private static final Properties cacheProps = new Properties();

    /**
     * <p>First time requires initialization overhead.</p>
     */
    private static volatile boolean firstTime = true;

    static {
        // Use try/catch block to support applets
        try {
            debug = ss.getSystemProperty("jaxp.debug") != null;
        } catch (Exception unused) {
            debug = false;
        }
    }

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
     * <p><code>ClassLoader</code> to use to find <code>SchemaFactory</code>.</p>
     */
    private final ClassLoader classLoader;

    /**
     * <p>Constructor that specifies <code>ClassLoader</code> to use
     * to find <code>SchemaFactory</code>.</p>
     *
     * @param loader
     *      to be used to load resource, {@link SchemaFactory}, and
     *      {@link SchemaFactoryLoader} implementations during
     *      the resolution process.
     *      If this parameter is null, the default system class loader
     *      will be used.
     */
    public SchemaFactoryFinder(ClassLoader loader) {
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
        } catch( Throwable unused ) {
            // getContextClassLoader() undefined in JDK1.1
        }

        if( classLoader==ClassLoader.getSystemClassLoader() ) {
            debugPrintln("using system class loader ("+classLoader+") for search");
            return;
        }

        debugPrintln("using class loader ("+classLoader+") for search");
    }

    /**
     * <p>Creates a new {@link SchemaFactory} object for the specified
     * schema language.</p>
     *
     * @param schemaLanguage
     *      See {@link SchemaFactory Schema Language} table in <code>SchemaFactory</code>
     *      for the list of available schema languages.
     *
     * @return <code>null</code> if the callee fails to create one.
     *
     * @throws NullPointerException
     *      If the <code>schemaLanguage</code> parameter is null.
     * @throws SchemaFactoryConfigurationError
     *      If a configuration error is encountered.
     */
    public SchemaFactory newFactory(String schemaLanguage) {
        if(schemaLanguage==null) {
            throw new NullPointerException();
        }
        SchemaFactory f = _newFactory(schemaLanguage);
        if (f != null) {
            debugPrintln("factory '" + f.getClass().getName() + "' was found for " + schemaLanguage);
        } else {
            debugPrintln("unable to find a factory for " + schemaLanguage);
        }
        return f;
    }

    /**
     * <p>Lookup a <code>SchemaFactory</code> for the given <code>schemaLanguage</code>.</p>
     *
     * @param schemaLanguage Schema language to lookup <code>SchemaFactory</code> for.
     *
     * @return <code>SchemaFactory</code> for the given <code>schemaLanguage</code>.
     */
    private SchemaFactory _newFactory(String schemaLanguage) {
        SchemaFactory sf;

        String propertyName = SERVICE_CLASS.getName() + ":" + schemaLanguage;

        // system property look up
        try {
            debugPrintln("Looking up system property '"+propertyName+"'" );
            String r = ss.getSystemProperty(propertyName);
            if(r!=null) {
                debugPrintln("The value is '"+r+"'");
                sf = createInstance(r, true);
                if(sf!=null)    return sf;
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
            final String factoryClassName = cacheProps.getProperty(propertyName);
            debugPrintln("found " + factoryClassName + " in $java.home/jaxp.properties");

            if (factoryClassName != null) {
                sf = createInstance(factoryClassName, true);
                if(sf != null){
                    return sf;
                }
            }
        } catch (Exception ex) {
            if (debug) {
                ex.printStackTrace();
            }
        }

        // Try with ServiceLoader
        final SchemaFactory factoryImpl = findServiceProvider(schemaLanguage);

        // The following assertion should always be true.
        // Uncomment it, recompile, and run with -ea in case of doubts:
        // assert factoryImpl == null || factoryImpl.isSchemaLanguageSupported(schemaLanguage);

        if (factoryImpl != null) {
            return factoryImpl;
        }

        // platform default
        if(schemaLanguage.equals("http://www.w3.org/2001/XMLSchema")) {
            debugPrintln("attempting to use the platform default XML Schema validator");
            return createInstance("com.sun.org.apache.xerces.internal.jaxp.validation.XMLSchemaFactory", true);
        }

        debugPrintln("all things were tried, but none was found. bailing out.");
        return null;
    }

    /** <p>Create class using appropriate ClassLoader.</p>
     *
     * @param className Name of class to create.
     * @return Created class or <code>null</code>.
     */
    private Class<?> createClass(String className) {
        Class<?> clazz;
        // make sure we have access to restricted packages
        boolean internal = false;
        if (System.getSecurityManager() != null) {
            if (className != null && className.startsWith(DEFAULT_PACKAGE)) {
                internal = true;
            }
        }

        try {
            if (classLoader != null && !internal) {
                clazz = Class.forName(className, false, classLoader);
            } else {
                clazz = Class.forName(className);
            }
        } catch (Throwable t) {
            if(debug)  {
                t.printStackTrace();
            }
            return null;
        }

        return clazz;
    }

    /**
     * <p>Creates an instance of the specified and returns it.</p>
     *
     * @param className
     *      fully qualified class name to be instantiated.
     *
     * @return null
     *      if it fails. Error messages will be printed by this method.
     */
    SchemaFactory createInstance( String className ) {
        return createInstance( className, false );
    }

    SchemaFactory createInstance( String className, boolean useServicesMechanism ) {
        SchemaFactory schemaFactory = null;

        debugPrintln("createInstance(" + className + ")");

        // get Class from className
        Class<?> clazz = createClass(className);
        if (clazz == null) {
                debugPrintln("failed to getClass(" + className + ")");
                return null;
        }
        debugPrintln("loaded " + className + " from " + which(clazz));

        // instantiate Class as a SchemaFactory
        try {
                if (!SchemaFactory.class.isAssignableFrom(clazz)) {
                    throw new ClassCastException(clazz.getName()
                                + " cannot be cast to " + SchemaFactory.class);
                }
                if (!useServicesMechanism) {
                    schemaFactory = newInstanceNoServiceLoader(clazz);
                }
                if (schemaFactory == null) {
                    schemaFactory = (SchemaFactory) clazz.newInstance();
                }
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

        return schemaFactory;
    }

    /**
     * Try to construct using newXMLSchemaFactoryNoServiceLoader
     *   method if available.
     */
    private static SchemaFactory newInstanceNoServiceLoader(
         Class<?> providerClass
    ) {
        // Retain maximum compatibility if no security manager.
        if (System.getSecurityManager() == null) {
            return null;
        }
        try {
            final Method creationMethod =
                providerClass.getDeclaredMethod(
                    "newXMLSchemaFactoryNoServiceLoader"
                );
            final int modifiers = creationMethod.getModifiers();

            // Do not call the method if it's not public static.
            if (!Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers)) {
                return null;
            }

            // Only calls "newXMLSchemaFactoryNoServiceLoader" if it's
            // declared to return an instance of SchemaFactory.
            final Class<?> returnType = creationMethod.getReturnType();
            if (SERVICE_CLASS.isAssignableFrom(returnType)) {
                return SERVICE_CLASS.cast(creationMethod.invoke(null, (Object[])null));
            } else {
                // Should not happen since
                // XMLSchemaFactory.newXMLSchemaFactoryNoServiceLoader is
                // declared to return XMLSchemaFactory.
                throw new ClassCastException(returnType
                            + " cannot be cast to " + SERVICE_CLASS);
            }
        } catch(ClassCastException e) {
            throw new SchemaFactoryConfigurationError(e.getMessage(), e);
        } catch (NoSuchMethodException exc) {
            return null;
        } catch (Exception exc) {
            return null;
        }
    }

    // Call isSchemaLanguageSupported with initial context.
    private boolean isSchemaLanguageSupportedBy(final SchemaFactory factory,
            final String schemaLanguage,
            AccessControlContext acc) {
        return AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
            public Boolean run() {
                return factory.isSchemaLanguageSupported(schemaLanguage);
            }
        }, acc);
    }

    /**
     * Finds a service provider subclass of SchemaFactory that supports the
     * given schema language using the ServiceLoader.
     *
     * @param schemaLanguage The schema language for which we seek a factory.
     * @return A SchemaFactory supporting the specified schema language, or null
     *         if none is found.
     * @throws SchemaFactoryConfigurationError if a configuration error is found.
     */
    private SchemaFactory findServiceProvider(final String schemaLanguage) {
        assert schemaLanguage != null;
        // store current context.
        final AccessControlContext acc = AccessController.getContext();
        try {
            return AccessController.doPrivileged(new PrivilegedAction<SchemaFactory>() {
                public SchemaFactory run() {
                    final ServiceLoader<SchemaFactory> loader =
                            ServiceLoader.load(SERVICE_CLASS);
                    for (SchemaFactory factory : loader) {
                        // restore initial context to call
                        // factory.isSchemaLanguageSupported
                        if (isSchemaLanguageSupportedBy(factory, schemaLanguage, acc)) {
                            return factory;
                        }
                    }
                    return null; // no factory found.
                }
            });
        } catch (ServiceConfigurationError error) {
            throw new SchemaFactoryConfigurationError(
                    "Provider for " + SERVICE_CLASS + " cannot be created", error);
        }
    }

    private static final Class<SchemaFactory> SERVICE_CLASS = SchemaFactory.class;


    private static String which( Class<?> clazz ) {
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
