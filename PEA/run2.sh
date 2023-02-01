#!/usr/bin/bash 
java -showversion -Xcomp -Xms32M -Xmx32M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -XX:-UseOnStackReplacement -XX:-UseTLAB -XX:CompileOnly='Example2.foo' -XX:CompileCommand=dontinline,Example2.blackhole $* Example2
