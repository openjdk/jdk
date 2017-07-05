/*
 * Copyright 2006-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.beans.DefaultPersistenceDelegate;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

abstract class AbstractTest {
    public abstract int getValue();

    public final String toString() {
        return Integer.toString(getValue());
    }

    static void test(AbstractTest object) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        XMLEncoder encoder = new XMLEncoder(output);
        encoder.setPersistenceDelegate(
                object.getClass(),
                new DefaultPersistenceDelegate(new String[] {"value"}));

        encoder.writeObject(object);
        encoder.close();

        System.out.print(output);

        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        XMLDecoder decoder = new XMLDecoder(input);
        AbstractTest result = (AbstractTest) decoder.readObject();
        decoder.close();

        if (object.getValue() != result.getValue())
            throw new Error("Should be " + object);
    }
}
