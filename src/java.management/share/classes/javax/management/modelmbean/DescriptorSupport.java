/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @author    IBM Corp.
 *
 * Copyright IBM Corp. 1999-2000.  All rights reserved.
 */

package javax.management.modelmbean;

import static com.sun.jmx.defaults.JmxProperties.MODELMBEAN_LOGGER;
import static com.sun.jmx.mbeanserver.Util.cast;
import com.sun.jmx.mbeanserver.Util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;

import java.lang.reflect.Constructor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.lang.System.Logger.Level;

import javax.management.Descriptor;
import javax.management.ImmutableDescriptor;
import javax.management.MBeanException;
import javax.management.RuntimeOperationsException;

/**
 * This class represents the metadata set for a ModelMBean element.  A
 * descriptor is part of the ModelMBeanInfo,
 * ModelMBeanNotificationInfo, ModelMBeanAttributeInfo,
 * ModelMBeanConstructorInfo, and ModelMBeanParameterInfo.
 * <P>
 * A descriptor consists of a collection of fields.  Each field is in
 * fieldname=fieldvalue format.  Field names are not case sensitive,
 * case will be preserved on field values.
 * <P>
 * All field names and values are not predefined. New fields can be
 * defined and added by any program.  Some fields have been predefined
 * for consistency of implementation and support by the
 * ModelMBeanInfo, ModelMBeanAttributeInfo, ModelMBeanConstructorInfo,
 * ModelMBeanNotificationInfo, ModelMBeanOperationInfo and ModelMBean
 * classes.
 *
 * <p>The <b>serialVersionUID</b> of this class is <code>-6292969195866300415L</code>.
 *
 * @since 1.5
 */
public class DescriptorSupport
         implements javax.management.Descriptor
{

    private static final long serialVersionUID = -6292969195866300415L;
    /**
     * @serialField descriptor HashMap The collection of fields representing this descriptor
     */
    private static final ObjectStreamField[] serialPersistentFields =
    {
      new ObjectStreamField("descriptor", HashMap.class)
    };

    /* Spec says that field names are case-insensitive, but that case
       is preserved.  This means that we need to be able to map from a
       name that may differ in case to the actual name that is used in
       the HashMap.  Thus, descriptorMap is a TreeMap with a Comparator
       that ignores case.

       Previous versions of this class had a field called "descriptor"
       of type HashMap where the keys were directly Strings.  This is
       hard to reconcile with the required semantics, so we fabricate
       that field virtually during serialization and deserialization
       but keep the real information in descriptorMap.
    */
    private transient SortedMap<String, Object> descriptorMap;

    private static final String currClass = "DescriptorSupport";


    /**
     * Descriptor default constructor.
     * Default initial descriptor size is 20.  It will grow as needed.<br>
     * Note that the created empty descriptor is not a valid descriptor
     * (the method {@link #isValid isValid} returns <CODE>false</CODE>)
     */
    public DescriptorSupport() {
        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE, "Constructor");
        }
        init(null);
    }

    /**
     * Descriptor constructor.  Takes as parameter the initial
     * capacity of the Map that stores the descriptor fields.
     * Capacity will grow as needed.<br> Note that the created empty
     * descriptor is not a valid descriptor (the method {@link
     * #isValid isValid} returns <CODE>false</CODE>).
     *
     * @param initNumFields The initial capacity of the Map that
     * stores the descriptor fields.
     *
     * @exception RuntimeOperationsException for illegal value for
     * initNumFields (&lt;= 0)
     * @exception MBeanException Wraps a distributed communication Exception.
     */
    public DescriptorSupport(int initNumFields)
            throws MBeanException, RuntimeOperationsException {
        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE,
                    "Descriptor(initNumFields = " + initNumFields + ") " +
                    "Constructor");
        }
        if (initNumFields <= 0) {
            if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
                MODELMBEAN_LOGGER.log(Level.TRACE,
                        "Illegal arguments: initNumFields <= 0");
            }
            final String msg =
                "Descriptor field limit invalid: " + initNumFields;
            final RuntimeException iae = new IllegalArgumentException(msg);
            throw new RuntimeOperationsException(iae, msg);
        }
        init(null);
    }

    /**
     * Descriptor constructor taking a Descriptor as parameter.
     * Creates a new descriptor initialized to the values of the
     * descriptor passed in parameter.
     *
     * @param inDescr the descriptor to be used to initialize the
     * constructed descriptor. If it is null or contains no descriptor
     * fields, an empty Descriptor will be created.
     */
    public DescriptorSupport(DescriptorSupport inDescr) {
        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE,
                    "Descriptor(Descriptor) Constructor");
        }
        if (inDescr == null)
            init(null);
        else
            init(inDescr.descriptorMap);
    }

    /**
     * Constructor taking field names and field values.  Neither array
     * can be null.
     *
     * @param fieldNames String array of field names.  No elements of
     * this array can be null.
     * @param fieldValues Object array of the corresponding field
     * values.  Elements of the array can be null. The
     * <code>fieldValue</code> must be valid for the
     * <code>fieldName</code> (as defined in method {@link #isValid
     * isValid})
     *
     * <p>Note: array sizes of parameters should match. If both arrays
     * are empty, then an empty descriptor is created.</p>
     *
     * @exception RuntimeOperationsException for illegal value for
     * field Names or field Values.  The array lengths must be equal.
     * If the descriptor construction fails for any reason, this
     * exception will be thrown.
     *
     */
    public DescriptorSupport(String[] fieldNames, Object[] fieldValues)
            throws RuntimeOperationsException {
        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE,
                    "Descriptor(fieldNames,fieldObjects) Constructor");
        }

        if ((fieldNames == null) || (fieldValues == null) ||
            (fieldNames.length != fieldValues.length)) {
            if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
                MODELMBEAN_LOGGER.log(Level.TRACE,
                        "Descriptor(fieldNames,fieldObjects)" +
                        " Illegal arguments");
            }

            final String msg =
                "Null or invalid fieldNames or fieldValues";
            final RuntimeException iae = new IllegalArgumentException(msg);
            throw new RuntimeOperationsException(iae, msg);
        }

        /* populate internal structure with fields */
        init(null);
        for (int i=0; i < fieldNames.length; i++) {
            // setField will throw an exception if a fieldName is be null.
            // the fieldName and fieldValue will be validated in setField.
            setField(fieldNames[i], fieldValues[i]);
        }
        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE,
                    "Descriptor(fieldNames,fieldObjects) Exit");
        }
    }

    /**
     * Constructor taking fields in the <i>fieldName=fieldValue</i>
     * format.
     *
     * @param fields String array with each element containing a
     * field name and value.  If this array is null or empty, then the
     * default constructor will be executed. Null strings or empty
     * strings will be ignored.
     *
     * <p>All field values should be Strings.  If the field values are
     * not Strings, the programmer will have to reset or convert these
     * fields correctly.
     *
     * <p>Note: Each string should be of the form
     * <i>fieldName=fieldValue</i>.  The field name
     * ends at the first {@code =} character; for example if the String
     * is {@code a=b=c} then the field name is {@code a} and its value
     * is {@code b=c}.
     *
     * @exception RuntimeOperationsException for illegal value for
     * field Names or field Values.  The field must contain an
     * "=". "=fieldValue", "fieldName", and "fieldValue" are illegal.
     * FieldName cannot be null.  "fieldName=" will cause the value to
     * be null.  If the descriptor construction fails for any reason,
     * this exception will be thrown.
     *
     */
    public DescriptorSupport(String... fields)
    {
        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE,
                    "Descriptor(String... fields) Constructor");
        }
        init(null);
        if (( fields == null ) || ( fields.length == 0))
            return;

        init(null);

        for (int i=0; i < fields.length; i++) {
            if ((fields[i] == null) || (fields[i].isEmpty())) {
                continue;
            }
            int eq_separator = fields[i].indexOf('=');
            if (eq_separator < 0) {
                // illegal if no = or is first character
                if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
                    MODELMBEAN_LOGGER.log(Level.TRACE,
                            "Descriptor(String... fields) " +
                            "Illegal arguments: field does not have " +
                            "'=' as a name and value separator");
                }
                final String msg = "Field in invalid format: no equals sign";
                final RuntimeException iae = new IllegalArgumentException(msg);
                throw new RuntimeOperationsException(iae, msg);
            }

            String fieldName = fields[i].substring(0,eq_separator);
            String fieldValue = null;
            if (eq_separator < fields[i].length()) {
                // = is not in last character
                fieldValue = fields[i].substring(eq_separator+1);
            }

            if (fieldName.isEmpty()) {
                if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
                    MODELMBEAN_LOGGER.log(Level.TRACE,
                            "Descriptor(String... fields) " +
                            "Illegal arguments: fieldName is empty");
                }

                final String msg = "Field in invalid format: no fieldName";
                final RuntimeException iae = new IllegalArgumentException(msg);
                throw new RuntimeOperationsException(iae, msg);
            }

            setField(fieldName,fieldValue);
        }
        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE,
                    "Descriptor(String... fields) Exit");
        }
    }

    private void init(Map<String, ?> initMap) {
        descriptorMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (initMap != null)
            descriptorMap.putAll(initMap);
    }

    // Implementation of the Descriptor interface


    public synchronized Object getFieldValue(String fieldName)
            throws RuntimeOperationsException {

        if ((fieldName == null) || (fieldName.isEmpty())) {
            if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
                MODELMBEAN_LOGGER.log(Level.TRACE,
                        "Illegal arguments: null field name");
            }
            final String msg = "Fieldname requested is null";
            final RuntimeException iae = new IllegalArgumentException(msg);
            throw new RuntimeOperationsException(iae, msg);
        }
        Object retValue = descriptorMap.get(fieldName);
        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE,
                    "getFieldValue(String fieldName = " + fieldName + ") " +
                    "Returns '" + retValue + "'");
        }
        return(retValue);
    }

    public synchronized void setField(String fieldName, Object fieldValue)
            throws RuntimeOperationsException {

        // field name cannot be null or empty
        if ((fieldName == null) || (fieldName.isEmpty())) {
            if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
                MODELMBEAN_LOGGER.log(Level.TRACE,
                        "Illegal arguments: null or empty field name");
            }

            final String msg = "Field name to be set is null or empty";
            final RuntimeException iae = new IllegalArgumentException(msg);
            throw new RuntimeOperationsException(iae, msg);
        }

        if (!validateField(fieldName, fieldValue)) {
            if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
                MODELMBEAN_LOGGER.log(Level.TRACE,
                        "Illegal arguments");
            }

            final String msg =
                "Field value invalid: " + fieldName + "=" + fieldValue;
            final RuntimeException iae = new IllegalArgumentException(msg);
            throw new RuntimeOperationsException(iae, msg);
        }

        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE, "Entry: setting '"
                    + fieldName + "' to '" + fieldValue + "'");
        }

        // Since we do not remove any existing entry with this name,
        // the field will preserve whatever case it had, ignoring
        // any difference there might be in fieldName.
        descriptorMap.put(fieldName, fieldValue);
    }

    public synchronized String[] getFields() {
        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE, "Entry");
        }
        int numberOfEntries = descriptorMap.size();

        String[] responseFields = new String[numberOfEntries];
        Set<Map.Entry<String, Object>> returnedSet = descriptorMap.entrySet();

        int i = 0;

        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE,
                    "Returning " + numberOfEntries + " fields");
        }
        for (Iterator<Map.Entry<String, Object>> iter = returnedSet.iterator();
             iter.hasNext(); i++) {
            Map.Entry<String, Object> currElement = iter.next();

            if (currElement == null) {
                if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
                    MODELMBEAN_LOGGER.log(Level.TRACE,
                            "Element is null");
                }
            } else {
                Object currValue = currElement.getValue();
                if (currValue == null) {
                    responseFields[i] = currElement.getKey() + "=";
                } else {
                    if (currValue instanceof java.lang.String) {
                        responseFields[i] =
                            currElement.getKey() + "=" + currValue.toString();
                    } else {
                        responseFields[i] =
                            currElement.getKey() + "=(" +
                            currValue.toString() + ")";
                    }
                }
            }
        }

        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE, "Exit");
        }

        return responseFields;
    }

    public synchronized String[] getFieldNames() {
        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE, "Entry");
        }
        int numberOfEntries = descriptorMap.size();

        String[] responseFields = new String[numberOfEntries];
        Set<Map.Entry<String, Object>> returnedSet = descriptorMap.entrySet();

        int i = 0;

        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE,
                    "Returning " + numberOfEntries + " fields");
        }

        for (Iterator<Map.Entry<String, Object>> iter = returnedSet.iterator();
             iter.hasNext(); i++) {
            Map.Entry<String, Object> currElement = iter.next();

            if (( currElement == null ) || (currElement.getKey() == null)) {
                if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
                    MODELMBEAN_LOGGER.log(Level.TRACE, "Field is null");
                }
            } else {
                responseFields[i] = currElement.getKey().toString();
            }
        }

        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE, "Exit");
        }

        return responseFields;
    }


    public synchronized Object[] getFieldValues(String... fieldNames) {
        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE, "Entry");
        }
        // if fieldNames == null return all values
        // if fieldNames is String[0] return no values

        final int numberOfEntries =
            (fieldNames == null) ? descriptorMap.size() : fieldNames.length;
        final Object[] responseFields = new Object[numberOfEntries];

        int i = 0;

        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE,
                    "Returning " + numberOfEntries + " fields");
        }

        if (fieldNames == null) {
            for (Object value : descriptorMap.values())
                responseFields[i++] = value;
        } else {
            for (i=0; i < fieldNames.length; i++) {
                if ((fieldNames[i] == null) || (fieldNames[i].isEmpty())) {
                    responseFields[i] = null;
                } else {
                    responseFields[i] = getFieldValue(fieldNames[i]);
                }
            }
        }

        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE, "Exit");
        }

        return responseFields;
    }

    public synchronized void setFields(String[] fieldNames,
                                       Object[] fieldValues)
            throws RuntimeOperationsException {

        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE, "Entry");
        }

        if ((fieldNames == null) || (fieldValues == null) ||
            (fieldNames.length != fieldValues.length)) {
            if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
                MODELMBEAN_LOGGER.log(Level.TRACE,
                        "Illegal arguments");
            }

            final String msg = "fieldNames and fieldValues are null or invalid";
            final RuntimeException iae = new IllegalArgumentException(msg);
            throw new RuntimeOperationsException(iae, msg);
        }

        for (int i=0; i < fieldNames.length; i++) {
            if (( fieldNames[i] == null) || (fieldNames[i].isEmpty())) {
                if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
                    MODELMBEAN_LOGGER.log(Level.TRACE,
                            "Null field name encountered at element " + i);
                }
                final String msg = "fieldNames is null or invalid";
                final RuntimeException iae = new IllegalArgumentException(msg);
                throw new RuntimeOperationsException(iae, msg);
            }
            setField(fieldNames[i], fieldValues[i]);
        }
        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE, "Exit");
        }
    }

    /**
     * Returns a new Descriptor which is a duplicate of the Descriptor.
     *
     * @exception RuntimeOperationsException for illegal value for
     * field Names or field Values.  If the descriptor construction
     * fails for any reason, this exception will be thrown.
     */

    @Override
    public synchronized Object clone() throws RuntimeOperationsException {
        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE, "Entry");
        }
        return(new DescriptorSupport(this));
    }

    public synchronized void removeField(String fieldName) {
        if ((fieldName == null) || (fieldName.isEmpty())) {
            return;
        }

        descriptorMap.remove(fieldName);
    }

    @Override
    public synchronized boolean equals(Object o) {
        if (o == this)
            return true;
        if (! (o instanceof Descriptor))
            return false;
        if (o instanceof ImmutableDescriptor)
            return o.equals(this);
        return new ImmutableDescriptor(descriptorMap).equals(o);
    }

    @Override
    public synchronized int hashCode() {
        final int size = descriptorMap.size();
        // descriptorMap is sorted with a comparator that ignores cases.
        //
        return Util.hashCode(
                descriptorMap.keySet().toArray(new String[size]),
                descriptorMap.values().toArray(new Object[size]));
    }

    /**
     * Returns true if all of the fields have legal values given their
     * names.
     * <P>
     * This implementation does not support  interoperating with a directory
     * or lookup service. Thus, conforming to the specification, no checking is
     * done on the <i>"export"</i> field.
     * <P>
     * Otherwise this implementation returns false if:
     * <UL>
     * <LI> name and descriptorType fieldNames are not defined, or
     * null, or empty, or not String
     * <LI> class, role, getMethod, setMethod fieldNames, if defined,
     * are null or not String
     * <LI> persistPeriod, currencyTimeLimit, lastUpdatedTimeStamp,
     * lastReturnedTimeStamp if defined, are null, or not a Numeric
     * String or not a Numeric Value {@literal >= -1}
     * <LI> log fieldName, if defined, is null, or not a Boolean or
     * not a String with value "t", "f", "true", "false". These String
     * values must not be case sensitive.
     * <LI> visibility fieldName, if defined, is null, or not a
     * Numeric String or a not Numeric Value {@literal >= 1 and <= 4}
     * <LI> severity fieldName, if defined, is null, or not a Numeric
     * String or not a Numeric Value {@literal >= 0 and <= 6}<br>
     * <LI> persistPolicy fieldName, if defined, is null, or not one of
     * the following strings:<br>
     *   "OnUpdate", "OnTimer", "NoMoreOftenThan", "OnUnregister", "Always",
     *   "Never". These String values must not be case sensitive.<br>
     * </UL>
     *
     * @exception RuntimeOperationsException If the validity checking
     * fails for any reason, this exception will be thrown.
     */

    public synchronized boolean isValid() throws RuntimeOperationsException {
        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE, "Entry");
        }
        // verify that the descriptor is valid, by iterating over each field...

        Set<Map.Entry<String, Object>> returnedSet = descriptorMap.entrySet();

        if (returnedSet == null) {   // null descriptor, not valid
            if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
                MODELMBEAN_LOGGER.log(Level.TRACE,
                        "isValid() Returns false (null set)");
            }
            return false;
        }
        // must have a name and descriptor type field
        String thisName = (String)(this.getFieldValue("name"));
        String thisDescType = (String)(getFieldValue("descriptorType"));

        if ((thisName == null) || (thisDescType == null) ||
            (thisName.isEmpty()) || (thisDescType.isEmpty())) {
            return false;
        }

        // According to the descriptor type we validate the fields contained

        for (Map.Entry<String, Object> currElement : returnedSet) {
            if (currElement != null) {
                if (currElement.getValue() != null) {
                    // validate the field valued...
                    if (validateField((currElement.getKey()).toString(),
                                      (currElement.getValue()).toString())) {
                        continue;
                    } else {
                        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
                            MODELMBEAN_LOGGER.log(Level.TRACE,
                                    "Field " + currElement.getKey() + "=" +
                                    currElement.getValue() + " is not valid");
                        }
                        return false;
                    }
                }
            }
        }

        // fell through, all fields OK
        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE,
                    "isValid() Returns true");
        }
        return true;
    }


    // worker routine for isValid()
    // name is not null
    // descriptorType is not null
    // getMethod and setMethod are not null
    // persistPeriod is numeric
    // currencyTimeLimit is numeric
    // lastUpdatedTimeStamp is numeric
    // visibility is 1-4
    // severity is 0-6
    // log is T or F
    // role is not null
    // class is not null
    // lastReturnedTimeStamp is numeric


    private boolean validateField(String fldName, Object fldValue) {
        if ((fldName == null) || (fldName.isEmpty()))
            return false;
        String SfldValue = "";
        boolean isAString = false;
        if ((fldValue != null) && (fldValue instanceof java.lang.String)) {
            SfldValue = (String) fldValue;
            isAString = true;
        }

        boolean nameOrDescriptorType =
            (fldName.equalsIgnoreCase("Name") ||
             fldName.equalsIgnoreCase("DescriptorType"));
        if (nameOrDescriptorType ||
            fldName.equalsIgnoreCase("SetMethod") ||
            fldName.equalsIgnoreCase("GetMethod") ||
            fldName.equalsIgnoreCase("Role") ||
            fldName.equalsIgnoreCase("Class")) {
            if (fldValue == null || !isAString)
                return false;
            if (nameOrDescriptorType && SfldValue.isEmpty())
                return false;
            return true;
        } else if (fldName.equalsIgnoreCase("visibility")) {
            long v;
            if ((fldValue != null) && (isAString)) {
                v = toNumeric(SfldValue);
            } else if (fldValue instanceof java.lang.Integer) {
                v = ((Integer)fldValue).intValue();
            } else return false;

            if (v >= 1 &&  v <= 4)
                return true;
            else
                return false;
        } else if (fldName.equalsIgnoreCase("severity")) {

            long v;
            if ((fldValue != null) && (isAString)) {
                v = toNumeric(SfldValue);
            } else if (fldValue instanceof java.lang.Integer) {
                v = ((Integer)fldValue).intValue();
            } else return false;

            return (v >= 0 && v <= 6);
        } else if (fldName.equalsIgnoreCase("PersistPolicy")) {
            return (((fldValue != null) && (isAString)) &&
                    ( SfldValue.equalsIgnoreCase("OnUpdate") ||
                      SfldValue.equalsIgnoreCase("OnTimer") ||
                      SfldValue.equalsIgnoreCase("NoMoreOftenThan") ||
                      SfldValue.equalsIgnoreCase("Always") ||
                      SfldValue.equalsIgnoreCase("Never") ||
                      SfldValue.equalsIgnoreCase("OnUnregister")));
        } else if (fldName.equalsIgnoreCase("PersistPeriod") ||
                   fldName.equalsIgnoreCase("CurrencyTimeLimit") ||
                   fldName.equalsIgnoreCase("LastUpdatedTimeStamp") ||
                   fldName.equalsIgnoreCase("LastReturnedTimeStamp")) {

            long v;
            if ((fldValue != null) && (isAString)) {
                v = toNumeric(SfldValue);
            } else if (fldValue instanceof java.lang.Number) {
                v = ((Number)fldValue).longValue();
            } else return false;

            return (v >= -1);
        } else if (fldName.equalsIgnoreCase("log")) {
            return ((fldValue instanceof java.lang.Boolean) ||
                    (isAString &&
                     (SfldValue.equalsIgnoreCase("T") ||
                      SfldValue.equalsIgnoreCase("true") ||
                      SfldValue.equalsIgnoreCase("F") ||
                      SfldValue.equalsIgnoreCase("false") )));
        }

        // default to true, it is a field we aren't validating (user etc.)
        return true;
    }

    /**
     * Returns a human readable string representing the
     * descriptor.  The string will be in the format of
     * "fieldName=fieldValue,fieldName2=fieldValue2,..."<br>
     *
     * If there are no fields in the descriptor, then an empty String
     * is returned.<br>
     *
     * If a fieldValue is an object then the toString() method is
     * called on it and its returned value is used as the value for
     * the field enclosed in parenthesis.
     *
     * @exception RuntimeOperationsException for illegal value for
     * field Names or field Values.  If the descriptor string fails
     * for any reason, this exception will be thrown.
     */
    @Override
    public synchronized String toString() {
        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE, "Entry");
        }

        String[] fields = getFields();

        if ((fields == null) || (fields.length == 0)) {
            if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
                MODELMBEAN_LOGGER.log(Level.TRACE, "Empty Descriptor");
            }
            return "";
        }

        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE,
                    "Printing " + fields.length + " fields");
        }

        String respStr = String.join(", ", fields);

        if (MODELMBEAN_LOGGER.isLoggable(Level.TRACE)) {
            MODELMBEAN_LOGGER.log(Level.TRACE, "Exit returning " + respStr);
        }

        return respStr;
    }

    // utility to convert to int, returns -2 if bogus.

    private long toNumeric(String inStr) {
        try {
            return java.lang.Long.parseLong(inStr);
        } catch (Exception e) {
            return -2;
        }
    }


    /**
     * Deserializes a {@link DescriptorSupport} from an {@link
     * ObjectInputStream}.
     */
    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        ObjectInputStream.GetField fields = in.readFields();
        Map<String, Object> descriptor = cast(fields.get("descriptor", null));
        init(null);
        if (descriptor != null) {
            descriptorMap.putAll(descriptor);
        }
    }


    /**
     * Serializes a {@link DescriptorSupport} to an {@link ObjectOutputStream}.
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        ObjectOutputStream.PutField fields = out.putFields();

        /* Purge the field "targetObject" from the DescriptorSupport before
         * serializing since the referenced object is typically not
         * serializable.  We do this here rather than purging the "descriptor"
         * variable below because that HashMap doesn't do case-insensitivity.
         * See CR 6332962.
         */
        SortedMap<String, Object> startMap = descriptorMap;
        if (startMap.containsKey("targetObject")) {
            startMap = new TreeMap<>(descriptorMap);
            startMap.remove("targetObject");
        }

        final HashMap<String, Object> descriptor = new HashMap<>(startMap);
        fields.put("descriptor", descriptor);
        out.writeFields();
    }

}
