package com.zimatars.async.apt.transform;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.*;
import com.zimatars.async.apt.stat.JCEnd;
import com.zimatars.async.apt.stat.JCScopeSkip;
import com.zimatars.async.apt.util.JavacUtil;

import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class AsyncMethodTransformer {
    private final JCTree.JCClassDecl jcClassDecl;
    private final JCTree.JCMethodDecl jcMethodDecl;
    private final TreeMaker maker;
    private final int makerOriginalPos;
    private final Names names;
    private final Types types;
    private final Symtab syms;
    private final Messager messager;

    private int labelCount = 0;
    private Map<Name, JCTree.JCExpression> fieldTypes = new HashMap<>();

    private Set<Integer> caseSets = new HashSet<>();

    public AsyncMethodTransformer(JCTree.JCClassDecl jcClassDecl, JCTree.JCMethodDecl jcMethodDecl, Context context, Messager messager) {
        this.jcClassDecl = jcClassDecl;
        this.jcMethodDecl = jcMethodDecl;
        this.maker = TreeMaker.instance(context);
        this.names = Names.instance(context);
        this.types = Types.instance(context);
        this.syms = Symtab.instance(context);
        this.messager = messager;
        this.makerOriginalPos = this.maker.pos;
    }

    public void transform(AtomicReference<ListBuffer<JCTree>> reference) {
        validMethodDef();
        rewriteAsyncMethod(reference, jcMethodDecl, jcClassDecl);
    }

    public void validMethodDef() {
        Type resType = jcMethodDecl.restype.type;
        if (!resType.tsym.getQualifiedName().contentEquals("reactor.core.publisher.Mono")) {
            messager.printMessage(Diagnostic.Kind.ERROR, String.format("@Async method %s.%s must return Mono Type", jcClassDecl.sym.toString(), jcMethodDecl.name.toString()));
            throw new RuntimeException("validMethodDef fail");
        }
    }

    private void rewriteAsyncMethod(AtomicReference<ListBuffer<JCTree>> reference, JCTree.JCMethodDecl jcMethodDecl, JCTree.JCClassDecl jcClassDecl) {
//        this.maker.pos = -1;
        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();
        statements.append(makeContinuation(reference, jcMethodDecl, jcClassDecl));
        statements.append(makeReturnWrapCont());
        jcMethodDecl.body = maker.Block(0, statements.toList());
//        this.maker.pos = this.makerOriginalPos;
    }

    private JCTree.JCReturn makeReturnWrapCont() {
        JCTree.JCFieldAccess invokerSel = maker.Select(maker.Select(maker.Select(maker.Select(maker.Select(maker.Ident(names.fromString("com")),
                                                names.fromString("zimatars")),
                                        names.fromString("async")),
                                names.fromString("runtime")),
                        names.fromString("ContinuationInvoker")),
                names.fromString("invoke"));
        JCTree.JCLambda sinkLambda = maker.Lambda(List.of(maker.VarDef(maker.Modifiers(8589934592L), names.fromString("sink"), null, null)),
                maker.Apply(List.nil(), invokerSel, List.of(maker.Ident(names.fromString("sink")), maker.Ident(names.fromString("continuation")), maker.Literal(TypeTag.BOT, null))));

        return maker.Return(maker.Apply(List.nil(), maker.Select(maker.Ident(names.fromString("Mono")), names.fromString("create")), List.of(sinkLambda)));
    }

    private JCTree.JCVariableDecl makeContinuation(AtomicReference<ListBuffer<JCTree>> reference, JCTree.JCMethodDecl jcMethodDecl, JCTree.JCClassDecl jcClassDecl) {
        Name continuationName = names.fromString("continuation");
        Name continuationTypeName = names.fromString("Continuation");
        Name thisName = names.fromString("this");
        Name sentName = names.fromString("sent");
        ListBuffer<JCTree> continuationClassDefs = new ListBuffer<>();
        //int next;
        continuationClassDefs.append(maker.VarDef(maker.Modifiers(0), names.fromString("next"), maker.TypeIdent(TypeTag.INT), null));

        //case
        ListBuffer<JCTree.JCCase> caseList = new ListBuffer<>();
        doBlock(jcMethodDecl.body, caseList, new ArrayDeque<>(), null);
        caseList.append(maker.Case(null,
                List.of(maker.Throw(maker.NewClass(null, List.nil(), maker.Ident(names.fromString("IllegalStateException")), List.of(maker.Literal(TypeTag.CLASS, "illegal case value")), null)))));
        //switch
        JCTree.JCSwitch jcSwitch = maker.Switch(maker.Parens(maker.Ident(names.fromString("next"))), caseList.toList());
        //whileLoop
        JCTree.JCWhileLoop jcWhileLoop = maker.WhileLoop(maker.Parens(maker.Literal(TypeTag.BOOLEAN, 1)), jcSwitch);

        //fields varDef
        fieldTypes.forEach((k, v) -> continuationClassDefs.append(maker.VarDef(maker.Modifiers(0), k, v, null)));

        //getNext();
        JCTree.JCMethodDecl getNextMethodDecl = maker.MethodDef(maker.Modifiers(1), names.fromString("getNext"), maker.TypeIdent(TypeTag.INT), List.nil(), List.nil(), List.nil(),
                maker.Block(0, List.of(maker.Return(maker.Ident(names.fromString("next"))))), null);
        continuationClassDefs.append(getNextMethodDecl);

        ListBuffer<JCTree.JCStatement> genMethList = new ListBuffer<>();
        genMethList.append(JavacUtil.recursiveSetGeneratedBy(maker.VarDef(maker.Modifiers(0), names.fromString("next"), maker.TypeIdent(TypeTag.INT), maker.Select(maker.Ident(continuationName), names.fromString("next"))), jcMethodDecl));
        ListBuffer<JCTree.JCStatement> varWriteBackList = new ListBuffer<>();
        varWriteBackList.append(JavacUtil.recursiveSetGeneratedBy(maker.Exec(maker.Assign(maker.Select(maker.Ident(continuationName), names.fromString("next")), maker.Ident(names.fromString("next")))), jcMethodDecl));
        fieldTypes.forEach((k, v) -> {
            genMethList.append(JavacUtil.recursiveSetGeneratedBy(maker.VarDef(maker.Modifiers(0), k, v, maker.Select(maker.Ident(continuationName), k)), jcMethodDecl));
            varWriteBackList.append(JavacUtil.recursiveSetGeneratedBy(maker.Exec(maker.Assign(maker.Select(maker.Ident(continuationName), k), maker.Ident(k))), jcMethodDecl));
        });
        JCTree.JCTry aCaseTry = JavacUtil.setGeneratedBy(maker.Try(maker.Block(0, List.of(jcWhileLoop)), List.nil(), maker.Block(0, varWriteBackList.toList())), jcMethodDecl);
        genMethList.append(aCaseTry);


        JCTree.JCFieldAccess selectObject = maker.Select((maker.Select(maker.Ident(names.fromString("java")), names.fromString("lang"))), names.fromString("Object"));
        //$$gen$$
        JCTree.JCBlock genBlock = maker.Block(0, genMethList.toList());

        Name innerContName = names.fromString("Z$" + jcMethodDecl.name.toString() + "$Continuation$1");
        Name genMethName = names.fromString(jcMethodDecl.name.toString() + "$gen$1");
        JCTree.JCVariableDecl contParam = maker.VarDef(maker.Modifiers(Flags.PARAMETER), continuationName, null, null);
        JCTree.JCVariableDecl sentParam = maker.VarDef(maker.Modifiers(Flags.PARAMETER), names.fromString("sent"), null, null);


        JCTree.JCFieldAccess biFunctionTypeSel = maker.Select(maker.Select(maker.Select(maker.Ident(names.fromString("java")),
                                names.fromString("util")),
                        names.fromString("function")),
                names.fromString("BiFunction"));
        JCTree.JCVariableDecl genLambda = maker.VarDef(maker.Modifiers(0), genMethName, maker.TypeApply(biFunctionTypeSel, List.of(maker.Ident(innerContName), maker.Ident(names.fromString("Object")), maker.Ident(names.fromString("Mono")))),
                maker.Lambda(List.of(contParam, sentParam), genBlock));
        reference.get().append(genLambda);

        JCTree.JCFieldAccess contTypeSel = maker.Select(maker.Select(maker.Select(maker.Select(maker.Ident(names.fromString("com")),
                                        names.fromString("zimatars")),
                                names.fromString("async")),
                        names.fromString("runtime")),
                names.fromString("Continuation"));

        //continuation class def
        JCTree.JCClassDecl nestedContinuationClassDecl = maker.ClassDef(maker.Modifiers(1032), innerContName, List.nil(), null, List.of(contTypeSel), continuationClassDefs.toList());
        reference.get().append(nestedContinuationClassDecl);

        //resume(sent);
        JCTree.JCMethodDecl resumeMethodDecl = maker.MethodDef(maker.Modifiers(1), names.fromString("resume"), maker.Ident(names.fromString("Mono")),
                List.nil(), List.of(maker.VarDef(maker.Modifiers(8589934592L), names.fromString("sent"), maker.Ident(names.fromString("Object")), null)), List.nil(),
                maker.Block(0, List.of(maker.Return(
                        maker.Apply(List.nil(),
                                maker.Select(maker.Select(maker.Select(maker.Ident(jcClassDecl.name), names.fromString("this")), genMethName), names.fromString("apply")),
                                List.of(maker.Ident(thisName), maker.Ident(sentName)))
                ))), null);
        //anonymous class def
        JCTree.JCClassDecl anonymousContinuationClassDecl = maker.ClassDef(maker.Modifiers(0), names.fromString(""),
                List.nil(), null, List.nil(), List.of(resumeMethodDecl));
        //init new Continuation(){}
        JCTree.JCNewClass continuationNewClass = maker.NewClass(null, List.nil(), maker.Ident(innerContName), List.nil(), anonymousContinuationClassDecl);
        //Continuation continuation = new Continuation(){}
        JCTree.JCVariableDecl continuationVarDecl = maker.VarDef(maker.Modifiers(0), continuationName, contTypeSel, continuationNewClass);
        return continuationVarDecl;
    }

    private void doBlock(JCTree.JCBlock block, ListBuffer<JCTree.JCCase> caseStats, Deque<Integer> scope, CasePart casePart) {
        List<JCTree.JCStatement> stats = block.stats;
        Iterator<JCTree.JCStatement> iterator = stats.iterator();
        List<JCTree.JCStatement> newStats = List.nil();
        while (iterator.hasNext()) {
            JCTree.JCStatement stat = iterator.next();
            stat = writeWhileVarDecl(stat);
            if (isSuspend(stat)) {
                int label = getLabel();
                int nextLabel = getAndSetNextLabel();
                newStats = newStats.append(makeSetNextStat(nextLabel)).append(maker.Return(getInitAwaitMono(stat)));
                if (casePart == null) casePart = new CasePart(label, List.nil());
                JCTree.JCCase jcCase = makeCase(newStats, casePart);
                caseStats.add(jcCase);
                //resume case
                List<JCTree.JCStatement> remainStats = collectRemain(iterator);
                JCTree.JCStatement awaitAssign = makeAwaitAssign(stat);
                this.doBlock(maker.Block(0, remainStats), caseStats, scope, new CasePart(nextLabel, awaitAssign != null ? List.of(awaitAssign) : List.nil()));
            } else if (isReturn(stat)) {
                newStats = newStats.append(makeSetNextStat(-1)).append(stat);
                JCTree.JCCase jcCase = makeCase(newStats, casePart);
                caseStats.add(jcCase);
                getAndSetNextLabel();
            } else if (stat instanceof JCTree.JCIf && hasSuspend((JCTree.JCIf) stat)) {
                int label = getLabel();
                int nextLabel = getAndSetNextLabel();
                scope.push(nextLabel);
                if (casePart != null) {
                    casePart.headStats = casePart.headStats.appendList(newStats);
                } else {
                    casePart = new CasePart(label, newStats);
                }
                this.doIf((JCTree.JCIf) stat, collectRemain(iterator), caseStats, scope, casePart);
            } else if (stat == JCScopeSkip.INSTANCE) {
                Integer scopeVal = scope.pop();
                newStats = newStats.append(makeSetNextStat(scopeVal)).append(maker.Break(null));
                JCTree.JCCase jcCase = makeCase(newStats, casePart);
                caseStats.add(jcCase);
                getAndSetNextLabel();
                if (!caseSets.contains(scopeVal)) {
//                    this.doBlock(maker.Block(0,List.of(makeSetNextStat(nextLabel), maker.Break(null), JCEnd.INSTANCE)), caseStats, scope, new CasePart(scopeVal, List.nil()));
                    this.doBlock(maker.Block(0, collectRemain(iterator)), caseStats, scope, new CasePart(scopeVal, List.nil()));
                }
                return;
//                this.doBlock(maker.Block(0, collectRemain(iterator)), caseStats, scope, null);
            } else if (stat == JCEnd.INSTANCE) {
                JCTree.JCCase jcCase = makeCase(newStats, casePart);
                caseStats.add(jcCase);
                getAndSetNextLabel();
            } else {
                newStats = newStats.append(stat);
            }
        }
    }

    private void doIf(JCTree.JCIf stat, List<JCTree.JCStatement> remainStats, ListBuffer<JCTree.JCCase> caseStats, Deque<Integer> scope, CasePart casePart) {
        Deque<Integer> cloneScope = new ArrayDeque<>(scope);
        if (stat.elsepart == null) {
            stat.elsepart = maker.Block(0, List.nil());
        }
        int elseLabel = getAndSetNextLabel();
        JCTree.JCIf negationJump = maker.If(maker.Unary(JCTree.Tag.NOT, stat.getCondition()), maker.Block(0, List.of(makeSetNextStat(elseLabel), maker.Break(null))), null);
        casePart.headStats = casePart.headStats.append(negationJump);
        this.doBlock(maker.Block(0, remainStats.prependList(((JCTree.JCBlock) stat.thenpart).stats.append(JCScopeSkip.INSTANCE))), caseStats, scope, casePart);
        JCTree.JCStatement elsePart = stat.elsepart;
        if (elsePart instanceof JCTree.JCIf) {
            this.doIf((JCTree.JCIf) elsePart, List.nil(), caseStats, cloneScope, new CasePart(elseLabel, List.nil()));
        } else {
            this.doBlock(maker.Block(0, ((JCTree.JCBlock) elsePart).stats.append(JCScopeSkip.INSTANCE)), caseStats, cloneScope, new CasePart(elseLabel, List.nil()));
        }
    }

    static class CasePart {
        private Integer label;
        private List<JCTree.JCStatement> headStats;

        public CasePart(Integer label, List<JCTree.JCStatement> headStats) {
            this.label = label;
            this.headStats = headStats;
        }

        public Integer getLabel() {
            return label;
        }

        public void setLabel(Integer label) {
            this.label = label;
        }

        public List<JCTree.JCStatement> getHeadStats() {
            return headStats;
        }

        public void setHeadStats(List<JCTree.JCStatement> headStats) {
            this.headStats = headStats;
        }
    }


    private List<JCTree.JCStatement> collectRemain(Iterator<JCTree.JCStatement> iterator) {
        ListBuffer<JCTree.JCStatement> listBuf = new ListBuffer<>();
        iterator.forEachRemaining(listBuf::append);
        return listBuf.toList();
    }

    private JCTree.JCCase makeCase(List<JCTree.JCStatement> newStats, CasePart casePart) {
        int caseLabel;
        if (casePart != null) {
            newStats = casePart.headStats.appendList(newStats);
            caseLabel = casePart.getLabel();
        } else {
            caseLabel = getAndSetNextLabel();
        }
        caseSets.add(caseLabel);
        return maker.Case(maker.Literal(TypeTag.INT, caseLabel), newStats);
    }

    private JCTree.JCStatement writeWhileVarDecl(JCTree.JCStatement stat) {
        if (stat instanceof JCTree.JCVariableDecl) {
            JCTree.JCVariableDecl variableDecl = (JCTree.JCVariableDecl) stat;
            Name name = variableDecl.name;
            JCTree.JCAssign assign = maker.Assign(maker.Ident(name), variableDecl.getInitializer());
            assign.pos = stat.pos;
            JCTree.JCExpressionStatement assignStat = maker.Exec(assign);
            assignStat.pos = stat.pos;
            if (!fieldTypes.containsKey(name)) {
                fieldTypes.put(name, variableDecl.vartype);
            } else {
                throw new RuntimeException("repeat variableDecl");
            }
            return assignStat;
        }
        return stat;
    }

    private void makeFiledAccess(String access) {
        for (String word : access.split("\\.")) {
        }
    }

    private boolean hasSuspend(JCTree.JCIf ifStat) {
        if (ifStat.thenpart instanceof JCTree.JCBlock && anyMatchSuspend(((JCTree.JCBlock) ifStat.thenpart).stats)) {
            return true;
        }
        if (ifStat.elsepart == null) {
            return false;
        }
        if (ifStat.elsepart instanceof JCTree.JCBlock && anyMatchSuspend(((JCTree.JCBlock) ifStat.elsepart).stats)) {
            return true;
        }
        if (ifStat.elsepart instanceof JCTree.JCIf) {
            return hasSuspend((JCTree.JCIf) ifStat.elsepart);
        }
        return false;
    }

    private boolean anyMatchSuspend(List<JCTree.JCStatement> stats) {
        for (JCTree.JCStatement stat : stats) {
            if (isSuspend(stat)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSuspend(JCTree.JCStatement stat) {
        if (stat instanceof JCTree.JCVariableDecl) {
            JCTree.JCExpression init = ((JCTree.JCVariableDecl) stat).getInitializer();
            return isAwait(init);
        } else if (stat instanceof JCTree.JCExpressionStatement) {
            JCTree.JCExpression expr = ((JCTree.JCExpressionStatement) stat).expr;
            if (expr instanceof JCTree.JCAssign) {
                return isAwait(((JCTree.JCAssign) expr).rhs);
            } else if (expr instanceof JCTree.JCMethodInvocation) {
                return isAwait(expr);
            }
        }
        return false;
    }

    private boolean isAwait(JCTree.JCExpression init) {
        if (init instanceof JCTree.JCMethodInvocation) {
            JCTree.JCExpression awaitMeth = ((JCTree.JCMethodInvocation) init).meth;
            if (awaitMeth instanceof JCTree.JCFieldAccess && (awaitMeth.toString().equals("Z.await")) || awaitMeth.toString().equals("await")) {
                return true;
            }
        }
        return false;
    }

    private boolean isReturn(JCTree.JCStatement stat) {
        return stat instanceof JCTree.JCReturn;
    }

    private JCTree.JCStatement makeSetNextStat(int nextLabel) {
        return maker.Exec(maker.Assign(maker.Ident(names.fromString("next")), maker.Literal(TypeTag.INT, nextLabel)));
    }


    private int getLabel() {
        return labelCount;
    }

    private int getAndSetNextLabel() {
        return ++labelCount;
    }

    private int getNextLabel(int labelCount) {
        int next = labelCount + 1;
        return next;
    }

    private JCTree.JCExpression getInitAwaitMono(JCTree.JCStatement stat) {
        AtomicReference<JCTree.JCExpression> retReference = new AtomicReference<>();
        stat.accept(new TreeTranslator() {
            @Override
            public void visitApply(JCTree.JCMethodInvocation methodInvocation) {
                if (isAwait(methodInvocation)) {
                    retReference.set(getAwaitMethArg(methodInvocation));
                }
                super.visitApply(methodInvocation);
            }
        });
        return retReference.get();
    }

    private JCTree.JCExpression getAwaitMethArg(JCTree.JCExpression init) {
        if (init instanceof JCTree.JCMethodInvocation) {
            JCTree.JCExpression awaitMeth = ((JCTree.JCMethodInvocation) init).meth;
            if ((awaitMeth instanceof JCTree.JCFieldAccess && awaitMeth.toString().equals("Z.await"))
                    || (awaitMeth instanceof JCTree.JCIdent && ((JCTree.JCIdent) awaitMeth).name.contentEquals("await"))) {
                return ((JCTree.JCMethodInvocation) init).args.get(0);
            }
        }
        return null;
    }

    private JCTree.JCExpressionStatement makeAwaitAssign(JCTree.JCStatement stat) {
        if (stat instanceof JCTree.JCVariableDecl) {
            Name name = ((JCTree.JCVariableDecl) stat).getName();
            JCTree.JCExpression varType = ((JCTree.JCVariableDecl) stat).vartype;
            return maker.Exec(maker.Assign(maker.Ident(name), maker.TypeCast(varType, maker.Ident(names.fromString("sent")))));
        } else if (stat instanceof JCTree.JCExpressionStatement && ((JCTree.JCExpressionStatement) stat).expr instanceof JCTree.JCAssign) {
            JCTree.JCExpression lhs = ((JCTree.JCAssign) ((JCTree.JCExpressionStatement) stat).expr).lhs;
            if (lhs instanceof JCTree.JCIdent) {
                JCTree.JCExpression varType = fieldTypes.get(((JCTree.JCIdent) lhs).name);
                return maker.Exec(maker.Assign(lhs, maker.TypeCast(varType, maker.Ident(names.fromString("sent")))));
            }
        }
        return null;
    }

}
