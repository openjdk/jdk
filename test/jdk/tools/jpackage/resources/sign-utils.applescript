(*
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
 *)

on run argv
  set action to (item 1 of argv)

  if action is "run-shell-cmd" then
    runShellCommandInTerminal(item 2 of argv)
  else
    error "Unrecognized command: [" & action & "]"
  end if
end run


on runShellCommandInTerminal(shellCommand)
  waitShellCommandAndCloseTerminalWindow(startShellCommandInTerminal(shellCommand))
end runShellCommandInTerminal


on startShellCommandInTerminal(shellCommand)
  set runningFile to do shell script "mktemp"
  tell application "Terminal"
    set theTab to do script shellCommand & "; rm -f " & quoted form of runningFile & "; exit"
    log theTab
    return {terminalTab: theTab, file: runningFile}
  end tell
end startShellCommandInTerminal


on waitShellCommandAndCloseTerminalWindow(tabWithRunningFile)
  repeat
    try
      do shell script "test -f " & quoted form of file of tabWithRunningFile
      -- shell script is still running
      delay 2
    on error
      -- shell script is done, exit
      exit repeat
    end try
  end repeat
  closeTerminalTab(terminalTab of tabWithRunningFile)
end waitShellCommandAndCloseTerminalWindow


on closeTerminalTab(theTab)
  -- Find the window owning "theTab" tab and close it
  tell application "Terminal"
    repeat with w in windows
      repeat with t in tabs of w
        if theTab is contents of t then
          log "Closing window " & (id of w)
          close w
          return
        end if
      end repeat
    end repeat
  end tell
end closeTerminalTab
