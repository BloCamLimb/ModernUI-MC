### Modern UI 3.9.0.1
#### Forge Extension 1.18.2-40.1.73
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
#### Modern Text Engine 1.18.2
* Auto disable modern text shaders when Iris shaders are active
* Now follow vanilla's Force Unicode Font setting
* Fix compat with Curios
* Fix shadow offset Y for bitmap font
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

### Modern UI 3.8.2.1
#### Forge Extension 1.18.2-40.1.73
* Change step size for master volume multiplier option to 0.01
* Prevent Action Center from being opened when a screen with shouldCloseOnEsc=false is opened
* Request window attention when "Ding" is enabled
#### Modern Text Engine 1.18.2
* Only override Font's StringSplitter
#### Core Framework 3.8.2
* Add GridView
* Add GridLayout
* Add TableLayout
* Add UndoManager
* Add compatibility with LWJGL 3.2
#### Arc 3D Graphics Engine 3.8.2
* Fix compatibility with OpenGL 3.3
