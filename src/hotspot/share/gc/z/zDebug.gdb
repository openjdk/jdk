#
# GDB functions for debugging the Z Garbage Collector
#

printf "Loading zDebug.gdb\n"

# Print Klass*
define zpk
    printf "Klass: %s\n", (char*)((Klass*)($arg0))->_name->_body
end

# Print oop
define zpo
    set $obj = (oopDesc*)($arg0)

    printf "Oop:   0x%016llx\tState: ", (uintptr_t)$obj
    if ((uintptr_t)$obj & (uintptr_t)ZPointerStoreGoodMask)
        printf "Good "
        if ((uintptr_t)$obj & (uintptr_t)ZPointerRemapped)
            printf "(Remapped)"
        else
            if ((uintptr_t)$obj & (uintptr_t)ZPointerMarkedOld)
                printf "(MarkedOld)"
            else
                printf "(Unknown)"
            end
        end
    else
        printf "Bad "
        if ((uintptr_t)ZPointerStoreGoodMask & (uintptr_t)ZPointerMarkedOld)
            # Should be marked
            if ((uintptr_t)$obj & (uintptr_t)ZPointerRemapped)
                printf "(Not Marked, Remapped)"
            else
                printf "(Not Marked, Not Remapped)"
            end
        else
            if ((uintptr_t)ZPointerStoreGoodMask & (uintptr_t)ZPointerRemapped)
                # Should be remapped
                if ((uintptr_t)$obj & (uintptr_t)ZPointerMarkedOld)
                    printf "(Marked, Not Remapped)"
                else
                    printf "(Not Marked, Not Remapped)"
                end
            else
                # Unknown
                printf "(Unknown)"
            end
        end
    end
    printf "\t Page: %llu\n", ((uintptr_t)$obj & ZAddressOffsetMask) >> ZGranuleSizeShift
    x/16gx $obj
    if (UseCompressedClassPointers)
        set $klass = (Klass*)(void*)((uintptr_t)CompressedKlassPointers::_narrow_klass._base +((uintptr_t)$obj->_metadata->_compressed_klass << CompressedKlassPointers::_narrow_klass._shift))
    else
        set $klass = $obj->_metadata->_klass
    end
    printf "Mark:  0x%016llx\tKlass: %s\n", (uintptr_t)$obj->_mark, (char*)$klass->_name->_body
end

# Print heap page by page table index
define zpp
    set $page = (ZPage*)((uintptr_t)ZHeap::_heap._page_table._map._map[($arg0)] & ~1)
    printf "Page %p\n", $page
    print *$page
end

# Print page_table
define zpt
    printf "Pagetable (first 128 slots)\n"
    x/128gx ZHeap::_heap._page_table._map._map
end

# Print live map
define __zmarked
    set $livemap   = $arg0
    set $bit        = $arg1
    set $size       = $livemap._bitmap._size
    set $segment    = $size / ZLiveMap::nsegments
    set $segment_bit = 1 << $segment

    printf "Segment is "
    if !($livemap._segment_live_bits & $segment_bit)
        printf "NOT "
    end
    printf "live (segment %d)\n", $segment

    if $bit >= $size
        print "Error: Bit %z out of bounds (bitmap size %z)\n", $bit, $size
    else
        set $word_index = $bit / 64
        set $bit_index  = $bit % 64
        set $word       = $livemap._bitmap._map[$word_index]
        set $live_bit   = $word & (1 << $bit_index)

        printf "Object is "
        if $live_bit == 0
            printf "NOT "
        end
        printf "live (word index %d, bit index %d)\n", $word_index, $bit_index
    end
end

define zmarked
    set $addr          = $arg0
    set $obj           = ((uintptr_t)$addr & ZAddressOffsetMask)
    set $page_index    = $obj >> ZGranuleSizeShift
    set $page_entry    = (uintptr_t)ZHeap::_heap._page_table._map._map[$page_index]
    set $page          = (ZPage*)($page_entry & ~1)
    set $page_start    = (uintptr_t)$page._virtual._start
    set $page_end      = (uintptr_t)$page._virtual._end
    set $page_seqnum   = $page._livemap._seqnum
    set $global_seqnum = ZGlobalSeqNum

    if $obj < $page_start || $obj >= $page_end
        printf "Error: %p not in page %p (start %p, end %p)\n", $obj, $page, $page_start, $page_end
    else
        printf "Page is "
        if $page_seqnum != $global_seqnum
            printf "NOT "
        end
        printf "live (page %p, page seqnum %d, global seqnum %d)\n", $page, $page_seqnum, $global_seqnum

        #if $page_seqnum == $global_seqnum
            set $offset = $obj - $page_start
            set $bit = $offset / 8
            __zmarked $page._livemap $bit
        #end
    end
end

# For some reason gdb doesn't like ZGeneration::ZPhase::Mark etc.
# Use hard-coded values instead.
define z_print_phase
  if $arg0 == 0
    printf "Mark"
  else
    if $arg0 == 1
      printf "MarkComplete"
    else
      if $arg0 == 2
        printf "Relocate"
      else
	printf "Unknown"
      end
    end
  end
end

define z_print_generation
  printf "%u", $arg0->_seqnum
  printf "/"
  z_print_phase $arg0->_phase
end

define zz
  printf "Old: "
  z_print_generation ZHeap::_heap->_old

  printf " | "

  printf "Young: "
  z_print_generation ZHeap::_heap->_young

  printf "\n"
end

# Print heap information
define zph
    printf "Heap\n"
    printf "     Young Phase:       %u\n", ZHeap::_heap->_young->_phase
    printf "     Old Phase:         %u\n", ZHeap::_heap->_old->_phase
    printf "     Young SeqNum:      %u\n", ZHeap::_heap->_young->_seqnum
    printf "     Old SeqNum:        %u\n", ZHeap::_heap->_old->_seqnum
    printf "     Offset Max:        %-15llu (0x%llx)\n", ZAddressOffsetMax, ZAddressOffsetMax
    printf "     Page Size Small:   %-15llu (0x%llx)\n", ZPageSizeSmall, ZPageSizeSmall
    printf "     Page Size Medium:  %-15llu (0x%llx)\n", ZPageSizeMedium, ZPageSizeMedium
    printf "Metadata Bits\n"
    printf "     Good:              0x%016llx\n", ZPointerStoreGoodMask
    printf "     Bad:               0x%016llx\n", ZPointerStoreBadMask
    printf "     MarkedYoung:       0x%016llx\n", ZPointerMarkedYoung
    printf "     MarkedOld:         0x%016llx\n", ZPointerMarkedOld
    printf "     Remapped:          0x%016llx\n", ZPointerRemapped
end

define print_bits
  set $value=$arg0
  set $bits=$arg1

  set $bit=0
    while ($bit < $bits)
	set $bit_pos = (1ull << ($bits - 1 - $bit))
	printf "%d", ($arg0 & $bit_pos) != 0
  	set $bit = $bit + 1
  end

  printf " <%lX>", $value
end

define print_bits8
  print_bits $arg0 8
end

define print_s_bits8
  printf $arg0
  print_bits8 $arg1
end

# Print metadata information
define zpm
    printf          "Metadata Load Bits  "
    print_s_bits8 "\n     Mask:          " ZPointerLoadMetadataMask
    print_s_bits8 "\n     Good:          " ZPointerLoadGoodMask
    print_s_bits8 "\n     Remapped:      " ZPointerRemapped
    print_s_bits8 "\n     Bad:           " ZPointerLoadBadMask
    printf        "\n                    "
    printf        "\nMetadata Store Bits "
    print_s_bits8 "\n     Mask:          " ZPointerStoreMetadataMask
    print_s_bits8 "\n     Good:          " ZPointerStoreGoodMask
    print_s_bits8 "\n     Bad:           " ZPointerStoreBadMask
    print_s_bits8 "\n     MarkedYoung:   " ZPointerMarkedYoung
    print_s_bits8 "\n     MarkedOld:     " ZPointerMarkedOld
    print_s_bits8 "\n     Finalizable:   " ZPointerFinalizable
    printf        "\n"
end

# End of file
