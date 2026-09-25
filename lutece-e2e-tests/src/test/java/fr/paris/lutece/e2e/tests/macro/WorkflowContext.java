package fr.paris.lutece.e2e.tests.macro;

import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Contexte partage explicitement entre briques macro du plugin Workflow.
 *
 * <p>Porte le {@link Page}, le {@code baseUrl}, un suffixe unique par run, et les elements produits :
 * id du workflow, etats et actions (references par leur nom cote UF).</p>
 */
import fr.paris.lutece.e2e.tests.macro.verify.DeepVerificationMode;

public class WorkflowContext {

    public final Page page;
    public final String baseUrl;
    public final String runSuffix;

    /**
     * Instantane du mode « verification approfondie » au moment de la creation du contexte.
     *
     * <p>Lu une seule fois ici plutot qu'au fond de chaque brique : l'etat circule ainsi par le
     * contexte, conformement au principe pose plus haut, et le point de controle se reduit a une
     * lecture de champ d'instance final. Positionne par {@code DeepVerificationExtension} avant
     * chaque test, donc toujours a jour a la construction.</p>
     */
    public final boolean deepVerify;

    /** Instantane du controle SQL complementaire (sans effet si la base est injoignable). */
    public final boolean deepVerifySql;

    public int workflowId = -1;
    public String workflowName;

    public final List<StateRef> states = new ArrayList<>();
    public final List<ActionRef> actions = new ArrayList<>();

    public WorkflowContext(Page page, String baseUrl, String runSuffix) {
        this.page = page;
        this.baseUrl = baseUrl;
        this.runSuffix = runSuffix;
        this.deepVerify = DeepVerificationMode.isEnabled();
        this.deepVerifySql = DeepVerificationMode.isSqlEnabled();
    }

    /** Etat d'un workflow (reference par son nom dans les formulaires d'action). */
    public static class StateRef {
        public String name;
        public boolean initial;

        public StateRef() {}

        public StateRef(String name, boolean initial) {
            this.name = name;
            this.initial = initial;
        }
    }

    /** Action d'un workflow. */
    public static class ActionRef {
        public String name;

        public ActionRef() {}

        public ActionRef(String name) {
            this.name = name;
        }
    }

    public StateRef state(int index) {
        return states.get(index);
    }
}
