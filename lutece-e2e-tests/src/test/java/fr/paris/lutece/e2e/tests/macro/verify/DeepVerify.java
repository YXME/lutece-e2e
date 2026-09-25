package fr.paris.lutece.e2e.tests.macro.verify;

import fr.paris.lutece.e2e.tests.macro.FormsContext;
import fr.paris.lutece.e2e.tests.macro.MacroSupport;
import fr.paris.lutece.e2e.tests.macro.data.FormOptionsDataSet;
import fr.paris.lutece.e2e.tests.macro.data.QuestionDataSet;
import fr.paris.lutece.e2e.tests.macro.forms.AddValidationControlMacroTest;
import fr.paris.lutece.e2e.tests.macro.UnittreeContext;
import fr.paris.lutece.e2e.tests.macro.UnittreeSupport;
import fr.paris.lutece.e2e.tests.macro.WorkflowContext;
import fr.paris.lutece.e2e.tests.macro.WorkflowSupport;
import io.qameta.allure.Step;

import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Assertions;

/**
 * Points de controle « verification approfondie », appeles en fin de brique macro.
 *
 * <p>Chaque methode publique commence par la garde {@code if (!ctx.deepVerify) return;} : quand le
 * mode est inactif — le defaut — le cout se reduit a une lecture de champ d'instance {@code final}.
 * Les runs courants ne sont pas ralentis.</p>
 *
 * <p>Quand le mode est actif, chaque controle fait deux choses :</p>
 * <ol>
 *   <li>une <b>re-lecture de l'interface</b>, en reutilisant les extracteurs existants
 *       ({@code MacroSupport.extractFormId}, {@code WorkflowSupport.extractWorkflowId},
 *       {@code UnittreeSupport.extractUnitId}...) ;</li>
 *   <li>un <b>controle SQL</b> par cle primaire quand la base est joignable, sinon rien (repli
 *       silencieux, cf. {@link SqlProbe}).</li>
 * </ol>
 *
 * <p><b>Regle d'appel imperative</b> : ne jamais passer en argument le resultat d'un helper de
 * navigation. {@code DeepVerify.form(ctx, MacroSupport.extractFormId(ctx, titre))} naviguerait
 * <i>meme mode desactive</i>, les arguments etant evalues avant l'appel. Les signatures n'acceptent
 * donc que des scalaires et des references deja presentes dans le contexte.</p>
 *
 * <p><b>Pourquoi {@code onSamePage}</b> : les extracteurs naviguent sur la {@code page} partagee.
 * Sans restauration, inserer un controle en fin de brique deplacerait le navigateur et casserait la
 * brique suivante — uniquement en mode actif, donc dans le mode le moins souvent joue.</p>
 */
public final class DeepVerify {

    private DeepVerify() {
    }

    // ------------------------------------------------------------------ Forms

    /** Le formulaire existe, porte le titre attendu et l'id memorise dans le contexte. */
    public static void form(FormsContext ctx, String expectedTitle) {
        if (!ctx.deepVerify) {
            return;
        }
        onSamePage(ctx.page.url(), ctx, () -> checkForm(ctx, expectedTitle));
    }

    @Step("Verification approfondie : formulaire « {expectedTitle} »")
    private static void checkForm(FormsContext ctx, String expectedTitle) {
        int uiId = MacroSupport.extractFormId(ctx, expectedTitle);
        Assertions.assertEquals(ctx.formId, uiId,
            "Re-lecture BO : le formulaire '" + expectedTitle + "' devrait porter l'id " + ctx.formId);
        if (ctx.deepVerifySql) {
            SqlProbe.ifAvailable(sql -> sql.expectCount(1,
                "Le formulaire '" + expectedTitle + "' devrait exister une fois en base",
                "SELECT COUNT(*) FROM forms_form WHERE id_form = ? AND title = ?",
                ctx.formId, expectedTitle));
        }
    }

    /** L'etape existe, rattachee au formulaire courant. */
    public static void step(FormsContext ctx, String expectedTitle) {
        if (!ctx.deepVerify) {
            return;
        }
        onSamePage(ctx.page.url(), ctx, () -> checkStep(ctx, expectedTitle));
    }

    @Step("Verification approfondie : etape « {expectedTitle} »")
    private static void checkStep(FormsContext ctx, String expectedTitle) {
        Assertions.assertTrue(MacroSupport.extractStepId(ctx, ctx.formId, expectedTitle) > 0,
            "Re-lecture BO : l'etape '" + expectedTitle + "' devrait exister sur le formulaire " + ctx.formId);
        if (ctx.deepVerifySql) {
            SqlProbe.ifAvailable(sql -> sql.expectExists(
                "L'etape '" + expectedTitle + "' devrait exister en base",
                "SELECT COUNT(*) FROM forms_step WHERE id_form = ? AND title = ?",
                ctx.formId, expectedTitle));
        }
    }

    // --------------------------------------------------------------- Workflow

    /** Le workflow existe et porte l'id memorise. */
    public static void workflow(WorkflowContext ctx, String expectedName) {
        if (!ctx.deepVerify) {
            return;
        }
        onSamePage(ctx.page.url(), ctx, () -> checkWorkflow(ctx, expectedName));
    }

    @Step("Verification approfondie : workflow « {expectedName} »")
    private static void checkWorkflow(WorkflowContext ctx, String expectedName) {
        Assertions.assertEquals(ctx.workflowId, WorkflowSupport.extractWorkflowId(ctx, expectedName),
            "Re-lecture BO : le workflow '" + expectedName + "' devrait porter l'id " + ctx.workflowId);
        if (ctx.deepVerifySql) {
            SqlProbe.ifAvailable(sql -> sql.expectCount(1,
                "Le workflow '" + expectedName + "' devrait exister une fois en base",
                "SELECT COUNT(*) FROM workflow_workflow WHERE id_workflow = ? AND name = ?",
                ctx.workflowId, expectedName));
        }
    }

    /**
     * L'etat existe bien, rattache au workflow courant — et porte l'indicateur « initial » demande.
     *
     * <p>Remplace une assertion qui ne verifiait que l'URL de redirection.</p>
     */
    public static void state(WorkflowContext ctx, String expectedName, boolean initial) {
        if (!ctx.deepVerify) {
            return;
        }
        onSamePage(ctx.page.url(), ctx, () -> checkState(ctx, expectedName, initial));
    }

    @Step("Verification approfondie : etat « {expectedName} »")
    private static void checkState(WorkflowContext ctx, String expectedName, boolean initial) {
        WorkflowSupport.navigate(ctx, WorkflowSupport.WF + "ModifyWorkflow.jsp?id_workflow=" + ctx.workflowId);
        Assertions.assertTrue(
            ctx.page.locator("a[href*='id_state=']:has-text('" + expectedName + "')").count() > 0
                || WorkflowSupport.isTextVisible(ctx.page, expectedName),
            "Re-lecture BO : l'etat '" + expectedName + "' devrait etre liste sur le workflow " + ctx.workflowId);
        if (ctx.deepVerifySql) {
            SqlProbe.ifAvailable(sql -> {
                sql.expectExists("L'etat '" + expectedName + "' devrait exister en base",
                    "SELECT COUNT(*) FROM workflow_state WHERE id_workflow = ? AND name = ?",
                    ctx.workflowId, expectedName);
                if (initial) {
                    sql.expectExists("L'etat '" + expectedName + "' devrait etre marque initial en base",
                        "SELECT COUNT(*) FROM workflow_state"
                        + " WHERE id_workflow = ? AND name = ? AND is_initial_state = 1",
                        ctx.workflowId, expectedName);
                }
            });
        }
    }

    /** L'action existe, rattachee au workflow courant. Remplace une assertion sur l'URL seule. */
    public static void action(WorkflowContext ctx, String expectedName) {
        if (!ctx.deepVerify) {
            return;
        }
        onSamePage(ctx.page.url(), ctx, () -> checkAction(ctx, expectedName));
    }

    @Step("Verification approfondie : action « {expectedName} »")
    private static void checkAction(WorkflowContext ctx, String expectedName) {
        WorkflowSupport.openActionsPane(ctx);
        Assertions.assertNotNull(WorkflowSupport.actionRow(ctx.page, expectedName),
            "Re-lecture BO : l'action '" + expectedName + "' devrait etre listee dans l'onglet Actions"
            + " du workflow " + ctx.workflowId);
        if (ctx.deepVerifySql) {
            SqlProbe.ifAvailable(sql -> sql.expectExists(
                "L'action '" + expectedName + "' devrait exister en base",
                "SELECT COUNT(*) FROM workflow_action WHERE id_workflow = ? AND name = ?",
                ctx.workflowId, expectedName));
        }
    }

    /**
     * L'action identifiee porte bien son nouveau nom.
     *
     * <p>Controle par cle primaire : c'est la seule facon de prouver un renommage, la presence du
     * nouveau nom quelque part dans la page ne disant rien de l'action visee.</p>
     */
    public static void actionRenamed(WorkflowContext ctx, int actionId, String expectedName) {
        if (!ctx.deepVerify) {
            return;
        }
        onSamePage(ctx.page.url(), ctx, () -> checkActionRenamed(ctx, actionId, expectedName));
    }

    @Step("Verification approfondie : action {actionId} renommee en « {expectedName} »")
    private static void checkActionRenamed(WorkflowContext ctx, int actionId, String expectedName) {
        WorkflowSupport.navigate(ctx, WorkflowSupport.WF + "ModifyAction.jsp?id_action=" + actionId);
        String value = ctx.page.locator("input[name='name']").first().inputValue();
        Assertions.assertEquals(expectedName, value,
            "Re-lecture BO : l'action " + actionId + " devrait s'appeler '" + expectedName + "'");
        if (ctx.deepVerifySql) {
            SqlProbe.ifAvailable(sql -> sql.expectCount(1,
                "L'action " + actionId + " devrait porter le nom '" + expectedName + "' en base",
                "SELECT COUNT(*) FROM workflow_action WHERE id_action = ? AND name = ?",
                actionId, expectedName));
        }
    }

    // --------------------------------------------------------------- Unittree

    /** L'unite existe et porte le libelle attendu. */
    public static void unit(UnittreeContext ctx, int unitId, String expectedLabel) {
        if (!ctx.deepVerify) {
            return;
        }
        onSamePage(ctx.page.url(), ctx, () -> checkUnit(ctx, unitId, expectedLabel));
    }

    @Step("Verification approfondie : unite {unitId} = « {expectedLabel} »")
    private static void checkUnit(UnittreeContext ctx, int unitId, String expectedLabel) {
        Assertions.assertEquals(unitId, UnittreeSupport.extractUnitId(ctx, expectedLabel),
            "Re-lecture BO : le libelle '" + expectedLabel + "' devrait designer l'unite " + unitId);
        if (ctx.deepVerifySql) {
            SqlProbe.ifAvailable(sql -> sql.expectCount(1,
                "L'unite " + unitId + " devrait porter le libelle '" + expectedLabel + "' en base",
                "SELECT COUNT(*) FROM unittree_unit WHERE id_unit = ? AND label = ?",
                unitId, expectedLabel));
        }
    }

    /**
     * L'unite est rattachee au parent attendu.
     *
     * <p>Le rattachement n'est pas lisible de facon fiable dans l'arbre du back-office : le controle
     * de l'interface se limite donc a confirmer que l'unite existe toujours sous son libelle, et
     * <b>seul le controle SQL prouve le parent</b>. En mode externe (sans base), ce point de controle
     * est donc volontairement plus faible — c'est dit ici plutot que masque.</p>
     */
    public static void unitParent(UnittreeContext ctx, int unitId, String label, int expectedParentId) {
        if (!ctx.deepVerify) {
            return;
        }
        onSamePage(ctx.page.url(), ctx, () -> checkUnitParent(ctx, unitId, label, expectedParentId));
    }

    @Step("Verification approfondie : unite {unitId} rattachee au parent {expectedParentId}")
    private static void checkUnitParent(UnittreeContext ctx, int unitId, String label, int expectedParentId) {
        Assertions.assertEquals(unitId, UnittreeSupport.extractUnitId(ctx, label),
            "Re-lecture BO : l'unite '" + label + "' devrait toujours exister sous l'id " + unitId);
        if (ctx.deepVerifySql) {
            SqlProbe.ifAvailable(sql -> sql.expectCount(1,
                "L'unite " + unitId + " devrait avoir pour parent " + expectedParentId + " en base",
                "SELECT COUNT(*) FROM unittree_unit WHERE id_unit = ? AND id_parent = ?",
                unitId, expectedParentId));
        }
    }

    /**
     * L'utilisateur est affecte a l'unite.
     *
     * <p>Controle d'interface : l'utilisateur ne doit plus figurer dans la liste des affectables de
     * {@code AddUsers.jsp}. Controle SQL : la ligne existe dans {@code unittree_unit_user}.</p>
     */
    public static void userInUnit(UnittreeContext ctx, int unitId, int userId) {
        if (!ctx.deepVerify) {
            return;
        }
        onSamePage(ctx.page.url(), ctx, () -> checkUserInUnit(ctx, unitId, userId));
    }

    @Step("Verification approfondie : utilisateur {userId} affecte a l'unite {unitId}")
    private static void checkUserInUnit(UnittreeContext ctx, int unitId, int userId) {
        UnittreeSupport.navigate(ctx, UnittreeSupport.UT + "AddUsers.jsp?idUnit=" + unitId);
        Assertions.assertEquals(0,
            ctx.page.locator("input[name='idUsers'][value='" + userId + "']").count(),
            "Re-lecture BO : l'utilisateur " + userId + " ne devrait plus etre proposé a l'affectation"
            + " sur l'unite " + unitId + " puisqu'il y est deja affecte");
        if (ctx.deepVerifySql) {
            SqlProbe.ifAvailable(sql -> sql.expectExists(
                "L'utilisateur " + userId + " devrait etre affecte a l'unite " + unitId + " en base",
                "SELECT COUNT(*) FROM unittree_unit_user WHERE id_unit = ? AND id_user = ?",
                unitId, userId));
        }
    }

    /** La question existe sur l'etape visee. Remplace une assertion sur le DOM post-submit. */
    public static void question(FormsContext ctx, QuestionDataSet data) {
        if (!ctx.deepVerify || ctx.steps.isEmpty()) {
            return;
        }
        onSamePage(ctx.page.url(), ctx, () -> checkQuestion(ctx, data));
    }

    @Step("Verification approfondie : question « {data} »")
    private static void checkQuestion(FormsContext ctx, QuestionDataSet data) {
        int stepIndex = data.stepIndex() != null ? data.stepIndex() : 0;
        int stepId = ctx.steps.get(stepIndex).id;
        Assertions.assertTrue(
            AddValidationControlMacroTest.resolveQuestionId(ctx, stepId, data.title()) > 0,
            "Re-lecture BO : la question '" + data.title() + "' devrait exister sur l'etape " + stepId);
        if (ctx.deepVerifySql) {
            SqlProbe.ifAvailable(sql -> sql.expectExists(
                "La question '" + data.title() + "' devrait exister en base sur l'etape " + stepId,
                "SELECT COUNT(*) FROM forms_question WHERE id_step = ? AND title = ?",
                stepId, data.title()));
        }
    }

    /**
     * La transition relie bien les deux etapes.
     *
     * <p>La brique ne pouvait constater qu'un message flash, la vue liste ne rendant pas de carte
     * scannable : seul le controle SQL prouve reellement le lien entre les deux etapes.</p>
     */
    public static void transition(FormsContext ctx, int fromStepIndex, int toStepIndex) {
        if (!ctx.deepVerify
                || ctx.steps.size() <= Math.max(fromStepIndex, toStepIndex)) {
            return;
        }
        onSamePage(ctx.page.url(), ctx, () -> checkTransition(ctx, fromStepIndex, toStepIndex));
    }

    @Step("Verification approfondie : transition {fromStepIndex} -> {toStepIndex}")
    private static void checkTransition(FormsContext ctx, int fromStepIndex, int toStepIndex) {
        int from = ctx.steps.get(fromStepIndex).id;
        int to = ctx.steps.get(toStepIndex).id;
        if (ctx.deepVerifySql) {
            SqlProbe.ifAvailable(sql -> sql.expectExists(
                "La transition " + from + " -> " + to + " devrait exister en base",
                "SELECT COUNT(*) FROM forms_transition WHERE from_step = ? AND next_step = ?",
                from, to));
        }
    }

    /** Le controle a bien disparu. */
    public static void controlRemoved(FormsContext ctx, int controlId) {
        if (!ctx.deepVerify) {
            return;
        }
        onSamePage(ctx.page.url(), ctx, () -> checkControlRemoved(ctx, controlId));
    }

    @Step("Verification approfondie : controle {controlId} supprime")
    private static void checkControlRemoved(FormsContext ctx, int controlId) {
        if (ctx.deepVerifySql) {
            SqlProbe.ifAvailable(sql -> sql.expectNone(
                "Le controle " + controlId + " ne devrait plus exister en base",
                "SELECT COUNT(*) FROM forms_control WHERE id_control = ?", controlId));
        }
    }

    /**
     * Toutes les options du formulaire sont reellement persistees.
     *
     * <p>La brique ne relit que la case « sauvegarde ». {@code forms_form} porte les six options :
     * le controle SQL les verifie toutes d'un coup.</p>
     */
    public static void formOptions(FormsContext ctx, FormOptionsDataSet data) {
        if (!ctx.deepVerify || !ctx.deepVerifySql) {
            return;
        }
        checkFormOptions(ctx, data);
    }

    @Step("Verification approfondie : options du formulaire")
    private static void checkFormOptions(FormsContext ctx, FormOptionsDataSet data) {
        SqlProbe.ifAvailable(sql -> {
            expectFlag(sql, ctx, "backup_enabled", data.enableBackup());
            expectFlag(sql, ctx, "display_summary", data.displaySummary());
            expectFlag(sql, ctx, "one_response_by_user", data.oneResponsePerUser());
            expectFlag(sql, ctx, "authentification_needed", data.requireAuthentication());
        });
    }

    private static void expectFlag(SqlProbe sql, FormsContext ctx, String column, boolean expected) {
        sql.expectCount(1,
            "L'option " + column + " devrait valoir " + expected + " en base",
            "SELECT COUNT(*) FROM forms_form WHERE id_form = ? AND " + column + " = ?",
            ctx.formId, expected ? 1 : 0);
    }

    /** Le formulaire est reellement propose en front-office. */
    public static void formPublished(FormsContext ctx, boolean listedInFrontOffice) {
        if (!ctx.deepVerify) {
            return;
        }
        checkFormPublished(ctx, listedInFrontOffice);
    }

    @Step("Verification approfondie : formulaire publie")
    private static void checkFormPublished(FormsContext ctx, boolean listedInFrontOffice) {
        // En mode approfondi, l'absence du formulaire en front-office est un ECHEC et non un skip :
        // c'est tout l'objet du mode.
        Assertions.assertTrue(listedInFrontOffice,
            "Le formulaire '" + ctx.formTitle + "' devrait apparaitre dans la liste front-office"
            + " apres publication");
    }

    /**
     * Une reponse a reellement ete enregistree pour ce formulaire.
     *
     * <p><b>Controle SQL uniquement</b>, et c'est le fond du sujet : le front-office ne permet pas de
     * distinguer une soumission acceptee d'un echec silencieux — la validation renvoie un 302 vers un
     * formulaire vierge dans les deux cas. Sans base joignable, ce point de controle ne prouve rien,
     * ce qui est dit ici plutot que masque.</p>
     */
    public static void responseSubmitted(FormsContext ctx) {
        if (!ctx.deepVerify || !ctx.deepVerifySql) {
            return;
        }
        checkResponseSubmitted(ctx);
    }

    @Step("Verification approfondie : reponse enregistree")
    private static void checkResponseSubmitted(FormsContext ctx) {
        SqlProbe.ifAvailable(sql -> sql.expectExists(
            "Une reponse devrait etre enregistree en base pour le formulaire " + ctx.formId,
            "SELECT COUNT(*) FROM forms_response WHERE id_form = ?", ctx.formId));
    }

    /**
     * Type de ressource sous lequel le plugin forms enregistre une reponse dans le workflow.
     *
     * <p>Valeur relevee sur l'instance conteneurisee, pas supposee : une reponse soumise en
     * front-office cree la ligne
     * {@code id_resource=1 resource_type=FORMS_FORM_RESPONSE id_state=1 id_workflow=1}.</p>
     */
    private static final String FORMS_RESOURCE_TYPE = "FORMS_FORM_RESPONSE";

    /**
     * La reponse est reellement prise en charge par le workflow associe au formulaire.
     *
     * <p>Complement de {@link #responseSubmitted} : une reponse peut exister dans
     * {@code forms_response} sans etre rattachee au workflow, auquel cas aucune action ne pourra
     * jamais etre declenchee dessus. Sans workflow associe au formulaire, il n'y a rien a verifier.</p>
     */
    public static void responseInWorkflow(FormsContext ctx) {
        if (!ctx.deepVerify || !ctx.deepVerifySql || ctx.workflowId == null || ctx.workflowId <= 0) {
            return;
        }
        checkResponseInWorkflow(ctx);
    }

    @Step("Verification approfondie : reponse rattachee au workflow")
    private static void checkResponseInWorkflow(FormsContext ctx) {
        SqlProbe.ifAvailable(sql -> sql.expectExists(
            "La reponse au formulaire " + ctx.formId + " devrait etre rattachee au workflow "
            + ctx.workflowId,
            "SELECT COUNT(*) FROM workflow_resource_workflow r"
            + " JOIN forms_response f ON f.id_response = r.id_resource"
            + " WHERE r.resource_type = ? AND f.id_form = ? AND r.id_workflow = ?",
            FORMS_RESOURCE_TYPE, ctx.formId, ctx.workflowId));
    }

    /**
     * La reponse a reellement change d'etat : elle se trouve dans l'etat cible de l'action jouee.
     *
     * <p>C'est le seul controle qui prouve qu'une action de workflow a produit un effet. La brique se
     * contentait jusqu'ici d'un signal retombant sur « l'URL contient forms », vrai par
     * construction.</p>
     *
     * <p>Le controle porte sur l'etat d'arrivee ({@code id_state_after}) de l'action nommee, et non
     * sur un identifiant memorise : les references d'action du contexte ne portent pas d'id.</p>
     */
    public static void responseStateAfterAction(FormsContext ctx, String actionName) {
        if (!ctx.deepVerify || !ctx.deepVerifySql) {
            return;
        }
        checkResponseStateAfterAction(ctx, actionName);
    }

    @Step("Verification approfondie : reponse passee dans l'etat cible de « {actionName} »")
    private static void checkResponseStateAfterAction(FormsContext ctx, String actionName) {
        SqlProbe.ifAvailable(sql -> sql.expectExists(
            "La reponse au formulaire " + ctx.formId + " devrait se trouver dans l'etat cible de"
            + " l'action '" + actionName + "'",
            "SELECT COUNT(*) FROM workflow_resource_workflow r"
            + " JOIN forms_response f ON f.id_response = r.id_resource"
            + " JOIN workflow_action a ON a.id_workflow = r.id_workflow"
            + "   AND a.id_state_after = r.id_state"
            + " WHERE r.resource_type = ? AND f.id_form = ? AND a.name = ?",
            FORMS_RESOURCE_TYPE, ctx.formId, actionName));
    }

    /**
     * Une multivue vide alors que la base porte une reponse est un echec, pas un motif d'abandon.
     *
     * <p>Trois briques s'ignorent aujourd'hui sur « multivue vide ». Le relevé sur l'instance
     * conteneurisee montre que la reponse existe pourtant bien, et qu'elle est meme rattachee au
     * workflow : le skip masque donc un vrai defaut au lieu de le signaler. En mode approfondi on le
     * fait remonter — comme le fait deja {@link #formPublished}.</p>
     *
     * <p>Le controle ne se declenche que si la base confirme l'existence d'une reponse : aucune
     * chance de transformer en echec un scenario ou rien n'a effectivement ete soumis.</p>
     */
    public static void multiviewShowsResponse(FormsContext ctx, boolean opened,
            BooleanSupplier reopen) {
        if (!ctx.deepVerify || !ctx.deepVerifySql || opened) {
            return;
        }
        checkMultiviewShowsResponse(ctx, reopen);
    }

    @Step("Verification approfondie : multivue coherente avec la base")
    private static void checkMultiviewShowsResponse(FormsContext ctx, BooleanSupplier reopen) {
        if (reopenWithinIndexingWindow(reopen)) {
            return;
        }
        SqlProbe.ifAvailable(sql -> sql.expectNone(
            "La multivue reste vide " + INDEXING_WINDOW_MS / 1000 + "s apres la soumission alors"
            + " qu'une reponse existe en base pour le formulaire " + ctx.formId
            + " : la reponse n'est pas exploitable dans le back-office",
            "SELECT COUNT(*) FROM forms_response WHERE id_form = ?", ctx.formId));
    }

    /**
     * Laisse au demon d'indexation le temps de passer avant de conclure.
     *
     * <p>La multivue n'interroge pas la base : elle lit un index Lucene alimente par
     * {@code formsIndexerDaemon}, dont l'intervalle par defaut est de 30 s
     * ({@code daemon.formsIndexerDaemon.interval}). Une reponse tout juste soumise est donc
     * legitimement absente pendant un moment — conclure a l'echec immediatement fabriquerait un faux
     * negatif sur toute instance saine. La fenetre couvre deux intervalles, plus une marge.</p>
     */
    private static boolean reopenWithinIndexingWindow(BooleanSupplier reopen) {
        long deadline = System.currentTimeMillis() + INDEXING_WINDOW_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (reopen.getAsBoolean()) {
                return true;
            }
        }
        return false;
    }

    /** Deux intervalles du demon d'indexation, plus une marge. */
    private static final long INDEXING_WINDOW_MS = 75_000L;
    private static final long POLL_INTERVAL_MS = 5_000L;

    /** Le workflow est reellement actif. La brique ne verifiait que la presence de son nom. */
    public static void workflowActive(WorkflowContext ctx) {
        if (!ctx.deepVerify) {
            return;
        }
        onSamePage(ctx.page.url(), ctx, () -> checkWorkflowActive(ctx));
    }

    @Step("Verification approfondie : workflow actif")
    private static void checkWorkflowActive(WorkflowContext ctx) {
        if (ctx.deepVerifySql) {
            SqlProbe.ifAvailable(sql -> sql.expectCount(1,
                "Le workflow " + ctx.workflowId + " devrait etre actif (is_enabled) en base",
                "SELECT COUNT(*) FROM workflow_workflow WHERE id_workflow = ? AND is_enabled = 1",
                ctx.workflowId));
        }
    }

    /** Une tache du type demande est rattachee a une action du workflow courant. */
    public static void taskOnAction(WorkflowContext ctx, String taskTypeKey) {
        if (!ctx.deepVerify || !ctx.deepVerifySql) {
            return;
        }
        checkTaskOnAction(ctx, taskTypeKey);
    }

    @Step("Verification approfondie : tache « {taskTypeKey} » rattachee a une action")
    private static void checkTaskOnAction(WorkflowContext ctx, String taskTypeKey) {
        // Le filtre sur task_type_key est indispensable : sans lui, le controle passerait des qu'une
        // tache quelconque existe sur le workflow, y compris une tache posee par une autre brique.
        SqlProbe.ifAvailable(sql -> sql.expectExists(
            "Une tache '" + taskTypeKey + "' devrait etre rattachee a une action du workflow "
            + ctx.workflowId,
            "SELECT COUNT(*) FROM workflow_task t"
            + " JOIN workflow_action a ON t.id_action = a.id_action"
            + " WHERE a.id_workflow = ? AND t.task_type_key = ?", ctx.workflowId, taskTypeKey));
    }

    // ---------------------------------------------------------------- Interne

    /**
     * Restaure l'URL d'origine apres un controle qui a navigue.
     *
     * <p>Sans cela, un controle insere en fin de brique laisserait le navigateur sur une page de
     * verification, et la brique suivante — qui suppose l'etat laisse par la precedente — echouerait
     * uniquement lorsque le mode est actif.</p>
     */
    private static void onSamePage(String urlBefore, Object ctx, Runnable check) {
        com.microsoft.playwright.Page page = pageOf(ctx);
        try {
            check.run();
        } finally {
            if (!urlBefore.equals(page.url())) {
                page.navigate(urlBefore);
                page.waitForLoadState();
            }
        }
    }

    private static com.microsoft.playwright.Page pageOf(Object ctx) {
        if (ctx instanceof FormsContext) {
            return ((FormsContext) ctx).page;
        }
        if (ctx instanceof WorkflowContext) {
            return ((WorkflowContext) ctx).page;
        }
        return ((UnittreeContext) ctx).page;
    }
}
