/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

var msi;
if (WScript.Arguments.Count() > 0) {
  msi = WScript.Arguments(0)
} else {
  var shell = new ActiveXObject('WScript.Shell')
  msi = shell.ExpandEnvironmentStrings('%JpMsiFile%')
}

var query = "SELECT `UpgradeCode`, `VersionMin`,`VersionMax`,`Language`,`Attributes`,`Remove`,`ActionProperty` FROM Upgrade WHERE `VersionMax` = NULL"

var installer = new ActiveXObject('WindowsInstaller.Installer');
var database = installer.OpenDatabase(msi, 1)
var view = database.OpenView(query);
view.Execute();

try {
  var record = view.Fetch();
  record.StringData(2) = '2.0.0.3'
  record.IntegerData(5) = 257
  view.Modify(6, record)
  view.Modify(3, record)
  database.Commit();
} finally {
   view.Close();
}
