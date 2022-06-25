package com.zimatars.async.apt;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Names;
import com.zimatars.async.Async;
import com.zimatars.async.apt.transform.AsyncMethodTransformer;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@SupportedAnnotationTypes("com.zimatars.async.Async")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class AsyncProcessor extends AbstractProcessor {
    private Context context;
    private Messager messager;
    private JavacTrees trees;
    private TreeMaker maker;
    private Names names;
    private Types types;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.trees = JavacTrees.instance(processingEnv);
        this.context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.maker = TreeMaker.instance(context);
        this.names = Names.instance(context);
        this.types = Types.instance(context);
    }


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> set = roundEnv.getElementsAnnotatedWith(Async.class);
        AtomicReference<ListBuffer<JCTree>> reference = new AtomicReference<>(new ListBuffer<>());
        AtomicReference<JCTree.JCClassDecl> classTree = new AtomicReference<>();
        set.forEach(element -> {
            if (element instanceof Symbol.MethodSymbol) {
                classTree.set((JCTree.JCClassDecl) trees.getTree(((Symbol.MethodSymbol) element).owner));
            } else {
                messager.printMessage(Diagnostic.Kind.ERROR, "@Async element must be instanceof Symbol.MethodSymbol");
                return;
            }
            JCTree elementJCTree = trees.getTree(element);
            new AsyncMethodTransformer(classTree.get(), (JCTree.JCMethodDecl) elementJCTree, context, messager).transform(reference);
        });

        if (!reference.get().toList().isEmpty()) {
            classTree.get().defs = classTree.get().defs.prependList(reference.get().toList());
        }

        return true;
    }


}
