/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * JDK-8024619: JDBC java.sql.DriverManager is not usable from JS script
 *
 * @test
 * @run
 */

var DriverManager = Java.type("java.sql.DriverManager");
var e = DriverManager.getDrivers();

var driverFound = false;
// check for Nashorn SQL driver
while (e.hasMoreElements()) {
    var driver = e.nextElement();
    if (driver.acceptsURL("jdbc:nashorn:")) {
        driverFound = true;
        break;
    }
}

if (! driverFound) {
    fail("Nashorn JDBC Driver not found!");
}
