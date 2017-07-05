
package com.sun.tracing;

import java.util.HashSet;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.logging.Logger;

import sun.tracing.NullProviderFactory;
import sun.tracing.PrintStreamProviderFactory;
import sun.tracing.MultiplexProviderFactory;
import sun.tracing.dtrace.DTraceProviderFactory;

/**
 * {@code ProviderFactory} is a factory class used to create instances of
 * providers.
 *
 * To enable tracing in an application, this class must be used to create
 * instances of the provider interfaces defined by users.
 * The system-defined factory is obtained by using the
 * {@code getDefaultFactory()} static method.  The resulting instance can be
 * used to create any number of providers.
 *
 * @since 1.7
 */
public abstract class ProviderFactory {

    protected ProviderFactory() {}

    /**
     * Creates an implementation of a Provider interface.
     *
     * @param cls the provider interface to be defined.
     * @return an implementation of {@code cls}, whose methods, when called,
     * will trigger tracepoints in the application.
     * @throws NullPointerException if cls is null
     * @throws IllegalArgumentException if the class definition contains
     * non-void methods
     */
    public abstract <T extends Provider> T createProvider(Class<T> cls);

    /**
     * Returns an implementation of a {@code ProviderFactory} which
     * creates instances of Providers.
     *
     * The created Provider instances will be linked to all appropriate
     * and enabled system-defined tracing mechanisms in the JDK.
     *
     * @return a {@code ProviderFactory} that is used to create Providers.
     */
    public static ProviderFactory getDefaultFactory() {
        HashSet<ProviderFactory> factories = new HashSet<ProviderFactory>();

        // Try to instantiate a DTraceProviderFactory
        String prop = null;
        try { prop = System.getProperty("com.sun.tracing.dtrace"); }
        catch (java.security.AccessControlException e) {
            Logger.getAnonymousLogger().fine(
                "Cannot access property com.sun.tracing.dtrace");
        }
        if ( (prop == null || !prop.equals("disable")) &&
             DTraceProviderFactory.isSupported() ) {
            factories.add(new DTraceProviderFactory());
        }

        // Try to instantiate an output stream factory
        try { prop = System.getProperty("sun.tracing.stream"); }
        catch (java.security.AccessControlException e) {
            Logger.getAnonymousLogger().fine(
                "Cannot access property sun.tracing.stream");
        }
        if (prop != null) {
            for (String spec : prop.split(",")) {
                PrintStream ps = getPrintStreamFromSpec(spec);
                if (ps != null) {
                    factories.add(new PrintStreamProviderFactory(ps));
                }
            }
        }

        // See how many factories we instantiated, and return an appropriate
        // factory that encapsulates that.
        if (factories.size() == 0) {
            return new NullProviderFactory();
        } else if (factories.size() == 1) {
            return factories.toArray(new ProviderFactory[1])[0];
        } else {
            return new MultiplexProviderFactory(factories);
        }
    }

    private static PrintStream getPrintStreamFromSpec(String spec) {
        try {
            // spec is in the form of <class>.<field>, where <class> is
            // a fully specified class name, and <field> is a static member
            // in that class.  The <field> must be a 'PrintStream' or subtype
            // in order to be used.
            int fieldpos = spec.lastIndexOf('.');
            Class<?> cls = Class.forName(spec.substring(0, fieldpos));
            Field f = cls.getField(spec.substring(fieldpos + 1));
            Class<?> fieldType = f.getType();
            return (PrintStream)f.get(null);
        } catch (Exception e) {
            Logger.getAnonymousLogger().warning(
                "Could not parse sun.tracing.stream property: " + e);
        }
        return null;
    }
}

