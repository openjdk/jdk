.DEFAULT_GOAL := all

java_path := $(abspath ../build/linux-x86_64-server-fastdebug/images/jdk)
export JAVA_HOME := ${java_path}
export PATH := ${JAVA_HOME}/bin:$(PATH)

set_jdk:
	if [ -e ${java_path} ]; then \
		echo "choose ${JAVA_HOME}"; \
		java -version; \
	else \
		echo "can't find valid jdk!"; \
		exit 1; \
	fi

all: set_jdk
	javac *.java

run: set_jdk
	./auto.sh
