package jdk.internal.natives.include;

import jdk.internal.ValueBased;
import jdk.internal.natives.HasSegment;
import jdk.internal.natives.StructMapper;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.invoke.VarHandle;

import static jdk.internal.natives.CLayouts.*;

/*
 Generated partly via:

 jextract --source -t jdk.internal.natives.include \
 -I /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/ \
 /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/net/if.h

 */

/**
 * {@snippet :
 * struct ifconf {
 *     int ifc_len;
 *     union  ifc_ifcu;
 * };
 * }
 */
@ValueBased
public interface IfConf extends HasSegment {

    StructLayout LAYOUT = MemoryLayout.structLayout(
            C_INT.withName("ifc_len"),
            IfcU.LAYOUT.withName("ifc_ifcu")
    ).withName("ifconf");

    StructMapper<IfConf> MAPPER = IfConfImpl.mapper();

    int ifc_len();

    void ifc_len(int value);

    IfcU ifc_ifcu();

    static IfConf of(MemorySegment segment) {
        return MAPPER.of(segment);
    }

    /**
     * {@snippet :
     * union {
     *     caddr_t ifcu_buf;
     *     struct ifreq* ifcu_req;
     * };
     * }
     */
    interface IfcU extends HasSegment {

        UnionLayout LAYOUT = MemoryLayout.unionLayout(
                C_POINTER.withName("ifcu_buf"),
                C_POINTER.withName("ifcu_req")
        );;

        StructMapper<IfcU> MAPPER = IfConfImpl.IfcUImpl.mapper();

        // Todo: Add accessors for ifcu_buf

        /**
         * Setter for field:
         * {@snippet :
         * caddr_t ifcu_buf;
         * }
         */
        void ifcu_buf(MemorySegment x);



        IfReq ifcu_req();

        // Todo: Add setter

    }

}
