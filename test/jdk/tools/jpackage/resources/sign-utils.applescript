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

  if action is "run-shell-script" then
    runShellCommandInTerminal (item 2 of argv)
  else if action is "trust-certs" then
    set certs to {}
    set certFileDir to (item 2 of argv)
    repeat with i from 3 to (length of argv) by 2
      set end of certs to {keychain: item i of argv, cert: certFileDir & "/" & item (i + 1) of argv}
    end repeat
    trustCerts(certs)
  else
    error "Unrecognized command: [" & action & "]"
  end if
end run


on trustCerts(certs)
  set runShellScriptInTerminal to "osascript " & quoted form of (POSIX path of (path to me)) & " run-shell-script "
  repeat with i from 1 to count of certs
    set cert to item i of certs
    set theLabel to "certificate [" & i & "/" & count of certs & "]"
    repeat
      tell application "Finder"
        activate
        display dialog ("Trust " & theLabel) giving up after 60
      end tell
      if button returned of result = "OK" then
        try
          set theScript to "/usr/bin/security add-trusted-cert -k " & quoted form of (keychain of cert) & " " & quoted form of (cert of cert)
          set theCmdline to runShellScriptInTerminal & quoted form of theScript
          log "Execute: " & theCmdline
          do shell script theCmdline
          log "Trusted " & theLabel
          exit repeat
        on error errMsg number errNum
          log "Error occurred: " & errMsg & " (Error Code: " & errNum & ")"
        end try
      else if gave up of result then
        error "Timeout of a request to trust " & theLabel
      end if
    end repeat
  end repeat
end trustCerts


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
