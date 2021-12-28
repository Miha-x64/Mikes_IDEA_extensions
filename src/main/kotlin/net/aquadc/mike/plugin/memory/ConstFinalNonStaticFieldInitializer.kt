

// TODO
// When a final field is initialized with a constant expression, its value gets inlined to the use site.
// If the value is a deserialization fallback, move assignment to the constructor to avoid inlining.
// If you need a constant, make it static to avoid wasting memory.
// The only valid case for such a construct is when it is accessed **only** with reflection, LOL.

