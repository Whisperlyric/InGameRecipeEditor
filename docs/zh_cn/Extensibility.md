扩展点与二次开发指南（Extensibility）
================================================

本节介绍如何在 RecipesHelper 中扩展功能：添加新的 schema 适配器、渲染器、选择器或完善补丁系统。

主要扩展点
1. Schema 适配器（schema/adapter）
   - 目的：将不同模组/配方格式映射为编辑器可理解的 `RecipeSchema` / `SlotDefinition`。
   - 实现步骤：
     a. 在 `schema/adapter` 下新增类（例如 `MyModAdapter.java`），实现从模组的配方 JSON 到 `RecipeSchema` 的解析。
     b. 在 `SchemaRegistry` 注册该 adapter，使编辑器在遇到对应 recipeType 时能加载 schema。

2. 自定义渲染器（例如化学物质渲染）
   - 文件：`workspace/ChemicalSlotRenderer.java` 为示例。
   - 添加：若模组引入新类型资源，增加对应判断（isChemicalStack 扩展）与渲染函数，并在 applyDraftToLayout 中支持该类型。

3. 选择器/预览界面
   - `TagSelectorScreen`、`ItemSelectorScreen` 可作为示例实现。
   - 若需支持新的资源类型，复制并修改这些界面：提供代表元素渲染、搜索器和回调 Consumer。

4. 补丁系统增强
   - 当前 MVP 使用简单 path->value 合并；可升级为：
     - JSON Patch（RFC6902）支持更丰富的数组操作与删除/移动；
     - 补丁模板版本与兼容性检查（为不同 Minecraft/模组版本提供差异化补丁）；
     - GUI 管理面板：图形化启用/禁用 patch 并保存到 `refs/recipe_patches`。

实践建议
- 保持单一职责：render/GUI/patch logic 分离，新的渲染器只负责渲染，数据修改由 `RecipeEditManager` 或 `PatchRegistry` 完成。
- 使用 `RecipeWorkspaceManager.DraftInfo` 的 originalJson 存储临时 JSON；不要在没有用户确认时修改 refs 文件。
- 对外部依赖（例如 Mekanism 类型）使用反射检测（参考现有代码），以保证在无该模组时安全运行。

示例：新增模组 adapter
1. 新建 `schema/adapter/MyModAdapter.java` 并实现 `RecipeSchema parse(JsonObject recipeJson)`。将 slot 的 primaryJsonPath、amountPath、amountScale 等填入 `SlotDefinition`。
2. 在 `SchemaRegistry` 中注册该 adapter：当 recipeType 匹配时返回 `Optional.of(parsedSchema)`。
