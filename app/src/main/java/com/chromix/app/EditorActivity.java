package com.chromix.app;

import android.Manifest;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Editor de um Chromatic: onda, afinação, loop, envelope, piano de teste e exportação. */
public class EditorActivity extends BaseActivity implements PianoView.Listener {

    private static final int RQ_IMPORT = 1, RQ_EXPORT = 2, RQ_MIC = 10;
    private static final int[] RATES = {0, 22050, 32000, 44100, 48000};

    private Project p;
    private Slot cs;                       // vogal (slot) em edição
    private int cur = 0;
    private float[] audio;                 // áudio do slot em edição
    private final float[][] audios = new float[5][];
    private final boolean[] dirty = new boolean[5];
    private boolean solo = false;          // piano toca só a vogal selecionada
    private final float[] pkVal = new float[5];
    private final int[] pkTs = new int[5], pkTe = new int[5];
    private final float[][] pkRef = new float[5][];
    private Synth synth;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean loading = false;

    private WaveformView wave;
    private PianoView piano;
    private TextView title, status, noteLbl, sizeInfo, octLabel;
    private TextView chipTrim, chipLoop, btnRec, btnScale, chipSingle, chipMulti;
    private TextView chipVowel, chipSolo, tuneTitle;
    private final TextView[] vchips = new TextView[5];
    private LinearLayout vowelRow;
    private UI.Stepper rootStep, rateStep, loStep, hiStep;
    private UI.SeekRow fineRow, atkRow, relRow, volRow;
    private Switch loopSw, normSw;

    private AudioIO.Recorder rec;
    private long recStart;
    private boolean scaleRunning = false;
    private final List<Runnable> scaleTasks = new ArrayList<>();

    private final Runnable saveTask = new Runnable() {
        @Override public void run() { saveNow(); }
    };

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String id = getIntent().getStringExtra("id");
        p = id == null ? null : ProjectStore.load(this, id);
        if (p == null) { finish(); return; }
        for (int i = 0; i < 5; i++) {
            audios[i] = ProjectStore.loadAudio(this, p, i);
            p.slots[i].frames = audios[i] == null ? 0 : audios[i].length;
        }
        cur = 0;
        cs = p.slots[0];
        audio = audios[0];
        p.sanitize();
        synth = new Synth(this);
        build();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (synth != null) { synth.start(); updateSynth(); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (p == null) return;
        stopScale();
        if (rec != null) finishRecording();
        if (synth != null) synth.stop();
        h.removeCallbacks(saveTask);
        saveNow();
    }

    @Override
    public void finish() {
        super.finish();
        fade();
    }

    // ------------------------------------------------------------------ interface

    private TextView chip(String s, View.OnClickListener l) {
        TextView t = UI.btn(this, s, Pal.card2, Pal.text, l);
        t.setTextSize(12);
        t.setPadding(UI.dp(this, 11), UI.dp(this, 6), UI.dp(this, 11), UI.dp(this, 6));
        return t;
    }

    private void styleChip(TextView t, boolean on) {
        UI.restyle(t, on ? Pal.accent : Pal.card2, on ? Pal.onAccent : Pal.text);
    }

    private void build() {
        final int d8 = UI.dp(this, 8);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Pal.bg);
        root.setFitsSystemWindows(true);

        // ---- barra superior ----
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(d8, UI.dp(this, 4), d8, UI.dp(this, 4));
        TextView back = UI.btn(this, "←", Pal.card2, Pal.text, new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        back.setTextSize(18);
        top.addView(back, UI.lp(UI.dp(this, 46), UI.dp(this, 38)));

        title = UI.text(this, p.name, 17, Pal.text, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setClickable(true);
        title.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { rename(); }
        });
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        ((LinearLayout.LayoutParams) title.getLayoutParams()).setMargins(UI.dp(this, 12), 0, UI.dp(this, 8), 0);

        status = UI.text(this, "salvo", 11, Pal.sub, false);
        top.addView(status, UI.lpm(-2, -2, 0, 0, 10, 0, this));

        TextView export = UI.btn(this, "Exportar .sf2", Pal.accent2, 0xFF04241B, new View.OnClickListener() {
            @Override public void onClick(View v) { exportClick(); }
        });
        top.addView(export, UI.lp(-2, UI.dp(this, 38)));
        root.addView(top, UI.lp(-1, -2));

        // ---- meio ----
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.HORIZONTAL);
        mid.setPadding(d8, 0, d8, 0);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        wave = new WaveformView(this);
        wave.setListener(new WaveformView.Listener() {
            @Override public void onMarkers(boolean trimChanged) {
                cs.trimStart = wave.getTrimStart();
                cs.trimEnd = wave.getTrimEnd();
                cs.loopStart = wave.getLoopStart();
                cs.loopEnd = wave.getLoopEnd();
                changed(false);
            }
        });
        HorizontalScrollView vscroll = new HorizontalScrollView(this);
        vscroll.setHorizontalScrollBarEnabled(false);
        LinearLayout vrow = new LinearLayout(this);
        vrow.setOrientation(LinearLayout.HORIZONTAL);
        vrow.setGravity(Gravity.CENTER_VERTICAL);
        chipVowel = chip("Modo vogais", new View.OnClickListener() {
            @Override public void onClick(View v) { setVowelMode(!p.vowelMode); }
        });
        vrow.addView(chipVowel, UI.lpm(-2, -2, 0, 0, 10, 0, this));
        vowelRow = new LinearLayout(this);
        vowelRow.setOrientation(LinearLayout.HORIZONTAL);
        vowelRow.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            vchips[i] = chip(Project.VOWELS[i], new View.OnClickListener() {
                @Override public void onClick(View v) { selectSlot(idx); }
            });
            vowelRow.addView(vchips[i], UI.lpm(-2, -2, 0, 0, 6, 0, this));
        }
        chipSolo = chip("Só esta vogal", new View.OnClickListener() {
            @Override public void onClick(View v) {
                solo = !solo;
                styleChip(chipSolo, solo);
                updateSynth();
            }
        });
        vowelRow.addView(chipSolo, UI.lpm(-2, -2, 8, 0, 0, 0, this));
        vrow.addView(vowelRow);
        vscroll.addView(vrow);
        left.addView(vscroll, UI.lpm(-1, -2, 0, 0, 0, 6, this));
        left.addView(wave, new LinearLayout.LayoutParams(-1, 0, 1f));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chipTrim = chip("Cortar", new View.OnClickListener() {
            @Override public void onClick(View v) { setMode(WaveformView.MODE_TRIM); }
        });
        chipLoop = chip("Loop", new View.OnClickListener() {
            @Override public void onClick(View v) { setMode(WaveformView.MODE_LOOP); }
        });
        chips.addView(chipTrim);
        chips.addView(chipLoop, UI.lpm(-2, -2, 6, 0, 14, 0, this));
        chips.addView(chip("Importar áudio", new View.OnClickListener() {
            @Override public void onClick(View v) { doImport(); }
        }), UI.lpm(-2, -2, 0, 0, 6, 0, this));
        btnRec = chip("Gravar", new View.OnClickListener() {
            @Override public void onClick(View v) { toggleRecord(); }
        });
        chips.addView(btnRec, UI.lpm(-2, -2, 0, 0, 6, 0, this));
        chips.addView(chip("Voz de exemplo", new View.OnClickListener() {
            @Override public void onClick(View v) { useDemo(); }
        }), UI.lpm(-2, -2, 0, 0, 6, 0, this));
        chips.addView(chip("Detectar nota", new View.OnClickListener() {
            @Override public void onClick(View v) { detectPitch(false); }
        }), UI.lpm(-2, -2, 0, 0, 6, 0, this));
        chips.addView(chip("Suavizar loop", new View.OnClickListener() {
            @Override public void onClick(View v) { smoothLoop(); }
        }));
        hs.addView(chips);
        left.addView(hs, UI.lpm(-1, -2, 0, 6, 0, 0, this));
        mid.addView(left, UI.lpm(0, -1, 0, 0, 8, 0, this));
        ((LinearLayout.LayoutParams) left.getLayoutParams()).weight = 5f;

        // ---- painel de controles ----
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));

        LinearLayout tune = UI.card(this, "Afinação");
        tuneTitle = (TextView) tune.getChildAt(0);
        rootStep = new UI.Stepper(this, "Nota raiz", new UI.OnStep() {
            @Override public void on(int dir) {
                cs.rootNote = Math.max(0, Math.min(127, cs.rootNote + dir));
                changed(false);
                refreshUi();
            }
        });
        tune.addView(rootStep);
        fineRow = new UI.SeekRow(this, "Afinação fina da amostra", -50, 50, 0,
                new UI.Fmt() { @Override public String f(int v) { return (v > 0 ? "+" : "") + v + " cents"; } },
                new UI.OnVal() {
                    @Override public void on(int v, boolean user) {
                        if (loading || !user) return;
                        cs.fineCents = v;
                        changed(false);
                    }
                });
        tune.addView(fineRow, UI.lpm(-1, -2, 0, 4, 0, 0, this));
        col.addView(tune, UI.lpm(-1, -2, 0, 0, 0, 8, this));

        LinearLayout env = UI.card(this, "Loop e envelope");
        loopSw = UI.sw(this, "Repetir (loop) enquanto a tecla está pressionada", false, new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                if (loading) return;
                cs.loop = on;
                if (on) p.sanitize();
                syncWave();
                changed(false);
            }
        });
        env.addView(loopSw);
        atkRow = new UI.SeekRow(this, "Ataque", 0, 2000, 5,
                new UI.Fmt() { @Override public String f(int v) { return v + " ms"; } },
                new UI.OnVal() {
                    @Override public void on(int v, boolean user) {
                        if (loading || !user) return;
                        p.attackMs = v;
                        changed(false);
                    }
                });
        env.addView(atkRow);
        relRow = new UI.SeekRow(this, "Liberação (som depois de soltar)", 5, 5000, 150,
                new UI.Fmt() { @Override public String f(int v) { return v + " ms"; } },
                new UI.OnVal() {
                    @Override public void on(int v, boolean user) {
                        if (loading || !user) return;
                        p.releaseMs = v;
                        changed(false);
                    }
                });
        env.addView(relRow);
        col.addView(env, UI.lpm(-1, -2, 0, 0, 0, 8, this));

        LinearLayout out = UI.card(this, "Exportação");
        volRow = new UI.SeekRow(this, "Volume do instrumento", 1, 100, 100,
                new UI.Fmt() { @Override public String f(int v) { return v + "%"; } },
                new UI.OnVal() {
                    @Override public void on(int v, boolean user) {
                        if (loading || !user) return;
                        p.volume = v;
                        changed(false);
                    }
                });
        out.addView(volRow);
        normSw = UI.sw(this, "Normalizar (deixar o volume no máximo sem estourar)", true, new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                if (loading) return;
                p.normalize = on;
                changed(false);
            }
        });
        out.addView(normSw);

        TextView modeTitle = UI.text(this, "Como montar o .sf2", 12, Pal.sub, false);
        out.addView(modeTitle, UI.lpm(-2, -2, 0, 4, 0, 4, this));
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        chipSingle = chip("Nota única", new View.OnClickListener() {
            @Override public void onClick(View v) { setExportMode(0); }
        });
        chipMulti = chip("Uma amostra por nota", new View.OnClickListener() {
            @Override public void onClick(View v) { setExportMode(1); }
        });
        modes.addView(chipSingle, UI.lpm(-2, -2, 0, 0, 6, 0, this));
        modes.addView(chipMulti);
        out.addView(modes);
        TextView modeHelp = UI.text(this,
                "Nota única: guarda 1 áudio e o programa muda o tom (arquivo pequeno).\n"
                        + "Uma por nota: cada tecla tem seu próprio áudio já afinado (som mais fiel, arquivo maior).\n"
                        + "Modo vogais: a tecla toca A, E, I, O, A, E, I, O… (só as vogais que têm áudio).",
                11, Pal.sub, false);
        out.addView(modeHelp, UI.lpm(-1, -2, 0, 4, 0, 6, this));

        rateStep = new UI.Stepper(this, "Taxa", new UI.OnStep() {
            @Override public void on(int dir) {
                int i = 0;
                for (int k = 0; k < RATES.length; k++) if (RATES[k] == p.exportRate) i = k;
                i = (i + dir + RATES.length) % RATES.length;
                p.exportRate = RATES[i];
                changed(false);
                refreshUi();
            }
        });
        out.addView(rateStep);
        loStep = new UI.Stepper(this, "Nota mais grave", new UI.OnStep() {
            @Override public void on(int dir) {
                p.rangeLo = Math.max(0, Math.min(p.rangeHi, p.rangeLo + dir));
                changed(false);
                refreshUi();
            }
        });
        out.addView(loStep, UI.lpm(-1, -2, 0, 4, 0, 0, this));
        hiStep = new UI.Stepper(this, "Nota mais aguda", new UI.OnStep() {
            @Override public void on(int dir) {
                p.rangeHi = Math.min(127, Math.max(p.rangeLo, p.rangeHi + dir));
                changed(false);
                refreshUi();
            }
        });
        out.addView(hiStep, UI.lpm(-1, -2, 0, 4, 0, 0, this));

        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.addView(chip("C2–C7", new View.OnClickListener() {
            @Override public void onClick(View v) { setRange(36, 96); }
        }), UI.lpm(-2, -2, 0, 0, 6, 0, this));
        presets.addView(chip("Piano 88", new View.OnClickListener() {
            @Override public void onClick(View v) { setRange(21, 108); }
        }), UI.lpm(-2, -2, 0, 0, 6, 0, this));
        presets.addView(chip("Tudo", new View.OnClickListener() {
            @Override public void onClick(View v) { setRange(0, 127); }
        }));
        out.addView(presets, UI.lpm(-2, -2, 0, 6, 0, 0, this));

        sizeInfo = UI.text(this, "", 11, Pal.sub, false);
        out.addView(sizeInfo, UI.lpm(-1, -2, 0, 6, 0, 0, this));
        col.addView(out, UI.lp(-1, -2));

        mid.addView(sv, new LinearLayout.LayoutParams(0, -1, 4f));
        root.addView(mid, new LinearLayout.LayoutParams(-1, 0, 1f));

        // ---- barra do piano ----
        LinearLayout pbar = new LinearLayout(this);
        pbar.setOrientation(LinearLayout.HORIZONTAL);
        pbar.setGravity(Gravity.CENTER_VERTICAL);
        pbar.setPadding(d8, UI.dp(this, 4), d8, UI.dp(this, 4));
        pbar.addView(chip("Oitava −", new View.OnClickListener() {
            @Override public void onClick(View v) { shiftOctave(-1); }
        }));
        octLabel = UI.text(this, "", 12, Pal.text, true);
        octLabel.setGravity(Gravity.CENTER);
        pbar.addView(octLabel, UI.lp(UI.dp(this, 96), -2));
        pbar.addView(chip("Oitava +", new View.OnClickListener() {
            @Override public void onClick(View v) { shiftOctave(1); }
        }));
        btnScale = chip("▶ Escala cromática", new View.OnClickListener() {
            @Override public void onClick(View v) { toggleScale(); }
        });
        pbar.addView(btnScale, UI.lpm(-2, -2, 12, 0, 0, 0, this));
        noteLbl = UI.text(this, "Toque no piano para testar", 12, Pal.sub, false);
        noteLbl.setGravity(Gravity.END);
        pbar.addView(noteLbl, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(pbar, UI.lp(-1, -2));

        piano = new PianoView(this);
        piano.setListener(this);
        piano.setOctaves(Prefs.octaves(this));
        piano.setNoteStyle(Prefs.noteStyle(this));
        int rootOct = cs.rootNote / 12 - 1;
        piano.setFirstKey(12 * (Math.max(0, rootOct - piano.getOctaves() / 2) + 1));
        int ph = Math.max(UI.dp(this, 110), (int) (getResources().getDisplayMetrics().heightPixels * 0.30f));
        root.addView(piano, UI.lpm(-1, ph, 8, 0, 8, 6, this));

        setContentView(root);
    }

    // ------------------------------------------------------------------ estado -> interface

    private void refreshUi() {
        loading = true;
        int style = Prefs.noteStyle(this);
        title.setText(p.name);
        rootStep.set(Pal.noteName(cs.rootNote, style) + " (" + cs.rootNote + ")");
        fineRow.set(cs.fineCents);
        loopSw.setChecked(cs.loop);
        atkRow.set(p.attackMs);
        relRow.set(p.releaseMs);
        volRow.set(p.volume);
        normSw.setChecked(p.normalize);
        styleChip(chipSingle, p.exportMode == 0);
        styleChip(chipMulti, p.exportMode == 1);
        rateStep.set(p.exportRate == 0 ? "Original" : p.exportRate + " Hz");
        loStep.set(Pal.noteName(p.rangeLo, style));
        hiStep.set(Pal.noteName(p.rangeHi, style));
        styleChip(chipTrim, modeIsTrim);
        styleChip(chipLoop, !modeIsTrim);
        refreshVowelUi();
        wave.setData(audio);
        syncWave();
        piano.setRoot(cs.rootNote);
        piano.setRange(p.rangeLo, p.rangeHi);
        updateOctLabel();
        loading = false;
        updateSizeInfo();
    }

    private void refreshVowelUi() {
        styleChip(chipVowel, p.vowelMode);
        chipVowel.setText(p.vowelMode ? "Modo vogais: ligado" : "Modo vogais: desligado");
        vowelRow.setVisibility(p.vowelMode ? View.VISIBLE : View.GONE);
        for (int i = 0; i < 5; i++) {
            vchips[i].setText(Project.VOWELS[i] + (p.slots[i].has() ? " ●" : "") + (i == 4 ? " (opcional)" : ""));
            styleChip(vchips[i], i == cur);
        }
        styleChip(chipSolo, solo);
        tuneTitle.setText(p.vowelMode ? "AFINAÇÃO — VOGAL " + Project.VOWELS[cur] : "AFINAÇÃO");
    }

    private boolean anyAudio() {
        return p.activeSlots(audios).length > 0;
    }

    private void selectSlot(int i) {
        if (i < 0 || i > 4) return;
        stopScale();
        if (synth != null) synth.allOff();
        cur = i;
        cs = p.slots[i];
        audio = audios[i];
        refreshUi();
        updateSynth();
    }

    private void setVowelMode(boolean on) {
        stopScale();
        if (synth != null) synth.allOff();
        p.vowelMode = on;
        if (!on) {
            solo = false;
            cur = 0;
            cs = p.slots[0];
            audio = audios[0];
        }
        changed(false);
        refreshUi();
        if (on) {
            toastLong("Modo vogais: cada tecla toca uma vogal em sequência (A E I O A E I O…). "
                    + "Grave uma por vez ou importe vários áudios de uma vez. A vogal U é opcional.");
        }
    }

    /** Qual slot toca para esta tecla (mesma regra do .sf2): vogal ativa número (tecla mod n). */
    private int slotFor(int key) {
        if (!p.vowelMode) return 0;
        if (solo) return cur;
        int[] act = p.activeSlots(audios);
        return act.length == 0 ? -1 : act[key % act.length];
    }

    private boolean modeIsTrim = true;

    private void setMode(int m) {
        modeIsTrim = m == WaveformView.MODE_TRIM;
        if (!modeIsTrim && !cs.loop) {
            cs.loop = true;
            p.sanitize();
            loading = true;
            loopSw.setChecked(true);
            loading = false;
            changed(false);
        }
        wave.setMode(m);
        syncWave();
        styleChip(chipTrim, modeIsTrim);
        styleChip(chipLoop, !modeIsTrim);
    }

    private void syncWave() {
        wave.setTrim(cs.trimStart, cs.trimEnd);
        wave.setLoop(cs.loopStart, cs.loopEnd, cs.loop);
    }

    private void updateOctLabel() {
        int style = Prefs.noteStyle(this);
        int f = piano.getFirstKey();
        octLabel.setText(Pal.noteName(f, style) + " – " + Pal.noteName(f + 12 * piano.getOctaves(), style));
    }

    private void shiftOctave(int dir) {
        piano.setFirstKey(piano.getFirstKey() + dir * 12);
        updateOctLabel();
    }

    private void setRange(int lo, int hi) {
        p.rangeLo = lo;
        p.rangeHi = hi;
        changed(false);
        refreshUi();
    }

    private void setExportMode(int m) {
        p.exportMode = m;
        changed(false);
        refreshUi();
    }

    private void updateSizeInfo() {
        if (sizeInfo == null) return;
        if (!anyAudio()) {
            sizeInfo.setTextColor(Pal.sub);
            sizeInfo.setText("Adicione um áudio para ver o tamanho do .sf2");
            return;
        }
        long est = Exporter.estimateBytes(p);
        String s;
        if (est < 1024 * 1024) s = String.format(Locale.US, "%.0f KB", est / 1024.0);
        else s = String.format(Locale.US, "%.1f MB", est / 1048576.0);
        if (est > Exporter.MAX_BYTES) {
            sizeInfo.setTextColor(Pal.danger);
            sizeInfo.setText("Tamanho estimado: " + s + " — grande demais! Corte o áudio ou reduza a faixa de notas.");
        } else {
            sizeInfo.setTextColor(Pal.sub);
            sizeInfo.setText("Tamanho estimado do .sf2: " + s);
        }
    }

    // ------------------------------------------------------------------ salvar / sintetizador

    private void changed(boolean audioChanged) {
        if (audioChanged) dirty[cur] = true;
        p.sanitize();
        status.setText("salvando…");
        h.removeCallbacks(saveTask);
        h.postDelayed(saveTask, 700);
        updateSynth();
        if (piano != null) {
            piano.setRoot(cs.rootNote);
            piano.setRange(p.rangeLo, p.rangeHi);
        }
        updateSizeInfo();
    }

    private void saveNow() {
        try {
            p.modified = System.currentTimeMillis();
            for (int i = 0; i < 5; i++) {
                if (dirty[i] && audios[i] != null) {
                    ProjectStore.saveAudio(this, p, i, audios[i]);
                    dirty[i] = false;
                }
            }
            ProjectStore.save(this, p);
            if (status != null) status.setText("salvo ✓");
        } catch (Exception e) {
            if (status != null) status.setText("erro ao salvar");
        }
    }

    private float peakOf(int i) {
        Slot s = p.slots[i];
        float[] a = audios[i];
        if (pkRef[i] != a || pkTs[i] != s.trimStart || pkTe[i] != s.trimEnd) {
            pkVal[i] = Dsp.peak(a, s.trimStart, s.trimEnd);
            pkRef[i] = a;
            pkTs[i] = s.trimStart;
            pkTe[i] = s.trimEnd;
        }
        return pkVal[i];
    }

    private void updateSynth() {
        if (synth == null) return;
        for (int i = 0; i < 5; i++) {
            Slot s = p.slots[i];
            float[] a = audios[i];
            if (a == null || s.frames <= 0) {
                synth.setSound(i, null, 44100, 0, 0, false, 0, 0, 60, 0, 0f);
                continue;
            }
            float pk = peakOf(i);
            float g = (p.normalize && pk > 0.0001f) ? 0.95f / pk : 1f;
            g *= p.volume / 100f;
            synth.setSound(i, a, s.sampleRate, s.trimStart, s.trimEnd, s.loop, s.loopStart, s.loopEnd,
                    s.rootNote, s.fineCents, g);
        }
        synth.setEnvelope(p.attackMs / 1000f, p.releaseMs / 1000f);
        synth.setMapping(p.vowelMode, p.activeSlots(audios), (p.vowelMode && solo) ? cur : -1);
    }

    // ------------------------------------------------------------------ piano

    @Override
    public void down(int key, int pid) {
        if (!anyAudio()) { noteLbl.setText("Adicione um áudio primeiro"); return; }
        synth.noteOn(key, pid);
        int sl = slotFor(key);
        String extra = (p.vowelMode && sl >= 0) ? "  ·  vogal " + Project.VOWELS[sl] : "";
        noteLbl.setText(Pal.noteName(key, Prefs.noteStyle(this)) + "  (" + key + ")" + extra);
        if (Prefs.haptic(this)) piano.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    @Override
    public void up(int key, int pid) {
        synth.noteOff(pid);
    }

    private void toggleScale() {
        if (scaleRunning) { stopScale(); return; }
        if (!anyAudio()) { toast("Adicione um áudio primeiro"); return; }
        scaleRunning = true;
        btnScale.setText("■ Parar escala");
        final int base = cs.rootNote;
        final int style = Prefs.noteStyle(this);
        long last = 0;
        for (int i = 0; i <= 12; i++) {
            final int k = base + i;
            if (k > 127) break;
            final int pid = 1000 + i;
            Runnable on = new Runnable() {
                @Override public void run() {
                    if (!scaleRunning) return;
                    synth.noteOn(k, pid);
                    piano.setExt(k, true);
                    noteLbl.setText(Pal.noteName(k, style) + "  (" + k + ")");
                }
            };
            Runnable off = new Runnable() {
                @Override public void run() {
                    synth.noteOff(pid);
                    piano.setExt(k, false);
                }
            };
            scaleTasks.add(on);
            scaleTasks.add(off);
            h.postDelayed(on, i * 380L);
            h.postDelayed(off, i * 380L + 340L);
            last = i * 380L + 700L;
        }
        Runnable end = new Runnable() {
            @Override public void run() { stopScale(); }
        };
        scaleTasks.add(end);
        h.postDelayed(end, last);
    }

    private void stopScale() {
        for (Runnable r : scaleTasks) h.removeCallbacks(r);
        scaleTasks.clear();
        scaleRunning = false;
        if (btnScale != null) btnScale.setText("▶ Escala cromática");
        if (piano != null) piano.clearExt();
        if (synth != null) {
            for (int i = 0; i <= 12; i++) synth.noteOff(1000 + i);
        }
    }

    // ------------------------------------------------------------------ áudio: importar, gravar, exemplo, detectar

    private void doImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        if (p.vowelMode) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(i, RQ_IMPORT);
        } catch (Exception e) {
            toast("Não encontrei um app para escolher arquivos");
        }
    }

    @Override
    protected void onActivityResult(int rq, int res, Intent data) {
        super.onActivityResult(rq, res, data);
        if (res != RESULT_OK || data == null) return;
        if (rq == RQ_IMPORT) {
            final List<Uri> uris = new ArrayList<>();
            ClipData cd = data.getClipData();
            if (cd != null) {
                for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) importUris(uris);
        } else if (rq == RQ_EXPORT && data.getData() != null) {
            doExport(data.getData());
        }
    }

    /** Sem o modo vogais usa só o primeiro arquivo; com ele, um arquivo por vogal a partir da selecionada. */
    private void importUris(final List<Uri> uris) {
        final int firstSlot = cur;
        final int max = p.vowelMode ? Math.min(uris.size(), 5 - firstSlot) : 1;
        toast(max > 1 ? "Importando " + max + " áudios…" : "Importando…");
        new Thread(new Runnable() {
            @Override public void run() {
                final List<AudioIO.Pcm> got = new ArrayList<>();
                String err = null;
                for (int i = 0; i < max; i++) {
                    try {
                        got.add(AudioIO.decode(EditorActivity.this, uris.get(i)));
                    } catch (Exception e) {
                        err = e.getMessage() != null ? e.getMessage() : "formato não suportado";
                        got.add(null);
                    }
                }
                final String fe = err;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        int ok = 0;
                        for (int i = 0; i < got.size(); i++) {
                            AudioIO.Pcm pcm = got.get(i);
                            if (pcm == null) continue;
                            setAudioAt(firstSlot + i, pcm.data, pcm.rate, true, max == 1);
                            ok++;
                        }
                        if (ok > 0 && max > 1) {
                            changed(true);
                            refreshUi();
                            toast(ok + " vogais importadas, a partir da " + Project.VOWELS[firstSlot]);
                        }
                        if (fe != null) toastLong("Não consegui importar: " + fe);
                    }
                });
            }
        }, "chromix-import").start();
    }

    private void setAudio(float[] a, int rate, boolean detect) {
        setAudioAt(cur, a, rate, detect, true);
    }

    /** Coloca um áudio num slot. refresh = atualizar a tela (e avisar a nota detectada). */
    private void setAudioAt(int slot, float[] a, int rate, boolean detect, boolean refresh) {
        if (slot < 0 || slot > 4) return;
        stopScale();
        if (synth != null) synth.allOff();
        Slot s = p.slots[slot];
        audios[slot] = a;
        dirty[slot] = true;
        s.sampleRate = rate;
        s.frames = a.length;
        s.trimStart = 0;
        s.trimEnd = a.length;
        s.loopStart = a.length / 4;
        s.loopEnd = a.length * 3 / 4;
        s.fineCents = 0;
        if (slot == cur) audio = a;
        if (detect) detectPitchFor(s, a, refresh);
        if (a.length >= rate * AudioIO.MAX_SECONDS) toast("Áudio cortado em " + AudioIO.MAX_SECONDS + " segundos");
        if (refresh) {
            changed(true);
            refreshUi();
        }
    }

    private void useDemo() {
        if (p.vowelMode) {
            // preenche as vogais A, E, I, O que ainda estão vazias (se todas já têm áudio, troca só a selecionada)
            boolean any = false;
            for (int i = 0; i < 4; i++) {
                if (!p.slots[i].has()) { demoInto(i); any = true; }
            }
            if (!any) demoInto(cur);
            toast("Vogais de exemplo carregadas (A, E, I, O)");
        } else {
            demoInto(cur);
            toast("Voz de exemplo carregada (nota raiz A3)");
        }
        changed(true);
        refreshUi();
    }

    private void demoInto(int slot) {
        setAudioAt(slot, Dsp.demoVowel(44100, p.vowelMode ? slot : 0), 44100, false, false);
        Slot s = p.slots[slot];
        s.rootNote = 57;
        s.fineCents = 4;
        s.loop = true;
        s.loopStart = 200 * 40;
        s.loopEnd = 200 * 180;
    }

    private void detectPitch(boolean auto) {
        if (audio == null) { if (!auto) toast("Adicione um áudio primeiro"); return; }
        if (detectPitchFor(cs, audio, true)) {
            changed(false);
            refreshUi();
        }
    }

    /** Detecta a nota de um áudio e grava no slot. Retorna true se conseguiu. */
    private boolean detectPitchFor(Slot s, float[] a, boolean showToast) {
        double hz = Dsp.detectPitch(a, s.trimStart, s.trimEnd, s.sampleRate);
        if (hz <= 0) {
            if (showToast) toast("Não deu para detectar a nota. Ajuste a nota raiz na mão.");
            return false;
        }
        double midi = Dsp.freqToMidi(hz);
        int n = (int) Math.round(midi);
        int cents = (int) Math.round((midi - n) * 100);
        s.rootNote = Math.max(0, Math.min(127, n));
        s.fineCents = Math.max(-50, Math.min(50, cents));
        if (showToast) {
            toast("Nota detectada: " + Pal.noteName(s.rootNote, Prefs.noteStyle(this))
                    + " (" + (cents >= 0 ? "+" : "") + cents + " cents)");
        }
        return true;
    }

    private void smoothLoop() {
        if (audio == null) { toast("Adicione um áudio primeiro"); return; }
        if (!cs.loop) { cs.loop = true; p.sanitize(); }
        int[] r = Dsp.refineLoop(audio, cs.loopStart, cs.loopEnd, cs.trimStart, cs.trimEnd);
        cs.loopStart = r[0];
        cs.loopEnd = r[1];
        changed(false);
        refreshUi();
        toast("Loop ajustado para emendar sem estalo");
    }

    private void toggleRecord() {
        if (rec != null) { finishRecording(); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, RQ_MIC);
            return;
        }
        startRecording();
    }

    @Override
    public void onRequestPermissionsResult(int rq, String[] perms, int[] results) {
        super.onRequestPermissionsResult(rq, perms, results);
        if (rq == RQ_MIC) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startRecording();
            else toastLong("Sem permissão do microfone não dá para gravar. Você ainda pode importar um áudio.");
        }
    }

    private final Runnable recTick = new Runnable() {
        @Override public void run() {
            if (rec == null) return;
            if (!rec.isRunning()) { finishRecording(); return; }
            long s = (System.currentTimeMillis() - recStart) / 1000;
            btnRec.setText("■ Parar (" + s + " s)");
            h.postDelayed(this, 250);
        }
    };

    private void startRecording() {
        stopScale();
        AudioIO.Recorder r = new AudioIO.Recorder();
        if (!r.start()) { toast("Não consegui usar o microfone"); return; }
        rec = r;
        recStart = System.currentTimeMillis();
        UI.restyle(btnRec, Pal.danger, 0xFFFFFFFF);
        btnRec.setText("■ Parar (0 s)");
        h.postDelayed(recTick, 250);
    }

    private void finishRecording() {
        if (rec == null) return;
        h.removeCallbacks(recTick);
        float[] a = rec.stop();
        int rate = rec.rate;
        rec = null;
        UI.restyle(btnRec, Pal.card2, Pal.text);
        btnRec.setText("Gravar");
        if (a.length < rate / 10) { toast("Gravação muito curta"); return; }
        a = trimSilence(a, rate);
        int was = cur;
        setAudio(a, rate, true);
        if (p.vowelMode && was < 4 && !p.slots[was + 1].has()) {
            selectSlot(was + 1);
            toast("Vogal " + Project.VOWELS[was] + " gravada! Agora grave a " + Project.VOWELS[was + 1]);
        }
    }

    /** Tira o silêncio do começo e do fim da gravação. */
    private static float[] trimSilence(float[] a, int rate) {
        float pk = Dsp.peak(a, 0, a.length);
        if (pk < 0.001f) return a;
        float th = pk * 0.05f;
        int s = 0, e = a.length - 1;
        while (s < e && Math.abs(a[s]) < th) s++;
        while (e > s && Math.abs(a[e]) < th) e--;
        int m = rate / 40;
        s = Math.max(0, s - m);
        e = Math.min(a.length, e + m);
        if (e - s < rate / 20) return a;
        return java.util.Arrays.copyOfRange(a, s, e);
    }

    // ------------------------------------------------------------------ exportar

    private static String safeName(String s) {
        String n = s == null ? "" : s.replaceAll("[^A-Za-z0-9_\\- ]", "").trim().replace(' ', '_');
        return n.isEmpty() ? "chromatic" : n;
    }

    private void exportClick() {
        if (!anyAudio()) { toast("Adicione um áudio primeiro"); return; }
        if (Exporter.estimateBytes(p) > Exporter.MAX_BYTES) {
            toastLong("Arquivo grande demais. Corte o áudio ou reduza a faixa de notas.");
            return;
        }
        saveNow();
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/octet-stream");
        i.putExtra(Intent.EXTRA_TITLE, safeName(p.name) + ".sf2");
        try {
            startActivityForResult(i, RQ_EXPORT);
        } catch (Exception e) {
            toast("Não consegui abrir o seletor de arquivos");
        }
    }

    private void doExport(final Uri u) {
        toast("Gerando .sf2…");
        final Project snap = Project.fromJson(p.toJson());
        final float[][] a = audios.clone();
        final String author = Prefs.author(this);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    byte[] bytes = Exporter.build(snap, a, author);
                    try (OutputStream os = getContentResolver().openOutputStream(u, "wt")) {
                        if (os == null) throw new java.io.IOException("não consegui abrir o destino");
                        os.write(bytes);
                    }
                    final String size = bytes.length < 1048576
                            ? String.format(Locale.US, "%.0f KB", bytes.length / 1024.0)
                            : String.format(Locale.US, "%.1f MB", bytes.length / 1048576.0);
                    runOnUiThread(new Runnable() {
                        @Override public void run() { toastLong("Exportado! .sf2 de " + size + " salvo."); }
                    });
                } catch (final Throwable e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { toastLong("Erro ao exportar: " + e.getMessage()); }
                    });
                }
            }
        }, "chromix-export").start();
    }

    // ------------------------------------------------------------------ renomear

    private void rename() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setText(p.name);
        et.setSelectAllOnFocus(true);
        dlg().setTitle("Renomear projeto").setView(et)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String n = et.getText().toString().trim();
                        if (!n.isEmpty()) {
                            p.name = n;
                            title.setText(n);
                            changed(false);
                        }
                    }
                })
                .setNegativeButton("Cancelar", null).show();
    }
}
