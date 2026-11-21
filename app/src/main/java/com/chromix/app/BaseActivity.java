package com.chromix.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

/** Aplica tema, cores das barras do sistema e atalhos comuns. */
public abstract class BaseActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        Pal.apply(this);
        setTheme(Pal.dark ? R.style.Theme_Chromix_Dark : R.style.Theme_Chromix_Light);
        super.onCreate(b);
        getWindow().setStatusBarColor(Pal.bg);
        getWindow().setNavigationBarColor(Pal.bg);
        View decor = getWindow().getDecorView();
        if (!Pal.dark) {
            decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        if (Prefs.keepOn(this)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    protected void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    protected void toastLong(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    protected AlertDialog.Builder dlg() {
        return new AlertDialog.Builder(this, Pal.dark
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
    }

    protected void fade() {
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
