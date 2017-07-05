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

/*
 * @test
 * @summary Tests <long> element
 * @author Sergey Malenkov
 */

import java.beans.XMLDecoder;

public final class TestLong extends AbstractTest {
    public static final String XML
            = "<java>\n"
            + " <long>0</long>\n"
            + " <long>127</long>\n"
            + " <long>-128</long>\n"
            + " <long>32767</long>\n"
            + " <long>-32768</long>\n"
            + " <long>2147483647</long>\n"
            + " <long>-2147483648</long>\n"
            + " <long>9223372036854775807</long>\n"
            + " <long>-9223372036854775808</long>\n"
            + "</java>";

    public static void main(String[] args) {
        new TestLong().test(true);
    }

    @Override
    protected void validate(XMLDecoder decoder) {
        validate(0L, decoder.readObject());
        validate((long) Byte.MAX_VALUE, decoder.readObject());
        validate((long) Byte.MIN_VALUE, decoder.readObject());
        validate((long) Short.MAX_VALUE, decoder.readObject());
        validate((long) Short.MIN_VALUE, decoder.readObject());
        validate((long) Integer.MAX_VALUE, decoder.readObject());
        validate((long) Integer.MIN_VALUE, decoder.readObject());
        validate(Long.MAX_VALUE, decoder.readObject());
        validate(Long.MIN_VALUE, decoder.readObject());
    }

    private static void validate(long value, Object object) {
        if (!object.equals(Long.valueOf(value))) {
            throw new Error("long " + value + " expected");
        }
    }
}
