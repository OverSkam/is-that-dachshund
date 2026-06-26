# MedicalVideoAnnotator / projectH

A Java desktop application for annotating medical videos, mainly laparoscopic surgery videos. The main workflow is: open a video, navigate to a specific frame, draw polygons around anatomical structures or instruments, save the annotation project, and export annotations to COCO JSON for training segmentation models in Python.

The project was developed step by step as a learning-oriented JavaFX/JavaCV application. One important goal is that the code remains understandable for a developer with basic/intermediate Java experience who has not previously worked with JavaFX Canvas, JavaCV, or mouse-event based drawing.

## Current Stack

- Java 21.
- JavaFX 21.0.4 for the UI.
- JavaCV `javacv-platform` 1.5.10 for video reading through FFmpeg.
- Jackson Databind 2.17.2 for JSON.
- Maven.
- IntelliJ IDEA as the main IDE.
- `jpackage` for building a Windows `.exe` installer.
- GitHub Actions for building macOS `.dmg` packages.

## Application Purpose

The user opens a video, and the application displays individual frames on a JavaFX `Canvas`. On each frame, the user can:

- navigate frames: next, previous, or jump to a specific frame number;
- hold Next/Previous buttons for faster frame navigation;
- draw a polygon by clicking with a mouse or touchpad;
- close a polygon using a button or hotkey;
- reopen an accidentally closed polygon;
- choose an object class/category;
- create custom classes;
- bind keys to classes;
- remove a key binding with `Bind Key` + `Esc`;
- set confidence and uncertainty metadata;
- edit polygons: select, move points, delete points, delete polygons, add a new point on an edge;
- mark problematic frames;
- set frame quality;
- save and load project files;
- export and import COCO JSON;
- export video frames together with annotations.

## Project Structure

Source code is located here:

```text
src/main/java/overskam/projectH
```

## Main Classes and Packages

### `MainApp.java`

The JavaFX application entry point. It builds the UI: buttons, panels, input fields, canvas, and layout. The current architectural decision is that `MainApp` knows which controls exist and where they are placed, but it should not contain the main business logic for those controls.

If you need to change the UI layout, start here.

### `Launcher.java`

The entry point used by the shaded/fat jar. This is needed so the packaged jar starts correctly. In `pom.xml`, the `maven-shade-plugin` manifest main class points to `overskam.projectH.Launcher`.

### `controller/AnnotationController.java`

The main application logic. This class connects the UI, video reader, project state, renderer, storage, and export/import logic.

It is responsible for:

- button handlers;
- mouse and keyboard handlers;
- Draw/Edit modes;
- creating, closing, reopening, and editing polygons;
- category, confidence, and uncertainty selection;
- category key bindings;
- project save/load;
- autosave;
- COCO import/export;
- frame navigation;
- calling `CanvasRenderer.redraw(...)`.

### `controller/InteractionMode.java`

Enum for interaction modes, such as drawing and editing.

### `render/CanvasRenderer.java`

Responsible only for drawing on the JavaFX Canvas.

It draws:

- the current video frame;
- completed polygons;
- the current open polygon;
- polygon points;
- the selected polygon;
- hovered point/polygon highlights;
- category labels;
- zoom/pan viewport.

Category colors are defined here:

```java
private static final Color[] CATEGORY_PALETTE = { ... };
```

The application currently uses exactly 10 static colors in a cycle. Color order follows the order of categories in the category list. After the 10th category, colors repeat from the beginning.

The first colors are:

1. light green
2. red
3. orange
4. light purple
5. pink
6. light blue
7. bright blue
8. green
9. yellow
10. magenta

These colors are only for UI visualization. They do not affect how YOLO or another model will display predicted segmentation after training. COCO export stores polygon geometry and `category_id`, not the visual outline color.

### `render/ImageViewport.java`

Handles coordinate conversion between canvas coordinates and original image/frame coordinates.

This is critical because the frame may be scaled and centered on the canvas. The user clicks in canvas coordinates, but polygon points must be stored in original frame coordinates.

Typical flow:

```text
MouseEvent canvas x/y -> ImageViewport.canvasToImage(...) -> store point in frame coordinates
frame point -> ImageViewport.imageToCanvas(...) -> draw point/line on canvas
```

If polygon lines and points do not visually match, the most likely cause is mixing canvas coordinates and original image coordinates.

### `video/VideoFrameReader.java`

Wrapper around JavaCV/FFmpegFrameGrabber.

Responsible for:

- opening video files;
- reading the current frame;
- jumping to a frame number;
- next/previous navigation;
- converting decoded frames into JavaFX `Image`.

There have previously been issues with visually corrupted frames. The likely cause is not always the source video itself, but seek/decode behavior through FFmpeg/JavaCV, especially with inter-frame codecs. The code already includes more careful frame reading/warmup logic, but if this issue appears again, inspect this class first.

### `model/AnnotationProject.java`

Stores the annotation data in memory. The main structure is polygons attached to frame numbers.

Important concept: when navigating to another frame, polygons from the previous frame must not remain visible as the current frame's annotations. Only polygons belonging to the current frame are rendered, while data for other frames remains stored in the project.

### `model/AnnotationPolygon.java`

Model of one polygon.

Contains:

- `frameIndex`;
- `points`;
- `categoryName`;
- `confidence`;
- `uncertainty`.

### `model/AnnotatedFrame.java`

Model for additional per-frame metadata, such as problem-frame flag and frame quality.

### `model/CategoryStore.java`

Global storage for user-created classes/categories.

Default categories:

```text
gallbladder
cystic_duct
cystic_artery
liver
instrument
```

User-created categories are saved locally on the current computer:

```text
%USERPROFILE%\.medical-video-annotator\categories.txt
```

On Windows for the current user, this is approximately:

```text
C:\Users\super\.medical-video-annotator\categories.txt
```

Important: if the app is installed on another PC, custom categories from this machine will not automatically exist there. However, if a `.mvap.json` project is opened, the category list saved inside that project is loaded.

### `model/CategoryKeyBindingStore.java`

Stores keyboard bindings for categories:

```text
%USERPROFILE%\.medical-video-annotator\category-key-bindings.properties
```

To remove a binding: select the category, press `Bind Key`, then press `Esc`.

### `export/CocoExporter.java`

Exports annotations to COCO JSON.

The COCO structure includes:

- `info`;
- `licenses`;
- `images`;
- `annotations`;
- `categories`.

Important decisions:

- `image.id` should not simply be the frame number, because this creates collisions when combining several operations/videos;
- `file_name` includes operation/video/frame information;
- image entries may contain extra fields such as `operation_id`, `frame_index`, `problem_frame`, and `frame_quality`;
- annotation entries contain polygon segmentation, `category_id`, confidence, and uncertainty reason.

### `export/CocoImporter.java`

Imports COCO JSON back into the project and restores polygons on the correct frames.

### `storage/ProjectFileStore.java` and `ProjectFileData.java`

Save/load the custom project format `.mvap.json`.

This format is used to continue annotation work later, not just to export final COCO files.

It saves:

- video path/information;
- category list;
- polygons;
- frame metadata.

## Local Settings Files

The app writes settings into the user's home directory:

```text
%USERPROFILE%\.medical-video-annotator\
```

Possible files/folders:

```text
categories.txt
category-key-bindings.properties
autosave\
```

These files are not inside the Git repository and are not automatically copied to another PC.

## Data Formats

### `.mvap.json`

The application's working project format. Use this format when the annotation work needs to be continued later.

### COCO `.json`

The export format for Python dataset preparation and model training.

Before training on multiple videos, it is recommended to have a separate Python merge script that combines JSON files from different operations and recalculates global `image_id` and `annotation_id` values without collisions.

## Build and Run from IntelliJ IDEA

The project is intended to be used with IntelliJ IDEA.

Using the Maven tool window:

1. Open the project.
2. Open the `Maven` panel on the right side.
3. Expand `projectH`.
4. Expand `Lifecycle`.
5. Double-click `compile` to verify compilation.
6. Double-click `package` to build the jar.

If the Maven window is not visible:

```text
View -> Tool Windows -> Maven
```

## Maven Commands from PowerShell

If plain `mvn` is not available in PATH, use the Maven bundled with IntelliJ IDEA:

```powershell
cd "C:\Users\super\Java Projects\projectH\projectH"
& "C:\Program Files\JetBrains\IntelliJ IDEA 2025.2\plugins\maven\lib\maven3\bin\mvn.cmd" compile
```

Build the jar:

```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA 2025.2\plugins\maven\lib\maven3\bin\mvn.cmd" package
```

After building, look for jars here:

```text
target\
```

The important jar for packaging is usually:

```text
target\projectH-1.0-SNAPSHOT-all.jar
```

This jar is large because it includes dependencies. Do not push it to GitHub. GitHub rejects files larger than 100 MB.

## Windows `.exe` Installer

To build a Windows installer, you need a JDK with `jpackage` and WiX Toolset 3.x.

Check tools:

```powershell
java -version
jpackage --version
where.exe candle.exe
where.exe light.exe
```

Build the jar:

```powershell
cd "C:\Users\super\Java Projects\projectH\projectH"
& "C:\Program Files\JetBrains\IntelliJ IDEA 2025.2\plugins\maven\lib\maven3\bin\mvn.cmd" package
```

Prepare the input folder:

```powershell
Remove-Item package-input -Recurse -Force -ErrorAction SilentlyContinue
mkdir package-input
copy "target\projectH-1.0-SNAPSHOT-all.jar" "package-input\"
```

Build the installer:

```powershell
jpackage `
  --type exe `
  --name MedicalVideoAnnotator `
  --input package-input `
  --main-jar projectH-1.0-SNAPSHOT-all.jar `
  --main-class overskam.projectH.Launcher `
  --dest installer-output `
  --app-version 1.0.0 `
  --win-menu `
  --win-shortcut
```

The installer will be created here:

```text
installer-output\
```

## macOS Build

A native macOS `.dmg` cannot be reliably built on Windows with `jpackage`. It must be built on a macOS runner.

The project contains GitHub Actions workflows:

```text
.github/workflows/build-macos.yml
.github/workflows/build-macos-intel.yml
```

One workflow is for Apple Silicon / ARM, and the other is for Intel / x86_64. If a `.dmg` on Mac says: “application is not supported on this Mac”, it usually means the app was built for the wrong architecture.

Use an x86_64 build for Intel Macs. Use an ARM/aarch64 build for Apple Silicon Macs.

## Git/GitHub Rules

Do not push large build artifacts:

```text
target/
package-input/
installer-output/
dist/
```

`package-input/projectH-1.0-SNAPSHOT-all.jar` was previously too large for GitHub. If such a file gets committed, remove it from the commit/history before pushing.

## UI and UX Decisions

The current UI uses a dark theme. CSS file:

```text
src/main/resources/dark-theme.css
```

Resource image/icon:

```text
src/main/resources/tb.png
```

The UI is intentionally a dense work tool, not a landing page. Fast access to video, frames, classes, editing tools, metadata, and export is more important than decorative layout.

## Hotkeys and Text Input

Important issues that have already appeared:

- `Enter` inside a `TextField` may trigger input behavior or press the last focused/default button, so closing polygons should also have an explicit button and careful focus handling.
- MacBook touchpad clicks work as normal JavaFX mouse click events.
- Dragging a polygon point should use mouse press/drag/release.
- If focus is inside the frame number input, Backspace must edit the text instead of triggering an app shortcut.
- Category key bindings are user-configurable, but reserved keys should not break core application actions.

## Drawing and Coordinates

The most important Canvas rule:

- `MouseEvent.getX()/getY()` returns canvas coordinates, not original frame coordinates.
- The frame is usually scaled and centered.
- To save polygon points, convert canvas coordinates to image coordinates through `ImageViewport`.
- To draw saved points, convert image coordinates back to canvas coordinates.

If lines and points do not match visually, the bug is almost always caused by mixing coordinate systems.

## Category Colors

Colors are defined in:

```text
src/main/java/overskam/projectH/render/CanvasRenderer.java
```

Array:

```java
private static final Color[] CATEGORY_PALETTE = { ... };
```

The application currently uses exactly 10 static colors in a cycle. Do not use hash-based colors: they can be low-contrast and unpredictable.

## Known Risks and Future Improvements

1. COCO export should be finally validated on the Python side with pycocotools or a custom loader.
2. For a large dataset, create a Python merge script that combines exports per operation and creates train/val/test splits by `operation_id`.
3. Frame seeking in long videos should be tested carefully: JavaCV/FFmpeg can sometimes show artifacts after seeking.
4. Custom categories are local; team workflows need settings export/import or a project template.
5. macOS apps without notarization may be blocked or warned about by Gatekeeper. This is separate from building the `.dmg`.

## Quick Onboarding for a New Agent

If continuing development:

1. Open the project:

```text
C:\Users\super\Java Projects\projectH\projectH
```

2. Verify compilation:

```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA 2025.2\plugins\maven\lib\maven3\bin\mvn.cmd" compile
```

3. Before making changes, read at least:

```text
MainApp.java
controller/AnnotationController.java
render/CanvasRenderer.java
render/ImageViewport.java
model/AnnotationProject.java
model/AnnotationPolygon.java
video/VideoFrameReader.java
export/CocoExporter.java
storage/ProjectFileStore.java
```

4. Do not break existing decisions without a reason:

- `MainApp` builds the UI;
- `AnnotationController` owns the logic;
- `CanvasRenderer` only draws;
- polygon coordinates are stored in original frame coordinates;
- COCO export must preserve metadata, confidence, uncertainty, and operation/frame fields;
- category colors are static: 10 colors in a cycle.

5. Run compile after every change.

## Current Maven Warning

During compile, this warning may appear:

```text
location of system modules is not set in conjunction with -source 21
--release 21 is recommended instead of -source 21 -target 21
```

This is a warning, not an error. It can be fixed later by replacing `maven.compiler.source/target` with `maven.compiler.release`, but it does not currently break the build.