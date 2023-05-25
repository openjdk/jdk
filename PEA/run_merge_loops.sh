#!/usr/bin/bash
java -XX:+UnlockExperimentalVMOptions  -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:+UseTLAB -XX:CompileCommand=dontinline,MergeLoop::blackhole -XX:CompileCommand='compileonly,MergeLoop::test' -Xbatch $* MergeLoop
