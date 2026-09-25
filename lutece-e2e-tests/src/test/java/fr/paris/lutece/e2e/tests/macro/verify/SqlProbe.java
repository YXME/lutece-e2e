package fr.paris.lutece.e2e.tests.macro.verify;

import fr.paris.lutece.e2e.tests.bo.testsuites.ContainerSetup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.testcontainers.containers.MariaDBContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Consumer;

/**
 * Acces JDBC optionnel pour les verifications approfondies.
 *
 * <p>Sources de connexion, dans l'ordre :</p>
 * <ol>
 *   <li>proprietes explicites {@code lutece.db.url} / {@code lutece.db.user} / {@code lutece.db.password},
 *       qui permettent le controle SQL meme en mode externe ({@code -Dlutece.base.url}), ou aucune
 *       configuration base n'existe par ailleurs ;</li>
 *   <li>le conteneur MariaDB de {@code ContainerSetup}, s'il tourne ;</li>
 *   <li>aucune : <b>repli silencieux</b>, la verification se limite alors a la re-lecture de l'interface.</li>
 * </ol>
 *
 * <p><b>Une connexion neuve par verification.</b> MariaDB est en {@code REPEATABLE READ} : une
 * connexion mise en cache ne verrait jamais les ecritures committees ensuite par l'application, et
 * les controles seraient justes au premier appel puis silencieusement perimes. Le cout (~20 ms)
 * n'est paye que lorsque le mode est actif.</p>
 */
public final class SqlProbe {

    private static final Logger LOGGER = LogManager.getLogger(SqlProbe.class);

    /** Nombre de tentatives : une ecriture applicative peut etre committee avec un leger decalage. */
    private static final int ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 200L;

    private static volatile Spec spec;
    private static volatile boolean resolved;

    private final Connection connection;

    private SqlProbe(Connection connection) {
        this.connection = connection;
    }

    /**
     * Execute le controle si une base est joignable ; ne fait rien sinon.
     *
     * <p>Le repli est volontairement silencieux : c'est ce qui permet a une meme suite de tourner en
     * mode conteneur (avec SQL) et en mode externe (sans), sans double maintenance.</p>
     */
    public static void ifAvailable(Consumer<SqlProbe> check) {
        Spec target = spec();
        if (target == null) {
            return;
        }
        try (Connection cx = DriverManager.getConnection(target.url, target.user, target.password)) {
            check.accept(new SqlProbe(cx));
        } catch (SQLException unreachable) {
            LOGGER.info("Verification SQL sautee (base injoignable) : {}", unreachable.getMessage());
        }
    }

    /** Vrai si une base est joignable : utile pour documenter un message d'assertion. */
    public static boolean available() {
        return spec() != null;
    }

    /** Exige exactement {@code expected} lignes. */
    public void expectCount(int expected, String message, String sql, Object... params) {
        Assertions.assertEquals(expected, countWithRetry(expected, sql, params),
            message + " [verification SQL] " + sql);
    }

    /** Exige au moins une ligne. */
    public void expectExists(String message, String sql, Object... params) {
        Assertions.assertTrue(countWithRetry(1, sql, params) >= 1,
            message + " [verification SQL] " + sql);
    }

    /** Exige aucune ligne (suppression). */
    public void expectNone(String message, String sql, Object... params) {
        Assertions.assertEquals(0, count(sql, params), message + " [verification SQL] " + sql);
    }

    /**
     * Compte avec quelques tentatives tant que le resultat n'atteint pas la cible : certaines
     * ecritures Lutece passent par des taches ou des caches, et un controle immediat produirait des
     * faux negatifs intermittents.
     */
    private int countWithRetry(int target, String sql, Object... params) {
        int found = 0;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            found = count(sql, params);
            if (found >= target) {
                return found;
            }
            sleep();
        }
        return found;
    }

    private int count(String sql, Object... params) {
        try (PreparedStatement st = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                st.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Requete de verification invalide : " + sql, e);
        }
    }

    private void sleep() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Resolution memorisee : on ne retente pas la decouverte a chaque controle. */
    private static Spec spec() {
        if (!resolved) {
            synchronized (SqlProbe.class) {
                if (!resolved) {
                    spec = resolveSpec();
                    resolved = true;
                    LOGGER.info("Verification SQL : {}", spec == null
                        ? "base injoignable, repli sur la verification de l'interface"
                        : "active sur " + spec.url);
                }
            }
        }
        return spec;
    }

    private static Spec resolveSpec() {
        String url = System.getProperty("lutece.db.url");
        if (url != null && !url.isBlank()) {
            return new Spec(url,
                System.getProperty("lutece.db.user", "lutece"),
                System.getProperty("lutece.db.password", "lutece"));
        }
        try {
            if (ContainerSetup.isContainersStarted()) {
                MariaDBContainer<?> db = ContainerSetup.getMariaDBContainer();
                if (db != null && db.isRunning()) {
                    return new Spec(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                }
            }
        } catch (RuntimeException noContainer) {
            LOGGER.info("Conteneur MariaDB indisponible : {}", noContainer.getMessage());
        }
        return null;
    }

    /** Remet a zero la resolution : reserve aux tests du mecanisme. */
    static void resetResolution() {
        synchronized (SqlProbe.class) {
            spec = null;
            resolved = false;
        }
    }

    private static final class Spec {
        private final String url;
        private final String user;
        private final String password;

        private Spec(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }
    }
}
