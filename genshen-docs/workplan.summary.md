# Summary Plan of Record: GenShen Prototype (2020)

## Planned Milestones

### Overview of Initial Milestones

1. Pure young collection. (Young gen size unlimited. Old gen untouched.)
2. Size-restricted young gen.
3. Tenuring and promotion.
4. Global collection after young collection.
5. Young collection after global collection, repeat alternations.
6. Concurrent old marking.
7. Concurrent old and young collections.

Young collection reclaims garbage only from heap regions that are
identified as belonging to the young generation.

Old collection reclaims garbage only from heap regions that are
identified as belonging to the old generation, which holds objects
that have been promoted from young generation.

Global collection reclaims garbage from heap regions belonging to
either the young generation or the old generation.

### Milestone 1: GC of Young Gen Only

This demonstration proves successful implementation of:

1. Separating out that certain heap regions comprise young gen and
   other heap regions are considered to represent old gen. 
2. Confirming that card marking does not crash.  (Does not prove that
   card marking works because we have no objects in old gen.) 
3. Confirming that remembered set scanning does not crash.  (Does not
   prove that remembered set scanning works because we have no objects in
   old gen.) 
4. Demonstrating a simplified form of young collection.

Tasks

1. Integrate various completed code patches into a common branch
2. Test and debug existing code

### Milestone 2: Restrict Size of Young Gen

This milestone constrains the young generation to a certain number of
regions.  After that number is reached, allocation fails.  For now, the
number can be fixed, say 25% of all regions, and we can simply crash
thereafter. 

Tasks

1. Establish a mechanism to specify and enforce a limit on the number
   of heap regions that may comprise young-gen memory.
2. Adjust the GC triggering mechanism so that the size of young gen
   does not have reason to exceed the young-gen size during young-gen
   GC, where the size of young-gen is affected by GC in the
   following ways:
   1. Certain free heap regions may be added to young gen in order to
      support allocation requests that are made by concurrent mutator
      threads while GC is being performed.
   2. At the end of GC, all heap regions that were part of the
      collection set are removed from young-gen memory and placed
      in the free pool.


### Milestone 3: Young Collection with Promotion of Tenured Objects

Add to Milestone 2 the capability of promoting young gen objects.
Don’t worry about odd objects or humongous objects at this time.
Demonstrate: 

1. That we can promote objects into old gen.
2. That card-marking works.
3. That remembered set scanning works (correctly, but perhaps not with
   full desired efficiency). 

The following tasks must be implemented:

1. The collection set is selected as a subset of all young collections
   heap regions using TBD heuristics. 
2. During young collection, each time an object is evacuated to a
   young consolidation region, the object’s age is incremented. 
    1. For simplicity, don’t try to implement the complete region
       aging or promotion at this time. 
    2. Also for simplicity, don’t try to implement the TenureCycle
       optimization. 
3. During young collection, each GC thread maintains both a young
   GCLAB and and old GCLAB.
    1. When evacuating an object whose incremented age is less than
       TenureAge, allocate memory for the object’s copy from within the
       young GCLAB. 
    2. When evacuating an object whose incremented age is >=
       TenureAge, allocate memory for the object’s copy from within the
       old GCLAB. 
    3. Don’t support Odd or Humongous objects for simplicity.
4. During young collection, each mutator thread maintains both a TLAB
   and a GCLAB.  The GCLAB is associated with old gen. 
    1. When evacuating an object whose incremented age is less than
       TenureAge, allocate memory for the object’s copy from within the
       TLAB. 
    2. When evacuating an object whose incremented age is >=
       TenureAge, allocate memory for the object’s copy from within the
       GCLAB. 
5. Perform maintenance on the contents of each retired old GCLAB,
   where this maintenance consists of: 
    1. Registering each object’s starting location and length with the
       remembered set abstraction so that remembered set scanning can
       quickly find the first object within each DIRTY card region, 
    2. Updating the card table to reflect all references from this
       object into young gen,  
    3. Evacuating all objects directly referenced from this object
       which reside within a collection set and have not yet been
       evacuated, and 
    4. Healing all pointers from within newly evacuated old objects
       that refer to objects residing within the collection set. 
    5. The last two items listed above are already performed by
       traditional Shenandoah but can be merged with the implementation of
       the other maintenance activities in order to perform all this work
       in a consolidated pass. 

### Milestone 4: Sequential Execution of Multiple Young Collections Followed by Multiple Global Collections

__Demonstrated Concurrency Between Young-Gen and Old-Gen Activities__

   ✓ denotes that this combination of activities is allowed.

   ✗ denotes that this combination of activities is disallowed.

|                |  Old-Gen Mark  | Old-Gen Evac  | Old-Gen Idle |
|:--------------:|:--------------:|:-------------:|:------------:|
| Young Gen Mark |      ✓         |     ✗         |     ✓        |
| Young Gen Evac |      ✗         |     ✓         |     ✓        |
| Young Gen Idle |      ✗         |     ✗         |     ✓        |

Add to Milestone 3 the ability to switch to global collection after a
series of young Collections.

1. The switch is triggered by a simple TBD test, such as when the
   space available within old gen is less than the size of young gen. 
2. The global collection continues to run with the card-marking
   barrier enabled though the card values will not be consulted further.
3. For this demonstration, the switch to global collections is
   one-way.  Following this switch, we can no longer perform young
   collections. 
4. For this demonstration, the global collection does not distinguish
   between young regions and old regions. 
    1. Evacuation of objects that resided in an old region is handled
    the same as evacuation of objects that resided in a young heap
    region. 
5. This demonstrates that:
    1. Promotion of objects works.  The objects that have been promoted
    into old gen maintain whatever protocols and invariants are
    assumed by Shenandoah GC. 
    2. That we can manage the transition from young GC to global GC.
    3. That global collection, insofar as we characterize it, works.
    4. That Young collections do not corrupt the heap.

Tasks:

1. Fix bugs in existing code enhancements.
2. Implement the transition from young collection to global collection
3. Implement global collection with write barrier for card-table marking

### Milestone 5: Interleaved Execution of Young and Global Collections

__Demonstrated Concurrency Between Young-Gen and Old-Gen Activities__

   ✓ denotes that this combination of activities is allowed.

   ✗ denotes that this combination of activities is disallowed.

|                |  Old-Gen Mark  | Old-Gen Evac  | Old-Gen Idle |
|:--------------:|:--------------:|:-------------:|:------------:|
| Young Gen Mark |      ✓         |     ✗         |     ✓        |
| Young Gen Evac |      ✗         |     ✓         |     ✓        |
| Young Gen Idle |      ✗         |     ✗         |     ✓        |

Add to Milestone 4 the ability to switch back to Young Collection upon
completion of a global collection. 

1. Assume that the global collection is successful in reclaiming
   necessary memory.  No heuristics to resize oldgen or young gen at
   this time. Specify sizes of each on command line. 
2. The switch to old collection is triggered by exhaustion of old gen.
   At least one young collection is assumed to execute following
   completion of each global collection. 
3. For this demonstration, the global collection does distinguish
   between young collections and old regions. 
    1. Objects in the old collection set are evacuated to old
       consolidation regions. 
    2. Objects in the young collection set are evacuated
       to young collections consolidation regions. 
    3. There is no tenuring of objects during a global collection (for
       simplicity). 

This demonstrates that:

1. Promotion of objects works.  The objects that have been promoted
   into old gen maintain whatever protocols and invariants are
   assumed by Shenandoah GC. 
2. That we can manage the transition from young GC to global GC.
3. That we can manage the transition from global GC to young GC.
4. That the transition from global GC to young GC
   establishes all invariants required for correct operation of young
   GC. 

Tasks:

1. Distinguish between “global collection” for purposes of
   maintaining support for non-generational GC and “global collection”
   for purposes of supporting sequential interleaving of young GC
   and global GC.
2. Implement the transition from global GC to young GC.
3. Initialize the remembered set for each consolidation heap region
   of old gen to all CLEAN before allocating any GCLAB buffers within
   the consolidation heap region.
4. At the start of global evacuation, select the collection set as
   some combination of existing young regions and
   old regions based on heuristics TBD.
5. During global collection, maintain old-gen GCLABs for all GC
   threads and mutator threads.
6. During global collection, distinguish evacuation behavior
   depending on whether an object to be evacuated resides within the
   young collection set or the old collection set since
   young objects are evacuated into young 
   consolidation regions and old objects are evacuated into old
   consolidation regions;
7. Add minimal logging reports to describe behavior of young-gen and global
   GC.
8. During global collection, perform maintenance on the contents of
   each retired old GCLAB, where this maintenance consists of:
   1. Registering each object’s starting location and length with the
      remembered set abstraction so that remembered set scanning can
      quickly find the first object within each DIRTY card region,
   2. Updating the card table to reflect all references from this
      object into young gen,
   3. Evacuating all objects directly referenced from this object
      that reside within a collection set and that have not already been
      evacuated, and
   4. Healing all pointers from within new replica objects residing in old
      gen that refer to objects residing within the collection set.
   5. The last two items listed above are already performed by
      traditional Shenandoah but can be merged with the implementation of
      the other maintenance activities in order to perform all this work
      in a consolidated pass.

### Milestone 6: GC of young collections with Concurrent Marking (but not Collecting) of Old Gen

__Demonstrated Concurrency Between Young-Gen and Old-Gen Activities__

   ✓ denotes that this combination of activities is allowed.

   ✗ denotes that this combination of activities is disallowed.

|                |  Old-Gen Mark  | Old-Gen Evac  | Old-Gen Idle |
|:--------------:|:--------------:|:-------------:|:------------:|
| Young Gen Mark |      ✓         |     NA         |     ✓        |
| Young Gen Evac |      ✓         |     NA         |     ✓        |
| Young Gen Idle |      ✓         |     NA         |     ✓        |

This demonstration relies on GC log reports to show that marking of
old gen runs concurrently with marking of young gen.
Since the results of old-gen marking are not used to support old-gen
evacuation, this demonstration does not prove that old-gen marking
produces correct results.

All pointers to old-gen memory that are discovered during scan of
young-gen memory are communicated to the old-gen concurrent mark
threads by inserting these pointer values into a SATB buffer as
keep-alive values.  Every SATB buffer is post-processed both by a
young-gen GC thread and by an old-gen GC thread.

Pointers from old-gen memory to young-gen memory that are discovered
during the marking of old-gen are ignored.

At the start of young-gen concurrent marking, the remembered set is
scanned to detect all inter-generational references.

The SATB write barrier remains enabled as long as either young-gen or
old-gen concurrent marking is active.

Tasks:

1. Each young GC thread has a dedicated SATB buffer into which it places
   discovered references to old-gen memory.
2. SATB write barrier is left enabled as long as either young
   or old marking is active.
3. SATB buffer is enlarged to 4096 entries.
4. SATB buffer compression is enhanced to deal with the mix of old
   and young pointers.
5. SATB buffer processing is performed by both a young collection
   thread and an old collection thread.  Pointers to
   old gen within the SATB buffer are marked and added to the old-gen
   closure queues so that they can be subsequently scanned.
6. Certain GC threads (or work items) are dedicated to old GC
   and others are dedicated to young GC.
7. Old-gen GC threads process the closure of previously marked
   old-gen objects, scanning all references contained therein.
8. Old-gen GC threads add to the old-gen closures all old-gen objects
   referenced by SATB buffers if those objects were not previously marked.

### Milestone 7: Concurrent Young and Old Collections


__Demonstrated Concurrency Between Young-Gen and Old-Gen Activities__

   ✓ denotes that this combination of activities is allowed.

   ✗ denotes that this combination of activities is disallowed.

|                |  Old-Gen Mark  | Old-Gen Evac  | Old-Gen Idle |
|:--------------:|:--------------:|:-------------:|:------------:|
| Young Gen Mark |      ✓         |     ✗         |     ✓        |
| Young Gen Evac |      ✓         |     ✓         |     ✓        |
| Young Gen Idle |      ✓         |     ✗         |     ✓        |

In this demonstration, old-gen concurrent marking runs concurrently with all
phases of young-gen GC.  Old-gen evacuation only runs while young-gen
evacuation is running.  In the case that old-gen needs to evacuate so
much memory that doing so in a single uninterruptible batch would
significantly extend the duration of the young-gen evacuation phase,
the total old-gen evacuation workload is divided into multiple smaller
batches of evacuation work, each batch being processed concurrently
with a different young-gen evacuation cycle. 

The demonstration:

1. Uses logging reports to describe the results of young
   collection and old collection.
2. Shows for some “simple” workload (Heapothysis or
   Extremem?) that generational GC provides performance benefits over
   non-generational GC.

Tasks

1. Add minimal logging reports to describe behavior of old-gen
   GC.
2. Decide which old regions comprise the old collection set.
3. Divide the old collection set into multiple collection subsets.
4. For each of the collection subsets
   1. Communicate the subset to young GC tasks to process these
      evacuations when it begins its next evacuation cycle.
   2. Wait for young GC tasks to signal completion of the evacuation
      cycle.

## Proposed Future Milestones Not Yet Fully Planned

### Milestone 8: Performance Improvements

1. Remembered Set scanning sets cards to CLEAN if they are no longer
   DIRTY.
2. Remembered Set scanning maintains and utilizes start-offset data
   structure to quickly find the first object to be scanned within each
   DIRTY card.
3. Remembered set scanning refrains from scanning the portions of
   large objects and arrays that overlap card regions that are not
   DIRTY.

### Milestone 9: Fix Known Bugs

We are aware of bugs in our existing card-marking implementation.

### Milestone 10: Multiple Young-Gen Evacuations Process Old Collection Set

### Milestone 11: Odd Objects (larger than 50% of TLAB/GCLAB size)

By default, the promotion of such objects is handled by a
slower-than-normal path.  Instead of allocating old gen from the
GCLAB, the mutator thread obtains memory for the copy by directly
accessing free lists. See existing code that does that already. 

### Micro Milestone 12: Collect and Report New Metrics

### Micro Milestone 13: SATB-Based Remembered Set

### Micro Milestone 14: Heuristic Pacing of Young Collection Frequency

### Micro Milestone 15: Heuristic Pacing of Old Collection Frequency

### Micro Milestone 16: Heuristic Sizing of Young and Old Sizes

### Micro Milestone 17: Heuristic Adjustments of Tenuring Strategies

### Micro Milestone 18: Overlap Evacuation of Cycle N with Marking of Cycle N+1

### Micro Milestone 19: Humongous Objects

### Micro Milestone 20: Reference Processing

### Micro Milestone 21: String Dedup

### Micro Milestone 22: Degenerated GC

### Micro Milesones TBD: Various Performance Improvements

