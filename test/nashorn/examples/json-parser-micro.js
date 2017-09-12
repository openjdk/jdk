/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

function bench() {
    var start = Date.now();
    for (var i = 0; i < 2000; i++) {
        JSON.parse(String(json));
    }
    print("1000 iterations in", Date.now() - start, "millis");
}

var json = '[\
  {\
    "_id": "54ca34171d3ade49782294c8",\
    "index": 0,\
    "guid": "ed0e74d5-ac63-47b6-8938-1750abab5770",\
    "isActive": false,\
    "balance": "$1,996.19",\
    "picture": "http://placehold.it/32x32",\
    "age": 39,\
    "eyeColor": "green",\
    "name": "Rose Graham",\
    "gender": "male",\
    "company": "PRIMORDIA",\
    "email": "rosegraham@primordia.com",\
    "phone": "+1 (985) 600-3551",\
    "address": "364 Melba Court, Succasunna, Texas, 8393",\
    "about": "Sunt commodo cillum occaecat velit eu eiusmod ex eiusmod sunt deserunt nulla proident incididunt. Incididunt ullamco Lorem elit do culpa esse do ex dolor aliquip labore. Ullamco velit laboris incididunt dolor. Nostrud dolor sint pariatur fugiat ullamco exercitation. Eu laboris do cupidatat eiusmod incididunt mollit occaecat voluptate.",\
    "registered": "2014-03-13T12:05:14 -01:00",\
    "latitude": 18.55665,\
    "longitude": 81.641001,\
    "tags": [\
      "sint",\
      "Lorem",\
      "veniam",\
      "quis",\
      "proident",\
      "consectetur",\
      "consequat"\
    ],\
    "friends": [\
      {\
        "id": 0,\
        "name": "Evangelina Morgan"\
      },\
      {\
        "id": 1,\
        "name": "Saunders Snyder"\
      },\
      {\
        "id": 2,\
        "name": "Walker Wood"\
      }\
    ],\
    "greeting": "Hello, Rose Graham! You have 1 unread messages.",\
    "favoriteFruit": "strawberry"\
  },\
  {\
    "_id": "54ca34176790c4c60fcae085",\
    "index": 1,\
    "guid": "9dc42e4c-b58f-4d92-a2ee-968d2b627d92",\
    "isActive": true,\
    "balance": "$3,832.97",\
    "picture": "http://placehold.it/32x32",\
    "age": 40,\
    "eyeColor": "brown",\
    "name": "Delaney Cherry",\
    "gender": "male",\
    "company": "INJOY",\
    "email": "delaneycherry@injoy.com",\
    "phone": "+1 (807) 463-2295",\
    "address": "470 Hale Avenue, Mulberry, District Of Columbia, 5455",\
    "about": "Deserunt sit cupidatat elit Lorem excepteur ex. Magna officia minim cupidatat nulla enim deserunt. Amet ex in tempor commodo consequat non ad qui elit cupidatat esse labore sint.",\
    "registered": "2014-03-27T23:06:33 -01:00",\
    "latitude": -4.984238,\
    "longitude": 116.039285,\
    "tags": [\
      "minim",\
      "velit",\
      "aute",\
      "minim",\
      "id",\
      "enim",\
      "enim"\
    ],\
    "friends": [\
      {\
        "id": 0,\
        "name": "Barrera Flowers"\
      },\
      {\
        "id": 1,\
        "name": "Leann Larson"\
      },\
      {\
        "id": 2,\
        "name": "Latoya Petty"\
      }\
    ],\
    "greeting": "Hello, Delaney Cherry! You have 2 unread messages.",\
    "favoriteFruit": "strawberry"\
  },\
  {\
    "_id": "54ca3417920666f00c54bfc4",\
    "index": 2,\
    "guid": "f91e08f8-1598-49bc-a08b-bb48f0cc1751",\
    "isActive": true,\
    "balance": "$2,932.84",\
    "picture": "http://placehold.it/32x32",\
    "age": 28,\
    "eyeColor": "brown",\
    "name": "Mosley Hammond",\
    "gender": "male",\
    "company": "AQUACINE",\
    "email": "mosleyhammond@aquacine.com",\
    "phone": "+1 (836) 598-2591",\
    "address": "879 Columbia Place, Seymour, Montana, 4897",\
    "about": "Sunt laborum incididunt et elit in deserunt deserunt irure enim ea qui non. Minim nisi sint aute veniam reprehenderit veniam reprehenderit. Elit enim eu voluptate eu cupidatat nulla ea incididunt exercitation voluptate ut aliquip excepteur ipsum. Consequat anim fugiat irure Lorem anim consectetur est.",\
    "registered": "2014-07-27T05:05:58 -02:00",\
    "latitude": -43.608015,\
    "longitude": -38.33894,\
    "tags": [\
      "proident",\
      "incididunt",\
      "eiusmod",\
      "anim",\
      "consectetur",\
      "qui",\
      "excepteur"\
    ],\
    "friends": [\
      {\
        "id": 0,\
        "name": "Hanson Davidson"\
      },\
      {\
        "id": 1,\
        "name": "Autumn Kaufman"\
      },\
      {\
        "id": 2,\
        "name": "Tammy Foley"\
      }\
    ],\
    "greeting": "Hello, Mosley Hammond! You have 4 unread messages.",\
    "favoriteFruit": "apple"\
  },\
  {\
    "_id": "54ca341753b67572a2b04935",\
    "index": 3,\
    "guid": "3377416b-43a2-4f9e-ada3-2479e13b44b8",\
    "isActive": false,\
    "balance": "$3,821.54",\
    "picture": "http://placehold.it/32x32",\
    "age": 31,\
    "eyeColor": "green",\
    "name": "Mueller Barrett",\
    "gender": "male",\
    "company": "GROK",\
    "email": "muellerbarrett@grok.com",\
    "phone": "+1 (890) 535-2834",\
    "address": "571 Norwood Avenue, Westwood, Arkansas, 2164",\
    "about": "Occaecat est sunt commodo ut ex excepteur elit nulla velit minim commodo commodo esse. Lorem quis eu minim consectetur. Cupidatat cupidatat consequat sit eu ex non quis nulla veniam sint enim excepteur. Consequat minim duis do do minim fugiat minim elit laborum ut velit. Occaecat laboris veniam sint reprehenderit.",\
    "registered": "2014-07-18T17:15:35 -02:00",\
    "latitude": 10.746577,\
    "longitude": -160.266041,\
    "tags": [\
      "reprehenderit",\
      "veniam",\
      "sint",\
      "commodo",\
      "exercitation",\
      "cillum",\
      "sunt"\
    ],\
    "friends": [\
      {\
        "id": 0,\
        "name": "Summers Finch"\
      },\
      {\
        "id": 1,\
        "name": "Tracie Mcdaniel"\
      },\
      {\
        "id": 2,\
        "name": "Ayers Patrick"\
      }\
    ],\
    "greeting": "Hello, Mueller Barrett! You have 7 unread messages.",\
    "favoriteFruit": "apple"\
  },\
  {\
    "_id": "54ca34172775ab9615db0d1d",\
    "index": 4,\
    "guid": "a3102a3e-3f08-4df3-b5b5-62eff985d5ca",\
    "isActive": true,\
    "balance": "$3,962.27",\
    "picture": "http://placehold.it/32x32",\
    "age": 34,\
    "eyeColor": "green",\
    "name": "Patrick Foster",\
    "gender": "male",\
    "company": "QUAREX",\
    "email": "patrickfoster@quarex.com",\
    "phone": "+1 (805) 577-2362",\
    "address": "640 Richards Street, Roberts, American Samoa, 5530",\
    "about": "Aute occaecat occaecat ad eiusmod esse aliqua ullamco minim. Exercitation aute ut ex nostrud deserunt laboris officia amet enim do. Cillum officia laborum occaecat eiusmod reprehenderit ex et aliqua minim elit ex aliqua mollit. Occaecat dolor in fugiat laboris aliquip nisi ad voluptate duis eiusmod ad do.",\
    "registered": "2014-07-22T16:45:35 -02:00",\
    "latitude": 6.609025,\
    "longitude": -5.357026,\
    "tags": [\
      "ea",\
      "ut",\
      "excepteur",\
      "enim",\
      "ad",\
      "non",\
      "sit"\
    ],\
    "friends": [\
      {\
        "id": 0,\
        "name": "Duncan Lewis"\
      },\
      {\
        "id": 1,\
        "name": "Alyce Benton"\
      },\
      {\
        "id": 2,\
        "name": "Angelique Larsen"\
      }\
    ],\
    "greeting": "Hello, Patrick Foster! You have 1 unread messages.",\
    "favoriteFruit": "strawberry"\
  },\
  {\
    "_id": "54ca3417a190f26fef815f6d",\
    "index": 5,\
    "guid": "c09663dd-bb0e-45a4-960c-232c0e8a9486",\
    "isActive": false,\
    "balance": "$1,871.12",\
    "picture": "http://placehold.it/32x32",\
    "age": 20,\
    "eyeColor": "blue",\
    "name": "Foreman Chaney",\
    "gender": "male",\
    "company": "DEMINIMUM",\
    "email": "foremanchaney@deminimum.com",\
    "phone": "+1 (966) 523-2182",\
    "address": "960 Granite Street, Sunnyside, Tennessee, 1097",\
    "about": "Adipisicing nisi qui id sit incididunt aute exercitation veniam consequat ipsum sit irure. Aute officia commodo Lorem consequat. Labore exercitation consequat voluptate deserunt consequat do est fugiat nisi eu dolor minim id ea.",\
    "registered": "2015-01-21T00:18:00 -01:00",\
    "latitude": -69.841726,\
    "longitude": 121.809383,\
    "tags": [\
      "laboris",\
      "sunt",\
      "exercitation",\
      "enim",\
      "anim",\
      "excepteur",\
      "tempor"\
    ],\
    "friends": [\
      {\
        "id": 0,\
        "name": "Espinoza Johnston"\
      },\
      {\
        "id": 1,\
        "name": "Doreen Holder"\
      },\
      {\
        "id": 2,\
        "name": "William Ellison"\
      }\
    ],\
    "greeting": "Hello, Foreman Chaney! You have 5 unread messages.",\
    "favoriteFruit": "strawberry"\
  }\
]';

for (var i = 0; i < 100; i++) {
    bench();
}
