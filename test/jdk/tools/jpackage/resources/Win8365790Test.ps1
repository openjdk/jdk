#
# Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

param (
  # Path to executable to start.
  [Parameter(Mandatory=$true)]
  [string]$Executable,

  # Timeout to wait after the executable has been started.
  [Parameter(Mandatory=$true)]
  [double]$TimeoutSeconds
)

$type = @{
  TypeDefinition = @'
using System;
using System.Runtime.InteropServices;

namespace Stuff {

  internal struct Details {
    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    internal static extern bool GenerateConsoleCtrlEvent(uint dwCtrlEvent, uint dwProcessGroupId);
  }

  public struct Facade {
    public static void GenerateConsoleCtrlEvent() {
      if (!Details.GenerateConsoleCtrlEvent(0, 0)) {
        reportLastErrorAndExit("GenerateConsoleCtrlEvent");
      }
    }

    internal static void reportLastErrorAndExit(String func) {
      int errorCode = Marshal.GetLastWin32Error();
      Console.Error.WriteLine(func + " function failed with error code: " + errorCode);
      Environment.Exit(100);
    }
  }
}
'@
}
Add-Type @type

Set-PSDebug -Trace 2

# Launch the target executable.
# `-NoNewWindow` parameter will attach the started process to the existing console.
$childProc = Start-Process -PassThru -NoNewWindow $Executable

# Wait a bit to let the started process complete initialization.
Start-Sleep -Seconds $TimeoutSeconds

# Call GenerateConsoleCtrlEvent to send a CTRL+C event to the launched executable.
# CTRL+C event will be sent to all processes attached to the console of the current process,
# i.e., it will be sent to this PowerShell process and to the started $Executable process because
# it was configured to attach to the existing console (the console of this PowerShell process).
[Stuff.Facade]::GenerateConsoleCtrlEvent()

# Wait for child process termination
Wait-Process -InputObject $childProc

Exit 0
