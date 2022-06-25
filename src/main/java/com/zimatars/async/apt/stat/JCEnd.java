package com.zimatars.async.apt.stat;

import com.sun.source.tree.TreeVisitor;
import com.sun.tools.javac.tree.JCTree;

public class JCEnd extends JCTree.JCStatement {

    public static final JCEnd INSTANCE = new JCEnd();

    private JCEnd() {
    }

    @Override
    public Tag getTag() {
        return null;
    }

    @Override
    public void accept(Visitor v) {

    }

    @Override
    public Kind getKind() {
        return null;
    }

    @Override
    public <R, D> R accept(TreeVisitor<R, D> v, D d) {
        return null;
    }
}
