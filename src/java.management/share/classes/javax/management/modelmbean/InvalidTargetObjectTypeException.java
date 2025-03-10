/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;

/**
 * Exception thrown when an invalid target object type is specified.
 *
 *
 * <p>The <b>serialVersionUID</b> of this class is <code>1190536278266811217L</code>.
 *
 * @since 1.5
 */
public class InvalidTargetObjectTypeException  extends Exception
{
    private static final long serialVersionUID = 1190536278266811217L;
    /**
     * @serialField exception Exception Encapsulated {@link Exception}
     */
    private static final ObjectStreamField[] serialPersistentFields =
    {
      new ObjectStreamField("exception", Exception.class)
    };

    /**
     * @serial Encapsulated {@link Exception}
     */
    Exception exception;


    /**
     * Default constructor.
     */
    public InvalidTargetObjectTypeException ()
    {
      super("InvalidTargetObjectTypeException: ");
      exception = null;
    }


    /**
     * Constructor from a string.
     *
     * @param s String value that will be incorporated in the message for
     *    this exception.
     */

    public InvalidTargetObjectTypeException (String s)
    {
      super("InvalidTargetObjectTypeException: " + s);
      exception = null;
    }


    /**
     * Constructor taking an exception and a string.
     *
     * @param e Exception that we may have caught to reissue as an
     *    InvalidTargetObjectTypeException.  The message will be used, and we may want to
     *    consider overriding the printStackTrace() methods to get data
     *    pointing back to original throw stack.
     * @param s String value that will be incorporated in message for
     *    this exception.
     */

    public InvalidTargetObjectTypeException (Exception e, String s)
    {
      super("InvalidTargetObjectTypeException: " +
            s +
            ((e != null)?("\n\t triggered by:" + e.toString()):""));
      exception = e;
    }

    /**
     * Deserializes an {@link InvalidTargetObjectTypeException} from an {@link ObjectInputStream}.
     */
    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
      in.defaultReadObject();
    }


    /**
     * Serializes an {@link InvalidTargetObjectTypeException} to an {@link ObjectOutputStream}.
     */
    private void writeObject(ObjectOutputStream out)
            throws IOException {
      out.defaultWriteObject();
    }
}
