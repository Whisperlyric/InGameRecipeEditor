配方管理系统（Recipe Management）
=================================

核心概念
- 工作区（Workspace）：基于 JEI 的配方视图，提供更大、更清晰的编辑画布。
- 草稿（Draft）：对配方所做的修改在内存中以草稿形式维护，直到用户显式提交（生成 JSON）。
- 原始 JSON（originalJson）：当从已有配方编辑或应用补丁时，工作区会保存一个原始 JSON 作为编辑基底。
- 补丁（Patch）：用于向“无编码”配方注入额外属性（例如能量槽、时间倍率、输出概率等）。

主要文件与职责
- `RecipeWorkspaceScreen.java` — 主 UI：渲染配方、槽位高亮、选取逻辑、按钮（包含编辑/标签/属性编辑）。
- `RecipeWorkspaceManager.java` — 管理多个草稿、打开/关闭工作区、与 `DraftInfo` 的生命周期。
- `RecipeEditManager.java` — 草稿的增删改、将草稿应用到 JEI 布局（display overrides）、提交生成 JSON。
- `PatchRegistry.java` — （新增）从 `refs/mod_patches` / `refs/recipe_patches` 读取并应用补丁。

工作流程
1. 打开工作区：通过 JEI 的按钮或复制打开配方。工作区管理器 `openWorkspace` 会执行：
   - 获取 recipeId 与 recipeType；
   - 如果存在 schema，则调用 `RecipeEditManager.startEdit` 保存原始槽位数据；
   - 如果无 schema，则尝试调用 `PatchRegistry.applyPatches`，把合并后的 JSON 作为 `DraftInfo.originalJson` 存入 drafts；
2. 编辑：用户可以通过左键选中槽位（渲染白框），使用底部或编辑区按钮打开：文本编辑、标签选择、物品选择、属性编辑等。所有更改写入内存草稿。
3. 预览：`RecipeEditManager.applyDraftToLayout` 会将草稿内容通过 JEI display overrides 应用于布局，以便实时查看修改效果。
4. 提交：点击 Submit 会调用 `RecipeEditManager.submit(recipeId)` 生成最终 JSON（并返回给 `RecipeWorkspaceManager` 用于保存/导出）。

验证与确保配方有效
- 使用 `refs/mod_patches/<modid>.json` 中的 `requiredFields` 列表来定义某些配方必须包含的字段。属性编辑器提供“插入必须字段”按钮，自动在草稿中创建占位字段以保证后续提交不缺失必需属性。

注意事项
- submit 流程目前生成的是依草稿与模板合并的 JSON；如果需要把结果写回到磁盘（refs/recipe_patches 或生成新的 recipe 文件），应在 `RecipeWorkspaceManager` 中添加保存/导出逻辑。
