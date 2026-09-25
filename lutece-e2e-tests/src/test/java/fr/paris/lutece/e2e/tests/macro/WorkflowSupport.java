package fr.paris.lutece.e2e.tests.macro;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Helpers Playwright partages par les briques macro Workflow (plugin en ancien style JSP-par-operation).
 */
public final class WorkflowSupport {

    public static final String WF = "/jsp/admin/plugins/workflow/";

    private WorkflowSupport() {}

    public static void navigate(WorkflowContext ctx, String relativeUrl) {
        ctx.page.navigate(ctx.baseUrl + relativeUrl);
        ctx.page.waitForLoadState();
    }

    /**
     * Navigue vers la liste des workflows et retourne l'id du workflow dont le nom correspond, ou -1.
     */
    public static int extractWorkflowId(WorkflowContext ctx, String workflowName) {
        navigate(ctx, WF + "ManageWorkflow.jsp");
        Locator link = ctx.page.locator(
            "a[href*='id_workflow=']:has-text('" + workflowName + "')").first();
        try {
            link.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.ATTACHED).setTimeout(10_000));
        } catch (RuntimeException notFound) {
            return -1;
        }
        String href = link.getAttribute("href");
        if (href == null || !href.contains("id_workflow=")) {
            return -1;
        }
        return Integer.parseInt(href.split("id_workflow=")[1].split("&")[0].split("#")[0]);
    }

    /**
     * Ouvre l'onglet « Actions » de l'edition du workflow.
     *
     * <p><b>Le parametre {@code pane} n'est pas cosmetique.</b> {@code ModifyWorkflow.jsp} ouvre par
     * defaut sur l'onglet « Etats » ; le panneau des actions est present dans le DOM mais masque,
     * donc invisible pour {@code getByText} et pour toute assertion de visibilite. Une re-lecture
     * faite sans ce parametre conclut systematiquement que l'action n'existe pas — alors qu'elle est
     * bien en base. C'est precisement ce qui rendait {@code RemoveAction} ineluctablement ignore et
     * {@code ModifyAction} faussement vert.</p>
     */
    public static void openActionsPane(WorkflowContext ctx) {
        navigate(ctx, WF + "ModifyWorkflow.jsp?id_workflow=" + ctx.workflowId + "&pane=pane-actions");
    }

    /**
     * Ligne de l'action portant exactement ce nom dans l'onglet « Actions », ou {@code null}.
     *
     * <p>Les liens d'une action portent des libelles generiques (« Modifier l'action », « Copier
     * l'action », « Supprimer l'action ») : chercher un lien {@code :has-text(nom)} ne peut donc
     * jamais aboutir. Le nom vit dans la ligne qui contient ces liens, sur sa premiere ligne de
     * texte. La comparaison est exacte, pour qu'« Valider » ne selectionne pas « Valider et clore ».</p>
     *
     * <p>Prerequis : {@link #openActionsPane} a ete appele.</p>
     */
    public static Locator actionRow(Page page, String actionName) {
        Locator rows = page.locator("div.row:has(a[href*='ModifyAction.jsp?id_action='])");
        for (int i = 0; i < rows.count(); i++) {
            Locator row = rows.nth(i);
            if (actionName.equals(firstLine(row.innerText()))) {
                return row;
            }
        }
        return null;
    }

    /** Id de l'action portant ce nom dans l'onglet « Actions », ou -1. Voir {@link #actionRow}. */
    public static int actionId(Page page, String actionName) {
        Locator row = actionRow(page, actionName);
        if (row == null) {
            return -1;
        }
        String href = row.locator("a[href*='ModifyAction.jsp?id_action=']").first().getAttribute("href");
        if (href == null || !href.contains("id_action=")) {
            return -1;
        }
        return Integer.parseInt(href.split("id_action=")[1].split("&")[0].split("#")[0]);
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        for (String line : text.split("\n")) {
            if (!line.isBlank()) {
                return line.trim();
            }
        }
        return "";
    }

    /** Vrai si le texte est visible sur la page (helper d'assertion). */
    public static boolean isTextVisible(Page page, String text) {
        try {
            Locator loc = page.getByText(text);
            return loc.count() > 0 && loc.first().isVisible();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
