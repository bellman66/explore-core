package compile;

import com.sun.source.util.TaskEvent;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.CompileStates;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.resources.CompilerProperties;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import java.util.Collection;
import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;

import static com.sun.tools.javac.code.Kinds.Kind.ABSENT_TYP;
import static com.sun.tools.javac.code.Kinds.Kind.PCK;
import static com.sun.tools.javac.main.Option.PROC;

/**
 * Jdk 소스내 어노테이션 프로세싱 소스코드
 */
public class AnnotationProcessor {

    /**
     * [Prev] 1. 어노테이션 진행전 어노테이션 설정 주입
     *   - 파라미터를 통해 프로세서 입력 받음
     *   - Java Env 구현체에 Processor를 Setting함
     *
     * call - initProcessAnnotations(processors, sourceFileObjects, classnames);
     *
     * @param processors
     * @param initialFiles
     * @param initialClassNames
     */
    public void initProcessAnnotations(Iterable<? extends Processor> processors,
                                       Collection<? extends JavaFileObject> initialFiles,
                                       Collection<String> initialClassNames) {
        if (processors != null && processors.iterator().hasNext())
            explicitAnnotationProcessingRequested = true;

        // Process annotations if processing is not disabled and there
        // is at least one Processor available.
        if (options.isSet(PROC, "none")) {
            processAnnotations = false;
        } else if (procEnvImpl == null) {
            procEnvImpl = JavacProcessingEnvironment.instance(context);
            procEnvImpl.setProcessors(processors);
            processAnnotations = procEnvImpl.atLeastOneProcessor();

            if (processAnnotations) {
                if (!explicitAnnotationProcessingRequested() &&
                        !optionsCheckingInitiallyDisabled) {
                    log.note(CompilerProperties.Notes.ImplicitAnnotationProcessing);
                }

                options.put("parameters", "parameters");
                reader.saveParameterNames = true;
                keepComments = true;
                genEndPos = true;
                if (!taskListener.isEmpty())
                    taskListener.started(new TaskEvent(TaskEvent.Kind.ANNOTATION_PROCESSING));
                deferredDiagnosticHandler = new Log.DeferredDiagnosticHandler(log);
                procEnvImpl.getFiler().setInitialState(initialFiles, initialClassNames);
            } else { // free resources
                procEnvImpl.close();
            }
        }
    }

    /**
     * [Core] 2. 어노테이션 실행
     *   - 새로운 어노테이션 처리기 확인
     *   - 존재한다면 JavaProcessingEnvironment.doProcessing() 호출
     *
     * call native code sample
     *
     *   // These method calls must be chained to avoid memory leaks
     *   processAnnotations(
     *       enterTrees(
     *               stopIfError(CompileState.ENTER,
     *                       initModules(stopIfError(CompileState.ENTER, parseFiles(sourceFileObjects))))
     *       ),
     *       classnames
     *   );
     *
     * @param roots
     * @param classnames
     */
    public void processAnnotations(List<JCTree.JCCompilationUnit> roots,
                                   Collection<String> classnames) {
        if (shouldStop(CompileStates.CompileState.PROCESS)) {
            // Errors were encountered.
            // Unless all the errors are resolve errors, the errors were parse errors
            // or other errors during enter which cannot be fixed by running
            // any annotation processors.
            if (processAnnotations) {
                deferredDiagnosticHandler.reportDeferredDiagnostics();
                log.popDiagnosticHandler(deferredDiagnosticHandler);
                return ;
            }
        }

        // ASSERT: processAnnotations and procEnvImpl should have been set up by
        // by initProcessAnnotations

        // NOTE: The !classnames.isEmpty() checks should be refactored to Main.

        if (!processAnnotations) {
            // If there are no annotation processors present, and
            // annotation processing is to occur with compilation,
            // emit a warning.
            if (options.isSet(PROC, "only")) {
                log.warning(CompilerProperties.Warnings.ProcProcOnlyRequestedNoProcs);
                todo.clear();
            }
            // If not processing annotations, classnames must be empty
            if (!classnames.isEmpty()) {
                log.error(CompilerProperties.Errors.ProcNoExplicitAnnotationProcessingRequested(classnames));
            }
            Assert.checkNull(deferredDiagnosticHandler);
            return ; // continue regular compilation
        }

        Assert.checkNonNull(deferredDiagnosticHandler);

        try {
            List<Symbol.ClassSymbol> classSymbols = List.nil();
            List<Symbol.PackageSymbol> pckSymbols = List.nil();
            if (!classnames.isEmpty()) {
                // Check for explicit request for annotation
                // processing
                if (!explicitAnnotationProcessingRequested()) {
                    log.error(CompilerProperties.Errors.ProcNoExplicitAnnotationProcessingRequested(classnames));
                    deferredDiagnosticHandler.reportDeferredDiagnostics();
                    log.popDiagnosticHandler(deferredDiagnosticHandler);
                    return ; // TODO: Will this halt compilation?
                } else {
                    boolean errors = false;
                    for (String nameStr : classnames) {
                        Symbol sym = resolveBinaryNameOrIdent(nameStr);
                        if (sym == null ||
                                (sym.kind == PCK && !processPcks) ||
                                sym.kind == ABSENT_TYP) {
                            if (sym != silentFail)
                                log.error(CompilerProperties.Errors.ProcCantFindClass(nameStr));
                            errors = true;
                            continue;
                        }
                        try {
                            if (sym.kind == PCK)
                                sym.complete();
                            if (sym.exists()) {
                                if (sym.kind == PCK)
                                    pckSymbols = pckSymbols.prepend((Symbol.PackageSymbol)sym);
                                else
                                    classSymbols = classSymbols.prepend((Symbol.ClassSymbol)sym);
                                continue;
                            }
                            Assert.check(sym.kind == PCK);
                            log.warning(CompilerProperties.Warnings.ProcPackageDoesNotExist(nameStr));
                            pckSymbols = pckSymbols.prepend((Symbol.PackageSymbol)sym);
                        } catch (Symbol.CompletionFailure e) {
                            log.error(CompilerProperties.Errors.ProcCantFindClass(nameStr));
                            errors = true;
                            continue;
                        }
                    }
                    if (errors) {
                        deferredDiagnosticHandler.reportDeferredDiagnostics();
                        log.popDiagnosticHandler(deferredDiagnosticHandler);
                        return ;
                    }
                }
            }
            try {
                annotationProcessingOccurred =
                        procEnvImpl.doProcessing(roots,
                                classSymbols,
                                pckSymbols,
                                deferredDiagnosticHandler);
                // doProcessing will have handled deferred diagnostics
            } finally {
                procEnvImpl.close();
            }
        } catch (Symbol.CompletionFailure ex) {
            log.error(CompilerProperties.Errors.CantAccess(ex.sym, ex.getDetailValue()));
            if (deferredDiagnosticHandler != null) {
                deferredDiagnosticHandler.reportDeferredDiagnostics();
                log.popDiagnosticHandler(deferredDiagnosticHandler);
            }
        }
    }

}
