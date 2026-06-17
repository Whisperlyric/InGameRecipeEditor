模组适配与补丁定义（Mod Adapter & Patch 定义）
=================================================

目标
- 让编辑器对“无编码”或未提供 schema 的配方，能够通过可配置的补丁（patch）注入模组通用/可复用的配方属性（例如能量槽参数、处理时间倍率、输出概率等），并且允许在 recipe 层开启/关闭这些补丁。

目录结构（约定）
- refs/mod_patches/<modid>.json — 模组级别的 patch 定义与 required 字段。
- refs/recipe_patches/<modid>_<recipeid>.json — 配方级别的控制文件：开启/关闭 mod 中的 patch，并编写 localPatches。

`mod_patches` 文件格式（示例）
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

`recipe_patches` 文件格式（示例）
```
{
  "recipeId": "examplemod:cooler",
  "enabledPatches": { "default_energy_slot": true },
  "localPatches": { "/outputs/0/chance": 0.75 }
}
```

实现要点
- PatchRegistry 会在打开工作区时尝试读取以上两个文件并把被启用的补丁应用到草稿 JSON。
- Patch 的 path 使用 `/` 分隔路径；数字段视为数组索引。PatchRegistry 调用 `RecipeEditManager.setJsonAtPath` 写入值。

如何为模组添加 Patch
1. 在仓库 `refs/mod_patches/` 新建 `<modid>.json` 并按格式添加 patchDefinitions 与 requiredFields。
2. 对于已存在的配方，可在 `refs/recipe_patches/` 下新建对应控制文件并开启/禁用 patch，或手动在工作区使用属性编辑器插入 localPatches。

扩展建议
- 若 patch 需要复杂的数组操作（中间插入、按条件删除），考虑改成 RFC6902 JSON Patch 格式并在 PatchRegistry 中集成现成库来应用 patch。
- 若模组提供了自己的 schema（slot definition 等），优先支持 schema；patch 仅作补充属性或在无 schema 时填充必需字段。
