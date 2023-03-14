#!/usr/bin/bash
java -Xms32M -Xmx32M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -XX:-UseOnStackReplacement -XX:+UseTLAB -XX:CompileCommand='compileonly,Example2_1::foo*' -XX:CompileCommand=dontinline,Example2.blackhole $* Example2_1
java -Xcomp -Xms32M -Xmx32M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -XX:-UseOnStackReplacement -XX:+UseTLAB -XX:CompileCommand='compileonly,Example2_1::foo*' -XX:CompileCommand=dontinline,Example2.blackhole $* Example2_1
