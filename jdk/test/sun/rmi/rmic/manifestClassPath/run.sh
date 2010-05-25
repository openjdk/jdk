#
# Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#

#!/bin/sh
# @test
# @bug 6473331 6485027 6934615
# @summary Test handling of the Class-Path attribute in jar file manifests
#          for the rmic tool
# @author Andrey Ozerov
#
# @run shell run.sh

# To run this test manually, simply do ./run.sh

. ${TESTSRC-.}/Util.sh

set -u

Cleanup() {
    Sys rm -rf pkg Main.java MainI.java Main.class MainI.class Main_Stub.class
    Sys rm -rf jars MANIFEST.MF A.jar B.zip
}

Cleanup
Sys mkdir pkg

#----------------------------------------------------------------
# Create mutually referential jar files
#----------------------------------------------------------------
cat >pkg/A.java <<EOF
package pkg;
public class A implements java.io.Serializable {
    public int f(B b) { return b.g(); }
    public int g() { return 0; }
}
EOF

cat >pkg/B.java <<EOF
package pkg;
public class B implements java.io.Serializable {
    public int f(A a) { return a.g(); }
    public int g() { return 0; }
}
EOF

Sys "$javac" pkg/A.java pkg/B.java

# NOTE: Previously, some lines were commented out and alternative lines
# provided, to work around javac bug 6485027. That bug, and related rmic
# bug 6934615 have now been fixed, so most of the workarounds have been
# removed. However, javac still does not evaluate jar class paths on
# the bootclasspath, including -extdirs.

MkManifestWithClassPath "sub/B.zip"
Sys "$jar" cmf MANIFEST.MF A.jar pkg/A.class

MkManifestWithClassPath "../A.jar"
Sys "$jar" cmf MANIFEST.MF B.zip pkg/B.class

Sys rm -rf pkg
Sys mkdir jars
Sys mv A.jar jars/.
Sys mkdir jars/sub
Sys mv B.zip jars/sub/.

cat >MainI.java <<EOF
import pkg.*;
public interface MainI extends java.rmi.Remote {
    public int doIt(A a, B b) throws java.rmi.RemoteException;
}
EOF

cat >Main.java <<EOF
import pkg.*;
import java.rmi.server.UnicastRemoteObject;
public class Main implements MainI {
    public int doIt(A a, B b) {
	return a.f(b) + b.f(a);
    }
    public static void main(String args[]) throws Exception {
	Main impl = new Main();
	try {
	    MainI stub = (MainI) UnicastRemoteObject.exportObject(impl);
	    int result = stub.doIt(new A(), new B());
	    System.exit(result);
	} finally {
	    try {
		UnicastRemoteObject.unexportObject(impl, true);
	    } catch (Exception e) { }
	}
    }
}
EOF

Success "$javac" -classpath "jars/A.jar"       Main.java MainI.java
Success "$rmic"  -classpath "jars/A.jar${PS}." Main
Success "$java"  -classpath "jars/A.jar${PS}." Main

Sys rm -f Main.class MainI.class Main_Stub.class

Success "$javac" -classpath "jars/sub/B.zip"       Main.java MainI.java
Success "$rmic"  -classpath "jars/sub/B.zip${PS}." Main
Success "$java"  -classpath "jars/sub/B.zip${PS}." Main

#Sys rm -f Main.class MainI.class Main_Stub.class
Sys rm -f Main_Stub.class				# javac -extdirs workaround

#Success "$javac" -extdirs "jars" -classpath None Main.java MainI.java
Success "$rmic"  -extdirs "jars" -classpath .    Main
Success "$java"  -Djava.ext.dirs="jars" -cp .    Main

Sys rm -f Main_Stub.class

#Success "$javac" -extdirs "jars/sub" -classpath None Main.java MainI.java
Success "$rmic"  -extdirs "jars/sub" -classpath . Main
Success "$java"  -Djava.ext.dirs="jars/sub" -cp . Main

Cleanup

Bottom Line
