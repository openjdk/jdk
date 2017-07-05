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
 * @summary Tests <class> element
 * @author Sergey Malenkov
 */

import java.beans.XMLDecoder;

public final class TestClass extends AbstractTest {
    public static final String PREFIX = "javax.swing.colorchooser.";
    public static final String INTERFACE = "ColorSelectionModel";
    public static final String PUBLIC_CLASS = "DefaultColorSelectionModel";
    public static final String PRIVATE_CLASS = "DiagramComponent";
    public static final String XML
            = "<java>\n"
            + " <class>" + PREFIX + INTERFACE + "</class>\n"
            + " <class>" + PREFIX + PUBLIC_CLASS + "</class>\n"
            + " <class>" + PREFIX + PRIVATE_CLASS + "</class>\n"
            + "</java>";

    public static void main(String[] args) {
        new TestClass().test(true);
    }

    @Override
    protected void validate(XMLDecoder decoder) {
        validate(PREFIX + INTERFACE, decoder.readObject());
        validate(PREFIX + PUBLIC_CLASS, decoder.readObject());
        validate(PREFIX + PRIVATE_CLASS, decoder.readObject());
    }

    private static void validate(String name, Object object) {
        Class type = (Class) object;
        if (!type.getName().equals(name)) {
            throw new Error(name + " expected");
        }
    }
}
