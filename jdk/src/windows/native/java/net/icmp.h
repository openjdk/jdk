/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#ifndef ICMP_H
#define ICMP_H

/*
 * Structure of an internet header, naked of options.
 *
 * We declare ip_len and ip_off to be short, rather than ushort_t
 * pragmatically since otherwise unsigned comparisons can result
 * against negative integers quite easily, and fail in subtle ways.
 */
struct ip {
        unsigned char   ip_hl:4,        /* header length */
                        ip_v:4;         /* version */
        unsigned char   ip_tos;                 /* type of service */
        short   ip_len;                 /* total length */
        unsigned short ip_id;                   /* identification */
        short   ip_off;                 /* fragment offset field */
#define IP_DF 0x4000                    /* don't fragment flag */
#define IP_MF 0x2000                    /* more fragments flag */
        unsigned char   ip_ttl;                 /* time to live */
        unsigned char   ip_p;                   /* protocol */
        unsigned short ip_sum;          /* checksum */
        struct  in_addr ip_src, ip_dst; /* source and dest address */
};

/*
 * Structure of an icmp header.
 */
struct icmp {
        unsigned char   icmp_type;              /* type of message, see below */
        unsigned char   icmp_code;              /* type sub code */
        unsigned short icmp_cksum;              /* ones complement cksum of struct */
        union {
                unsigned char ih_pptr;          /* ICMP_PARAMPROB */
                struct in_addr ih_gwaddr;       /* ICMP_REDIRECT */
                struct ih_idseq {
                        unsigned short icd_id;
                        unsigned short icd_seq;
                } ih_idseq;
                int ih_void;

                /* ICMP_UNREACH_NEEDFRAG -- Path MTU Discovery (RFC1191) */
                struct ih_pmtu {
                        unsigned short ipm_void;
                        unsigned short ipm_nextmtu;
                } ih_pmtu;

                struct ih_rtradv {
                        unsigned char irt_num_addrs;
                        unsigned char irt_wpa;
                        unsigned short irt_lifetime;
                } ih_rtradv;
        } icmp_hun;
#define icmp_pptr       icmp_hun.ih_pptr
#define icmp_gwaddr     icmp_hun.ih_gwaddr
#define icmp_id         icmp_hun.ih_idseq.icd_id
#define icmp_seq        icmp_hun.ih_idseq.icd_seq
#define icmp_void       icmp_hun.ih_void
#define icmp_pmvoid     icmp_hun.ih_pmtu.ipm_void
#define icmp_nextmtu    icmp_hun.ih_pmtu.ipm_nextmtu
        union {
                struct id_ts {
                        unsigned int its_otime;
                        unsigned int its_rtime;
                        unsigned int its_ttime;
                } id_ts;
                struct id_ip  {
                        struct ip idi_ip;
                        /* options and then 64 bits of data */
                } id_ip;
                unsigned int id_mask;
                char    id_data[1];
        } icmp_dun;
#define icmp_otime      icmp_dun.id_ts.its_otime
#define icmp_rtime      icmp_dun.id_ts.its_rtime
#define icmp_ttime      icmp_dun.id_ts.its_ttime
#define icmp_ip         icmp_dun.id_ip.idi_ip
#define icmp_mask       icmp_dun.id_mask
#define icmp_data       icmp_dun.id_data
};

#define ICMP_ECHOREPLY          0               /* echo reply */
#define ICMP_ECHO               8               /* echo service */

/*
 * ICMPv6 structures & constants
 */

typedef struct icmp6_hdr {
        u_char   icmp6_type;    /* type field */
        u_char   icmp6_code;    /* code field */
        u_short  icmp6_cksum;   /* checksum field */
        union {
                u_int icmp6_un_data32[1];       /* type-specific field */
                u_short icmp6_un_data16[2];     /* type-specific field */
                u_char  icmp6_un_data8[4];      /* type-specific field */
        } icmp6_dataun;
} icmp6_t;

#define icmp6_data32    icmp6_dataun.icmp6_un_data32
#define icmp6_data16    icmp6_dataun.icmp6_un_data16
#define icmp6_data8     icmp6_dataun.icmp6_un_data8
#define icmp6_pptr      icmp6_data32[0] /* parameter prob */
#define icmp6_mtu       icmp6_data32[0] /* packet too big */
#define icmp6_id        icmp6_data16[0] /* echo request/reply */
#define icmp6_seq       icmp6_data16[1] /* echo request/reply */
#define icmp6_maxdelay  icmp6_data16[0] /* mcast group membership */

struct ip6_pseudo_hdr  /* for calculate the ICMPv6 checksum */
{
  struct in6_addr ip6_src;
  struct in6_addr ip6_dst;
  u_int       ip6_plen;
  u_int       ip6_nxt;
};

#define ICMP6_ECHO_REQUEST      128
#define ICMP6_ECHO_REPLY        129
#define IPPROTO_ICMPV6          58
#define IPV6_UNICAST_HOPS       4  /* Set/get IP unicast hop limit */


#endif
