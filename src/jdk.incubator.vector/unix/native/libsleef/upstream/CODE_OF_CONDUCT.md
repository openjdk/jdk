
# Community Guidelines for Sustainability of Open-Source Ecosystem

Version 1.0.1, 2024-12-29

Copyright Naoki Shibata 2024. https://github.com/shibatch/nofreelunch

This document is licensed under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).


## Preface

### Free-riding and the burnout problem of OSS developers

First of all, I would like to start by explaining a few economic
concepts. Suppose you have paid to buy a hamburger. Then, of course,
that hamburger is yours and you have all the right to decide what to
do with it. You can eat all the hamburger without sharing it with your
friends. But what if you are paying for flood control, i.e., the
maintenance of rivers? Flood control is essential to building a modern
city, but it is almost impossible to limit the people who will benefi
from flood control. If you pay for the maintenance of the river, your
friends will automatically benefit from it too. Goods and services
such as parks, public roads, fire protection, police, flood control
and knowledge are called public goods. A public good is defined as a
good that is both non-excludable (anyone can access it) and
non-rivalrous (one person’s use does not prevent another’s use). The
cost of preventing people from using public goods is significantly
high and it is difficult to collect a price for their use. If someone
bears the cost and provides a public good, those who do not bear the
cost can also receive the benefit. As a result, the incentive to bear
the cost of providing public goods does not work, and everyone tries
to get a free ride. In economics, a free rider is someone who enjoys a
benefit without paying for it.

OSS and free software (hereafter, OSS and free software are
collectively referred to as FOSS) are public goods because they are
both non-excludable and non-rivalrous, and thus they are being
free-ridden by a large number of people and companies. It is
undeniable that FOSS has become popular because they can be free-ridden
as much as people want. But, development and maintenance of FOSS canno
proceed unless someone contributes resources. If no user pays for it,
it is a natural consequence that maintenance will eventually cease to
continue, which in essence means that the FOSS ecosystem is no
sustainable. FOSS developers have little or no financial incentive to
continue maintenance and development. As a result, FOSS developers tend
to stop maintaining their projects much sooner than users expect,
often abruptly. This problem is called the FOSS developer "burnout"
problem. To make the FOSS ecosystem sustainable, the cost of
maintenance must continue to be supplied from somewhere. Downloading
software may seem like less of an incentive to pay a fee because you
cannot see the face of the developer and the software is only
information and not substance. However, there are real people involved
in the development and maintenance of the software, and real resources
are committed for this purpose. Needless to say, the burden of those
costs should not be placed on the developers and maintainers.


### Lack of funds is not the cause of the problem

Let me explain in simpler language. Imagine that you are offered a
free lunch somewhere. In such a case, a rather large number of people
would say, "Who is covering the cost of this lunch? Let me bear the
cost of what I eat." But when it comes to using FOSS, the number of
such people is much smaller. For some reason, people take it for
granted that I provide a free lunch and that everyone else should
receive the lunch I provide without paying for it. And then there are
those who sell what is provided free of charge to others at a
price. Certainly, as a free lunch provider, I don't forbid that, bu
isn't that making too much use of the generosity? If I ask those
people to donate, sometimes they do, but often they look at me as if
donating is something special. And when I stop offering free lunch,
people say I have "burned out." Is it appropriate to call it “burnout”
that I have run out of ingredients to prepare a free lunch? It doesn'
have to be me who provides the ingredients for the free lunch, does
it? It would be fine if the others could supply all the ingredients
and prepare all the lunches instead, but this usually doesn'
happen. Because the lunches prepared by others are not that tasty. Or
they don't want to provide such a good lunch for free. If those who
are making a profit give some of it back, then I can continue to offer
free lunches. I think everyone would be happier that way, don't you?

The problem of developer "burnout" becomes a problem because there is
still commercial value in the software. If the software has no value
anymore, it would not be called "burnout." In such a case, the projec
simply "fades away" without anyone noticing. If no one needs free
lunch anymore, then no one will have a problem when I stop providing
free lunch. In other words, the reason developers "burn out" is no
because the funds are nowhere to be found, but because the funds do
not reach them. In some cases, companies are making huge profits by
using FOSS. If we can get these funds to reach the developers, the
problem can be solved.


### Noncommercial license does not solve the problem

Some may argue that if that is the case, then I should just make the
license noncommercial. However, if I prohibit commercial use, even if
conditionally, adopting the software will require complex
deliberations within the company. The story is relatively simple if
the number of computers that will run the software is known in advance
and only that number of licenses need to be purchased. However, the
use of FOSS is not limited to this. One of the advantages of FOSS is
that it can be easily adopted when the number of computers running the
software is not known in advance. If a license server were to be se
up to monitor software usage, there would be increased hassles with
server maintenance and security. If the company is required to pay,
for example, a certain percentage of the profits earned, the paperwork
for this would become cumbersome, and it would be necessary to publish
the figures on which the payment is based.

Here, I would like to explain another concept in economics. Consider
the case where farmers graze cows on pastureland. If there are too
many cows, the pasture will become desolate. If the pasture is owned
by one farmer, he/she adjusts the number of cows so that the pasture
does not become desolate. However, if a pasture is owned by multiple
farmers, there is no advantage in reducing the number of cows because
if one farmer reduces the number of cows, the others would increase
it. Therefore, each farmer tries to increase the number of cows to
maximize his/her own profit. As a result, the pastureland becomes
desolate due to the fact that it is common land. This is called the
tragedy of the commons.

Companies are, after all, organizations that try to maximize
shareholder profit. They try to use as much free stuff as possible,
while they are less likely to use software that is not free for
commercial use because of the complicated procedures to use it. There
used to be many free-of-charge software products that prohibited
commercial use, but many of them were not used much and eventually
abandoned. In other words, whether the license prohibits commercial
use or not, the developers will not be paid after all. From a
different perspective, this means that the pasture of FOSS developers
is being exploited until it becomes desolate. Even if each company is
aware of the fact that it is exploiting FOSS developers, it has little
incentive to stop, because even if one company stops, others will
continue, and the result will be the same. Thus, it is unlikely that a
company will voluntarily offer to make a donation, because even if
only their company makes a donation, the result will not be much
different if other companies do not also make donations. This
structure of the problem is the same as the tragedy of the commons. A
license that prohibits commercial use does not solve this problem.

FOSS developers have to go around on their own to find companies tha
use their software and ask them for donations. There is no way to even
know what a reasonable donation amount would be. In order to maximize
shareholder profit, companies tend to refrain from activities tha
would benefit other companies, and as a result, they are often
reluctant to contribute to society as a whole. Often a company needs a
particular reason as to why it makes a particular contribution. In
other words, a company needs an explanation of how its contribution
will specifically benefit the company. However, most FOSS products are
not designed to benefit a specific company. FOSS projects cannot be run
only by members who participate from companies who think only of their
own convenience. It is not surprising that contributions made under a
declaration that only contributions beneficial to a particular
organization can be made are less appreciated.


### Code of conduct comes to rescue

To begin with, the general public only understands that FOSS is
software that can be used for free, and there is no recognition tha
the general public is free-riding on various resources provided by
developers. There is a general lack of awareness that if everyone
continues to free-ride, those resources will quickly run out and the
project will not last long. On the other hand, companies need a reason
to pay for software that is labeled as free to use. In other words,
there must be some logical framework for the manager to persuade his
or her bosses to do this. For FOSS developers, the only place to write
such a prohibition on free-riding was in the distribution
license. However, since the distribution license is legally binding,
even the slightest restriction on the conditions of use would preven
people from using the software, as mentioned above.

Codes of conduct are not considered legally binding, and their
contents are often unchecked by corporate legal departments.
Nevertheless, their contents are similar to rules, and they are
presented alongside distribution licenses. Incorporating a code of
conduct into FOSS has already been established to some
extent. Originally, a code of conduct was a series of practices that a
company requires its employees to adhere to. Since this has expanded
to FOSS projects, we can expect that there is little risk of companies
completely ignoring it. A code of conduct is a set of rules tha
defines the normative behavior and responsibilities of individuals,
parties, and groups. In other words, a code of conduct is a definition
of the behaviors of members that are desirable and undesirable for a
project. By having each member in the project agree to a code of
conduct, the members are made aware that certain behaviors are
undesirable for the project. In other words, members will not be able
to openly engage in behavior that is undesirable for the
project. Since a code of conduct is not legally binding, members would
be able to continue to participate in the project without agreeing to
the code of conduct or while violating it. However, even in that case,
the fact that a particular member did not agree to or violated the
code of conduct is visible. The purpose of having members make a
pledge not to engage in undesirable behavior is not to exclude members
who disagree or violate the code from the project, but to make other
members aware of it. This allows the "name and shame" approach to
discourage members from taking undesirable actions. If a particular
member engages in an undesirable behavior, other members become aware
of it, and the member with the undesirable behavior is excluded from
participation in the overall decision-making process of the
project. In other words, members with undesirable behavior will have
difficulty participating in decision-making within the project. This
assumes, of course, that members do not lie.


### How our guidelines work

Community Guidelines for Sustainability of Open-Source Ecosystem are a
code of conduct aiming to alleviate the burnout problems of FOSS
developers. Our guidelines use the nature of the code of conduc
described above to discourage free-riding on FOSS projects. Since a
code of conduct defines desirable and undesirable behaviors of projec
members, it is not possible to directly ask companies to comply with
the code. Therefore, our guidelines ask members representing a company
to pledge that they will make effort to ensure that the company to
which they belong does not free-ride. If a member's company continues
to free-ride, that member can be regarded as not making sufficien
efforts to ensure that the company does not free-ride. In addition,
our guidelines do not require only that each company does no
free-ride on projects that have adopted our guidelines, but also tha
they do not free-ride on FOSS projects in general. As there becomes
greater public awareness of the importance of discouraging free-riding
in order to sustain FOSS projects, it will become more difficult for
companies to openly disagree with our guidelines. As more projects
adopt our guidelines, we can expect that free-riding on FOSS projects
in general will be discouraged.

Now, based on the above, will this scheme really work? What is mos
concerning is the part that although prohibiting commercial use of
software in a distribution license does not work, discouraging
free-riding in a code of conduct will work. The reason why prohibiting
commercial use does not work is that it requires a lot of internal
deliberation and procedures within the company before the software can
be used, and a budget must be set aside to purchase the software
license at this point. Distribution licenses are supposed to be
legally binding, which is incompatible with asking licensees to take
some action without legally binding them to do so. On the other hand,
a code of conduct, by its very nature, only prescribes the desired
behavior of project members, not the conditions for the use of the
software. The existence of a code of conduct makes it possible to
check whether each member is behaving in a way that is desirable for
the project. If companies continue to free-ride on FOSS, this will
become visible to the public and subject to social criticism. It would
be a serious PR loss for a company if it were to make the news tha
the company gets disciplined in a prominent FOSS project. Therefore, we
can expect that companies will voluntarily make contributions even
without a request from the project side.

One of the purposes of creating new guidelines is to lower the
workload on the maintainers. Adopting our guidelines will only require
little work on the project. What is required in the project is to
ensure transparency in project management so that public scrutiny can
be conducted. Our guidelines will not work well if the public thinks
that opaque decisions made within the project are inevitable because
they were made for some reason that cannot be disclosed. For the
guidelines to work well, the public must be able to judge the
appropriateness of each decision made within the project. Transparency
in project management would also have positive results in terms of
ensuring diversity in the project.

What our guidelines require is that each member takes action to
discourage free-riding. Thus, even if a member's company is
free-riding, that company will not be immediately denounced. A
company's free-riding will only be brought to attention if it becomes
a problem over some extended period of time. For companies, it is only
when their own employees participate in a project that they need to
comply with the intent of the code of conduct, which is usually long
after the company has started using the software. Thus, the use of the
software itself can be started easily, and minor violations can be
tolerated. In addition, companies can decide the budget for their
contribution to FOSS projects on their own discretion.

At first glance, this might seem to mean that companies can continue
to free-ride without worrying about the guidelines by silently
allowing their employees to continue using the software without having
them participate in the project. However, for companies that use FOSS
extensively, not being involved in FOSS projects at all would cause
various practical disadvantages. In addition, if the status of FOSS use
and contribution to the project by each company becomes clear, the
existence of large FOSS user companies that continue to free-ride in
silence will naturally emerge.

This may also mean that little-known companies will not be subject to
much public scrutiny, and only well-known companies that use FOSS on a
large scale will be significantly affected by the guidelines. However,
this has the advantage of supporting companies that use FOSS on a small
scale, thereby expanding the use of FOSS. Rather than going around
pointing out every minor violation, we can expect to bring long-term
benefits to the FOSS ecosystem by fostering companies that use FOSS on a
large scale. What is important is visibility into the use of FOSS in
each company, and for this purpose, it may be reasonable to mandate a
usage indication in the distribution license.

You might be wondering why our guidelines target only companies, even
though individuals are also free-riding. This is due to the
limitations of how our guidelines work. The basic stance of our
guidelines is to request uniformly the same content regardless of
whether the member belongs to a company or not. This is because
confusion arises in the definition of the terms if we try to
distinguish between companies and individuals. If members
participating as individuals were asked to donate to the project, they
would effectively be excluded from participating in the project.
Thus, all that a member participating as an individual can do to deter
free-riding is to call out to their friends not to free-ride, which is
effectively the same as doing nothing. If donations were collected
from individual users in a thin and broad base, the total amount could
be large. However, it would be too costly to track the amount of each
individual user's donation, and it would be difficult to establish a
practical mechanism for this purpose.

You might also wonder whether educational and research institutions
are also included in the scope of free-ride deterrence. It is
ultimately public scrutiny that determines whether the objectives of
the guidelines are being followed. Also, the project has its own
policy and philosophy independent of this. Educational and research
institutions are generally believed to support FOSS operations directly
or indirectly by the nature of their work. Thus, it can be assumed
that whether they are taking specific actions to deter free-riding is
not an issue. In reality, however, in many cases, even educational and
research institutions do not support FOSS operations in their work
content. Some institutions clearly disregard their contribution to FOSS
projects. In such cases, free-riding should be considered a problem
even for educational and research institutions. In addition, if FOSS
plays an important role in a research project with a large research
budget, it is only natural that the research project should make a
commensurate contribution to the associated FOSS project. When
applying for a research budget, the cost of the contribution to the
relevant projects of the FOSS to be used in the research should be
accounted for. I hope that the status of support for FOSS by
educational and research institutions, including indirect ones, will
become visible.

There is another reason why companies should pay for the maintenance
of FOSS. Public goods such as flood control and public road
construction are usually paid for by taxpayers. However, if we try to
do the same with FOSS maintenance, we will undoubtedly see terrible
results. Because it is difficult to evaluate which FOSS is useful,
large amounts of taxpayer money will be spent on maintenance of
software that no one has ever heard of. There is also a problem tha
it is difficult to coordinate among the countries to which the members
belong, since the development of FOSS takes place across countries. By
having companies fund software that is valuable to them, we can expec
that truly valuable software will be funded.


## Positioning and purpose of the guidelines

Our guidelines summarize the standards of practice that the
participating members of the project are expected to follow in order
to facilitate the promotion and operation of the project. Our
guidelines are not a set of rules, and no penalties or other
consequences for violations are set forth in the guidelines. However,
it is possible that your opinion will not be respected within the
community if you disagree with or violate these guidelines. Since
there is no provision in the software distribution license prohibiting
the removal of the guidelines in a derivative project, you can fork
the project to remove the guidelines.

The guidelines ask members representing companies to pledge that they
will make efforts to ensure that their companies do not engage in
free-riding. However, this is nominal and members are not actually
asked to take any specific action. What actually matters is the
attitude of the company to which the member belongs, not the specific
actions of the member.


## The guidelines

The primary purpose for establishing our guidelines is to improve the
sustainability of the FOSS ecosystem by encouraging companies tha
directly benefit from the commercial use of OSS or free software to
contribute to the relevant projects to serve society as a whole. The
members of the project are asked to help raise awareness to make this
happen.

The second purpose is to prevent conflicts among the members. Although
this project is a software development project, political discussions
may be sometimes required to keep the project moving forward. Some of
the guidelines summarize the items that each member is expected to
follow in order to avoid conflicts among members and to promote calm
and smooth discussions.


### Striving to change perceptions about the use of OSS and free software

* Project members strive to promote the public awareness that use of
  open source or free software for free without any contribution is
  free-riding and if everyone continues to free-ride, the project will
  not last long.

* Project members strive to promote awareness of the existence,
  philosophy, purpose, and content of these guidelines to those who
  directly or indirectly use open source or free software.

* Project members strive to promote the awareness that it is natural
  for organizations that make a profit from the commercial use of open
  source or free software to make contribution commensurate with the
  increased profits from the use of the software to relevant projects,
  where the contribution is made in order for the project to serve
  society as a whole. This contribution must not be biased to benefi
  any specific organizations beyond the purpose or original nature of
  the project. On top of that, the contribution herein refers to
  providing the following items.

  * Financial suppor
  * Contributing code or documentation
  * Providing assistance with tasks necessary to maintain the projec
  * Providing in-kind equipment, services, and software necessary to
    run the projec
  * Providing any other items needed for the projec

* Project members strive to promote the awareness that organizations
  that benefit from the commercial use of open source or free software
  should voluntarily and actively offer to contribute to the projects,
  not wait until they are asked to do so by the projects.

* Project members strive to promote that companies should give high
  recognition to their members who contribute to open source or free
  software projects on the job.

* Project members strive to promote that if a company agrees with the
  objectives of the guidelines, it should indicate this in some way so
  that the public is aware of it.


### Compliance with laws

* Each member should comply with the laws of his/her own place of
  residence.
  * Each member should abide by laws of his/her own place of residence
    even if the laws do not have penal provisions.

* Each member should not follow any request of anti-social groups or
  cults.
  * The terms "antisocial organization" and "cult" herein refer to
    organizations officially recognized as such in each member's place
    of residence. The same applies hereinafter.


### Maintaining transparency in project operations

* Each member in the project should disclose information regarding his
  or her identity and affiliation when necessary to carry out the
  project. In requesting disclosure of the identity and affiliation of
  each member, an explanation should be given as to why this is
  necessary to carry out the project.

* In engaging in activities within the project, each member should
  make an effort to disclose the basis for decisions in a manner tha
  the general public can see and understand.
  * If the basis for a decision cannot be disclosed, the reason why i
    cannot be disclosed should be explained as much as possible.
  * If each member notices that the basis for a decision or the reason
    why it cannot be disclosed has not been explained in the project,
    the member should prompt the person who made the decision to
    explain it, even if the matter is not directly related to the
    member's own, or if it is a past matter.


### Keeping calm and logical discussion

* Each member should refrain from posting a comment that is considered
  likely to cause strong emotions in those who read the comment.

* Each member should refrain from posting a comment that is not in
  line with the project's objectives.

* Each member of the project should not change the way he/she treats
  another member for any of the following reasons.
  * Discriminatory reasons (attributes that were determined at the
    time of the person's birth and cannot be changed)
  * Ideology or beliefs that are not relevant to the purpose of the
    projec
    * Whether or not each member belongs to an antisocial group or
      cult is always regarded as relevant to the purpose of the
      project. The project may ban a member who is found to be a
      member of an anti-social organization or cult for the sole
      reason of his/her membership in such an organization.
  * Inequalities that existed in the pas

* Each member of the project should treat another member equally a
  the time the action is taken.
  * In principle, affirmative action is not supported in this project.
    Imposing disadvantages on members who do not support affirmative
    action is production of new inequalities and should be avoided. If
    affirmative action must be taken, the disadvantage for it should
    be borne entirely by the supporters of affirmative action.


### To resolve the problem throughout the community

* Each member of the project should listen sincerely to the claims of
  other members that there have been violations of the above items and
  cooperates in resolving the problem.

* Project members should not leave it up to the project maintainers to
  resolve the problems.
  * The relationship between the maintainers and the other members is
    not that of parents and children. The maintainers only have the
    privileges of the websites related to the project and basically
    what you cannot do is not possible for the maintainers either.


### Not taking the guidelines as an absolute

* Each member should understand that each item of these guidelines is
  merely a means to achieve the objectives of the guidelines and to
  respect basic human rights.
  * Each member should always consider first how to achieve the
    objectives of the guidelines and respect basic human rights, and
    for this purpose, allow deviations from each item of these
    guidelines.
