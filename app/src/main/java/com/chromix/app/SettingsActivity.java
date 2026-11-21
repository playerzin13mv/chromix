package com.chromix.app;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Configurações do app. */
public class SettingsActivity extends BaseActivity {

    private static final int[] RATES = {0, 22050, 32000, 44100, 48000};
    private static final String[] RATE_LABELS = {"Original", "22050 Hz", "32000 Hz", "44100 Hz", "48000 Hz"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Pal.bg);
        root.setFitsSystemWindows(true);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(UI.dp(this, 8), UI.dp(this, 4), UI.dp(this, 8), UI.dp(this, 4));
        TextView back = UI.btn(this, "←", Pal.card2, Pal.text, new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        back.setTextSize(18);
        top.addView(back, UI.lp(UI.dp(this, 48), UI.dp(this, 40)));
        top.addView(UI.text(this, "Configurações", 18, Pal.text, true), UI.lpm(-2, -2, 12, 0, 0, 0, this));
        root.addView(top, UI.lp(-1, -2));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout cols = new LinearLayout(this);
        cols.setOrientation(LinearLayout.HORIZONTAL);
        cols.setPadding(UI.dp(this, 10), UI.dp(this, 4), UI.dp(this, 10), UI.dp(this, 12));
        LinearLayout c1 = new LinearLayout(this);
        c1.setOrientation(LinearLayout.VERTICAL);
        LinearLayout c2 = new LinearLayout(this);
        c2.setOrientation(LinearLayout.VERTICAL);
        cols.addView(c1, UI.lpm(0, -2, 0, 0, 5, 0, this));
        ((LinearLayout.LayoutParams) c1.getLayoutParams()).weight = 1f;
        cols.addView(c2, UI.lpm(0, -2, 5, 0, 0, 0, this));
        ((LinearLayout.LayoutParams) c2.getLayoutParams()).weight = 1f;
        sv.addView(cols, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        // ---------- coluna 1 ----------
        LinearLayout look = UI.card(this, "Aparência");
        look.addView(cycle("Tema", new String[]{"Escuro", "Claro", "Sistema"}, new int[]{0, 1, 2}, "theme", 0, new Runnable() {
            @Override public void run() { recreate(); }
        }));
        look.addView(cycle("Nomes das notas", new String[]{"C D E F G A B", "Dó Ré Mi Fá Sol Lá Si"}, new int[]{0, 1}, "noteStyle", 0, null));
        c1.addView(look, UI.lpm(-1, -2, 0, 0, 0, 10, this));

        LinearLayout kb = UI.card(this, "Piano de teste");
        kb.addView(cycle("Oitavas visíveis", new String[]{"2", "3", "4"}, new int[]{2, 3, 4}, "octaves", 2, null));
        kb.addView(UI.sw(this, "Vibrar ao tocar uma tecla", Prefs.haptic(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) { Prefs.putBool(SettingsActivity.this, "haptic", on); }
        }));
        c1.addView(kb, UI.lpm(-1, -2, 0, 0, 0, 10, this));

        LinearLayout au = UI.card(this, "Áudio");
        au.addView(cycle("Latência", new String[]{"Baixa (rápida)", "Normal", "Alta (mais estável)"}, new int[]{0, 1, 2}, "latency", 1, null));
        au.addView(new UI.SeekRow(this, "Volume geral do piano", 0, 100, Prefs.masterVol(this),
                new UI.Fmt() { @Override public String f(int v) { return v + "%"; } },
                new UI.OnVal() { @Override public void on(int v, boolean u) { Prefs.putInt(SettingsActivity.this, "masterVol", v); } }));
        TextView hint = UI.text(this, "Se o som estalar ou falhar, aumente a latência. A mudança vale na próxima vez que abrir um projeto.", 11, Pal.sub, false);
        au.addView(hint, UI.lpm(-1, -2, 0, 4, 0, 0, this));
        c1.addView(au, UI.lpm(-1, -2, 0, 0, 0, 10, this));

        // ---------- coluna 2 ----------
        LinearLayout ex = UI.card(this, "Padrões para novos projetos");
        ex.addView(cycle("Modo de exportação", new String[]{"Nota única", "Uma amostra por nota"}, new int[]{0, 1}, "defMode", 0, null));
        ex.addView(cycle("Taxa de amostragem", RATE_LABELS, RATES, "defRate", 0, null));
        ex.addView(range("Nota mais grave", true));
        ex.addView(range("Nota mais aguda", false));
        c2.addView(ex, UI.lpm(-1, -2, 0, 0, 0, 10, this));

        LinearLayout gen = UI.card(this, "Geral");
        gen.addView(UI.sw(this, "Manter a tela ligada", Prefs.keepOn(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) { Prefs.putBool(SettingsActivity.this, "keepOn", on); }
        }));
        final TextView authorBtn = UI.btn(this, authorText(), Pal.card2, Pal.text, null);
        authorBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { editAuthor(authorBtn); }
        });
        LinearLayout ar = new LinearLayout(this);
        ar.setOrientation(LinearLayout.HORIZONTAL);
        ar.setGravity(Gravity.CENTER_VERTICAL);
        ar.addView(UI.text(this, "Autor (gravado no .sf2)", 12, Pal.sub, false), new LinearLayout.LayoutParams(0, -2, 1f));
        ar.addView(authorBtn);
        gen.addView(ar, UI.lpm(-1, -2, 0, 4, 0, 0, this));
        c2.addView(gen, UI.lpm(-1, -2, 0, 0, 0, 10, this));

        LinearLayout data = UI.card(this, "Dados");
        TextView wipe = UI.btn(this, "Apagar todos os projetos", Pal.danger, 0xFFFFFFFF, new View.OnClickListener() {
            @Override public void onClick(View v) { confirmWipe(); }
        });
        data.addView(wipe, UI.lp(-1, -2));
        c2.addView(data, UI.lpm(-1, -2, 0, 0, 0, 10, this));

        LinearLayout about = UI.card(this, "Sobre");
        TextView t = UI.text(this, "Chromix 1.0\nCrie Chromatics de FNF a partir de qualquer som e exporte como SoundFont (.sf2).\n"
                + "Use o .sf2 em qualquer programa que aceite SoundFonts (FL Studio, LMMS, VSTs de SF2, etc.).", 12, Pal.sub, false);
        t.setLineSpacing(UI.dp(this, 2), 1f);
        about.addView(t);
        c2.addView(about, UI.lp(-1, -2));

        setContentView(root);
    }

    private View cycle(String title, final String[] labels, final int[] values, final String key, int def, final Runnable after) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UI.dp(this, 3), 0, UI.dp(this, 3));
        row.addView(UI.text(this, title, 12, Pal.sub, false), new LinearLayout.LayoutParams(0, -2, 1f));
        int cur = Prefs.getInt(this, key, def);
        int idx = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == cur) idx = i;
        final int[] state = {idx};
        final TextView b = UI.btn(this, labels[idx], Pal.card2, Pal.text, null);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                state[0] = (state[0] + 1) % values.length;
                Prefs.putInt(SettingsActivity.this, key, values[state[0]]);
                b.setText(labels[state[0]]);
                if (after != null) after.run();
            }
        });
        row.addView(b);
        return row;
    }

    private View range(String title, final boolean low) {
        final UI.Stepper[] holder = new UI.Stepper[1];
        holder[0] = new UI.Stepper(this, title, new UI.OnStep() {
            @Override public void on(int dir) {
                int lo = Prefs.defLo(SettingsActivity.this), hi = Prefs.defHi(SettingsActivity.this);
                if (low) lo = Math.max(0, Math.min(hi, lo + dir));
                else hi = Math.min(127, Math.max(lo, hi + dir));
                Prefs.putInt(SettingsActivity.this, "defLo", lo);
                Prefs.putInt(SettingsActivity.this, "defHi", hi);
                holder[0].set(Pal.noteName(low ? lo : hi, Prefs.noteStyle(SettingsActivity.this)));
            }
        });
        holder[0].set(Pal.noteName(low ? Prefs.defLo(this) : Prefs.defHi(this), Prefs.noteStyle(this)));
        holder[0].setPadding(0, UI.dp(this, 3), 0, UI.dp(this, 3));
        return holder[0];
    }

    private String authorText() {
        String a = Prefs.author(this);
        return a.isEmpty() ? "Definir" : a;
    }

    private void editAuthor(final TextView btn) {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText(Prefs.author(this));
        dlg().setTitle("Nome do autor").setView(et)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Prefs.putStr(SettingsActivity.this, "author", et.getText().toString().trim());
                        btn.setText(authorText());
                    }
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void confirmWipe() {
        dlg().setTitle("Apagar tudo?")
                .setMessage("Todos os projetos (áudios e configurações) serão apagados. Isso não pode ser desfeito.")
                .setPositiveButton("Apagar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        ProjectStore.deleteAll(SettingsActivity.this);
                        toast("Projetos apagados");
                    }
                })
                .setNegativeButton("Cancelar", null).show();
    }

    @Override
    public void finish() {
        super.finish();
        fade();
    }
}
