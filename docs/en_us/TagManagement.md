Tag Management System
=====================

Feature Overview
- List and preview game and custom tags (items/blocks/fluids)
- Create/edit custom tags (save them in `refs/tags/` or config location)
- Preview/select tags in workspace via tag button (supports left-click preview, right-click select and return)

Main Files
- `src/main/java/.../gui/TagSelectorScreen.java` — Tag selector interface
- `src/main/java/.../gui/TagPreviewScreen.java` — Tag preview interface
- `src/main/java/.../tags/CustomTagManager.java` — Custom tag persistence and query

Usage Instructions (User)
1. Open a slot in the workspace and click the "Tag" button.
2. In `TagSelector`:
   - Left-click on an item: Enter `TagPreviewScreen` to view tag contents;
   - Right-click on an item: Immediately select that tag and return to the parent interface (tag is written to slot draft as a `#modid:path` string).
3. In `TagPreviewScreen`, you can right-click again to confirm selection (if callback is implemented).

Add/Edit Custom Tags (Developer)
1. Use the API provided by `CustomTagManager` to register or write custom tag JSON files (see `CustomTagManager` implementation)
2. Custom tag file structure reference: existing `refs/tags` examples (items/fluids subdirectories)
3. The editor will scan these custom tags on startup or when first opening the tag interface and merge them into the selectable list

Extension Points
- To support new resource types (e.g., custom chemicals), add corresponding rendering/representative elements in `TagSelectorScreen` and `TagEntry`; and extend save/load logic in `CustomTagManager`.