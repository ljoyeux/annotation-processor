package fr.devlogic;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.transaction.Transactional;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TransactionBeanProcessor extends AbstractProcessor {

    public static final Set<String> HANDLED_ANNOTATIONS = new HashSet<>();

    static {
        HANDLED_ANNOTATIONS.add(Component.class.getName());
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return HANDLED_ANNOTATIONS;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_8;
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        Trees trees = Trees.instance(processingEnv);

        annotations.stream().forEach(a -> {
            final Set<? extends Element> elementsAnnotatedWith = roundEnv.getElementsAnnotatedWith(a);
            final List<? extends Element> classElements = elementsAnnotatedWith.stream().filter(e -> ElementKind.CLASS.equals(((Element) e).getKind())).collect(Collectors.toList());
            classElements.forEach(ce -> {
                if (ce.getAnnotation(Transactional.class) != null) {
                    return;
                }

                final List<? extends Element> enclosedElements = ce.getEnclosedElements();
                final List<Symbol.VarSymbol> symbols = enclosedElements.stream().filter(ee -> ee instanceof Symbol.VarSymbol).map(ee -> (Symbol.VarSymbol) ee).collect(Collectors.toList());

                final List<Symbol.VarSymbol> crudImpl = symbols.stream().filter(s -> ((Type.ClassType) s.type).all_interfaces_field.stream().map(type -> type.toString()).filter(str -> CrudRepository.class.getName().equals(str)).count() > 0).collect(Collectors.toList());

                crudImpl.forEach(c -> {
                    final TreePath p = trees.getPath(ce);
                    final JavaFileObject file = p.getCompilationUnit().getSourceFile();
                    final int[] lineColumn = getLine(file, c.pos);
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, file.getName() + ":[" + lineColumn[0] + "," + lineColumn[1] + "] " + ce.getSimpleName() + " bean is mandatory transactional since " + c.getSimpleName() + " is a CrudRepository");
                });
            });
        });

        return false;
    }

    /**
     * Get the line number for the primary position for a tree.
     * The code is intended to be simple, although not necessarily efficient.
     * However, note that a file manager such as JavacFileManager is likely
     * to cache the results of file.getCharContent, avoiding the need to read
     * the bits from disk each time this method is called.
     */
    private int[] getLine(final JavaFileObject file, final int pos) {
        try {
            CharSequence cs = file.getCharContent(true);
            int line = 1;
            int column = 1;
            for (int i = 0; i < pos; i++) {
                if (cs.charAt(i) == '\n') { // jtreg tests always use Unix line endings
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }
            return new int[]{line, column};
        } catch (IOException e) {
            return null;
        }
    }

}
