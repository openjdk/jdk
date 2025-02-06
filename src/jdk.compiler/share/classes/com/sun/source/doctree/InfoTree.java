package com.sun.source.doctree;

import java.util.List;

public interface InfoTree extends BlockTagTree{
    List<? extends DocTree> getReference();
}
