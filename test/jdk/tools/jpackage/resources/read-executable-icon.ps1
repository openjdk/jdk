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
  # Path to input executable file.
  [Parameter(Mandatory=$true)]
  [string]$InputExecutable,

  # Path to BMP file where to save an icon extracted from the input executable.
  [Parameter(Mandatory=$true)]
  [string]$OutputIcon
)

Add-Type -AssemblyName 'System.Drawing'

$Shell32MethodDefinitions = @'
[DllImport("shell32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
public static extern uint ExtractIconEx(string szFileName, int nIconIndex, IntPtr[] phiconLarge, IntPtr[] phiconSmall, uint nIcons);
'@
$Shell32 = Add-Type -MemberDefinition $Shell32MethodDefinitions -Name 'Shell32' -Namespace 'Win32' -PassThru

$IconHandleArray = New-Object IntPtr[] 1 # Allocate IntPtr[1] to recieve HICON
$IconCount = $Shell32::ExtractIconEx($InputExecutable, 0, $IconHandleArray, $null, 1);
if ($IconCount -eq [uint32]::MaxValue) {
  Write-Error "Failed to read icon."
  exit 100
} elseif ($IconCount -ne 0) {
  # Executable has an icon.
  $Icon = [System.Drawing.Icon]::FromHandle($IconHandleArray[0]);
  $Icon.ToBitmap().Save($OutputIcon, [System.Drawing.Imaging.ImageFormat]::Bmp)
} else {
  # Execeutable doesn't have an icon. Empty output icon file.
  $null = New-Item -Force $OutputIcon -ItemType File
}
