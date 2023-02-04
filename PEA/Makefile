.DEFAULT_GOAL := all

JAVA_PATH := $(abspath ../build/linux-x86_64-server-fastdebug/images/jdk)
export JAVA_HOME := ${JAVA_PATH}
export PATH := ${JAVA_HOME}/bin:$(PATH)

CTW_DIR := $(abspath ../test/hotspot/jtreg/testlibrary/ctw)
CTW_SCRIPT = ${CTW_DIR}/dist/ctw.sh
CTW_JAVA_OPTIONS := -XX:+CITime -XX:-TieredCompilation -XX:+UseTLAB -XX:+PrintCompilation -XX:+UnlockExperimentalVMOptions -XX:+DoPartialEscapeAnalysis

set_jdk:
	if [ -e ${JAVA_PATH} ]; then \
		echo "choose ${JAVA_HOME}"; \
		java -version; \
	else \
		echo "can't find valid jdk!"; \
		exit 1; \
	fi

all: set_jdk
	javac *.java
	cd ${CTW_DIR}; make

run: set_jdk
	./auto.sh

$(CTW_SCRIPT):
	${MAKE} -C ${CTW_DIR} all

run-ctw: set_jdk ${CTW_SCRIPT}
	cd ${CTW_DIR}/dist; \
	JAVA_OPTIONS="${CTW_JAVA_OPTIONS}" ./ctw.sh modules:java.base

run-ctw-no-inline: set_jdk ${CTW_SCRIPT}
	cd ${CTW_DIR}/dist; \
	JAVA_OPTIONS="${CTW_JAVA_OPTIONS} -XX:-Inline" ./ctw.sh modules:java.base
