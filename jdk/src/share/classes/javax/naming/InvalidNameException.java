/*
 * Copyright 1999 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.naming;

/**
  * This exception indicates that the name being specified does
  * not conform to the naming syntax of a naming system.
  * This exception is thrown by any of the methods that does name
  * parsing (such as those in Context, DirContext, CompositeName and CompoundName).
  * <p>
  * Synchronization and serialization issues that apply to NamingException
  * apply directly here.
  *
  * @author Rosanna Lee
  * @author Scott Seligman
  *
  * @see Context
  * @see javax.naming.directory.DirContext
  * @see CompositeName
  * @see CompoundName
  * @see NameParser
  * @since 1.3
  */

public class InvalidNameException extends NamingException {
    /**
      * Constructs an instance of InvalidNameException using an
      * explanation of the problem.
      * All other fields are initialized to null.
      * @param explanation      A possibly null message explaining the problem.
      * @see java.lang.Throwable#getMessage
      */
    public InvalidNameException(String explanation) {
        super(explanation);
    }

    /**
      * Constructs an instance of InvalidNameException with
      * all fields set to null.
      */
    public InvalidNameException() {
        super();
    }

    /**
     * Use serialVersionUID from JNDI 1.1.1 for interoperability
     */
    private static final long serialVersionUID = -8370672380823801105L;
}
