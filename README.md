
## Mike's IDEA extensions

Some great inspections, quickfixes, tools. Well, only inspections at the moment.

  <h3>Java inspections</h3>
  <ul>
    <li>BigDecimal instantiation can be replaced with constant <em>by <a href="http://github.com/stokito/">stokito</a></em></li>
    <li>BigDecimal.compareTo(ZERO) can be replaced with signum() <em>by <a href="http://github.com/stokito/">stokito</a></em></li>
  </ul>

  <h3>UAST (Java + Kotlin) inspections</h3>
  <ul>
    <li>Atomic can be replaced with volatile</li>
    <li>Calling Enum values() without caching</li>
  </ul>

  <h3>Kotlin inspections</h3>
  <ul>
    <li>Property delegation</li>
    <li>Declaration name is Java keyword</li>
    <li>Inline function leaks anonymous declaration</li>
    <li>Function won't be inlined;
      noinline callable references are a bit more expensive than noinline lambdas;
      function cannot be inlined if it is a receiver of an extension function</li>
  </ul>

  <h3>Android inspections</h3>
  <ul>
    <li>&lt;include layout="?themeAttribute"&gt;</li>
    <li><code>@TargetApi</code> should be replaced with <code>@RequiresApi</code></li>
    <li>Use of reflective ObjectAnimator/PropertyValuesHolder</li>
  </ul>

  <h3>Java tooltips</h3>
  <ul>
    <li>Upcast to interface, e. g.<br/>putExtra(list<code> as Serializable</code>)</li>
    <li>Method override from superclass, e. g.<br/>@Override <code>from Runnable</code></li>
  </ul>

  [Plugin page on JetBrains marketplace](https://plugins.jetbrains.com/plugin/12690-mike-s-idea-extensions)
