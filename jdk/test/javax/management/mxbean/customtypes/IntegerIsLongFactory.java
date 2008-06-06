/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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

// IntegerIsLongFactory.java - see CustomTypeTest

package customtypes;

import java.io.InvalidObjectException;
import java.lang.reflect.Type;
import javax.management.openmbean.MXBeanMapping;
import javax.management.openmbean.MXBeanMappingFactory;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.SimpleType;

public class IntegerIsLongFactory implements MXBeanMappingFactory {
    public MXBeanMapping forType(Type t, MXBeanMappingFactory f)
    throws OpenDataException {
        if (t == Integer.class)
            return IntegerIsLongMapping;
        else
            return MXBeanMappingFactory.DEFAULT.forType(t, f);
    }

    private static final MXBeanMapping IntegerIsLongMapping =
            new IntegerIsLongMapping();

    private static class IntegerIsLongMapping extends MXBeanMapping {
        IntegerIsLongMapping() {
            super(Integer.class, SimpleType.STRING);
        }

        public Object fromOpenValue(Object openValue)
        throws InvalidObjectException {
            try {
                return (Long) openValue;
            } catch (Exception e) {
                InvalidObjectException ioe = new InvalidObjectException("oops");
                ioe.initCause(e);
                throw ioe;
            }
        }

        public Object toOpenValue(Object javaValue) throws OpenDataException {
            try {
                Integer i = (Integer) javaValue;
                return new Long((int) i);
            } catch (Exception e) {
                OpenDataException ode = new OpenDataException("oops");
                ode.initCause(e);
                throw ode;
            }
        }
    }
}
