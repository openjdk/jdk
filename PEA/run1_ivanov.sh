#!/usr/bin/bash 
java -Xcomp -Xms32M -Xmx32M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -XX:-UseOnStackReplacement -XX:+UseTLAB -XX:CompileOnly='Example1_ivanov.ivanov' -XX:CompileCommand=dontinline,Example1_ivanov.blackhole $* Example1_ivanov
