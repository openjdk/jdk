/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6852574
 * @summary Tests Enum subclass encoding
 * @author Sergey Malenkov
 */

public final class Test6852574 extends AbstractTest {
    public static void main(String[] args) {
        new Test6852574().test(true);
    }

    protected Object getObject() {
        return Data.FIRST;
    }

    protected Object getAnotherObject() {
        return Data.SECOND;
    }

    public enum Data {
        FIRST {
            @Override
            public String toString() {
                return "1";
            }
        },
        SECOND {
            @Override
            public String toString() {
                return "2";
            }
        }
    }
}
