package br.com.pevicasi.nfefacil;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import java.util.*;

/** App-owned appearance only: updates live views without recreating camera or invoice state. */
public abstract class AppearanceActivity extends ComponentActivity {
    private SharedPreferences preferences;
    private final Map<TextView, Float> originalSizes = new WeakHashMap<>();
    private final Set<AlertDialog> dialogs = Collections.newSetFromMap(new WeakHashMap<>());
    private TextView percentage;
    private Button smaller, larger, themeButton;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener = (p, key) -> refreshAppearance();

    @Override public void onCreate(Bundle state) {
        preferences = getSharedPreferences("appearance", MODE_PRIVATE);
        setTheme(isDark() ? R.style.AppThemeDark : R.style.AppTheme);
        super.onCreate(state);
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
    }
    int fontPercent() { return Math.max(90, Math.min(160, preferences.getInt("fontPercent", 100))); }
    String themeMode() {
        String mode = preferences.getString("themeMode", "auto");
        return "dark".equals(mode) || "light".equals(mode) ? mode : "auto";
    }
    boolean isDark() {
        return "dark".equals(themeMode()) || ("auto".equals(themeMode()) &&
            (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
    }
    void setFontPercent(int value) { preferences.edit().putInt("fontPercent", Math.max(90, Math.min(160, value))).apply(); }
    void setThemeMode(String mode) { preferences.edit().putString("themeMode", mode).apply(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    protected void addAppearanceControls(LinearLayout parent) {
        LinearLayout bar = new LinearLayout(this); bar.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        smaller = control("A−", "Diminuir fontes", () -> setFontPercent(fontPercent()-10));
        larger = control("A+", "Aumentar fontes", () -> setFontPercent(fontPercent()+10));
        percentage = new TextView(this); percentage.setGravity(Gravity.CENTER); percentage.setTextSize(14);
        percentage.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        row.addView(smaller, new LinearLayout.LayoutParams(dp(64), -2));
        row.addView(percentage, new LinearLayout.LayoutParams(dp(64), -1));
        row.addView(larger, new LinearLayout.LayoutParams(dp(64), -2));
        row.addView(control("Restaurar padrão", "Restaurar fontes para 100%", () -> setFontPercent(100)), new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(row);
        themeButton = control("", "Escolher tema: Claro, Escuro ou Automático", () -> {
            String[] modes = {"light", "dark", "auto"};
            int selected = Arrays.asList(modes).indexOf(themeMode());
            showOwnedDialog(new AlertDialog.Builder(this).setTitle("Aparência")
                .setSingleChoiceItems(new String[]{"Claro", "Escuro", "Automático (Android)"}, selected, (dialog, which) -> {
                    setThemeMode(modes[which]); dialog.dismiss();
                }).setNegativeButton("Fechar", null).create());
        });
        themeButton.setTag("appearance-theme");
        bar.addView(themeButton, new LinearLayout.LayoutParams(-1, -2)); parent.addView(bar);
    }
    private Button control(String label, String description, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setTextSize(14);
        button.setAllCaps(false); button.setContentDescription(description);
        button.setOnClickListener(v -> action.run()); return button;
    }
    @Override public void setContentView(View view) {
        super.setContentView(view);
        view.getViewTreeObserver().addOnGlobalLayoutListener(() -> style(view));
        refreshAppearance();
    }
    protected void showOwnedDialog(AlertDialog dialog) {
        dialogs.add(dialog); dialog.show();
        View decor = dialog.getWindow().getDecorView();
        decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> style(decor));
        style(decor);
        dialog.getWindow().setBackgroundDrawableResource(isDark() ? android.R.color.background_dark : android.R.color.background_light);
    }
    private void style(View view) {
        int text = Color.parseColor(isDark() ? "#E8EDF5" : "#172033");
        if (view instanceof LinearLayout || view instanceof ScrollView || view instanceof ListView)
            view.setBackgroundColor(Color.parseColor(isDark()?"#111827":"#F4F7FB"));
        if (view instanceof TextView) {
            TextView label = (TextView)view;
            if (!originalSizes.containsKey(label)) {
                originalSizes.put(label, label.getTextSize());
                label.setSingleLine(false); label.setEllipsize(null);
            }
            float size = originalSizes.get(label) * fontPercent()/100f;
            if (Math.abs(label.getTextSize()-size) > .1f) label.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
            label.setTextColor(text);
            if (label instanceof CompoundButton)
                ((CompoundButton)label).setButtonTintList(ColorStateList.valueOf(Color.parseColor(isDark()?"#9BBDFF":"#1F5EFF")));
            if (label instanceof CheckedTextView) {
                label.setBackgroundColor(Color.parseColor(isDark()?"#111827":"#F4F7FB"));
                ((CheckedTextView)label).setCheckMarkTintList(ColorStateList.valueOf(Color.parseColor(isDark()?"#9BBDFF":"#1F5EFF")));
            }
            if (label instanceof Button) {
                if (label.getMinHeight() < dp(48)) label.setMinHeight(dp(48));
                label.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},
                    new int[]{Color.parseColor(isDark()?"#242E3D":"#E1E6EF"),Color.parseColor(isDark()?"#34445D":"#E8EFFC")}));
            }
        }
        if (view instanceof ViewGroup && !(view instanceof android.webkit.WebView)) {
            ViewGroup group = (ViewGroup)view;
            for (int i=0;i<group.getChildCount();i++) style(group.getChildAt(i));
        }
    }
    protected void onAppearanceChanged() {}
    @SuppressWarnings("deprecation") // Custom text is required for the app's own foreground font preference.
    protected void showNotice(String message) {
        TextView label = new TextView(this); label.setText(message);
        label.setTextSize(14 * fontPercent()/100f); label.setPadding(dp(16),dp(12),dp(16),dp(12));
        label.setTextColor(Color.parseColor(isDark()?"#E8EDF5":"#172033"));
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(Color.parseColor(isDark()?"#293E5C":"#E8EFFC")); background.setCornerRadius(dp(12)); label.setBackground(background);
        Toast notice = new Toast(this); notice.setView(label); notice.setDuration(Toast.LENGTH_SHORT); notice.show();
    }
    private void refreshAppearance() {
        if (preferences == null) return;
        setTheme(isDark() ? R.style.AppThemeDark : R.style.AppTheme);
        int background = Color.parseColor(isDark()?"#111827":"#F4F7FB");
        View content = findViewById(android.R.id.content);
        if (content != null) { content.setBackgroundColor(background); style(content); }
        getWindow().setStatusBarColor(background); getWindow().setNavigationBarColor(background);
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(isDark() ? flags & ~light : flags | light);
        if (percentage != null) {
            percentage.setText(fontPercent()+"%"); smaller.setEnabled(fontPercent()>90); larger.setEnabled(fontPercent()<160);
            themeButton.setText("Tema: "+("dark".equals(themeMode())?"Escuro":"light".equals(themeMode())?"Claro":"Automático (Android)"));
            themeButton.setContentDescription(themeButton.getText()+". Escolher Claro, Escuro ou Automático");
        }
        for (AlertDialog dialog : new ArrayList<>(dialogs)) if (dialog.isShowing()) {
            dialog.getWindow().getDecorView().setBackgroundColor(background); style(dialog.getWindow().getDecorView());
        }
        // Adapter popup rows are outside the Activity tree; keep live owned rows in sync too.
        for (TextView label : new ArrayList<>(originalSizes.keySet())) if (label != null) style(label);
        onAppearanceChanged();
    }
    @Override protected void onResume() { super.onResume(); refreshAppearance(); }
    @Override public void onConfigurationChanged(Configuration configuration) { super.onConfigurationChanged(configuration); refreshAppearance(); }
    @Override protected void onDestroy() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        for (AlertDialog dialog : new ArrayList<>(dialogs)) if (dialog.isShowing()) dialog.dismiss();
        super.onDestroy();
    }
}
