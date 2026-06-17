标签管理系统（Tag Management System）
=====================================

功能概述
- 列出并预览游戏与自定义标签（物品/方块/流体）
- 创建/编辑自定义标签（将其保存在 `refs/tags/` 或配置位置）
- 在工作区中通过标签按钮预览/选择标签（支持左键预览，右键选中并返回）

主要文件
- `src/main/java/.../gui/TagSelectorScreen.java` — 标签选择器界面
- `src/main/java/.../gui/TagPreviewScreen.java` — 标签预览界面
- `src/main/java/.../tags/CustomTagManager.java` — 自定义标签持久化与查询

使用说明（用户）
1. 在工作区中打开某个槽位，点击“标签”按钮。
2. 在 `TagSelector` 中：
   - 左键点击某一项：进入 `TagPreviewScreen` 查看标签包含内容；
   - 右键点击某一项：马上选中该标签并返回上级界面（标签以 `#modid:path` 的字符串形式写入槽位草稿）。
3. 在 `TagPreviewScreen` 中可再次右键确认选择（如果实现了回调）。

添加/编辑自定义标签（开发者）
1. 使用 `CustomTagManager` 提供的 API 注册或写入自定义标签 JSON 文件（参见 `CustomTagManager` 实现）
2. 自定义标签文件结构参考现有 `refs/tags` 示例（items/fluids 两种子目录）
3. 编辑器会在启动或第一次打开标签界面时扫描这些自定义标签并合并到可选列表

扩展点
- 若要支持新的资源类型（例如自定义化学物质），在 `TagSelectorScreen` 和 `TagEntry` 中添加对应的渲染/代表元素；并在 `CustomTagManager` 中扩展保存/加载逻辑。
