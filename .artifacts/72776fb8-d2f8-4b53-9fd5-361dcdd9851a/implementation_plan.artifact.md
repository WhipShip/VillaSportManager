# HomeScreen Redesign Plan

Redesign the `HomeScreen` to feature a hero background, a "Welcome [Name]" top section, and a dynamic 3x3 grid of buttons that adapts to both portrait and landscape orientations.

## User Review Required

> [!IMPORTANT]
> The layout will use a `Box` to layer the Hero background behind the content. In landscape mode, the height of the hero section might be reduced to ensure the 3x3 grid remains visible without excessive scrolling.

## Proposed Changes

### UI Components

#### [MODIFY] [HomeScreen.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/ui/features/home/HomeScreen.kt)

- **Layout Structure**:
    - Root `Box` to hold the Hero background.
    - `Column` for foreground content.
    - `TopSection`: A composable for "Welcome [Name]".
    - `BottomSection`: A responsive 3x3 grid using `LazyVerticalGrid` or nested `Rows/Columns` with `weight` to ensure equal margins and dynamic sizing.
- **Margins**: Define a constant `GRID_SPACING` for easy adjustment of margins between buttons.
- **Responsiveness**: Use `LocalConfiguration` or `WindowSizeClass` to adjust padding, font sizes, and hero height based on orientation.

## Verification Plan

### Automated Tests
- I will use `render_compose_preview` to verify the layout in both portrait and landscape modes.

### Manual Verification
- Deploy to the emulator and rotate the device to ensure the layout adapts correctly.
