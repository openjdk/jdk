//   Copyright Naoki Shibata and contributors 2010 - 2024.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

// Always use -ffp-contract=off option to compile SLEEF.

#include <stdio.h>

#include <assert.h>
#include <stdint.h>
#include <limits.h>

#include "misc.h"
#include "quaddef.h"

#ifdef DORENAME
#include "rename.h"
#endif

#if (defined(_MSC_VER))
#pragma fp_contract (off)
#endif

static INLINE CONST Sleef_quad mlaq(Sleef_quad x, Sleef_quad y, Sleef_quad z) { return x * y + z; }
static INLINE CONST int64_t xrintq(Sleef_quad x) { return x < 0 ? (int64_t)(x - 0.5) : (int64_t)(x + 0.5); }
static INLINE CONST int64_t xceilq(Sleef_quad x) { return (int64_t)x + (x < 0 ? 0 : 1); }
static INLINE CONST Sleef_quad xtruncq(Sleef_quad x) { return (Sleef_quad)(int64_t)x; }

static INLINE CONST int xisnanq(Sleef_quad x) { return x != x; }
static INLINE CONST int xisinfq(Sleef_quad x) { return x == SLEEF_INFINITYq || x == -SLEEF_INFINITYq; }
static INLINE CONST int xisminfq(Sleef_quad x) { return x == -SLEEF_INFINITYq; }
static INLINE CONST int xispinfq(Sleef_quad x) { return x == SLEEF_INFINITYq; }

static INLINE CONST Sleef_quad xfabsq(Sleef_quad x) {
  union {
    Sleef_quad q;
    uint64_t u[2];
  } cnv;

  cnv.q = x;
  cnv.u[1] &= UINT64_C(0x7fffffffffffffff);
  return cnv.q;
}

//

#ifndef NDEBUG
static int checkfp(Sleef_quad x) {
  if (xisinfq(x) || xisnanq(x)) return 1;
  return 0;
}
#endif

static INLINE CONST Sleef_quad upperq(Sleef_quad d) {
  union {
    Sleef_quad q;
    uint64_t u[2];
  } cnv;

  cnv.q = d;
  cnv.u[0] &= ~((UINT64_C(1) << (112/2+1)) - 1);
  return cnv.q;
}

static INLINE CONST Sleef_quad2 dq(Sleef_quad h, Sleef_quad l) {
  Sleef_quad2 ret;
  ret.x = h; ret.y = l;
  return ret;
}

static INLINE CONST Sleef_quad2 dqnormalize_q2_q2(Sleef_quad2 t) {
  Sleef_quad2 s;

  s.x = t.x + t.y;
  s.y = t.x - s.x + t.y;

  return s;
}

static INLINE CONST Sleef_quad2 dqscale_q2_q2_q(Sleef_quad2 d, Sleef_quad s) {
  Sleef_quad2 r;

  r.x = d.x * s;
  r.y = d.y * s;

  return r;
}

static INLINE CONST Sleef_quad2 dqneg_q2_q2(Sleef_quad2 d) {
  Sleef_quad2 r;

  r.x = -d.x;
  r.y = -d.y;

  return r;
}

static INLINE CONST Sleef_quad2 dqadd_q2_q_q(Sleef_quad x, Sleef_quad y) {
  // |x| >= |y|

  Sleef_quad2 r;

#ifndef NDEBUG
  if (!(checkfp(x) || checkfp(y) || xfabsq(x) >= xfabsq(y))) {
    fprintf(stderr, "[dqadd_q2_q_q : %g, %g]\n", (double)x, (double)y);
    fflush(stderr);
  }
#endif

  r.x = x + y;
  r.y = x - r.x + y;

  return r;
}

static INLINE CONST Sleef_quad2 dqadd2_q2_q_q(Sleef_quad x, Sleef_quad y) {
  Sleef_quad2 r;

  r.x = x + y;
  Sleef_quad v = r.x - x;
  r.y = (x - (r.x - v)) + (y - v);

  return r;
}

static INLINE CONST Sleef_quad2 dqadd_q2_q2_q(Sleef_quad2 x, Sleef_quad y) {
  // |x| >= |y|

  Sleef_quad2 r;

#ifndef NDEBUG
  if (!(checkfp(x.x) || checkfp(y) || xfabsq(x.x) >= xfabsq(y))) {
    fprintf(stderr, "[dqadd_q2_q2_q : %g %g]\n", (double)x.x, (double)y);
    fflush(stderr);
  }
#endif

  r.x = x.x + y;
  r.y = x.x - r.x + y + x.y;

  return r;
}

static INLINE CONST Sleef_quad2 dqadd2_q2_q2_q(Sleef_quad2 x, Sleef_quad y) {
  // |x| >= |y|

  Sleef_quad2 r;

  r.x  = x.x + y;
  Sleef_quad v = r.x - x.x;
  r.y = (x.x - (r.x - v)) + (y - v);
  r.y += x.y;

  return r;
}

static INLINE CONST Sleef_quad2 dqadd_q2_q_q2(Sleef_quad x, Sleef_quad2 y) {
  // |x| >= |y|

  Sleef_quad2 r;

#ifndef NDEBUG
  if (!(checkfp(x) || checkfp(y.x) || xfabsq(x) >= xfabsq(y.x))) {
    fprintf(stderr, "[dqadd_q2_q_q2 : %g %g]\n", (double)x, (double)y.x);
    fflush(stderr);
  }
#endif

  r.x = x + y.x;
  r.y = x - r.x + y.x + y.y;

  return r;
}

static INLINE CONST Sleef_quad2 dqadd2_q2_q_q2(Sleef_quad x, Sleef_quad2 y) {
  Sleef_quad2 r;

  r.x  = x + y.x;
  Sleef_quad v = r.x - x;
  r.y = (x - (r.x - v)) + (y.x - v) + y.y;

  return r;
}

static INLINE CONST Sleef_quad2 dqadd_q2_q2_q2(Sleef_quad2 x, Sleef_quad2 y) {
  // |x| >= |y|

  Sleef_quad2 r;

#ifndef NDEBUG
  if (!(checkfp(x.x) || checkfp(y.x) || xfabsq(x.x) >= xfabsq(y.x))) {
    fprintf(stderr, "[dqadd_q2_q2_q2 : %g %g]\n", (double)x.x, (double)y.x);
    fflush(stderr);
  }
#endif

  r.x = x.x + y.x;
  r.y = x.x - r.x + y.x + x.y + y.y;

  return r;
}

static INLINE CONST Sleef_quad2 dqadd2_q2_q2_q2(Sleef_quad2 x, Sleef_quad2 y) {
  Sleef_quad2 r;

  r.x  = x.x + y.x;
  Sleef_quad v = r.x - x.x;
  r.y = (x.x - (r.x - v)) + (y.x - v);
  r.y += x.y + y.y;

  return r;
}

static INLINE CONST Sleef_quad2 dqsub_q2_q2_q2(Sleef_quad2 x, Sleef_quad2 y) {
  // |x| >= |y|

  Sleef_quad2 r;

#ifndef NDEBUG
  if (!(checkfp(x.x) || checkfp(y.x) || xfabsq(x.x) >= xfabsq(y.x))) {
    fprintf(stderr, "[dqsub_q2_q2_q2 : %g %g]\n", (double)x.x, (double)y.x);
    fflush(stderr);
  }
#endif

  r.x = x.x - y.x;
  r.y = x.x - r.x - y.x + x.y - y.y;

  return r;
}

static INLINE CONST Sleef_quad2 dqdiv_q2_q2_q2(Sleef_quad2 n, Sleef_quad2 d) {
  Sleef_quad t = 1.0 / d.x;
  Sleef_quad dh  = upperq(d.x), dl  = d.x - dh;
  Sleef_quad th  = upperq(t  ), tl  = t   - th;
  Sleef_quad nhh = upperq(n.x), nhl = n.x - nhh;

  Sleef_quad2 q;

  q.x = n.x * t;

  Sleef_quad u = -q.x + nhh * th + nhh * tl + nhl * th + nhl * tl +
    q.x * (1 - dh * th - dh * tl - dl * th - dl * tl);

  q.y = t * (n.y - q.x * d.y) + u;

  return q;
}

static INLINE CONST Sleef_quad2 dqmul_q2_q_q(Sleef_quad x, Sleef_quad y) {
  Sleef_quad xh = upperq(x), xl = x - xh;
  Sleef_quad yh = upperq(y), yl = y - yh;
  Sleef_quad2 r;

  r.x = x * y;
  r.y = xh * yh - r.x + xl * yh + xh * yl + xl * yl;

  return r;
}

static INLINE CONST Sleef_quad2 dqmul_q2_q2_q(Sleef_quad2 x, Sleef_quad y) {
  Sleef_quad xh = upperq(x.x), xl = x.x - xh;
  Sleef_quad yh = upperq(y  ), yl = y   - yh;
  Sleef_quad2 r;

  r.x = x.x * y;
  r.y = xh * yh - r.x + xl * yh + xh * yl + xl * yl + x.y * y;

  return r;
}

static INLINE CONST Sleef_quad2 dqmul_q2_q2_q2(Sleef_quad2 x, Sleef_quad2 y) {
  Sleef_quad xh = upperq(x.x), xl = x.x - xh;
  Sleef_quad yh = upperq(y.x), yl = y.x - yh;
  Sleef_quad2 r;

  r.x = x.x * y.x;
  r.y = xh * yh - r.x + xl * yh + xh * yl + xl * yl + x.x * y.y + x.y * y.x;

  return r;
}

static INLINE CONST Sleef_quad2 dqsqu_q2_q2(Sleef_quad2 x) {
  Sleef_quad xh = upperq(x.x), xl = x.x - xh;
  Sleef_quad2 r;

  r.x = x.x * x.x;
  r.y = xh * xh - r.x + (xh + xh) * xl + xl * xl + x.x * (x.y + x.y);

  return r;
}

static INLINE CONST Sleef_quad2 dqrec_q2_q(Sleef_quad d) {
  Sleef_quad t = 1.0 / d;
  Sleef_quad dh = upperq(d), dl = d - dh;
  Sleef_quad th = upperq(t), tl = t - th;
  Sleef_quad2 q;

  q.x = t;
  q.y = t * (1 - dh * th - dh * tl - dl * th - dl * tl);

  return q;
}

static INLINE CONST Sleef_quad2 dqrec_q2_q2(Sleef_quad2 d) {
  Sleef_quad t = 1.0 / d.x;
  Sleef_quad dh = upperq(d.x), dl = d.x - dh;
  Sleef_quad th = upperq(t  ), tl = t   - th;
  Sleef_quad2 q;

  q.x = t;
  q.y = t * (1 - dh * th - dh * tl - dl * th - dl * tl - d.y * t);

  return q;
}

/*
static INLINE CONST Sleef_quad2 dqsqrt_q2_q2(Sleef_quad2 d) {
  Sleef_quad t = sqrt(d.x + d.y);
  return dqscale_q2_q2_q(dqmul_q2_q2_q2(dqadd2_q2_q2_q2(d, dqmul_q2_q_q(t, t)), dqrec_q2_q(t)), 0.5);
}
*/

//

EXPORT CONST Sleef_quad2 xsincospiq_u05(Sleef_quad d) {
  Sleef_quad u, s, t;
  Sleef_quad2 r, x, s2;

  u = d * 4;
  int64_t q = xceilq(u) & ~(int64_t)1;

  s = u - (Sleef_quad)q;
  t = s;
  s = s * s;
  s2 = dqmul_q2_q_q(t, t);

  //

  u = +0.1528321016188828732764080161368244291e-27Q;
  u = mlaq(u, s, -0.1494741498689376415859233754050616110e-24Q);
  u = mlaq(u, s, +0.1226149947504428931621181953791777769e-21Q);
  u = mlaq(u, s, -0.8348589834426964519785265770009675533e-19Q);
  u = mlaq(u, s, +0.4628704628834415551415078707261146069e-16Q);
  u = mlaq(u, s, -0.2041026339664143925641158896030605061e-13Q);
  u = mlaq(u, s, +0.6948453273886629408492386065037620114e-11Q);
  u = mlaq(u, s, -0.1757247673443401045145682042627557066e-8Q);
  u = mlaq(u, s, +0.3133616890378121520950407496603902388e-6Q);
  u = mlaq(u, s, -0.3657620418217725078660518698299784909e-4Q);
  u = mlaq(u, s, +0.2490394570192720160015798421577395304e-2Q);
  x = dqadd2_q2_q_q2(u * s, dq(-0.08074551218828078170696957048724322192457Q, 5.959584458773288360696286320980429277618e-36));
  x = dqadd2_q2_q2_q2(dqmul_q2_q2_q2(s2, x), dq(0.7853981633974483096156608458198756993698Q, 2.167745574452451779709844565881105067311e-35Q));

  x = dqmul_q2_q2_q(x, t);
  r.x = x.x + x.y;

  //

  u = -0.4616472554003168470361503708527464705e-29Q;
  u = mlaq(u, s, +0.4891528531228245577148587028696897180e-26Q);
  u = mlaq(u, s, -0.4377345071482935585011339656701961637e-23Q);
  u = mlaq(u, s, +0.3278483561449753435303463083506802784e-20Q);
  u = mlaq(u, s, -0.2019653396886554861865456720993185772e-17Q);
  u = mlaq(u, s, +0.1001886461636271957275884859852184250e-14Q);
  u = mlaq(u, s, -0.3898073171259675439843028673969857173e-12Q);
  u = mlaq(u, s, +0.1150115912797405152263176921581706121e-9Q);
  u = mlaq(u, s, -0.2461136950494199754009084018126527316e-7Q);
  u = mlaq(u, s, +0.3590860448591510079069203991167071234e-5Q);
  u = mlaq(u, s, -0.3259918869273900136414318317506198622e-3Q);
  x = dqadd2_q2_q_q2(u * s, dq(0.01585434424381550085228521039855226376329Q, 6.529088663284413499535484912972485728198e-38Q));
  x = dqadd2_q2_q2_q2(dqmul_q2_q2_q2(s2, x), dq(-0.308425137534042456838577843746129712906Q, -1.006808646313642786855469666154064243572e-35Q));

  x = dqadd2_q2_q2_q(dqmul_q2_q2_q2(x, s2), 1);
  r.y = x.x + x.y;

  //

  if ((q & 2) != 0) { s = r.y; r.y = r.x; r.x = s; }
  if ((q & 4) != 0) { r.x = -r.x; }
  if (((q+2) & 4) != 0) { r.y = -r.y; }

  if (xisinfq(d)) { r.x = r.y = SLEEF_NANq; }
  if (!xisinfq(d) && xfabsq(d) > TRIGRANGEMAX3) { r.x = r.y = 0; }

  return r;
}

EXPORT CONST Sleef_quad2 xsincospiq_u35(Sleef_quad d) {
  Sleef_quad u, s, t;
  Sleef_quad2 r;

  u = d * 4;
  int64_t q = xceilq(u) & ~(int64_t)1;

  s = u - (Sleef_quad)q;
  t = s;
  s = s * s;

  //

  u = -0.1485963032785725729464918728185622156e-24Q;
  u = mlaq(u, s, +0.1226127943866088943202201676879490635e-21Q);
  u = mlaq(u, s, -0.8348589518463078609690110857435995326e-19Q);
  u = mlaq(u, s, +0.4628704628547538824855302470312741438e-16Q);
  u = mlaq(u, s, -0.2041026339663972432248777826778586936e-13Q);
  u = mlaq(u, s, +0.6948453273886628726907826757576187848e-11Q);
  u = mlaq(u, s, -0.1757247673443401044967978719804318982e-8Q);
  u = mlaq(u, s, +0.3133616890378121520950114757196589206e-6Q);
  u = mlaq(u, s, -0.3657620418217725078660518414453815240e-4Q);
  u = mlaq(u, s, +0.2490394570192720160015798421435124000e-2Q);
  u = mlaq(u, s, -0.8074551218828078170696957048724041729e-1Q);
  u = mlaq(u, s, +0.7853981633974483096156608458198756994e+0Q);

  r.x = u * t;

  //

  u = +0.4862670988511544771355006256522366302e-26Q;
  u = mlaq(u, s, -0.4377265452147065611484052550741141029e-23Q);
  u = mlaq(u, s, +0.3278483433857326331665386021267750285e-20Q);
  u = mlaq(u, s, -0.2019653396755055912482006994709659430e-17Q);
  u = mlaq(u, s, +0.1001886461636180795663169552615123249e-14Q);
  u = mlaq(u, s, -0.3898073171259675007871885150022866077e-12Q);
  u = mlaq(u, s, +0.1150115912797405152123832255915284811e-9Q);
  u = mlaq(u, s, -0.2461136950494199754008784937314856168e-7Q);
  u = mlaq(u, s, +0.3590860448591510079069203583263258862e-5Q);
  u = mlaq(u, s, -0.3259918869273900136414318317180623832e-3Q);
  u = mlaq(u, s, +0.1585434424381550085228521039855096075e-1Q);
  u = mlaq(u, s, -0.3084251375340424568385778437461297129e+0Q);
  u = mlaq(u, s, 1.0Q);

  r.y = u;

  //

  if ((q & 2) != 0) { s = r.y; r.y = r.x; r.x = s; }
  if ((q & 4) != 0) { r.x = -r.x; }
  if (((q+2) & 4) != 0) { r.y = -r.y; }

  if (xisinfq(d)) { r.x = r.y = SLEEF_NANq; }
  if (!xisinfq(d) && xfabsq(d) > TRIGRANGEMAX3) { r.x = r.y = 0; }

  return r;
}

//

#ifdef ENABLE_MAIN
#include <stdio.h>
#include <stdlib.h>
int main(int argc, char **argv) {
  Sleef_quad a = -8.3998726984803832684266802333309369056312711821029e-09Q;
  Sleef_quad2 q = xsincospiq_u05(a);
  printf("    "); printf128(q.x); printf("\n");

  /*
  printf128(0.1Q); printf("\n");
  Sleef_quad2 q2 = dqmul_q2_q_q(0.1Q, 0.1Q);
  printf128(q2.x); printf("\n");
  printf128(q2.y); printf("\n");
  */
  /*
  printf("%s\n", toBCq(0.1Q));
  printf("%s\n", toBCq(upperq(0.1Q)));
  printf("%s\n", toBCq(0.1Q-upperq(0.1Q)));
  Sleef_quad2 q2 = dqmul_q2_q_q(0.1Q, 0.1Q);
  printf("%s + ", toBCq(q2.x));
  printf("%s\n", toBCq(q2.y));
  */
}
#endif
