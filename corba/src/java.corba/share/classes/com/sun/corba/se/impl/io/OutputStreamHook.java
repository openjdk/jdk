/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.io;

import java.io.IOException;
import java.io.NotActiveException;
import java.io.OutputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectOutput;
import java.util.Map;
import java.util.HashMap;

import org.omg.CORBA.INTERNAL;

public abstract class OutputStreamHook extends ObjectOutputStream
{
    private HookPutFields putFields = null;

    /**
     * Since ObjectOutputStream.PutField methods specify no exceptions,
     * we are not checking for null parameters on put methods.
     */
    private class HookPutFields extends ObjectOutputStream.PutField
    {
        private Map<String,Object> fields = new HashMap<>();

        /**
         * Put the value of the named boolean field into the persistent field.
         */
        public void put(String name, boolean value){
            fields.put(name, new Boolean(value));
        }

        /**
         * Put the value of the named char field into the persistent fields.
         */
        public void put(String name, char value){
            fields.put(name, new Character(value));
        }

        /**
         * Put the value of the named byte field into the persistent fields.
         */
        public void put(String name, byte value){
            fields.put(name, new Byte(value));
        }

        /**
         * Put the value of the named short field into the persistent fields.
         */
        public void put(String name, short value){
            fields.put(name, new Short(value));
        }

        /**
         * Put the value of the named int field into the persistent fields.
         */
        public void put(String name, int value){
            fields.put(name, new Integer(value));
        }

        /**
         * Put the value of the named long field into the persistent fields.
         */
        public void put(String name, long value){
            fields.put(name, new Long(value));
        }

        /**
         * Put the value of the named float field into the persistent fields.
         *
         */
        public void put(String name, float value){
            fields.put(name, new Float(value));
        }

        /**
         * Put the value of the named double field into the persistent field.
         */
        public void put(String name, double value){
            fields.put(name, new Double(value));
        }

        /**
         * Put the value of the named Object field into the persistent field.
         */
        public void put(String name, Object value){
            fields.put(name, value);
        }

        /**
         * Write the data and fields to the specified ObjectOutput stream.
         */
        public void write(ObjectOutput out) throws IOException {
            OutputStreamHook hook = (OutputStreamHook)out;

            ObjectStreamField[] osfields = hook.getFieldsNoCopy();

            // Write the fields to the stream in the order
            // provided by the ObjectStreamClass.  (They should
            // be sorted appropriately already.)
            for (int i = 0; i < osfields.length; i++) {

                Object value = fields.get(osfields[i].getName());

                hook.writeField(osfields[i], value);
            }
        }
    }

    abstract void writeField(ObjectStreamField field, Object value) throws IOException;

    public OutputStreamHook()
        throws java.io.IOException {
        super();
    }

    public void defaultWriteObject() throws IOException {

        writeObjectState.defaultWriteObject(this);

        defaultWriteObjectDelegate();
    }

    public abstract void defaultWriteObjectDelegate();

    public ObjectOutputStream.PutField putFields()
        throws IOException {
        if (putFields == null) {
            putFields = new HookPutFields();
        }
        return putFields;
    }

    // Stream format version, saved/restored during recursive calls
    protected byte streamFormatVersion = 1;

    // Return the stream format version currently being used
    // to serialize an object
    public byte getStreamFormatVersion() {
        return streamFormatVersion;
    }

    abstract ObjectStreamField[] getFieldsNoCopy();

    // User uses PutFields to simulate default data.
    // See java.io.ObjectOutputStream.PutFields
    public void writeFields()
        throws IOException {

        writeObjectState.defaultWriteObject(this);
        if (putFields != null) {
            putFields.write(this);
        } else {
            throw new NotActiveException("no current PutField object");
        }
    }

    abstract org.omg.CORBA_2_3.portable.OutputStream getOrbStream();

    protected abstract void beginOptionalCustomData();


    // The following is a State pattern implementation of what
    // should be done when a Serializable has a
    // writeObject method.  This was especially necessary for
    // RMI-IIOP stream format version 2.  Please see the
    // state diagrams in the docs directory of the workspace.

    protected WriteObjectState writeObjectState = NOT_IN_WRITE_OBJECT;

    protected void setState(WriteObjectState newState) {
        writeObjectState = newState;
    }

    // Description of possible actions
    protected static class WriteObjectState {
        public void enterWriteObject(OutputStreamHook stream) throws IOException {}
        public void exitWriteObject(OutputStreamHook stream) throws IOException {}
        public void defaultWriteObject(OutputStreamHook stream) throws IOException {}
        public void writeData(OutputStreamHook stream) throws IOException {}
    }

    protected static class DefaultState extends WriteObjectState {
        public void enterWriteObject(OutputStreamHook stream) throws IOException {
            stream.setState(IN_WRITE_OBJECT);
        }
    }

    protected static final WriteObjectState NOT_IN_WRITE_OBJECT = new DefaultState();
    protected static final WriteObjectState IN_WRITE_OBJECT = new InWriteObjectState();
    protected static final WriteObjectState WROTE_DEFAULT_DATA = new WroteDefaultDataState();
    protected static final WriteObjectState WROTE_CUSTOM_DATA = new WroteCustomDataState();

    protected static class InWriteObjectState extends WriteObjectState {

        public void enterWriteObject(OutputStreamHook stream) throws IOException {
            // XXX I18N, logging needed.
            throw new IOException("Internal state failure: Entered writeObject twice");
        }

        public void exitWriteObject(OutputStreamHook stream) throws IOException {

            // We didn't write any data, so write the
            // called defaultWriteObject indicator as false
            stream.getOrbStream().write_boolean(false);

            // If we're in stream format verison 2, we must
            // put the "null" marker to say that there isn't
            // any optional data
            if (stream.getStreamFormatVersion() == 2)
                stream.getOrbStream().write_long(0);

            stream.setState(NOT_IN_WRITE_OBJECT);
        }

        public void defaultWriteObject(OutputStreamHook stream) throws IOException {

            // The writeObject method called defaultWriteObject
            // or writeFields, so put the called defaultWriteObject
            // indicator as true
            stream.getOrbStream().write_boolean(true);

            stream.setState(WROTE_DEFAULT_DATA);
        }

        public void writeData(OutputStreamHook stream) throws IOException {

            // The writeObject method first called a direct
            // write operation.  Write the called defaultWriteObject
            // indicator as false, put the special stream format
            // version 2 header (if stream format version 2, of course),
            // and write the data
            stream.getOrbStream().write_boolean(false);
            stream.beginOptionalCustomData();
            stream.setState(WROTE_CUSTOM_DATA);
        }
    }

    protected static class WroteDefaultDataState extends InWriteObjectState {

        public void exitWriteObject(OutputStreamHook stream) throws IOException {

            // We only wrote default data, so if in stream format
            // version 2, put the null indicator to say that there
            // is no optional data
            if (stream.getStreamFormatVersion() == 2)
                stream.getOrbStream().write_long(0);

            stream.setState(NOT_IN_WRITE_OBJECT);
        }

        public void defaultWriteObject(OutputStreamHook stream) throws IOException {
            // XXX I18N, logging needed.
            throw new IOException("Called defaultWriteObject/writeFields twice");
        }

        public void writeData(OutputStreamHook stream) throws IOException {

            // The writeObject method called a direct write operation.
            // If in stream format version 2, put the fake valuetype
            // header.
            stream.beginOptionalCustomData();

            stream.setState(WROTE_CUSTOM_DATA);
        }
    }

    protected static class WroteCustomDataState extends InWriteObjectState {

        public void exitWriteObject(OutputStreamHook stream) throws IOException {
            // In stream format version 2, we must tell the ORB
            // stream to close the fake custom valuetype.
            if (stream.getStreamFormatVersion() == 2)
                ((org.omg.CORBA.portable.ValueOutputStream)stream.getOrbStream()).end_value();

            stream.setState(NOT_IN_WRITE_OBJECT);
        }

        public void defaultWriteObject(OutputStreamHook stream) throws IOException {
            // XXX I18N, logging needed.
            throw new IOException("Cannot call defaultWriteObject/writeFields after writing custom data in RMI-IIOP");
        }

        // We don't have to do anything special here, just let
        // the stream write the data.
        public void writeData(OutputStreamHook stream) throws IOException {}
    }
}
