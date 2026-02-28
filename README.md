
## Mike's IDE extensions

[![IntelliJ IDEA Plugin](https://img.shields.io/jetbrains/plugin/v/12690-mike-s-idea-extensions?label=plugin&logo=intellij-idea)](https://plugins.jetbrains.com/plugin/12690-mike-s-idea-extensions/)
[![IntelliJ IDEA Plugin](https://img.shields.io/jetbrains/plugin/d/12690-mike-s-idea-extensions?logo=intellij-idea)](https://plugins.jetbrains.com/plugin/12690-mike-s-idea-extensions/)

<!-- start plugin.xml -->

<p>This plugin focuses on finding subtle bugs, 
performance pitfalls, and API misuse that standard inspections often miss in Java, Kotlin, and Android projects.</p>

<p>It's developed for <del>myself</del> developers who care about performance, memory usage, correctness, 
and want their IDE to actively point out non-obvious problems and better alternatives while they code.</p>

  <h3>UAST (Java + Kotlin) inspections</h3>
  <ul>
    <li><code>Atomic</code> can be replaced with <code>volatile</code></li>
    <li>Allocation should be cached (<code>Gson()</code>, <code>OkHttpClient()</code>)</li>
    <li><code>BigDecimal|BigInteger</code> instantiation can be replaced with constant <em>by <a href="https://github.com/stokito/">stokito</a></em></li>
    <li><code>(BigDecimal|BigInteger).compareTo(ZERO)</code> can be replaced with <code>signum()</code> <em>by <a href="https://github.com/stokito/">stokito</a></em></li>
  </ul>

  <h3>Kotlin inspections</h3>
  <ul>
    <li>Heavyweight property delegation</li>
    <li>Declaration name is Java keyword</li>
    <li>Inline function leaks anonymous declaration which will be inlined to the call-site if called from another module</li>
    <li>Anonymous function won't be inlined;
      function cannot be inlined if it is a receiver of an extension function</li>
    <li><del>Nullable argument to string concatenation</del> <i>should be re-implemented in more reliable way</i></li>
    <li>Boxed primitive array allocation (<code>arrayOf(1, 2, 3)</code>)</li>
  </ul>

  <h3>Android inspections</h3>
  <ul>
    <li><code>&lt;include layout="?themeAttribute"&gt;</code> requires Marshmallow</li>
    <li><code>&lt;drawable android:tint&gt;</code> requires Lollipop</li>
    <li><code>&lt;layer-list>&lt;item android:gravity&gt;</code> requires Marshmallow</li>
    <li><code>@TargetApi</code> should be replaced with <code>@RequiresApi</code></li>
    <li>Use of reflective <code>ObjectAnimator</code>/<code>PropertyValuesHolder</code></li>
    <li>Use of attributes like <code>android.R.attr.enabled</code> in context where state attributes expected, like <code>android.R.attr.state_enabled</code></li>
    <li><code>Color.parseColor(&lt;constexpr&gt;)</code> or <code>&lt;constexpr&gt;.toColorInt()</code> should be replaced with an integer literal</li>
    <li>Useless resource element
      <ul>
        <li>Drawables: single-item layer-lists, single stateless item selectors, insetless insets, empty shapes</li>
        <li>Vector drawables: empty paths and clip-paths, invisible paths, suboptimal paths, useless clip-paths and groups, attributes with no effect</li>
        <li>Animations and animators: empty and single-element sets</li>
        <li>Layouts: suspicious combination of <code>(layout_margin|padding)(Left|Top|Right|Bottom|Start|End|Horizontal|Vertical|*)</code></li>
      </ul>
    </li>
    <li>Android <code>TextUtils</code> methods should be replaced with Kotlin extensions</li>
    <li><code>setOnClickListener</code> doesn't work on <code>RecyclerView</code>, on <code>VideoView</code> before API 26</li>
    <li>Kotlin Android Extensions are deprecated</li>
    <li>Drawable subclass should override <code>getConstantState()</code></li>
    <li><code>Activity#onCreate(, PersistableBundle)</code> will highly likely not be called</li>
    <li><code>&lt;AnyScrollableView></code> should have an ID to save its scroll position</li>
    <li><code>Context.obtainStylesAttributes</code>|<code>Context.withStyledAttributes</code> need a sorted array</li>
  </ul>

  <h3>Regexp inspections</h3>
  <ul>
    <li>Ranges <code>[А-Я]</code>, <code>[а-я]</code> do not cover any of Cyrillic alphabets, some letters are out of range</li>
  </ul>

  <h3>Editor features</h3>
  <ul>
    <li>Inlay hints when upcasting to interface, e.g.<br/>putExtra(list<code>as Serializable</code>)</li>
    <li>Inlay hints when overriding an interface method, e.g.<br/>@Override <code>from Runnable</code>,<br/>override fun <code>Runnable.</code>run()</li>
    <li>Inlay hints for vararg array allocation: <br/>String.format("%d", <code>new[]{</code>1<code>}</code>);<br/>maxOf(1, <code>*[</code>2, 3, 4<code>]</code>)</li>
    <li>ARGB Color swatches in gutter, folding int literals to <code>#[AA]RRGGBB</code>, color picker, pasting CSS colors as int literals</li>
    <li>Live templates for SVG and Android Vector Drawable pathData</li>
    <li>Live templates for implementing <code>Property</code> for <code>ObjectAnimator</code></li>
  </ul>

<!-- end plugin.xml -->

  [Plugin page on JetBrains marketplace](https://plugins.jetbrains.com/plugin/12690-mike-s-idea-extensions) <br/>
  Alternatively, new versions are announced on and uploaded to my [Telegram channel](https://t.me/javanese_online)
