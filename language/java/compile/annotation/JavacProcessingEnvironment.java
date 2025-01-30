package compile.annotation;

import com.sun.source.util.TaskEvent;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.tools.JavaFileObject;

public class JavacProcessingEnvironment {

    /**
     * [Core] 1. 어노테이션 Main 프로세스 로직
     *   - Round 개념이 들어감 ( JavaCompiler )
     *
     *  !! Round 란
     *  컴파일러가 어노테이션을 읽고 구문트리를 수정해야 될 경우
     *  컴파일러는 구문 분석 & 어노테이션 처리 ( 전 단계로 )로 돌아가야함.
     *  이는 구문트리가 수정되지 않을때까지 지속적으로 반복하도록 설계.
     *  이렇게 반복되는 것을 라운드라고 부른다.
     *
     * @param roots
     * @param classSymbols
     * @param pckSymbols
     * @param deferredDiagnosticHandler
     * @return boolean
     */
    public boolean doProcessing(List<JCTree.JCCompilationUnit> roots,
                                List<Symbol.ClassSymbol> classSymbols,
                                Iterable<? extends Symbol.PackageSymbol> pckSymbols,
                                Log.DeferredDiagnosticHandler deferredDiagnosticHandler) {
        final Set<JCTree.JCCompilationUnit> treesToClean =
                Collections.newSetFromMap(new IdentityHashMap<JCTree.JCCompilationUnit, Boolean>());

        //fill already attributed implicit trees:
        for (Env<AttrContext> env : enter.getEnvs()) {
            treesToClean.add(env.toplevel);
        }

        Set<Symbol.PackageSymbol> specifiedPackages = new LinkedHashSet<>();
        for (Symbol.PackageSymbol psym : pckSymbols)
            specifiedPackages.add(psym);
        this.specifiedPackages = Collections.unmodifiableSet(specifiedPackages);

        com.sun.tools.javac.processing.JavacProcessingEnvironment.Round
                round = new com.sun.tools.javac.processing.JavacProcessingEnvironment.Round(roots, classSymbols, treesToClean, deferredDiagnosticHandler);

        boolean errorStatus;
        boolean moreToDo;
        do {
            // Run processors for round n
            round.run(false, false);

            // Processors for round n have run to completion.
            // Check for errors and whether there is more work to do.
            errorStatus = round.unrecoverableError();
            moreToDo = moreToDo();

            round.showDiagnostics(showResolveErrors);

            // Set up next round.
            // Copy mutable collections returned from filer.
            round = round.next(
                    new LinkedHashSet<>(filer.getGeneratedSourceFileObjects()),
                    new LinkedHashMap<>(filer.getGeneratedClasses()));

            // Check for errors during setup.
            if (round.unrecoverableError())
                errorStatus = true;

        } while (moreToDo && !errorStatus);

        // run last round
        round.run(true, errorStatus);
        round.showDiagnostics(true);

        filer.warnIfUnclosedFiles();
        warnIfUnmatchedOptions();

        /*
         * If an annotation processor raises an error in a round,
         * that round runs to completion and one last round occurs.
         * The last round may also occur because no more source or
         * class files have been generated.  Therefore, if an error
         * was raised on either of the last *two* rounds, the compile
         * should exit with a nonzero exit code.  The current value of
         * errorStatus holds whether or not an error was raised on the
         * second to last round; errorRaised() gives the error status
         * of the last round.
         */
        if (messager.errorRaised()
                || werror && round.warningCount() > 0 && round.errorCount() > 0)
            errorStatus = true;

        Set<JavaFileObject> newSourceFiles =
                new LinkedHashSet<>(filer.getGeneratedSourceFileObjects());
        roots = round.roots;

        errorStatus = errorStatus || (compiler.errorCount() > 0);


        if (newSourceFiles.size() > 0)
            roots = roots.appendList(compiler.parseFiles(newSourceFiles));

        errorStatus = errorStatus || (compiler.errorCount() > 0);

        if (errorStatus && compiler.errorCount() == 0) {
            compiler.log.nerrors++;
        }

        if (compiler.continueAfterProcessAnnotations()) {
            round.finalCompiler();
            compiler.enterTrees(compiler.initModules(roots));
        } else {
            compiler.todo.clear();
        }

        // Free resources
        this.close();

        if (!taskListener.isEmpty())
            taskListener.finished(new TaskEvent(TaskEvent.Kind.ANNOTATION_PROCESSING));

        return true;
    }
}
