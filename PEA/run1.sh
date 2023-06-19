#!/usr/bin/bash
java -Xms32M -Xmx32M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:-UseOnStackReplacement -XX:+UseTLAB -XX:CompileCommand='compileOnly,Example1.test*' $* Example1
