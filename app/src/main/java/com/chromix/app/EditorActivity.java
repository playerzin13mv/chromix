package com.chromix.app;

import android.Manifest;
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
    private float[] audio;
    private Synth synth;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean loading = false;
    private boolean audioDirty = false;

    private WaveformView wave;
    private PianoView piano;
    private TextView title, status, noteLbl, sizeInfo, octLabel;
    private TextView chipTrim, chipLoop, btnRec, btnScale, chipSingle, chipMulti;
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
        audio = ProjectStore.loadAudio(this, p);
        p.frames = audio == null ? 0 : audio.length;
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
                p.trimStart = wave.getTrimStart();
                p.trimEnd = wave.getTrimEnd();
                p.loopStart = wave.getLoopStart();
                p.loopEnd = wave.getLoopEnd();
                changed(false);
            }
        });
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
        rootStep = new UI.Stepper(this, "Nota raiz", new UI.OnStep() {
            @Override public void on(int dir) {
                p.rootNote = Math.max(0, Math.min(127, p.rootNote + dir));
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
                        p.fineCents = v;
                        changed(false);
                    }
                });
        tune.addView(fineRow, UI.lpm(-1, -2, 0, 4, 0, 0, this));
        col.addView(tune, UI.lpm(-1, -2, 0, 0, 0, 8, this));

        LinearLayout env = UI.card(this, "Loop e envelope");
        loopSw = UI.sw(this, "Repetir (loop) enquanto a tecla está pressionada", false, new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                if (loading) return;
                p.loop = on;
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
                        + "Uma por nota: cada tecla tem seu próprio áudio já afinado (som mais fiel, arquivo maior).",
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
        int rootOct = p.rootNote / 12 - 1;
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
        rootStep.set(Pal.noteName(p.rootNote, style) + " (" + p.rootNote + ")");
        fineRow.set(p.fineCents);
        loopSw.setChecked(p.loop);
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
        wave.setData(audio);
        syncWave();
        piano.setRoot(p.rootNote);
        piano.setRange(p.rangeLo, p.rangeHi);
        updateOctLabel();
        loading = false;
        updateSizeInfo();
    }

    private boolean modeIsTrim = true;

    private void setMode(int m) {
        modeIsTrim = m == WaveformView.MODE_TRIM;
        if (!modeIsTrim && !p.loop) {
            p.loop = true;
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
        wave.setTrim(p.trimStart, p.trimEnd);
        wave.setLoop(p.loopStart, p.loopEnd, p.loop);
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
        if (audio == null || p.frames <= 0) {
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
        if (audioChanged) audioDirty = true;
        p.sanitize();
        status.setText("salvando…");
        h.removeCallbacks(saveTask);
        h.postDelayed(saveTask, 700);
        updateSynth();
        if (piano != null) {
            piano.setRoot(p.rootNote);
            piano.setRange(p.rangeLo, p.rangeHi);
        }
        updateSizeInfo();
    }

    private void saveNow() {
        try {
            p.modified = System.currentTimeMillis();
            if (audioDirty && audio != null) {
                ProjectStore.saveAudio(this, p, audio);
                audioDirty = false;
            }
            ProjectStore.save(this, p);
            if (status != null) status.setText("salvo ✓");
        } catch (Exception e) {
            if (status != null) status.setText("erro ao salvar");
        }
    }

    private void updateSynth() {
        if (synth == null) return;
        if (audio == null || p.frames <= 0) { synth.set(null, 44100, 0, 0, false, 0, 0, 60, 0, 0, 0, 0); return; }
        float peak = Dsp.peak(audio, p.trimStart, p.trimEnd);
        float g = (p.normalize && peak > 0.0001f) ? 0.95f / peak : 1f;
        g *= p.volume / 100f;
        synth.set(audio, p.sampleRate, p.trimStart, p.trimEnd, p.loop, p.loopStart, p.loopEnd,
                p.rootNote, p.fineCents, p.attackMs / 1000f, p.releaseMs / 1000f, g);
    }

    // ------------------------------------------------------------------ piano

    @Override
    public void down(int key, int pid) {
        if (audio == null) { noteLbl.setText("Adicione um áudio primeiro"); return; }
        synth.noteOn(key, pid);
        noteLbl.setText(Pal.noteName(key, Prefs.noteStyle(this)) + "  (" + key + ")");
        if (Prefs.haptic(this)) piano.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    @Override
    public void up(int key, int pid) {
        synth.noteOff(pid);
    }

    private void toggleScale() {
        if (scaleRunning) { stopScale(); return; }
        if (audio == null) { toast("Adicione um áudio primeiro"); return; }
        scaleRunning = true;
        btnScale.setText("■ Parar escala");
        final int base = p.rootNote;
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
        try {
            startActivityForResult(i, RQ_IMPORT);
        } catch (Exception e) {
            toast("Não encontrei um app para escolher arquivos");
        }
    }

    @Override
    protected void onActivityResult(int rq, int res, Intent data) {
        super.onActivityResult(rq, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri u = data.getData();
        if (rq == RQ_IMPORT) {
            toast("Importando…");
            new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        final AudioIO.Pcm pcm = AudioIO.decode(EditorActivity.this, u);
                        runOnUiThread(new Runnable() {
                            @Override public void run() { setAudio(pcm.data, pcm.rate, true); }
                        });
                    } catch (final Exception e) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() { toastLong("Não consegui importar: " + e.getMessage()); }
                        });
                    }
                }
            }, "chromix-import").start();
        } else if (rq == RQ_EXPORT) {
            doExport(u);
        }
    }

    private void setAudio(float[] a, int rate, boolean detect) {
        stopScale();
        synth.allOff();
        audio = a;
        p.sampleRate = rate;
        p.frames = a.length;
        p.trimStart = 0;
        p.trimEnd = a.length;
        p.loopStart = a.length / 4;
        p.loopEnd = a.length * 3 / 4;
        p.fineCents = 0;
        if (detect) detectPitch(true);
        changed(true);
        refreshUi();
        if (a.length >= rate * AudioIO.MAX_SECONDS) toast("Áudio cortado em " + AudioIO.MAX_SECONDS + " segundos");
    }

    private void useDemo() {
        float[] v = Dsp.demoVoice(44100);
        setAudio(v, 44100, false);
        p.rootNote = 57;
        p.fineCents = 4;
        p.loop = true;
        p.loopStart = 200 * 40;
        p.loopEnd = 200 * 180;
        changed(false);
        refreshUi();
        toast("Voz de exemplo carregada (nota raiz A3)");
    }

    private void detectPitch(boolean auto) {
        if (audio == null) { if (!auto) toast("Adicione um áudio primeiro"); return; }
        double hz = Dsp.detectPitch(audio, p.trimStart, p.trimEnd, p.sampleRate);
        if (hz <= 0) {
            if (!auto) toast("Não consegui detectar a nota. Ajuste a nota raiz manualmente.");
            else toast("Não deu para detectar a nota — ajuste a nota raiz na mão");
            return;
        }
        double midi = Dsp.freqToMidi(hz);
        int n = (int) Math.round(midi);
        int cents = (int) Math.round((midi - n) * 100);
        p.rootNote = Math.max(0, Math.min(127, n));
        p.fineCents = Math.max(-50, Math.min(50, cents));
        toast("Nota detectada: " + Pal.noteName(p.rootNote, Prefs.noteStyle(this))
                + " (" + (cents >= 0 ? "+" : "") + cents + " cents)");
        if (!auto) { changed(false); refreshUi(); }
    }

    private void smoothLoop() {
        if (audio == null) { toast("Adicione um áudio primeiro"); return; }
        if (!p.loop) { p.loop = true; p.sanitize(); }
        int[] r = Dsp.refineLoop(audio, p.loopStart, p.loopEnd, p.trimStart, p.trimEnd);
        p.loopStart = r[0];
        p.loopEnd = r[1];
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
        setAudio(a, rate, true);
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
        if (audio == null || p.frames <= 0) { toast("Adicione um áudio primeiro"); return; }
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
        final float[] a = audio;
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
