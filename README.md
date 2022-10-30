
## Mike's IDEA extensions

[![IntelliJ IDEA Plugin](https://img.shields.io/jetbrains/plugin/v/12690-mike-s-idea-extensions?label=plugin&logo=intellij-idea)](https://plugins.jetbrains.com/plugin/12690-mike-s-idea-extensions/)
[![IntelliJ IDEA Plugin](https://img.shields.io/jetbrains/plugin/d/12690-mike-s-idea-extensions?logo=intellij-idea)](https://plugins.jetbrains.com/plugin/12690-mike-s-idea-extensions/)

<!-- start plugin.xml -->

Code quality goodifier, RAM saver, performance watcher, mood upgrader, vector drawable optimizer, color previewer.

  <h3>UAST (Java + Kotlin) inspections</h3>
  <ul>
    <li>Atomic can be replaced with volatile</li>
    <li>Allocation should be cached (Enum.values(), new Gson(), ...)</li>
    <li>BigDecimal instantiation can be replaced with constant <em>by <a href="https://github.com/stokito/">stokito</a></em></li>
    <li>BigDecimal.compareTo(ZERO) can be replaced with signum() <em>by <a href="https://github.com/stokito/">stokito</a></em></li>
  </ul>

  <h3>Kotlin inspections</h3>
  <ul>
    <li>Heavyweight property delegation</li>
    <li>Declaration name is Java keyword</li>
    <li>Inline function leaks anonymous declaration which will be inlined to the call-site if called from another module</li>
    <li>Anonymous function won't be inlined;
      function cannot be inlined if it is a receiver of an extension function</li>
    <li>Nullable argument to string concatenation</li>
    <li>Boxed primitive array allocation</li>
  </ul>

  <h3>Android inspections</h3>
  <ul>
    <li><code>&lt;include layout="?themeAttribute"&gt;</code> requires Marshmallow</li>
    <li><code>&lt;drawable android:tint&gt;</code> requires Lollipop</li>
    <li><code>@TargetApi</code> should be replaced with <code>@RequiresApi</code></li>
    <li>Use of reflective <code>ObjectAnimator</code>/<code>PropertyValuesHolder</code></li>
    <li>Use of attributes like <code>android.R.attr.enabled</code> in context where state attributes expected, like <code>android.R.attr.state_enabled</code></li>
    <li><code>Color.parseColor(&lt;constant expression&gt;)</code> should be replaced with an integer literal</li>
    <li>Useless resource element
      <ul>
        <li>Drawables: single-item layer-lists, single stateless item selectors, insetless insets, empty shapes</li>
        <li>Vector drawables: empty paths and clip-paths, invisible paths, useless clip-paths and groups, attributes with no effect</li>
        <li>Animations and animators: empty and single-element sets</li>
        <li>Layouts: overridden attributes in (layout_margin|padding)(Left|Top|Right|Bottom|Start|End|Horizontal|Vertical|*)</li>
      </ul>
    </li>
    <li>Android utility methods should be replaced with Kotlin extensions</li>
    <li><code>setOnClickListener</code> doesn't work in <code>VideoView</code> until SDK26, in <code>RecyclerView</code>; Kotlin Android Extensions are deprecated</li>
    <li>Drawable subclass should override <code>getConstantState()</code></li>
  </ul>

  <h3>Editor tweaks</h3>
  <ul>
    <li>Inlay hints when upcasting to interface, e. g.<br/>putExtra(list<code>as Serializable</code>)</li>
    <li>Inlay hints when overriding an interface method, e. g.<br/>@Override <code>from Runnable</code>,<br/>override fun <code>Runnable.</code>run()</li>
    <li>ARGB Color swatches in gutter, folding int literals to <code>#[AA]RRGGBB</code> for Android</li>
    <li>Backing property folding for Kotlin</li>
    <li>Live templates for SVG and Android Vector Drawable pathData</li>
  </ul>

<!-- end plugin.xml -->

  [Plugin page on JetBrains marketplace](https://plugins.jetbrains.com/plugin/12690-mike-s-idea-extensions)
