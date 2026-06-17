RecipesHelper — Usage and Extension Documentation
===================================================

This directory contains user and developer documentation for the RecipesHelper mod editor. The documentation is written in English and covers: tag management system, recipe management system, mod adapter and patch mechanism, as well as extensibility points and examples for each functional module.

Documentation List:
- `TagManagement.md` — Tag Management System (create/edit/customize tags)
- `RecipeManagement.md` — Recipe Management System: workspace, drafts, submission process and patch application
- `ModAdapter.md` — Mod Adapter and schema/patch definition format (how to provide patches and required fields for a mod)
- `Extensibility.md` — Module extension points, how to add new adapters, renderers or UI features

How to Start:
1. Read `RecipeManagement.md` to understand the workspace opening / draft generation / submission process.
2. To provide generic property patches for a specific mod, refer to the `refs/mod_patches` and `refs/recipe_patches` examples in `ModAdapter.md`.
3. If you need to extend the tag system or add new resource selectors, see `TagManagement.md` and `Extensibility.md`.