package com.roomarchive.server;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
public class TetrisGameActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent i = new Intent(this, GameWebActivity.class);
        i.putExtra("game", "tetris");
        startActivity(i);
        finish();
    }
}
