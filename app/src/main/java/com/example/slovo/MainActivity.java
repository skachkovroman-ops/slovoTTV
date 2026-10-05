package com.example.slovo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Вся игра написана на HTML/JS (папка assets), Activity только показывает её
 * во весь экран, передаёт в неё нажатия кнопок пульта и умеет запускать
 * системное распознавание речи для голосового ввода слова.
 */
public class MainActivity extends Activity {

    private static final int REQ_VOICE = 42;
    private static final int REQ_MIC_PERMISSION = 43;

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        web.setBackgroundColor(0xFF0F1B24);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.setWebViewClient(new WebViewClient());

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);   // для сохранения статистики и счёта

        // Мост для вызова голосового ввода из JS: AndroidVoice.start()
        web.addJavascriptInterface(new VoiceBridge(), "AndroidVoice");

        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
        web.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    /** Кнопки пульта: стрелки двигают выбор на экранной клавиатуре, OK нажимает выбранную клавишу. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        String cmd;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:     cmd = "up";    break;
            case KeyEvent.KEYCODE_DPAD_DOWN:   cmd = "down";  break;
            case KeyEvent.KEYCODE_DPAD_LEFT:   cmd = "left";  break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:  cmd = "right"; break;
            case KeyEvent.KEYCODE_DPAD_CENTER: cmd = "ok";    break;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: cmd = "enter"; break;
            // Кнопка микрофона на пульте. На части пультов (в т.ч. некоторых Xiaomi)
            // этот код вообще не доходит до приложения — система перехватывает его
            // раньше и открывает голосовой ассистент сама. Если дойдёт — запускаем
            // распознавание речи напрямую, в остальных случаях работает экранная
            // кнопка «🎤» в приложении.
            case KeyEvent.KEYCODE_VOICE_ASSIST:
            case KeyEvent.KEYCODE_SEARCH:
                if (e.getAction() == KeyEvent.ACTION_DOWN) startVoice();
                return true;
            default:
                return super.dispatchKeyEvent(e);   // буквы с USB-клавиатуры, «Назад» и т.д.
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            web.evaluateJavascript("window.tvKey && window.tvKey('" + cmd + "')", null);
        }
        return true;
    }

    /** Вызывается из JS (кнопка «🎤» на экране) и из обработчика кнопки микрофона на пульте. */
    private void startVoice() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC_PERMISSION);
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажите слово из 5 букв");
        try {
            startActivityForResult(intent, REQ_VOICE);
        } catch (Exception notFound) {
            // На устройстве нет ни одного приложения, умеющего распознавать речь
            // (бывает на ТВ-прошивках без сервисов Google).
            Toast.makeText(this, "Голосовой ввод недоступен на этом устройстве", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoice();
            } else {
                Toast.makeText(this, "Нужно разрешение на микрофон", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_VOICE || resultCode != RESULT_OK || data == null) return;
        List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) return;
        String heard = results.get(0).replace("'", "\\'").replace("\n", " ");
        web.evaluateJavascript("window.voiceResult && window.voiceResult('" + heard + "')", null);
    }

    /** Методы, доступные из JS как window.AndroidVoice.* */
    private class VoiceBridge {
        @JavascriptInterface
        public void start() {
            runOnUiThread(MainActivity.this::startVoice);
        }
    }
}
