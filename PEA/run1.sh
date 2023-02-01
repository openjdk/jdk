#!/usr/bin/bash 
java -showversion -Xcomp -Xms32M -Xmx32M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -XX:-UseOnStackReplacement -XX:+UseTLAB -XX:CompileOnly='Example1.foo' -XX:CompileCommand=dontinline,Example1.blackhole $* Example1
