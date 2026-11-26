package com.zippy.ear;

import android.app.AlertDialog;

/** v1.3: confirm before "Reset voice" deletes the saved Hey Zippy voice takes. */
public final class ResetConfirm {
    private ResetConfirm() {}

    public static void show(final MainActivity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Reset voice data?")
                .setMessage("This will delete your saved Hey Zippy voice takes and you'll need to train again.")
                .setPositiveButton("Delete", (dialog, which) -> MainActivity.resetVoiceConfirmed(activity))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }
}
