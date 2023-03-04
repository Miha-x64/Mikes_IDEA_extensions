package net.aquadc.mike.plugin.test;

import android.app.Activity;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.Nullable;
import kotlin.Function;
import kotlin.jvm.functions.Function1;

import java.io.Serializable;
import java.util.*;

@SuppressWarnings({"WeakerAccess", "SameParameterValue", "unused", "rawtypes"})
public class Interfaces implements Cloneable {

    private void a() {
        a(1, 1, 1, Collections.<Number>emptyList(), null, this);
//        InterfacesKt.a(1, 1, Collections.<Number>emptyList(), null, this);
        collections(
                new ArrayList(),
                new ArrayList(),
                new ArrayList(),
                new ArrayList(),
                new ArrayList(),
                new ArrayList(),
                new ArrayList()
        );
        str("", "", "", "");
        consumeSerializable(new ArrayList<Void>()); // don't show, it's obvious from method name
        Toast.makeText(null, null, Toast.LENGTH_LONG).show();
    }

    static void collections(ArrayList al, Iterable it, Collection co, List l, RandomAccess a, Cloneable c, Serializable s) {

    }

    static void consumeSerializable(Serializable s) {}

    static void a(Serializable a, Comparable b, int c, List<? extends Number> d, Function1<?, ?> e, Cloneable f) {

    }

    static void str(String q, Serializable w, Comparable<String> e, CharSequence r) {}

    static final class SomeActivity extends Activity implements View.OnClickListener {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            findViewById(0).setOnClickListener(this);
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
            super.onCreate(savedInstanceState, persistentState);
        }

        @Override
        public void onClick(View view) {

        }
    }

}
