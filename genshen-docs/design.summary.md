## Design Points

This section discusses design decisions, as guided by the following tenets.

### Tenets

1. Don’t punish the mutator: While mutator code must be added to 
   implement generational collection, degradations of
   mutator throughput and responsiveness 
   should be contained as much as possible.
   If in doubt between design choices, 
   pick the one that promises to minimize mutator overhead.
2. Safeguard incremental progress with milestones that demonstrate key capabilities.
3. Minimize the overall development effort.  This includes containing
   efforts to prepare and present each milestone deliverable 
   that do not directly contribute to the end product.

### Design Decisions

While it is probable that we may revisit some of the following design
decisions if we run into implementation difficulties or performance
problems, the following is the current plan of record.

1. This document is maintained alongside the source code. 
   1. It is organized hierarchically with sections
      dedicated to major design decisions.
      Further, more detailed decisions are organized in top-down fashion,
      with overarching design decisions preceding derivative design decisions.
      The topic numbers assigned to particular design decisions may evolve over time.
   2. There is a separate "rationale" document that explains more of the reasoning behind design decisions
      and links relevant portions between documents.
2. Genshen changes to the Shenandoah implementation will be
   compartmented.  See discussion [here](rationale.summary.md#compartmentalization").
3. We will support concurrent collection of old gen and young gen,
   with the expectation that a single old collection will typically
   overlap with the execution of many young collections.
4. In order to minimize performance impact on the mutator, we will use
   a single Load-Reference-Barrier to implement both evacuation
   from young gen and from old gen.
   1. Tenuring will be implemented as part of young evacuation.
   2. All evacuation for both young and old gen regions
      happens under the control of the same GC threads and is
      supported by the same Load Reference Barrier (LRB) as in
      vanilla Shenandoah, with only small refinements to the
      implementation of slow-path code within the LRB.
   3. See discussion [here](rationale.summary.md#load-reference-barrier).
5. An old collection begins at the same time as a young gen
   collection, with both collections leveraging the
   results of a single shared root scan.
6. Old collection generally runs at lower priority (e.g., fewer
   numbers of parallel threads or greater “nice” values for
   concurrent threads) than young collection because
   replenishing the allocation pool to support ongoing allocation
   needs of mutator threads requires urgent completion of young GC.
   See discussion [here](rationale.summary.md#prioritization).
7. Although the planned future production release of GenShen will
   run young collection concurrently with old collection, support
   will also be implemented and maintained for alternating executions
   of young collections with global collections.  See
   discussion [here](rationale.summary.md#young-global-discussion).
   1. Since this is not a production release, efficiency of
      implementation is not a primary concern.
   2. Regression tests will exercise the implementation of this mode
      of operation.
8. A regression test suite will be developed and maintained to
   support all of the enhanced capabilities of GenShen, including
   capabilities that are enabled and disabled at build time for the
   JVM.
9. Though old collections start at the same time as young
   collections collections, they do not necessarily end at the same
   time.  Typically, many young-gen collections will be completed
   concurrently during the time that a single old-gen collection is
   running.  See discussion [here](rationale.summary.md#concurrency-of-young-and-old).
10. Root scanning will be performed during a JVM safe point.
11. Scanning of the remembered set will be performed by parallel
    young-gen GC threads during a JVM safe point.  A side
    effect of scanning is to mark as CLEAN any cards of the remembered
    set that are found by remembered set scanning to no longer be
    DIRTY. 
12. The remembered set maintains a start-of-object representation to
    facilitate quick identification of where the first object
    pertaining to each DIRTY card region begins.  See discussion
    [here](rationale.summary.md#remembered-set-starting-offsets).
    This requires that:
    1. Newly promoted objects be registered with the remembered set
       and start-of-object support by post-processing the associated GCLAB block, and
    2. When entire regions are promoted out of young collections into
       old gen, the objects contained within those regions must be
       registered with the remembered set and the start-of-object support.
13. Young marking, including scanning of root pointers,
    will place discovered references to old gen into a thread-local SATB
    buffer so that they can be processed by an old-gen collection
    thread.  See discussion [here](rationale.summary.md#satb-keep-old-alive-entries). 
    1. Possible optimization: refrain from logging old-gen pointers
       that refer to already marked old objects into the SATB buffer.
14. The default size of the SATB buffer will increase from 1024 to
    4096 words because we will be placing more information into the
    SATB buffer.
15. Whenever the SATB buffer is full, the slow path for adding to
    the SATB buffer will attempt to compress the buffer contents before
    communicating the buffer to GC threads.  If compression leaves a
    minimum of 256 slots available within the SATB buffer, the thread
    continues to add values to the existing buffer.  Compression
    consists of:
    1. For pointers to young gen:
       1. If concurrent marking of young gen is disabled, ignore the
          pointer.
       2. Otherwise, if the object referenced by this pointer has
          already been marked, ignore the pointer.
       3. If we choose to use SATB-based remembered set, ignore all
          overwritten address values that reside within young gen.
    2. For pointers to old gen:
       1. If concurrent marking of old gen is disabled, ignore the
          pointer.
       2. Otherwise, if the object referenced by this pointer
          has already been marked, ignore the pointer.
       3. If we choose to use an SATB-based remembered set:
          - If the card corresponding to an overwritten old gen
            address is already DIRTY, ignore this overwritten
            address.
          - Otherwise, fetch the pointer value held at the overwritten
            address.  If the fetched pointer value does not
            refer to young gen, ignore this overwritten address.
          - Otherwise, use a hash table (suggested size 127) to sift
            redundant DIRTY card references for the current batch of
            overwritten old-gen addresses.  Preserve only one address
            for each card entry that needs to be marked as DIRTY.
    3. SATB buffers will be processed by both a young GC
       thread and an old GC thread.
       1. The young GC thread will mark objects referenced
          by young pointers.
       2. The old GC thread will:
          1. Mark objects referenced by old-gen pointers, and
          2. If we choose to use SATB-based remembered set: mark as DIRTY
             the card entries corresponding to overwritten addresses.
16. GC uses a G1-style heuristic to choose collection sets for both
    young and old memory areas.  The collection set represents the
    heap regions that offer the greatest opportunity to quickly reclaim
    garbage, as with the existing Shenandoah implementation.  See
    discussion [here](rationale.summary.md#g1-heuristic).
17. Following an evacuation phase that evacuates
    both old and young heap regions, the update-references phase is
    required to update references throughout all 
    old regions that were not selected as part of the old
    collection set in addition to updating references in young
    heap regions.
    1. If a particular young collection evacuation phase does not
       evacuate any old regions, then its update references phase can
       focus solely on young heap regions and the
       remembered set.
18. Tenuring is based on object age with the enhancements described
    below.  See discussion [here](rationale.summary.md#tenuring).
    1. The variable TenureCycle counts how many GC cycles correspond to
       each increment of an object’s age.  Object ages are not
       necessarily incremented each time a GC cycle is completed.  They
       are incremented each time TenureCycle GC cycles have been
       completed.
    2. The variable TenureAge represents the age at which an object
       becomes eligible for tenuring.
    3. During GC cycles that correspond to TenureCycle, the “age” of
       individual objects is incremented by 1 plus the size of the
       object’s original heap region age when the object is evacuated.
    4. During GC cycles that correspond to TenureCycle, the “age” of
       each heap region that has been fully allocated (i.e. no more
       available memory for allocation) and that is not in the
       collection set is incremented by 1 at the end of the evacuation
       phase.  If the resulting “age” equals TenureAge, then the entire
       region is reassigned to become part of old gen.
       1. The update-refs phase will process this heap region even
          though it is “now” considered to be part of old gen.
       2. Each of the objects contained within the promoted region
          shall be registered with the remembered set abstraction.
       3. Each of the objects contained within the promoted region
         be scanned to determine any references to young-gen
         memory that are contained therein.  For any such pointers, set
         the associated card table entry to DIRTY.
19. During evacuation, each running mutator thread has both a TLAB
    and a GCLAB.
    1. The TLAB is associated with a young heap region.
    2. The GCLAB is associated with an old heap region.
    3. When the mutator’s load reference barrier encounters a
       reference for which the associated object needs to be tenured, it
       allocates the copy memory from the GCLAB.
    4. When the mutator’s load reference barrier encounters a
       reference for which the associated object resides within the
       collection set of old gen, it allocates the copy memory from the
       GCLAB.
    5. When the mutator’s load reference barrier encounters a
       reference for which the associated object needs to be evacuated to
       young gen, it allocates the copy memory from the TLAB.
    6. We initially plan to use the same size for TLAB and GCLAB, but
       this decision may be revisited.
    7. If the size of the object to be evacuated is larger than half
       the size of the respective local allocation buffer, allocation
       of the replica memory is handled by alternative allocators, to be
       designed.  Call these "odd" objects.
20. During evacuation, each evacuating GC thread will maintain two
    GCLAB buffers:
    1. GCLAB-Young is associated with young gen.
    2. GCLAB-Old is associated with old gen.
    3. If the object to be evacuated currently resides in old gen or
      if it resides in young gen and it is to be tenured, allocate the
      copy memory from GCLAB-Old.
    4. Otherwise, allocate the copy memory from GCLAB-Young.
    5. At the end of the evacuation phase, consider repurposing any
       unspent GCLAB-Young as a TLAB if there is sufficient unallocated
       memory remaining within it.
    6. At the end of the evacuation phase, consider preserving the
       GCLAB-Old for use as a GCLAB for a mutator thread during the next
       young collections collection or as a GCLAB-Old during the next
       old-gen evacuation pass.
    7. See 19.7.
21. A certain budget of CPU time is provisioned to perform young-gen
    GC in order to support a particular planned workload.
    1. The resources dedicated to young-gen GC are limited in order
       to assure a certain amount of CPU time is available to mutator
       threads.
    2. Command line options allow user control over provisioning.  A
       TBD API may allow services to adjust provisioning dynamically.
    3. In the ideal, provisioning is adjusted automatically based on
       TBD heuristics.
    4. The provisioned CPU resources can support a range of service
       quality, reclaiming large numbers of heap regions with a low GC
       frequency or reclaiming small numbers of heap regions with a
       high GC frequency.  Given a particular frequency of GC cycles,
       the same CPU resources can evacuate a large number of sparsely
       populated heap regions or a small number of densely populated
       heap regions.  Tradeoffs between these configuration extremes
       may be adjusted under software control or by TBD
       heuristics.
    5. For each young-gen GC pass, a certain TBD percentage
       of CPU resources are reserved for old-gen evacuation and
       update-refs activities.
       1. The old-gen CPU resource budget represents the total amount
          of old-gen memory that can be relocated, and is quantified as
          a multiple N of the heap region size.
       2. The old-gen GC threads determine the composition of the
          old-gen collection set, up to but never exceeding the upper
          bound N on cumulative evacuation size.
       3. The old-gen GC thread may select for the collection set
          N heap regions which are known to have 100%
          utilization, 2N heap regions known to have 50% utilization,
          5N heap regions known to have 20% utilization, and so on.
       4. If the old-gen GC refrains from delegating N heap regions
          worth of evacuation work to the young-gen evacuation phase,
          then the young GC is free to use the excess CPU resources to
          more aggressively evacuate more of its own young heap regions,
          using a larger than normal young-gen collection set.
       5. The budget for updating old-gen references must be large
          enough to handle the total old-gen memory size - N.  In the
          case that old-gen GC configures a collection set that
          represents its full evacuation capacity of N heap regions, the
          number of old-gen heap regions that are not part of the
          old-gen collection set is never larger than this quantity.
          In the case that old-gen GC configures a smaller collection
          set, then for each fraction of a heap region that is not
          evacuated, this much more of a heap region might have to be
          processed during the update-refs phase of GC.  We estimate
          that the cost of evacuating M bytes of memory is similar to
          the cost of updating the references within M bytes of
          memory.
22. A smaller (generally) budget of CPU time is provisioned to
    perform old-gen GC in order to support a particular planned
    workload.
    1. The resources dedicated to young-gen GC are limited in order
       to assure a certain amount of CPU time is available to mutator
       threads.
    2. Command-line options allow user control over provisioning.  A
       TBD API may allow services to adjust provisioning dynamically.
    3. In the ideal, provisioning is adjusted automatically based on
       TBD heuristics.
    4. As with young-gen GC, the CPU resources provisioned for
       old-gen GC can support a range of service quality.
    5. The CPU resources dedicated to old-gen collection do not have
       responsibility for evacuating old regions as all evacuation
       is performed by young-gen GC threads.  Instead, the CPU
       resources dedicated to old-gen GC activities are used for
       activities such as the following:
       1. Processing the content of SATB buffers:
          - If old collection is in concurrent marking phase, mark
            objects referenced by any keep-alive pointers.
          - If we are using SATB-based remembered set, update the
            remembered set based on overwritten addresses reported in the
            SATB buffer.
       2. During concurrent marking, scan the contents of previously
          marked objects to complete the transitive closure of
          reachability.
       3. Perform heuristic computations:
          - Determine when to start the next old-gen GC cycle.
          - Determine which old regions to evacuate on this and future
            passes of young-gen GC.
          - Adjust young-gen efficiency parameters such as: How many
            heap regions should be dedicated to young gen?  What is
            optimal value of TenureCycle?  What is optimal value of
            TenureAge?  How much CPU time should be dedicated to
            young-gen GC?
          - Adjust old-gen efficiency parameters such as: How much CPU
            time should be dedicated to old-gen GC?  How many heap regions
            should be dedicated to old gen?  Should any heap regions be
            decommissioned and returned to the operating system in order
            to shrink the memory footprint of this service?
       4. Perform routine maintenance as time and schedule permits:
          - Potentially sweep up dead memory, accumulating ages at
            which dead objects were reclaimed within old regions.
          - Potentially, sweep through evacuated memory to accumulate
            ages at which dead objects were reclaimed.
          - Organize free lists for fast and efficient allocation of
            GCLAB and Odd object allocations.
          - Return emptied old regions to the free set.
          - Eventually, reference processing and string deduplication
            should be performed by lower priority old-gen threads
            rather than higher priority young-gen threads.
       5. Following completion of each old-gen concurrent mark pass,
          select regions to be included in the old-gen collection set:
          - No more than a total of N bytes of old gen is evacuated by
            each pass of the young-gen evacuator.
          - If old-gen GC threads desire to evacuate M, which is more
            than N bytes of old gen, it does so by piggy backing on
            multiple subsequent young-gen evacuation passes, selecting
            evacuating no more than the accumulation of N total heap
            regions in each of the following young-gen evacuation passes.
          - Since it is most efficient to evacuate all M > N regions
            of old-gen memory with a single young-gen evacuation pass,
            configure the old-gen collection set to include all M
            regions if there are sufficient free regions available to
            afford the young-gen allocator to continue allocating new
            objects during the longer delay that would be required to
            evacuate more than the traditionally budgeted N regions of
            old-gen memory.
