#!/bin/sh

#
# Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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

# @test
# @bug 4991526 6514993
# @summary Unit test for Preferences jar providers
#
# @build PrefsSpi
# @run shell PrefsSpi.sh
# @author Martin Buchholz

# Command-line usage: sh PrefsSpi.sh /path/to/build

if [ -z "$TESTJAVA" ]; then
    if [ $# -lt 1 ]; then exit 1; fi
    TESTJAVA="$1"; shift
    TESTSRC="`pwd`"
    TESTCLASSES="`pwd`"
fi

 java="$TESTJAVA/bin/java"
javac="$TESTJAVA/bin/javac"
  jar="$TESTJAVA/bin/jar"

Die() { printf "%s\n" "$*"; exit 1; }

Sys() {
    printf "%s\n" "$*"; "$@"; rc="$?";
    test "$rc" -eq 0 || Die "Command \"$*\" failed with exitValue $rc";
}

cat > StubPreferences.java <<'EOF'
import java.util.prefs.*;

public class StubPreferences extends AbstractPreferences {
    public StubPreferences() { super(null, ""); }
    public String              getSpi(String x)           { return null; }
    public void                putSpi(String x, String y) { }
    public void                removeSpi(String x)        { }
    public AbstractPreferences childSpi(String x)         { return null; }
    public void                removeNodeSpi()            { }
    public String[]            keysSpi()                  { return null; }
    public String[]            childrenNamesSpi()         { return null; }
    public void                syncSpi()                  { }
    public void                flushSpi()                 { }
}
EOF

cat > StubPreferencesFactory.java <<'EOF'
import java.util.prefs.*;

public class StubPreferencesFactory implements PreferencesFactory {
    public Preferences userRoot()   { return new StubPreferences(); }
    public Preferences systemRoot() { return new StubPreferences(); }
}
EOF

Sys rm -rf jarDir extDir
Sys mkdir -p jarDir/META-INF/services extDir
echo "StubPreferencesFactory" \
  > "jarDir/META-INF/services/java.util.prefs.PreferencesFactory"
Sys "$javac" -d jarDir StubPreferencesFactory.java StubPreferences.java

(cd jarDir && "$jar" "cf" "../extDir/PrefsSpi.jar" ".")

case "`uname`" in Windows*|CYGWIN* ) CPS=';';; *) CPS=':';; esac

Sys "$java" "-cp" "$TESTCLASSES${CPS}extDir/PrefsSpi.jar" \
    -Djava.util.prefs.PreferencesFactory=StubPreferencesFactory \
    PrefsSpi "StubPreferences"
Sys "$java" "-cp" "$TESTCLASSES" \
    PrefsSpi "java.util.prefs.*"
Sys "$java" "-cp" "$TESTCLASSES${CPS}extDir/PrefsSpi.jar" \
    PrefsSpi "StubPreferences"
Sys "$java" "-cp" "$TESTCLASSES" "-Djava.ext.dirs=extDir" \
    PrefsSpi "StubPreferences"

rm -rf jarDir extDir
