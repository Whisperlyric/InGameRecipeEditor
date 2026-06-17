Extensibility and Secondary Development Guide
=============================================

This section introduces how to extend functionality in RecipesHelper: add new schema adapters, renderers, selectors, or improve the patch system.

Main Extension Points
1. Schema Adapter (schema/adapter)
   - Purpose: Map different mod/recipe formats to editor-understandable `RecipeSchema` / `SlotDefinition`.
   - Implementation Steps:
     a. Add a new class under `schema/adapter` (e.g., `MyModAdapter.java`) that implements parsing from the mod's recipe JSON to `RecipeSchema`.
     b. Register the adapter in `SchemaRegistry` so the editor can load the schema when encountering the corresponding recipeType.

2. Custom Renderer (e.g., Chemical Renderer)
   - File: `workspace/ChemicalSlotRenderer.java` as an example.
   - Adding: If a mod introduces new resource types, add corresponding checks (isChemicalStack extension) and render functions, and support that type in applyDraftToLayout.

3. Selector/Preview Interface
   - `TagSelectorScreen`, `ItemSelectorScreen` can serve as example implementations.
   - To support new resource types, copy and modify these interfaces: provide representative element rendering, searcher, and callback Consumer.

4. Patch System Enhancement
   - Current MVP uses simple path->value merging; can upgrade to:
     - JSON Patch (RFC6902) for richer array operations and delete/move;
     - Patch template versioning and compatibility checks (provide differentiated patches for different Minecraft/mod versions);
     - GUI management panel: graphically enable/disable patches and save to `refs/recipe_patches`.

Practice Suggestions
- Maintain single responsibility: separate render/GUI/patch logic. New renderers only handle rendering; data modifications are done by `RecipeEditManager` or `PatchRegistry`.
- Use `RecipeWorkspaceManager.DraftInfo`'s originalJson to store temporary JSON; do not modify refs files without user confirmation.
- Use reflection detection for external dependencies (e.g., Mekanism types) (refer to existing code) to ensure safe operation when the mod is absent.

Example: Adding a Mod Adapter
1. Create new `schema/adapter/MyModAdapter.java` and implement `RecipeSchema parse(JsonObject recipeJson)`. Fill slot's primaryJsonPath, amountPath, amountScale, etc. into `SlotDefinition`.
2. Register the adapter in `SchemaRegistry`: return `Optional.of(parsedSchema)` when recipeType matches.