#
# Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
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

Instructions on adding a java.lang.management demo.

Basically you want to mimic the java.lang.management demo "FullThreadDump".

* Create and populate a source directory at src/demo/management
  (This should include a small README.txt document on what this demo is)

* Make sure the appropriate "demo" copyright notice is added to all the
  source files.

* Edit src/share/demo/management/index.html and add in reference to this demo.

* Create make directory at make/mkdemo/management
  (Mimic make/mkdemo/management/FullThreadDump/Makefile)

* Edit make/mkdemo/management/Makefile and add in the new demo

* Create test directory at test/demo/management, create at least one test
  (Use test/demo/management/FullThreadDump as a template)

* Don't forget to put all files under SCM control

* Build and create images (cd make && gnumake && gnumake images)
  (Do this on Solaris, Linux, and at least one Windows platform)

* Verify that browsing build/*/j2sdk-images/demo/management looks right

* Run the tests: cd test/demo/management && runregress .
  (Do this on Solaris, Linux, and at least one Windows platform)

Contact: jk-svc-group@sun.com for more information or help.

