Recipe Management System
========================

Core Concepts
- Workspace: Based on JEI's recipe view, provides a larger and clearer editing canvas.
- Draft: Modifications made to recipes are maintained in memory as drafts until the user explicitly submits (generates JSON).
- Original JSON (originalJson): When editing from an existing recipe or applying patches, the workspace saves an original JSON as the editing base.
- Patch: Used to inject additional properties into "codeless" recipes (e.g., energy slots, time multiplier, output probability, etc.).

Main Files and Responsibilities
- `RecipeWorkspaceScreen.java` — Main UI: renders recipes, slot highlighting, selection logic, buttons (including edit/tag/property edit).
- `RecipeWorkspaceManager.java` — Manages multiple drafts, opens/closes workspaces, and the lifecycle of `DraftInfo`.
- `RecipeEditManager.java` — Draft CRUD operations, applies drafts to JEI layout (display overrides), submits to generate JSON.
- `PatchRegistry.java` — (New) Reads and applies patches from `refs/mod_patches` / `refs/recipe_patches`.

Workflow
1. Open Workspace: Open a recipe via JEI button or copy. The workspace manager `openWorkspace` will:
   - Get recipeId and recipeType;
   - If a schema exists, call `RecipeEditManager.startEdit` to save original slot data;
   - If no schema, try calling `PatchRegistry.applyPatches`, storing the merged JSON as `DraftInfo.originalJson` in drafts;
2. Edit: Users can select slots with left-click (renders white frame), use bottom or edit area buttons to open: text edit, tag selection, item selection, property edit, etc. All changes are written to memory drafts.
3. Preview: `RecipeEditManager.applyDraftToLayout` applies draft content to the layout via JEI display overrides for real-time preview of modifications.
4. Submit: Clicking Submit calls `RecipeEditManager.submit(recipeId)` to generate final JSON (and returns to `RecipeWorkspaceManager` for saving/exporting).

Validation and Ensuring Recipe Validity
- Use the `requiredFields` list in `refs/mod_patches/<modid>.json` to define fields that certain recipes must contain. The property editor provides an "Insert Required Fields" button that automatically creates placeholder fields in the draft to ensure subsequent submissions don't miss required properties.

Notes
- The submit process currently generates JSON merged from draft and template; if you need to write results back to disk (refs/recipe_patches or generate new recipe files), add save/export logic in `RecipeWorkspaceManager`.