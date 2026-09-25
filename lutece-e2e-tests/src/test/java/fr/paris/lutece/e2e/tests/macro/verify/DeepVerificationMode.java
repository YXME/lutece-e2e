package fr.paris.lutece.e2e.tests.macro.verify;

/**
 * Detenteur du mode « verification approfondie » pour le test en cours.
 *
 * <p>Ecrit <b>uniquement</b> par {@code DeepVerificationExtension}, avant chaque classe et avant
 * chaque test, puis restaure a la valeur precedente. Lu une seule fois par les constructeurs des
 * contextes macro, qui en prennent un instantane : au point de controle, la garde est donc une
 * lecture de champ d'instance, pas un acces a un etat global.</p>
 *
 * <p><b>Volontairement sans initialiseur statique lisant la configuration.</b> Un
 * {@code static final} alimente depuis la config serait fige au chargement de la classe — c'est le
 * comportement de {@code BaseTest.HEADLESS} / {@code TIMEOUT} — alors que la valeur depend de la
 * suite en cours et change au sein d'une meme JVM. Les champs demarrent donc a {@code false} en dur,
 * et seule l'extension les positionne.</p>
 *
 * <p>Les champs sont {@code volatile} pour garantir la visibilite si le thread de l'extension differe
 * du thread de test. L'execution etant sequentielle aujourd'hui (aucun
 * {@code junit.jupiter.execution.parallel.enabled} n'est positionne dans le projet), un statique
 * suffit ; si le parallelisme etait active un jour, il faudrait passer en {@link ThreadLocal} — ce que
 * l'encapsulation par {@code isEnabled()} / {@code set(...)} rend local a ce fichier.</p>
 */
public final class DeepVerificationMode {

    /**
     * Cle d'activation de la verification par re-lecture de l'interface.
     *
     * <p>Utilisable en {@code @ConfigurationParameter} sur une classe {@code @Suite}, ou en
     * {@code -Dlutece.e2e.deep.verify=true} : {@code ExtensionContext.getConfigurationParameter}
     * resout les parametres explicites, puis les proprietes systeme.</p>
     */
    public static final String KEY = "lutece.e2e.deep.verify";

    /**
     * Cle d'activation du controle SQL complementaire. Suit {@link #KEY} par defaut.
     *
     * <p>Flag distinct pour pouvoir couper le SQL en ligne de commande, le jour ou une requete casse
     * sur une image, <b>sans</b> perdre la verification par l'interface ni recompiler.</p>
     */
    public static final String SQL_KEY = "lutece.e2e.deep.verify.sql";

    private static volatile boolean enabled = false;
    private static volatile boolean sqlEnabled = false;

    private DeepVerificationMode() {
    }

    /** Vrai si la verification par re-lecture de l'interface est active pour le test courant. */
    public static boolean isEnabled() {
        return enabled;
    }

    /** Vrai si le controle SQL complementaire est demande (il reste sans effet si la base est injoignable). */
    public static boolean isSqlEnabled() {
        return sqlEnabled;
    }

    /**
     * Reserve a l'extension : positionne les deux flags et retourne l'etat precedent, que l'appelant
     * doit restaurer (discipline de pile, pour ne rien laisser fuir d'une classe a l'autre).
     */
    public static boolean[] set(boolean uiEnabled, boolean sql) {
        boolean[] previous = { enabled, sqlEnabled };
        enabled = uiEnabled;
        sqlEnabled = sql;
        return previous;
    }
}
