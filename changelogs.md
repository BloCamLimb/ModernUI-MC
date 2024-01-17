### Modern UI 3.10.0.2
#### Forge Extension 1.19.2
* Add Markdown preview
* Use vanilla border style for modern tooltip (when rounded = false)
* Add font names for registered fonts, add JetBrains Mono
* Update to Emoji 15.1
* Add auto scroll when tooltip is out of screen
* Add shadow effect for tooltip
* Add developer mode config to Preferences GUI
* Hide Advanced Options and Dev when not in developer mode
* Adjust default font behavior to be locale-sensitive
* Tweak fallback font loading behavior (now it accepts font files)
* Move destroy() method so that the game won't crash in forced tick after the window closes
* Handle glowing sign where text color is black
* Update Traditional Chinese - notlin4
#### Modern Text Engine 1.19.2
* Add Untranslated Items integration
* Fix useComponentCache not working at all
* Fix force unicode font not working after game restart
* Fix line breaker SIOOBE for illegal string (this fixed crash with Better Statistics Screen)
#### Core Framework 3.10.0
* Move kotlin extension to a separate module (Core-KTX)
* Add Log class to avoid using log4j in submodules
* Implement blend mode filter for ShapeDrawable, ColorDrawable and other Drawable classes
* Fix incorrect drop-down position in RTL layout direction
* Fix MenuPopup overlap anchor (google-bug) (fix #199)
* Fix TextShaper context range for BiDi analysis
* Add LocaleSpan
* Add all 42 blend modes that used in Photoshop (currently no shader implementation)
* Update BlendMode and Color.blend()
* Change Bitmap.getSize() type to long
* Make Bitmap's color info mutable (for reinterpretation)
* Add path measurement implementation (PathMeasure class)
* Remove 2GB restriction on Bitmap creation, add more sanitizations
* Deprecate ImageStore, fix javadoc errors
* Update Bitmap with Arc3D
* Update Matrix and Path with Arc3D
* Fix Underline and Strikethrough offset
* Add "exclusive" East Asian family support (currently not used)
* Public Menu.setOptionalIconsVisible() method
* Change atlas coverage type to double
#### Core Framework - Kotlin Extension 3.10.0
* Add kotlin-flavored methods, update annotations
#### Markdown 3.10.0
* Suppress unchecked warning
#### Arc 3D Graphics Engine 3.10.0
* Add color filters and color matrix
* Add/update all blend modes and their raster implementations: PLUS, MINUS, DIFFERENCE, EXCLUSION, COLOR_DODGE, COLOR_BURN, HARD_LIGHT, SOFT_LIGHT, LINEAR_DODGE, LINEAR_BURN, VIVID_LIGHT, LINEAR_LIGHT, PIN_LIGHT, HARD_MIX and HSL blend modes (HUE, SATURATION, COLOR, LUMINOSITY)
* Rename shaderc package to compiler
* Add Image-derived and Shader-derived skeleton classes
* Add UNORM_PACK16 and UNORM_PACK32 encoding constant
* Public ColorType.channelFlags
* Add missing GRAY_ALPHA_88 for ColorType.encoding
* Add alpha type validation
* Make owner's reference to pixel map mutable
* Fix ColorSpace initializer
* Add Raster, remove heap version of Bitmap
* Add full path measurement implementation
* Add PixelUtils for pixel conversion
* Add PixelMap and PixelRef, remove Pixmap
* Add and optimize Path methods
* Add Path.bounds computation, optimize Path allocation
* Add Rect2fc and Rect2ic for read-only usage
* Inline Path.Ref usage count implementation
* Finish approximation of cubic strokes by quadratic splines
* Finish approximation of quadratic strokes by quadratic splines
* Add MathUtil.pin() method for capturing NaN values, replace some use of clamp()
* Add conic section to quadratic curves conversion
* Add several methods to reset the Path
* Finish RoundJoiner, fix Path reversePop
* Add Matrixc interface for read-only usage of Matrix
* Update and optimize PathStroker
* Optimize approximation of conic sections by quadratic splines
* Add PathConsumer
* Add Path tessellation for quadratic and cubic splines
* Add PathUtils and WangsFormula for subdivisions
* Add Path, add Path.Ref, add PathIterator
* Add Geometry class for finding inflection points, tangent, curvature, max curvature, cusp, solving quadratic equations, cubic equations, etc
* Add RefCounted interface
* Add Hardware transfer processor
* Optimize rectangle packer

### Modern UI 3.9.0.2
#### Forge Extension 1.19.2-43.1.2
* Fix validation errors
* Add font atlas compact
* Add Iris shaders integration
* Schedule GUI Scale value listener on next tick
* Better text config category
* Change typeface loading behavior
* Restore cursor position for Emoji shortcode substitution
* Rework font manager to implement core Emoji rendering
* Add GPU driver bug workarounds bootstrap properties
* Improve text field undo/redo
* Add undo/redo for EditBox
* Add GUI Scale to Preferences
* Add batch input commit
* Update translations
* Implement grapheme break for all text fields
* Implement break iter for vanilla EditBox
* Add music player
* Increase window size for OpenGL version test, fix game freeze on Linux
* Update font resources and licenses
* Add tooltip border width config
* Fix format error when setting color opacity
* Remove unused assets
* Completely switch to Arc3D and abandon GL*Compat
* Auto clean up less used GPU resources
* Always generate text config
* Use jar-in-jar for caffeine and flexmark
* Update synchronization, improve render performance
* Disable MSAA by default, 0.176x VRAM usage than before, and faster
* Update to latest Arc3D and ModernUI
#### Modern Text Engine 1.19.2
* Auto disable modern text shaders when Iris shaders are active
* Now follow vanilla's Force Unicode Font setting
* Fix compat with Curios
* Fix shadow offset Y for bitmap font
* Fix fist line rendering in sign edit GUI
* Add text layout command
* Remove text cache cleanup on parallel dispatch
* Dont draw text outline when 'modern text shaders in 3D' disabled
* Keep text shader preload consistent with vanilla UI shader preload
* Improve UniformScale text when GUI scale is less than 4
#### Core Framework 3.9.0
* Separate Arc 3D from core framework
* Optimize Matrix
* Optimize ImageStore
* Fix Image cleanup
* Add font atlas compact
* Fix emoji font color
* Add full Emoji font support to core framework
* Add Half (float16) type
* Move BinaryIO to Parcel
* Add ByteBuffer implementation for Parcelable
* Add commit batch input
* Fix per-cluster measure bug
* Fix track on rewind
* Rework on AudioSystem
* Move old ViewPager implementation to core framework
* Delay mipmaps regeneration for font atlas
* Fix and optimize SpanSet
* Decrease the default touchSlop value
* Completely remove GL*Compat classes, remove MSAA rendering
* Review bug on glfwWaitEventsTimeout
* Add CascadingMenuPopup presenter
* Remove IOException in readIntoNativeBuffer if >=2GB
* Fix compat with default render loop for OpenGL 3.3
* Optimize default bootstrap process
* Improve synchronization between UI thread and render thread
* Fragment now implements OnCreateContextMenuListener
* Fix saveLayer with alpha=0
* Add ContextMenuInfo
* Add ExpandableListView
* Fix ShapeDrawable line thickness
* Disable MSAA by default, and reduce the number of off-screen targets
* Remove the limit on the number of families in FontCollection
* Other small fixes and improvements
#### Arc 3D Graphics Engine 3.9.0
* Fix validation errors
* Add DriverBugWorkarounds
* Change to LinkedListMultimap
* Use HashMap for resource cache
* Better handling dirty OpenGL context states
* Add Blend constants
* Refactor Engine API
* Add Pixmap
* Fix GpuBufferPool
* Add SDF rectangle geometry processor
* Add NVIDIA driver bug workaround, when binding index buffer using DSA
* Add compat with OpenGL 3.3 upload pixels
* Add copyImage implementation, change Surface hierarchy
* Add Matrix.mapPoints and Matrix.getMin/MaxScale
* Add shear, map and I/O methods for Matrix
* Fix and optimize Matrix#invert
* Re-implement Matrix functions
* Fully implement ClipStack functions
* Other small fixes and improvements

### Modern UI 3.8.2.2
#### Forge Extension 1.19.2-43.1.2
* Change the crash with TipTheScales to warning
* Change step size for master volume multiplier option to 0.01
* Prevent Action Center from being opened when a screen with shouldCloseOnEsc=false is opened
* Request window attention when "Ding" is enabled
#### Modern Text Engine 1.19.2
* Only override Font's StringSplitter
#### Core Framework 3.8.2
* Add GridView
* Add GridLayout
* Add TableLayout
* Add UndoManager
* Add compatibility with LWJGL 3.2
#### Arc 3D Graphics Engine 3.8.2
* Fix compatibility with OpenGL 3.3