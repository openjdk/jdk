/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.org.glassfish.external.arc;

/**
  Taxonomy values.
  See http://opensolaris.org/os/community/arc/policies/interface-taxonomy/
  <p>
  <h3>Policy</h3>
    <ul>
    <li>Applies to All software produced by SMI</li>
    <li>Authority SAC</li>
    <li>Approval SAC</li>
    <li>Effective April, 1992</li>
    <li>Policy </li>
        <ul><li>All software interfaces must be classified according to this taxonomy.
        Interfaces are defined as APIs, files and directory structures, file formats, protocols,
        (sometimes) even performance and reliability behaviors, and any other attribute upon
        which another component might reasonably depend.</li>

        <li>An ARC must review, approve and archive the specification for all interfaces
        other than Project Private and Internal. Unreviewed, unapproved interfaces are assumed
        to be Internal. An adequate specification, suitable for archiving must exist for all
        interfaces submitted for review. Often Project Private interfaces are also reviewed if
        the presentation of them aids the understanding of the entire project or it is expected
        they will be promoted to a broader classification in the future.</li>

        <li>Adequate customer documentation must exist for all Public interfaces.
        It is strongly preferred that manual pages exist for all Public interfaces
        (supported on Solaris), even if only significant content of those pages are SYNOPSIS
        and ATTRIBUTES sections and a textual pointer to other documentation.
        Independent of the form of documentation delivery, the interface taxonomy commitment
        level must be presented to the consumer.</li>

        <li>In cases where the organization delivering the interface implementation does not
        control the interface specification, the controlling body must be be clearly cited
        in the documentation. In the case where a well-defined, versioned document is the
        specification, both the name and precise version must be be cited.</li>
        </ul>
    </ul>
  @author llc
 */
public enum Stability  {
    /**
    <pre>
    +----------------------------------------------------------------------------+
    | Committed (formerly Stable, Public; encompasses Standard, Evolving)        |
    |----------------------------------------------------------------------------|
    |   | Specification       | Open                                             |
    |   |---------------------+--------------------------------------------------|
    |   | Incompatible Change | major release (X.0)                              |
    |   |---------------------+--------------------------------------------------|
    |   | ARC review of Specs | Yes                                              |
    |   |---------------------+--------------------------------------------------|
    |   | Examples            | Compiler command line options,                   |
    |   |                     | hardware  (SBus, PCI, USB), RPC, POSIX utilities |
    +----------------------------------------------------------------------------+
    </pre>
        We publish the specification of these interfaces, typically as manual pages or other product documentation.
        We also tell customers we will remain compatible with them. (Scott McNealy's principle that "Compatibility is a
        constraint, not a goal") The intention of a Committed interface is to enable arbitrary third parties to develop
        applications to these interfaces, release them, and have confidence that they will run on all releases of the product
        after the one in which the interface was introduced, and within the same Major release. Even at a Major release,
        incompatible changes are expected to be rare, and to have strong justifications.
        <p>
        Committed interfaces are often proposed to be industry standards, as was the case with RPC.
        Also, interfaces defined and controlled as industry standards are most often treated as Committed interfaces.
        <p>
        These are interfaces whose specification is often under the provider's control or which are specified by a
        clearly versioned document controlled by a well-defined organization. If the interface specification is not
        under the implementation provider's control, the provider must be willing to fork from the interface specification
        if required to maintain compatibility. In the case of interface specifications controlled by a standards body,
        the commitment must be to a clearly identified version of the specification, minimizing the likelihood of an
        incompatible change (but it can happen through formal spec interpretations).
        <p>
        Also, if the interface specification is not under the control of the interface implementation provider,
        then the controlling body and/or public, versioned document must be be noted in the documentation.
        This is particularly important for specifications controlled by recognized standards organizations.
        <p>
        Although a truely exceptional event, incompatible changes are possible in any release if
        the associated defect is serious enough as outlined in the EXEMPTIONS section of this document or
        in a Minor release by following the End of Feature process.
     */
    COMMITTED( "Committed" ),

/**
 <pre>
    +--------------------------------------------------------------------------+
    | Uncommitted (formerly Unstable)                                          |
    |--------------------------------------------------------------------------|
    |   | Specification       | Open                                           |
    |   |---------------------+------------------------------------------------|
    |   | Incompatible Change | minor release (x.Y) with impact assessment     |
    |   |---------------------+------------------------------------------------|
    |   | ARC review of Specs | Yes                                            |
    |   |---------------------+------------------------------------------------|
    |   | Examples            | SUNW* package abbreviations, some config utils |
    +--------------------------------------------------------------------------+
    </pre>
    No guarantees are made about either source or binary compatibility of these interfaces
    from one Minor release to the next. The most drastic incompatible change of removal of
     the interface in a Minor release is allowed. Uncommitted interfaces are generally not
     appropriate for use by release-independent products.
    <p>
    Uncommitted is not a license for gratuitous change. Any incompatible changes to the
    interface should be motivated by true improvement to the interface which may include
    justifiable ease of use considerations. The general expectation is that Uncommitted
    interfaces are not likely to change incompatibly and if such changes occur they will be
    small in impact and should often have a mitigation plan.
    <p>
    Uncommitted interfaces generally fall into one of the following subcategories:
    <p>
    <ul>
        <li>
            Interfaces that are experimental or transitional.
            They are typically used to give outside developers early access to new or
            rapidly-changing technology, or to provide an interim solution to a problem where a
            more general solution is anticipated.
        </li>

        <li>
            Interfaces whose specification is controlled by an outside body and the
            implementation provider is only willing to commit to forking until the next minor
            release point should that outside body introduce incompatible change.
            Note that this "middle of the road" approach is often the best business decision
            when the controlling body hasn't established a history of respecting compatibility.
        </li>

        <li>
            Interfaces whose target audience values innovation (and possibly ease of use) over
            stability. This attribute is often asserted for administrative interfaces for higher
            web tier components. Note that ARC review may request data to support such an assertion.
        </li>
    <p>
    A project's intention to import an Uncommitted interface from another consolidation should
    be discussed with the ARC early. The stability classification of the interface -- or
    a replacement interface -- might be raised. The opinion allowing any project to import an
    Uncommitted interface must explain why it is acceptable, and a contract must be put into
    place allowing this use. For Sun products, the similarity in the usage of Uncommitted and
    Consolidation Private interfaces should be noted.
    <p>
    Any documentation for an Uncommitted interface must contain warnings that "these interfaces
    are subject to change without warning and should not be used in unbundled products".
    In some situations, it may be appropriate to document Uncommitted interfaces in white papers
    rather than in standard product documentation. When changes are introduced, the changes
    should be mentioned in the release notes for the affected release.
    <p>
    NOTE: If we choose to offer a draft standard implementation but state our intention to track
    the standard (or the portions we find technically sound or likely to be standardized),
    we set customer expectations for incompatible changes by classifying the interface Uncommitted.
    The interface must be reclassified Committed when standard is final.
    Such an intention could be encoded "Uncommitted->Committed".)
</pre>
 */
    UNCOMMITTED( "Uncommitted" ),


/**
<pre>
    +--------------------------------------------------------------------+
    | Volatile (encompasses External)                                    |
    |--------------------------------------------------------------------|
    |   | Specification       | Open                                     |
    |   |---------------------+------------------------------------------|
    |   | Incompatible Change | micro release (x.y.z) or patch release   |
    |   |---------------------+------------------------------------------|
    |   | Arc review of Specs | A precise reference is normally recorded |
    |   |---------------------+------------------------------------------|
    |   | Examples            | Gimp user interface, IETF internet-draft |
    +--------------------------------------------------------------------+
</pre>
        Volatile interfaces may change at any time and for any reason.
        <p>
        Use of the Volatile interface stability level allows interface providers to
        quickly track a fluid, rapidly evolving specification. In many cases, this is
        preferred to providing additional stability to the interface, as it may better
        meet the expectations of the consumer.
        <p>
        The most common application of this taxonomy level is to interfaces that are
        controlled by a body other than the final implementation provider, but unlike
        specifications controlled by standards bodies or communities we place trust in,
        it can not be asserted that an incompatible change to the interface
        specification would be exceedingly rare. In some cases it may not even be
        possible to clearly identify the controlling body. Although not prohibited by
        this taxonomy, the Volatile classification is not typically applied to
        interfaces where the specification is controlled by the implementation provider.
        <p>
        It should be noted that in some cases it will be preferable to apply a less
        fluid interface classification to an interface even if the controlling body is
        separate from the implementor. Use of the Uncommitted classification extends the
        stability commitment over micro/patch releases, allowing use of additional
        support models for software that depends upon these interfaces, at the potential
        cost of less frequent updates. Committed should be considered for required, core
        interfaces. If instability in the interface definition can't be reconciled with
        the requirement for stability, then alternate solutions should be considered.
        <p>
        This classification is typically used for free or open source software (FOSS),
        also referred to as community software, and similar models where it is deemed
        more important to track the community with minimal latency than to provide
        stability to our customers. When applying this classification level to community
        software, particular attention should be paid to the considerations presented in
        the preceding paragraph.
        <p>
        It also may be appropriate to apply the Volatile classification level to
        interfaces in the process of being defined by trusted or widely accepted
        organization. These are generically referred to as draft standards. An "IETF
        internet draft" is a well understood example of a specification under
        development.
        <p>
        There may also cases where Volatile is appropriate for experimental interfaces,
        but in most cases Uncommitted should be considered first.
        <p>
        Irrespective of the control of the specification, the Volatile classification
        must not be applied to "core" interfaces (those that must be used) for which no
        alternate (and more stable) interface exists. Volatile interfaces must also
        adhere to Sun internal standards in the following areas:
        <ul>
            <li>Security, Authentication</li>
            <li>The existence of (perhaps vestigial) Manual Pages and conformance to Sun section numbering</li>
            <li>File System Semantics (Solaris examples: /usr may be read-only, /var is where
            all significant run-time growth occurs, ...)</li>
        </ul>
        All Volatile interfaces should be labeled as such in all associated
        documentation and the consequence of using such interfaces must be explained
        either as part of that documentation or by reference.
        <p>
        Shipping incompatible change in a patch should be strongly avoided. It is not
        strictly prohibited for the following two reasons:
        <ul>
            <li>Since the patch provider may not be in explicit control of the changes to the
            upstream implementation, it cannot guarantee with reasonable assurance that an
            unidentified incompatibility is not present.
            </li>
            <li>A strong business case may exist for shipping a newer version as a patch if that
            newer version closes significant escalations.
            </li>
        </ul>
        In general, the intent of allowing change in a patch is to allow for change in
        Update Releases.
        <p>
        Sun products should consider Volatile interfaces as equivalent to Consolidation
        Private. A contract is required for use of these interfaces outside of the
        supplying consolidation.
        <p>
        Extreme care in the use of Volatile interfaces is required by layered or
        unbundled products. Layered products that depend upon Volatile interfaces must
        include as part of their review material how they intend to manage the
        dependency. It is not explicitly prohibited, but it is probable that unbundled
        or layered products that ship asynchronously from the Volatile interfaces upon
        which they depend will face nearly insurmountable difficulty in constructing a
        plan to manage such a dependency.
 */
    VOLATILE( "Volatile" ),

/**
<pre>
    +--------------------------------------------------------------------+
    | Not-an-interface                                                   |
    |--------------------------------------------------------------------|
    |   | Specification       | None                                     |
    |   |---------------------+------------------------------------------|
    |   | Incompatible Change | micro release (x.y.z) or patch release   |
    |   |---------------------+------------------------------------------|
    |   | Arc review of Specs | None                                     |
    |   |---------------------+------------------------------------------|
    |   | Examples            | CLI output, error text                   |
    +--------------------------------------------------------------------+
</pre>
        In the course of reviewing or documenting interfaces, the situation often occurs
        that an attribute will be present which may be inferred to be an interface, but
        actually is not. A couple of common examples of this are output from CLIs
        intended only for human consumption and the exact layout of a GUI.
        <p>
        This classification is simply a convenience term to be used to clarify such
        situations where such confusion is identified as likely. Failure to apply this
        term to an attribute is no indication that said attribute is some form of
        interface. It only indicates that the potential for confusion was not
        identified.
 */
    NOT_AN_INTERFACE( "Not-An-Interface" ),

    /**
        See: http://opensolaris.org/os/community/arc/policies/interface-taxonomy/
        <p>
        Javadoc or other means should establish the nature of the private interface.
     */
    PRIVATE( "Private" ),


    /**
        Not a formal term. Indicates that the interface, while visible, is experimental,
        and can be removed at any time.
     */
    EXPERIMENTAL( "Experimental" ),

    /**
        Interrim classification; a real one should be chosen asap.
     */
    UNSPECIFIED( "Unspecified" );

    private final String mName;
    private Stability( final String name ) { mName = name; }

    public String toString() { return mName; }
}
