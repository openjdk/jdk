/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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


function readMsi(msiPath, callback) {
  var installer = new ActiveXObject('WindowsInstaller.Installer')
  var database = installer.OpenDatabase(msiPath, 0 /* msiOpenDatabaseModeReadOnly */)

  return callback(database)
}


function exportTables(db, outputDir, requestedTableNames) {
  var tables = {}

  var view = db.OpenView("SELECT `Name` FROM _Tables")
  view.Execute()

  try {
    while (true) {
      var record = view.Fetch()
      if (!record) {
          break
      }

      var name = record.StringData(1)

      if (requestedTableNames.hasOwnProperty(name)) {
        tables[name] = name
      }
    }
  } finally {
    view.Close()
  }

  var fso = new ActiveXObject("Scripting.FileSystemObject")
  for (var table in tables) {
    var idtFileName = table + ".idt"
    var idtFile = outputDir + "/" + idtFileName
    if (fso.FileExists(idtFile)) {
      WScript.Echo("Delete [" + idtFile + "]")
      fso.DeleteFile(idtFile)
    }
    WScript.Echo("Export table [" + table + "] in [" + idtFile + "] file")
    db.Export(table, fso.GetFolder(outputDir).Path, idtFileName)
  }
}


(function () {
  var msi = WScript.arguments(0)
  var outputDir = WScript.arguments(1)
  var tables = {}
  for (var i = 0; i !== WScript.arguments.Count(); i++) {
    tables[WScript.arguments(i)] = true
  }

  readMsi(msi, function (db) {
    exportTables(db, outputDir, tables)
  })
})()
