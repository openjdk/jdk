/*
 * Copyright 2002-2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
Instructions for running FontChecker 
------------------------------------

FontChecker is a program designed to identify fonts that may cause JRE
crashes. Such fonts may be corrupted files, or badly constructed fonts.
Some crashes may also be due to bugs in the JRE's font code.
This test is designed to run quickly and silently as part of the JRE
installation process. It will only benefit users who install the JRE
via that mechanism. It cannot guarantee to identify all "bad fonts" because
the tests are minimal. Nor can it prevent problems due to fonts installed
subsequently to the JRE's installation. However it does ensure that the
vast majority of problem fonts are identified. This is important
"RAS" functionality. It is targeted at the consumer/plugin market where
there is substantial likelihood of end-users having installed software
packages which may be delivered with fonts that are not up to commercial
standards.

The test is designed to be "fail safe". If the program fails to run
properly it has no impact on the installer or on JRE execution.
Thus there is no need to monitor successful execution of the test.

The test is not a new "tool" in the sense of "javah" etc.
The test is not designed to be user executable or visible, and should
be unpacked by the installer into a temporary location, and executed
once the rest of the JRE is installed (ie as a postinstall step), and
can then be deleted from the temporary location once installation is
complete. Not deleting the jar file before execution is complete is
probably the sole reason that the installer may want to wait for
the program to complete.

The FontChecker application can be run directly from the jar 
file with this command: 
	%java -jar fontchecker.jar -o <file>

The output file is a required parameter in this version of the application.
The JRE installer should use the above form, and use it to create an
output file which must be named "badfonts.txt" and be placed into
the JRE's lib\fonts directory eg:-

        java -jar fontchecker.jar -o "C:\Program Files\jre\lib\fonts\badfonts.txt"

Note the lower case "badfonts.txt", and the string quotes because of the spaces
in the path name.
The location given here is an example and needs to be calculated at install
time as $JREHOME\lib\fonts\badfonts.txt
The location and name are important, because the JRE at runtime will
look for this exactly located name and file.
This location is private to that JRE instance. It will not affect
any other JRE installed on the system.

If running from a different directory than that containing the jar file,
use the form containing the full path to the jar file, eg :

	java -jar C:\fc\fontchecker.jar -o "C:\Program Files\jre\lib\fonts\badfonts.txt"

FontChecker application accepts following command line flags. 
usage: java -jar fontchecker.jar -o outputfile
	                -v 

       -o is the name of the file to contains canonical path names of
          bad fonts that are identified. This file is not created if
          no bad fonts are found.

       -v verbose mode: print progress/warning messages. Not recommended
         for installer use.

       -w if running on Windows, use "javaw" to exec the sub-process.
