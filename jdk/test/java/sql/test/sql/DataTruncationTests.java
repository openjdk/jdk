/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package test.sql;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.SQLException;
import java.sql.DataTruncation;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DataTruncationTests {

    private final String READ_TRUNCATION = "01004";
    private final String WRITE_TRUNCATION = "22001";
    private final String reason = "Data truncation";
    private final String cause = "java.lang.Throwable: cause";
    private final Throwable t = new Throwable("cause");
    private final Throwable t1 = new Throwable("cause 1");
    private final Throwable t2 = new Throwable("cause 2");
    private final int errorCode = 0;
    private final String[] msgs = {reason, "cause 1", reason,
        reason, "cause 2"};
    private boolean onRead = false;
    private final boolean parameter = false;
    private final int index = 21;
    private final int dataSize = 25;
    private final int transferSize = 10;

    public DataTruncationTests() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @BeforeMethod
    public void setUpMethod() throws Exception {
    }

    @AfterMethod
    public void tearDownMethod() throws Exception {
    }

    /**
     * Create DataTruncation object indicating a truncation on read
     */
    @Test
    public void test() {
        onRead = true;
        DataTruncation e = new DataTruncation(index, parameter, onRead,
                dataSize, transferSize);
        assertTrue(e.getMessage().equals(reason)
                && e.getSQLState().equals(READ_TRUNCATION)
                && e.getCause() == null
                && e.getErrorCode() == errorCode
                && e.getParameter() == parameter
                && e.getRead() == onRead
                && e.getDataSize() == dataSize
                && e.getTransferSize() == transferSize
                && e.getIndex() == index);
    }

    /**
     * Create DataTruncation object indicating a truncation on write
     */
    @Test
    public void test1() {
        onRead = false;
        DataTruncation e = new DataTruncation(index, parameter, onRead,
                dataSize, transferSize);
        assertTrue(e.getMessage().equals(reason)
                && e.getSQLState().equals(WRITE_TRUNCATION)
                && e.getCause() == null
                && e.getErrorCode() == errorCode
                && e.getParameter() == parameter
                && e.getRead() == onRead
                && e.getDataSize() == dataSize
                && e.getTransferSize() == transferSize
                && e.getIndex() == index);
    }

    /**
     * Create DataTruncation object indicating a truncation on read with a
     * Throwable
     */
    @Test
    public void test2() {
        onRead = true;
        DataTruncation e = new DataTruncation(index, parameter, onRead,
                dataSize, transferSize, t);
        assertTrue(e.getMessage().equals(reason)
                && e.getSQLState().equals(READ_TRUNCATION)
                && cause.equals(e.getCause().toString())
                && e.getErrorCode() == errorCode
                && e.getParameter() == parameter
                && e.getRead() == onRead
                && e.getDataSize() == dataSize
                && e.getTransferSize() == transferSize
                && e.getIndex() == index);
    }

    /**
     * Create DataTruncation object indicating a truncation on read with null
     * specified for the Throwable
     */
    @Test
    public void test3() {
        onRead = true;;
        DataTruncation e = new DataTruncation(index, parameter, onRead,
                dataSize, transferSize, null);
        assertTrue(e.getMessage().equals(reason)
                && e.getSQLState().equals(READ_TRUNCATION)
                && e.getCause() == null
                && e.getErrorCode() == errorCode
                && e.getParameter() == parameter
                && e.getRead() == onRead
                && e.getDataSize() == dataSize
                && e.getTransferSize() == transferSize
                && e.getIndex() == index);
    }

    /**
     * Create DataTruncation object indicating a truncation on read and you can
     * pass a -1 for the index
     */
    @Test
    public void test4() {
        onRead = true;
        int negIndex = -1;
        DataTruncation e = new DataTruncation(negIndex, parameter, onRead,
                dataSize, transferSize);
        assertTrue(e.getMessage().equals(reason)
                && e.getSQLState().equals(READ_TRUNCATION)
                && e.getCause() == null
                && e.getErrorCode() == errorCode
                && e.getParameter() == parameter
                && e.getRead() == onRead
                && e.getDataSize() == dataSize
                && e.getTransferSize() == transferSize
                && e.getIndex() == negIndex);
    }

    /**
     * Serialize a DataTruncation and make sure you can read it back properly
     */
    @Test
    public void test5() throws Exception {
        DataTruncation e = new DataTruncation(index, parameter, onRead,
                dataSize, transferSize);
        ObjectOutputStream out
                = new ObjectOutputStream(
                        new FileOutputStream("DataTruncation.ser"));
        out.writeObject(e);
        ObjectInputStream is = new ObjectInputStream(
                new FileInputStream("DataTruncation.ser"));
        DataTruncation ex1 = (DataTruncation) is.readObject();
        assertTrue(e.getMessage().equals(reason)
                && e.getSQLState().equals(READ_TRUNCATION)
                && e.getCause() == null
                && e.getErrorCode() == errorCode
                && e.getParameter() == parameter
                && e.getRead() == onRead
                && e.getDataSize() == dataSize
                && e.getTransferSize() == transferSize
                && e.getIndex() == index);
    }

    /**
     * Validate that the ordering of the returned Exceptions is correct using
     * for-each loop
     */
    @Test
    public void test11() {
        DataTruncation ex = new DataTruncation(index, parameter, onRead,
                dataSize, transferSize, t1);
        DataTruncation ex1 = new DataTruncation(index, parameter, onRead,
                dataSize, transferSize);
        DataTruncation ex2 = new DataTruncation(index, parameter, onRead,
                dataSize, transferSize, t2);
        ex.setNextException(ex1);
        ex.setNextException(ex2);
        int num = 0;
        for (Throwable e : ex) {
            assertTrue(msgs[num++].equals(e.getMessage()));
        }
    }

    /**
     * Validate that the ordering of the returned Exceptions is correct using
     * traditional while loop
     */
    @Test
    public void test12() {
        DataTruncation ex = new DataTruncation(index, parameter, onRead,
                dataSize, transferSize, t1);
        DataTruncation ex1 = new DataTruncation(index, parameter, onRead,
                dataSize, transferSize);
        DataTruncation ex2 = new DataTruncation(index, parameter, onRead,
                dataSize, transferSize, t2);
        ex.setNextException(ex1);
        ex.setNextException(ex2);
        int num = 0;
        SQLException sqe = ex;
        while (sqe != null) {
            assertTrue(msgs[num++].equals(sqe.getMessage()));
            Throwable c = sqe.getCause();
            while (c != null) {
                assertTrue(msgs[num++].equals(c.getMessage()));
                c = c.getCause();
            }
            sqe = sqe.getNextException();
        }
    }
}
