package compile;

import com.sun.source.util.TaskEvent;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.resources.CompilerProperties;
import com.sun.tools.javac.util.Log;
import java.util.Collection;
import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;

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
}
