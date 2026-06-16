# InGameRecipeEditor

An open-source Minecraft Forge mod for dynamically managing and generating crafting recipes at runtime via JEI interface.

The mod provides intuitive buttons in the JEI recipe viewer to disable, edit, copy, and create recipes without restarting the game. It features a workspace system for recipe editing, custom tag management, and extensible schema-based recipe processors for mod compatibility.

This project is actively developed and released under Apache-2.0 license.

## Features

- Recipe management buttons integrated in JEI interface (disable/enable, edit, copy, add)
- Workspace editor for creating and modifying recipes with ingredient selectors
- Custom tag creation and management system
- Extensible recipe schema system supporting vanilla and modded recipes (Mekanism, etc.)
- Pinyin-based search support for Chinese users
- Compatible with multiple popular mods

## AI Assistance Declaration

This project was developed with the assistance of generative AI tools (GLM-5, Claude, etc.) for coding, code review suggestions, and documentation improvements.

**Developer Commitment:**

- All AI-generated code has been manually reviewed, tested, and verified by the project maintainer.
- AI-generated code does not contain direct copies of core logic from closed-source copyrighted works.
- Despite AI assistance, all ownership and responsibility for this project lies with the maintainer and contributors.

For any questions about code implementation, feel free to submit an Issue for discussion.

## Acknowledgments

This project references and draws inspiration from the following projects:

### [JEIRecipeManager](https://github.com/taisuiyileba/JEIRecipeManager)

- **Usage**: Reference for GUI implementation logic
- **Note**: Only the logic was referenced and re-implemented independently for Minecraft 1.20.1
- **License**: No license specified (default: all rights reserved)

### [RecipesHelper](https://github.com/WoZhiZhan/RecipesHelper)

- **Usage**: Reference for partial recipe editing logic implementation and repository structure
- **License**: Apache-2.0
- **Note**: This repository was forked from RecipesHelper and extended with Mekanism support, logic improvements, and UI optimizations

## Third-party Libraries

- [TinyPinyin](https://github.com/promeG/TinyPinyin) - Pinyin search support for Chinese users

## License

Apache-2.0 License