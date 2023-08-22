#!/bin/bash
java  -XX:+UnlockExperimentalVMOptions -XX:CompileCommand=dontinline,java.util.Collections::addAll -XX:-UseOnStackReplacement -XX:CompileCommand=compileonly,MaterializeMember::test -Xbatch $* MaterializeMember
