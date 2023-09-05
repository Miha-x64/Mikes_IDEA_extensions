
### 0.30
  <ul>
    <li>Fix <code>Color.parseColor("cyan")</code> mistakenly rendered yellow (copy-paste error, he-he)</li>
    <li>Report that <code>&lt;layer-list>&lt;item android:gravity></code> requires API 23</li>
    <li>Android Studio Hedgehog and Iguana compatibility (someone please tell Google that I'm tired with their zoo)</li>
  </ul>

### 0.29
  <ul>
    <li><code>BigDecimal|BigInteger</code> constant inspection: full number parsing (e.g. "0" = "0000.0000" = "0e10") and Java 9's <code>BigInteger.TWO</code> constant support</li>
    <li>Removed “Nullable argument to string concatenation” because of false-positives</li>
    <li>Stopped reporting <code>AnyEnum.values()</code> calls in non-static context, leave this to Kotlin team and their new <code>AnyEnum.entries</code> feature</li>
    <li>Reporting <code>new OkHttpClient()</code> in “Allocation should be cached” inspection (and not reporting <code>Gson.Builder()</code>, it could be configured somehow)</li>
    <li>Previewing even less opaque colors, >= 0x19 instead of > 0x27, useful for Ripple</li>
    <li>Don't showing useless interface hints for anonymous classes</li>
    <li>Reporting heavyweight property delegation for <code>var</code>s, not only <code>val</code>s</li>
    <li>Stop logging errors on <code>R.styleable</code> when trying to find un-sorted styled attributes</li>
    <li>Stop reporting empty layer-list on LayerList subclasses like Ripple</li>
    <li>Stop reporting absent <code>getConstantState()</code> for Java type variables</li>
  </ul>

### 0.28
  <ul>
    <li>Added live templates for implementing <code>Property</code> for <code>ObjectAnimator</code></li>
    <li>Reporting that <code>Activity#onCreate(, PersistableBundle)</code> will highly likely not be called</li>
    <li>Reporting that <code>&lt;AnyScrollableView></code> should have an ID to save its scroll position</li>
  </ul>

### 0.27
  <ul>
    <li>Inspecting more drawable types</li>
    <li>Reporting Drawable classes without getConstantState()</li>
    <li>Eliminated some false-positives on useless fillColor/Alpha, strokeWidth/Color/Alpha</li>
    <li>Vector underdraw detection ((sub-)path adds nothing to the image)</li>
    <li>Suggest merging sibling vector path and clip-path pathData</li>
    <li>Unhidden interface hints from IDE settings</li>
    <li>Added color picker</li>
  </ul>


### 0.26
  <ul>
    <li>Moved from Trove to fastutil, fixes crash on new IDE builds</li>
    <li>Reworked upcast hints, fixes various bugs</li>
    <li>Not showing override hints for single-interface anonymous classes</li>
  </ul>


### 0.25

  <ul>
    <li>Fixed trimming tails in <code>pathData</code> for floats with exponent</li>
    <li>Fixed crash when parsing <code>pathData</code> which is incomplete or starts with spaces/linebreaks/etc</li>
    <li>Fixed handling overdraw of clipped paths</li>
    <li>Reporting that <code>RecyclerView.setOnClickListener</code> is unsupported; stopped mistakenly reporting <code>VideoView.setOnTouchListener</code></li>
    <li>Quickfix for removing empty vector sub-path and fixing the following relative moveTo command</li>
    <li>Quickfix for splitting <code>&lt;path></code> tag</li>
    <li>Live templates for SVG and Android Vector Drawable pathData</li>
    <li>Suggest merging (layout_margin|padding)(Left|Top|Right|Bottom|Start|End) pairs into (Horizontal|Vertical) even for SDK&lt;22 (without highlight though)</li>
  </ul>

### 0.24
  <ul>
    <li>Showing a quickfix for inlining layer-list only if on-the-fly to force conscious choice</li>
    <li>UselessResElement: merging intersecting sub-path into single to support “donut hole” effect</li>
  </ul>


### 0.23
  <ul>
    <li>Added inspection about programmatic <code>GradientDrawable</code> (a.k.a. <code>&lt;shape></code>) attribute application order</li>
    <li>Support comparison operators when reporting `Big(Integer|Decimal).compareTo(ZERO)`</li>
    <li>Fixed binary incompatibility with Android Studio Chipmunk | 2021.2.1 Patch 1</li>
    <li>Reporting unsorted arrays passed to <code>obtainStyledAttributes</code></li>
    <li>Relaxed warning on “useless” layer-lists: they may actually behave differently than insets</li>
    <li>Fallen back to standard introduceConstantHandler when suggesting fix for uncached allocations inspection due to incompatibility with IU-222.2270.31 (2022.2 (eap))
(may not work for Kotlin)</li>
    <li>When pasting colors in Kotlin, adding <code>.toInt()</code> only if necessary</li>
    <li>When detecting vector <code>&lt;group></code> usefulness, looking at successors, not siblings</li>
  </ul>


### 0.22
  <ul>
    <li>Fix reflective property replacement search when name is capitalized</li>
    <li>More compact backing property folding</li>
    <li>Suggest merging (layout_margin|padding)(Left|Top|Right|Bottom|Start|End) pairs into (Horizontal|Vertical)</li>
    <li>Fix clip-path application order in UselessResourceElement</li>
    <li>Split vector paths and report useless sub-paths separately</li>
    <li>Skip Java keywords in Kotlin declaration if its container is not exposed</li>
  </ul>

### 0.21
  <ul>
    <li>Fix interface upcast hints after last fix</li>
    <li>Fix vector path optimizations: don't let numbers stick together when optimizing -0.01 to 0</li>
    <li>Reporting Kotlin Android Extensions imports as deprecated</li>
    <li>Reporting overridden attributes in (layout_margin|padding)(|Left|Top|Right|Bottom|Start|End|Horizontal|Vertical)</li>
    <li>Added “Boxed primitive array allocation” inspection</li>
  </ul>

### 0.20
  <ul>
    <li>Reworked Reflective Property Animation inspection with various fixes</li>
    <li>Vector drawable analysis: better accuracy when detecting emptiness, fix crash on bad paths, add resource resolution, fix trimming accuracy</li>
    <li>Fix crash due to absent libcore</li>
    <li>Fix interface upcast inlay hints for lambda argument outside of parentheses</li>
  </ul>


### 0.19
  <ul>
    <li>Detecting empty and single-item animation sets</li>
    <li>Fixed Kotlin upcast hints for extension functions and named arguments</li>
    <li>Added backing property folding for Kotlin</li>
    <li>Added “Android utility methods should be replaced with Kotlin extension” inspection</li>
    <li>Improved vector drawable inspection: reporting paths fully overdrawn by other paths</li>
  </ul>


### 0.18
  <ul>
    <li>Numerous quickfix quality fixes: shorten references, reformat etc</li>
    <li>“Function won't be inlined” inspection made disabled by default</li>
    <li>“This allocation should be cached” inspection provides “Introduce constant” quickfix for Kotlin</li>
    <li>Reporting `VideoView.setOnClickListener` which has no sense before Android API 26</li>
  </ul>

### 0.17
  <ul>
    <li>Marked ConstantParseColor, ReflectPropAnimInspection, TargetApiInspection as cleanup tools</li>
    <li>Added uselessDrawableElement inspection for Android</li>
    <li>More accurate and less annoying UncachedAlloc inspection</li>
  </ul>

### 0.16
  <ul>
    <li>Implemented Kotlin upcast hints. Reworked interface inlay hints so their settings reside with their colleagues</li>
    <li>Transforming colors from CSS (#RRGGBB, #RRGGBBAA, rgb[a](r, g, b[, a])) to 0xAARRGGBB on paste</li>
    <li>“Kotlin identifier is a Java keyword”: detecting keywords in a package directive</li>
    <li>Android: reporting use of <code>container</code> parameter in <code>AnyDialogFragment.onCreateView</code> which is always null</li>
    <li>BigDecimalConstant, BigDecimalSignum: BigInteger and Kotlin support, inspection description</li>
  </ul>


### 0.15
  <ul>
    <li>Fixed crashes in ConcatNullable and inline/noinline function inspections</li>
    <li>Override hints: showing FunctionN as <code>(…) -> …</code> (without actual type arguments, LOL)</li>
    <li>More sensitive main interface detection heuristic for hints</li>
  </ul>

### 0.14

  <ul>
    <li>Fixed WrongStateAttr for negative states</li>
    <li>Gutter colors: improved performance and preview accuracy, added preview for <code>@ColorInt</code> constant references</li>
    <li>Added folding of int literals which suspected to be colors</li>
    <li>Removed XML-related inspections superseded by Android Lint: <code>&lt;bitmap tintMode</code>, “<code>&lt;view class="@resource or ?themeAttribute"&gt;</code> is not supported”</li>
    <li>Improved accuracy of “Nullable argument to String concatenation”, less false-positives</li>
    <li>Added settings to disable upcast or override hints</li>
  </ul>

### 0.13

  <ul>
    <li>Fixed NoSuchMethodError in ReflectPropAnimException</li>
    <li>Added Android inspection: state attribute expected</li>
    <li>Added Android inspection: <code>parseColor(with constant string)</code></li>
    <li>Added gutter icon: ColorInt preview</li>
  </ul>

### 0.12

  <ul>
    <li>Fixed crash in TargetApiInspection</li>
  </ul>

### 0.11
  <ul>
    <li>Added Kotlin inspection: “Nullable argument to string concatenation”</li>
    <li>Stopped reporting “identifier is Java keyword” for functions with reified type parameters</li>
  </ul>

### 0.10

  <ul>
    <li>Added BadCyrillicRegexp inspection</li>
    <li>Shut up false-positive KtInlineFunctionLeaksAnonymousDeclaration</li>
    <li>Less severity of ktNoinlineFunc for :: which are gonna be optimized</li>
    <li>Added Android inspection: <code>&lt;view class="@resource or ?themeAttribute"&gt;</code> is not supported</li>
  </ul>

### 0.9

  <ul>
    <li>Kotlin identifier is Java keyword: ignoring props with normal getter-setter, looking into <code>@JvmName</code> of getter-setter</li>
  </ul>

### 0.8

  <ul>
    <li>added minSdk check for <code>layout=?attr inspection</code></li>
    <li>Added inspection for <code>&lt;bitmap tint=</code></li>
    <li>fixed noinline function inspection to ignore return type</li>
    <li>added quickfix to UncachedAlloc inspection</li>
    <li>Upgraded main interface inference heuristic for override hints</li>
    <li>migrate off deprecated API in EditorCustomElementRenderer <em>by <a href="https://github.com/JB-Dmitry">Dmitry Batrak</a></em></li>
    <li>property animation quickfix for Kotlin</li>

  </ul>

### 0.7

  <ul>
    <li>Less noise from KtNoinlineFuncInspection and UpcastHints</li>
    <li>Added Java+Kotlin hints for interface method overrides</li>
    <li>uncachedEnumValues inspection becomes uncachedAlloc and also detects <code>new Gson()</code></li>
    <li><code>@TargetApi</code> quickfix now adds imports in Java</li>
    <li>Caught up property delegation inspection with Kotlin 1.3.60</li>
  </ul>

### 0.6

  <ul>
    <li>Added android inspection: Use of reflective ObjectAnimator/PropertyValuesHolder</li>
    <li>Upcast hints made less noisy</li>
    <li>Enum.values() without caching inspection supported for Kotlin files</li>
  </ul>

### 0.5

  <ul>
    <li>Added Android inspection: <code>@TargetApi</code> should be replaced with <code>@RequiresApi</code></li>
    <li>Added Java hints for upcast to interface</li>
  </ul>

### 0.4

  <ul>
    <li>Added Java inspection: BigDecimal instantiation can be replaced with constant <em>by <a href="https://github.com/stokito/">stokito</a></em></li>
    <li>Added Java inspection: <code>BigDecimal.compareTo(ZERO)</code> can be replaced with signum() <em>by <a href="https://github.com/stokito/">stokito</a></em></li>
    <li>Android plugin dependency made optional</li>
  </ul>

### 0.3

  <ul>
    <li>Fixed NPE in “Atomic as volatile” inspection</li>
  </ul>

### 0.2

  <ul>
    <li>“Kotlin declaration name is Java keyword” now inspects only declarations, also checks whether they are public to Java</li>
    <li>“Kotlin identifier is Java keyword”: added <code>@JvmName</code> support</li>
  </ul>

### 0.1

  <ul>
    <li>Added UAST inspection: Atomic can be replaced with volatile</li>
    <li>Added UAST inspection: Calling Enum values() without caching</li>
    <li>Added Kotlin inspection: Property delegation</li>
    <li>Added Kotlin inspection: Declaration name is Java keyword</li>
    <li>Added Kotlin inspection: Inline function leaks anonymous declaration</li>
    <li>Added Kotlin inspection: Function won't be inlined;
      noinline callable references are a bit more expensive than noinline lambdas;
      function cannot be inlined if it is a receiver of an extension function</li>
    <li>Added Android inspection: <code>&lt;include layout="?themeAttribute"&gt;</code></li>
  </ul>
