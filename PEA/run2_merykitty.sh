#!/usr/bin/bash 
java -ea -Xcomp -Xms32M -Xmx32M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -XX:-UseOnStackReplacement -XX:-UseTLAB -XX:CompileOnly='Example2_merykitty.foo' -XX:CompileCommand=dontinline,Example2_merykitty.blackhole $* Example2_merykitty
