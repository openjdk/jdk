/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

import java.beans.ExceptionListener;
import java.beans.XMLDecoder;

import java.io.ByteArrayInputStream;

abstract class AbstractTest implements ExceptionListener {
    public void exceptionThrown(Exception exception) {
        throw new Error("unexpected exception", exception);
    }

    /**
     * Validates the XML decoder for XML archive
     * that defined in the public field of the subclass.
     *
     * @param decoder  the initialized XML decoder
     * @throws Error if validation failed
     */
    protected abstract void validate(XMLDecoder decoder);

    /**
     * This is entry point to start testing.
     *
     * @param security  use {@code true} to start
     *                  second pass in secure context
     */
    final void test(boolean security) {
        byte[] array = getFieldValue("XML").getBytes(); // NON-NLS: the field name
        ByteArrayInputStream input = new ByteArrayInputStream(array);
        XMLDecoder decoder = new XMLDecoder(input);
        decoder.setExceptionListener(this);
        validate(decoder);
        try {
            throw new Error("unexpected object" + decoder.readObject());
        } catch (ArrayIndexOutOfBoundsException exception) {
            // expected exception
        }
        decoder.close();
        if (security) {
            System.setSecurityManager(new SecurityManager());
            test(false);
        }
    }

    private String getFieldValue(String field) {
        try {
            return getClass().getField(field).get(this).toString();
        } catch (NoSuchFieldException exception) {
            throw new Error("unexpected exception", exception);
        } catch (IllegalAccessException exception) {
            throw new Error("unexpected exception", exception);
        }
    }
}
