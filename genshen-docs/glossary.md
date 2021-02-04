## Glossary for the Generational Shenandoah (GenShen) Project

Shen := Shenandoah := the Shenandoah garbage collector

GenShen := Generational Shenandoah

gen := generation
young gen := the young generation
old gen := the old generation

collector := garbage collector
young collector := young gen collector
old collector := old gen collector
global collector := single-generation collector that works on the entire heap

young/old/global collection := a complete cycle through the phases of the young/old/global collector

cset := collection set
remset := remembered set
rset := remembered set

parallel := same task, dealt with by multiple threads
concurrent := different tasks, operated upon simultaneously
conc := concurrent

conc mark := concurrent marking
conc global GC := concurrent global GC, like vanilla Shen
evac := evacuation (phase)
UR := update references (phase)

LRB := Load Reference Barrier
SATB := Snapshot At The Beginning
TLAB := Thread-Local Allocation Buffer for a mutator thread
GCLAB := like a TLAB, but for a GC thread

young region := a heap region affiliated with young gen
old region := a heap region affiliated with old gen
free region := a region that is not affiliated with either generation and available for future reuse by allocators 

young object := an object in young gen
old object := an object in old gen

block := an identifiable chunk of space in a region that is or was occupied by a Java object
block start := a pointer to the beginning of a block
