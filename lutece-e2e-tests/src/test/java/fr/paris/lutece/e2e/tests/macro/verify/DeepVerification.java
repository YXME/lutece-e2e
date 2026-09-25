package fr.paris.lutece.e2e.tests.macro.verify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Active la verification approfondie sur une classe de test ou une methode.
 *
 * <p>Destinee aux <b>suites metier</b> ({@code XxxSuite extends MacroTest}) : ce sont de vraies
 * classes Jupiter, sur lesquelles {@code @ConfigurationParameter} n'a aucun effet (cette derniere
 * n'est lue que par le moteur de suite). Pour les classes {@code @Suite} sans code, utiliser a la
 * place {@code @ConfigurationParameter(key = DeepVerificationMode.KEY, value = "true")}.</p>
 *
 * <p>L'annotation la plus proche l'emporte (methode, puis classe, puis classes englobantes), et prime
 * sur le parametre de configuration : c'est ce qui permet de neutraliser localement un heritage avec
 * {@code @DeepVerification(false)}.</p>
 *
 * <p>A l'inverse, une suite ne doit <b>jamais</b> declarer
 * {@code @ConfigurationParameter(key = ..., value = "false")} : les parametres explicites primant sur
 * les proprietes systeme, {@code -Dlutece.e2e.deep.verify=true} ne pourrait plus l'activer.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@Inherited
public @interface DeepVerification {

    /** {@code false} pour neutraliser explicitement un heritage ou un parametre de suite. */
    boolean value() default true;

    /** Controle SQL complementaire, quand la base est joignable. Suit {@link #value()} par defaut. */
    boolean sql() default true;
}
