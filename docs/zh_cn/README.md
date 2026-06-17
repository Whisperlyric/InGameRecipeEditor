RecipesHelper — 使用与扩展文档
=================================

本目录包含 RecipesHelper 模组编辑器的用户与开发文档。文档以中文撰写，覆盖：标签管理系统、配方管理系统、模组适配与补丁机制，以及各功能模块的可扩展点和示例。

文档列表：
- `TagManagement.md` — 标签管理系统（创建/编辑/自定义标签）
- `RecipeManagement.md` — 配方管理系统：工作区、草稿、提交流程与补丁应用
- `ModAdapter.md` — 模组适配与 schema / patch 定义格式（如何为一个模组提供补丁与 required 字段）
- `Extensibility.md` — 模块扩展点、如何添加新的 adapter、渲染器或 UI 功能

如何开始：
1. 阅读 `RecipeManagement.md`，了解工作区打开 / 草稿生成 / 提交流程。
2. 若要为某个模组提供通用属性补丁，参考 `ModAdapter.md` 中 `refs/mod_patches` 和 `refs/recipe_patches` 示例。
3. 若需要扩展标签系统或添加新的资源选择器，请查看 `TagManagement.md` 与 `Extensibility.md`。
