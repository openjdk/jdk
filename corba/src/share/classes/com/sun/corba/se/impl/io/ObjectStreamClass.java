/*
 * Copyright 1998-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package com.sun.corba.se.impl.io;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.DigestOutputStream;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedAction;

import java.lang.reflect.Modifier;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;

import java.io.IOException;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.Serializable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Hashtable;

import com.sun.corba.se.impl.util.RepositoryId;

import org.omg.CORBA.ValueMember;

import sun.corba.Bridge;

/**
 * A ObjectStreamClass describes a class that can be serialized to a stream
 * or a class that was serialized to a stream.  It contains the name
 * and the serialVersionUID of the class.
 * <br>
 * The ObjectStreamClass for a specific class loaded in this Java VM can
 * be found using the lookup method.
 *
 * @author  Roger Riggs
 * @since   JDK1.1
 */
public class ObjectStreamClass implements java.io.Serializable {
    private static final boolean DEBUG_SVUID = false ;

    public static final long kDefaultUID = -1;

    private static Object noArgsList[] = {};
    private static Class noTypesList[] = {};

    private static Hashtable translatedFields;

    private static final Bridge bridge =
        (Bridge)AccessController.doPrivileged(
            new PrivilegedAction() {
                public Object run() {
                    return Bridge.get() ;
                }
            }
        ) ;

    /** Find the descriptor for a class that can be serialized.  Null
     * is returned if the specified class does not implement
     * java.io.Serializable or java.io.Externalizable.
     */
    static final ObjectStreamClass lookup(Class cl)
    {
        ObjectStreamClass desc = lookupInternal(cl);
        if (desc.isSerializable() || desc.isExternalizable())
            return desc;
        return null;
    }

    /*
     * Find the class descriptor for the specified class.
     * Package access only so it can be called from ObjectIn/OutStream.
     */
    static ObjectStreamClass lookupInternal(Class cl)
    {
        /* Synchronize on the hashtable so no two threads will do
         * this at the same time.
         */
        ObjectStreamClass desc = null;
        synchronized (descriptorFor) {
            /* Find the matching descriptor if it already known */
            desc = findDescriptorFor(cl);
            if (desc == null) {
                /* Check if it's serializable */
                boolean serializable = classSerializable.isAssignableFrom(cl);

                /* If the class is only Serializable,
                 * lookup the descriptor for the superclass.
                 */
                ObjectStreamClass superdesc = null;
                if (serializable) {
                    Class superclass = cl.getSuperclass();
                    if (superclass != null)
                        superdesc = lookup(superclass);
                }

                /* Check if its' externalizable.
                 * If it's Externalizable, clear the serializable flag.
                 * Only one or the other may be set in the protocol.
                 */
                boolean externalizable = false;
                if (serializable) {
                    externalizable =
                        ((superdesc != null) && superdesc.isExternalizable()) ||
                        classExternalizable.isAssignableFrom(cl);
                    if (externalizable) {
                        serializable = false;
                    }
                }

                /* Create a new version descriptor,
                 * it put itself in the known table.
                 */
                desc = new ObjectStreamClass(cl, superdesc,
                                             serializable, externalizable);
            }
            // Must always call init.  See bug 4488137.  This code was
            // incorrectly changed to return immediately on a non-null
            // cache result.  That allowed threads to gain access to
            // unintialized instances.
            //
            // History: Note, the following init() call was originally within
            // the synchronization block, as it currently is now. Later, the
            // init() call was moved outside the synchronization block, and
            // the init() method used a private member variable lock, to
            // avoid performance problems. See bug 4165204. But that lead to
            // a deadlock situation, see bug 5104239. Hence, the init() method
            // has now been moved back into the synchronization block. The
            // right approach to solving these problems would be to rewrite
            // this class, based on the latest java.io.ObjectStreamClass.
            desc.init();
        }
        return desc;
    }

    /**
     * The name of the class described by this descriptor.
     */
    public final String getName() {
        return name;
    }

    /**
     * Return the serialVersionUID for this class.
     * The serialVersionUID defines a set of classes all with the same name
     * that have evolved from a common root class and agree to be serialized
     * and deserialized using a common format.
     */
    public static final long getSerialVersionUID( java.lang.Class clazz) {
        ObjectStreamClass theosc = ObjectStreamClass.lookup( clazz );
        if( theosc != null )
        {
                return theosc.getSerialVersionUID( );
        }
        return 0;
    }

    /**
     * Return the serialVersionUID for this class.
     * The serialVersionUID defines a set of classes all with the same name
     * that have evolved from a common root class and agree to be serialized
     * and deserialized using a common format.
     */
    public final long getSerialVersionUID() {
        return suid;
    }

    /**
     * Return the serialVersionUID string for this class.
     * The serialVersionUID defines a set of classes all with the same name
     * that have evolved from a common root class and agree to be serialized
     * and deserialized using a common format.
     */
    public final String getSerialVersionUIDStr() {
        if (suidStr == null)
            suidStr = Long.toHexString(suid).toUpperCase();
        return suidStr;
    }

    /**
     * Return the actual (computed) serialVersionUID for this class.
     */
    public static final long getActualSerialVersionUID( java.lang.Class clazz )
    {
        ObjectStreamClass theosc = ObjectStreamClass.lookup( clazz );
        if( theosc != null )
        {
                return theosc.getActualSerialVersionUID( );
        }
        return 0;
    }

    /**
     * Return the actual (computed) serialVersionUID for this class.
     */
    public final long getActualSerialVersionUID() {
        return actualSuid;
    }

    /**
     * Return the actual (computed) serialVersionUID for this class.
     */
    public final String getActualSerialVersionUIDStr() {
        if (actualSuidStr == null)
            actualSuidStr = Long.toHexString(actualSuid).toUpperCase();
        return actualSuidStr;
    }

    /**
     * Return the class in the local VM that this version is mapped to.
     * Null is returned if there is no corresponding local class.
     */
    public final Class forClass() {
        return ofClass;
    }

    /**
     * Return an array of the fields of this serializable class.
     * @return an array containing an element for each persistent
     * field of this class. Returns an array of length zero if
     * there are no fields.
     * @since JDK1.2
     */
    public ObjectStreamField[] getFields() {
        // Return a copy so the caller can't change the fields.
        if (fields.length > 0) {
            ObjectStreamField[] dup = new ObjectStreamField[fields.length];
            System.arraycopy(fields, 0, dup, 0, fields.length);
            return dup;
        } else {
            return fields;
        }
    }

    public boolean hasField(ValueMember field)
    {
        try {
            for (int i = 0; i < fields.length; i++) {
                if (fields[i].getName().equals(field.name)) {
                    if (fields[i].getSignature().equals(
                        ValueUtility.getSignature(field)))
                        return true;
                }
            }
        } catch (Exception exc) {
            // Ignore this; all we want to do is return false
            // Note that ValueUtility.getSignature can throw checked exceptions.
        }

        return false;
    }

    /* Avoid unnecessary allocations. */
    final ObjectStreamField[] getFieldsNoCopy() {
        return fields;
    }

    /**
     * Get the field of this class by name.
     * @return The ObjectStreamField object of the named field or null if there
     * is no such named field.
     */
    public final ObjectStreamField getField(String name) {
        /* Binary search of fields by name.
         */
        for (int i = fields.length-1; i >= 0; i--) {
            if (name.equals(fields[i].getName())) {
                return fields[i];
            }
        }
        return null;
    }

    public Serializable writeReplace(Serializable value) {
        if (writeReplaceObjectMethod != null) {
            try {
                return (Serializable) writeReplaceObjectMethod.invoke(value,noArgsList);
            } catch(Throwable t) {
                throw new RuntimeException(t);
            }
        }
        else return value;
    }

    public Object readResolve(Object value) {
        if (readResolveObjectMethod != null) {
            try {
                return readResolveObjectMethod.invoke(value,noArgsList);
            } catch(Throwable t) {
                throw new RuntimeException(t);
            }
        }
        else return value;
    }

    /**
     * Return a string describing this ObjectStreamClass.
     */
    public final String toString() {
        StringBuffer sb = new StringBuffer();

        sb.append(name);
        sb.append(": static final long serialVersionUID = ");
        sb.append(Long.toString(suid));
        sb.append("L;");
        return sb.toString();
    }

    /*
     * Create a new ObjectStreamClass from a loaded class.
     * Don't call this directly, call lookup instead.
     */
    private ObjectStreamClass(java.lang.Class cl, ObjectStreamClass superdesc,
                              boolean serial, boolean extern)
    {
        ofClass = cl;           /* created from this class */

        if (Proxy.isProxyClass(cl)) {
            forProxyClass = true;
        }

        name = cl.getName();
        superclass = superdesc;
        serializable = serial;
        if (!forProxyClass) {
            // proxy classes are never externalizable
            externalizable = extern;
        }

        /*
         * Enter this class in the table of known descriptors.
         * Otherwise, when the fields are read it may recurse
         * trying to find the descriptor for itself.
         */
        insertDescriptorFor(this);

        /*
         * The remainder of initialization occurs in init(), which is called
         * after the lock on the global class descriptor table has been
         * released.
         */
    }

    /*
     * Initialize class descriptor.  This method is only invoked on class
     * descriptors created via calls to lookupInternal().  This method is kept
     * separate from the ObjectStreamClass constructor so that lookupInternal
     * does not have to hold onto a global class descriptor table lock while the
     * class descriptor is being initialized (see bug 4165204).
     */


    private void init() {
      synchronized (lock) {

        // See description at definition of initialized.
        if (initialized)
            return;

        final Class cl = ofClass;

        if (!serializable ||
            externalizable ||
            forProxyClass ||
            name.equals("java.lang.String")) {
            fields = NO_FIELDS;
        } else if (serializable) {
            /* Ask for permission to override field access checks.
             */
            AccessController.doPrivileged(new PrivilegedAction() {
                public Object run() {
                /* Fill in the list of persistent fields.
                 * If it is declared, use the declared serialPersistentFields.
                 * Otherwise, extract the fields from the class itself.
                 */
                try {
                    Field pf = cl.getDeclaredField("serialPersistentFields");
                    // serial bug 7; the serialPersistentFields were not
                    // being read and stored as Accessible bit was not set
                    pf.setAccessible(true);
                    // serial bug 7; need to find if the field is of type
                    // java.io.ObjectStreamField
                    java.io.ObjectStreamField[] f =
                           (java.io.ObjectStreamField[])pf.get(cl);
                    int mods = pf.getModifiers();
                    if ((Modifier.isPrivate(mods)) &&
                        (Modifier.isStatic(mods)) &&
                        (Modifier.isFinal(mods)))
                    {
                        fields = (ObjectStreamField[])translateFields((Object[])pf.get(cl));
                    }
                } catch (NoSuchFieldException e) {
                    fields = null;
                } catch (IllegalAccessException e) {
                    fields = null;
                } catch (IllegalArgumentException e) {
                    fields = null;
                } catch (ClassCastException e) {
                    /* Thrown if a field serialPersistentField exists
                     * but it is not of type ObjectStreamField.
                     */
                    fields = null;
                }


                if (fields == null) {
                    /* Get all of the declared fields for this
                     * Class. setAccessible on all fields so they
                     * can be accessed later.  Create a temporary
                     * ObjectStreamField array to hold each
                     * non-static, non-transient field. Then copy the
                     * temporary array into an array of the correct
                     * size once the number of fields is known.
                     */
                    Field[] actualfields = cl.getDeclaredFields();

                    int numFields = 0;
                    ObjectStreamField[] tempFields =
                        new ObjectStreamField[actualfields.length];
                    for (int i = 0; i < actualfields.length; i++) {
                        Field fld = actualfields[i] ;
                        int modifiers = fld.getModifiers();
                        if (!Modifier.isStatic(modifiers) &&
                            !Modifier.isTransient(modifiers)) {
                            fld.setAccessible(true) ;
                            tempFields[numFields++] = new ObjectStreamField(fld);
                        }
                    }

                    fields = new ObjectStreamField[numFields];
                    System.arraycopy(tempFields, 0, fields, 0, numFields);

                } else {
                    // For each declared persistent field, look for an actual
                    // reflected Field. If there is one, make sure it's the correct
                    // type and cache it in the ObjectStreamClass for that field.
                    for (int j = fields.length-1; j >= 0; j--) {
                        try {
                            Field reflField = cl.getDeclaredField(fields[j].getName());
                            if (fields[j].getType() == reflField.getType()) {
                                reflField.setAccessible(true);
                                fields[j].setField(reflField);
                            }
                        } catch (NoSuchFieldException e) {
                            // Nothing to do
                        }
                    }
                }
                return null;
            }
            });

            if (fields.length > 1)
                Arrays.sort(fields);

            /* Set up field data for use while writing using the API api. */
            computeFieldInfo();
        }

        /* Get the serialVersionUID from the class.
         * It uses the access override mechanism so make sure
         * the field objects is only used here.
         *
         * NonSerializable classes have a serialVerisonUID of 0L.
         */
         if (isNonSerializable()) {
             suid = 0L;
         } else {
             // Lookup special Serializable members using reflection.
             AccessController.doPrivileged(new PrivilegedAction() {
                public Object run() {
                if (forProxyClass) {
                    // proxy classes always have serialVersionUID of 0L
                    suid = 0L;
                } else {
                    try {
                        final Field f = cl.getDeclaredField("serialVersionUID");
                        int mods = f.getModifiers();
                        // SerialBug 5:  static final SUID should be read
                        if (Modifier.isStatic(mods) && Modifier.isFinal(mods) ) {
                            f.setAccessible(true);
                            suid = f.getLong(cl);
                            // SerialBug 2: should be computed after writeObject
                            // actualSuid = computeStructuralUID(cl);
                        } else {
                            suid = _computeSerialVersionUID(cl);
                            // SerialBug 2: should be computed after writeObject
                            // actualSuid = computeStructuralUID(cl);
                        }
                    } catch (NoSuchFieldException ex) {
                        suid = _computeSerialVersionUID(cl);
                        // SerialBug 2: should be computed after writeObject
                        // actualSuid = computeStructuralUID(cl);
                    } catch (IllegalAccessException ex) {
                        suid = _computeSerialVersionUID(cl);
                    }
                }

                writeReplaceObjectMethod = ObjectStreamClass.getInheritableMethod(cl,
                    "writeReplace", noTypesList, Object.class);

                readResolveObjectMethod = ObjectStreamClass.getInheritableMethod(cl,
                    "readResolve", noTypesList, Object.class);

                if (externalizable)
                    cons = getExternalizableConstructor(cl) ;
                else
                    cons = getSerializableConstructor(cl) ;

                if (serializable && !forProxyClass) {
                    /* Look for the writeObject method
                     * Set the accessible flag on it here. ObjectOutputStream
                     * will call it as necessary.
                     */
                    writeObjectMethod = getPrivateMethod( cl, "writeObject",
                        new Class[] { java.io.ObjectOutputStream.class }, Void.TYPE ) ;
                    readObjectMethod = getPrivateMethod( cl, "readObject",
                        new Class[] { java.io.ObjectInputStream.class }, Void.TYPE ) ;
                }
                return null;
            }
          });
        }

        // This call depends on a lot of information computed above!
        actualSuid = ObjectStreamClass.computeStructuralUID(this, cl);

        // If we have a write object method, precompute the
        // RMI-IIOP stream format version 2 optional data
        // repository ID.
        if (hasWriteObject())
            rmiiiopOptionalDataRepId = computeRMIIIOPOptionalDataRepId();

        // This must be done last.
        initialized = true;
      }
    }

    /**
     * Returns non-static private method with given signature defined by given
     * class, or null if none found.  Access checks are disabled on the
     * returned method (if any).
     */
    private static Method getPrivateMethod(Class cl, String name,
                                           Class[] argTypes,
                                           Class returnType)
    {
        try {
            Method meth = cl.getDeclaredMethod(name, argTypes);
            meth.setAccessible(true);
            int mods = meth.getModifiers();
            return ((meth.getReturnType() == returnType) &&
                    ((mods & Modifier.STATIC) == 0) &&
                    ((mods & Modifier.PRIVATE) != 0)) ? meth : null;
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    // Specific to RMI-IIOP
    /**
     * Java to IDL ptc-02-01-12 1.5.1
     *
     * "The rep_id string passed to the start_value method must be
     * 'RMI:org.omg.custom.class:hashcode:suid' where class is the
     * fully-qualified name of the class whose writeObject method
     * is being invoked and hashcode and suid are the class's hashcode
     * and SUID."
     */
    private String computeRMIIIOPOptionalDataRepId() {

        StringBuffer sbuf = new StringBuffer("RMI:org.omg.custom.");
        sbuf.append(RepositoryId.convertToISOLatin1(this.getName()));
        sbuf.append(':');
        sbuf.append(this.getActualSerialVersionUIDStr());
        sbuf.append(':');
        sbuf.append(this.getSerialVersionUIDStr());

        return sbuf.toString();
    }

    /**
     * This will return null if there is no writeObject method.
     */
    public final String getRMIIIOPOptionalDataRepId() {
        return rmiiiopOptionalDataRepId;
    }

    /*
     * Create an empty ObjectStreamClass for a class about to be read.
     * This is separate from read so ObjectInputStream can assign the
     * wire handle early, before any nested ObjectStreamClass might
     * be read.
     */
    ObjectStreamClass(String n, long s) {
        name = n;
        suid = s;
        superclass = null;
    }

    private static Object[] translateFields(Object objs[])
        throws NoSuchFieldException {
        try{
            java.io.ObjectStreamField fields[] = (java.io.ObjectStreamField[])objs;
            Object translation[] = null;

            if (translatedFields == null)
                translatedFields = new Hashtable();

            translation = (Object[])translatedFields.get(fields);

            if (translation != null)
                return translation;
            else {
                Class osfClass = Class.forName("com.sun.corba.se.impl.io.ObjectStreamField");
                translation = (Object[])java.lang.reflect.Array.newInstance(osfClass, objs.length);
                Object arg[] = new Object[2];
                Class types[] = {String.class, Class.class};
                Constructor constructor = osfClass.getDeclaredConstructor(types);
                for (int i = fields.length -1; i >= 0; i--){
                    arg[0] = fields[i].getName();
                    arg[1] = fields[i].getType();

                    translation[i] = constructor.newInstance(arg);
                }
                translatedFields.put(fields, translation);

            }

            return (Object[])translation;
        }
        catch(Throwable t){
            NoSuchFieldException nsfe = new NoSuchFieldException();
            nsfe.initCause( t ) ;
            throw nsfe ;
        }
    }

    /*
     * Set the class this version descriptor matches.
     * The base class name and serializable hash must match.
     * Fill in the reflected Fields that will be used
     * for reading.
     */
    final void setClass(Class cl) throws InvalidClassException {

        if (cl == null) {
            localClassDesc = null;
            ofClass = null;
            computeFieldInfo();
            return;
        }

        localClassDesc = lookupInternal(cl);
        if (localClassDesc == null)
            // XXX I18N, logging needed
            throw new InvalidClassException(cl.getName(),
                                            "Local class not compatible");
        if (suid != localClassDesc.suid) {

            /* Check for exceptional cases that allow mismatched suid. */

            /* Allow adding Serializable or Externalizable
             * to a later release of the class.
             */
            boolean addedSerialOrExtern =
                isNonSerializable() || localClassDesc.isNonSerializable();

            /* Disregard the serialVersionUID of an array
             * when name and cl.Name differ. If resolveClass() returns
             * an array with a different package name,
             * the serialVersionUIDs will not match since the fully
             * qualified array class is used in the
             * computation of the array's serialVersionUID. There is
             * no way to set a permanent serialVersionUID for an array type.
             */

            boolean arraySUID = (cl.isArray() && ! cl.getName().equals(name));

            if (! arraySUID && ! addedSerialOrExtern ) {
                // XXX I18N, logging needed
                throw new InvalidClassException(cl.getName(),
                                                "Local class not compatible:" +
                                                " stream classdesc serialVersionUID=" + suid +
                                                " local class serialVersionUID=" + localClassDesc.suid);
            }
        }

        /* compare the class names, stripping off package names. */
        if (! compareClassNames(name, cl.getName(), '.'))
            // XXX I18N, logging needed
            throw new InvalidClassException(cl.getName(),
                         "Incompatible local class name. " +
                         "Expected class name compatible with " +
                         name);

        /*
         * Test that both implement either serializable or externalizable.
         */

        // The next check is more generic, since it covers the
        // Proxy case, the JDK 1.3 serialization code has
        // both checks
        //if ((serializable && localClassDesc.externalizable) ||
        //    (externalizable && localClassDesc.serializable))
        //    throw new InvalidClassException(localCl.getName(),
        //            "Serializable is incompatible with Externalizable");

        if ((serializable != localClassDesc.serializable) ||
            (externalizable != localClassDesc.externalizable) ||
            (!serializable && !externalizable))

            // XXX I18N, logging needed
            throw new InvalidClassException(cl.getName(),
                                            "Serialization incompatible with Externalization");

        /* Set up the reflected Fields in the class where the value of each
         * field in this descriptor should be stored.
         * Each field in this ObjectStreamClass (the source) is located (by
         * name) in the ObjectStreamClass of the class(the destination).
         * In the usual (non-versioned case) the field is in both
         * descriptors and the types match, so the reflected Field is copied.
         * If the type does not match, a InvalidClass exception is thrown.
         * If the field is not present in the class, the reflected Field
         * remains null so the field will be read but discarded.
         * If extra fields are present in the class they are ignored. Their
         * values will be set to the default value by the object allocator.
         * Both the src and dest field list are sorted by type and name.
         */

        ObjectStreamField[] destfield =
            (ObjectStreamField[])localClassDesc.fields;
        ObjectStreamField[] srcfield =
            (ObjectStreamField[])fields;

        int j = 0;
    nextsrc:
        for (int i = 0; i < srcfield.length; i++ ) {
            /* Find this field in the dest*/
            for (int k = j; k < destfield.length; k++) {
                if (srcfield[i].getName().equals(destfield[k].getName())) {
                    /* found match */
                    if (srcfield[i].isPrimitive() &&
                        !srcfield[i].typeEquals(destfield[k])) {
                        // XXX I18N, logging needed
                        throw new InvalidClassException(cl.getName(),
                                                        "The type of field " +
                                                        srcfield[i].getName() +
                                                        " of class " + name +
                                                        " is incompatible.");
                    }

                    /* Skip over any fields in the dest that are not in the src */
                    j = k;

                    srcfield[i].setField(destfield[j].getField());
                    // go on to the next source field
                    continue nextsrc;
                }
            }
        }

        /* Set up field data for use while reading from the input stream. */
        computeFieldInfo();

        /* Remember the class this represents */
        ofClass = cl;

        /* get the cache of these methods from the local class
         * implementation.
         */
        readObjectMethod = localClassDesc.readObjectMethod;
        readResolveObjectMethod = localClassDesc.readResolveObjectMethod;
    }

    /* Compare the base class names of streamName and localName.
     *
     * @return  Return true iff the base class name compare.
     * @parameter streamName    Fully qualified class name.
     * @parameter localName     Fully qualified class name.
     * @parameter pkgSeparator  class names use either '.' or '/'.
     *
     * Only compare base class name to allow package renaming.
     */
    static boolean compareClassNames(String streamName,
                                     String localName,
                                     char pkgSeparator) {
        /* compare the class names, stripping off package names. */
        int streamNameIndex = streamName.lastIndexOf(pkgSeparator);
        if (streamNameIndex < 0)
            streamNameIndex = 0;

        int localNameIndex = localName.lastIndexOf(pkgSeparator);
        if (localNameIndex < 0)
            localNameIndex = 0;

        return streamName.regionMatches(false, streamNameIndex,
                                        localName, localNameIndex,
                                        streamName.length() - streamNameIndex);
    }

    /*
     * Compare the types of two class descriptors.
     * They match if they have the same class name and suid
     */
    final boolean typeEquals(ObjectStreamClass other) {
        return (suid == other.suid) &&
            compareClassNames(name, other.name, '.');
    }

    /*
     * Return the superclass descriptor of this descriptor.
     */
    final void setSuperclass(ObjectStreamClass s) {
        superclass = s;
    }

    /*
     * Return the superclass descriptor of this descriptor.
     */
    final ObjectStreamClass getSuperclass() {
        return superclass;
    }

    /**
     * Return whether the class has a readObject method
     */
    final boolean hasReadObject() {
        return readObjectMethod != null;
    }

    /*
     * Return whether the class has a writeObject method
     */
    final boolean hasWriteObject() {
        return writeObjectMethod != null ;
    }

    /**
     * Returns when or not this class should be custom
     * marshaled (use chunking).  This should happen if
     * it is Externalizable OR if it or
     * any of its superclasses has a writeObject method,
     */
    final boolean isCustomMarshaled() {
        return (hasWriteObject() || isExternalizable())
            || (superclass != null && superclass.isCustomMarshaled());
    }

    /*
     * Return true if all instances of 'this' Externalizable class
     * are written in block-data mode from the stream that 'this' was read
     * from. <p>
     *
     * In JDK 1.1, all Externalizable instances are not written
     * in block-data mode.
     * In JDK 1.2, all Externalizable instances, by default, are written
     * in block-data mode and the Externalizable instance is terminated with
     * tag TC_ENDBLOCKDATA. Change enabled the ability to skip Externalizable
     * instances.
     *
     * IMPLEMENTATION NOTE:
     *   This should have been a mode maintained per stream; however,
     *   for compatibility reasons, it was only possible to record
     *   this change per class. All Externalizable classes within
     *   a given stream should either have this mode enabled or
     *   disabled. This is enforced by not allowing the PROTOCOL_VERSION
     *   of a stream to he changed after any objects have been written.
     *
     * @see ObjectOutputStream#useProtocolVersion
     * @see ObjectStreamConstants#PROTOCOL_VERSION_1
     * @see ObjectStreamConstants#PROTOCOL_VERSION_2
     *
     * @since JDK 1.2
     */
    boolean hasExternalizableBlockDataMode() {
        return hasExternalizableBlockData;
    }

    /**
     * Creates a new instance of the represented class.  If the class is
     * externalizable, invokes its public no-arg constructor; otherwise, if the
     * class is serializable, invokes the no-arg constructor of the first
     * non-serializable superclass.  Throws UnsupportedOperationException if
     * this class descriptor is not associated with a class, if the associated
     * class is non-serializable or if the appropriate no-arg constructor is
     * inaccessible/unavailable.
     */
    Object newInstance()
        throws InstantiationException, InvocationTargetException,
               UnsupportedOperationException
    {
        if (cons != null) {
            try {
                return cons.newInstance(new Object[0]);
            } catch (IllegalAccessException ex) {
                // should not occur, as access checks have been suppressed
                InternalError ie = new InternalError();
                ie.initCause( ex ) ;
                throw ie ;
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Returns public no-arg constructor of given class, or null if none found.
     * Access checks are disabled on the returned constructor (if any), since
     * the defining class may still be non-public.
     */
    private static Constructor getExternalizableConstructor(Class cl) {
        try {
            Constructor cons = cl.getDeclaredConstructor(new Class[0]);
            cons.setAccessible(true);
            return ((cons.getModifiers() & Modifier.PUBLIC) != 0) ?
                cons : null;
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    /**
     * Returns subclass-accessible no-arg constructor of first non-serializable
     * superclass, or null if none found.  Access checks are disabled on the
     * returned constructor (if any).
     */
    private static Constructor getSerializableConstructor(Class cl) {
        Class initCl = cl;
        while (Serializable.class.isAssignableFrom(initCl)) {
            if ((initCl = initCl.getSuperclass()) == null) {
                return null;
            }
        }
        try {
            Constructor cons = initCl.getDeclaredConstructor(new Class[0]);
            int mods = cons.getModifiers();
            if ((mods & Modifier.PRIVATE) != 0 ||
                ((mods & (Modifier.PUBLIC | Modifier.PROTECTED)) == 0 &&
                 !packageEquals(cl, initCl)))
            {
                return null;
            }
            cons = bridge.newConstructorForSerialization(cl, cons);
            cons.setAccessible(true);
            return cons;
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    /*
     * Return the ObjectStreamClass of the local class this one is based on.
     */
    final ObjectStreamClass localClassDescriptor() {
        return localClassDesc;
    }

    /*
     * Get the Serializability of the class.
     */
    boolean isSerializable() {
        return serializable;
    }

    /*
     * Get the externalizability of the class.
     */
    boolean isExternalizable() {
        return externalizable;
    }

    boolean isNonSerializable() {
        return ! (externalizable || serializable);
    }

    /*
     * Calculate the size of the array needed to store primitive data and the
     * number of object references to read when reading from the input
     * stream.
     */
    private void computeFieldInfo() {
        primBytes = 0;
        objFields = 0;

        for (int i = 0; i < fields.length; i++ ) {
            switch (fields[i].getTypeCode()) {
            case 'B':
            case 'Z':
                primBytes += 1;
                break;
            case 'C':
            case 'S':
                primBytes += 2;
                break;

            case 'I':
            case 'F':
                primBytes += 4;
                break;
            case 'J':
            case 'D' :
                primBytes += 8;
                break;

            case 'L':
            case '[':
                objFields += 1;
                break;
            }
        }
    }

    private static void msg( String str )
    {
        System.out.println( str ) ;
    }

    /* JDK 1.5 has introduced some new modifier bits (such as SYNTHETIC)
     * that can affect the SVUID computation (see bug 4897937).  These bits
     * must be ignored, as otherwise interoperability with ORBs in earlier
     * JDK versions can be compromised.  I am adding these masks for this
     * purpose as discussed in the CCC for this bug (see http://ccc.sfbay/4897937).
     */

    public static final int CLASS_MASK = Modifier.PUBLIC | Modifier.FINAL |
        Modifier.INTERFACE | Modifier.ABSTRACT ;
    public static final int FIELD_MASK = Modifier.PUBLIC | Modifier.PRIVATE |
        Modifier.PROTECTED | Modifier.STATIC | Modifier.FINAL |
        Modifier.TRANSIENT | Modifier.VOLATILE ;
    public static final int METHOD_MASK = Modifier.PUBLIC | Modifier.PRIVATE |
        Modifier.PROTECTED | Modifier.STATIC | Modifier.FINAL |
        Modifier.SYNCHRONIZED | Modifier.NATIVE | Modifier.ABSTRACT |
        Modifier.STRICT ;

    /*
     * Compute a hash for the specified class.  Incrementally add
     * items to the hash accumulating in the digest stream.
     * Fold the hash into a long.  Use the SHA secure hash function.
     */
    private static long _computeSerialVersionUID(Class cl) {
        if (DEBUG_SVUID)
            msg( "Computing SerialVersionUID for " + cl ) ;
        ByteArrayOutputStream devnull = new ByteArrayOutputStream(512);

        long h = 0;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA");
            DigestOutputStream mdo = new DigestOutputStream(devnull, md);
            DataOutputStream data = new DataOutputStream(mdo);

            if (DEBUG_SVUID)
                msg( "\twriteUTF( \"" + cl.getName() + "\" )" ) ;
            data.writeUTF(cl.getName());

            int classaccess = cl.getModifiers();
            classaccess &= (Modifier.PUBLIC | Modifier.FINAL |
                            Modifier.INTERFACE | Modifier.ABSTRACT);

            /* Workaround for javac bug that only set ABSTRACT for
             * interfaces if the interface had some methods.
             * The ABSTRACT bit reflects that the number of methods > 0.
             * This is required so correct hashes can be computed
             * for existing class files.
             * Previously this hack was previously present in the VM.
             */
            Method[] method = cl.getDeclaredMethods();
            if ((classaccess & Modifier.INTERFACE) != 0) {
                classaccess &= (~Modifier.ABSTRACT);
                if (method.length > 0) {
                    classaccess |= Modifier.ABSTRACT;
                }
            }

            // Mask out any post-1.4 attributes
            classaccess &= CLASS_MASK ;

            if (DEBUG_SVUID)
                msg( "\twriteInt( " + classaccess + " ) " ) ;
            data.writeInt(classaccess);

            /*
             * Get the list of interfaces supported,
             * Accumulate their names their names in Lexical order
             * and add them to the hash
             */
            if (!cl.isArray()) {
                /* In 1.2fcs, getInterfaces() was modified to return
                 * {java.lang.Cloneable, java.io.Serializable} when
                 * called on array classes.  These values would upset
                 * the computation of the hash, so we explicitly omit
                 * them from its computation.
                 */

                Class interfaces[] = cl.getInterfaces();
                Arrays.sort(interfaces, compareClassByName);

                for (int i = 0; i < interfaces.length; i++) {
                    if (DEBUG_SVUID)
                        msg( "\twriteUTF( \"" + interfaces[i].getName() + "\" ) " ) ;
                    data.writeUTF(interfaces[i].getName());
                }
            }

            /* Sort the field names to get a deterministic order */
            Field[] field = cl.getDeclaredFields();
            Arrays.sort(field, compareMemberByName);

            for (int i = 0; i < field.length; i++) {
                Field f = field[i];

                /* Include in the hash all fields except those that are
                 * private transient and private static.
                 */
                int m = f.getModifiers();
                if (Modifier.isPrivate(m) &&
                    (Modifier.isTransient(m) || Modifier.isStatic(m)))
                    continue;

                if (DEBUG_SVUID)
                    msg( "\twriteUTF( \"" + f.getName() + "\" ) " ) ;
                data.writeUTF(f.getName());

                // Mask out any post-1.4 bits
                m &= FIELD_MASK ;

                if (DEBUG_SVUID)
                    msg( "\twriteInt( " + m + " ) " ) ;
                data.writeInt(m);

                if (DEBUG_SVUID)
                    msg( "\twriteUTF( \"" + getSignature(f.getType()) + "\" ) " ) ;
                data.writeUTF(getSignature(f.getType()));
            }

            if (hasStaticInitializer(cl)) {
                if (DEBUG_SVUID)
                    msg( "\twriteUTF( \"<clinit>\" ) " ) ;
                data.writeUTF("<clinit>");

                if (DEBUG_SVUID)
                    msg( "\twriteInt( " + Modifier.STATIC + " )" ) ;
                data.writeInt(Modifier.STATIC); // TBD: what modifiers does it have

                if (DEBUG_SVUID)
                    msg( "\twriteUTF( \"()V\" )" ) ;
                data.writeUTF("()V");
            }

            /*
             * Get the list of constructors including name and signature
             * Sort lexically, add all except the private constructors
             * to the hash with their access flags
             */

            MethodSignature[] constructors =
                MethodSignature.removePrivateAndSort(cl.getDeclaredConstructors());
            for (int i = 0; i < constructors.length; i++) {
                MethodSignature c = constructors[i];
                String mname = "<init>";
                String desc = c.signature;
                desc = desc.replace('/', '.');
                if (DEBUG_SVUID)
                    msg( "\twriteUTF( \"" + mname + "\" )" ) ;
                data.writeUTF(mname);

                // mask out post-1.4 modifiers
                int modifier = c.member.getModifiers() & METHOD_MASK ;

                if (DEBUG_SVUID)
                    msg( "\twriteInt( " + modifier + " ) " ) ;
                data.writeInt( modifier ) ;

                if (DEBUG_SVUID)
                    msg( "\twriteUTF( \"" + desc+ "\" )" ) ;
                data.writeUTF(desc);
            }

            /* Include in the hash all methods except those that are
             * private transient and private static.
             */
            MethodSignature[] methods =
                MethodSignature.removePrivateAndSort(method);
            for (int i = 0; i < methods.length; i++ ) {
                MethodSignature m = methods[i];
                String desc = m.signature;
                desc = desc.replace('/', '.');

                if (DEBUG_SVUID)
                    msg( "\twriteUTF( \"" + m.member.getName()+ "\" )" ) ;
                data.writeUTF(m.member.getName());

                // mask out post-1.4 modifiers
                int modifier = m.member.getModifiers() & METHOD_MASK ;

                if (DEBUG_SVUID)
                    msg( "\twriteInt( " + modifier + " ) " ) ;
                data.writeInt( modifier ) ;

                if (DEBUG_SVUID)
                    msg( "\twriteUTF( \"" + desc + "\" )" ) ;
                data.writeUTF(desc);
            }

            /* Compute the hash value for this class.
             * Use only the first 64 bits of the hash.
             */
            data.flush();
            byte hasharray[] = md.digest();
            for (int i = 0; i < Math.min(8, hasharray.length); i++) {
                h += (long)(hasharray[i] & 255) << (i * 8);
            }
        } catch (IOException ignore) {
            /* can't happen, but be deterministic anyway. */
            h = -1;
        } catch (NoSuchAlgorithmException complain) {
            SecurityException se = new SecurityException() ;
            se.initCause( complain ) ;
            throw se ;
        }

        return h;
    }

    private static long computeStructuralUID(com.sun.corba.se.impl.io.ObjectStreamClass osc, Class cl) {
        ByteArrayOutputStream devnull = new ByteArrayOutputStream(512);

        long h = 0;
        try {

            if ((!java.io.Serializable.class.isAssignableFrom(cl)) ||
                (cl.isInterface())){
                return 0;
            }

            if (java.io.Externalizable.class.isAssignableFrom(cl)) {
                return 1;
            }

            MessageDigest md = MessageDigest.getInstance("SHA");
            DigestOutputStream mdo = new DigestOutputStream(devnull, md);
            DataOutputStream data = new DataOutputStream(mdo);

            // Get SUID of parent
            Class parent = cl.getSuperclass();
            if ((parent != null))
            // SerialBug 1; acc. to spec the one for
            // java.lang.object
            // should be computed and put
            //     && (parent != java.lang.Object.class))
            {
                                //data.writeLong(computeSerialVersionUID(null,parent));
                data.writeLong(computeStructuralUID(lookup(parent), parent));
            }

            if (osc.hasWriteObject())
                data.writeInt(2);
            else
                data.writeInt(1);

            // CORBA formal 00-11-03 10.6.2:  For each field of the
            // class that is mapped to IDL, sorted lexicographically
            // by Java field name, in increasing order...
            ObjectStreamField[] field = osc.getFields();
            if (field.length > 1) {
                Arrays.sort(field, compareObjStrFieldsByName);
            }

            // ...Java field name in UTF encoding, field
            // descriptor, as defined by the JVM spec...
            for (int i = 0; i < field.length; i++) {
                data.writeUTF(field[i].getName());
                data.writeUTF(field[i].getSignature());
            }

            /* Compute the hash value for this class.
             * Use only the first 64 bits of the hash.
             */
            data.flush();
            byte hasharray[] = md.digest();
            // int minimum = Math.min(8, hasharray.length);
            // SerialBug 3: SHA computation is wrong; for loop reversed
            //for (int i = minimum; i > 0; i--)
            for (int i = 0; i < Math.min(8, hasharray.length); i++) {
                h += (long)(hasharray[i] & 255) << (i * 8);
            }
        } catch (IOException ignore) {
            /* can't happen, but be deterministic anyway. */
            h = -1;
        } catch (NoSuchAlgorithmException complain) {
            SecurityException se = new SecurityException();
            se.initCause( complain ) ;
            throw se ;
        }
        return h;
    }

    /**
     * Compute the JVM signature for the class.
     */
    static String getSignature(Class clazz) {
        String type = null;
        if (clazz.isArray()) {
            Class cl = clazz;
            int dimensions = 0;
            while (cl.isArray()) {
                dimensions++;
                cl = cl.getComponentType();
            }
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < dimensions; i++) {
                sb.append("[");
            }
            sb.append(getSignature(cl));
            type = sb.toString();
        } else if (clazz.isPrimitive()) {
            if (clazz == Integer.TYPE) {
                type = "I";
            } else if (clazz == Byte.TYPE) {
                type = "B";
            } else if (clazz == Long.TYPE) {
                type = "J";
            } else if (clazz == Float.TYPE) {
                type = "F";
            } else if (clazz == Double.TYPE) {
                type = "D";
            } else if (clazz == Short.TYPE) {
                type = "S";
            } else if (clazz == Character.TYPE) {
                type = "C";
            } else if (clazz == Boolean.TYPE) {
                type = "Z";
            } else if (clazz == Void.TYPE) {
                type = "V";
            }
        } else {
            type = "L" + clazz.getName().replace('.', '/') + ";";
        }
        return type;
    }

    /*
     * Compute the JVM method descriptor for the method.
     */
    static String getSignature(Method meth) {
        StringBuffer sb = new StringBuffer();

        sb.append("(");

        Class[] params = meth.getParameterTypes(); // avoid clone
        for (int j = 0; j < params.length; j++) {
            sb.append(getSignature(params[j]));
        }
        sb.append(")");
        sb.append(getSignature(meth.getReturnType()));
        return sb.toString();
    }

    /*
     * Compute the JVM constructor descriptor for the constructor.
     */
    static String getSignature(Constructor cons) {
        StringBuffer sb = new StringBuffer();

        sb.append("(");

        Class[] params = cons.getParameterTypes(); // avoid clone
        for (int j = 0; j < params.length; j++) {
            sb.append(getSignature(params[j]));
        }
        sb.append(")V");
        return sb.toString();
    }

    /*
     * Cache of Class -> ClassDescriptor Mappings.
     */
    static private ObjectStreamClassEntry[] descriptorFor = new ObjectStreamClassEntry[61];

    /*
     * findDescriptorFor a Class.  This looks in the cache for a
     * mapping from Class -> ObjectStreamClass mappings.  The hashCode
     * of the Class is used for the lookup since the Class is the key.
     * The entries are extended from java.lang.ref.SoftReference so the
     * gc will be able to free them if needed.
     */
    private static ObjectStreamClass findDescriptorFor(Class cl) {

        int hash = cl.hashCode();
        int index = (hash & 0x7FFFFFFF) % descriptorFor.length;
        ObjectStreamClassEntry e;
        ObjectStreamClassEntry prev;

        /* Free any initial entries whose refs have been cleared */
        while ((e = descriptorFor[index]) != null && e.get() == null) {
            descriptorFor[index] = e.next;
        }

        /* Traverse the chain looking for a descriptor with ofClass == cl.
         * unlink entries that are unresolved.
         */
        prev = e;
        while (e != null ) {
            ObjectStreamClass desc = (ObjectStreamClass)(e.get());
            if (desc == null) {
                // This entry has been cleared,  unlink it
                prev.next = e.next;
            } else {
                if (desc.ofClass == cl)
                    return desc;
                prev = e;
            }
            e = e.next;
        }
        return null;
    }

    /*
     * insertDescriptorFor a Class -> ObjectStreamClass mapping.
     */
    private static void insertDescriptorFor(ObjectStreamClass desc) {
        // Make sure not already present
        if (findDescriptorFor(desc.ofClass) != null) {
            return;
        }

        int hash = desc.ofClass.hashCode();
        int index = (hash & 0x7FFFFFFF) % descriptorFor.length;
        ObjectStreamClassEntry e = new ObjectStreamClassEntry(desc);
        e.next = descriptorFor[index];
        descriptorFor[index] = e;
    }

    private static Field[] getDeclaredFields(final Class clz) {
        return (Field[]) AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                return clz.getDeclaredFields();
            }
        });
    }


    /*
     * The name of this descriptor
     */
    private String name;

    /*
     * The descriptor of the supertype.
     */
    private ObjectStreamClass superclass;

    /*
     * Flags for Serializable and Externalizable.
     */
    private boolean serializable;
    private boolean externalizable;

    /*
     * Array of persistent fields of this class, sorted by
     * type and name.
     */
    private ObjectStreamField[] fields;

    /*
     * Class that is a descriptor for in this virtual machine.
     */
    private Class ofClass;

    /*
     * True if descriptor for a proxy class.
     */
    boolean forProxyClass;


    /*
     * SerialVersionUID for this class.
     */
    private long suid = kDefaultUID;
    private String suidStr = null;

    /*
     * Actual (computed) SerialVersionUID for this class.
     */
    private long actualSuid = kDefaultUID;
    private String actualSuidStr = null;

    /*
     * The total number of bytes of primitive fields.
     * The total number of object fields.
     */
    int primBytes;
    int objFields;

    /**
     * Flag indicating whether or not this instance has
     * successfully completed initialization.  This is to
     * try to fix bug 4373844.  Working to move to
     * reusing java.io.ObjectStreamClass for JDK 1.5.
     */
    private boolean initialized = false;

    /* Internal lock object. */
    private Object lock = new Object();

    /* In JDK 1.1, external data was not written in block mode.
     * As of JDK 1.2, external data is written in block data mode. This
     * flag enables JDK 1.2 to be able to read JDK 1.1 written external data.
     *
     * @since JDK 1.2
     */
    private boolean hasExternalizableBlockData;
    Method writeObjectMethod;
    Method readObjectMethod;
    private Method writeReplaceObjectMethod;
    private Method readResolveObjectMethod;
    private Constructor cons ;

    /**
     * Beginning in Java to IDL ptc/02-01-12, RMI-IIOP has a
     * stream format version 2 which puts a fake valuetype around
     * a Serializable's optional custom data.  This valuetype has
     * a special repository ID made from the Serializable's
     * information which we are pre-computing and
     * storing here.
     */
    private String rmiiiopOptionalDataRepId = null;

    /*
     * ObjectStreamClass that this one was built from.
     */
    private ObjectStreamClass localClassDesc;

    /* Find out if the class has a static class initializer <clinit> */
    private static Method hasStaticInitializerMethod = null;
    /**
     * Returns true if the given class defines a static initializer method,
     * false otherwise.
     */
    private static boolean hasStaticInitializer(Class cl) {
        if (hasStaticInitializerMethod == null) {
            Class classWithThisMethod = null;

            try {
                try {
                    // When using rip-int with Merlin or when this is a Merlin
                    // workspace, the method we want is in sun.misc.ClassReflector
                    // and absent from java.io.ObjectStreamClass.
                    //
                    // When compiling rip-int with JDK 1.3.x, we have to get it
                    // from java.io.ObjectStreamClass.
                    classWithThisMethod = Class.forName("sun.misc.ClassReflector");
                } catch (ClassNotFoundException cnfe) {
                    // Do nothing.  This is either not a Merlin workspace,
                    // or rip-int is being compiled with something other than
                    // Merlin, probably JDK 1.3.  Fall back on java.io.ObjectStreaClass.
                }
                if (classWithThisMethod == null)
                    classWithThisMethod = java.io.ObjectStreamClass.class;

                hasStaticInitializerMethod =
                    classWithThisMethod.getDeclaredMethod("hasStaticInitializer",
                                                          new Class[] { Class.class });
            } catch (NoSuchMethodException ex) {
            }

            if (hasStaticInitializerMethod == null) {
                // XXX I18N, logging needed
                throw new InternalError("Can't find hasStaticInitializer method on "
                                        + classWithThisMethod.getName());
            }
            hasStaticInitializerMethod.setAccessible(true);
        }

        try {
            Boolean retval = (Boolean)
                hasStaticInitializerMethod.invoke(null, new Object[] { cl });
            return retval.booleanValue();
        } catch (Exception ex) {
            // XXX I18N, logging needed
            InternalError ie = new InternalError( "Error invoking hasStaticInitializer" ) ;
            ie.initCause( ex ) ;
            throw ie ;
        }
    }


    /* The Class Object for java.io.Serializable */
    private static Class classSerializable = null;
    private static Class classExternalizable = null;

    /*
     * Resolve java.io.Serializable at load time.
     */
    static {
        try {
            classSerializable = Class.forName("java.io.Serializable");
            classExternalizable = Class.forName("java.io.Externalizable");
        } catch (Throwable e) {
            System.err.println("Could not load java.io.Serializable or java.io.Externalizable.");
        }
    }

    /** use serialVersionUID from JDK 1.1. for interoperability */
    private static final long serialVersionUID = -6120832682080437368L;

    /**
     * Set serialPersistentFields of a Serializable class to this value to
     * denote that the class has no Serializable fields.
     */
    public static final ObjectStreamField[] NO_FIELDS =
        new ObjectStreamField[0];

    /*
     * Entries held in the Cache of known ObjectStreamClass objects.
     * Entries are chained together with the same hash value (modulo array size).
     */
    private static class ObjectStreamClassEntry // extends java.lang.ref.SoftReference
    {
        ObjectStreamClassEntry(ObjectStreamClass c) {
            //super(c);
            this.c = c;
        }
        ObjectStreamClassEntry next;

        public Object get()
        {
            return c;
        }
        private ObjectStreamClass c;
    }

    /*
     * Comparator object for Classes and Interfaces
     */
    private static Comparator compareClassByName =
        new CompareClassByName();

    private static class CompareClassByName implements Comparator {
        public int compare(Object o1, Object o2) {
            Class c1 = (Class)o1;
            Class c2 = (Class)o2;
            return (c1.getName()).compareTo(c2.getName());
        }
    }

    /**
     * Comparator for ObjectStreamFields by name
     */
    private final static Comparator compareObjStrFieldsByName
        = new CompareObjStrFieldsByName();

    private static class CompareObjStrFieldsByName implements Comparator {
        public int compare(Object o1, Object o2) {
            ObjectStreamField osf1 = (ObjectStreamField)o1;
            ObjectStreamField osf2 = (ObjectStreamField)o2;

            return osf1.getName().compareTo(osf2.getName());
        }
    }

    /*
     * Comparator object for Members, Fields, and Methods
     */
    private static Comparator compareMemberByName =
        new CompareMemberByName();

    private static class CompareMemberByName implements Comparator {
        public int compare(Object o1, Object o2) {
            String s1 = ((Member)o1).getName();
            String s2 = ((Member)o2).getName();

            if (o1 instanceof Method) {
                s1 += getSignature((Method)o1);
                s2 += getSignature((Method)o2);
            } else if (o1 instanceof Constructor) {
                s1 += getSignature((Constructor)o1);
                s2 += getSignature((Constructor)o2);
            }
            return s1.compareTo(s2);
        }
    }

    /* It is expensive to recompute a method or constructor signature
       many times, so compute it only once using this data structure. */
    private static class MethodSignature implements Comparator {
        Member member;
        String signature;      // cached parameter signature

        /* Given an array of Method or Constructor members,
           return a sorted array of the non-private members.*/
        /* A better implementation would be to implement the returned data
           structure as an insertion sorted link list.*/
        static MethodSignature[] removePrivateAndSort(Member[] m) {
            int numNonPrivate = 0;
            for (int i = 0; i < m.length; i++) {
                if (! Modifier.isPrivate(m[i].getModifiers())) {
                    numNonPrivate++;
                }
            }
            MethodSignature[] cm = new MethodSignature[numNonPrivate];
            int cmi = 0;
            for (int i = 0; i < m.length; i++) {
                if (! Modifier.isPrivate(m[i].getModifiers())) {
                    cm[cmi] = new MethodSignature(m[i]);
                    cmi++;
                }
            }
            if (cmi > 0)
                Arrays.sort(cm, cm[0]);
            return cm;
        }

        /* Assumes that o1 and o2 are either both methods
           or both constructors.*/
        public int compare(Object o1, Object o2) {
            /* Arrays.sort calls compare when o1 and o2 are equal.*/
            if (o1 == o2)
                return 0;

            MethodSignature c1 = (MethodSignature)o1;
            MethodSignature c2 = (MethodSignature)o2;

            int result;
            if (isConstructor()) {
                result = c1.signature.compareTo(c2.signature);
            } else { // is a Method.
                result = c1.member.getName().compareTo(c2.member.getName());
                if (result == 0)
                    result = c1.signature.compareTo(c2.signature);
            }
            return result;
        }

        final private boolean isConstructor() {
            return member instanceof Constructor;
        }
        private MethodSignature(Member m) {
            member = m;
            if (isConstructor()) {
                signature = ObjectStreamClass.getSignature((Constructor)m);
            } else {
                signature = ObjectStreamClass.getSignature((Method)m);
            }
        }
    }

    /**
     * Returns non-static, non-abstract method with given signature provided it
     * is defined by or accessible (via inheritance) by the given class, or
     * null if no match found.  Access checks are disabled on the returned
     * method (if any).
     *
     * Copied from the Merlin java.io.ObjectStreamClass.
     */
    private static Method getInheritableMethod(Class cl, String name,
                                               Class[] argTypes,
                                               Class returnType)
    {
        Method meth = null;
        Class defCl = cl;
        while (defCl != null) {
            try {
                meth = defCl.getDeclaredMethod(name, argTypes);
                break;
            } catch (NoSuchMethodException ex) {
                defCl = defCl.getSuperclass();
            }
        }

        if ((meth == null) || (meth.getReturnType() != returnType)) {
            return null;
        }
        meth.setAccessible(true);
        int mods = meth.getModifiers();
        if ((mods & (Modifier.STATIC | Modifier.ABSTRACT)) != 0) {
            return null;
        } else if ((mods & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0) {
            return meth;
        } else if ((mods & Modifier.PRIVATE) != 0) {
            return (cl == defCl) ? meth : null;
        } else {
            return packageEquals(cl, defCl) ? meth : null;
        }
    }

    /**
     * Returns true if classes are defined in the same package, false
     * otherwise.
     *
     * Copied from the Merlin java.io.ObjectStreamClass.
     */
    private static boolean packageEquals(Class cl1, Class cl2) {
        Package pkg1 = cl1.getPackage(), pkg2 = cl2.getPackage();
        return ((pkg1 == pkg2) || ((pkg1 != null) && (pkg1.equals(pkg2))));
    }
}
