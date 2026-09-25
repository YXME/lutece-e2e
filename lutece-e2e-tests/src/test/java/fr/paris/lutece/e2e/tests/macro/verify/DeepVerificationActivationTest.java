package fr.paris.lutece.e2e.tests.macro.verify;

import fr.paris.lutece.e2e.tests.bo.config.DeepVerificationExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Garde-fou du mecanisme d'activation de la verification approfondie.
 *
 * <p>Ce test ne touche ni navigateur ni conteneur : il s'execute en quelques centaines de
 * millisecondes et tourne donc a chaque {@code mvn test}. Son role est d'empecher une regression
 * silencieuse du mecanisme lui-meme — si l'activation cessait de fonctionner, toutes les
 * verifications approfondies deviendraient des coquilles vides sans que rien ne vire au rouge.</p>
 *
 * <p>L'ordre des methodes est impose : la derniere verifie qu'aucun etat ne fuit d'un test active
 * vers le test suivant, ce qui n'a de sens qu'apres le test annote.</p>
 *
 * <p>Les deux tests qui portent sur l'etat « au repos » recoivent la configuration ambiante en
 * parametre ({@code DeepVerificationExtension.Ambient}) plutot que de postuler qu'elle est a l'arret :
 * la suite doit rester juste quand on force {@code -Dlutece.e2e.deep.verify=true} sur tout un run,
 * cas ou le defaut attendu n'est plus « inactif » mais « ce que demande la configuration ».</p>
 */
@Tag("macro")
@ExtendWith(DeepVerificationExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Mecanisme d'activation de la verification approfondie")
public class DeepVerificationActivationTest {

    @Test
    @Order(1)
    @DisplayName("Desactive par defaut : les runs courants ne paient rien")
    void desactiveParDefaut(DeepVerificationExtension.Ambient ambient) {
        assumeFalse(ambient.ui(),
            "Verification forcee par la configuration du run (-D" + DeepVerificationMode.KEY
            + " ou @ConfigurationParameter) : le comportement par defaut n'est pas observable ici");
        assertFalse(DeepVerificationMode.isEnabled(),
            "Sans declaration, la verification approfondie doit rester inactive");
        assertFalse(DeepVerificationMode.isSqlEnabled(),
            "Le controle SQL ne doit pas etre actif sans la verification");
    }

    @Test
    @Order(2)
    @DeepVerification
    @DisplayName("Active par @DeepVerification, SQL compris")
    void activeParAnnotation() {
        assertTrue(DeepVerificationMode.isEnabled(),
            "@DeepVerification doit activer la verification pour ce test");
        assertTrue(DeepVerificationMode.isSqlEnabled(),
            "Le controle SQL suit la verification par defaut");
    }

    @Test
    @Order(3)
    @DeepVerification(sql = false)
    @DisplayName("Le controle SQL se coupe independamment de la verification")
    void sqlCoupableSeparement() {
        assertTrue(DeepVerificationMode.isEnabled(),
            "La verification de l'interface reste active");
        assertFalse(DeepVerificationMode.isSqlEnabled(),
            "Le controle SQL doit pouvoir etre coupe sans perdre la verification de l'interface");
    }

    @Test
    @Order(4)
    @DeepVerification(false)
    @DisplayName("Neutralisable localement, meme sous une suite qui active")
    void neutralisationLocale() {
        assertFalse(DeepVerificationMode.isEnabled(),
            "@DeepVerification(false) doit primer sur le parametre de configuration");
    }

    @Test
    @Order(5)
    @DisplayName("Aucune fuite vers le test suivant")
    void pasDeFuiteVersLeTestSuivant(DeepVerificationExtension.Ambient ambient) {
        assertEquals(ambient.ui(), DeepVerificationMode.isEnabled(),
            "Apres les tests annotes ci-dessus, l'etat doit etre revenu a ce que demande la"
            + " configuration ambiante : surefire ne forke pas par classe, un etat non restaure"
            + " contaminerait toute la suite");
        assertEquals(ambient.sql(), DeepVerificationMode.isSqlEnabled(),
            "Le controle SQL doit lui aussi etre revenu a la configuration ambiante");
    }
}
