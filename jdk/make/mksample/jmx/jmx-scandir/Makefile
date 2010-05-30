#
# Copyright (c) 2006, 2007, Oracle and/or its affiliates. All rights reserved.
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

#
# Makefile for the nioserver sample code
#

BUILDDIR = ../../..

MODULE  = samples
PRODUCT = java

include $(BUILDDIR)/common/Defs.gmk

SAMPLE_SRC_DIR = $(SHARE_SRC)/sample/jmx/jmx-scandir
SAMPLE_DST_DIR = $(SAMPLEDIR)/jmx/jmx-scandir

SAMPLE_FILES =									\
	$(SAMPLE_DST_DIR)/build.xml						\
	$(SAMPLE_DST_DIR)/build.properties					\
	$(SAMPLE_DST_DIR)/index.html						\
	$(SAMPLE_DST_DIR)/keystore						\
	$(SAMPLE_DST_DIR)/logging.properties					\
	$(SAMPLE_DST_DIR)/manifest.mf						\
	$(SAMPLE_DST_DIR)/truststore						\
	$(SAMPLE_DST_DIR)/docfiles/connect-local-ant-run.jpg			\
	$(SAMPLE_DST_DIR)/docfiles/connect-local-java-jar.jpg			\
	$(SAMPLE_DST_DIR)/docfiles/connect-local.jpg				\
	$(SAMPLE_DST_DIR)/docfiles/remote-connection-failed.jpg			\
	$(SAMPLE_DST_DIR)/docfiles/remote-connection.jpg			\
	$(SAMPLE_DST_DIR)/docfiles/scandir-config.jpg				\
	$(SAMPLE_DST_DIR)/docfiles/scandir-result.jpg				\
	$(SAMPLE_DST_DIR)/docfiles/scandir-start.jpg				\
	$(SAMPLE_DST_DIR)/nbproject/file-targets.xml				\
	$(SAMPLE_DST_DIR)/nbproject/jdk.xml					\
	$(SAMPLE_DST_DIR)/nbproject/netbeans-targets.xml			\
	$(SAMPLE_DST_DIR)/nbproject/project.xml					\
	$(SAMPLE_DST_DIR)/src/com/sun/jmx/examples/scandir/DirectoryScanner.java		\
	$(SAMPLE_DST_DIR)/src/com/sun/jmx/examples/scandir/DirectoryScannerMXBean.java		\
	$(SAMPLE_DST_DIR)/src/com/sun/jmx/examples/scandir/ResultLogManager.java		\
	$(SAMPLE_DST_DIR)/src/com/sun/jmx/examples/scandir/ResultLogManagerMXBean.java		\
	$(SAMPLE_DST_DIR)/src/com/sun/jmx/examples/scandir/ScanDirAgent.java			\
	$(SAMPLE_DST_DIR)/src/com/sun/jmx/examples/scandir/ScanDirClient.java			\
	$(SAMPLE_DST_DIR)/src/com/sun/jmx/examples/scandir/ScanDirConfig.java			\
	$(SAMPLE_DST_DIR)/src/com/sun/jmx/examples/scandir/ScanDirConfigMXBean.java		\
	$(SAMPLE_DST_DIR)/src/com/sun/jmx/examples/scandir/ScanManager.java			\
	$(SAMPLE_DST_DIR)/src/com/sun/jmx/examples/scandir/ScanManagerMXBean.java		\
	$(SAMPLE_DST_DIR)/src/com/sun/jmx/examples/scandir/package.html				\
	$(SAMPLE_DST_DIR)/src/com/sun/jmx/examples/scandir/config/DirectoryScannerConfig.java	\
	$(SAMPLE_DST_DIR)/src/com/sun/jmx/examples/scandir/config/FileMatch.java		\
	$(SAMPLE_DST_DIR)/src/com/sun/jmx/examples/scandir/config/ResultLogConfig.java		\
	$(SAMPLE_DST_DIR)/src/com/sun/jmx/examples/scandir/config/ResultRecord.java		\
	$(SAMPLE_DST_DIR)/src/com/sun/jmx/examples/scandir/config/ScanManagerConfig.java	\
	$(SAMPLE_DST_DIR)/src/com/sun/jmx/examples/scandir/config/XmlConfigUtils.java		\
	$(SAMPLE_DST_DIR)/src/com/sun/jmx/examples/scandir/config/package.html			\
	$(SAMPLE_DST_DIR)/src/etc/access.properties					\
	$(SAMPLE_DST_DIR)/src/etc/management.properties					\
	$(SAMPLE_DST_DIR)/src/etc/password.properties					\
	$(SAMPLE_DST_DIR)/src/etc/testconfig.xml					\
	$(SAMPLE_DST_DIR)/test/com/sun/jmx/examples/scandir/DirectoryScannerTest.java		\
	$(SAMPLE_DST_DIR)/test/com/sun/jmx/examples/scandir/ScanDirConfigTest.java		\
	$(SAMPLE_DST_DIR)/test/com/sun/jmx/examples/scandir/ScanManagerTest.java		\
	$(SAMPLE_DST_DIR)/test/com/sun/jmx/examples/scandir/TestUtils.java			\
	$(SAMPLE_DST_DIR)/test/com/sun/jmx/examples/scandir/config/XmlConfigUtilsTest.java


all build: $(SAMPLE_FILES)

$(SAMPLE_DST_DIR)/%: $(SAMPLE_SRC_DIR)/%
	$(install-file)

clean clobber:
	$(RM) -r $(SAMPLE_DST_DIR)

.PHONY: all build clean clobber
