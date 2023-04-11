#!/usr/bin/bash
java -Xms16M -Xmx16M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:-TieredCompilation -XX:+UseEpsilonGC -XX:-UseOnStackReplacement -XX:CompileCommand=dontinline,CrazyException.blackhole -XX:CompileCommand=compileonly,CrazyException.foo -XX:CompileCommand=dontinline,CrazyException.close $* CrazyException
