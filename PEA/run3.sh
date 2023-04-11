#!/usr/bin/bash
java -XX:+PrintCompilation -XX:CompileCommand='CompileOnly,Example3::confined_close' -XX:CompileCommand=quiet -XX:-UseOnStackReplacement -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -Xms32M -Xmx32M -XX:-TieredCompilation  $* Example3
