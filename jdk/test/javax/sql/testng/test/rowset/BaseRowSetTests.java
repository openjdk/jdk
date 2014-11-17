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
package test.rowset;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringBufferInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.sql.Ref;
import java.sql.RowId;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.rowset.serial.SerialArray;
import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialClob;
import javax.sql.rowset.serial.SerialRef;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import util.BaseTest;
import util.StubArray;
import util.StubBaseRowSet;
import util.StubBlob;
import util.StubClob;
import util.StubNClob;
import util.StubRef;
import util.StubRowId;
import util.StubSQLXML;
import util.TestRowSetListener;

public class BaseRowSetTests extends BaseTest {

    private StubBaseRowSet brs;
    private StubBaseRowSet brs1;
    private final String query = "SELECT * FROM SUPERHEROS";
    private final String url = "jdbc:derby://localhost:1527/myDB";
    private final String dsName = "jdbc/myDB";
    private final String user = "Bruce Wayne";
    private final String password = "The Dark Knight";
    private final Date aDate = Date.valueOf(LocalDate.now());
    private final Time aTime = Time.valueOf(LocalTime.now());
    private final Timestamp ts = Timestamp.valueOf(LocalDateTime.now());
    private final Calendar cal = Calendar.getInstance();
    private final byte[] bytes = new byte[10];
    private RowId aRowid;
    private Ref aRef;
    private Blob aBlob;
    private Clob aClob;
    private Array aArray;
    private InputStream is;
    private Reader rdr;
    private Map<String, Class<?>> map = new HashMap<>();

    public BaseRowSetTests() {
        brs1 = new StubBaseRowSet();
        is = new StringBufferInputStream(query);
        rdr = new StringReader(query);
        aRowid = new StubRowId();
        try {
            aBlob = new SerialBlob(new StubBlob());
            aClob = new SerialClob(new StubClob());
            aRef = new SerialRef(new StubRef("INTEGER", query));
            aArray = new SerialArray(new StubArray("INTEGER", new Object[1]));
            map.put("SUPERHERO", Class.forName("util.SuperHero"));
        } catch (SQLException | ClassNotFoundException ex) {
            Logger.getLogger(BaseRowSetTests.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @BeforeMethod
    @Override
    public void setUpMethod() throws Exception {
        brs = new StubBaseRowSet();
    }

    /*
     * Validate that getCommand() returns null by default
     */
    @Test
    public void test() {
        assertTrue(brs.getCommand() == null);
    }

    /*
     * Validate that getCommand() returns command specified to setCommand
     */
    @Test
    public void test01() throws Exception {
        brs.setCommand(query);
        assertTrue(brs.getCommand().equals(query));
    }

    /*
     * Validate that getCurrency() returns the correct default value
     */
    @Test
    public void test02() throws Exception {
        assertTrue(brs.getConcurrency() == ResultSet.CONCUR_UPDATABLE);
    }

    /*
     * Validate that getCurrency() returns the correct value
     * after a call to setConcurrency())
     */
    @Test(dataProvider = "concurTypes")
    public void test03(int concurType) throws Exception {
        brs.setConcurrency(concurType);
        assertTrue(brs.getConcurrency() == concurType);
    }

    /*
     * Validate that getCurrency() throws a SQLException for an invalid value
     */
    @Test(expectedExceptions = SQLException.class)
    public void test04() throws Exception {
        brs.setConcurrency(ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    /*
     * Validate that getDataSourceName() returns null by default
     */
    @Test
    public void test05() throws Exception {
        assertTrue(brs.getDataSourceName() == null);
    }

    /*
     * Validate that getDataSourceName() returns the value specified
     * by setDataSourceName() and getUrl() returns null
     */
    @Test
    public void test06() throws Exception {
        brs.setUrl(url);
        brs.setDataSourceName(dsName);
        assertTrue(brs.getDataSourceName().equals(dsName));
        assertTrue(brs.getUrl() == null);
    }

    /*
     * Validate that setDataSourceName() throws a SQLException for an empty
     * String specified for the data source name
     */
    @Test(expectedExceptions = SQLException.class)
    public void test07() throws Exception {
        String dsname = "";
        brs.setDataSourceName(dsname);
    }

    /*
     * Validate that getEscapeProcessing() returns false by default
     */
    @Test
    public void test08() throws Exception {
        assertFalse(brs.getEscapeProcessing());
    }

    /*
     * Validate that getEscapeProcessing() returns value set by
     * setEscapeProcessing()
     */
    @Test(dataProvider = "trueFalse")
    public void test09(boolean val) throws Exception {
        brs.setEscapeProcessing(val);
        assertTrue(brs.getEscapeProcessing() == val);
    }

    /*
     * Validate that getFetchDirection() returns the correct default value
     */
    @Test
    public void test10() throws Exception {
        assertTrue(brs.getFetchDirection() == ResultSet.FETCH_FORWARD);
    }

    /*
     * Validate that getFetchDirection() returns the value set by
     * setFetchDirection()
     */
    @Test(dataProvider = "fetchDirection")
    public void test11(int direction) throws Exception {
        brs.setFetchDirection(direction);
        assertTrue(brs.getFetchDirection() == direction);
    }

    /*
     * Validate that setConcurrency() throws a SQLException for an invalid value
     */
    @Test(expectedExceptions = SQLException.class)
    public void test12() throws Exception {
        brs.setConcurrency(ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    /*
     * Validate that setFetchSize() throws a SQLException for an invalid value
     */
    @Test(expectedExceptions = SQLException.class)
    public void test13() throws Exception {
        brs.setFetchSize(-1);
    }

    /*
     * Validate that setFetchSize() throws a SQLException for a
     * value greater than getMaxRows()
     */
    @Test(expectedExceptions = SQLException.class)
    public void test14() throws Exception {
        brs.setMaxRows(5);
        brs.setFetchSize(brs.getMaxRows() + 1);
    }

    /*
     * Validate that getFetchSize() returns the correct value after
     * setFetchSize() has been called
     */
    @Test
    public void test15() throws Exception {
        int maxRows = 150;
        brs.setFetchSize(0);
        assertTrue(brs.getFetchSize() == 0);
        brs.setFetchSize(100);
        assertTrue(brs.getFetchSize() == 100);
        brs.setMaxRows(maxRows);
        brs.setFetchSize(maxRows);
        assertTrue(brs.getFetchSize() == maxRows);
    }

    /*
     * Validate that setMaxFieldSize() throws a SQLException for an invalid value
     */
    @Test(expectedExceptions = SQLException.class)
    public void test16() throws Exception {
        brs.setMaxFieldSize(-1);
    }

    /*
     * Validate that getMaxFieldSize() returns the value set by
     * setMaxFieldSize()
     */
    @Test
    public void test17() throws Exception {
        brs.setMaxFieldSize(0);
        assertTrue(brs.getMaxFieldSize() == 0);
        brs.setMaxFieldSize(100);
        assertTrue(brs.getMaxFieldSize() == 100);
        brs.setMaxFieldSize(50);
        assertTrue(brs.getMaxFieldSize() == 50);
    }

    /*
     * Validate that isReadOnly() returns value set by
     * setReadOnly()
     */
    @Test(dataProvider = "trueFalse")
    public void test18(boolean val) throws Exception {
        brs.setReadOnly(val);
        assertTrue(brs.isReadOnly() == val);
    }

    /*
     * Validate that getTransactionIsolation() returns value set by
     * setTransactionIsolation()
     */
    @Test(dataProvider = "isolationTypes")
    public void test19(int val) throws Exception {
        brs.setTransactionIsolation(val);
        assertTrue(brs.getTransactionIsolation() == val);
    }

    /*
     * Validate that getType() returns value set by setType()
     */
    @Test(dataProvider = "scrollTypes")
    public void test20(int val) throws Exception {
        brs.setType(val);
        assertTrue(brs.getType() == val);
    }

    /*
     * Validate that getEscapeProcessing() returns value set by
     * setEscapeProcessing()
     */
    @Test(dataProvider = "trueFalse")
    public void test21(boolean val) throws Exception {
        brs.setShowDeleted(val);
        assertTrue(brs.getShowDeleted() == val);
    }

    /*
     * Validate that getTypeMap() returns same value set by
     * setTypeMap()
     */
    @Test()
    public void test22() throws Exception {
        brs.setTypeMap(map);
        assertTrue(brs.getTypeMap().equals(map));
    }

    /*
     * Validate that getUsername() returns same value set by
     * setUsername()
     */
    @Test()
    public void test23() throws Exception {
        brs.setUsername(user);
        assertTrue(brs.getUsername().equals(user));
    }

    /*
     * Validate that getPassword() returns same password set by
     * setPassword()
     */
    @Test()
    public void test24() throws Exception {
        brs.setPassword(password);
        assertTrue(brs.getPassword().equals(password));
    }

    /*
     * Validate that getQueryTimeout() returns same value set by
     * setQueryTimeout() and that 0 is a valid timeout value
     */
    @Test()
    public void test25() throws Exception {
        int timeout = 0;
        brs.setQueryTimeout(timeout);
        assertTrue(brs.getQueryTimeout() == timeout);
    }

    /*
     * Validate that getQueryTimeout() returns same value set by
     * setQueryTimeout() and that 0 is a valid timeout value
     */
    @Test()
    public void test26() throws Exception {
        int timeout = 10000;
        brs.setQueryTimeout(timeout);
        assertTrue(brs.getQueryTimeout() == timeout);
    }

    /*
     * Validate that setQueryTimeout() throws a SQLException for a timeout
     * value < 0
     */
    @Test(expectedExceptions = SQLException.class)
    public void test27() throws Exception {
        brs.setQueryTimeout(-1);
    }

    /*
     * Create a RowSetListener and validate that notifyRowSetChanged is called
     */
    @Test()
    public void test28() throws Exception {
        TestRowSetListener rsl = new TestRowSetListener();
        brs.addRowSetListener(rsl);
        brs.notifyRowSetChanged();
        assertTrue(rsl.isNotified(TestRowSetListener.ROWSET_CHANGED));
    }

    /*
     * Create a RowSetListener and validate that notifyRowChanged is called
     */
    @Test()
    public void test29() throws Exception {
        TestRowSetListener rsl = new TestRowSetListener();
        brs.addRowSetListener(rsl);
        brs.notifyRowChanged();
        assertTrue(rsl.isNotified(TestRowSetListener.ROW_CHANGED));
    }

    /*
     * Create a RowSetListener and validate that notifyCursorMoved is called
     */
    @Test()
    public void test30() throws Exception {
        TestRowSetListener rsl = new TestRowSetListener();
        brs.addRowSetListener(rsl);
        brs.notifyCursorMoved();
        assertTrue(rsl.isNotified(TestRowSetListener.CURSOR_MOVED));
    }

    /*
     * Create a RowSetListener and validate that notifyRowSetChanged,
     * notifyRowChanged() and notifyCursorMoved are called
     */
    @Test()
    public void test31() throws Exception {
        TestRowSetListener rsl = new TestRowSetListener();
        brs.addRowSetListener(rsl);
        brs.notifyRowSetChanged();
        brs.notifyRowChanged();
        brs.notifyCursorMoved();
        assertTrue(rsl.isNotified(
                TestRowSetListener.CURSOR_MOVED | TestRowSetListener.ROWSET_CHANGED
                | TestRowSetListener.ROW_CHANGED));
    }

    /*
     * Create multiple RowSetListeners and validate that notifyRowSetChanged
     * is called on all listeners
     */
    @Test()
    public void test32() throws Exception {
        TestRowSetListener rsl = new TestRowSetListener();
        TestRowSetListener rsl2 = new TestRowSetListener();
        brs.addRowSetListener(rsl);
        brs.addRowSetListener(rsl2);
        brs.notifyRowSetChanged();
        assertTrue(rsl.isNotified(TestRowSetListener.ROWSET_CHANGED));
        assertTrue(rsl2.isNotified(TestRowSetListener.ROWSET_CHANGED));
    }

    /*
     * Create multiple RowSetListeners and validate that notifyRowChanged
     * is called on all listeners
     */
    @Test()
    public void test33() throws Exception {
        TestRowSetListener rsl = new TestRowSetListener();
        TestRowSetListener rsl2 = new TestRowSetListener();
        brs.addRowSetListener(rsl);
        brs.addRowSetListener(rsl2);
        brs.notifyRowChanged();
        assertTrue(rsl.isNotified(TestRowSetListener.ROW_CHANGED));
        assertTrue(rsl2.isNotified(TestRowSetListener.ROW_CHANGED));
    }

    /*
     * Create multiple RowSetListeners and validate that notifyCursorMoved
     * is called on all listeners
     */
    @Test()
    public void test34() throws Exception {
        TestRowSetListener rsl = new TestRowSetListener();
        TestRowSetListener rsl2 = new TestRowSetListener();
        brs.addRowSetListener(rsl);
        brs.addRowSetListener(rsl2);
        brs.notifyCursorMoved();
        assertTrue(rsl.isNotified(TestRowSetListener.CURSOR_MOVED));
        assertTrue(rsl2.isNotified(TestRowSetListener.CURSOR_MOVED));
    }

    /*
     * Create multiple RowSetListeners and validate that notifyRowSetChanged,
     * notifyRowChanged() and notifyCursorMoved are called on all listeners
     */
    @Test()
    public void test35() throws Exception {
        TestRowSetListener rsl = new TestRowSetListener();
        TestRowSetListener rsl2 = new TestRowSetListener();
        brs.addRowSetListener(rsl);
        brs.addRowSetListener(rsl2);
        brs.notifyRowSetChanged();
        brs.notifyRowChanged();
        brs.notifyCursorMoved();
        assertTrue(rsl.isNotified(
                TestRowSetListener.CURSOR_MOVED | TestRowSetListener.ROWSET_CHANGED
                | TestRowSetListener.ROW_CHANGED));
        assertTrue(rsl2.isNotified(
                TestRowSetListener.CURSOR_MOVED | TestRowSetListener.ROWSET_CHANGED
                | TestRowSetListener.ROW_CHANGED));
    }

    /*
     * Create a RowSetListener and validate that notifyRowSetChanged is called,
     * remove the listener, invoke notifyRowSetChanged again and verify the
     * listner is not called
     */
    @Test()
    public void test36() throws Exception {
        TestRowSetListener rsl = new TestRowSetListener();
        brs.addRowSetListener(rsl);
        brs.notifyRowSetChanged();
        assertTrue(rsl.isNotified(TestRowSetListener.ROWSET_CHANGED));
        // Clear the flag indicating the listener has been called
        rsl.resetFlag();
        brs.removeRowSetListener(rsl);
        brs.notifyRowSetChanged();
        assertFalse(rsl.isNotified(TestRowSetListener.ROWSET_CHANGED));
    }

    /*
     * Validate addRowSetListener does not throw an Exception when null is
     * passed as the parameter
     */
    @Test()
    public void test37() throws Exception {
        brs.addRowSetListener(null);
    }

    /*
     * Validate removeRowSetListener does not throw an Exception when null is
     * passed as the parameter
     */
    @Test()
    public void test38() throws Exception {
        brs.removeRowSetListener(null);
    }

    /*
     * Set two parameters and then validate clearParameters() will clear them
     */
    @Test()
    public void test39() throws Exception {
        brs.setInt(1, 1);
        brs.setString(2, query);
        assertTrue(brs.getParams().length == 2);
        brs.clearParameters();
        assertTrue(brs.getParams().length == 0);
    }

    /*
     * Set the base parameters and validate that the value set is
     * the correct type and value
     */
    @Test(dataProvider = "testBaseParameters")
    public void test40(int pos, Object o) throws Exception {
        assertTrue(getParam(pos, o).getClass().isInstance(o));
        assertTrue(o.equals(getParam(pos, o)));
    }

    /*
     * Set the complex parameters and validate that the value set is
     * the correct type
     */
    @Test(dataProvider = "testAdvancedParameters")
    public void test41(int pos, Object o) throws Exception {
        assertTrue(getParam(pos, o).getClass().isInstance(o));
    }

    /*
     * Validate setNull specifying the supported type values
     */
    @Test(dataProvider = "jdbcTypes")
    public void test42(Integer type) throws Exception {
        brs.setNull(1, type);
        assertTrue(checkNullParam(1, type, null));
    }

    /*
     * Validate setNull specifying the supported type values and that
     * typeName is set internally
     */
    @Test(dataProvider = "jdbcTypes")
    public void test43(Integer type) throws Exception {
        brs.setNull(1, type, "SUPERHERO");
        assertTrue(checkNullParam(1, type, "SUPERHERO"));
    }

    /*
     *  Validate that setDate sets the specified Calendar internally
     */
    @Test()
    public void test44() throws Exception {
        brs.setDate(1, aDate, cal);
        assertTrue(checkCalendarParam(1, cal));
    }

    /*
     *  Validate that setTime sets the specified Calendar internally
     */
    @Test()
    public void test45() throws Exception {
        brs.setTime(1, aTime, cal);
        assertTrue(checkCalendarParam(1, cal));
    }

    /*
     *  Validate that setTimestamp sets the specified Calendar internally
     */
    @Test()
    public void test46() throws Exception {
        brs.setTimestamp(1, ts, cal);
        assertTrue(checkCalendarParam(1, cal));
    }

    /*
     * Validate that getURL() returns same value set by
     * setURL()
     */
    @Test()
    public void test47() throws Exception {
        brs.setUrl(url);
        assertTrue(brs.getUrl().equals(url));
    }

    /*
     * Validate that initParams() initializes the parameters
     */
    @Test()
    public void test48() throws Exception {
        brs.setInt(1, 1);
        brs.initParams();
        assertTrue(brs.getParams().length == 0);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */

    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test100() throws Exception {
        brs1.setAsciiStream(1, is);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test101() throws Exception {
        brs1.setAsciiStream("one", is);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test102() throws Exception {
        brs1.setAsciiStream("one", is, query.length());
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test103() throws Exception {
        brs1.setBinaryStream(1, is);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test104() throws Exception {
        brs1.setBinaryStream("one", is);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test105() throws Exception {
        brs1.setBinaryStream("one", is, query.length());
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test106() throws Exception {
        brs1.setBigDecimal("one", BigDecimal.ONE);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test107() throws Exception {
        brs1.setBlob(1, is);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test108() throws Exception {
        brs1.setBlob("one", is);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test109() throws Exception {
        brs1.setBlob("one", is, query.length());
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test110() throws Exception {
        brs1.setBlob("one", aBlob);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test111() throws Exception {
        brs1.setBoolean("one", true);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test112() throws Exception {
        byte b = 1;
        brs1.setByte("one", b);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test113() throws Exception {
        byte b = 1;
        brs1.setBytes("one", bytes);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test114() throws Exception {
        brs1.setCharacterStream("one", rdr, query.length());
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test115() throws Exception {
        brs1.setCharacterStream("one", rdr);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test116() throws Exception {
        brs1.setCharacterStream(1, rdr);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test117() throws Exception {
        brs1.setClob(1, rdr);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test118() throws Exception {
        brs1.setClob("one", rdr);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test119() throws Exception {
        brs1.setClob("one", rdr, query.length());
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test120() throws Exception {
        brs1.setClob("one", aClob);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test121() throws Exception {
        brs1.setDate("one", aDate);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test122() throws Exception {
        brs1.setDate("one", aDate, cal);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test123() throws Exception {
        brs1.setTime("one", aTime);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test124() throws Exception {
        brs1.setTime("one", aTime, cal);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test125() throws Exception {
        brs1.setTimestamp("one", ts);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test126() throws Exception {
        brs1.setTimestamp("one", ts, cal);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test127() throws Exception {
        brs1.setDouble("one", 2.0d);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test128() throws Exception {
        brs1.setFloat("one", 2.0f);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test129() throws Exception {
        brs1.setInt("one", 21);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test130() throws Exception {
        brs1.setLong("one", 21l);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test131() throws Exception {
        brs1.setNCharacterStream("one", rdr, query.length());
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test132() throws Exception {
        brs1.setNCharacterStream("one", rdr);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test133() throws Exception {
        brs1.setNCharacterStream(1, rdr);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test134() throws Exception {
        brs1.setNCharacterStream(1, rdr, query.length());
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test135() throws Exception {
        brs1.setClob("one", rdr);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test136() throws Exception {
        brs1.setClob("one", rdr, query.length());
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test137() throws Exception {
        brs1.setNClob("one", new StubNClob());
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test138() throws Exception {
        brs1.setNClob(1, rdr);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test139() throws Exception {
        brs1.setNClob(1, rdr, query.length());
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test140() throws Exception {
        brs1.setNClob(1, new StubNClob());
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test141() throws Exception {
        brs1.setNString(1, query);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test142() throws Exception {
        brs1.setNull("one", Types.INTEGER);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test143() throws Exception {
        brs1.setNull("one", Types.INTEGER, "my.type");
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test144() throws Exception {
        brs1.setObject("one", query, Types.VARCHAR);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test145() throws Exception {
        brs1.setObject("one", query, Types.VARCHAR, 0);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test146() throws Exception {
        brs1.setObject("one", query);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test147() throws Exception {
        brs1.setRowId("one", aRowid);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test148() throws Exception {
        brs1.setSQLXML("one", new StubSQLXML());
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test149() throws Exception {
        brs1.setSQLXML(1, new StubSQLXML());
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test150() throws Exception {
        brs1.setNString(1, query);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test151() throws Exception {
        brs1.setNString("one", query);
    }

    /*
     * This method is currently not implemented in BaseRowSet and will
     * throw a SQLFeatureNotSupportedException
     */
    @Test(expectedExceptions = SQLFeatureNotSupportedException.class)
    public void test152() throws Exception {
        short val = 21;
        brs1.setShort("one", val);
    }

    /*
     * DataProvider used to specify the value to set and check for
     * methods using transaction isolation types
     */
    @DataProvider(name = "isolationTypes")
    private Object[][] isolationTypes() {
        return new Object[][]{
            {Connection.TRANSACTION_NONE},
            {Connection.TRANSACTION_READ_COMMITTED},
            {Connection.TRANSACTION_READ_UNCOMMITTED},
            {Connection.TRANSACTION_REPEATABLE_READ},
            {Connection.TRANSACTION_SERIALIZABLE}
        };
    }

    /*
     * DataProvider used to specify the value to set and check for the
     * methods for fetch direction
     */
    @DataProvider(name = "fetchDirection")
    private Object[][] fetchDirection() {
        return new Object[][]{
            {ResultSet.FETCH_FORWARD},
            {ResultSet.FETCH_REVERSE},
            {ResultSet.FETCH_UNKNOWN}
        };
    }

    /*
     * DataProvider used to specify the value to set and check for the
     * methods for Concurrency
     */
    @DataProvider(name = "concurTypes")
    private Object[][] concurTypes() {
        return new Object[][]{
            {ResultSet.CONCUR_READ_ONLY},
            {ResultSet.CONCUR_UPDATABLE}
        };
    }

    /*
     * DataProvider used to specify the value to set and check for the
     * methods for Cursor Scroll Type
     */
    @DataProvider(name = "scrollTypes")
    private Object[][] scrollTypes() {
        return new Object[][]{
            {ResultSet.TYPE_FORWARD_ONLY},
            {ResultSet.TYPE_SCROLL_INSENSITIVE},
            {ResultSet.TYPE_SCROLL_SENSITIVE}
        };
    }

    /*
     * DataProvider used to set parameters for basic types that are supported
     */
    @DataProvider(name = "testBaseParameters")
    private Object[][] testBaseParameters() throws SQLException {
        Integer aInt = 1;
        Long aLong = Long.MAX_VALUE;
        Short aShort = Short.MIN_VALUE;
        BigDecimal bd = BigDecimal.ONE;
        Double aDouble = Double.MAX_VALUE;
        Boolean aBoolean = true;
        Float aFloat = 1.5f;
        Byte aByte = 1;

        brs1.clearParameters();
        brs1.setInt(1, aInt);
        brs1.setString(2, query);
        brs1.setLong(3, aLong);
        brs1.setBoolean(4, aBoolean);
        brs1.setShort(5, aShort);
        brs1.setDouble(6, aDouble);
        brs1.setBigDecimal(7, bd);
        brs1.setFloat(8, aFloat);
        brs1.setByte(9, aByte);
        brs1.setDate(10, aDate);
        brs1.setTime(11, aTime);
        brs1.setTimestamp(12, ts);
        brs1.setDate(13, aDate, cal);
        brs1.setTime(14, aTime, cal);
        brs1.setTimestamp(15, ts);
        brs1.setObject(16, query);
        brs1.setObject(17, query, Types.CHAR);
        brs1.setObject(18, query, Types.CHAR, 0);

        return new Object[][]{
            {1, aInt},
            {2, query},
            {3, aLong},
            {4, aBoolean},
            {5, aShort},
            {6, aDouble},
            {7, bd},
            {8, aFloat},
            {9, aByte},
            {10, aDate},
            {11, aTime},
            {12, ts},
            {13, aDate},
            {14, aTime},
            {15, ts},
            {16, query},
            {17, query},
            {18, query}

        };
    }

    /*
     * DataProvider used to set advanced parameters for types that are supported
     */
    @DataProvider(name = "testAdvancedParameters")
    private Object[][] testAdvancedParameters() throws SQLException {

        brs1.clearParameters();
        brs1.setBytes(1, bytes);
        brs1.setAsciiStream(2, is, query.length());
        brs1.setRef(3, aRef);
        brs1.setArray(4, aArray);
        brs1.setBlob(5, aBlob);
        brs1.setClob(6, aClob);
        brs1.setBinaryStream(7, is, query.length());
        brs1.setUnicodeStream(8, is, query.length());
        brs1.setCharacterStream(9, rdr, query.length());

        return new Object[][]{
            {1, bytes},
            {2, is},
            {3, aRef},
            {4, aArray},
            {5, aBlob},
            {6, aClob},
            {7, is},
            {8, is},
            {9, rdr}
        };
    }

    /*
     *  Method that returns the specified parameter instance that was set via setXXX
     *  Note non-basic types are stored as an Object[] where the 1st element
     *  is the object instnace
     */
    @SuppressWarnings("unchecked")
    private <T> T getParam(int pos, T o) throws SQLException {
        Object[] params = brs1.getParams();
        if (params[pos - 1] instanceof Object[]) {
            Object[] param = (Object[]) params[pos - 1];
            return (T) param[0];
        } else {
            return (T) params[pos - 1];
        }
    }

    /*
     * Utility method to validate parameters when the param is an Object[]
     */
    private boolean checkParam(int pos, int type, Object val) throws SQLException {
        boolean result = false;
        Object[] params = brs.getParams();
        if (params[pos - 1] instanceof Object[]) {
            Object[] param = (Object[]) params[pos - 1];

            if (param[0] == null) {
                // setNull was used
                if (param.length == 2 && (Integer) param[1] == type) {
                    result = true;
                } else {
                    if (param.length == 3 && (Integer) param[1] == type
                            && val.equals(param[2])) {
                        result = true;
                    }
                }

            } else if (param[0] instanceof java.util.Date) {
                // setDate/Time/Timestamp with a Calendar object
                if (param[1] instanceof Calendar && val.equals(param[1])) {
                    result = true;
                }
            }
        }
        return result;
    }

    /*
     * Wrapper method for validating that a null was set and the appropriate
     * type and typeName if applicable
     */
    private boolean checkNullParam(int pos, int type, String typeName) throws SQLException {
        return checkParam(pos, type, typeName);
    }

    /*
     *  Wrapper method for validating that a Calander was set
     */
    private boolean checkCalendarParam(int pos, Calendar cal) throws SQLException {
        // 2nd param is ignored when instanceof java.util.Date
        return checkParam(pos, Types.DATE, cal);
    }
}
