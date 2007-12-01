/*
 * Copyright 1996-2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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

package sunw.util;

/**
 * FOR BACKWARD COMPATIBILITY ONLY - DO NOT USE.
 * <p>
 * This is a backwards compatibility class to allow Java Beans that
 * were developed under JDK 1.0.2 to run correctly under JDK 1.1
 * <p>
 * To allow beans development under JDK 1.0.2, JavaSoft delivered three
 * no-op interfaces/classes (sunw.io.Serializable, sunw.util.EventObject
 * and sunw.util.EventListener) that could be downloaded into JDK 1.0.2
 * systems and which would act as placeholders for the real JDK 1.1
 * classes.
 * <p>
 * Now under JDK 1.1 we provide versions of these classes and interfaces
 * that inherit from the real version in java.util and java.io.  These
 * mean that beans developed under JDK 1.0.2 against the sunw.* classes
 * will now continue to work on JDK 1.1 and will (indirectly) inherit
 * from the approrpiate java.* interfaces/classes.
 *
 * @deprecated This is a compatibility type to allow Java Beans that
 * were developed under JDK 1.0.2 to run correctly under JDK 1.1.  The
 * corresponding JDK1.1 type is java.util.EventObject
 *
 * @see java.util.EventObject
 */

public class EventObject extends java.util.EventObject {

    private static final long serialVersionUID = 6723767567830330255L;

    public EventObject(Object source) {
        super(source);
    }

}
