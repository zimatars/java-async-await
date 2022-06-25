package com.zimatars.async.apt.util;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;

public class JavacUtil {

    public static <T extends JCTree, U extends JCTree> T setGeneratedBy(T node, U source) {
        if (node == null) return null;
//        node.setPos(source.getStartPosition());
        node.setPos(-1);
        return node;
    }

    public static <T extends JCTree, U extends JCTree> T recursiveSetGeneratedBy(T node, U source) {
        if (node == null) return null;
//        setGeneratedBy(node, source);
//        node.accept(new MarkingScanner(source));
        return node;
    }

    private static class MarkingScanner extends TreeScanner {
        private final JCTree source;

        MarkingScanner(JCTree source) {
            this.source = source;
        }

        @Override
        public void scan(JCTree tree) {
            if (tree == null) return;
            setGeneratedBy(tree, source);
            super.scan(tree);
        }
    }
}
