/*
 * Copyright (c) 2002, 2004, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

package sun.jvm.hotspot.jdi;

import com.sun.jdi.connect.*;
import com.sun.jdi.InternalException;

import java.io.*;
import java.lang.ref.*;
import java.lang.reflect.*;
import java.util.*;

abstract class ConnectorImpl implements Connector {
    Map defaultArguments = new LinkedHashMap();

    // Used by BooleanArgument
    static String trueString = null;
    static String falseString;


    /**  This is not public in VirtualMachineManagerImpl
    ThreadGroup mainGroupForJDI() {
        return ((VirtualMachineManagerImpl)manager).mainGroupForJDI();
    }
    ***/

    // multiple debuggee support for SA/JDI
    private static List freeVMClasses; // List<SoftReference<Class>>
    private static ClassLoader myLoader;
    // debug mode for SA/JDI connectors
    static final protected boolean DEBUG;
    static {
        myLoader = ConnectorImpl.class.getClassLoader();
        freeVMClasses = new ArrayList(0);
        DEBUG = System.getProperty("sun.jvm.hotspot.jdi.ConnectorImpl.DEBUG") != null;
    }

    // add a new free VirtualMachineImpl class
    private static synchronized void addFreeVMImplClass(Class clazz) {
        if (DEBUG) {
            System.out.println("adding free VirtualMachineImpl class");
        }
        freeVMClasses.add(new SoftReference(clazz));
    }

    // returns null if we don't have anything free
    private static synchronized Class getFreeVMImplClass() {
        while (!freeVMClasses.isEmpty()) {
              SoftReference ref = (SoftReference) freeVMClasses.remove(0);
              Object o = ref.get();
              if (o != null) {
                  if (DEBUG) {
                      System.out.println("re-using loaded VirtualMachineImpl");
                  }
                  return (Class) o;
              }
        }
        return null;
    }

    private static Class getVMImplClassFrom(ClassLoader cl)
                               throws ClassNotFoundException {
        return Class.forName("sun.jvm.hotspot.jdi.VirtualMachineImpl", true, cl);
    }

    /* SA has not been designed to support multiple debuggee VMs
     * at-a-time.  But, JDI supports multiple debuggee VMs.  We
     * support multiple debuggee VMs in SA/JDI, by creating a new
     * class loader instance (refer to comment in SAJDIClassLoader
     * for details). But, to avoid excessive class loading (and
     * thereby resulting in larger footprint), we re-use 'dispose'd
     * VirtualMachineImpl classes.
     */
    protected static Class loadVirtualMachineImplClass()
                               throws ClassNotFoundException {
        Class vmImplClass = getFreeVMImplClass();
        if (vmImplClass == null) {
            ClassLoader cl = new SAJDIClassLoader(myLoader);
            vmImplClass = getVMImplClassFrom(cl);
        }
        return vmImplClass;
    }

    /* We look for System property sun.jvm.hotspot.jdi.<vm version>.
     * This property should have the value of JDK HOME directory for
     * the given <vm version>.
     */
    private static String getSAClassPathForVM(String vmVersion) {
        final String prefix = "sun.jvm.hotspot.jdi.";
        // look for exact match of VM version
        String jvmHome = System.getProperty(prefix + vmVersion);
        if (DEBUG) {
            System.out.println("looking for System property " + prefix + vmVersion);
        }

        if (jvmHome == null) {
            // omit chars after first '-' in VM version and try
            // for example, in '1.5.0-b55' we take '1.5.0'
            int index = vmVersion.indexOf('-');
            if (index != -1) {
                vmVersion = vmVersion.substring(0, index);
                if (DEBUG) {
                    System.out.println("looking for System property " + prefix + vmVersion);
                }
                jvmHome = System.getProperty(prefix + vmVersion);
            }

            if (jvmHome == null) {
                // System property is not set
                if (DEBUG) {
                    System.out.println("can't locate JDK home for " + vmVersion);
                }
                return null;
            }
        }

        if (DEBUG) {
            System.out.println("JDK home for " + vmVersion + " is " + jvmHome);
        }

        // sa-jdi is in $JDK_HOME/lib directory
        StringBuffer buf = new StringBuffer();
        buf.append(jvmHome);
        buf.append(File.separatorChar);
        buf.append("lib");
        buf.append(File.separatorChar);
        buf.append("sa-jdi.jar");
        return buf.toString();
    }

    /* This method loads VirtualMachineImpl class by a ClassLoader
     * configured with sa-jdi.jar path of given 'vmVersion'. This is
     * used for cross VM version debugging. Refer to comments in
     * SAJDIClassLoader as well.
     */
    protected static Class loadVirtualMachineImplClass(String vmVersion)
            throws ClassNotFoundException {
        if (DEBUG) {
            System.out.println("attemping to load sa-jdi.jar for version " + vmVersion);
        }
        String classPath = getSAClassPathForVM(vmVersion);
        if (classPath != null) {
            ClassLoader cl = new SAJDIClassLoader(myLoader, classPath);
            return getVMImplClassFrom(cl);
        } else {
            return null;
        }
    }

    /* Is the given throwable an instanceof VMVersionMismatchException?
     * Note that we can't do instanceof check because the exception
     * class might have been loaded by a different class loader.
     */
    private static boolean isVMVersionMismatch(Throwable throwable) {
        String className = throwable.getClass().getName();
        return className.equals("sun.jvm.hotspot.runtime.VMVersionMismatchException");
    }

    /* gets target VM version from the given VMVersionMismatchException.
     * Note that we need to reflectively call the method because of we may
     * have got this from different classloader's namespace */
    private static String getVMVersion(Throwable throwable)
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        // assert isVMVersionMismatch(throwable), "not a VMVersionMismatch"
        Class expClass = throwable.getClass();
        Method targetVersionMethod = expClass.getMethod("getTargetVersion", new Class[0]);
        return (String) targetVersionMethod.invoke(throwable, null);
    }

    /** If the causal chain has a sun.jvm.hotspot.runtime.VMVersionMismatchException,
        attempt to load VirtualMachineImpl class for target VM version. */
    protected static Class handleVMVersionMismatch(InvocationTargetException ite) {
        Throwable cause = ite.getCause();
        if (DEBUG) {
            System.out.println("checking for version mismatch...");
        }
        while (cause != null) {
            try {
                if (isVMVersionMismatch(cause)) {
                    if (DEBUG) {
                        System.out.println("Triggering cross VM version support...");
                    }
                    return loadVirtualMachineImplClass(getVMVersion(cause));
                }
            } catch (Exception exp) {
                if (DEBUG) {
                    System.out.println("failed to load VirtualMachineImpl class");
                    exp.printStackTrace();
                }
                return null;
            }
            cause = cause.getCause();
        }
        return null;
    }

    protected void checkNativeLink(SecurityManager sm, String os) {
        if (os.equals("SunOS") || os.equals("Linux")) {
            // link "saproc" - SA native library on SunOS and Linux?
            sm.checkLink("saproc");
        } else if (os.startsWith("Windows")) {
            // link "sawindbg" - SA native library on Windows.
            sm.checkLink("sawindbg");
        } else {
           throw new RuntimeException(os + " is not yet supported");
        }
    }

    // we set an observer to detect VirtualMachineImpl.dispose call
    // and on dispose we add corresponding VirtualMachineImpl.class to
    // free VirtualMachimeImpl Class list.
    protected static void setVMDisposeObserver(final Object vm) {
        try {
            Method setDisposeObserverMethod = vm.getClass().getDeclaredMethod("setDisposeObserver",
                                                         new Class[] { java.util.Observer.class });
            setDisposeObserverMethod.setAccessible(true);
            setDisposeObserverMethod.invoke(vm,
                                         new Object[] {
                                             new Observer() {
                                                 public void update(Observable o, Object data) {
                                                     if (DEBUG) {
                                                         System.out.println("got VM.dispose notification");
                                                     }
                                                     addFreeVMImplClass(vm.getClass());
                                                 }
                                             }
                                         });
        } catch (Exception exp) {
            if (DEBUG) {
               System.out.println("setVMDisposeObserver() got an exception:");
               exp.printStackTrace();
            }
        }
    }

    public Map defaultArguments() {
        Map defaults = new LinkedHashMap();
        Collection values = defaultArguments.values();

        Iterator iter = values.iterator();
        while (iter.hasNext()) {
            ArgumentImpl argument = (ArgumentImpl)iter.next();
            defaults.put(argument.name(), argument.clone());
        }
        return defaults;
    }

    void addStringArgument(String name, String label, String description,
                           String defaultValue, boolean mustSpecify) {
        defaultArguments.put(name,
                             new StringArgumentImpl(name, label,
                                                    description,
                                                    defaultValue,
                                                    mustSpecify));
    }

    void addBooleanArgument(String name, String label, String description,
                            boolean defaultValue, boolean mustSpecify) {
        defaultArguments.put(name,
                             new BooleanArgumentImpl(name, label,
                                                     description,
                                                     defaultValue,
                                                     mustSpecify));
    }

    void addIntegerArgument(String name, String label, String description,
                            String defaultValue, boolean mustSpecify,
                            int min, int max) {
        defaultArguments.put(name,
                             new IntegerArgumentImpl(name, label,
                                                     description,
                                                     defaultValue,
                                                     mustSpecify,
                                                     min, max));
    }

    void addSelectedArgument(String name, String label, String description,
                             String defaultValue, boolean mustSpecify,
                             List list) {
        defaultArguments.put(name,
                             new SelectedArgumentImpl(name, label,
                                                      description,
                                                      defaultValue,
                                                      mustSpecify, list));
    }

    ArgumentImpl argument(String name, Map arguments)
                throws IllegalConnectorArgumentsException {

        ArgumentImpl argument = (ArgumentImpl)arguments.get(name);
        if (argument == null) {
            throw new IllegalConnectorArgumentsException(
                         "Argument missing", name);
        }
        String value = argument.value();
        if (value == null || value.length() == 0) {
            if (argument.mustSpecify()) {
            throw new IllegalConnectorArgumentsException(
                         "Argument unspecified", name);
            }
        } else if(!argument.isValid(value)) {
            throw new IllegalConnectorArgumentsException(
                         "Argument invalid", name);
        }

        return argument;
    }

    String getString(String key) {
        //fixme jjh; needs i18n
        // this is not public return ((VirtualMachineManagerImpl)manager).getString(key);
        return key;
    }

    public String toString() {
        String string = name() + " (defaults: ";
        Iterator iter = defaultArguments().values().iterator();
        boolean first = true;
        while (iter.hasNext()) {
            ArgumentImpl argument = (ArgumentImpl)iter.next();
            if (!first) {
                string += ", ";
            }
            string += argument.toString();
            first = false;
        }
        return string  + ")";
    }

    abstract class ArgumentImpl implements Connector.Argument, Cloneable, Serializable {
        private String name;
        private String label;
        private String description;
        private String value;
        private boolean mustSpecify;

        ArgumentImpl(String name, String label, String description,
                     String value,
                     boolean mustSpecify) {
            this.name = name;
            this.label = label;
            this.description = description;
            this.value = value;
            this.mustSpecify = mustSpecify;
        }

        public abstract boolean isValid(String value);

        public String name() {
            return name;
        }

        public String label() {
            return label;
        }

        public String description() {
            return description;
        }

        public String value() {
            return value;
        }

        public void setValue(String value) {
            if (value == null) {
                throw new NullPointerException("Can't set null value");
            }
            this.value = value;
        }

        public boolean mustSpecify() {
            return mustSpecify;
        }

        public boolean equals(Object obj) {
            if ((obj != null) && (obj instanceof Connector.Argument)) {
                Connector.Argument other = (Connector.Argument)obj;
                return (name().equals(other.name())) &&
                       (description().equals(other.description())) &&
                       (mustSpecify() == other.mustSpecify()) &&
                       (value().equals(other.value()));
            } else {
                return false;
            }
        }

        public int hashCode() {
            return description().hashCode();
        }

        public Object clone() {
            try {
                return super.clone();
            } catch (CloneNotSupportedException e) {
                // Object should always support clone
                throw (InternalException) new InternalException().initCause(e);
            }
        }

        public String toString() {
            return name() + "=" + value();
        }
    }

    class BooleanArgumentImpl extends ConnectorImpl.ArgumentImpl
                              implements Connector.BooleanArgument {

        BooleanArgumentImpl(String name, String label, String description,
                            boolean value,
                            boolean mustSpecify) {
            super(name, label, description, null, mustSpecify);
            if(trueString == null) {
                trueString = getString("true");
                falseString = getString("false");
            }
            setValue(value);
        }

        /**
         * Sets the value of the argument.
         */
        public void setValue(boolean value) {
            setValue(stringValueOf(value));
        }

        /**
         * Performs basic sanity check of argument.
         * @return <code>true</code> if value is a string
         * representation of a boolean value.
         * @see #stringValueOf(boolean)
         */
        public boolean isValid(String value) {
            return value.equals(trueString) || value.equals(falseString);
        }

        /**
         * Return the string representation of the <code>value</code>
         * parameter.
         * Does not set or examine the value or the argument.
         * @return the localized String representation of the
         * boolean value.
         */
        public String stringValueOf(boolean value) {
            return value? trueString : falseString;
        }

        /**
         * Return the value of the argument as a boolean.  Since
         * the argument may not have been set or may have an invalid
         * value {@link #isValid(String)} should be called on
         * {@link #value()} to check its validity.  If it is invalid
         * the boolean returned by this method is undefined.
         * @return the value of the argument as a boolean.
         */
        public boolean booleanValue() {
            return value().equals(trueString);
        }
    }

    class IntegerArgumentImpl extends ConnectorImpl.ArgumentImpl
                              implements Connector.IntegerArgument {

        private final int min;
        private final int max;

        IntegerArgumentImpl(String name, String label, String description,
                            String value,
                            boolean mustSpecify, int min, int max) {
            super(name, label, description, value, mustSpecify);
            this.min = min;
            this.max = max;
        }

        /**
         * Sets the value of the argument.
         * The value should be checked with {@link #isValid(int)}
         * before setting it; invalid values will throw an exception
         * when the connection is established - for example,
         * on {@link LaunchingConnector#launch}
         */
        public void setValue(int value) {
            setValue(stringValueOf(value));
        }

        /**
         * Performs basic sanity check of argument.
         * @return <code>true</code> if value represents an int that is
         * <code>{@link #min()} &lt;= value &lt;= {@link #max()}</code>
         */
        public boolean isValid(String value) {
            if (value == null) {
                return false;
            }
            try {
                return isValid(Integer.decode(value).intValue());
            } catch(NumberFormatException exc) {
                return false;
            }
        }

        /**
         * Performs basic sanity check of argument.
         * @return <code>true</code> if
         * <code>{@link #min()} &lt;= value  &lt;= {@link #max()}</code>
         */
        public boolean isValid(int value) {
            return min <= value && value <= max;
        }

        /**
         * Return the string representation of the <code>value</code>
         * parameter.
         * Does not set or examine the value or the argument.
         * @return the String representation of the
         * int value.
         */
        public String stringValueOf(int value) {
            // *** Should this be internationalized????
            // *** Even Brian Beck was unsure if an Arabic programmer
            // *** would expect port numbers in Arabic numerals,
            // *** so punt for now.
            return ""+value;
        }

        /**
         * Return the value of the argument as a int.  Since
         * the argument may not have been set or may have an invalid
         * value {@link #isValid(String)} should be called on
         * {@link #value()} to check its validity.  If it is invalid
         * the int returned by this method is undefined.
         * @return the value of the argument as a int.
         */
        public int intValue() {
            if (value() == null) {
                return 0;
            }
            try {
                return Integer.decode(value()).intValue();
            } catch(NumberFormatException exc) {
                return 0;
            }
        }

        /**
         * The upper bound for the value.
         * @return the maximum allowed value for this argument.
         */
        public int max() {
            return max;
        }

        /**
         * The lower bound for the value.
         * @return the minimum allowed value for this argument.
         */
        public int min() {
            return min;
        }
    }

    class StringArgumentImpl extends ConnectorImpl.ArgumentImpl
                              implements Connector.StringArgument {

        StringArgumentImpl(String name, String label, String description,
                           String value,
                           boolean mustSpecify) {
            super(name, label, description, value, mustSpecify);
        }

        /**
         * Performs basic sanity check of argument.
         * @return <code>true</code> always
         */
        public boolean isValid(String value) {
            return true;
        }
    }

    class SelectedArgumentImpl extends ConnectorImpl.ArgumentImpl
                              implements Connector.SelectedArgument {

        private final List choices;

        SelectedArgumentImpl(String name, String label, String description,
                             String value,
                             boolean mustSpecify, List choices) {
            super(name, label, description, value, mustSpecify);
            this.choices = Collections.unmodifiableList(
                                           new ArrayList(choices));
        }

        /**
         * Return the possible values for the argument
         * @return {@link List} of {@link String}
         */
        public List choices() {
            return choices;
        }

        /**
         * Performs basic sanity check of argument.
         * @return <code>true</code> if value is one of {@link #choices()}.
         */
        public boolean isValid(String value) {
            return choices.contains(value);
        }
    }
}
