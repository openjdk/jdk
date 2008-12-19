/*
 * Copyright 1999-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.management;

import com.sun.jmx.mbeanserver.Util;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import java.security.AccessController;
import java.security.PrivilegedAction;

import java.util.HashMap;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import static javax.management.ImmutableDescriptor.nonNullDescriptor;

/**
 * <p>Describes the management interface exposed by an MBean; that is,
 * the set of attributes and operations which are available for
 * management operations.  Instances of this class are immutable.
 * Subclasses may be mutable but this is not recommended.</p>
 *
 * <p id="info-changed">Usually the {@code MBeanInfo} for any given MBean does
 * not change over the lifetime of that MBean.  Dynamic MBeans can change their
 * {@code MBeanInfo} and in that case it is recommended that they emit a {@link
 * Notification} with a {@linkplain Notification#getType() type} of {@code
 * "jmx.mbean.info.changed"} and a {@linkplain Notification#getUserData()
 * userData} that is the new {@code MBeanInfo}.  This is not required, but
 * provides a conventional way for clients of the MBean to discover the change.
 * See also the <a href="Descriptor.html#immutableInfo">immutableInfo</a> and
 * <a href="Descriptor.html#infoTimeout">infoTimeout</a> fields in the {@code
 * MBeanInfo} {@link Descriptor}.</p>
 *
 * <p>The contents of the <code>MBeanInfo</code> for a Dynamic MBean
 * are determined by its {@link DynamicMBean#getMBeanInfo
 * getMBeanInfo()} method.  This includes Open MBeans and Model
 * MBeans, which are kinds of Dynamic MBeans.</p>
 *
 * <p>The contents of the <code>MBeanInfo</code> for a Standard MBean
 * are determined by the MBean server as follows:</p>
 *
 * <ul>
 *
 * <li>{@link #getClassName()} returns the Java class name of the MBean
 * object;
 *
 * <li>{@link #getConstructors()} returns the list of all public
 * constructors in that object;
 *
 * <li>{@link #getAttributes()} returns the list of all attributes
 * whose existence is deduced as follows:
 * <ul>
 * <li>if the Standard MBean is defined with an MBean interface,
 * from <code>get<i>Name</i></code>, <code>is<i>Name</i></code>, or
 * <code>set<i>Name</i></code> methods that conform to the conventions
 * for Standard MBeans;
 * <li>if the Standard MBean is defined with the {@link MBean &#64;MBean} or
 * {@link MXBean &#64;MXBean} annotation on a class, from methods with the
 * {@link ManagedAttribute &#64;ManagedAttribute} annotation;
 * </ul>
 *
 * <li>{@link #getOperations()} returns the list of all operations whose
 * existence is deduced as follows:
 * <ul>
 * <li>if the Standard MBean is defined with an MBean interface, from methods in
 * the MBean interface that do not represent attributes;
 * <li>if the Standard MBean is defined with the {@link MBean &#64;MBean} or
 * {@link MXBean &#64;MXBean} annotation on a class, from methods with the
 * {@link ManagedOperation &#64;ManagedOperation} annotation;
 * </ul>
 *
 * <li>{@link #getNotifications()} returns:
 * <ul>
 * <li>if the MBean implements the {@link NotificationBroadcaster} interface,
 * the result of calling {@link
 * NotificationBroadcaster#getNotificationInfo()} on it;
 * <li>otherwise, if there is a {@link NotificationInfo &#64;NotificationInfo}
 * or {@link NotificationInfos &#64;NotificationInfos} annotation on the
 * MBean interface or <code>&#64;MBean</code> or <code>&#64;MXBean</code>
 * class, the array implied by those annotations;
 * <li>otherwise an empty array;
 * </ul>
 *
 * <li>{@link #getDescriptor()} returns a descriptor containing the contents
 * of any descriptor annotations in the MBean interface (see
 * {@link DescriptorFields &#64;DescriptorFields} and
 * {@link DescriptorKey &#64;DescriptorKey}).
 *
 * </ul>
 *
 * <p>The description returned by {@link #getDescription()} and the
 * descriptions of the contained attributes and operations are determined
 * by the corresponding {@link Description} annotations if any;
 * otherwise their contents are not specified.</p>
 *
 * <p>The remaining details of the <code>MBeanInfo</code> for a
 * Standard MBean are not specified.  This includes the description of
 * any contained constructors, and notifications; the names
 * of parameters to constructors and operations; and the descriptions of
 * constructor parameters.</p>
 *
 * @since 1.5
 */
public class MBeanInfo implements Cloneable, Serializable, DescriptorRead {

    /* Serial version */
    static final long serialVersionUID = -6451021435135161911L;

    /**
     * @serial The Descriptor for the MBean.  This field
     * can be null, which is equivalent to an empty Descriptor.
     */
    private transient Descriptor descriptor;

    /**
     * @serial The human readable description of the class.
     */
    private final String description;

    /**
     * @serial The MBean qualified name.
     */
    private final String className;

    /**
     * @serial The MBean attribute descriptors.
     */
    private final MBeanAttributeInfo[] attributes;

    /**
     * @serial The MBean operation descriptors.
     */
    private final MBeanOperationInfo[] operations;

     /**
     * @serial The MBean constructor descriptors.
     */
    private final MBeanConstructorInfo[] constructors;

    /**
     * @serial The MBean notification descriptors.
     */
    private final MBeanNotificationInfo[] notifications;

    private transient int hashCode;

    /**
     * <p>True if this class is known not to override the array-valued
     * getters of MBeanInfo.  Obviously true for MBeanInfo itself, and true
     * for a subclass where we succeed in reflecting on the methods
     * and discover they are not overridden.</p>
     *
     * <p>The purpose of this variable is to avoid cloning the arrays
     * when doing operations like {@link #equals} where we know they
     * will not be changed.  If a subclass overrides a getter, we
     * cannot access the corresponding array directly.</p>
     */
    private final transient boolean arrayGettersSafe;

    /**
     * Constructs an <CODE>MBeanInfo</CODE>.
     *
     * @param className The name of the Java class of the MBean described
     * by this <CODE>MBeanInfo</CODE>.  This value may be any
     * syntactically legal Java class name.  It does not have to be a
     * Java class known to the MBean server or to the MBean's
     * ClassLoader.  If it is a Java class known to the MBean's
     * ClassLoader, it is recommended but not required that the
     * class's public methods include those that would appear in a
     * Standard MBean implementing the attributes and operations in
     * this MBeanInfo.
     * @param description A human readable description of the MBean (optional).
     * @param attributes The list of exposed attributes of the MBean.
     * This may be null with the same effect as a zero-length array.
     * @param constructors The list of public constructors of the
     * MBean.  This may be null with the same effect as a zero-length
     * array.
     * @param operations The list of operations of the MBean.  This
     * may be null with the same effect as a zero-length array.
     * @param notifications The list of notifications emitted.  This
     * may be null with the same effect as a zero-length array.
     */
    public MBeanInfo(String className,
                     String description,
                     MBeanAttributeInfo[] attributes,
                     MBeanConstructorInfo[] constructors,
                     MBeanOperationInfo[] operations,
                     MBeanNotificationInfo[] notifications)
            throws IllegalArgumentException {
        this(className, description, attributes, constructors, operations,
             notifications, null);
    }

    /**
     * Constructs an <CODE>MBeanInfo</CODE>.
     *
     * @param className The name of the Java class of the MBean described
     * by this <CODE>MBeanInfo</CODE>.  This value may be any
     * syntactically legal Java class name.  It does not have to be a
     * Java class known to the MBean server or to the MBean's
     * ClassLoader.  If it is a Java class known to the MBean's
     * ClassLoader, it is recommended but not required that the
     * class's public methods include those that would appear in a
     * Standard MBean implementing the attributes and operations in
     * this MBeanInfo.
     * @param description A human readable description of the MBean (optional).
     * @param attributes The list of exposed attributes of the MBean.
     * This may be null with the same effect as a zero-length array.
     * @param constructors The list of public constructors of the
     * MBean.  This may be null with the same effect as a zero-length
     * array.
     * @param operations The list of operations of the MBean.  This
     * may be null with the same effect as a zero-length array.
     * @param notifications The list of notifications emitted.  This
     * may be null with the same effect as a zero-length array.
     * @param descriptor The descriptor for the MBean.  This may be null
     * which is equivalent to an empty descriptor.
     *
     * @since 1.6
     */
    public MBeanInfo(String className,
                     String description,
                     MBeanAttributeInfo[] attributes,
                     MBeanConstructorInfo[] constructors,
                     MBeanOperationInfo[] operations,
                     MBeanNotificationInfo[] notifications,
                     Descriptor descriptor)
            throws IllegalArgumentException {

        this.className = className;

        this.description = description;

        if (attributes == null)
            attributes = MBeanAttributeInfo.NO_ATTRIBUTES;
        this.attributes = attributes;

        if (operations == null)
            operations = MBeanOperationInfo.NO_OPERATIONS;
        this.operations = operations;

        if (constructors == null)
            constructors = MBeanConstructorInfo.NO_CONSTRUCTORS;
        this.constructors = constructors;

        if (notifications == null)
            notifications = MBeanNotificationInfo.NO_NOTIFICATIONS;
        this.notifications = notifications;

        if (descriptor == null)
            descriptor = ImmutableDescriptor.EMPTY_DESCRIPTOR;
        this.descriptor = descriptor;

        this.arrayGettersSafe =
                arrayGettersSafe(this.getClass(), MBeanInfo.class);
    }

    /**
     * <p>Returns a shallow clone of this instance.
     * The clone is obtained by simply calling <tt>super.clone()</tt>,
     * thus calling the default native shallow cloning mechanism
     * implemented by <tt>Object.clone()</tt>.
     * No deeper cloning of any internal field is made.</p>
     *
     * <p>Since this class is immutable, the clone method is chiefly of
     * interest to subclasses.</p>
     */
     @Override
     public Object clone () {
         try {
             return super.clone() ;
         } catch (CloneNotSupportedException e) {
             // should not happen as this class is cloneable
             return null;
         }
     }


    /**
     * Returns the name of the Java class of the MBean described by
     * this <CODE>MBeanInfo</CODE>.
     *
     * @return the class name.
     */
    public String getClassName()  {
        return className;
    }

    /**
     * Returns a human readable description of the MBean.
     *
     * @return the description.
     */
    public String getDescription()  {
        return description;
    }

    /**
     * Returns the list of attributes exposed for management.
     * Each attribute is described by an <CODE>MBeanAttributeInfo</CODE> object.
     *
     * The returned array is a shallow copy of the internal array,
     * which means that it is a copy of the internal array of
     * references to the <CODE>MBeanAttributeInfo</CODE> objects
     * but that each referenced <CODE>MBeanAttributeInfo</CODE> object is not copied.
     *
     * @return  An array of <CODE>MBeanAttributeInfo</CODE> objects.
     */
    public MBeanAttributeInfo[] getAttributes()   {
        MBeanAttributeInfo[] as = nonNullAttributes();
        if (as.length == 0)
            return as;
        else
            return as.clone();
    }

    private MBeanAttributeInfo[] fastGetAttributes() {
        if (arrayGettersSafe)
            return nonNullAttributes();
        else
            return getAttributes();
    }

    /**
     * Return the value of the attributes field, or an empty array if
     * the field is null.  This can't happen with a
     * normally-constructed instance of this class, but can if the
     * instance was deserialized from another implementation that
     * allows the field to be null.  It would be simpler if we enforced
     * the class invariant that these fields cannot be null by writing
     * a readObject() method, but that would require us to define the
     * various array fields as non-final, which is annoying because
     * conceptually they are indeed final.
     */
    private MBeanAttributeInfo[] nonNullAttributes() {
        return (attributes == null) ?
            MBeanAttributeInfo.NO_ATTRIBUTES : attributes;
    }

    /**
     * Returns the list of operations  of the MBean.
     * Each operation is described by an <CODE>MBeanOperationInfo</CODE> object.
     *
     * The returned array is a shallow copy of the internal array,
     * which means that it is a copy of the internal array of
     * references to the <CODE>MBeanOperationInfo</CODE> objects
     * but that each referenced <CODE>MBeanOperationInfo</CODE> object is not copied.
     *
     * @return  An array of <CODE>MBeanOperationInfo</CODE> objects.
     */
    public MBeanOperationInfo[] getOperations()  {
        MBeanOperationInfo[] os = nonNullOperations();
        if (os.length == 0)
            return os;
        else
            return os.clone();
    }

    private MBeanOperationInfo[] fastGetOperations() {
        if (arrayGettersSafe)
            return nonNullOperations();
        else
            return getOperations();
    }

    private MBeanOperationInfo[] nonNullOperations() {
        return (operations == null) ?
            MBeanOperationInfo.NO_OPERATIONS : operations;
    }

    /**
     * <p>Returns the list of the public constructors of the MBean.
     * Each constructor is described by an
     * <CODE>MBeanConstructorInfo</CODE> object.</p>
     *
     * <p>The returned array is a shallow copy of the internal array,
     * which means that it is a copy of the internal array of
     * references to the <CODE>MBeanConstructorInfo</CODE> objects but
     * that each referenced <CODE>MBeanConstructorInfo</CODE> object
     * is not copied.</p>
     *
     * <p>The returned list is not necessarily exhaustive.  That is,
     * the MBean may have a public constructor that is not in the
     * list.  In this case, the MBean server can construct another
     * instance of this MBean's class using that constructor, even
     * though it is not listed here.</p>
     *
     * @return  An array of <CODE>MBeanConstructorInfo</CODE> objects.
     */
    public MBeanConstructorInfo[] getConstructors()  {
        MBeanConstructorInfo[] cs = nonNullConstructors();
        if (cs.length == 0)
            return cs;
        else
            return cs.clone();
    }

    private MBeanConstructorInfo[] fastGetConstructors() {
        if (arrayGettersSafe)
            return nonNullConstructors();
        else
            return getConstructors();
    }

    private MBeanConstructorInfo[] nonNullConstructors() {
        return (constructors == null) ?
            MBeanConstructorInfo.NO_CONSTRUCTORS : constructors;
    }

    /**
     * Returns the list of the notifications emitted by the MBean.
     * Each notification is described by an <CODE>MBeanNotificationInfo</CODE> object.
     *
     * The returned array is a shallow copy of the internal array,
     * which means that it is a copy of the internal array of
     * references to the <CODE>MBeanNotificationInfo</CODE> objects
     * but that each referenced <CODE>MBeanNotificationInfo</CODE> object is not copied.
     *
     * @return  An array of <CODE>MBeanNotificationInfo</CODE> objects.
     */
    public MBeanNotificationInfo[] getNotifications()  {
        MBeanNotificationInfo[] ns = nonNullNotifications();
        if (ns.length == 0)
            return ns;
        else
            return ns.clone();
    }

    private MBeanNotificationInfo[] fastGetNotifications() {
        if (arrayGettersSafe)
            return nonNullNotifications();
        else
            return getNotifications();
    }

    private MBeanNotificationInfo[] nonNullNotifications() {
        return (notifications == null) ?
            MBeanNotificationInfo.NO_NOTIFICATIONS : notifications;
    }

    /**
     * Get the descriptor of this MBeanInfo.  Changing the returned value
     * will have no affect on the original descriptor.
     *
     * @return a descriptor that is either immutable or a copy of the original.
     *
     * @since 1.6
     */
    public Descriptor getDescriptor() {
        return (Descriptor) nonNullDescriptor(descriptor).clone();
    }

    @Override
    public String toString() {
        return
            getClass().getName() + "[" +
            "description=" + getDescription() + ", " +
            "attributes=" + Arrays.asList(fastGetAttributes()) + ", " +
            "constructors=" + Arrays.asList(fastGetConstructors()) + ", " +
            "operations=" + Arrays.asList(fastGetOperations()) + ", " +
            "notifications=" + Arrays.asList(fastGetNotifications()) + ", " +
            "descriptor=" + getDescriptor() +
            "]";
    }

    /**
     * <p>Compare this MBeanInfo to another.  Two MBeanInfo objects
     * are equal if and only if they return equal values for {@link
     * #getClassName()}, for {@link #getDescription()}, and for
     * {@link #getDescriptor()}, and the
     * arrays returned by the two objects for {@link
     * #getAttributes()}, {@link #getOperations()}, {@link
     * #getConstructors()}, and {@link #getNotifications()} are
     * pairwise equal.  Here "equal" means {@link
     * Object#equals(Object)}, not identity.</p>
     *
     * <p>If two MBeanInfo objects return the same values in one of
     * their arrays but in a different order then they are not equal.</p>
     *
     * @param o the object to compare to.
     *
     * @return true if and only if <code>o</code> is an MBeanInfo that is equal
     * to this one according to the rules above.
     */
    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof MBeanInfo))
            return false;
        MBeanInfo p = (MBeanInfo) o;
        if (!isEqual(getClassName(),  p.getClassName()) ||
                !isEqual(getDescription(), p.getDescription()) ||
                !getDescriptor().equals(p.getDescriptor())) {
            return false;
        }

        return
            (Arrays.equals(p.fastGetAttributes(), fastGetAttributes()) &&
             Arrays.equals(p.fastGetOperations(), fastGetOperations()) &&
             Arrays.equals(p.fastGetConstructors(), fastGetConstructors()) &&
             Arrays.equals(p.fastGetNotifications(), fastGetNotifications()));
    }

    @Override
    public int hashCode() {
        /* Since computing the hashCode is quite expensive, we cache it.
           If by some terrible misfortune the computed value is 0, the
           caching won't work and we will recompute it every time.

           We don't bother synchronizing, because, at worst, n different
           threads will compute the same hashCode at the same time.  */
        if (hashCode != 0)
            return hashCode;

        hashCode =
            getClassName().hashCode() ^
            getDescriptor().hashCode() ^
            arrayHashCode(fastGetAttributes()) ^
            arrayHashCode(fastGetOperations()) ^
            arrayHashCode(fastGetConstructors()) ^
            arrayHashCode(fastGetNotifications());

        return hashCode;
    }

    private static int arrayHashCode(Object[] array) {
        int hash = 0;
        for (int i = 0; i < array.length; i++)
            hash ^= array[i].hashCode();
        return hash;
    }

    /**
     * Cached results of previous calls to arrayGettersSafe.  This is
     * a WeakHashMap so that we don't prevent a class from being
     * garbage collected just because we know whether it's immutable.
     */
    private static final Map<Class<?>, Boolean> arrayGettersSafeMap =
        new WeakHashMap<Class<?>, Boolean>();

    /**
     * Return true if <code>subclass</code> is known to preserve the
     * immutability of <code>immutableClass</code>.  The class
     * <code>immutableClass</code> is a reference class that is known
     * to be immutable.  The subclass <code>subclass</code> is
     * considered immutable if it does not override any public method
     * of <code>immutableClass</code> whose name begins with "get".
     * This is obviously not an infallible test for immutability,
     * but it works for the public interfaces of the MBean*Info classes.
    */
    static boolean arrayGettersSafe(Class<?> subclass, Class<?> immutableClass) {
        if (subclass == immutableClass)
            return true;
        synchronized (arrayGettersSafeMap) {
            Boolean safe = arrayGettersSafeMap.get(subclass);
            if (safe == null) {
                try {
                    ArrayGettersSafeAction action =
                        new ArrayGettersSafeAction(subclass, immutableClass);
                    safe = AccessController.doPrivileged(action);
                } catch (Exception e) { // e.g. SecurityException
                    /* We don't know, so we assume it isn't.  */
                    safe = false;
                }
                arrayGettersSafeMap.put(subclass, safe);
            }
            return safe;
        }
    }

    /*
     * The PrivilegedAction stuff is probably overkill.  We can be
     * pretty sure the caller does have the required privileges -- a
     * JMX user that can't do reflection can't even use Standard
     * MBeans!  But there's probably a performance gain by not having
     * to check the whole call stack.
     */
    private static class ArrayGettersSafeAction
            implements PrivilegedAction<Boolean> {

        private final Class<?> subclass;
        private final Class<?> immutableClass;

        ArrayGettersSafeAction(Class<?> subclass, Class<?> immutableClass) {
            this.subclass = subclass;
            this.immutableClass = immutableClass;
        }

        public Boolean run() {
            Method[] methods = immutableClass.getMethods();
            for (int i = 0; i < methods.length; i++) {
                Method method = methods[i];
                String methodName = method.getName();
                if (methodName.startsWith("get") &&
                        method.getParameterTypes().length == 0 &&
                        method.getReturnType().isArray()) {
                    try {
                        Method submethod =
                            subclass.getMethod(methodName);
                        if (!submethod.equals(method))
                            return false;
                    } catch (NoSuchMethodException e) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    private static boolean isEqual(String s1, String s2) {
        boolean ret;

        if (s1 == null) {
            ret = (s2 == null);
        } else {
            ret = s1.equals(s2);
        }

        return ret;
    }

    /**
     * Serializes an {@link MBeanInfo} to an {@link ObjectOutputStream}.
     * @serialData
     * For compatibility reasons, an object of this class is serialized as follows.
     * <ul>
     * The method {@link ObjectOutputStream#defaultWriteObject defaultWriteObject()}
     * is called first to serialize the object except the field {@code descriptor}
     * which is declared as transient. The field {@code descriptor} is serialized
     * as follows:
     *     <ul>
     *     <li> If {@code descriptor} is an instance of the class
     *        {@link ImmutableDescriptor}, the method {@link ObjectOutputStream#write
     *        write(int val)} is called to write a byte with the value {@code 1},
     *        then the method {@link ObjectOutputStream#writeObject writeObject(Object obj)}
     *        is called twice to serialize the field names and the field values of the
     *        {@code descriptor}, respectively as a {@code String[]} and an
     *        {@code Object[]};</li>
     *     <li> Otherwise, the method {@link ObjectOutputStream#write write(int val)}
     *        is called to write a byte with the value {@code 0}, then the method
     *        {@link ObjectOutputStream#writeObject writeObject(Object obj)} is called
     *        to serialize the field {@code descriptor} directly.
     *     </ul>
     * </ul>
     * @since 1.6
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();

        if (descriptor.getClass() == ImmutableDescriptor.class) {
            out.write(1);

            final String[] names = descriptor.getFieldNames();

            out.writeObject(names);
            out.writeObject(descriptor.getFieldValues(names));
        } else {
            out.write(0);

            out.writeObject(descriptor);
        }
    }

    /**
     * Deserializes an {@link MBeanInfo} from an {@link ObjectInputStream}.
     * @serialData
     * For compatibility reasons, an object of this class is deserialized as follows.
     * <ul>
     * The method {@link ObjectInputStream#defaultReadObject defaultReadObject()}
     * is called first to deserialize the object except the field
     * {@code descriptor}, which is not serialized in the default way. Then the method
     * {@link ObjectInputStream#read read()} is called to read a byte, the field
     * {@code descriptor} is deserialized according to the value of the byte value:
     *    <ul>
     *    <li>1. The method {@link ObjectInputStream#readObject readObject()}
     *       is called twice to obtain the field names (a {@code String[]}) and
     *       the field values (a {@code Object[]}) of the {@code descriptor}.
     *       The two obtained values then are used to construct
     *       an {@link ImmutableDescriptor} instance for the field
     *       {@code descriptor};</li>
     *    <li>0. The value for the field {@code descriptor} is obtained directly
     *       by calling the method {@link ObjectInputStream#readObject readObject()}.
     *       If the obtained value is null, the field {@code descriptor} is set to
     *       {@link ImmutableDescriptor#EMPTY_DESCRIPTOR EMPTY_DESCRIPTOR};</li>
     *    <li>-1. This means that there is no byte to read and that the object is from
     *       an earlier version of the JMX API. The field {@code descriptor} is set to
     *       {@link ImmutableDescriptor#EMPTY_DESCRIPTOR EMPTY_DESCRIPTOR}.</li>
     *    <li>Any other value. A {@link StreamCorruptedException} is thrown.</li>
     *    </ul>
     * </ul>
     * @since 1.6
     */

    private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {

        in.defaultReadObject();

        switch (in.read()) {
        case 1:
            final String[] names = (String[])in.readObject();

            if (names.length == 0) {
                descriptor = ImmutableDescriptor.EMPTY_DESCRIPTOR;
            } else {
                final Object[] values = (Object[])in.readObject();
                descriptor = new ImmutableDescriptor(names, values);
            }

            break;
        case 0:
            descriptor = (Descriptor)in.readObject();

            if (descriptor == null) {
                descriptor = ImmutableDescriptor.EMPTY_DESCRIPTOR;
            }

            break;
        case -1: // from an earlier version of the JMX API
            descriptor = ImmutableDescriptor.EMPTY_DESCRIPTOR;

            break;
        default:
            throw new StreamCorruptedException("Got unexpected byte.");
        }
    }

    /**
     * <p>Return an {@code MBeanInfo} object that is the same as this one
     * except that its descriptions are localized in the given locale.
     * This means the text returned by {@link MBeanInfo#getDescription}
     * (the description of the MBean itself), and the text returned by the
     * {@link MBeanFeatureInfo#getDescription getDescription()} method
     * for every {@linkplain MBeanAttributeInfo attribute}, {@linkplain
     * MBeanOperationInfo operation}, {@linkplain MBeanConstructorInfo
     * constructor}, and {@linkplain MBeanNotificationInfo notification}
     * contained in the {@code MBeanInfo}.</p>
     *
     * <p>Here is how the description {@code this.getDescription()} is
     * localized.</p>
     *
     * <p>First, if the {@linkplain #getDescriptor() descriptor}
     * of this {@code MBeanInfo} contains a field <code><a
     * href="Descriptor.html#locale">"locale"</a></code>, and the value of
     * the field is the same as {@code locale.toString()}, then this {@code
     * MBeanInfo} is returned. Otherwise, localization proceeds as follows,
     * and the {@code "locale"} field in the returned {@code MBeanInfo} will
     * be {@code locale.toString()}.
     *
     * <p>A <em>{@code className}</em> is determined. If this
     * {@code MBeanInfo} contains a descriptor with the field
     * <a href="Descriptor.html#interfaceClassName">{@code
     * "interfaceClassName"}</a>, then the value of that field is the
     * {@code className}. Otherwise, it is {@link #getClassName()}.
     * Everything before the last period (.) in the {@code className} is
     * the <em>{@code package}</em>, and everything after is the <em>{@code
     * simpleClassName}</em>. (If there is no period, then the {@code package}
     * is empty and the {@code simpleClassName} is the same as the {@code
     * className}.)</p>
     *
     * <p>A <em>{@code resourceKey}</em> is determined. If this {@code
     * MBeanInfo} contains a {@linkplain MBeanInfo#getDescriptor() descriptor}
     * with a field {@link JMX#DESCRIPTION_RESOURCE_KEY_FIELD
     * "descriptionResourceKey"}, the value of the field is
     * the {@code resourceKey}. Otherwise, the {@code resourceKey} is {@code
     * simpleClassName + ".mbean"}.</p>
     *
     * <p>A <em>{@code resourceBundleBaseName}</em> is determined. If
     * this {@code MBeanInfo} contains a descriptor with a field {@link
     * JMX#DESCRIPTION_RESOURCE_BUNDLE_BASE_NAME_FIELD
     * "descriptionResourceBundleBaseName"}, the value of the field
     * is the {@code resourceBundleBaseName}. Otherwise, the {@code
     * resourceBundleBaseName} is {@code package + ".MBeanDescriptions"}.
     *
     * <p>Then, a {@link java.util.ResourceBundle ResourceBundle} is
     * determined, using<br> {@link java.util.ResourceBundle#getBundle(String,
     * Locale, ClassLoader) ResourceBundle.getBundle(resourceBundleBaseName,
     * locale, loader)}. If this succeeds, and if {@link
     * java.util.ResourceBundle#getString(String) getString(resourceKey)}
     * returns a string, then that string is the localized description.
     * Otherwise, the original description is unchanged.</p>
     *
     * <p>A localized description for an {@code MBeanAttributeInfo} is
     * obtained similarly. The default {@code resourceBundleBaseName}
     * is the same as above. The default description and the
     * descriptor fields {@code "descriptionResourceKey"} and {@code
     * "descriptionResourceBundleBaseName"} come from the {@code
     * MBeanAttributeInfo} rather than the {@code MBeanInfo}. If the
     * attribute's {@linkplain MBeanFeatureInfo#getName() name} is {@code
     * Foo} then its default {@code resourceKey} is {@code simpleClassName +
     * ".attribute.Foo"}.</p>
     *
     * <p>Similar rules apply for operations, constructors, and notifications.
     * If the name of the operation, constructor, or notification is {@code
     * Foo} then the default {@code resourceKey} is respectively {@code
     * simpleClassName + ".operation.Foo"}, {@code simpleClassName +
     * ".constructor.Foo"}, or {@code simpleClassName + ".notification.Foo"}.
     * If two operations or constructors have the same name (overloading) then
     * they have the same default {@code resourceKey}; if different localized
     * descriptions are needed then a non-default key must be supplied using
     * {@code "descriptionResourceKey"}.</p>
     *
     * <p>Similar rules also apply for descriptions of parameters ({@link
     * MBeanParameterInfo}). The default {@code resourceKey} for a parameter
     * whose {@linkplain MBeanFeatureInfo#getName() name} is {@code
     * Bar} in an operation or constructor called {@code Foo} is {@code
     * simpleClassName + ".operation.Foo.Bar"} or {@code simpleClassName +
     * ".constructor.Foo.Bar"} respectively.</p>
     *
     * <h4>Example</h4>
     *
     * <p>Suppose you have an MBean defined by these two Java source files:</p>
     *
     * <pre>
     * // ConfigurationMBean.java
     * package com.example;
     * public interface ConfigurationMBean {
     *     public String getName();
     *     public void save(String fileName);
     * }
     *
     * // Configuration.java
     * package com.example;
     * public class Configuration implements ConfigurationMBean {
     *     public Configuration(String defaultName) {
     *         ...
     *     }
     *     ...
     * }
     * </pre>
     *
     * <p>Then you could define the default descriptions for the MBean, by
     * including a resource bundle called {@code com/example/MBeanDescriptions}
     * with the compiled classes. Most often this is done by creating a file
     * {@code MBeanDescriptions.properties} in the same directory as {@code
     * ConfigurationMBean.java}. Make sure that this file is copied into the
     * same place as the compiled classes; in typical build environments that
     * will be true by default.</p>
     *
     * <p>The file {@code com/example/MBeanDescriptions.properties} might
     * look like this:</p>
     *
     * <pre>
     * # Description of the MBean
     * ConfigurationMBean.mbean = Configuration manager
     *
     * # Description of the Name attribute
     * ConfigurationMBean.attribute.Name = The name of the configuration
     *
     * # Description of the save operation
     * ConfigurationMBean.operation.save = Save the configuration to a file
     *
     * # Description of the parameter to the save operation.
     * # Parameter names from the original Java source are not available,
     * # so the default names are p1, p2, etc.  If the names were available,
     * # this would be ConfigurationMBean.operation.save.fileName
     * ConfigurationMBean.operation.save.p1 = The name of the file
     *
     * # Description of the constructor.  The default name of a constructor is
     * # its fully-qualified class name.
     * ConfigurationMBean.constructor.com.example.Configuration = <!--
     * -->Constructor with name of default file
     * # Description of the constructor parameter.
     * ConfigurationMBean.constructor.com.example.Configuration.p1 = <!--
     * -->Name of the default file
     * </pre>
     *
     * <p>Starting with this file, you could create descriptions for the French
     * locale by creating {@code com/example/MBeanDescriptions_fr.properties}.
     * The keys in this file are the same as before but the text has been
     * translated:
     *
     * <pre>
     * ConfigurationMBean.mbean = Gestionnaire de configuration
     *
     * ConfigurationMBean.attribute.Name = Le nom de la configuration
     *
     * ConfigurationMBean.operation.save = Sauvegarder la configuration <!--
     * -->dans un fichier
     *
     * ConfigurationMBean.operation.save.p1 = Le nom du fichier
     *
     * ConfigurationMBean.constructor.com.example.Configuration = <!--
     * -->Constructeur avec nom du fichier par d&eacute;faut
     * ConfigurationMBean.constructor.com.example.Configuration.p1 = <!--
     * -->Nom du fichier par d&eacute;faut
     * </pre>
     *
     * <p>The descriptions in {@code MBeanDescriptions.properties} and
     * {@code MBeanDescriptions_fr.properties} will only be consulted if
     * {@code localizeDescriptions} is called, perhaps because the
     * MBean Server has been wrapped by {@link
     * ClientContext#newLocalizeMBeanInfoForwarder} or because the
     * connector server has been created with the {@link
     * javax.management.remote.JMXConnectorServer#LOCALIZE_MBEAN_INFO_FORWARDER
     * LOCALIZE_MBEAN_INFO_FORWARDER} option. If you want descriptions
     * even when there is no localization step, then you should consider
     * using {@link Description &#64;Description} annotations. Annotations
     * provide descriptions by default but are overridden if {@code
     * localizeDescriptions} is called.</p>
     *
     * @param locale the target locale for descriptions.  Cannot be null.
     *
     * @param loader the {@code ClassLoader} to use for looking up resource
     * bundles.
     *
     * @return an {@code MBeanInfo} with descriptions appropriately localized.
     *
     * @throws NullPointerException if {@code locale} is null.
     */
    public MBeanInfo localizeDescriptions(Locale locale, ClassLoader loader) {
        if (locale == null)
            throw new NullPointerException("locale");
        Descriptor d = getDescriptor();
        String mbiLocaleString = (String) d.getFieldValue(JMX.LOCALE_FIELD);
        if (locale.toString().equals(mbiLocaleString))
            return this;
        return new Rewriter(this, locale, loader).getMBeanInfo();
    }

    private static class Rewriter {
        private final MBeanInfo mbi;
        private final ClassLoader loader;
        private final Locale locale;
        private final String packageName;
        private final String simpleClassNamePlusDot;
        private ResourceBundle defaultBundle;
        private boolean defaultBundleLoaded;

        // ResourceBundle.getBundle throws NullPointerException
        // if the loader is null, even though that is perfectly
        // valid and means the bootstrap loader.  So we work
        // around with a ClassLoader that is equivalent to the
        // bootstrap loader but is not null.
        private static final ClassLoader bootstrapLoader =
                new ClassLoader(null) {};

        Rewriter(MBeanInfo mbi, Locale locale, ClassLoader loader) {
            this.mbi = mbi;
            this.locale = locale;
            if (loader == null)
                loader = bootstrapLoader;
            this.loader = loader;

            String intfName = (String)
                    mbi.getDescriptor().getFieldValue("interfaceClassName");
            if (intfName == null)
                intfName = mbi.getClassName();
            int lastDot = intfName.lastIndexOf('.');
            this.packageName = intfName.substring(0, lastDot + 1);
            this.simpleClassNamePlusDot = intfName.substring(lastDot + 1) + ".";
            // Inner classes show up as Outer$Inner so won't match the dot.
            // When there is no dot, lastDot is -1,
            // packageName is empty, and simpleClassNamePlusDot is intfName.
        }

        MBeanInfo getMBeanInfo() {
            MBeanAttributeInfo[] mbais =
                    rewrite(mbi.getAttributes(), "attribute.");
            MBeanOperationInfo[] mbois =
                    rewrite(mbi.getOperations(), "operation.");
            MBeanConstructorInfo[] mbcis =
                    rewrite(mbi.getConstructors(), "constructor.");
            MBeanNotificationInfo[] mbnis =
                    rewrite(mbi.getNotifications(), "notification.");
            Descriptor d = mbi.getDescriptor();
            d = changeLocale(d);
            String description = getDescription(d, "mbean", "");
            if (description == null)
                description = mbi.getDescription();
            return new MBeanInfo(
                    mbi.getClassName(), description,
                    mbais, mbcis, mbois, mbnis, d);
        }

        private Descriptor changeLocale(Descriptor d) {
            if (d.getFieldValue(JMX.LOCALE_FIELD) != null) {
                Map<String, Object> map = new HashMap<String, Object>();
                for (String field : d.getFieldNames())
                    map.put(field, d.getFieldValue(field));
                map.remove(JMX.LOCALE_FIELD);
                d = new ImmutableDescriptor(map);
            }
            return ImmutableDescriptor.union(
                    d, new ImmutableDescriptor(JMX.LOCALE_FIELD + "=" + locale));
        }

        private String getDescription(
                Descriptor d, String defaultPrefix, String defaultSuffix) {
            ResourceBundle bundle = bundleFromDescriptor(d);
            if (bundle == null)
                return null;
            String key =
                    (String) d.getFieldValue(JMX.DESCRIPTION_RESOURCE_KEY_FIELD);
            if (key == null)
                key = simpleClassNamePlusDot + defaultPrefix + defaultSuffix;
            return descriptionFromResource(bundle, key);
        }

        private <T extends MBeanFeatureInfo> T[] rewrite(
                T[] features, String resourcePrefix) {
            for (int i = 0; i < features.length; i++) {
                T feature = features[i];
                Descriptor d = feature.getDescriptor();
                String description =
                        getDescription(d, resourcePrefix, feature.getName());
                if (description != null &&
                        !description.equals(feature.getDescription())) {
                    features[i] = setDescription(feature, description);
                }
            }
            return features;
        }

        private <T extends MBeanFeatureInfo> T setDescription(
                T feature, String description) {

            Object newf;
            String name = feature.getName();
            Descriptor d = feature.getDescriptor();

            if (feature instanceof MBeanAttributeInfo) {
                MBeanAttributeInfo mbai = (MBeanAttributeInfo) feature;
                newf = new MBeanAttributeInfo(
                        name, mbai.getType(), description,
                        mbai.isReadable(), mbai.isWritable(), mbai.isIs(),
                        d);
            } else if (feature instanceof MBeanOperationInfo) {
                MBeanOperationInfo mboi = (MBeanOperationInfo) feature;
                MBeanParameterInfo[] sig = rewrite(
                        mboi.getSignature(), "operation." + name + ".");
                newf = new MBeanOperationInfo(
                        name, description, sig,
                        mboi.getReturnType(), mboi.getImpact(), d);
            } else if (feature instanceof MBeanConstructorInfo) {
                MBeanConstructorInfo mbci = (MBeanConstructorInfo) feature;
                MBeanParameterInfo[] sig = rewrite(
                        mbci.getSignature(), "constructor." + name + ".");
                newf = new MBeanConstructorInfo(
                        name, description, sig, d);
            } else if (feature instanceof MBeanNotificationInfo) {
                MBeanNotificationInfo mbni = (MBeanNotificationInfo) feature;
                newf = new MBeanNotificationInfo(
                        mbni.getNotifTypes(), name, description, d);
            } else if (feature instanceof MBeanParameterInfo) {
                MBeanParameterInfo mbpi = (MBeanParameterInfo) feature;
                newf = new MBeanParameterInfo(
                        name, mbpi.getType(), description, d);
            } else {
                logger().log(Level.FINE, "Unknown feature type: " +
                        feature.getClass());
                newf = feature;
            }

            return Util.<T>cast(newf);
        }

        private ResourceBundle bundleFromDescriptor(Descriptor d) {
            String bundleName = (String) d.getFieldValue(
                    JMX.DESCRIPTION_RESOURCE_BUNDLE_BASE_NAME_FIELD);

            if (bundleName != null)
                return getBundle(bundleName);

            if (defaultBundleLoaded)
                return defaultBundle;

            bundleName = packageName + "MBeanDescriptions";
            defaultBundle = getBundle(bundleName);
            defaultBundleLoaded = true;
            return defaultBundle;
        }

        private String descriptionFromResource(
                ResourceBundle bundle, String key) {
            try {
                return bundle.getString(key);
            } catch (MissingResourceException e) {
                logger().log(Level.FINEST, "No resource for " + key, e);
            } catch (Exception e) {
                logger().log(Level.FINE, "Bad resource for " + key, e);
            }
            return null;
        }

        private ResourceBundle getBundle(String name) {
            try {
                return ResourceBundle.getBundle(name, locale, loader);
            } catch (Exception e) {
                logger().log(Level.FINE,
                           "Could not load ResourceBundle " + name, e);
                return null;
            }
        }

        private Logger logger() {
            return Logger.getLogger("javax.management.locale");
        }
    }
}
