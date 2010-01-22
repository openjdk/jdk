#
# Copyright 2004-2010 Sun Microsystems, Inc.  All Rights Reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Sun designates this
# particular file as subject to the "Classpath" exception as provided
# by Sun in the LICENSE file that accompanied this code.
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

Instructions on adding a jvmti demo agent.

Basically you want to mimic the jvmti demo agent "mtrace".

* Create and populate a source directory at src/share/demo/jvmti
  (Try and re-use code in agent_util area like src/share/demo/jvmti/mtrace)
  (This should include a small README.txt document on what this demo is)

* Make sure the appropriate "demo" copyright notice is added to all the
  source files.

* Edit src/share/demo/jvmti/index.html and add in reference to this demo.

* Create make directory at make/mkdemo/jvmti
  (Mimic make/mkdemo/jvmti/mtrace/Makefile)

* Edit make/mkdemo/jvmti/Makefile and add in the new demo

* Create test directory at test/demo/jvmti, create at least one test
  (Use test/demo/jvmti/mtrace as a template)

* Don't forget to check in all the new files

* Build and create images (cd make && gnumake && gnumake images)
  (Do this on Solaris, Linux, and at least one Windows platform)

* Verify that browsing build/*/j2sdk-images/demo/jvmti looks right

* Run the tests: cd test/demo/jvmti && runregress .
  (Do this on Solaris, Linux, and at least one Windows platform)

Contact: serviceability-dev@openjdk.java.net for more information or help.

