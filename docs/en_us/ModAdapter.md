Mod Adapter & Patch Definition
==============================

Goal
- Enable the editor to inject mod-generic/reusable recipe properties (e.g., energy slot parameters, processing time multiplier, output probability, etc.) into "codeless" or schemaless recipes via configurable patches, and allow enabling/disabling these patches at the recipe level.

Directory Structure (Convention)
- refs/mod_patches/<modid>.json — Mod-level patch definition and required fields.
- refs/recipe_patches/<modid>_<recipeid>.json — Recipe-level control file: enable/disable patches in mod, and write localPatches.

`mod_patches` File Format (Example)
```
{
  "modId": "examplemod",
  "requiredFields": ["/energySlots/0/maxEnergy", "/processing/time"],
  "patchDefinitions": {
    "default_energy_slot": {
      "description": "...",
      "patch": { "/energySlots/0/maxEnergy": 100000 },
      "scope": "slot",
      "usageCountHint": 5
    }
  }
}
```

`recipe_patches` File Format (Example)
```
{
  "recipeId": "examplemod:cooler",
  "enabledPatches": { "default_energy_slot": true },
  "localPatches": { "/outputs/0/chance": 0.75 }
}
```

Implementation Points
- PatchRegistry will attempt to read the above two files when opening a workspace and apply enabled patches to the draft JSON.
- Patch paths use `/` as separator; numeric segments are treated as array indices. PatchRegistry calls `RecipeEditManager.setJsonAtPath` to write values.

How to Add Patches for a Mod
1. Create a new `<modid>.json` in the repository `refs/mod_patches/` and add patchDefinitions and requiredFields according to the format.
2. For existing recipes, create corresponding control files under `refs/recipe_patches/` to enable/disable patches, or manually use the property editor in the workspace to insert localPatches.

Extension Suggestions
- If patches require complex array operations (insert in middle, conditional deletion), consider switching to RFC6902 JSON Patch format and integrate an existing library in PatchRegistry to apply patches.
- If a mod provides its own schema (slot definition, etc.), prioritize schema support; patches should only serve as supplementary properties or fill required fields when no schema exists.