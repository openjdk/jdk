/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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


function modifyMsi(msiPath, callback) {
  var installer = new ActiveXObject('WindowsInstaller.Installer')
  var database = installer.OpenDatabase(msiPath, 1 /* msiOpenDatabaseModeTransact */)

  callback(installer, database)

  database.Commit()
}


function disableActions(installer, db, sequence, actionIDs) {
  var tables = {}

  var view = db.OpenView("SELECT `Action`, `Condition`, `Sequence` FROM " + sequence)
  view.Execute()

  try {
    while (true) {
      var record = view.Fetch()
      if (!record) {
          break
      }

      var action = record.StringData(1)

      if (actionIDs.hasOwnProperty(action)) {
        WScript.Echo("Set condition of [" + action + "] action in [" + sequence + "] sequence to [0]")
        var newRecord = installer.CreateRecord(3)
        for (var i = 1; i !== newRecord.FieldCount + 1; i++) {
          newRecord.StringData(i) = record.StringData(i)
        }
        newRecord.StringData(2) = "0" // Set condition value to `0`
        view.Modify(3 /* msiViewModifyAssign */, newRecord) // Replace existing record
      }
    }
  } finally {
    view.Close()
  }
}


(function () {
  var msi = WScript.arguments(0)
  var sequence = WScript.arguments(1)
  var actionIDs = {}
  for (var i = 0; i !== WScript.arguments.Count(); i++) {
    actionIDs[WScript.arguments(i)] = true
  }

  modifyMsi(msi, function (installer, db) {
    disableActions(installer, db, sequence, actionIDs)
  })
})()
