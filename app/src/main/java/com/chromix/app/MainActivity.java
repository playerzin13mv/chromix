package com.chromix.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Tela inicial: lista de projetos. */
public class MainActivity extends BaseActivity {

    private LinearLayout list;
    private TextView count;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        build();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Pal.bg);
        root.setFitsSystemWindows(true);

        // ----- painel esquerdo -----
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER);
        left.setPadding(UI.dp(this, 20), UI.dp(this, 12), UI.dp(this, 20), UI.dp(this, 12));

        LogoView logo = new LogoView(this);
        left.addView(logo, UI.lp(UI.dp(this, 92), UI.dp(this, 92)));

        TextView name = UI.text(this, "Chromix", 30, Pal.text, true);
        name.setGravity(Gravity.CENTER);
        left.addView(name, UI.lpm(-2, -2, 0, 10, 0, 0, this));

        TextView tag = UI.text(this, "Crie Chromatics de FNF\ne exporte em .sf2", 12, Pal.sub, false);
        tag.setGravity(Gravity.CENTER);
        left.addView(tag, UI.lpm(-2, -2, 0, 2, 0, 14, this));

        TextView novo = UI.btn(this, "+  Novo projeto", Pal.accent, Pal.onAccent, new View.OnClickListener() {
            @Override public void onClick(View v) { novoProjeto(); }
        });
        novo.setTextSize(14);
        novo.setPadding(UI.dp(this, 16), UI.dp(this, 11), UI.dp(this, 16), UI.dp(this, 11));
        left.addView(novo, UI.lp(-1, -2));

        TextView cfg = UI.btn(this, "Configurações", Pal.card2, Pal.text, new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                fade();
            }
        });
        left.addView(cfg, UI.lpm(-1, -2, 0, 8, 0, 0, this));

        TextView ver = UI.text(this, "v1.0", 11, Pal.sub, false);
        left.addView(ver, UI.lpm(-2, -2, 0, 12, 0, 0, this));

        root.addView(left, new LinearLayout.LayoutParams(UI.dp(this, 250), -1));

        // ----- lista -----
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(UI.dp(this, 6), UI.dp(this, 14), UI.dp(this, 18), UI.dp(this, 10));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(UI.text(this, "Seus projetos", 20, Pal.text, true), new LinearLayout.LayoutParams(0, -2, 1f));
        count = UI.text(this, "", 12, Pal.sub, false);
        head.addView(count);
        right.addView(head, UI.lpm(-1, -2, 0, 0, 0, 8, this));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list, new ScrollView.LayoutParams(-1, -2));
        right.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.addView(right, new LinearLayout.LayoutParams(0, -1, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        list.removeAllViews();
        List<Project> ps = ProjectStore.list(this);
        count.setText(ps.size() == 1 ? "1 projeto" : ps.size() + " projetos");
        if (ps.isEmpty()) {
            LinearLayout empty = UI.card(this, "Como começar");
            TextView t = UI.text(this,
                    "1. Toque em \"Novo projeto\"\n"
                            + "2. Importe um áudio (ex.: o \"oh\" de um personagem) ou grave sua voz\n"
                            + "3. Defina a nota raiz (ou use \"Detectar nota\") e teste no piano\n"
                            + "4. Ajuste o loop e o envelope\n"
                            + "5. Toque em \"Exportar .sf2\" e use no seu FNF!",
                    13, Pal.text, false);
            t.setLineSpacing(UI.dp(this, 3), 1f);
            empty.addView(t);
            list.addView(empty, UI.lp(-1, -2));
            return;
        }
        int idx = 0;
        for (final Project p : ps) {
            View card = card(p);
            card.setAlpha(0f);
            card.setTranslationY(UI.dp(this, 14));
            card.animate().alpha(1f).translationY(0).setStartDelay(idx * 45L).setDuration(260).start();
            list.addView(card, UI.lpm(-1, -2, 0, 0, 0, 8, this));
            idx++;
        }
    }

    private View card(final Project p) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(UI.dp(this, 14), UI.dp(this, 12), UI.dp(this, 8), UI.dp(this, 12));
        UI.setBg(c, Pal.card, 14);
        c.setClickable(true);

        View dot = new View(this);
        dot.setBackground(UI.rr(this, Pal.RAINBOW[p.slots[0].rootNote % 12], 8));
        c.addView(dot, UI.lpm(UI.dp(this, 8), UI.dp(this, 40), 0, 0, 12, 0, this));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(UI.text(this, p.name, 16, Pal.text, true));
        Slot s0 = p.slots[0];
        String mode = p.exportMode == 0 ? "nota única" : "uma por nota";
        String sub;
        if (p.vowelMode) {
            String v = p.vowelSummary();
            sub = "Vogais: " + (v.isEmpty() ? "nenhuma ainda" : v) + "  ·  " + mode;
        } else {
            String dur = s0.frames > 0
                    ? String.format(java.util.Locale.US, "%.1f s", (s0.trimEnd - s0.trimStart) / (double) s0.sampleRate)
                    : "sem áudio";
            sub = "Nota raiz " + Pal.noteName(s0.rootNote, Prefs.noteStyle(this)) + "  ·  " + dur
                    + "  ·  " + mode + (s0.loop ? "  ·  loop" : "");
        }
        info.addView(UI.text(this, sub, 12, Pal.sub, false));
        String when = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(p.modified));
        info.addView(UI.text(this, "Editado em " + when, 11, Pal.sub, false));
        c.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView more = UI.btn(this, "⋯", Pal.card2, Pal.text, new View.OnClickListener() {
            @Override public void onClick(View v) { menu(p); }
        });
        more.setTextSize(18);
        c.addView(more, UI.lp(UI.dp(this, 44), UI.dp(this, 40)));

        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { abrir(p); }
        });
        c.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { menu(p); return true; }
        });
        return c;
    }

    private void abrir(Project p) {
        Intent i = new Intent(this, EditorActivity.class);
        i.putExtra("id", p.id);
        startActivity(i);
        fade();
    }

    private void novoProjeto() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setText("Chromatic " + (ProjectStore.list(this).size() + 1));
        et.setSelectAllOnFocus(true);
        dlg().setTitle("Nome do projeto").setView(et)
                .setPositiveButton("Criar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String n = et.getText().toString().trim();
                        if (n.isEmpty()) n = "Chromatic";
                        abrir(ProjectStore.create(MainActivity.this, n));
                    }
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void menu(final Project p) {
        dlg().setTitle(p.name)
                .setItems(new String[]{"Abrir", "Renomear", "Duplicar", "Excluir"}, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) abrir(p);
                        else if (which == 1) renomear(p);
                        else if (which == 2) { ProjectStore.duplicate(MainActivity.this, p); refresh(); }
                        else excluir(p);
                    }
                }).show();
    }

    private void renomear(final Project p) {
        final EditText et = new EditText(this);
        et.setText(p.name);
        et.setSelectAllOnFocus(true);
        dlg().setTitle("Renomear").setView(et)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String n = et.getText().toString().trim();
                        if (!n.isEmpty()) {
                            p.name = n;
                            try { ProjectStore.save(MainActivity.this, p); } catch (Exception ignored) { }
                            refresh();
                        }
                    }
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void excluir(final Project p) {
        AlertDialog.Builder b = dlg().setTitle("Excluir \"" + p.name + "\"?")
                .setMessage("O áudio e as configurações desse projeto serão apagados. Os .sf2 já exportados não são afetados.")
                .setPositiveButton("Excluir", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        ProjectStore.delete(MainActivity.this, p);
                        refresh();
                    }
                })
                .setNegativeButton("Cancelar", null);
        b.show();
    }
}
