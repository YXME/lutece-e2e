package fr.paris.lutece.e2e.tests.bo.config;

import fr.paris.lutece.e2e.tests.macro.verify.DeepVerification;
import fr.paris.lutece.e2e.tests.macro.verify.DeepVerificationMode;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.AnnotationSupport;

import java.util.Optional;

/**
 * Pont entre la declaration d'activation de la verification approfondie et le detenteur statique lu
 * par les contextes macro.
 *
 * <p>Une methode {@code static run(ctx, data)} de brique n'a aucun {@code ExtensionContext} sous la
 * main, et JUnit 5 n'offre pas d'acces statique au contexte courant : un pont est donc necessaire
 * entre la declaration (annotation ou parametre de configuration de suite) et le code des briques.</p>
 *
 * <p>La valeur est <b>recalculee</b> avant chaque classe et avant chaque test, puis <b>restauree</b>
 * depuis le {@code Store} apres. C'est essentiel : surefire ne forke pas par classe
 * ({@code forkCount=1}, {@code reuseForks=true}), donc un simple « positionner une fois » fuirait
 * definitivement d'une suite a l'autre dans la meme JVM — c'est le defaut du canal
 * {@code System.setProperty("test.run.suffix", ...)} deja present dans le projet.</p>
 */
public class DeepVerificationExtension
        implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback,
                   ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(DeepVerificationExtension.class);
    private static final String PREVIOUS = "previous";

    /**
     * Ce que la <b>configuration ambiante</b> demande, annotations mises de cote : ce qu'obtiendrait
     * un test non annote. C'est la reference dont un test du mecanisme a besoin pour rester juste
     * aussi bien dans un run ordinaire que sous {@code -Dlutece.e2e.deep.verify=true}.
     */
    public record Ambient(boolean ui, boolean sql) { }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext context) {
        return parameterContext.getParameter().getType() == Ambient.class;
    }

    @Override
    public Ambient resolveParameter(ParameterContext parameterContext, ExtensionContext context) {
        boolean ui = parameter(context, DeepVerificationMode.KEY, false);
        return new Ambient(ui, ui && parameter(context, DeepVerificationMode.SQL_KEY, true));
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        push(context);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        pop(context);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        push(context);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        pop(context);
    }

    private void push(ExtensionContext context) {
        Optional<DeepVerification> annotation = findAnnotation(context);
        boolean ui;
        boolean sql;
        if (annotation.isPresent()) {
            ui = annotation.get().value();
            sql = ui && annotation.get().sql();
        } else {
            ui = parameter(context, DeepVerificationMode.KEY, false);
            // Le SQL suit l'UI par defaut, mais reste coupable independamment en ligne de commande.
            sql = ui && parameter(context, DeepVerificationMode.SQL_KEY, true);
        }
        context.getStore(NAMESPACE).put(PREVIOUS, DeepVerificationMode.set(ui, sql));
    }

    private void pop(ExtensionContext context) {
        boolean[] previous = context.getStore(NAMESPACE).remove(PREVIOUS, boolean[].class);
        if (previous != null) {
            DeepVerificationMode.set(previous[0], previous[1]);
        }
    }

    /** Annotation la plus proche : methode, puis classe, puis classes englobantes. */
    private Optional<DeepVerification> findAnnotation(ExtensionContext context) {
        for (ExtensionContext current = context; current != null;
                current = current.getParent().orElse(null)) {
            Optional<DeepVerification> found =
                AnnotationSupport.findAnnotation(current.getElement(), DeepVerification.class);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /**
     * Lit un parametre de configuration JUnit. La resolution couvre, dans l'ordre, les parametres
     * explicites (donc les {@code @ConfigurationParameter} des suites, heritees par les suites
     * imbriquees) puis les proprietes systeme — d'ou le fonctionnement de {@code -Dcle=true} sans
     * une ligne de code supplementaire.
     */
    private boolean parameter(ExtensionContext context, String key, boolean defaultValue) {
        return context.getConfigurationParameter(key, Boolean::parseBoolean).orElse(defaultValue);
    }
}
