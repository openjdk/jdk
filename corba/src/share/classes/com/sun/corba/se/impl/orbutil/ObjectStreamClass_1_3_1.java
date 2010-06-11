/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package com.sun.corba.se.impl.orbutil;

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

import org.omg.CORBA.ValueMember;

import com.sun.corba.se.impl.io.ValueUtility;
import com.sun.corba.se.impl.io.ObjectStreamClass;

/**
 * This is duplicated here to preserve the JDK 1.3.1FCS behavior
 * of calculating the OMG hash code incorrectly when serialPersistentFields
 * is used, but some of the fields no longer exist in the class itself.
 *
 * We have to duplicate it since we aren't allowed to modify the
 * com.sun.corba.se.impl.io version further, and can't make it
 * public outside of its package for security reasons.
 */
/**
 * A ObjectStreamClass_1_3_1 describes a class that can be serialized to a stream
 * or a class that was serialized to a stream.  It contains the name
 * and the serialVersionUID of the class.
 * <br>
 * The ObjectStreamClass_1_3_1 for a specific class loaded in this Java VM can
 * be found using the lookup method.
 *
 * @author  Roger Riggs
 * @since   JDK1.1
 */
public class ObjectStreamClass_1_3_1 implements java.io.Serializable {

    public static final long kDefaultUID = -1;

    private static Object noArgsList[] = {};
    private static Class noTypesList[] = {};

    private static Hashtable translatedFields;

    /** Find the descriptor for a class that can be serialized.  Null
     * is returned if the specified class does not implement
     * java.io.Serializable or java.io.Externalizable.
     */
    static final ObjectStreamClass_1_3_1 lookup(Class cl)
    {
        ObjectStreamClass_1_3_1 desc = lookupInternal(cl);
        if (desc.isSerializable() || desc.isExternalizable())
            return desc;
        return null;
    }

    /*
     * Find the class descriptor for the specified class.
     * Package access only so it can be called from ObjectIn/OutStream.
     */
    static ObjectStreamClass_1_3_1 lookupInternal(Class cl)
    {
        /* Synchronize on the hashtable so no two threads will do
         * this at the same time.
         */
        ObjectStreamClass_1_3_1 desc = null;
        synchronized (descriptorFor) {
            /* Find the matching descriptor if it already known */
            desc = findDescriptorFor(cl);
            if (desc != null) {
                return desc;
            }

                /* Check if it's serializable */
                boolean serializable = classSerializable.isAssignableFrom(cl);
                /* If the class is only Serializable,
                 * lookup the descriptor for the superclass.
                 */
                ObjectStreamClass_1_3_1 superdesc = null;
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
            desc = new ObjectStreamClass_1_3_1(cl, superdesc,
                                         serializable, externalizable);
        }
        desc.init();
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
        ObjectStreamClass_1_3_1 theosc = ObjectStreamClass_1_3_1.lookup( clazz );
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
        ObjectStreamClass_1_3_1 theosc = ObjectStreamClass_1_3_1.lookup( clazz );
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

    public boolean hasField(ValueMember field){

        for (int i = 0; i < fields.length; i++){
            try{
                if (fields[i].getName().equals(field.name)) {

                    if (fields[i].getSignature().equals(ValueUtility.getSignature(field)))
                        return true;
                }
            }
            catch(Throwable t){}
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
            }
            catch(Throwable t) {
                throw new RuntimeException(t.getMessage());
            }
        }
        else return value;
    }

    public Object readResolve(Object value) {
        if (readResolveObjectMethod != null) {
            try {
                return readResolveObjectMethod.invoke(value,noArgsList);
            }
            catch(Throwable t) {
                throw new RuntimeException(t.getMessage());
            }
        }
        else return value;
    }

    /**
     * Return a string describing this ObjectStreamClass_1_3_1.
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
     * Create a new ObjectStreamClass_1_3_1 from a loaded class.
     * Don't call this directly, call lookup instead.
     */
    private ObjectStreamClass_1_3_1(java.lang.Class cl, ObjectStreamClass_1_3_1 superdesc,
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
     * separate from the ObjectStreamClass_1_3_1 constructor so that lookupInternal
     * does not have to hold onto a global class descriptor table lock while the
     * class descriptor is being initialized (see bug 4165204).
     */


    private void init() {
      synchronized (lock) {

        final Class cl = ofClass;

        if (fields != null) // already initialized
                return;


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
                        int modifiers = actualfields[i].getModifiers();
                        if (!Modifier.isStatic(modifiers) &&
                            !Modifier.isTransient(modifiers)) {
                            tempFields[numFields++] =
                                new ObjectStreamField(actualfields[i]);
                        }
                    }
                    fields = new ObjectStreamField[numFields];
                    System.arraycopy(tempFields, 0, fields, 0, numFields);

                } else {
                    // For each declared persistent field, look for an actual
                    // reflected Field. If there is one, make sure it's the correct
                    // type and cache it in the ObjectStreamClass_1_3_1 for that field.
                    for (int j = fields.length-1; j >= 0; j--) {
                        try {
                            Field reflField = cl.getDeclaredField(fields[j].getName());
                            if (fields[j].getType() == reflField.getType()) {
                                // reflField.setAccessible(true);
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
                        if (Modifier.isStatic(mods) &&
                            Modifier.isFinal(mods) ) {
                            f.setAccessible(true);
                            suid = f.getLong(cl);
                            // get rid of native code
                            // suid = getSerialVersionUIDField(cl);
                    // SerialBug 2: should be computed after writeObject
                    // actualSuid = computeStructuralUID(cl);
                        } else {
                            suid = ObjectStreamClass.getSerialVersionUID(cl);
                            // SerialBug 2: should be computed after writeObject
                            // actualSuid = computeStructuralUID(cl);
                        }
                    } catch (NoSuchFieldException ex) {
                        suid = ObjectStreamClass.getSerialVersionUID(cl);
                        // SerialBug 2: should be computed after writeObject
                        // actualSuid = computeStructuralUID(cl);
                    } catch (IllegalAccessException ex) {
                        suid = ObjectStreamClass.getSerialVersionUID(cl);
                    }
                }


                try {
                    writeReplaceObjectMethod = cl.getDeclaredMethod("writeReplace", noTypesList);
                    if (Modifier.isStatic(writeReplaceObjectMethod.getModifiers())) {
                        writeReplaceObjectMethod = null;
                    } else {
                        writeReplaceObjectMethod.setAccessible(true);
                    }

                } catch (NoSuchMethodException e2) {

                }

                try {
                    readResolveObjectMethod = cl.getDeclaredMethod("readResolve", noTypesList);
                    if (Modifier.isStatic(readResolveObjectMethod.getModifiers())) {
                       readResolveObjectMethod = null;
                    } else {
                       readResolveObjectMethod.setAccessible(true);
                    }

                } catch (NoSuchMethodException e2) {

                }

                /* Cache lookup of writeObject and readObject for
                 * Serializable classes. (Do not lookup for
                 * Externalizable)
                 */

                if (serializable && !forProxyClass) {

                    /* Look for the writeObject method
                     * Set the accessible flag on it here. ObjectOutputStream
                     * will call it as necessary.
                     */
                    try {
                      Class[] args = {java.io.ObjectOutputStream.class};
                      writeObjectMethod = cl.getDeclaredMethod("writeObject", args);
                      hasWriteObjectMethod = true;
                      int mods = writeObjectMethod.getModifiers();

                      // Method must be private and non-static
                      if (!Modifier.isPrivate(mods) ||
                        Modifier.isStatic(mods)) {
                        writeObjectMethod = null;
                        hasWriteObjectMethod = false;
                      }

                    } catch (NoSuchMethodException e) {
                    }

                    /* Look for the readObject method
                     * set the access override and save the reference for
                     * ObjectInputStream so it can all the method directly.
                     */
                    try {
                      Class[] args = {java.io.ObjectInputStream.class};
                      readObjectMethod = cl.getDeclaredMethod("readObject", args);
                      int mods = readObjectMethod.getModifiers();

                      // Method must be private and non-static
                      if (!Modifier.isPrivate(mods) ||
                        Modifier.isStatic(mods)) {
                        readObjectMethod = null;
                      }
                    } catch (NoSuchMethodException e) {
                    }
                    // Compute the structural UID.  This must be done after the
                    // calculation for writeObject.  Fixed 4/20/2000, eea1
                    // SerialBug 2: to have correct value in RepId
                }
                return null;
            }
          });
        }

        actualSuid = computeStructuralUID(this, cl);
      }

    }

    /*
     * Create an empty ObjectStreamClass_1_3_1 for a class about to be read.
     * This is separate from read so ObjectInputStream can assign the
     * wire handle early, before any nested ObjectStreamClass_1_3_1 might
     * be read.
     */
    ObjectStreamClass_1_3_1(String n, long s) {
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
                Class osfClass = com.sun.corba.se.impl.orbutil.ObjectStreamField.class;

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
            throw new NoSuchFieldException();
        }
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
    final boolean typeEquals(ObjectStreamClass_1_3_1 other) {
        return (suid == other.suid) &&
            compareClassNames(name, other.name, '.');
    }

    /*
     * Return the superclass descriptor of this descriptor.
     */
    final void setSuperclass(ObjectStreamClass_1_3_1 s) {
        superclass = s;
    }

    /*
     * Return the superclass descriptor of this descriptor.
     */
    final ObjectStreamClass_1_3_1 getSuperclass() {
        return superclass;
    }

    /*
     * Return whether the class has a writeObject method
     */
    final boolean hasWriteObject() {
        return hasWriteObjectMethod;
    }

    final boolean isCustomMarshaled() {
        return (hasWriteObject() || isExternalizable());
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

    /*
     * Return the ObjectStreamClass_1_3_1 of the local class this one is based on.
     */
    final ObjectStreamClass_1_3_1 localClassDescriptor() {
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

    private static long computeStructuralUID(ObjectStreamClass_1_3_1 osc, Class cl) {
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

            /* Sort the field names to get a deterministic order */
            // Field[] field = ObjectStreamClass_1_3_1.getDeclaredFields(cl);

            ObjectStreamField[] fields = osc.getFields();

            // Must make sure that the Field array we allocate
            // below is exactly the right size.  Bug fix for
            // 4397133.
            int numNonNullFields = 0;
            for (int i = 0; i < fields.length; i++)
                if (fields[i].getField() != null)
                    numNonNullFields++;

            Field [] field = new java.lang.reflect.Field[numNonNullFields];
            for (int i = 0, fieldNum = 0; i < fields.length; i++) {
                if (fields[i].getField() != null) {
                    field[fieldNum++] = fields[i].getField();
                }
            }

            if (field.length > 1)
                Arrays.sort(field, compareMemberByName);

            for (int i = 0; i < field.length; i++) {
                Field f = field[i];

                                /* Include in the hash all fields except those that are
                                 * transient
                                 */
                int m = f.getModifiers();
                //Serial 6
                //if (Modifier.isTransient(m) || Modifier.isStatic(m))
                // spec reference 00-01-06.pdf, 1.3.5.6, states non-static
                // non-transient, public fields are mapped to Java IDL.
                //
                // Here's the quote from the first paragraph:
                // Java non-static non-transient public fields are mapped to
                // OMG IDL public data members, and other Java fields are
                // not mapped.

                // if (Modifier.isTransient(m) || Modifier.isStatic(m))
                //     continue;

                data.writeUTF(f.getName());
                data.writeUTF(getSignature(f.getType()));
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
            throw new SecurityException(complain.getMessage());
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
    private static ObjectStreamClass_1_3_1 findDescriptorFor(Class cl) {

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
            ObjectStreamClass_1_3_1 desc = (ObjectStreamClass_1_3_1)(e.get());
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
     * insertDescriptorFor a Class -> ObjectStreamClass_1_3_1 mapping.
     */
    private static void insertDescriptorFor(ObjectStreamClass_1_3_1 desc) {
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
    private ObjectStreamClass_1_3_1 superclass;

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

    /* Internal lock object. */
    private Object lock = new Object();

    /* True if this class has/had a writeObject method */
    private boolean hasWriteObjectMethod;

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

    /*
     * ObjectStreamClass_1_3_1 that this one was built from.
     */
    private ObjectStreamClass_1_3_1 localClassDesc;

    /* Get the private static final field for serial version UID */
    // private static native long getSerialVersionUIDField(Class cl);

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
     * Entries held in the Cache of known ObjectStreamClass_1_3_1 objects.
     * Entries are chained together with the same hash value (modulo array size).
     */
    private static class ObjectStreamClassEntry // extends java.lang.ref.SoftReference
    {
        ObjectStreamClassEntry(ObjectStreamClass_1_3_1 c) {
            //super(c);
            this.c = c;
        }
        ObjectStreamClassEntry next;

        public Object get()
        {
            return c;
        }
        private ObjectStreamClass_1_3_1 c;
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
                signature = ObjectStreamClass_1_3_1.getSignature((Constructor)m);
            } else {
                signature = ObjectStreamClass_1_3_1.getSignature((Method)m);
            }
        }
    }
}
