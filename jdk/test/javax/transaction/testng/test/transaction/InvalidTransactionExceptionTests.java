/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package test.transaction;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import javax.transaction.InvalidTransactionException;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import util.SerializedTransactionExceptions;

public class InvalidTransactionExceptionTests {

    protected final String reason = "reason";

    /**
     * Create InvalidTransactionException with no-arg constructor
     */
    @Test
    public void test1() {
        InvalidTransactionException ex = new InvalidTransactionException();
        assertTrue(ex.getMessage() == null
                && ex.getCause() == null);
    }

    /**
     * Create InvalidTransactionException with message
     */
    @Test
    public void test2() {
        InvalidTransactionException ex = new InvalidTransactionException(reason);
        assertTrue(ex.getMessage().equals(reason)
                && ex.getCause() == null);
    }

    /**
     * De-Serialize a InvalidTransactionException from JDBC 4.0 and make sure
     * you can read it back properly
     */
    @Test
    public void test3() throws Exception {

        ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(SerializedTransactionExceptions.ITE_DATA));
        InvalidTransactionException ex = (InvalidTransactionException) ois.readObject();
        assertTrue(reason.equals(ex.getMessage())
                && ex.getCause() == null);
    }

}
