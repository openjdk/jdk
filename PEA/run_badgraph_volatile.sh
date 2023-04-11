#!/usr/bin/bash
java -Xms8M -Xmx8M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -XX:-UseOnStackReplacement -XX:-UseTLAB -XX:-TieredCompilation -XX:CompileOnly='BadGraphVolatile.foo' -XX:CompileCommand='dontinline,BadGraphVolatile::blackhole' -Xbatch $* BadGraphVolatile
