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
 * @summary Tests <new> element
 * @author Sergey Malenkov
 */

import java.beans.XMLDecoder;
import java.util.ArrayList;
import java.util.List;

public final class TestNew extends AbstractTest {
    public static final String XML
            = "<java>\n"
            + " <new class=\"TestNew\"/>\n"
            + " <new class=\"TestNew\">\n"
            + "  <null/>\n"
            + " </new>\n"
            + " <new class=\"TestNew\">\n"
            + "  <string>single</string>\n"
            + " </new>\n"
            + " <new class=\"TestNew\">\n"
            + "  <string>first</string>\n"
            + "  <string>second</string>\n"
            + "  <string>third</string>\n"
            + " </new>\n"
            + "</java>";

    public static void main(String[] args) {
        new TestNew().test(true);
    }

    private List<String> list;

    public TestNew(String...messages) {
        if (messages != null) {
            this.list = new ArrayList<String>();
            for (String message : messages) {
                this.list.add(message);
            }
        }
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof TestNew) {
            TestNew test = (TestNew) object;
            return (test.list == null)
                    ? this.list == null
                    : test.list.equals(this.list);
        }
        return false;
    }

    @Override
    protected void validate(XMLDecoder decoder) {
        validate(decoder.readObject());
        validate(decoder.readObject(), null);
        validate(decoder.readObject(), "single");
        validate(decoder.readObject(), "first", "second", "third");
    }

    private static void validate(Object object, String...messages) {
        if (!object.equals(new TestNew(messages))) {
            throw new Error("expected object");
        }
    }
}
